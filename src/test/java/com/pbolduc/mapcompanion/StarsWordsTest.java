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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the Stars tab says about its own list: that it is loading, how old it is, why it is empty,
 * and how long a star has left. Each said something untrue before: "log in" to a player who was,
 * "0 sites · 0 stars" over a list still on its way, a stale list with no age, the player's filters
 * blamed for an hour with no stars, and "Depleted left".
 *
 * <p>The feed is the website's own projection, answered by an interceptor, and the game is a fake
 * that is logged in with Mining 72 and a total of 1620.
 */
public class StarsWordsTest
{
	private static final MediaType JSON = MediaType.get("application/json");

	private final HarnessStore store = new HarnessStore();
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationState destinations = new DestinationState(new Gson());
	/** Let go at the end of every test, so no reply is left waiting on OkHttp's thread. */
	private final CountDownLatch answer = new CountDownLatch(1);
	private StarsClient service;
	private StarsPanel panel;

	@After
	public void letTheReplyGo()
	{
		answer.countDown();
	}

	@Test
	public void theFirstLoadSaysLoadingAndNeitherCountsNorDatesAListItHasNot() throws Exception
	{
		scene(fixture(), true);
		open();
		SwingUtilities.invokeAndWait(() ->
		{
			assertTrue("logged in and connected", service.eligible());
			panel.refresh();
			List<String> words = words(panel);
			assertTrue(words.toString(), words.contains("Loading stars…"));
			for (String word : words)
			{
				assertFalse("nobody logged in is told to log in: " + word, word.toLowerCase().contains("log"));
			}
			assertEquals("no count of a list not yet here", "", text(named(panel, "stars.count")));
			assertEquals("and no age for it", "", text(named(panel, "stars.updated")));
			assertFalse(named(panel, "stars.status").isVisible());
		});
	}

