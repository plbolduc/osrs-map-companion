/*
 * Copyright (c) 2026, pbolduc
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pbolduc.mapcompanion;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * Progress through one received run, from local evidence rather than being near a patch.
 *
 * <p>Replant receipts after local receipt of the run finish its tracked patches. A seeded
 * birdhouse first observed after that instant is weaker Time Tracking evidence, labelled by the
 * view. Custom stops only move the pointer on: a visit never becomes a confirmed farming job.
 *
 * <p>A port-task run's stops are ticked by this client's own task slots alone ({@link #observePorts}):
 * a counter reaching the stop's target, or the slot letting go of a task where that is how the task
 * ends. A slot that comes to hold another task, empties early, or takes a task the run never had
 * makes the run {@link View#changed}, which the sidebar says and offers Reroute for.
 *
 * <p>The server's revision orders heartbeat, stream and press replies, including removal.
 * Repeated frames cannot rewind local progress. A connection/profile reset invalidates tickets;
 * a local End ignores that run even if a newer reroute frame was already in flight.
 * All fields share this lock because game ticks, Swing and network callbacks meet here.
 */
@Singleton
final class FarmRunState
{
	static final long FRESH_MS = 120_000L;
	static final long RETENTION_MS = 3 * 60 * 60_000L;
	static final int HERE = 8;

	private RelayResponse.FarmRun run;
	private String member;
	private long revision;
	private long generation;
	private long acceptedAt;
	private long changedAt;
	private boolean following;
	private String dismissedId;
	private final DirectiveEpoch epoch = new DirectiveEpoch();
	/** End may be pressed before an initial plan's id has reached either channel. */
	private boolean endingUnknown;
	private List<String> carried;
	private final Set<String> receipts = new LinkedHashSet<>();
	private final Set<String> patches = new HashSet<>();
	private final Set<String> birdhouses = new HashSet<>();
	private final Set<Integer> done = new HashSet<>();
	private final Set<Integer> visited = new HashSet<>();
	private final Set<Integer> here = new HashSet<>();
	/** The task each slot held when this run's slots were first read; null until then. */
	private Map<Integer, Integer> heldAtStart;
	/** Each slot's last reading while it held a task, by slot; see {@link #tick(RelayResponse.RunTask, List, PortTaskReader.Slot)}. */
	private final Map<Integer, PortTaskReader.Slot> lastHeld = new HashMap<>();
	private boolean changed;

	static final class View
	{
		final RelayResponse.FarmRun run;
		final Set<Integer> done;
		final Set<Integer> visited;
		final Set<Integer> here;
		final boolean following;
		final long generation;
		final long revision;
		final List<String> carried;
		/** A port-task run whose task slots no longer match what it was planned from. */
		final boolean changed;

		View(RelayResponse.FarmRun run, Set<Integer> done, Set<Integer> visited, Set<Integer> here,
			 boolean following, long generation, long revision, @Nullable List<String> carried)
		{
			this(run, done, visited, here, following, generation, revision, carried, false);
		}

		View(RelayResponse.FarmRun run, Set<Integer> done, Set<Integer> visited, Set<Integer> here,
			 boolean following, long generation, long revision, @Nullable List<String> carried, boolean changed)
		{
			this.changed = changed;
			this.run = run;
			this.done = Collections.unmodifiableSet(new HashSet<>(done));
			this.visited = Collections.unmodifiableSet(new HashSet<>(visited));
			this.here = Collections.unmodifiableSet(new HashSet<>(here));
			this.following = following;
			this.generation = generation;
			this.revision = revision;
			this.carried = carried == null ? null : Collections.unmodifiableList(new java.util.ArrayList<>(carried));
		}

		@Nullable
		RelayResponse.FarmStop next()
		{
			for (RelayResponse.FarmStop stop : run.stops)
				if (!done.contains(stop.n) && !visited.contains(stop.n))
				{
					return stop;
				}
			return null;
		}

		int tracked()
		{
			int count = 0;
			for (RelayResponse.FarmStop stop : run.stops)
				if (FarmRunState.tracked(stop))
				{
					count++;
				}
			return count;
		}

