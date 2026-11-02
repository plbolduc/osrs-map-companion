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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The destination's card under Now, read off its real components as a player sees them: the
 * buttons under a route, and what each press does.
 *
 * <p>The card is the real {@link NowCards} over the real route, presses and destination state. The
 * destination is the reply fixture's: Lumbridge Home Teleport, then a walk upstairs to the bank.
 */
public class DestinationCardTest
{
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String NAME = "Lumbridge";

	private final Gson gson = new Gson();
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, gson);
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationState destinations = new DestinationState(gson);
	private final DestinationRoute route = new DestinationRoute();
	private final ClientThread thread = new ClientThread()
	{
		@Override
		public void invokeLater(Runnable action)
		{
			action.run();
		}
	};
	/** The world map's points need a running client; the card only asks for them to be synced. */
	private final DestinationMarker marker = new DestinationMarker(null, null, null, null, null, null)
	{
		@Override
		void sync()
		{
		}
	};
	/** Whether the Shortest Path plugin answers, which ShortestPath learns from its own messages. */
	private boolean shortestPathRunning;
	private final ShortestPath shortestPath = new ShortestPath(message -> { })
	{
		@Override
		boolean installed()
		{
			return shortestPathRunning;
		}
	};
	private NowCards cards;

	public DestinationCardTest() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		DeathWatcher deaths = construct(DeathWatcher.class);
		RouteClient rerouter = new RouteClient(new OkHttpClient(), gson, game.client(), thread, pairing, identity, route);
		RoutePress press = new RoutePress(new OkHttpClient(), gson, game.client(), thread, pairing, identity, route,
			destinations, marker);
		FarmRunRoute runs = new FarmRunRoute(new FarmRunState(), destinations, route, thread);
		ClueReader clues = new ClueReader(game.client());
		SwingUtilities.invokeAndWait(() -> cards = new NowCards(config, destinations, deaths, marker, route, rerouter,
			shortestPath, press, clues, runs, new GameSprites(null, null, null), this::refreshNow,
			() -> MapCompanionConfig.DEFAULT_MAP_URL));
	}

	/**
	 * Without Shortest Path, Follow is still offered, because following is what puts the step in the
	 * box over the game. Offered only where Shortest Path could draw, a route somebody sent never
	 * reached the box, and one pressed here put the box up with no button to take it down.
	 */
	@Test
	public void followShowsTheStepOverTheGameWithoutShortestPath() throws Exception
	{
		received();
		refresh();
		assertEquals(Arrays.asList("Follow", "Reroute", "Dismiss"), actions());
		assertEquals("Show the step you're on in a box over the game", button("Follow").getToolTipText());
		assertSame("the Follow key presses it too", button("Follow"), cards.hotkey(false));
		assertNull("a route that merely arrived is not followed", RouteStepOverlay.box(route.view(), NAME));

		press("Follow");
		assertNotNull("the box over the game shows the step", RouteStepOverlay.box(route.view(), NAME));
		assertEquals(Arrays.asList("Stop", "Reroute", "Dismiss"), actions());
		press("Stop");
		assertNull(RouteStepOverlay.box(route.view(), NAME));
	}

	/** With the box off and no Shortest Path, following would show nothing, so it is not offered. */
	@Test
	public void nothingToFollowWithNeitherTheBoxNorShortestPath() throws Exception
	{
		config.set("showStepOnScreen", false);
		received();
		refresh();
		assertEquals(Arrays.asList("Reroute", "Dismiss"), actions());

		shortestPathRunning = true;
		refresh();
		assertEquals("Shortest Path alone has walks to draw", Arrays.asList("Follow", "Reroute", "Dismiss"), actions());
		assertEquals("Have Shortest Path draw each walking step of this route. It is sent nothing for a teleport or a ride.",
			button("Follow").getToolTipText());
	}

	/** Once there, no step is left to show or to draw. */
	@Test
	public void followGoesOnArrival() throws Exception
	{
		received();
		route.advance(new WorldPoint(3222, 3218, 0));
		route.advance(new WorldPoint(3208, 3220, 2));
		refresh();
		assertTrue(route.view().arrived());
		assertEquals(Arrays.asList("Reroute", "Dismiss"), actions());
	}

	/** The reply fixture's destination with its route, as the map sends it and the next tick takes it. */
	private void received() throws Exception
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")),
			StandardCharsets.UTF_8);
		destinations.accept(gson.fromJson(reply, RelayResponse.class).directive);
		route.sync(destinations.getDestination());
	}

	/** The card as the sidebar's one-second refresh draws it, connected. */
	private void refresh() throws Exception
	{
		SwingUtilities.invokeAndWait(this::refreshNow);
	}

	private void refreshNow()
	{
		cards.refresh(true, true);
	}

	/** A click on a button the player can see and press, on the event thread as Swing makes it. */
	private void press(String words) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JButton button = find(cards, words);
			assertTrue("no " + words + " to press: " + actions(), button != null && button.isEnabled());
			button.doClick();
		});
	}

	private JButton button(String words)
	{
		JButton button = find(cards, words);
		assertNotNull("no " + words + ": " + actions(), button);
		return button;
	}

	/** The destination card's row of buttons, in order: the row Dismiss is always in. */
	private List<String> actions()
	{
		List<String> out = new ArrayList<>();
		JButton dismiss = find(cards, "Dismiss");
		if (dismiss != null)
		{
			for (Component child : dismiss.getParent().getComponents())
			{
				if (child.isVisible() && child instanceof JButton)
				{
					out.add(((JButton) child).getText());
				}
			}
		}
		return out;
	}

	private static JButton find(Component component, String words)
	{
		if (!component.isVisible())
		{
			return null;
		}
		if (component instanceof JButton && words.equals(((JButton) component).getText()))
		{
			return (JButton) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JButton found = find(child, words);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private <T> T construct(Class<T> type) throws Exception
	{
		Constructor<T> make = type.getDeclaredConstructor(Client.class);
		make.setAccessible(true);
		return make.newInstance(game.client());
	}
}
