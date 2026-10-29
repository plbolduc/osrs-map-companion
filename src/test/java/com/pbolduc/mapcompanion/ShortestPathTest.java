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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What Shortest Path is told, and when — with a stand-in that answers {@code getTarget} the way its
 * {@code PluginMessageHandler} does: {@code currentTarget}, the caller's id, {@code set}, and the
 * targets as world points.
 *
 * <p>The route is the reply fixture's Al Kharid shape: a teleport, a walk to the stairs, a climb,
 * and a walk to the counter upstairs.
 */
public class ShortestPathTest
{
	/** Every message sent to Shortest Path, in order. */
	private final List<PluginMessage> sent = new ArrayList<>();
	private final ShortestPath shortestPath = new ShortestPath(sent::add);
	private final DestinationRoute route = new DestinationRoute();
	/** What Shortest Path is drawing, as its own state; null for nothing. */
	@Nullable
	private WorldPoint theirs;
	private boolean installed = true;
	private int tick;
	private int answered;

	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint LANDING = new WorldPoint(3293, 3184, 0);
	private static final WorldPoint STAIRS = new WorldPoint(3300, 3190, 0);

	private void receive()
	{
		route.sync(DestinationRouteTest.destination("Al Kharid bank",
			DestinationRouteTest.route("item:ring-of-dueling", DestinationRouteTest.alKharid())));
	}

	/** One game tick standing at {@code where}, then Shortest Path's answers, as the client delivers them. */
	private void tick(WorldPoint where)
	{
		route.advance(where);
		shortestPath.tick(++tick, route.view(), true, where, () -> route.stopFollowing(ShortestPath.TAKEN_OVER));
		answer();
	}

	/** Shortest Path's side: take what it was sent, and answer each question once. */
	private void answer()
	{
		for (; answered < sent.size(); answered++)
		{
			PluginMessage message = sent.get(answered);
			switch (message.getName())
			{
				case "path":
					theirs = (WorldPoint) message.getData().get("target");
					break;
				case "clear":
					theirs = null;
					break;
				case "getTarget":
					if (installed)
					{
						shortestPath.onPluginMessage(currentTarget(message.getData().get("id"), theirs));
					}
					break;
				default:
					throw new AssertionError("Shortest Path was sent " + message.getName());
			}
		}
	}

	private static PluginMessage currentTarget(Object id, @Nullable WorldPoint target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("set", target != null);
		if (target != null)
		{
			data.put("target", Collections.singletonList(target));
		}
		return new PluginMessage("shortestpath", "currentTarget", data);
	}

	private List<String> names()
	{
		List<String> names = new ArrayList<>();
		for (PluginMessage message : sent)
		{
			names.add(message.getName());
		}
		return names;
	}

	private List<PluginMessage> named(String name)
	{
		List<PluginMessage> out = new ArrayList<>();
		for (PluginMessage message : sent)
		{
			if (message.getName().equals(name))
			{
				out.add(message);
			}
		}
		return out;
	}

	@Test
	public void aDestinationThatMerelyArrivesIsNeverDrawn()
	{
		receive();
		for (int i = 0; i < 30; i++)
		{
			tick(i < 10 ? LUMBRIDGE : i < 20 ? LANDING : STAIRS);
		}
		assertTrue("a friend's send asks whether Shortest Path is there, and nothing else: " + names(),
			named("path").isEmpty() && named("clear").isEmpty());
		assertTrue(shortestPath.installed());
	}