		int trackedDone()
		{
			int count = 0;
			for (RelayResponse.FarmStop stop : run.stops)
				if (FarmRunState.tracked(stop) && done.contains(stop.n))
				{
					count++;
				}
			return count;
		}
	}

	/** True only for a stop with evidence to wait for; other stops are route references. */
	static boolean tracked(RelayResponse.FarmStop stop)
	{
		return !stop.patchIds.isEmpty() || stop.birdhouseId != null || ported(stop);
	}

	/** A port-task stop: ticked by task slots, never by patches or by position. */
	static boolean ported(RelayResponse.FarmStop stop)
	{
		return stop.tasks != null && !stop.tasks.isEmpty();
	}

	/** Where one tick stands against the slots read now. */
	enum Tick
	{
		REACHED, WAITING, GONE, UNREAD
	}

	/**
	 * @param last the last reading of this tick's slot while it still held the tick's task, or null
	 *             where none was seen this run. A slot that has let go of a task finished it only if the
	 *             task was last seen with nothing left to do but hand it over: every crate loaded, or
	 *             every part collected. Otherwise it was abandoned, and the run has changed.
	 */
	static Tick tick(RelayResponse.RunTask tick, @Nullable List<PortTaskReader.Slot> slots, @Nullable PortTaskReader.Slot last)
	{
		PortTaskReader.Slot slot = null;
		if (slots != null)
		{
			for (PortTaskReader.Slot candidate : slots)
				if (candidate.slot == tick.slot)
				{
					slot = candidate;
				}
		}
		if (slot == null || PortTaskReader.UNKNOWN.equals(slot.state))
		{
			return Tick.UNREAD;
		}
		boolean holds = slot.held() && slot.taskId != null && slot.taskId == tick.taskId;
		if ("held".equals(tick.counter) && holds)
		{
			return Tick.WAITING;
		}
		if (!holds)
		{
			// The game may clear a slot the moment the task's last crate goes in, so a task let go of
			// after its work was done finished; one let go of before was abandoned. Never seen held,
			// only the stop where the map says the task ends may take the slot's letting go.
			if (last == null)
			{
				return tick.ends ? Tick.REACHED : Tick.GONE;
			}
			boolean finished = PortTaskReader.COURIER.equals(last.state) ? last.taken >= tick.n
				: last.remaining <= 0;
			return finished ? Tick.REACHED : Tick.GONE;
		}
		boolean reached;
		switch (tick.counter)
		{
			case "taken":
				reached = slot.taken >= tick.n;
				break;
			case "delivered":
				reached = slot.delivered >= tick.n;
				break;
			default:
				reached = slot.remaining <= tick.n;
		}
		return reached ? Tick.REACHED : Tick.WAITING;
	}

	/** A receiving channel must still belong to the character it was opened for. */
	synchronized void bind(@Nullable String incomingMember)
	{
		if (!Objects.equals(member, incomingMember))
		{
			clear();
			member = incomingMember;
		}
	}

	synchronized boolean acceptFor(String incomingMember, @Nullable String incomingEpoch, @Nullable RelayResponse.FarmRun incoming, long incomingRevision, long now)
	{
		if (!scope(incomingMember, incomingEpoch))
		{
			return false;
		}
		accept(incoming, incomingRevision, now);
		return true;
	}

	private boolean scope(String incomingMember, @Nullable String incomingEpoch)
	{
		if (incomingMember == null || !incomingMember.equals(member))
		{
			return false;
		}
		DirectiveEpoch.Seen seen = epoch.accept(incomingEpoch);
		if (seen == DirectiveEpoch.Seen.INVALID)
		{
			return false;
		}
		if (seen == DirectiveEpoch.Seen.NEW)
		{
			forgetRun();
			revision = 0;
			dismissedId = null;
		}
		return true;
	}

	/** An explicit new plan's reply can replace a cancelled initial plan even after a failed End. */
	synchronized boolean acceptPlannedFor(String incomingMember, @Nullable String incomingEpoch, RelayResponse.FarmRun incoming, long incomingRevision, long now)
	{
		if (!scope(incomingMember, incomingEpoch))
		{
			return false;
		}
		if (incomingRevision > revision)
		{
			endingUnknown = false;
			accept(incoming, incomingRevision, now);
		}
		return true;
	}