	@Test
	public void aHeldListSaysHowOldItIsWhenStarMinersIsDownOrARefreshFails() throws Exception
	{
		String stale = fixture().replace("\"stale\": false", "\"stale\": true").replace("\"snapshotAgeSeconds\": 12", "\"snapshotAgeSeconds\": 840");
		scene(stale, false);
		open();
		until(() -> service.model.feed != null);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			/*
			 * One line said both, "Updated 14m ago · Star Miners not answering". The age shares the
			 * count's line now, and what is wrong has the line under it to itself.
			 */
			JLabel status = (JLabel) named(panel, "stars.status");
			assertEquals("Updated 14m ago", text(named(panel, "stars.updated")));
			assertEquals("Star Miners not answering", text(status));

			/* What a failed refresh leaves, with the list it kept. */
			service.message = "Stars unavailable. Retrying shortly.";
			panel.refresh();
			assertEquals("Updated 14m ago", text(named(panel, "stars.updated")));
			assertEquals("Couldn't refresh, retrying", text(status));
			assertEquals("and why, on a hover", "Stars unavailable. Retrying shortly.", status.getToolTipText());
		});
	}

	/**
	 * A list with nothing wrong has one line over it: how old it is, and how much of it shows. It had
	 * two, "Most time left first" beside the count and "Updated just now · estimates" under them. The
	 * order is the column of times down the list's right edge, and every time is written "~46m".
	 */
	@Test
	public void aListWithNothingWrongHasOneLineOverIt() throws Exception
	{
		scene(fixture(), false);
		open();
		until(() -> service.model.feed != null);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			assertEquals("Updated just now", text(named(panel, "stars.updated")));
			assertEquals("on the count's line", named(panel, "stars.count").getParent(), named(panel, "stars.updated").getParent());
			assertFalse("and no line for trouble there is none of", named(panel, "stars.status").isVisible());
			for (String word : words(panel))
			{
				assertFalse(word, word.contains("first") || word.contains("estimates"));
			}
		});
	}

	/**
	 * Why there is no list is said in the refusal pink, since the tab has nothing else to show. A held
	 * list and how old it is stay grey, even when its last refresh failed: the list is still there.
	 */
	@Test
	public void noListIsSaidInPinkAndAHeldListsAgeIsNot() throws Exception
	{
		scene(fixture(), true);
		open();
		SwingUtilities.invokeAndWait(() ->
		{
			service.message = "The map is unreachable.";
			panel.refresh();
			JLabel status = (JLabel) named(panel, "stars.status");
			assertEquals("The map is unreachable.", text(status));
			assertEquals(MapCompanionPanel.REFUSED, status.getForeground());

			service.model.accept(new Gson().fromJson(fixture(), StarsModel.Feed.class));
			panel.refresh();
			assertEquals("Updated just now", text(named(panel, "stars.updated")));
			assertEquals("Couldn't refresh, retrying", text(status));
			assertEquals(ColorScheme.LIGHT_GRAY_COLOR, status.getForeground());
		});
	}

	/** Logged out, the tab says the one thing to do. It said to log in and connect a map the player is in. */
	@Test
	public void loggedOutTheTabSaysToLogIn() throws Exception
	{
		playing = GameState.LOGIN_SCREEN;
		scene(fixture(), false);
		open();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			assertEquals("Log in to see stars.", text(named(panel, "stars.status")));
		});
	}

	@Test
	public void anHourWithNoStarsIsNotThePlayersFilters() throws Exception
	{
		scene(fixture(), false);
		SwingUtilities.invokeAndWait(() ->
		{
			StarsModel.Feed none = new Gson().fromJson(fixture(), StarsModel.Feed.class);
			none.rows = new ArrayList<>();
			service.model.accept(none);
			panel.refresh();
			assertTrue(words(panel).contains("No stars reported right now."));
			assertFalse(words(panel).contains("No stars match these filters."));

			service.model.accept(new Gson().fromJson(fixture(), StarsModel.Feed.class));
			service.model.preferences.filters.minTier = 9;
			panel.refresh();
			assertTrue("T9+ hides the stars there are", words(panel).contains("No stars match these filters."));
			assertFalse(words(panel).contains("No stars reported right now."));
		});
	}

	@Test
	public void timeLeftIsSaidOneWayAndADepletedStarHasNone() throws Exception
	{
		assertEquals("~46m left", StarsModel.timeLeft(46 * 60_000L));
		assertEquals("Depleted", StarsModel.timeLeft(0));
		assertEquals("Depleted", StarsModel.timeLeft(-30_000L));
		scene(fixture(), false);
		SwingUtilities.invokeAndWait(() ->
		{
			StarsModel.Feed feed = new Gson().fromJson(fixture(), StarsModel.Feed.class);
			for (StarsModel.Star star : feed.rows)
			{
				if (star.world == 306)
				{
					star.remainingMs = -30_000L;
				}
			}
			service.model.accept(feed);
			service.model.preferences.filters.hideDepleted = false;
			panel.refresh();
			/* The star's own card says it: the picked world's line over the tab, which this read, is gone. */
			List<String> words = words(panel);
			assertTrue("the star's card says it plainly: " + words, words.contains("Depleted"));
			for (String word : words)
			{
				assertFalse(word, word.contains("Depleted left"));
			}
		});
	}

	/**
	 * A star on the world the player is on needs no hop, so its world is drawn in the Group tab's green
	 * for one; every other world is white. The world is the game's own, read with the levels.
	 */
	@Test
	public void theWorldThePlayerIsOnIsGreen() throws Exception
	{
		scene(fixture(), false);
		open();
		until(() -> service.model.feed != null);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			assertEquals(Integer.valueOf(302), service.model.playerWorld);
			assertEquals(StarsPanel.HERE, within(named(panel, "stars.site.tile:2974,3241"), "star.world").getForeground());
			assertEquals("W302", ((JLabel) within(named(panel, "stars.site.tile:2974,3241"), "star.world")).getText());
			assertEquals(Color.WHITE, within(named(panel, "stars.site.tile:3296,3298"), "star.world").getForeground());
		});
	}

	private static Component within(Component parent, String name)
	{
		return named((Container) parent, name);
	}

	@Test
	public void moreFiltersSaysWhatTheLevelsHideAndWhatEachChoiceShows() throws Exception
	{
		assertEquals("For Mining 72: up to T7 · worlds up to 1620 total", StarsPanel.levelsLine(72, 1620));
		assertEquals("For Mining 70: up to T7", StarsPanel.levelsLine(70, null));
		assertEquals("For Mining 95: every tier", StarsPanel.levelsLine(95, null));
		assertEquals("For Mining 5: T1 needs 10", StarsPanel.levelsLine(5, null));
		assertEquals("Worlds up to 1620 total", StarsPanel.levelsLine(null, 1620));

		/*
		 * This also asserted the League dropdown's two choices. The list now follows the world the
		 * player is on, so there is no choice to word; what a League or Hardcore world says is the
		 * line over the list, asserted in the two tests below.
		 */
		scene(fixture(), false);
		open();
		until(() -> service.model.feed != null);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			((JToggleButton) named(panel, "stars.filters")).doClick();
			assertEquals("the levels the game read", "For Mining 72: up to T7 · worlds up to 1620 total",
				text(named(panel, "stars.levels")));
			assertTrue("no League choice left under More filters", named(panel, "stars.league") == null);
		});
	}

	/**
	 * On a main world the list is the main game's and says nothing about it. The fixture's world 640
	 * is a Hardcore World: a star a main-game character cannot reach, so it is never offered.
	 */
	@Test
	public void aMainWorldListsTheMainGameAndNeverAHardcoreStar() throws Exception
	{
		scene(fixture(), false);
		open();
		until(() -> service.model.feed != null);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			assertFalse("no game line on the main game", named(panel, "stars.game").isVisible());
			assertTrue(service.model.sites().stream().flatMap(site -> site.worlds.stream()).noneMatch(star -> star.world == 640));
		});
	}

	/** On a Hardcore World only its stars are listed, and the gold line over them says whose they are. */
	@Test
	public void aHardcoreWorldListsOnlyItsStarsAndSaysSo() throws Exception
	{
		worldTypes = EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN);
		scene(fixture(), false);
		open();
		until(() -> service.model.feed != null && StarsModel.HARDCORE.equals(service.model.playerGame));
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			assertTrue(named(panel, "stars.game").isVisible());
			assertEquals("Hardcore Worlds", text(named(panel, "stars.game")));
			List<StarsModel.Site> sites = service.model.sites();
			assertEquals(1, sites.size());
			assertEquals(640, sites.get(0).worlds.get(0).world);
		});
	}

	/** With no Hardcore star up, the empty list is the game's, not the filters'. */
	@Test
	public void aHardcoreWorldWithNoStarSaysNoneHasBeenCalled() throws Exception
	{
		worldTypes = EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN);
		scene(fixture().replace("\"game\": \"hardcore\"", "\"game\": \"main\""), false);
		open();
		until(() -> service.model.feed != null && StarsModel.HARDCORE.equals(service.model.playerGame));
		SwingUtilities.invokeAndWait(() ->
		{
			panel.refresh();
			assertTrue(words(panel).contains("No star has been called on Hardcore Worlds yet."));
			assertFalse(words(panel).contains("No stars match these filters."));
		});
	}

	/**
	 * The tab, its client and the fixture behind them. {@code hold} keeps the feed's reply waiting
	 * until the test ends, so the tab can be looked at while its first answer is on its way.
	 */
	/** The game's state in the scene: logged in, unless a test is about the login screen. */
	private GameState playing = GameState.LOGGED_IN;
	/** The world's type in the scene: an ordinary members world, unless a test hops elsewhere. */
	private EnumSet<WorldType> worldTypes = EnumSet.of(WorldType.MEMBERS);

	private void scene(String feed, boolean hold)
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			if (hold)
			{
				hold(answer);
			}
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
				.body(ResponseBody.create(JSON, feed)).build();
		}).build();
		Client game = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return playing;
					case "getRealSkillLevel":
						return 72;
					case "getTotalLevel":
						return 1620;
					case "getWorld":
						return 302;
					case "getWorldType":
						return worldTypes;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		ClientThread now = new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		};
		service = new StarsClient(http, new Gson(), game, now, new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL),
			pairing, identity, store);
		RoutePress press = new RoutePress(http, new Gson(), game, now, pairing, identity, new DestinationRoute(), destinations,
			new TestDestinationMarker());
		panel = new StarsPanel(service, destinations, now, new TestDestinationMarker(), press, () -> MapCompanionConfig.DEFAULT_MAP_URL,
			new DestinationRoute(), null, null, null, rerouting -> null, () -> { });
	}

	/** The sidebar open on Stars: the client asks the game, and hands its answer back to Swing. */
	private void open() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			panel.selected(true);
			panel.active(true);
		});
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static String fixture()
	{
		try (InputStream in = StarsWordsTest.class.getResourceAsStream("/companion-stars.sample.json"))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static void hold(CountDownLatch answer) throws IOException
	{
		try
		{
			if (!answer.await(10, TimeUnit.SECONDS))
			{
				throw new InterruptedIOException("the test never let the reply go");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("interrupted while holding the reply");
		}
	}

	/** Asked on the event thread, which is the one that writes what is asked about. */
	private static void until(Supplier<Boolean> condition) throws Exception
	{
		long deadline = System.currentTimeMillis() + 3_000;
		boolean[] met = new boolean[1];
		while (System.currentTimeMillis() < deadline)
		{
			SwingUtilities.invokeAndWait(() -> met[0] = condition.get());
			if (met[0])
			{
				return;
			}
			Thread.sleep(5);
		}
		assertTrue("the answer landed", met[0]);
	}

	/** Every line the tab shows, in order, as a player reads it. */
	private static List<String> words(Container root)
	{
		List<String> found = new ArrayList<>();
		collect(root, found);
		return found;
	}

	private static void collect(Container parent, List<String> found)
	{
		for (Component child : parent.getComponents())
		{
			if (!child.isVisible())
			{
				continue;
			}
			if (child instanceof JLabel || child instanceof AbstractButton)
			{
				String text = text(child);
				if (!text.isEmpty())
				{
					found.add(text);
				}
			}
			if (child instanceof Container)
			{
				collect((Container) child, found);
			}
		}
	}

	private static String text(Component component)
	{
		String raw = component instanceof JLabel ? ((JLabel) component).getText() : ((AbstractButton) component).getText();
		return raw == null ? "" : raw.replaceAll("<br>", " ").replaceAll("<[^>]+>", "").replace("&amp;", "&").trim();
	}

	@Nullable
	private static Component named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
