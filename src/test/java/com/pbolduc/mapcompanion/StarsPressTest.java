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
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * One press on a star pins it and asks the way there. The pin is this client's own and is made on
 * the press. The way is one of the member's routes on the map, so what is pinned here is how few
 * are asked for: none before the star has stayed the destination for the dwell, none sooner than
 * the gate every Route shares allows, one for the star stopped on and none for those passed over,
 * and never a second for a refusal.
 *
 * <p>Driven through the tab's own buttons and a real {@link RoutePress}, whose calls an interceptor
 * answers, so what is counted is what would have left the machine. The dwell is a list the test
 * runs, where the client has Swing's timer.
 */
public class StarsPressTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String RIMMINGTON = "stars.site.tile:2974,3241";
	private static final String CRAFTING = "stars.site.tile:2940,3280";
	private static final String AL_KHARID = "stars.site.tile:3296,3298";
	/** A site whose tile is a guess: its card offers the website, and the star is never pinned. */
	private static final String MINING_GUILD = "stars.site.label:mining guild";

	private static final String TABLET = "{\"id\":\"tablet\",\"label\":\"Rimmington teleport\",\"best\":true,\"blocked\":false,"
		+ "\"tiles\":41,\"steps\":[{\"slot\":\"method\",\"title\":\"Break a Rimmington teleport\",\"walk\":false,"
		+ "\"at\":{\"x\":2954,\"y\":3224,\"plane\":0}},{\"slot\":\"arrival-walk\",\"title\":\"Walk 29 tiles east\",\"walk\":true,"
		+ "\"at\":{\"x\":2974,\"y\":3241,\"plane\":0}}]}";
	private static final String WALK = "{\"id\":\"walk\",\"label\":\"Walk\",\"best\":false,\"blocked\":false,\"tiles\":310,"
		+ "\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk 310 tiles\",\"walk\":true,\"at\":{\"x\":2974,\"y\":3241,\"plane\":0}}]}";
	/** The map's answer to Route for Rimmington mine: a tablet, which is the best way, and the walk. */
	private static final String TWO_WAYS = "{\"target\":{\"x\":2974,\"y\":3241,\"plane\":0,\"name\":null},\"origin\":\"request\","
		+ "\"cached\":false,\"options\":[" + TABLET + "," + WALK + "]}";

	/** The press is the pin: the destination, the marker and the head of the tab, with nothing sent for it. */
	@Test
	public void aPressPinsTheStarAtOnceAndSendsNothingBeforeTheDwell() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			assertNull(tab.destinations.getDestination());
			tab.press(RIMMINGTON);

			RelayResponse.Destination pin = tab.destinations.getDestination();
			assertNotNull("pinned by the press", pin);
			assertTrue("as this client's own destination", tab.destinations.hasLocalDestination());
			assertEquals("Rimmington mine", pin.name);
			assertEquals(Integer.valueOf(302), pin.world);
			assertEquals(2974, pin.x);
			assertEquals(3241, pin.y);
			assertNull("with no way yet", pin.route);
			assertNull("and no note that would age", pin.note);
			assertEquals("the world map's marker and the arrow are told", 1, tab.synced.get());

			Container head = head(tab);
			assertTrue("it heads the tab straight away: " + words(head), words(head).containsAll(Arrays.asList("Pinned destination", "W302")));
			AbstractButton route = (AbstractButton) named(head, "stars.route");
			assertEquals("the way is being asked for", "Planning…", route.getText());
			assertFalse(route.isEnabled());
			assertNotNull(named(head, "stars.unpin"));
			assertNull("no Pin anywhere: the press was it", named(tab.stars, "stars.pin"));
			assertNull("and no Route in the star's card", named(card(tab.stars, RIMMINGTON), "stars.route"));

			assertEquals("one dwell", 1, tab.dwells.size());
			assertEquals("of 700ms", 700L, tab.dwells.get(0)[1]);
			tab.stars.refresh();
			tab.stars.refresh();
			assertTrue("and nothing is asked while it runs", tab.sent.isEmpty());
			assertNull("or said", tab.press.last());
		});
	}

	/** The dwell over, one question goes: Route's own, the star's tile and the tile the player stands on. */
	@Test
	public void oneQuestionGoesWhenTheDwellEnds() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			tab.press(RIMMINGTON);
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			assertEquals(1, tab.sent.size());
			Request request = tab.sent.get(0);
			assertEquals("Route's own request", RoutePress.PATH, request.url().encodedPath());
			assertEquals("POST", request.method());
			JsonObject question = body(request);
			assertEquals("the star's tile", "{\"x\":2974,\"y\":3241,\"plane\":0}", question.get("target").toString());
			assertEquals("from where the player stands, as Route sends it", "{\"x\":3222,\"y\":3218,\"plane\":0}",
				question.get("from").toString());
			assertEquals(2, question.size());

			tab.stars.refresh();
			tab.stars.refresh();
			assertTrue("no dwell waits to ask again", tab.dwells.isEmpty());
			assertEquals("and the seconds after ask nothing more", 1, tab.sent.size());
		});
	}

	/** Pressing down the list: each press pins, and ends the dwell before it, so only the star stopped on is asked about. */
	@Test
	public void pressingThroughThreeStarsPinsEachAndAsksOnceForTheLast() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			tab.press(RIMMINGTON);
			assertEquals(Integer.valueOf(302), tab.destinations.getDestination().world);
			tab.press(CRAFTING);
			assertEquals("the second press replaces the first pin", Integer.valueOf(301), tab.destinations.getDestination().world);
			tab.press(AL_KHARID);
			assertEquals(Integer.valueOf(306), tab.destinations.getDestination().world);
			assertEquals("each was pinned on the map too", 3, tab.synced.get());
			assertEquals("the dwells of the stars passed over were stopped", 1, tab.dwells.size());
			assertTrue("and nothing was asked for them", tab.sent.isEmpty());
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			assertEquals(1, tab.sent.size());
			assertEquals("Al Kharid mine, the star stopped on", "{\"x\":3296,\"y\":3298,\"plane\":0}",
				body(tab.sent.get(0)).get("target").toString());
		});
	}

	/**
	 * A press inside the wait the map named pins all the same. Its question waits out the wait, and
	 * goes when the gate opens, for the star still pinned then. A question still out shuts the gate
	 * too, with no wait to count: the question looks again and goes once the other has landed.
	 */
	@Test
	public void withTheGateShutTheQuestionGoesWhenItOpens() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			tab.route.finishPress(System.currentTimeMillis() + 20_000L);
			tab.press(RIMMINGTON);
			assertEquals("pinned inside the wait", Integer.valueOf(302), tab.destinations.getDestination().world);
			assertEquals(1, tab.dwells.size());
			assertTrue("its question no sooner than the map allows: " + tab.dwells.get(0)[1], (long) tab.dwells.get(0)[1] > 15_000L);

			/* Run early, as a timer never is: the gate is still shut, so nothing goes and the question waits on. */
			tab.dwell();
			assertTrue(tab.sent.isEmpty());
			assertEquals("the same one question, waiting again", 1, tab.dwells.size());
			assertTrue((long) tab.dwells.get(0)[1] > 15_000L);
			assertEquals("Planning…", ((AbstractButton) named(head(tab), "stars.route")).getText());
			assertNull("and nothing is said of a question not yet asked", tab.press.last());

			tab.openGate();
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			assertEquals("asked once the gate opened", 1, tab.sent.size());
			assertEquals("{\"x\":2974,\"y\":3241,\"plane\":0}", body(tab.sent.get(0)).get("target").toString());
		});

		Tab busy = tab();
		edt(() ->
		{
			assertNull("another question is out", busy.route.beginPress(System.currentTimeMillis()));
			busy.press(CRAFTING);
			assertEquals(700L, busy.dwells.get(0)[1]);
			busy.dwell();
			assertTrue("one question at a time", busy.sent.isEmpty());
			assertEquals("so this one waits", 1, busy.dwells.size());

			busy.route.finishPress(0L);
			busy.dwell();
		});
		busy.answered();
		edt(() -> assertEquals("{\"x\":2940,\"y\":3280,\"plane\":0}", body(busy.sent.get(0)).get("target").toString()));
	}

	/** The question is the pin's: unpinned, or replaced by a place from the website, before the dwell ends, nothing is asked. */
	@Test
	public void nothingIsAskedWhenTheDestinationChangedDuringTheDwell() throws Exception
	{
		Tab unpinned = tab();
		edt(() ->
		{
			unpinned.press(RIMMINGTON);
			click(unpinned.stars, "stars.unpin");
			assertNull(unpinned.destinations.getDestination());
			unpinned.dwell();
		});
		Tab replaced = tab();
		edt(() ->
		{
			replaced.press(RIMMINGTON);
			RelayResponse.Directive sent = new RelayResponse.Directive();
			sent.destination = new RelayResponse.Destination();
			sent.destination.x = 3212;
			sent.destination.y = 3424;
			sent.destination.name = "Varrock square";
			replaced.destinations.accept(sent);
			assertEquals("Varrock square", replaced.destinations.getDestination().name);
			replaced.dwell();
			assertEquals("the place sent stays", "Varrock square", replaced.destinations.getDestination().name);
		});
		for (Tab tab : Arrays.asList(unpinned, replaced))
		{
			edt(() ->
			{
				tab.stars.refresh();
				assertTrue("nothing left the machine", tab.sent.isEmpty());
				assertTrue("and nothing waits to", tab.dwells.isEmpty());
				assertNull("no question was handed over at all", tab.press.last());
			});
		}
	}

	/**
	 * A question bound to a pin is not sent once that pin has gone, whoever asks it: the press's own
	 * after its dwell, or Route beside Unpin pressed as the destination changed under it.
	 */
	@Test
	public void aQuestionForAPinSinceReplacedIsNotSent() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			RoutePress.Target star = RoutePress.Target.tile(StarsPanel.SOURCE, new WorldPoint(2974, 3241, 0), "Rimmington mine", 302, null);
			RelayResponse.Destination gone = RoutePress.destination(star, null, null);
			tab.press.follow(star.pinned(gone));
			assertTrue(tab.sent.isEmpty());
			assertFalse("and it is not left planning", tab.press.said(StarsPanel.SOURCE).planning);
			assertNull(tab.press.said(StarsPanel.SOURCE).message);
			tab.press.press(star.pinned(gone));
			assertTrue(tab.sent.isEmpty());
			assertTrue("the gate was never taken", tab.press.ready());

			/* The same question while the pin stands is sent. */
			tab.destinations.selectLocal(gone);
			tab.press.follow(star.pinned(gone));
		});
		tab.answered();
		edt(() -> assertEquals(1, tab.sent.size()));
	}

	/**
	 * Where no route can be asked, nobody logged in or no member the map takes, the press is the pin
	 * Pin gave: the star heads the tab, nothing is sent, and no refusal is said for a question that
	 * nobody pressed Route for. Route beside Unpin is there, and says why if it is pressed.
	 */
	@Test
	public void whereNoRouteCanBeAskedTheStarIsPinnedAndNothingIsSentOrSaid() throws Exception
	{
		Tab loggedOut = tab(false, true);
		Tab unpaired = tab(true, false);
		for (Tab tab : Arrays.asList(loggedOut, unpaired))
		{
			edt(() ->
			{
				tab.press(RIMMINGTON);
				assertEquals("pinned", Integer.valueOf(302), tab.destinations.getDestination().world);
				assertEquals(1, tab.synced.get());
				tab.dwell();
				tab.stars.refresh();

				assertTrue("nothing left the machine", tab.sent.isEmpty());
				assertTrue("and nothing waits to", tab.dwells.isEmpty());
				List<String> said = words(tab.stars);
				assertFalse(said.toString(), said.contains(RoutePress.LOG_IN) || said.contains(RoutePress.NOT_CONNECTED));
				RoutePress.Said last = tab.press.last();
				assertTrue("no refusal for a question never sent", last == null || !last.refused());
				assertEquals("still pinned", Integer.valueOf(302), tab.destinations.getDestination().world);
				AbstractButton route = (AbstractButton) named(head(tab), "stars.route");
				assertEquals("and not left saying it is planning", "Route", route.getText());
				assertTrue(route.isEnabled());
			});
		}
		edt(() ->
		{
			click(head(loggedOut), "stars.route");
			loggedOut.stars.refresh();
			assertTrue("a press of Route is told why", words(head(loggedOut)).contains(RoutePress.LOG_IN));
			assertTrue(loggedOut.sent.isEmpty());
		});
	}

	/** The answer lands as a Route press's did: the way under the pinned star, its chips and steps, and followed. */
	@Test
	public void theAnswerIsDrawnUnderThePinnedStarAndFollowed() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			tab.press(RIMMINGTON);
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			tab.stars.refresh();
			RelayResponse.Destination pinned = tab.destinations.getDestination();
			assertEquals("still the star", Integer.valueOf(302), pinned.world);
			assertEquals("Rimmington mine", pinned.name);
			assertNotNull("with its way now", pinned.route);
			assertTrue(tab.destinations.hasLocalDestination());
			DestinationRoute.View way = tab.route.view();
			assertEquals(DestinationRoute.keyOf(pinned), way.key);
			assertEquals("the best way", "tablet", way.way().id);
			assertTrue("followed, as a pressed Route is", way.following);
			assertEquals("the marker is told of the pin and of the way", 2, tab.synced.get());
			assertEquals("and the card is this press's", DestinationRoute.keyOf(pinned), tab.press.said(StarsPanel.SOURCE).key);

			Container head = head(tab);
			RouteStepsView steps = steps(head);
			assertNotNull("the way under the pinned star", steps);
			assertTrue(steps.isVisible());
			assertTrue("a chip a way: " + words(steps), words(steps).containsAll(Arrays.asList("Rimmington teleport", "Walk")));
			assertTrue(words(steps).toString(), words(steps).stream().anyMatch(line -> line.contains("Break a Rimmington teleport")));
			assertNull("Route gives way to the way", named(head, "stars.route"));
			assertNotNull(named(head, "stars.unpin"));
			assertEquals("one request did it", 1, tab.sent.size());
		});
	}

	/**
	 * A refusal is said under the pinned star, with its wait, and that is the end of it: the star stays
	 * pinned, nothing waits to ask again, and the seconds after send nothing. Route beside Unpin waits
	 * out the map's wait as after any refusal, and is the player's own second question.
	 */
	@Test
	public void aRefusalIsSaidUnderThePinnedStarIsNotRetriedAndRouteAsksAgain() throws Exception
	{
		Tab tab = tab();
		tab.status = 429;
		tab.body = "{\"reason\":\"busy\"}";
		tab.retryAfter = "20";
		edt(() ->
		{
			tab.press(RIMMINGTON);
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			for (int second = 0; second < 3; second++)
			{
				tab.stars.refresh();
			}
			assertEquals(MapWait.BUSY, tab.press.said(StarsPanel.SOURCE).message);
			Container head = head(tab);
			assertTrue("said under the pinned star, with its wait: " + words(head),
				words(head).stream().anyMatch(line -> line.startsWith("The map is busy.") && line.contains("Try again in")));
			assertEquals("the star stays pinned", Integer.valueOf(302), tab.destinations.getDestination().world);
			assertNull(tab.destinations.getDestination().route);
			assertTrue("nothing waits to ask again", tab.dwells.isEmpty());
			assertEquals("and nothing more was sent", 1, tab.sent.size());
			AbstractButton route = (AbstractButton) named(head, "stars.route");
			assertEquals("Route", route.getText());
			assertFalse("it waits out the map's twenty seconds, as after any refusal", route.isEnabled());

			tab.openGate();
			tab.status = 200;
			tab.body = TWO_WAYS;
			tab.stars.refresh();
			route = (AbstractButton) named(head(tab), "stars.route");
			assertTrue("the player's own retry", route.isEnabled());
			route.doClick();
		});
		until(() -> tab.sent.size() == 2);
		tab.answered();
		edt(() ->
		{
			tab.stars.refresh();
			assertEquals("Route asked again", 2, tab.sent.size());
			assertNotNull("and its answer is the star's way", tab.destinations.getDestination().route);
			assertTrue(tab.route.view().following);
		});
	}

	/** What the map said was about the star it was asked about: the next star pressed does not say it. */
	@Test
	public void aRefusalIsNotSaidUnderTheNextStarPressed() throws Exception
	{
		Tab tab = tab();
		tab.status = 429;
		tab.body = "{\"reason\":\"busy\"}";
		tab.retryAfter = "20";
		edt(() ->
		{
			tab.press(RIMMINGTON);
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			tab.stars.refresh();
			assertTrue(words(head(tab)).stream().anyMatch(line -> line.startsWith("The map is busy.")));
			tab.press(CRAFTING);
			assertEquals(Integer.valueOf(301), tab.destinations.getDestination().world);
			assertFalse(words(tab.stars).toString(), words(tab.stars).stream().anyMatch(line -> line.startsWith("The map is busy.")));
			assertEquals("its own question waits out the map's wait", 1, tab.dwells.size());
			assertTrue((long) tab.dwells.get(0)[1] > 15_000L);
		});
	}

	/** The pinned star pressed again is no new pin and no new question: Unpin is at the head. */
	@Test
	public void pressingThePinnedStarAgainDoesNothing() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			tab.press(RIMMINGTON);
			RelayResponse.Destination pin = tab.destinations.getDestination();
			Object[] dwell = tab.dwells.get(0);
			tab.press(RIMMINGTON);
			assertSame("the pin is the one already made", pin, tab.destinations.getDestination());
			assertEquals("the marker is not told again", 1, tab.synced.get());
			assertEquals(1, tab.dwells.size());
			assertSame("and its dwell is not started over", dwell, tab.dwells.get(0));
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			tab.stars.refresh();
			RelayResponse.Destination routed = tab.destinations.getDestination();
			tab.press(RIMMINGTON);
			assertSame("routed, a press still changes nothing", routed, tab.destinations.getDestination());
			assertTrue("its way stays", tab.route.view().hasRoute());
			assertTrue(tab.dwells.isEmpty());
			assertEquals(1, tab.sent.size());
		});
	}

	/**
	 * A star whose tile is a guess is not pinned: there is no tile to pin. It keeps its picked card,
	 * Open website and the note, and a pin already made and its question are left alone.
	 */
	@Test
	public void aStarWhoseTileIsAGuessIsNotPinnedAndKeepsItsOpenButton() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			tab.press(MINING_GUILD);
			assertNull("not pinned", tab.destinations.getDestination());
			assertEquals(0, tab.synced.get());
			assertTrue("and nothing to ask", tab.dwells.isEmpty());
			Container card = card(tab.stars, MINING_GUILD);
			assertNotNull("its card opens on the website", named(card, "stars.open"));
			assertTrue(words(card).toString(), words(card).contains(Words.of("stars.approximate")));
			assertNull(named(card, "stars.route"));

			tab.press(RIMMINGTON);
			tab.press(MINING_GUILD);
			assertEquals("a pick leaves the pin", Integer.valueOf(302), tab.destinations.getDestination().world);
			assertEquals("and its question", 1, tab.dwells.size());
			tab.dwell();
		});
		tab.answered();
		edt(() ->
		{
			assertEquals(1, tab.sent.size());
			assertEquals("{\"x\":2974,\"y\":3241,\"plane\":0}", body(tab.sent.get(0)).get("target").toString());
		});
	}

	/** A star with no time left cannot be pinned, so a press on it is no press. */
	@Test
	public void aDepletedStarIsNotPinned() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			StarsModel.Feed feed = feed();
			feed.rows.stream().filter(star -> star.world == 306).findFirst().get().remainingMs = -30_000L;
			tab.service.model.preferences.filters.hideDepleted = false;
			tab.service.model.accept(feed);
			tab.stars.refresh();
			tab.press(AL_KHARID);
			assertNull("not pinned", tab.destinations.getDestination());
			assertEquals(0, tab.synced.get());
			assertTrue("and nothing to ask", tab.dwells.isEmpty());
			assertFalse(words(tab.stars).contains("Pinned destination"));

			/* A star with time left, to show the press itself works here. */
			tab.press(RIMMINGTON);
			assertEquals(Integer.valueOf(302), tab.destinations.getDestination().world);
		});
	}

	/** The press was the asking, so the question goes on another tab, and with the sidebar closed. */
	@Test
	public void theQuestionStillGoesAfterTurningToAnotherTabOrClosingTheSidebar() throws Exception
	{
		Tab turned = tab();
		edt(() ->
		{
			turned.press(RIMMINGTON);
			turned.stars.selected(false);
			turned.dwell();
		});
		turned.answered();
		Tab closed = tab();
		edt(() ->
		{
			closed.press(RIMMINGTON);
			closed.stars.active(false);
			closed.dwell();
		});
		closed.answered();
		for (Tab tab : Arrays.asList(turned, closed))
		{
			edt(() ->
			{
				assertEquals(1, tab.sent.size());
				assertNotNull("and its answer is followed", tab.destinations.getDestination().route);
				assertTrue(tab.route.view().following);
			});
		}
	}

	/**
	 * A reply for a star the player has pressed on from does not bring that star back: the star
	 * pressed since stays pinned, nothing is said under it for the other, and its own question goes
	 * once the wait the reply left is over.
	 */
	@Test
	public void aReplyForAStarPressedOnFromDoesNotReplaceTheStarPinnedSince() throws Exception
	{
		Tab tab = tab();
		tab.hold = new CountDownLatch(1);
		edt(() ->
		{
			tab.press(RIMMINGTON);
			tab.dwell();
		});
		until(() -> tab.sent.size() == 1);
		RelayResponse.Destination[] crafting = new RelayResponse.Destination[1];
		edt(() ->
		{
			tab.press(CRAFTING);
			crafting[0] = tab.destinations.getDestination();
			assertEquals(Integer.valueOf(301), crafting[0].world);
		});
		tab.hold.countDown();
		tab.hold = null;
		tab.answered();
		edt(() ->
		{
			tab.stars.refresh();
			assertSame("Crafting guild is still the pin", crafting[0], tab.destinations.getDestination());
			assertFalse("Rimmington's way was not adopted", tab.route.hasRoute());
			assertNull("and nothing is said of it", tab.press.said(StarsPanel.SOURCE).message);
			assertEquals("Crafting guild's question is still to come", "Planning…", ((AbstractButton) named(head(tab), "stars.route")).getText());

			/* Its dwell ends inside the five seconds Rimmington's reply left, so it waits them out. */
			tab.dwell();
			assertEquals(1, tab.sent.size());
			assertEquals(1, tab.dwells.size());
			tab.openGate();
			tab.dwell();
		});
		until(() -> tab.sent.size() == 2);
		tab.answered();
		edt(() ->
		{
			assertEquals("{\"x\":2940,\"y\":3280,\"plane\":0}", body(tab.sent.get(1)).get("target").toString());
			assertEquals(Integer.valueOf(301), tab.destinations.getDestination().world);
			assertNotSame("with the way to it now", crafting[0], tab.destinations.getDestination());
			assertTrue(tab.route.view().following);
		});
	}

	/**
	 * Route beside Unpin is the way to its own pin too. Pressed after a refusal and still out when the
	 * player presses another star, its answer does not take the head of the tab back.
	 */
	@Test
	public void routeBesideUnpinStillOutDoesNotReplaceTheStarPressedSince() throws Exception
	{
		Tab tab = tab();
		tab.status = 429;
		tab.body = "{\"reason\":\"busy\"}";
		edt(() ->
		{
			tab.press(RIMMINGTON);
			tab.dwell();
		});
		tab.answered();
		tab.status = 200;
		tab.body = TWO_WAYS;
		tab.hold = new CountDownLatch(1);
		edt(() ->
		{
			tab.openGate();
			tab.stars.refresh();
			click(head(tab), "stars.route");
		});
		until(() -> tab.sent.size() == 2);
		RelayResponse.Destination[] crafting = new RelayResponse.Destination[1];
		edt(() ->
		{
			tab.press(CRAFTING);
			crafting[0] = tab.destinations.getDestination();
		});
		tab.hold.countDown();
		tab.hold = null;
		tab.answered();
		edt(() ->
		{
			tab.stars.refresh();
			assertSame("Crafting guild is still the pin", crafting[0], tab.destinations.getDestination());
			assertFalse("Rimmington's way was not adopted", tab.route.hasRoute());
			assertTrue("and Crafting guild's own question still waits", tab.dwells.size() == 1);
		});
	}

	/** A site folded over several worlds: the card pins its first world, and a row under the fold pins its own. */
	@Test
	public void aWorldsRowUnderAFoldPinsThatWorld() throws Exception
	{
		Tab tab = tab();
		edt(() ->
		{
			click(tab.stars, "stars.expand.tile:2974,3241");
			click(tab.stars, "stars.world.330|rimmington mine");
			assertEquals("the row's world", Integer.valueOf(330), tab.destinations.getDestination().world);
			assertTrue(words(head(tab)).toString(), words(head(tab)).contains("W330"));
			Component row = named(tab.stars, "stars.world.330|rimmington mine");
			assertTrue("and the row is marked as the one pressed", row.isOpaque());
			assertEquals(StarsPanel.PICKED, row.getBackground());
			assertFalse(named(tab.stars, "stars.world.444|rimmington mine").isOpaque());

			tab.press(RIMMINGTON);
			assertEquals("the card is its first world", Integer.valueOf(302), tab.destinations.getDestination().world);
			assertFalse(named(tab.stars, "stars.world.330|rimmington mine").isOpaque());
			tab.dwell();
		});
		tab.answered();
		edt(() -> assertEquals("asked once, for the world stopped on", 1, tab.sent.size()));
	}

	/** The tab showing, over the fixture feed, with a map that answers {@link #body}. */
	private final class Tab
	{
		final DestinationState destinations = new DestinationState(new Gson());
		final DestinationRoute route = new DestinationRoute();
		final List<Request> sent = new CopyOnWriteArrayList<>();
		final AtomicInteger synced = new AtomicInteger();
		/** The dwells waiting, each its action and the delay it asked for. */
		final List<Object[]> dwells = new ArrayList<>();
		volatile int status = 200;
		volatile String body = TWO_WAYS;
		volatile String retryAfter;
		/** When set, the map's reply waits for the test to let it go. */
		volatile CountDownLatch hold;
		final StarsClient service;
		final RoutePress press;
		final StarsPanel stars;

		/**
		 * @param loggedIn whether a character is in the game, so there is somebody to ask the map as
		 * @param paired whether the map has issued this member its secret
		 */
		Tab(boolean loggedIn, boolean paired)
		{
			HarnessStore store = new HarnessStore();
			Pairing pairing = new Pairing(store, new Gson());
			assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
			MemberIdentity identity = new MemberIdentity(store);
			if (paired)
			{
				pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
			}
			OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
			{
				sent.add(chain.request());
				CountDownLatch held = hold;
				try
				{
					if (held != null && !held.await(5, TimeUnit.SECONDS))
					{
						throw new InterruptedIOException("the test never let the reply go");
					}
				}
				catch (InterruptedException e)
				{
					throw new InterruptedIOException("interrupted");
				}
				Response.Builder reply = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(status).message("").body(ResponseBody.create(JSON, body));
				if (retryAfter != null)
				{
					reply.header("Retry-After", retryAfter);
				}
				return reply.build();
			}).build();
			DestinationMarker marker = new DestinationMarker(null, null, null, null, null, null)
			{
				@Override
				void sync()
				{
					synced.incrementAndGet();
				}
			};
			ClientThread now = new ClientThread()
			{
				@Override
				public void invokeLater(Runnable r)
				{
					r.run();
				}
			};
			Client in = RoutePressTest.game();
			Client game = loggedIn ? in : (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) -> "getGameState".equals(method.getName()) ? GameState.LOGIN_SCREEN : method.invoke(in, args));
			/* The list's own client thread never runs: the feed is put in by hand, and is not what is counted. */
			service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);
			press = new RoutePress(http, new Gson(), game, now, pairing, identity, route, destinations, marker);
			stars = new StarsPanel(service, destinations, now, marker, press,
				() -> MapCompanionConfig.DEFAULT_MAP_URL, route, null, null, null, rerouting -> null, () -> { });
			stars.dwell = (action, delayMs) ->
			{
				Object[] entry = {action, delayMs};
				dwells.add(entry);
				return () -> dwells.remove(entry);
			};
			/* Free-to-play worlds too, so the list has three sites with a tile of their own to press through. */
			service.model.preferences.filters.showFreeToPlay = true;
			service.model.accept(feed());
			service.model.miningLevel = 72;
			stars.active(true);
			stars.selected(true);
		}

		/** A press on a site's card, which is its first world. */
		void press(String site)
		{
			click(stars, site);
		}

		/** The dwell's time is up: run what waits, as Swing's timer would. */
		void dwell()
		{
			for (Object[] entry : new ArrayList<>(dwells))
			{
				dwells.remove(entry);
				((Runnable) entry[0]).run();
			}
		}

		/** Wait for the question's reply to land, whatever it was. */
		void answered() throws Exception
		{
			until(() ->
			{
				RoutePress.Said said = press.said(StarsPanel.SOURCE);
				return said != null && !said.planning;
			});
		}

		/** As if the map's wait between questions had passed. */
		void openGate()
		{
			try
			{
				java.lang.reflect.Field next = DestinationRoute.class.getDeclaredField("nextRerouteMs");
				next.setAccessible(true);
				synchronized (route)
				{
					next.setLong(route, 0L);
				}
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		}
	}

	private Tab tab() throws Exception
	{
		return tab(true, true);
	}

	private Tab tab(boolean loggedIn, boolean paired) throws Exception
	{
		Tab[] made = new Tab[1];
		edt(() -> made[0] = new Tab(loggedIn, paired));
		return made[0];
	}

	/** A step of the test on Swing's thread, where the tab lives. */
	private interface Step
	{
		void run() throws Exception;
	}

	private static void edt(Step step) throws Exception
	{
		Throwable[] thrown = new Throwable[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				step.run();
			}
			catch (Throwable t)
			{
				thrown[0] = t;
			}
		});
		if (thrown[0] instanceof Error)
		{
			throw (Error) thrown[0];
		}
		if (thrown[0] != null)
		{
			throw (Exception) thrown[0];
		}
	}

	private static void until(BooleanSupplier done) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 5_000;
		while (!done.getAsBoolean() && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertTrue("waited five seconds", done.getAsBoolean());
	}

	private static JsonObject body(Request request) throws Exception
	{
		Buffer body = new Buffer();
		request.body().writeTo(body);
		return new Gson().fromJson(body.readString(StandardCharsets.UTF_8), JsonObject.class);
	}

	private static StarsModel.Feed feed()
	{
		return new Gson().fromJson(new InputStreamReader(StarsPressTest.class.getResourceAsStream("/companion-stars.sample.json"),
			StandardCharsets.UTF_8), StarsModel.Feed.class);
	}

	private static void click(Container in, String name)
	{
		Component button = named(in, name);
		assertNotNull(name, button);
		((AbstractButton) button).doClick();
	}

	/** The pinned star's block at the head of the tab: its line, its way or Route, and Unpin. */
	private static Container head(Tab tab)
	{
		return (Container) tab.stars.getComponent(0);
	}

	/** A site's card: the press on it, and whatever opened under it. */
	private static Container card(Container stars, String site)
	{
		return named(stars, site).getParent().getParent();
	}

	/** The view of a route's ways and steps in a container, or null. */
	private static RouteStepsView steps(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof RouteStepsView)
			{
				return (RouteStepsView) child;
			}
			if (child instanceof Container)
			{
				RouteStepsView found = steps((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The words showing, as a player reads them: label and button text without its markup. */
	private static List<String> words(Container root)
	{
		List<String> found = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (!child.isVisible())
			{
				continue;
			}
			String text = child instanceof JLabel ? ((JLabel) child).getText()
				: child instanceof AbstractButton ? ((AbstractButton) child).getText() : null;
			if (text != null && !text.isEmpty())
			{
				found.add(text.replaceAll("<[^>]+>", "").trim());
			}
			if (child instanceof Container)
			{
				found.addAll(words((Container) child));
			}
		}
		return found;
	}

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