	@Test
	public void aTeleportStepSendsNothing()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		for (int i = 0; i < 25; i++)
		{
			tick(LUMBRIDGE);
		}
		assertTrue("the current step is a teleport: " + names(), named("path").isEmpty() && named("clear").isEmpty());
	}

	@Test
	public void aWalkingStepSendsItsStartAndItsEndOnce()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		List<PluginMessage> paths = named("path");
		assertEquals(1, paths.size());
		Map<String, Object> data = paths.get(0).getData();
		assertEquals("shortestpath", paths.get(0).getNamespace());
		assertEquals(LANDING, data.get("start"));
		assertEquals(STAIRS, data.get("target"));
		assertEquals("start and target, and never config", 2, data.size());
		assertEquals(2, shortestPath.drawing());
		for (int i = 0; i < 20; i++)
		{
			tick(new WorldPoint(3294, 3185, 0));
		}
		assertEquals("one path for one step", 1, named("path").size());
	}

	@Test
	public void itClearsThePathItSetWhenTheWalkIsDone()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		assertEquals(STAIRS, theirs);
		tick(STAIRS);
		tick(STAIRS);
		assertEquals("the climb is no walk: the path to the stairs is taken down, and nothing else sent",
			Arrays.asList("getTarget", "path", "getTarget", "clear"), names().subList(names().size() - 4, names().size()));
		assertNull(theirs);
		assertEquals(1, named("path").size());
	}

	@Test
	public void itNeverClearsAPathItDidNotSet()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		/* The player picks a target of their own in Shortest Path. */
		WorldPoint own = new WorldPoint(3165, 3487, 0);
		theirs = own;
		for (int i = 0; i < ShortestPath.ASK_EVERY_TICKS + 2; i++)
		{
			tick(LANDING);
		}
		assertFalse("taken over, the route stops drawing there", route.view().following);
		assertEquals(ShortestPath.TAKEN_OVER, route.view().message);
		tick(STAIRS);
		tick(STAIRS);
		assertTrue(named("clear").isEmpty());
		assertEquals(own, theirs);
	}

	@Test
	public void anAnswerAboutThePathBeforeOursIsNotATakeover()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		/* The player's own target was showing when the question went; the answer lands after our path. */
		WorldPoint own = new WorldPoint(3165, 3487, 0);
		route.advance(LANDING);
		shortestPath.tick(++tick, route.view(), true, LANDING, () -> route.stopFollowing(ShortestPath.TAKEN_OVER));
		PluginMessage question = sent.get(0);
		shortestPath.onPluginMessage(currentTarget(question.getData().get("id"), own));
		assertTrue(route.view().following);
	}

	@Test
	public void withoutShortestPathNothingIsDrawn()
	{
		installed = false;
		receive();
		route.follow(true);
		for (int i = 0; i < 25; i++)
		{
			tick(i == 0 ? LUMBRIDGE : LANDING);
		}
		assertFalse(shortestPath.installed());
		assertTrue(names().toString(), named("path").isEmpty());
	}

	@Test
	public void turnedOffItAsksNothingAndTakesDownWhatItDrew()
	{
		receive();
		shortestPath.tick(++tick, route.view(), false, LUMBRIDGE, () -> { });
		assertTrue("off, with nothing drawn: not even a question", sent.isEmpty());

		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		assertEquals(STAIRS, theirs);
		shortestPath.tick(++tick, route.view(), false, LANDING, () -> { });
		answer();
		assertNull("turning it off takes down the path it drew", theirs);
	}

	@Test
	public void stoppingThePluginTakesDownItsPath()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		shortestPath.shutDown();
		answer();
		assertNull(theirs);
	}

	@Test
	public void reroutingTheSameStepReplacesItsChangedWaypoint()
	{
		route.sync(DestinationRouteTest.destination("Al Kharid bank", DestinationRouteTest.route("walk", Arrays.asList(
			DestinationRouteTest.step("walk-leg", "Walk to the stairs", true, 3300, 3190, 0),
			DestinationRouteTest.step("climb", "Climb", false, 3310, 3200, 1)))));
		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		assertEquals(STAIRS, theirs);

		DestinationRoute.Ticket ticket = route.beginReroute(0);
		assertNotNull(ticket);
		route.finishReroute(ticket, DestinationRouteTest.route("walk", Arrays.asList(
			DestinationRouteTest.step("walk-leg", "Walk to the other stairs", true, 3350, 3190, 0),
			DestinationRouteTest.step("climb", "Climb", false, 3310, 3200, 1))), null, 0);
		tick(LANDING);
		assertEquals(new WorldPoint(3350, 3190, 0), theirs);
		assertEquals(2, named("path").size());
	}

	@Test
	public void stoppingPreservesAPathThePlayerReplacedBetweenPolls()
	{
		receive();
		tick(LUMBRIDGE);
		route.follow(true);
		tick(LANDING);
		WorldPoint own = new WorldPoint(3165, 3487, 0);
		theirs = own;
		shortestPath.shutDown();
		answer();
		assertEquals(own, theirs);
		assertTrue(named("clear").isEmpty());
	}

	@Test
	public void shutdownCanCheckOwnershipAfterThePluginUnregisters()
	{
		net.runelite.client.eventbus.EventBus bus = new net.runelite.client.eventbus.EventBus();
		List<java.util.function.BooleanSupplier> timeouts = new ArrayList<>();
		ShortestPath real = new ShortestPath(bus, new net.runelite.client.callback.ClientThread()
		{
			@Override
			public void invokeLater(java.util.function.BooleanSupplier action)
			{
				timeouts.add(action);
			}
		});
		List<PluginMessage> posted = new ArrayList<>();
		List<PluginMessage> questions = new ArrayList<>();
		bus.register(PluginMessage.class, message ->
		{
			posted.add(message);
			if ("getTarget".equals(message.getName()))
			{
				questions.add(message);
			}
		}, 0);
		// During ordinary operation the plugin forwards the answers, then unregisters at shutdown.
		net.runelite.client.eventbus.EventBus.Subscriber forwarding = bus.register(PluginMessage.class, real::onPluginMessage, 0);
		receive();
		real.tick(1, route.view(), true, LUMBRIDGE, () -> { });
		bus.post(currentTarget(questions.get(0).getData().get("id"), null));
		route.follow(true);
		route.advance(LANDING);
		real.tick(2, route.view(), true, LANDING, () -> { });
		bus.unregister(forwarding);
		real.shutDown();
		assertEquals(1, timeouts.size());
		PluginMessage question = questions.get(questions.size() - 1);
		assertFalse(posted.stream().anyMatch(message -> "clear".equals(message.getName())));
		bus.post(currentTarget(question.getData().get("id"), STAIRS));
		assertEquals(1L, posted.stream().filter(message -> "clear".equals(message.getName())).count());
		bus.post(currentTarget(question.getData().get("id"), STAIRS));
		assertEquals("the temporary listener is removed after one answer", 1L,
			posted.stream().filter(message -> "clear".equals(message.getName())).count());
		assertTrue(timeouts.get(0).getAsBoolean());
	}
}
