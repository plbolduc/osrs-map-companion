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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * What the sidebar hears of Quest Helper's step through {@link ShortestPath}: the messages Quest
 * Helper posts for Shortest Path, in the shape its {@code DetailedQuestStep} builds them — namespace
 * {@code shortestpath}, {@code path} with {@code start} and {@code target} world points, and a bare
 * {@code clear} — and nothing while Quest Helper is not the one sending, nor anything of this
 * plugin's own walks.
 */
public class QuestHelperStepTest
{
	private static final WorldPoint STANDING = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint STEP = new WorldPoint(3206, 3214, 0);
	private static final WorldPoint UPSTAIRS = new WorldPoint(3166, 3305, 2);

	private boolean sending = true;
	private final ShortestPath heard = new ShortestPath(message -> { });

	{
		heard.questHelperSends = () -> sending;
	}

	/** A step's path, built as Quest Helper's {@code setShortestPath} builds it. */
	static PluginMessage questHelperPath(WorldPoint target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("start", STANDING);
		data.put("target", target);
		return new PluginMessage("shortestpath", "path", data);
	}

	@Test
	public void questHelpersStepIsHeardAndItsNextReplacesIt()
	{
		assertNull(heard.questStep());
		heard.onPluginMessage(questHelperPath(STEP));
		assertEquals(STEP, heard.questStep());
		heard.onPluginMessage(questHelperPath(UPSTAIRS));
		assertEquals("the next step's tile, floor and all", UPSTAIRS, heard.questStep());
	}

	@Test
	public void questHelpersClearEndsTheStep()
	{
		heard.onPluginMessage(questHelperPath(STEP));
		heard.onPluginMessage(new PluginMessage("shortestpath", "clear"));
		assertNull(heard.questStep());
	}

	/**
	 * With Quest Helper stopped, or its Use 'Shortest Path' plugin off, a path is some other plugin's,
	 * and so is the step held from before: neither is offered until Quest Helper sends again.
	 */
	@Test
	public void nothingIsOfferedWhileQuestHelperIsNotSending()
	{
		heard.onPluginMessage(questHelperPath(STEP));
		sending = false;
		assertNull(heard.questStep());
		heard.onPluginMessage(questHelperPath(UPSTAIRS));
		assertNull(heard.questStep());
		sending = true;
		heard.onPluginMessage(questHelperPath(STEP));
		assertEquals(STEP, heard.questStep());
	}

	@Test
	public void aMalformedOrUnrelatedMessageIsIgnored()
	{
		heard.onPluginMessage(questHelperPath(STEP));
		Map<String, Object> words = new HashMap<>();
		words.put("target", "3206,3214,0");
		heard.onPluginMessage(new PluginMessage("shortestpath", "path", words));
		heard.onPluginMessage(new PluginMessage("shortestpath", "path"));
		Map<String, Object> many = new HashMap<>();
		many.put("target", Collections.singletonList(UPSTAIRS));
		heard.onPluginMessage(new PluginMessage("shortestpath", "path", many));
		heard.onPluginMessage(new PluginMessage("shortestpath", "currentTarget", many));
		heard.onPluginMessage(new PluginMessage("some-other-plugin", "clear"));
		Map<String, Object> elsewhere = new HashMap<>();
		elsewhere.put("target", UPSTAIRS);
		heard.onPluginMessage(new PluginMessage("some-other-plugin", "path", elsewhere));
		assertEquals("none of those is a step, nor the end of one", STEP, heard.questStep());
	}

	/** The plugin stopping forgets the step: Quest Helper names the next one only when it starts. */
	@Test
	public void shuttingDownForgetsTheStep()
	{
		heard.onPluginMessage(questHelperPath(STEP));
		heard.shutDown();
		assertNull(heard.questStep());
	}

	/**
	 * This plugin's own walks go to Shortest Path as {@code path} and {@code clear} too, and the bus
	 * hands them to this plugin's own subscription before {@code post} returns. Neither may become or
	 * end the quest step: a followed route would otherwise offer its own walk back as Quest Helper's.
	 */
	@Test
	public void thisPluginsOwnPathsAndClearsAreNeverReadAsQuestHelpers()
	{
		List<PluginMessage> sent = new ArrayList<>();
		ShortestPath[] bus = new ShortestPath[1];
		ShortestPath shortestPath = new ShortestPath(message ->
		{
			sent.add(message);
			bus[0].onPluginMessage(message);
		});
		bus[0] = shortestPath;
		DestinationRoute route = new DestinationRoute();
		route.sync(DestinationRouteTest.destination("Al Kharid bank",
			DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid())));
		WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
		WorldPoint landing = new WorldPoint(3293, 3184, 0);
		WorldPoint[] drawn = new WorldPoint[1];
		int[] answered = {0};
		Runnable frame = () ->
		{
			/* Shortest Path's side, a frame later: take a path or a clear, and answer each question. */
			for (; answered[0] < sent.size(); answered[0]++)
			{
				PluginMessage message = sent.get(answered[0]);
				if (message.getName().equals("path"))
				{
					drawn[0] = (WorldPoint) message.getData().get("target");
				}
				else if (message.getName().equals("clear"))
				{
					drawn[0] = null;
				}
				else
				{
					Map<String, Object> data = new HashMap<>();
					data.put("id", message.getData().get("id"));
					data.put("set", drawn[0] != null);
					if (drawn[0] != null)
					{
						data.put("target", Collections.singletonList(drawn[0]));
					}
					shortestPath.onPluginMessage(new PluginMessage("shortestpath", "currentTarget", data));
				}
			}
		};
		int tick = 0;
		route.advance(lumbridge);
		shortestPath.tick(++tick, route.view(), true, lumbridge, () -> { });
		frame.run();
		shortestPath.onPluginMessage(questHelperPath(STEP));
		route.follow(true);
		route.advance(landing);
		shortestPath.tick(++tick, route.view(), true, landing, () -> { });
		frame.run();
		assertEquals("this plugin drew its walk", 1, sent.stream().filter(m -> m.getName().equals("path")).count());
		assertEquals("and its walk is not the quest's step", STEP, shortestPath.questStep());

		route.follow(false);
		for (int i = 0; i < 3; i++)
		{
			shortestPath.tick(++tick, route.view(), true, landing, () -> { });
			frame.run();
		}
		assertEquals("this plugin cleared its walk", 1, sent.stream().filter(m -> m.getName().equals("clear")).count());
		assertEquals("and its clear is not Quest Helper's", STEP, shortestPath.questStep());
		shortestPath.onPluginMessage(new PluginMessage("shortestpath", "clear"));
		assertNull("Quest Helper's own clear, equal to that one, still ends the step", shortestPath.questStep());
	}
}
