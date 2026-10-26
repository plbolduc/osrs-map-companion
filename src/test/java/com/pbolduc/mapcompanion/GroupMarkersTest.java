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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import okhttp3.HttpUrl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The group on the game's world map: what is drawn, what moves, and what is taken down.
 *
 * <p>The world map itself cannot run here, so the manager is RuneLite's real one, which is a
 * list, and the client thread runs what it is handed at once.
 */
public class GroupMarkersTest
{
	private final List<WorldMapPoint> drawn = new ArrayList<>();
	private final WorldMapPointManager manager = new WorldMapPointManager()
	{
		@Override
		public void add(WorldMapPoint point)
		{
			drawn.add(point);
		}

		@Override
		public void remove(WorldMapPoint point)
		{
			drawn.remove(point);
		}
	};
	private final ClientThread now = new ClientThread()
	{
		@Override
		public void invoke(Runnable r)
		{
			r.run();
		}
	};
	private final GroupMarkers markers = new GroupMarkers(manager, now);

	private static RelayResponse.MemberDot dot(String id, String name, int x, int y)
	{
		RelayResponse.MemberDot dot = new RelayResponse.MemberDot();
		dot.id = id;
		dot.name = name;
		dot.colour = "#38bdf8";
		dot.x = x;
		dot.y = y;
		dot.world = 302;
		return dot;
	}

	@Test
	public void drawsEachMemberOnceAndMovesThemWithoutRebuilding()
	{
		markers.show(Arrays.asList(dot("bob", "Big Rob", 3222, 3218), dot("cara", "Cara", 2440, 3090)));
		assertEquals(2, drawn.size());
		WorldMapPoint bob = drawn.get(0);
		assertEquals("Big Rob · W302", bob.getTooltip());
		assertTrue("a friend off the view stays on its edge", bob.isSnapToEdge());

		markers.show(Arrays.asList(dot("bob", "Big Rob", 3223, 3218), dot("cara", "Cara", 2440, 3090)));
		assertEquals(2, drawn.size());
		assertSame("a step moves the point; it does not draw a new one", bob, drawn.get(0));
		assertEquals(new WorldPoint(3223, 3218, 0), bob.getWorldPoint());
	}

	@Test
	public void aFrameLargerThanAGroupDrawsNoMoreThanAGroupHolds()
	{
		List<RelayResponse.MemberDot> crowd = new ArrayList<>();
		for (int i = 0; i < 5_000; i++)
		{
			crowd.add(dot("m" + i, "M" + i, 3200 + i % 50, 3200));
		}
		markers.show(crowd);
		assertEquals(GroupMarkers.MAX_MEMBERS, drawn.size());
	}

	@Test
	public void takesDownWhoeverTheMapNoLongerSends()
	{
		markers.show(Arrays.asList(dot("bob", "Bob", 3222, 3218), dot("cara", "Cara", 2440, 3090)));
		markers.show(Collections.singletonList(dot("cara", "Cara", 2440, 3090)));
		assertEquals(1, drawn.size());
		assertEquals("Cara · W302", drawn.get(0).getTooltip());
	}

	@Test
	public void aFrameThatDidNotAskChangesNothingAndClearTakesEverythingDown()
	{
		markers.show(Collections.singletonList(dot("bob", "Bob", 3222, 3218)));
		markers.show(null);
		assertEquals(1, drawn.size());
		markers.clear();
		assertTrue(drawn.isEmpty());
		assertEquals(0, markers.count());
	}

	@Test
	public void clearingBeforeTheClientThreadDrawsDiscardsTheQueuedFrame()
	{
		List<Runnable> pending = new ArrayList<>();
		GroupMarkers queued = new GroupMarkers(manager, new ClientThread()
		{
			@Override
			public void invoke(Runnable action)
			{
				pending.add(action);
			}
		});

		queued.show(Collections.singletonList(dot("bob", "Bob", 3222, 3218)));
		queued.clear();
		pending.forEach(Runnable::run);

		assertTrue("a frame queued before logout or disabling sharing must never appear", drawn.isEmpty());
		assertEquals(0, queued.count());
	}

	@Test
	public void aNewConnectionCanDrawAfterAnOldQueuedFrameWasCleared()
	{
		List<Runnable> pending = new ArrayList<>();
		GroupMarkers queued = new GroupMarkers(manager, new ClientThread()
		{
			@Override
			public void invoke(Runnable action)
			{
				pending.add(action);
			}
		});

		queued.show(Collections.singletonList(dot("bob", "Bob", 3222, 3218)));
		queued.clear();
		queued.show(Collections.singletonList(dot("cara", "Cara", 2440, 3090)));
		pending.forEach(Runnable::run);

		assertEquals(1, drawn.size());
		assertEquals("Cara · W302", drawn.get(0).getTooltip());

		pending.clear();
		queued.clear();
		queued.show(Collections.singletonList(dot("dora", "Dora", 2441, 3090)));
		// A direct client-thread invoke can overtake a clear queued from another thread.
		pending.get(1).run();
		pending.get(0).run();
		assertEquals(1, drawn.size());
		assertEquals("Dora · W302", drawn.get(0).getTooltip());
	}

	@Test
	public void saysWhatTheDotCannotAndRebuildsWhenThatChanges()
	{
		RelayResponse.MemberDot quiet = dot("bob", "Bob", 2272, 4062);
		quiet.plane = 1;
		quiet.instance = true;
		quiet.stale = true;
		markers.show(Collections.singletonList(dot("bob", "Bob", 2272, 4062)));
		WorldMapPoint before = drawn.get(0);
		markers.show(Collections.singletonList(quiet));
		assertFalse("a fade is a new picture", before == drawn.get(0));
		assertEquals("Bob · W302 · floor 1 · in their own instance · not heard from lately", drawn.get(0).getTooltip());
	}

	/** A name is whatever its owner typed, and RuneLite reads tags in the menu entry a point adds. */
	@Test
	public void aNameIsShownAsWordsNeverAsTags()
	{
		markers.show(Collections.singletonList(dot("bob", "<col=ff0000>Bob</col><img=1>", 3222, 3218)));
		assertEquals("Bob", drawn.get(0).getName());
		assertEquals("Bob · W302", drawn.get(0).getTooltip());
		markers.show(Collections.singletonList(dot("bob", "", 3222, 3218)));
		assertEquals("Member", drawn.get(0).getName());
	}

	@Test
	public void initialsAreTheMapsInitials()
	{
		assertEquals("BR", MapPin.initials("Big Rob"));
		assertEquals("ZE", MapPin.initials("Zezima"));
		assertEquals("?", MapPin.initials("  "));
	}

	/** The question is asked only while the setting is on, and the map sends nobody otherwise. */
	@Test
	public void theStreamAsksForMembersOnlyWhenTold()
	{
		HttpUrl plain = DirectiveStream.streamUrl("https://osrs-map.com", "ABC234", "m1", false);
		HttpUrl asked = DirectiveStream.streamUrl("https://osrs-map.com", "ABC234", "m1", true);
		assertNull(plain.queryParameter("members"));
		assertEquals("1", asked.queryParameter("members"));
	}
}
