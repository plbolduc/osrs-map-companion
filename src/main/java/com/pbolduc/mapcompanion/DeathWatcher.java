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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.Player;

/**
 * Where you died.
 *
 * <p>The one piece of state in this plugin that is most useful <em>after</em> the thing it describes
 * has already gone wrong. You die, the screen sends you somewhere else, and the question is which
 * way is back and whether you can get there in time — a route calculation over your own unlocks,
 * done while a timer runs and with none of the composure that usually implies.
 *
 * <p>So it survives the death screen and it survives logging back in. Clearing it on logout would
 * throw the answer away at precisely the moment somebody wants it.
 *
 * <p>Nothing here acts. It records a tile; the map draws it and offers a route, and walking there
 * is the player's own business — the same line every other part of this plugin holds.
 */
@Slf4j
@Singleton
public class DeathWatcher
{
	/**
	 * After this long the marker stops being about a gravestone and starts being clutter.
	 *
	 * <p>An hour rather than the fifteen minutes a gravestone lasts, because the items are only half
	 * of it: a death in the middle of nowhere is also a walk you have to repeat, and knowing where
	 * it happened stays useful for longer than the stone does.
	 */
	private static final long FORGET_AFTER_MS = 60 * 60_000L;

	private final Client client;

	/**
	 * Written on the client thread (the death event) and read on Swing's, once a second, for as
	 * long as the sidebar is open. Two threads, so the fields are volatile — the same reason
	 * {@link LootLog} locks every door. A torn read here would be a marker at the origin or a
	 * death that aged out a millisecond ago still drawing as fresh.
	 */
	private volatile WorldPoint diedAt = null;
	private volatile long diedAtMs = 0L;

	@Inject
	private DeathWatcher(Client client)
	{
		this.client = client;
	}

	/** Called from the plugin, which owns every event subscription. See {@link LootLog#record}. */
	public void record(ActorDeath event)
	{
		/**
		 * Every actor's death arrives here, including every monster the player kills. Identity, not
		 * type, is the test: comparing against the local player object is what distinguishes your
		 * death from the thousands of others you cause.
		 */
		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local)
		{
			return;
		}
		/**
		 * Through {@link WorldTiles}, so an instanced death records the template tile rather than the
		 * instance's own coordinates. This read the world location directly and was wrong for every
		 * death in an instance — which is very nearly every death at a boss, the one case this whole
		 * class exists for. A marker in an empty field is worse than no marker.
		 */
		WorldPoint at = WorldTiles.of(client, local);
		if (at == null)
		{
			/**
			 * Nothing worth a marker: an instance the client cannot resolve a template tile for. An
			 * older real death is better kept than replaced with a guess — the same choice the rest of
			 * this plugin makes when a reading will not resolve.
			 */
			return;
		}
		if (WorldTiles.ASSEMBLED.equals(WorldTiles.layout(client)))
		{
			/**
			 * The same choice for an instance put together from rooms. Its template tile is a room in
			 * the store every variant of it is kept in, not a place anybody can walk back to — the
			 * marker in an empty field again, one level down. The ones known are a raid, a house and
			 * the Gauntlet.
			 */
			return;
		}
		diedAt = at;
		diedAtMs = System.currentTimeMillis();
		log.debug("recorded a death");
	}

	/** The death worth showing, or null when there is none or it has aged out. */
	@Nullable
	public CompanionSnapshot.Death snapshot(long nowMs)
	{
		if (diedAt == null)
		{
			return null;
		}
		long age = nowMs - diedAtMs;
		if (age < 0 || age > FORGET_AFTER_MS)
		{
			return null;
		}
		CompanionSnapshot.Death death = new CompanionSnapshot.Death();
		death.tile = new CompanionSnapshot.Tile(diedAt.getX(), diedAt.getY(), diedAt.getPlane());
		death.msAgo = age;
		return death;
	}

	/** Whether there is a death to report at all, for the sidebar to decide whether to draw a line. */
	public boolean hasRecent(long nowMs)
	{
		return snapshot(nowMs) != null;
	}

	public void clear()
	{
		diedAt = null;
		diedAtMs = 0L;
	}
}
