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

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The gravestone's time left, read from the game's own timer: shown only while the game says a stone
 * stands and the reading is one the game itself would print, and only under a death it can be from.
 *
 * <p>The unit is the game's: {@code GRAVESTONE_DURATION} counts ticks of 0.6s, as the game's HUD
 * script converts it. Whether it pauses while logged out, and what a blessing does to it, a live
 * client has to show; {@code docs/live-checks.md} says how.
 */
public class GravestoneTest
{
	private final Map<Integer, Integer> varbits = new ConcurrentHashMap<>();
	private Player player;
	private final DeathWatcher watcher = watcher();

	private DeathWatcher watcher()
	{
		player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getLocalLocation":
						return null;
					case "getWorldLocation":
						return new WorldPoint(3222, 3218, 0);
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getLocalPlayer":
						return player;
					case "getTopLevelWorldView":
						return null;
					case "getVarbitValue":
						return varbits.getOrDefault((Integer) args[0], 0);
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		try
		{
			Constructor<DeathWatcher> make = DeathWatcher.class.getDeclaredConstructor(Client.class);
			make.setAccessible(true);
			return make.newInstance(client);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private void stone(int visible, int ticks)
	{
		varbits.put(VarbitID.GRAVESTONE_VISIBLE, visible);
		varbits.put(VarbitID.GRAVESTONE_DURATION, ticks);
	}

	/** A session up long enough to believe, as the plugin's game tick would leave it. */
	private long settled()
	{
		long now = System.currentTimeMillis();
		watcher.onSessionResumed(now - 20_000L);
		return now;
	}

	@Test
	public void theTimerIsTicksAndTheCardSaysMinutesLeftRoundedUp()
	{
		assertEquals("15m left", new DeathWatcher.Stone(1_500, 1_500, 0L).left());
		/* 830 ticks are 498 seconds: eight minutes and some, so nine are left to say. */
		assertEquals("9m left", new DeathWatcher.Stone(830, 1_500, 0L).left());
		assertEquals("1m left", new DeathWatcher.Stone(1, 1_500, 0L).left());
		assertEquals("Gravestone · 15m left", NowCards.graveLine(new DeathWatcher.Stone(1_500, 1_500, 0L)));
	}

	/** Under a fresh death: full at the first reading, then draining, and gone with the stone. */
	@Test
	public void aStoneUnderTheDeathCountsDownAndGoes()
	{
		long now = settled();
		watcher.record(new ActorDeath(player));
		stone(1, 1_500);
		watcher.readGravestone(now + 3_000L);
		DeathWatcher.Stone first = watcher.gravestone(now + 3_000L);
		assertNotNull(first);
		assertEquals(1_500, first.ticksLeft);
		assertEquals(1_500, first.ticksFull);

		stone(1, 830);
		watcher.readGravestone(now + 4_000L);
		DeathWatcher.Stone later = watcher.gravestone(now + 4_000L);
		assertEquals(830, later.ticksLeft);
		assertEquals("the bar is full at the first reading", 1_500, later.ticksFull);

		stone(0, 0);
		watcher.readGravestone(now + 5_000L);
		assertNull("the stone is gone, and the card keeps only the death", watcher.gravestone(now + 5_000L));
	}

	/** Anything the game itself would not put a number on shows nothing rather than a doubtful one. */
	@Test
	public void aDoubtfulReadingShowsNothing()
	{
		long now = settled();
		watcher.record(new ActorDeath(player));
		stone(0, 900);
		watcher.readGravestone(now + 1_000L);
		assertNull("a timer with no stone standing", watcher.gravestone(now + 1_000L));
		stone(1, 0);
		watcher.readGravestone(now + 2_000L);
		assertNull(watcher.gravestone(now + 2_000L));
		stone(1, DeathWatcher.MOST_TICKS_SHOWN + 1);
		watcher.readGravestone(now + 3_000L);
		assertNull("past fifteen minutes the game prints no time either", watcher.gravestone(now + 3_000L));
	}

	/**
	 * Right after a login or a hop every varbit reads zero, and nothing is believed until the session
	 * settles; a logout puts the line away at once, and it comes back once settled again.
	 */
	@Test
	public void nothingIsShownUntilTheSessionSettles()
	{
		long now = System.currentTimeMillis();
		watcher.record(new ActorDeath(player));
		stone(1, 1_200);
		watcher.onSessionResumed(now);
		watcher.readGravestone(now + 1_000L);
		assertNull("not settled yet", watcher.gravestone(now + 1_000L));
		watcher.readGravestone(now + 16_000L);
		assertNotNull(watcher.gravestone(now + 16_000L));

		watcher.onSessionEnded();
		assertNull(watcher.gravestone(now + 16_500L));
		watcher.onSessionResumed(now + 60_000L);
		watcher.readGravestone(now + 61_000L);
		assertNull(watcher.gravestone(now + 61_000L));
		watcher.readGravestone(now + 76_000L);
		assertEquals(1_200, watcher.gravestone(now + 76_000L).ticksLeft);
	}

	/**
	 * A stone left by an earlier death is not the stone of a later one that left none: it was standing
	 * before that death, so the card shows no time. A timer that goes up is a stone placed since.
	 */
	@Test
	public void aStoneFromBeforeTheDeathIsNotItsOwn() throws Exception
	{
		long now = System.currentTimeMillis();
		watcher.onSessionResumed(now - 60_000L);
		stone(1, 1_000);
		watcher.readGravestone(now - 30_000L);
		watcher.record(new ActorDeath(player));
		stone(1, 990);
		watcher.readGravestone(now + 1_000L);
		assertNull(watcher.gravestone(now + 1_000L));

		stone(1, 1_500);
		watcher.readGravestone(now + 2_000L);
		DeathWatcher.Stone placed = watcher.gravestone(now + 2_000L);
		assertNotNull("a new stone, placed after the death", placed);
		assertEquals(1_500, placed.ticksFull);
	}
}
