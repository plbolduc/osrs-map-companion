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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * What this sidebar holds about the group beyond what the map last said: whose dot the player
 * asked to see, a Route to a friend waiting for that friend's position, and answers just given.
 *
 * <p><b>World map</b> draws one friend's dot while the world-map group layer is off. Their tile is
 * in {@link RelayResponse.MemberDot}, which the held stream carries only when asked for
 * ({@code members=1}), so a friend shown, or a friend being routed to, is what makes the stream ask —
 * see {@link #wantsPositions}. Nothing follows a friend: a Route goes once to where they stood when
 * their dot arrived, and pressing it again asks again.
 *
 * <p><b>Nothing here outlives the group.</b> Every reading first compares {@link DestinationState}'s
 * clear count with the one it last saw, and forgets everything when they differ, so a friend shown in
 * one group is not asked for in the next. Dots are trusted while the stream keeps delivering:
 * unchanged frames are deduped by the server, so its keep-alive comments renew their freshness.
 * A dropped stream discards them, and silence past {@link #DOT_FRESH_MS} refuses them.
 *
 * <p>Swing presses, OkHttp's stream frames and the game tick all reach it, so every method holds
 * this object's lock.
 */
@Singleton
public class GroupState
{
	/** Older than this, a dot is from a stream that is no longer delivering. */
	static final long DOT_FRESH_MS = 45_000L;
	/** How long a Route waits for the friend's dot before saying it did not come. */
	static final long PENDING_MS = 30_000L;
	/** How long an answer just given stands in for the roll-up's, until the map's next word. */
	static final long ANSWER_HOLD_MS = 30_000L;

	private final DestinationState destinations;
	private long seen = -1;
	private final Set<String> shown = new HashSet<>();
	private List<RelayResponse.MemberDot> dots = Collections.emptyList();
	private long dotsAtMs;
	@Nullable
	private String pending;
	private long pendingAtMs;
	@Nullable
	private String lost;
	private final Map<String, Answered> answered = new HashMap<>();

	private static final class Answered
	{
		final String answer;
		final long atMs;

		Answered(String answer, long atMs)
		{
			this.answer = answer;
			this.atMs = atMs;
		}
	}

	@Inject
	GroupState(DestinationState destinations)
	{
		this.destinations = destinations;
	}

	/** Forget the last group's choices once the destinations have been cleared for a new one. */
	private void current()
	{
		long generation = destinations.generation();
		if (generation != seen)
		{
			seen = generation;
			shown.clear();
			dots = Collections.emptyList();
			dotsAtMs = 0L;
			pending = null;
			lost = null;
			answered.clear();
		}
	}

	/** The friend's dot is drawn on the world map, or not. The player's own press. */
	synchronized void toggleShown(String id)
	{
		current();
		if (!shown.remove(id))
		{
			shown.add(id);
		}
	}

	synchronized boolean shown(String id)
	{
		current();
		return shown.contains(id);
	}

	/** Whether the stream should ask for positions for this sidebar's own sake. */
	synchronized boolean wantsPositions(long now)
	{
		current();
		return !shown.isEmpty() || (pending != null && now - pendingAtMs < PENDING_MS);
	}

	/** The positions a stream frame carried. Null is "not asked" and changes nothing. */
	synchronized void dots(@Nullable List<RelayResponse.MemberDot> members, long now)
	{
		current();
		if (members == null)
		{
			return;
		}
		dots = new ArrayList<>(members);
		dotsAtMs = now;
	}

	/** Transport liveness renews an unchanged answer; losing that transport forgets its dots. */
	synchronized void stream(boolean listening, long readAtMs)
	{
		current();
		if (!listening)
		{
			dots = Collections.emptyList();
			dotsAtMs = 0L;
			return;
		}
		dotsAtMs = Math.max(dotsAtMs, readAtMs);
	}

	/** Only the friends the player asked to see, for drawing while the group layer is off. */
	synchronized List<RelayResponse.MemberDot> shownDots(@Nullable List<RelayResponse.MemberDot> members)
	{
		current();
		List<RelayResponse.MemberDot> kept = new ArrayList<>();
		if (members != null)
		{
			for (RelayResponse.MemberDot dot : members)
			{
				if (dot != null && dot.id != null && shown.contains(dot.id))
				{
					kept.add(dot);
				}
			}
		}
		return kept;
	}

	/**
	 * Route to a friend: their fresh dot when there is one, or null after asking for it — the stream
	 * then asks for positions, and {@link #arrived} hands back the dot once it comes.
	 */
	@Nullable
	synchronized RelayResponse.MemberDot routeTo(String id, long now)
	{
		current();
		lost = null;
		RelayResponse.MemberDot dot = now - dotsAtMs < DOT_FRESH_MS ? find(dots, id) : null;
		if (dot != null)
		{
			pending = null;
			return dot;
		}
		pending = id;
		pendingAtMs = now;
		return null;
	}

	/**
	 * A frame with positions arrived: the waiting friend's dot, once, or null. A friend not in it is
	 * not sharing a position just now, and their row says so rather than waiting on.
	 */
	@Nullable
	synchronized RelayResponse.MemberDot arrived(@Nullable List<RelayResponse.MemberDot> members, long now)
	{
		current();
		if (pending == null || members == null)
		{
			return null;
		}
		String id = pending;
		pending = null;
		if (now - pendingAtMs >= PENDING_MS)
		{
			lost = id;
			return null;
		}
		RelayResponse.MemberDot dot = find(members, id);
		if (dot == null)
		{
			lost = id;
		}
		return dot;
	}

	/** Whether a Route to this friend is waiting for their dot. */
	synchronized boolean finding(String id, long now)
	{
		current();
		if (pending != null && now - pendingAtMs >= PENDING_MS)
		{
			lost = pending;
			pending = null;
		}
		return id.equals(pending);
	}

	/** Whether the last Route to this friend found no position to go to. */
	synchronized boolean lost(String id)
	{
		current();
		return id.equals(lost);
	}

	/** An answer just given, standing in for the roll-up's until the map has had time to say it. */
	synchronized void answered(String eventId, String answer, long now)
	{
		current();
		answered.put(eventId, new Answered(answer, now));
	}

	/** This member's answer to a meetup: one just given, else the map's. */
	@Nullable
	synchronized String mine(RelayResponse.Meetup meetup, long now)
	{
		current();
		Answered held = answered.get(meetup.id);
		if (held != null && now - held.atMs < ANSWER_HOLD_MS)
		{
			return held.answer;
		}
		return meetup.mine;
	}

	/** Which Route button a friend's press is said beside. */
	static String source(String id)
	{
		return "friend:" + id;
	}

	/**
	 * A Route to where a friend stands: their dot's tile, under the name their row gives them, with
	 * their world and where they are in words as the destination's line.
	 */
	static RoutePress.Target routeTarget(RelayResponse.MemberDot dot, @Nullable List<RelayResponse.RosterRow> roster)
	{
		RelayResponse.RosterRow row = null;
		if (roster != null)
		{
			for (RelayResponse.RosterRow candidate : roster)
			{
				if (candidate.id.equals(dot.id))
				{
					row = candidate;
				}
			}
		}
		String name = row != null ? row.name : MapPin.safe(dot.name);
		return RoutePress.Target.tile(source(dot.id), new net.runelite.api.coords.WorldPoint(dot.x, dot.y, dot.plane),
			name == null || name.isEmpty() ? "Member" : name, dot.world, row == null ? null : row.near);
	}

	@Nullable
	private static RelayResponse.MemberDot find(List<RelayResponse.MemberDot> members, String id)
	{
		for (RelayResponse.MemberDot dot : members)
		{
			if (dot != null && id.equals(dot.id))
			{
				return dot;
			}
		}
		return null;
	}
}
