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
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JLabel;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The search list stays up until a pick is answered, says which row a refusal was for, and takes no
 * pick while another route is planning.
 *
 * <p>The panel runs on a real {@link SearchClient} and a real {@link RoutePress}, each answered by an
 * interceptor, so what is checked is the list a player would see after each answer and what left the
 * machine. Swing's work is run on the test's own thread, as the sidebar runs it on its one thread.
 */
public class SearchPanelTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String BANKS = "{\"rows\":["
		+ "{\"id\":\"bank:lumbridge\",\"kind\":\"bank\",\"name\":\"Lumbridge Castle bank\",\"line\":\"Bank · 2nd floor\","
		+ "\"x\":3208,\"y\":3220,\"plane\":2,\"section\":\"Places\"},"
		+ "{\"id\":\"bank:al-kharid\",\"kind\":\"bank\",\"name\":\"Al Kharid bank\",\"line\":\"Bank\","
		+ "\"x\":3269,\"y\":3167,\"plane\":0,\"section\":\"Places\"}],\"items\":[]}";

	private final HarnessStore store = new HarnessStore();
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationRoute route = new DestinationRoute();
	private final DestinationState destinations = new DestinationState(new Gson());
	/** What reached the route door, in order. */
	private final List<Request> routed = new CopyOnWriteArrayList<>();
	/** Swing's queue: the search's answers land here and run on the test's thread. */
	private final List<Runnable> swing = new CopyOnWriteArrayList<>();
	/** Holds a route's answer back until a test lets it go, so a press can be seen planning. */
	@Nullable
	private volatile CountDownLatch gate;

	private RoutePress press(int status, String body)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			routed.add(chain.request());
			CountDownLatch held = gate;
			if (held != null)
			{
				try
				{
					held.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("").body(ResponseBody.create(JSON, body)).build();
		}).build();
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		return new RoutePress(http, new Gson(), RoutePressTest.game(), new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		}, pairing, identity, route, destinations, new DestinationMarker(null, null, null, null, null, null)
		{
			@Override
			void sync()
			{
			}
		});
	}

	/** The panel with the search for "bank" answered: two rows, nothing picked. */
	private SearchPanel listed(RoutePress press, String answer) throws Exception
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
			new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(200).message("").body(ResponseBody.create(JSON, answer)).build()).build();
		Pairing.Signed held = new Pairing.Signed("https://osrs-map.com", pairing.code(), identity.id(), SECRET);
		SearchClient search = new SearchClient(http, new Gson(), () -> held, (action, delayMs) -> () -> { }, swing::add);
		SearchPanel panel = new SearchPanel(search, press, null);
		panel.box().setText("bank");
		panel.box().postActionEvent();
		until(() -> !swing.isEmpty());
		runSwing();
		assertFalse(search.result().searching);
		panel.refresh();
		return panel;
	}

	private void runSwing()
	{
		List<Runnable> due = new ArrayList<>(swing);
		swing.clear();
		due.forEach(Runnable::run);
	}

	/** Typed on the end, as a player types: setting the text would empty the box first, and forget the list. */
	private static void typeOnTheEnd(SearchPanel panel, String more) throws Exception
	{
		javax.swing.text.Document typed = panel.box().getDocument();
		typed.insertString(typed.getLength(), more, null);
	}

	private static void until(BooleanSupplier condition) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(5);
		}
		assertTrue("the answer landed", condition.getAsBoolean());
	}

	private static List<Component> named(Container root, String name)
	{
		List<Component> found = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (name.equals(child.getName()))
			{
				found.add(child);
			}
			if (child instanceof Container)
			{
				found.addAll(named((Container) child, name));
			}
		}
		return found;
	}

	private static String status(SearchPanel panel)
	{
		JLabel line = (JLabel) named(panel, "search.status").get(0);
		return line.isVisible() ? line.getText() : null;
	}

	private static SearchClient.Hit hit(String id, String name)
	{
		return new SearchClient.Hit(id, name, "Bank", null, null);
	}

	/**
	 * A refusal leaves the rows up, so the next row is one press away, and the line above them says
	 * which row was refused; typing something new puts that line away.
	 */
	@Test
	public void aRefusalKeepsTheRowsUpAndNamesTheRow() throws Exception
	{
		RoutePress press = press(404, "{\"reason\":\"not_found\"}");
		SearchPanel panel = listed(press, BANKS);
		assertEquals(2, named(panel, "search.row").size());

		panel.pick(hit("bank:al-kharid", "Al Kharid bank"));
		until(() -> press.said(SearchPanel.SOURCE) != null && !press.said(SearchPanel.SOURCE).planning);
		panel.refresh();

		assertNull("a refusal leaves the card as it was", destinations.getDestination());
		assertEquals("the rows are still there to try the next", 2, named(panel, "search.row").size());
		String line = status(panel);
		assertNotNull(line);
		assertTrue(line, line.contains("Al Kharid bank: " + RouteClient.NOT_FOUND));

		typeOnTheEnd(panel, " chest");
		panel.refresh();
		String after = status(panel);
		assertTrue("a refusal does not head a list it was not picked from: " + after,
			after == null || !after.contains("Al Kharid bank"));
	}

	/**
	 * A refused pick was the grey of "Searching…", so whether a press had worked was read from its
	 * words alone. A pick that did not happen is in the refusal pink; one on its way stays grey.
	 */
	@Test
	public void aRefusedPickIsPinkAndOneOnItsWayIsGrey() throws Exception
	{
		RoutePress press = press(404, "{\"reason\":\"not_found\"}");
		SearchPanel panel = listed(press, BANKS);
		JLabel line = (JLabel) named(panel, "search.status").get(0);
		gate = new CountDownLatch(1);
		panel.pick(hit("bank:al-kharid", "Al Kharid bank"));
		until(() -> routed.size() == 1);
		panel.refresh();
		assertTrue(status(panel), status(panel).contains("Planning the way there…"));
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, line.getForeground());

		gate.countDown();
		until(() -> !press.said(SearchPanel.SOURCE).planning);
		panel.refresh();
		assertTrue(status(panel), status(panel).contains(RouteClient.NOT_FOUND));
		assertEquals(MapCompanionPanel.REFUSED, line.getForeground());
	}

	/**
	 * The search's own waits were written into their sentence once: "Try again in 6s." and "Searching
	 * in 3s." stayed put while the wait ran out. The line says the search's wait as it is drawn.
	 */
	@Test
	public void theSearchsOwnWaitsAreSaidAsTheyAre() throws Exception
	{
		RoutePress press = press(200, RoutePressTest.ANSWER);
		OkHttpClient busy = new OkHttpClient.Builder().addInterceptor(chain ->
			new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("")
				.header("Retry-After", "20").body(ResponseBody.create(JSON, "{\"reason\":\"busy\"}")).build()).build();
		Pairing.Signed held = new Pairing.Signed("https://osrs-map.com", pairing.code(), identity.id(), SECRET);
		SearchClient search = new SearchClient(busy, new Gson(), () -> held, (action, delayMs) -> () -> { }, swing::add);
		SearchPanel panel = new SearchPanel(search, press, null);
		panel.box().setText("bank");
		panel.box().postActionEvent();
		until(() -> !swing.isEmpty());
		runSwing();
		panel.refresh();
		assertTrue(status(panel), status(panel).matches(".*The map is busy\\. Try again in (19|20)s\\..*"));

		/* Enter again inside the wait: nothing is refused, and the search goes by itself once it is over. */
		panel.box().postActionEvent();
		panel.refresh();
		assertTrue(status(panel), status(panel).matches(".*The map asked to wait\\. Searching in (19|20)s\\..*"));
	}

	/** The list folds when the card has the answer, not at the press: while it plans, the rows stay. */
	@Test
	public void anAnswerFoldsTheListOnlyOnceItLands() throws Exception
	{
		RoutePress press = press(200, RoutePressTest.ANSWER.replace("Abyssal demon", "Lumbridge Castle bank"));
		SearchPanel panel = listed(press, BANKS);
		gate = new CountDownLatch(1);

		panel.pick(hit("bank:lumbridge", "Lumbridge Castle bank"));
		until(() -> routed.size() == 1);
		panel.refresh();
		assertTrue(press.said(SearchPanel.SOURCE).planning);
		assertEquals("still planning, so the list stays", 2, named(panel, "search.row").size());
		assertTrue(status(panel), status(panel).contains("Planning the way there…"));

		gate.countDown();
		until(() -> !press.said(SearchPanel.SOURCE).planning);
		panel.refresh();
		assertNotNull(destinations.getDestination());
		assertEquals("the card is the answer now", 0, named(panel, "search.row").size());

		typeOnTheEnd(panel, " booth");
		panel.refresh();
		assertEquals("typing opens it again", 2, named(panel, "search.row").size());
	}

	/**
	 * While another card's route plans, a row takes no pick: one would only be refused, and its
	 * refusal would replace that card's Planning…. The rows say so by drawing their names greyed.
	 */
	@Test
	public void aPickWhileAnotherRoutePlansIsNotTaken() throws Exception
	{
		RoutePress press = press(200, RoutePressTest.ANSWER);
		SearchPanel panel = listed(press, BANKS);
		JLabel live = (JLabel) named(panel, "search.name").get(0);
		assertEquals(java.awt.Color.WHITE, live.getForeground());
		gate = new CountDownLatch(1);
		press.press(RoutePress.Target.tile("death", new WorldPoint(2272, 4049, 0), "Where you died", null, null));
		until(() -> routed.size() == 1);
		assertFalse(press.ready());

		panel.pick(hit("bank:lumbridge", "Lumbridge Castle bank"));
		panel.refresh();
		assertEquals("nothing more was sent", 1, routed.size());
		assertTrue("Route back still says it is planning", press.said("death").planning);
		assertNull(press.said(SearchPanel.SOURCE));

		JLabel greyed = (JLabel) named(panel, "search.name").get(0);
		assertNotEquals(java.awt.Color.WHITE, greyed.getForeground());
		assertNotEquals("no hand over a row that takes no pick", Cursor.HAND_CURSOR,
			named(panel, "search.row").get(0).getCursor().getType());
		gate.countDown();
	}

	/**
	 * The line sits between the box and the rows, where it is read, and a misspelling's way out is a
	 * button: as grey link text it looked like one more status line.
	 */
	@Test
	public void theLineSitsUnderTheBoxAndDidYouMeanIsAButton() throws Exception
	{
		RoutePress press = press(200, RoutePressTest.ANSWER);
		SearchPanel panel = listed(press, "{\"rows\":[],\"items\":[],\"suggestion\":\"abyssal whip\"}");
		assertSame(panel.box(), panel.getComponent(0));
		assertEquals("search.status", panel.getComponent(1).getName());
		JButton mean = (JButton) named(panel, "search.suggestion").get(0);
		assertEquals("Did you mean abyssal whip?", mean.getText());
		assertTrue("drawn as a button, not as text", mean.isContentAreaFilled() && mean.isBorderPainted());
	}
}
