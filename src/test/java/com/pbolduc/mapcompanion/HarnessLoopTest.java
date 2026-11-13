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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Holds the integration harness to the plugin: one tick, one logout and one resume, and both call
 * them.
 *
 * <p>The harness stands in for the game so the map's stories can run this plugin's real classes. It
 * cannot make the plugin itself, and a tick it wrote out again beside the plugin's would drift
 * from it with nothing to say so: a logout that ended three readers where the plugin ended
 * thirteen, a step of the tick left out. A story would then pass on something no player's client
 * does. So the tick lives in {@link CompanionLoop}, and this drives the real harness through its
 * own commands and asks the loop's readers what a tick and a logout did to them, then asks the
 * plugin to do the same through the events RuneLite raises.
 */
public class HarnessLoopTest
{
	private final Gson gson = new Gson();
	/* No group is joined, so nothing here asks the map anything; the address is never dialled. What it prints is not read. */
	private final HarnessMain harness = new HarnessMain("http://127.0.0.1:9", 0L, new PrintStream(new ByteArrayOutputStream()));

	public HarnessLoopTest() throws Exception
	{
	}

	/**
	 * A tick in the harness starts every reader's session, as the plugin's resume does, and a logout
	 * ends every one of them, as the plugin's does. The readers are found by their type, so one the
	 * harness's own code never names is still asked.
	 */
	@Test
	public void aLogoutInTheHarnessEndsEveryReaderThePluginEnds() throws Exception
	{
		long before = System.currentTimeMillis();
		say("{\"cmd\":\"login\",\"name\":\"Example\",\"world\":444,\"x\":3222,\"y\":3218}");
		assertEquals("no session has begun before a tick says the client is up", all(), unsettled(before));

		say("{\"cmd\":\"tick\",\"n\":2}");
		assertEquals("a tick resumes every reader", Collections.emptySet(), unsettled(before));

		say("{\"cmd\":\"logout\"}");
		assertEquals("and a logout ends every one", all(), unsettled(before));
	}

	/**
	 * The harness has no tick, logout or resume of its own to drift: its source names none of what
	 * only those steps touch. Writing one out here again means naming one of these.
	 */
	@Test
	public void theHarnessKeepsNoCopyOfTheTick() throws Exception
	{
		String source = new String(Files.readAllBytes(
			Paths.get("src", "harness", "java", "com", "pbolduc", "mapcompanion", "HarnessMain.java")), StandardCharsets.UTF_8);
		assertTrue("a tick command is the loop's tick", source.contains("loop.tick();"));
		assertTrue("and a logout is the loop's", source.contains("loop.gameState(GameState.LOGIN_SCREEN);"));
		for (String step : Arrays.asList("onSessionEnded", "onSessionResumed", "snapshotBuilder", "snapshotFingerprint",
			"relay.post(", "directiveStream.start(", "relay.isReady(", "farmRunRoute", "destinationRoute", "observePorts",
			"lastPostAtMs", "HEARTBEAT_MS"))
		{
			assertFalse("the harness does a step of the tick itself: " + step, source.contains(step));
		}
	}

	/** And the plugin hands RuneLite's tick and game state to the same loop, where the harness's go. */
	@Test
	public void thePluginHandsItsTickAndItsLogoutToTheSameLoop()
	{
		List<String> heard = new ArrayList<>();
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		plugin.loop = new CompanionLoop()
		{
			@Override
			void tick()
			{
				heard.add("tick");
			}

			@Override
			void gameState(GameState state)
			{
				heard.add(state.name());
			}
		};

		plugin.onGameTick(new GameTick());
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(event);

		assertEquals(Arrays.asList("tick", "LOGIN_SCREEN"), heard);
	}

	private void say(String command)
	{
		harness.handle(gson.fromJson(command, JsonObject.class));
	}

	/** The readers whose session is not running a minute after {@code from}: never begun, or ended. */
	private Set<String> unsettled(long from) throws Exception
	{
		Set<String> ended = new TreeSet<>();
		for (SettlingReader reader : held())
		{
			if (!reader.settle.settled(from + 60_000L))
			{
				ended.add(reader.getClass().getSimpleName());
			}
		}
		return ended;
	}

	private Set<String> all() throws Exception
	{
		Set<String> names = new TreeSet<>();
		for (SettlingReader reader : held())
		{
			names.add(reader.getClass().getSimpleName());
		}
		return names;
	}

	/** Every settling reader the harness's loop holds, by the type of its fields. */
	private List<SettlingReader> held() throws Exception
	{
		List<SettlingReader> readers = new ArrayList<>();
		for (Field field : CompanionLoop.class.getDeclaredFields())
		{
			if (SettlingReader.class.isAssignableFrom(field.getType()))
			{
				field.setAccessible(true);
				readers.add((SettlingReader) field.get(harness.loop));
			}
		}
		assertTrue("the loop's readers were not found", readers.size() > 10);
		return readers;
	}
}