	synchronized void accept(@Nullable RelayResponse.FarmRun incoming, long incomingRevision, long now)
	{
		if (incomingRevision <= revision || incomingRevision <= 0)
		{
			return;
		}
		if (endingUnknown && incoming != null)
		{
			return;
		}
		revision = incomingRevision;
		RelayResponse.FarmRun clean = FarmRunWire.normalize(incoming);
		if (clean == null)
		{
			endingUnknown = false;
			forgetRun();
			return;
		}
		if (clean.id.equals(dismissedId))
		{
			return;
		}
		boolean same = run != null && run.id.equals(clean.id);
		if (!same)
		{
			forgetRun();
			acceptedAt = now; // The local clock prices local receipts; never compare it with the server's.
			following = true;
		}
		else
		{
			// Reroute can give an unfinished stop another ordinal. Only the same stop keeps a visit.
			Set<Integer> keepDone = new HashSet<>();
			Set<Integer> keepVisited = new HashSet<>();
			for (RelayResponse.FarmStop stop : clean.stops)
				for (RelayResponse.FarmStop old : run.stops)
					if (stop.n == old.n && identity(stop).equals(identity(old)))
					{
						if (done.contains(stop.n))
						{
							keepDone.add(stop.n);
						}
						if (visited.contains(stop.n))
						{
							keepVisited.add(stop.n);
						}
					}
			done.retainAll(keepDone);
			visited.retainAll(keepVisited);
			here.clear();
		}
		run = clean;
		changedAt = now;
		for (RelayResponse.FarmStop stop : clean.stops)
		{
			if (stop.done)
			{
				done.add(stop.n);
			}
			if (stop.visited)
			{
				visited.add(stop.n);
			}
		}
		finishTracked();
	}

	/** Fresh local readings only. Missing/disabled sharing may pause posting without disabling local following. */
	synchronized void observe(@Nullable List<CompanionSnapshot.Patch> seenPatches,
							  @Nullable List<CompanionSnapshot.Birdhouse> seenBirdhouses, @Nullable WorldPoint where, long now)
	{
		if (run == null)
		{
			return;
		}
		if (now < changedAt || now - changedAt > RETENTION_MS)
		{
			end();
			return;
		}
		if (seenPatches != null)
		{
			for (CompanionSnapshot.Patch patch : seenPatches)
			{
				if (patch == null || patch.completionId == null || patch.completedMsAgo == null
					|| !receipts.add(patch.completionId))
				{
					continue;
				}
				if (fresh(patch.completedMsAgo, now) && patch.observedMsAgo >= 0 && patch.observedMsAgo <= FRESH_MS)
				{
					patches.add(patch.id);
				}
			}
			while (receipts.size() > 256)
			{
				receipts.remove(receipts.iterator().next());
			}
		}
		if (seenBirdhouses != null)
		{
			for (CompanionSnapshot.Birdhouse house : seenBirdhouses)
				if (house != null && "seeded".equals(house.state) && fresh(house.observedMsAgo, now))
				{
					birdhouses.add(house.id);
				}
		}
		finishTracked();
		here.clear();
		if (where != null)
		{
			for (RelayResponse.FarmStop stop : run.stops)
			{
				if (where.distanceTo(point(stop)) <= HERE)
				{
					here.add(stop.n);
					if (!tracked(stop))
					{
						visited.add(stop.n);
					}
				}
			}
		}
	}

	private boolean fresh(long age, long now)
	{
		return age >= 0 && age <= FRESH_MS && now - age >= acceptedAt && now - age <= now;
	}

