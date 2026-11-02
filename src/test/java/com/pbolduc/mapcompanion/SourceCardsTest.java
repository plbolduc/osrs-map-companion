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
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The cards under Now that a route is pressed from — where you died, the clue, the meetup — and what
 * each says once its own answer is the destination above it.
 *
 * <p>The cards are the sidebar's own {@link NowCards}, pressed through their buttons, with a real
 * {@link RoutePress} answered by an interceptor, so what is checked is what the player would read.
 */
public class SourceCardsTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationState destinations = new DestinationState(new Gson());
	private final DestinationRoute route = new DestinationRoute();
	private final ItemReadingsTestClient items = new ItemReadingsTestClient();
	private final ClueReader clues = new ClueReader(items.client);
	private final List<Request> sent = new CopyOnWriteArrayList<>();
	private final ClientThread thread = new ClientThread()
	{
		@Override
		public void invokeLater(Runnable r)
		{
			r.run();
		}
	};

	private RoutePress press;
	private NowCards cards;

	/** The cards, connected, with the map answering every route with {@code answer}. */
	private void cards(DeathWatcher deaths, String answer) throws Exception
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(200).message("").body(ResponseBody.create(JSON, answer)).build();
		}).build();
		DestinationMarker marker = new DestinationMarker(null, null, null, null, null, null)
		{
			@Override
			void sync()
			{
			}
		};
		press = new RoutePress(http, new Gson(), RoutePressTest.game(), thread, pairing, identity, route, destinations, marker);
		RouteClient reroute = new RouteClient(http, new Gson(), RoutePressTest.game(), thread, pairing, identity, route);
		SwingUtilities.invokeAndWait(() -> cards = new NowCards(config, destinations, deaths, marker, route, reroute,
			new ShortestPath(message -> { }), press, clues, new FarmRunRoute(new FarmRunState(), destinations, route, thread),
			new GameSprites(null, null, null), () -> { }, () -> MapCompanionConfig.DEFAULT_MAP_URL));
		refresh();
	}

	private void refresh() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> cards.refresh(true, true));
	}

	/** Died at the Ungael crater a moment ago, through the real death event. */
	private static DeathWatcher died() throws Exception
	{
		return died(java.util.Collections.<Integer, Integer>emptyMap());
	}

	/** The same death, with the game's varbits as given, for the gravestone's timer. */
	private static DeathWatcher died(java.util.Map<Integer, Integer> varbits) throws Exception
	{
		Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getLocalLocation":
						return null;
					case "getWorldLocation":
						return new WorldPoint(2272, 4049, 0);
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
		Constructor<DeathWatcher> make = DeathWatcher.class.getDeclaredConstructor(Client.class);
		make.setAccessible(true);
		DeathWatcher deaths = make.newInstance(client);
		deaths.record(new ActorDeath(player));
		assertNotNull(deaths.snapshot(System.currentTimeMillis()));
		return deaths;
	}

	private static JButton button(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && name.equals(child.getName()))
			{
				return (JButton) child;
			}
			if (child instanceof Container)
			{
				JButton found = button((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private void settle(String source) throws Exception
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while ((press.said(source) == null || press.said(source).planning) && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertFalse("the answer landed", press.said(source).planning);
	}

	/**
	 * Route back's answer carries no age of its own, because the death card under it keeps counting
	 * one; and while that answer is the destination, the death card's button reads Reroute, as a
	 * friend's does, rather than offering the card above it as new. Dismiss brings Route back.
	 */
	@Test
	public void routeBackLeavesTheAgeToTheDeathCardAndThenReadsReroute() throws Exception
	{
		cards(died(), RoutePressTest.ANSWER.replace("\"name\":\"Abyssal demon\"", "\"name\":null"));
		JButton back = button(cards, "now.death.route");
		assertEquals("Route back", back.getText());

		SwingUtilities.invokeAndWait(back::doClick);
		settle("death");
		RelayResponse.Destination shown = destinations.getDestination();
		assertNotNull(shown);
		assertEquals("Where you died", shown.name);
		assertNull("no age written at the press, to go stale above the card that counts it", shown.note);

		refresh();
		assertEquals("Reroute", button(cards, "now.death.route").getText());

		destinations.dismiss();
		refresh();
		assertEquals("Route back", button(cards, "now.death.route").getText());
	}

	/**
	 * Under the death, the gravestone's time left by the game's own timer, with a bar full at the
	 * first reading; with no stone standing the card says only how long ago, as it did.
	 */
	@Test
	public void theDeathCardShowsTheGravestonesOwnTimeLeft() throws Exception
	{
		java.util.Map<Integer, Integer> varbits = new java.util.concurrent.ConcurrentHashMap<>();
		DeathWatcher deaths = died(varbits);
		cards(deaths, RoutePressTest.ANSWER);
		javax.swing.JLabel grave = label(cards, "now.death.grave");
		assertFalse("no stone read, no line", grave.getParent().isVisible());

		long now = System.currentTimeMillis();
		deaths.onSessionResumed(now - 20_000L);
		varbits.put(net.runelite.api.gameval.VarbitID.GRAVESTONE_VISIBLE, 1);
		varbits.put(net.runelite.api.gameval.VarbitID.GRAVESTONE_DURATION, 830);
		deaths.readGravestone(now + 1_000L);
		refresh();
		assertTrue(grave.getParent().isVisible());
		assertTrue(grave.getText(), grave.getText().contains("Gravestone · 9m left"));

		varbits.put(net.runelite.api.gameval.VarbitID.GRAVESTONE_VISIBLE, 0);
		deaths.readGravestone(now + 2_000L);
		refresh();
		assertFalse("the stone has gone; the death stays", grave.getParent().isVisible());
		assertNotNull(button(cards, "now.death.route"));
	}

	private static javax.swing.JLabel label(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof javax.swing.JLabel && name.equals(child.getName()))
			{
				return (javax.swing.JLabel) child;
			}
			if (child instanceof Container)
			{
				javax.swing.JLabel found = label((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The clue card's Route reads Reroute too while the destination is the answer it asked for. */
	@Test
	public void aCluesRouteReadsRerouteWhileItsAnswerIsTheDestination() throws Exception
	{
		items.define(ItemID.TRAIL_CLUE_MEDIUM_MAP003, "Clue scroll (medium)", false);
		items.carry(ItemID.TRAIL_CLUE_MEDIUM_MAP003);
		assertNotNull(clues.read());
		cards(died(), RoutePressTest.ANSWER.replace("\"name\":\"Abyssal demon\"", "\"name\":\"West of the Crafting Guild\""));
		JButton clue = button(cards, "now.clue.route");
		assertEquals("Route", clue.getText());

		SwingUtilities.invokeAndWait(clue::doClick);
		settle("clue");
		assertEquals("West of the Crafting Guild", destinations.getDestination().name);
		refresh();
		assertEquals("Reroute", button(cards, "now.clue.route").getText());
		assertEquals("another card's button is its own", "Route back", button(cards, "now.death.route").getText());
	}
}
