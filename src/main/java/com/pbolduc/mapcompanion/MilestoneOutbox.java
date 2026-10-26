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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The things worth telling a group about as they happen, held until the map says it heard.
 *
 * <h2>Why an outbox and not another snapshot field</h2>
 *
 * <p>Everything else on this wire is a <i>state</i>: where you are, what your levels are, what the
 * patches looked like. A state can be sent late, sent twice, or missed entirely and the next post
 * repairs it. A drop is an <i>event</i>, and none of that is true of one: missing it means it never
 * happened as far as the group is concerned, and sending it twice means announcing it twice.
 *
 * <p>The map could in principle spot a drop by diffing two kill tallies, and that was the shape
 * before this. It cannot work: the tallies are capped at forty monsters and eight drops each, so a
 * drop that falls off the end of the cap is a drop nobody hears about, and a reconnect that
 * re-sends a whole tally would announce a year of history as news. So drops are stated, once,
 * with an id.
 *
 * <h2>What makes an announcement arrive exactly once</h2>
 *
 * <p>Each event gets a random id when it happens, and keeps it. Every post carries up to
 * {@link #MAX_BATCH} of the pending ones, and an event stays pending until the map's reply names
 * its id — so a post that never lands, a reply that is lost, or a connection that drops for a
 * minute all end in the same event arriving with the same id, which the map recognises as one it
 * already has. Nothing here waits for an acknowledgement before sending the next thing, and
 * nothing here posts on its own: the existing snapshot cadence carries all of it, so this feature
 * costs the relay no requests at all.
 *
 * <p>The session id and sequence number ride along so the map can tell a fresh start from a
 * reconnect, and can order two events that were seen in the same second.
 *
 * <h2>Bounded, and why it forgets rather than grows</h2>
 *
 * <p>{@link #MAX_PENDING} events for {@link #RETRY_MS}. A queue that grew without limit would turn
 * an hour offline into a snapshot too large to send — the wire cap is sixteen kilobytes and the
 * refusal for exceeding it is permanent — and an event that has gone five minutes unacknowledged
 * is not news any more anyway. When the queue is full the oldest goes, because the newest is the
 * one somebody is still looking at their screen about.
 *
 * <h2>What is not in here</h2>
 *
 * <p>This class does not decide which game signals describe one reward. {@link RewardObserver}
 * joins those first, and this queue assigns delivery identity only after that bounded wait.
 * Legacy NPC entries remain readable for the existing harness and older protocol fixtures.
 */
@Singleton
public class MilestoneOutbox
{
	/** How many unacknowledged events are held before the oldest is dropped. */
	static final int MAX_PENDING = 32;

	/** How many ride on one post. Small: the wire cap is shared with everything else on it. */
	static final int MAX_BATCH = 8;

	/**
	 * How long an event keeps being retried.
	 *
	 * <p>The same five minutes the map's own dedup memory keeps an id for. Retrying past that would
	 * mean sending something the map has forgotten it already saw, which is how one drop becomes
	 * two announcements.
	 */
	static final long RETRY_MS = 5 * 60_000L;

	static final String KIND_NPC_DROP = "npc_drop";
	static final String KIND_LEVEL = "level";
	static final String KIND_REWARD = "reward";

	private static final class Pending
	{
		final String id = UUID.randomUUID().toString();
		final String kind;
		final int sequence;
		final long atMs;
		String npc;
		String item;
		Integer itemId;
		Integer quantity;
		String rewardType;
		String sourceKind;
		String sourceName;
		Boolean collectionNew;
		String skill;
		int level;

		Pending(String kind, int sequence, long atMs)
		{
			this.kind = kind;
			this.sequence = sequence;
			this.atMs = atMs;
		}
	}

	/**
	 * Events arrive on the client thread, are taken on the client thread, and are acknowledged on
	 * OkHttp's — three doors, so every method here is synchronized. The same reasoning as
	 * {@link LootLog}: the compound operations are what need to be atomic, not the collection.
	 */
	private final Deque<Pending> pending = new ArrayDeque<>();

	/**
	 * Which run of the plugin these events belong to.
	 *
	 * <p>Random per session and reset on a character change, so a group's timeline can tell "these
	 * came from one sitting" from "this client restarted", and so one character's drops can never
	 * be filed under another's.
	 */
	private String session = UUID.randomUUID().toString();

	private int sequence = 0;

	/** A notable drop somebody just got. Ignored if the item is not one the map holds a rate for. */
	public synchronized void npcDrop(String npc, String item, int itemId, int quantity, long nowMs)
	{
		if (npc == null || item == null || npc.isEmpty() || item.isEmpty() || itemId <= 0 || quantity <= 0)
		{
			return;
		}
		Pending event = new Pending(KIND_NPC_DROP, ++sequence, nowMs);
		event.npc = npc;
		event.item = item;
		event.itemId = itemId;
		event.quantity = quantity;
		enqueue(event, nowMs);
	}

	/** A settled observation. Unknown metadata is never filled from a nearby unrelated event. */
	public synchronized void reward(RewardObservation observation, long nowMs)
	{
		if (observation == null || observation.item == null || observation.item.trim().isEmpty()
			|| observation.item.length() > 80 || observation.item.matches(".*[\\p{Cntrl}].*")
			|| (observation.itemId != null && (observation.itemId <= 0 || observation.itemId > 1_000_000))
			|| (observation.quantity != null && observation.quantity <= 0)
			|| !("item".equals(observation.rewardType) || "pet".equals(observation.rewardType)
				 || "collection_log".equals(observation.rewardType))
			|| ("item".equals(observation.rewardType) && (observation.quantity == null || observation.itemId == null)))
		{
			return;
		}
		Pending event = new Pending(KIND_REWARD, ++sequence, observation.at);
		event.rewardType = observation.rewardType;
		event.item = observation.item;
		event.itemId = observation.itemId;
		event.quantity = observation.quantity;
		if (("npc".equals(observation.sourceKind) || "activity".equals(observation.sourceKind))
			&& observation.sourceName != null && !observation.sourceName.trim().isEmpty()
			&& observation.sourceName.length() <= 80 && !observation.sourceName.matches(".*[\\p{Cntrl}].*"))
		{
			event.sourceKind = observation.sourceKind;
			event.sourceName = observation.sourceName;
		}
		event.collectionNew = Boolean.TRUE.equals(observation.collectionNew) ? true : null;
		enqueue(event, nowMs);
	}

	/**
	 * A real level increase — not a boost, and not the first sighting of a level on login.
	 *
	 * <p>The caller owns that distinction, because it is the caller that knows whether it has seen
	 * this skill before this session. Announcing every level the client reports at login would tell
	 * a group that somebody just got twenty-three levels at once.
	 */
	public synchronized void levelUp(String skill, int level, long nowMs)
	{
		if (skill == null || skill.isEmpty() || level < 2 || level > 99)
		{
			return;
		}
		Pending event = new Pending(KIND_LEVEL, ++sequence, nowMs);
		event.skill = skill;
		event.level = level;
		enqueue(event, nowMs);
	}

	/** What to put on the next post, oldest first, or an empty list when there is nothing. */
	public synchronized List<CompanionSnapshot.Milestone> take(long nowMs)
	{
		sweep(nowMs);
		List<CompanionSnapshot.Milestone> batch = new ArrayList<>();
		for (Pending event : pending)
		{
			if (batch.size() >= MAX_BATCH)
			{
				break;
			}
			CompanionSnapshot.Milestone wire = new CompanionSnapshot.Milestone();
			wire.id = event.id;
			wire.session = session;
			wire.sequence = event.sequence;
			wire.kind = event.kind;
			/** Relative, like every other time on this wire, so three clocks cannot disagree. */
			wire.msAgo = Math.max(0L, nowMs - event.atMs);
			wire.npc = event.npc;
			wire.item = event.item;
			wire.itemId = event.itemId;
			wire.quantity = event.quantity;
			wire.rewardType = event.rewardType;
			wire.collectionNew = event.collectionNew;
			if (event.sourceKind != null)
			{
				wire.source = new CompanionSnapshot.RewardSource();
				wire.source.kind = event.sourceKind;
				wire.source.name = event.sourceName;
			}
			wire.skill = event.skill;
			wire.level = KIND_LEVEL.equals(event.kind) ? event.level : null;
			batch.add(wire);
		}
		return batch;
	}

	/**
	 * The map has these. Stop sending them.
	 *
	 * <p>It answers with every id it read, including ones it had already and ones it read and
	 * could not accept, which is what makes a retry converge instead of repeating forever: an
	 * entry the map refuses is refused the same way on every retry, so naming it is the only
	 * answer that frees its slot in the batch.
	 */
	public synchronized void acknowledge(Collection<String> ids)
	{
		if (ids == null || ids.isEmpty() || pending.isEmpty())
		{
			return;
		}
		Set<String> acknowledged = new HashSet<>(ids);
		pending.removeIf(event -> acknowledged.contains(event.id));
	}

	/**
	 * Forget everything and start a new session.
	 *
	 * <p>Called when the plugin stops and when the player switches character. The second is the one
	 * that matters: a drop this queue is still holding belongs to whoever got it, and posting it
	 * after a switch would report one character's history under another's dot.
	 */
	public synchronized void reset()
	{
		pending.clear();
		session = UUID.randomUUID().toString();
		sequence = 0;
	}

	/** How many are waiting. For the sidebar and for tests; nothing on the wire reads it. */
	synchronized int pendingCount()
	{
		return pending.size();
	}

	private void enqueue(Pending event, long nowMs)
	{
		sweep(nowMs);
		while (pending.size() >= MAX_PENDING)
		{
			/**
			 * The oldest goes. It has had the most chances to be delivered already, and the newest
			 * is the one somebody is still looking at their screen about.
			 */
			pending.removeFirst();
		}
		pending.addLast(event);
	}

	private void sweep(long nowMs)
	{
		Iterator<Pending> events = pending.iterator();
		while (events.hasNext())
		{
			Pending event = events.next();
			if (nowMs - event.atMs > RETRY_MS)
			{
				events.remove();
			}
		}
	}
}