	/**
	 * Tick a port-task run's stops from the slots read this tick, or null where they cannot be read
	 * now; nothing is ticked or changed by a missing reading. A later stop whose ticks are all met
	 * ticks too, so a counter that jumped past a stop does not hold the run on it.
	 */
	synchronized void observePorts(@Nullable List<PortTaskReader.Slot> slots)
	{
		if (run == null || slots == null || run.stops.stream().noneMatch(FarmRunState::ported))
		{
			return;
		}
		if (heldAtStart == null)
		{
			heldAtStart = new HashMap<>();
			for (PortTaskReader.Slot slot : slots)
			{
				heldAtStart.put(slot.slot, slot.held() ? slot.taskId : null);
			}
		}
		boolean now = false;
		Set<Integer> named = new HashSet<>();
		for (RelayResponse.FarmStop stop : run.stops)
		{
			if (!ported(stop))
			{
				continue;
			}
			boolean all = true;
			for (RelayResponse.RunTask task : stop.tasks)
			{
				named.add(task.taskId);
				PortTaskReader.Slot last = lastHeld.get(task.slot);
				Tick at = tick(task, slots, last != null && last.taskId != null && last.taskId == task.taskId ? last : null);
				all &= at == Tick.REACHED;
				now |= at == Tick.GONE && !done.contains(stop.n);
			}
			if (all)
			{
				done.add(stop.n);
			}
		}
		for (PortTaskReader.Slot slot : slots)
		{
			if (slot.held())
			{
				lastHeld.put(slot.slot, slot);
			}
		}
		for (PortTaskReader.Slot slot : slots)
		{
			// A task taken since the run was read, that the run has no stop for.
			if (slot.held() && slot.taskId != null && !named.contains(slot.taskId)
				&& !slot.taskId.equals(heldAtStart.get(slot.slot)))
			{
				now = true;
			}
		}
		changed = now;
	}

	private void finishTracked()
	{
		if (run == null)
		{
			return;
		}
		for (RelayResponse.FarmStop stop : run.stops)
			if (tracked(stop) && !ported(stop) && (stop.patchIds.isEmpty() || patches.containsAll(stop.patchIds))
				&& (stop.birdhouseId == null || birdhouses.contains(stop.birdhouseId)))
			{
				done.add(stop.n);
			}
	}

	@Nullable
	synchronized View view()
	{
		return run == null ? null : new View(run, done, visited, here, following, generation, revision, carried, changed);
	}

	synchronized void carried(@Nullable List<String> flags)
	{
		carried = flags == null ? null : new java.util.ArrayList<>(flags);
	}

	synchronized boolean active()
	{
		return run != null;
	}

	synchronized void follow(boolean on)
	{
		following = on && run != null;
	}

	/** A press callback only belongs to the connection/profile lifetime in which it was made. */
	synchronized long generation()
	{
		return generation;
	}

	synchronized boolean current(long ticket)
	{
		return ticket == generation;
	}

	synchronized void end()
	{
		end(run == null);
	}

	synchronized void end(boolean unknownId)
	{
		endingUnknown = unknownId;
		if (run != null)
		{
			dismissedId = run.id;
		}
		forgetRun();
	}

	synchronized boolean ending()
	{
		return endingUnknown;
	}

	synchronized void clear()
	{
		forgetRun();
		revision = 0;
		dismissedId = null;
		epoch.clear();
		endingUnknown = false;
		member = null;
	}

	private void forgetRun()
	{
		run = null;
		heldAtStart = null;
		lastHeld.clear();
		changed = false;
		following = false;
		carried = null;
		generation++;
		receipts.clear();
		patches.clear();
		birdhouses.clear();
		done.clear();
		visited.clear();
		here.clear();
	}

	static WorldPoint point(RelayResponse.FarmStop stop)
	{
		return new WorldPoint(stop.tile.x, stop.tile.y, stop.tile.plane == null ? 0 : stop.tile.plane);
	}

	private static String identity(RelayResponse.FarmStop stop)
	{
		StringBuilder ticks = new StringBuilder();
		if (stop.tasks != null)
		{
			for (RelayResponse.RunTask task : stop.tasks)
			{
				ticks.append(task.slot).append(':').append(task.taskId).append(':').append(task.counter).append(':').append(task.n).append(';');
			}
		}
		return point(stop) + "|" + stop.name + "|" + stop.patchIds + "|" + Objects.toString(stop.birdhouseId) + "|" + ticks;
	}
}
