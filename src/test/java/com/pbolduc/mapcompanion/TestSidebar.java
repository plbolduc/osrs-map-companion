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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertTrue;

/**
 * The whole sidebar, built the way the plugin builds it: {@link MapCompanionPanel}'s own injected
 * constructor, handed the real cards, clients and readers over a fake game.
 *
 * <p>Most tests build one card, which is enough for what a card says. What the sidebar does as a
 * whole, which card a key presses and what stops with the plugin, is only in the panel that holds
 * them, and its constructor is the injector's alone. So this assembles the collaborators as
 * {@code HarnessMain} does and calls that constructor by reflection.
 *
 * <p>Nothing leaves the machine: every request the cards could make fails before it is sent. The
 * search's debounce is a list here, as in {@code SearchClientTest}, so a test can see what is
 * still waiting to be asked.
 */
final class TestSidebar
{
	static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	final Gson gson = new Gson();
	final HarnessGame game = new HarnessGame();
	final HarnessStore store = new HarnessStore();
	final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	final Pairing pairing = new Pairing(store, gson);
	final MemberIdentity identity = new MemberIdentity(store);
	/** Every request a card tried to make. None is answered. */
	final List<String> asked = new ArrayList<>();
	/** The search's waiting actions; cancelling one takes it out. */
	final List<Runnable> waiting = new ArrayList<>();
	final Injector injector;
	final MapCompanionPanel panel;

	TestSidebar() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			synchronized (asked)
			{
				asked.add(chain.request().url().encodedPath());
			}
			throw new IOException("a test's sidebar asks the map nothing");
		}).build();
		ClientThread thread = new ClientThread()
		{
			@Override
			public void invoke(Runnable action)
			{
				action.run();
			}

			@Override
			public void invokeLater(Runnable action)
			{
				action.run();
			}
		};
		SearchClient search = new SearchClient(http, gson, () -> pairing.signed(identity.id()), (action, delayMs) ->
		{
			waiting.add(action);
			return () -> waiting.remove(action);
		}, Runnable::run);
		injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(game.client());
				bind(ClientThread.class).toInstance(thread);
				bind(Gson.class).toInstance(gson);
				bind(OkHttpClient.class).toInstance(http);
				/* Starts no thread until something is scheduled, and nothing here schedules. */
				bind(ScheduledExecutorService.class).toInstance(new ScheduledThreadPoolExecutor(1));
				bind(MapCompanionConfig.class).toInstance(config);
				bind(ConfigStore.class).toInstance(store);
				bind(MemberIdentity.class).toInstance(identity);
				bind(Pairing.class).toInstance(pairing);
				bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
				bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
				bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
				/* The four whose injected constructors want a running RuneLite, by their test constructors. */
				bind(StarsClient.class).toInstance(new StarsClient(http, gson, game.client(), thread, config, pairing, identity, store));
				bind(ShortestPath.class).toInstance(new ShortestPath(message -> { }));
				bind(GameSprites.class).toInstance(new GameSprites(null, null, null));
				bind(SearchClient.class).toInstance(search);
			}
		});
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		Constructor<?> make = MapCompanionPanel.class.getDeclaredConstructors()[0];
		make.setAccessible(true);
		Class<?>[] wanted = make.getParameterTypes();
		Object[] given = new Object[wanted.length];
		for (int i = 0; i < wanted.length; i++)
		{
			/* No item pictures: drop luck asks for one only for a row, and this log has none. */
			given[i] = wanted[i] == MapCompanionPlugin.class ? plugin
				: wanted[i] == ItemManager.class ? null
				: injector.getInstance(wanted[i]);
		}
		MapCompanionPanel[] built = new MapCompanionPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				built[0] = (MapCompanionPanel) make.newInstance(given);
			}
			catch (ReflectiveOperationException e)
			{
				throw new IllegalStateException(e);
			}
		});
		panel = built[0];
		settle();
	}

	/** Lets everything the sidebar queued on the event thread run, its own refresh included. */
	static void settle() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}
}
