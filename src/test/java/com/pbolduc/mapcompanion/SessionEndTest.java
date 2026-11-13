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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What each way a session ends forgets, held to the one list the plugin's loop keeps of it.
 *
 * <p>A session ends four ways, {@link SessionEnd}'s, and they do not forget the same things. While
 * each ending kept a list of its own, a reader or a marker could be missing from one of them and
 * nothing said so: a death survived the plugin being turned off and was posted as the next
 * character's. So every reader the loop holds is asked here, after each ending, whether it was
 * told, and the death whether it is still remembered. The readers are found by their type, not
 * read from the list being checked, so one left out of the list is one this finds still counting.
 *
 * <p>The plugin is {@link TestSidebar}'s: the real one over the real readers, ended by the events
 * the client raises.
 */
public class SessionEndTest
{
	/** Any moment; the readers' clocks are started here and asked a minute later. */
	private static final long START = 1_000_000L;

	private final TestSidebar sidebar = new TestSidebar();
	private final MapCompanionPlugin plugin = sidebar.plugin;
	private final DeathWatcher deaths = sidebar.injector.getInstance(DeathWatcher.class);

	public SessionEndTest() throws Exception
	{
		for (SettlingReader reader : held())
		{
			reader.onSessionResumed(START);
		}
		assertEquals("every reader's session has settled before it is ended", Collections.emptySet(), unsettled());
		deaths.record(new ActorDeath(sidebar.game.client().getLocalPlayer()));
		assertNotNull(deaths.snapshot(System.currentTimeMillis()));
	}

	/**
	 * The list is every reader there is: each class that settles is in it, and so is each one the
	 * loop holds. A reader written and never listed is told of no session's start and no end.
	 */
	@Test
	public void theOneListNamesEveryReaderThatSettles() throws Exception
	{
		Set<String> listed = new TreeSet<>();
		for (SettlingReader reader : plugin.loop.readers())
		{
			listed.add(reader.getClass().getSimpleName());
		}
		assertEquals("every reader class in the source is in the loop's list", settlingClasses(), listed);
		assertEquals("and no reader is held beside the list", new ArrayList<>(held()).size(), plugin.loop.readers().length);
		assertTrue(Arrays.asList(plugin.loop.readers()).containsAll(held()));
	}

	/** The login screen: every stored value is about to read zero, and the death waits for the relog. */
	@Test
	public void aLogoutEndsEveryReadersSessionAndKeepsTheDeath() throws Exception
	{
		sidebar.game.logout();
		plugin.onGameStateChanged(state(GameState.LOGIN_SCREEN));

		assertEquals(all(), unsettled());
		assertNotNull("a relog is what the marker is for", deaths.snapshot(System.currentTimeMillis()));
	}

	/** A hop is told to every reader as well; what each keeps through it is its own to say. */
	@Test
	public void aHopEndsEveryReadersSessionAndKeepsTheDeath() throws Exception
	{
		sidebar.game.logout();
		plugin.onGameStateChanged(state(GameState.HOPPING));

		assertEquals(all(), unsettled());
		assertNotNull(deaths.snapshot(System.currentTimeMillis()));
	}

	/**
	 * Another character: the death goes, and every reader is told but three. The patches and the
	 * diaries are not told of a switch, and the death watcher forgets its death without restarting
	 * its clock; that is what each did before there was one list, kept as it was.
	 */
	@Test
	public void anotherCharacterTakesTheDeathAndEndsTheReaders() throws Exception
	{
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged(null, "a"));
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("a", "b"));

		assertEquals(except("FarmingObserver", "DiaryReader", "DeathWatcher"), unsettled());
		assertNull("a death is one character's", deaths.snapshot(System.currentTimeMillis()));
	}

	/** Turned off: the next lifetime may be anybody's, so the death goes here too. */
	@Test
	public void turningThePluginOffTakesTheDeathAndEndsTheReaders() throws Exception
	{
		plugin.shutDown();

		assertEquals(except("DeathWatcher"), unsettled());
		assertNull(deaths.snapshot(System.currentTimeMillis()));
	}

	/** The readers whose session is not running a minute on: the ones an ending was told to. */
	private Set<String> unsettled() throws Exception
	{
		Set<String> ended = new TreeSet<>();
		for (SettlingReader reader : held())
		{
			if (!reader.settle.settled(START + 60_000L))
			{
				ended.add(reader.getClass().getSimpleName());
			}
		}
		return ended;
	}

	private Set<String> all() throws Exception
	{
		return except();
	}

	private Set<String> except(String... kept) throws Exception
	{
		Set<String> names = new TreeSet<>();
		for (SettlingReader reader : held())
		{
			names.add(reader.getClass().getSimpleName());
		}
		names.removeAll(Arrays.asList(kept));
		return names;
	}

	/** Every settling reader the loop holds, by the type of its fields and not by its own list. */
	private List<SettlingReader> held() throws Exception
	{
		List<SettlingReader> readers = new ArrayList<>();
		for (Field field : CompanionLoop.class.getDeclaredFields())
		{
			if (SettlingReader.class.isAssignableFrom(field.getType()))
			{
				field.setAccessible(true);
				readers.add((SettlingReader) field.get(plugin.loop));
			}
		}
		assertTrue("the loop's readers were not found", readers.size() > 10);
		return readers;
	}

	/** Each class in the plugin's source that says it settles. */
	private static Set<String> settlingClasses() throws IOException
	{
		try (Stream<Path> files = Files.list(Paths.get("src", "main", "java", "com", "pbolduc", "mapcompanion")))
		{
			return files.filter(file -> file.toString().endsWith(".java")).filter(SessionEndTest::settles)
				.map(file -> file.getFileName().toString().replace(".java", ""))
				.collect(Collectors.toCollection(TreeSet::new));
		}
	}

	private static boolean settles(Path file)
	{
		try
		{
			return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(" extends SettlingReader");
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static GameStateChanged state(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}
}
