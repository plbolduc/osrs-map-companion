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
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import okhttp3.Dispatcher;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The destination's card under Now, read off its real components as a player sees them: the
 * buttons under a route, and what each press does.
 *
 * <p>The card is the real {@link NowCards} over the real route, presses and destination state. The
 * destination is the reply fixture's: Lumbridge Home Teleport, then a walk upstairs to the bank. A
 * Route press is answered here, inside the press, by a client whose calls never leave the machine,
 * so a press has landed by the time it returns.
 */
public class DestinationCardTest
{
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String NAME = "Lumbridge";
	/** The map's answer to a Route to Rimmington mine: a walk from the bank, ending on the star's tile. */
	private static final String MINE_ANSWER = "{\"target\":{\"x\":2974,\"y\":3240,\"plane\":0},\"origin\":\"request\","
		+ "\"options\":[{\"id\":\"walk\",\"label\":\"Walk\",\"best\":true,\"blocked\":false,\"tiles\":61,"
		+ "\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk 61 tiles to the mine\",\"walk\":true,"
		+ "\"at\":{\"x\":2974,\"y\":3240,\"plane\":0}}]}]}";

	/** The map's answer to the next press, and every request a press made. */
	private int status = 200;
	private String body = "{}";
	private final List<Request> sent = new ArrayList<>();

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
	/**
	 * The walking boot the list of steps draws on the answer's one step, a walk. A step with no
	 * picture of its own was words alone, so these tests saw only the card's picture asked for; the
	 * card's is still asked first, and the boot after it.
	 */
	private static final String WALK = "sprite " + net.runelite.api.gameval.SpriteID.OrbIcon._2 + " at " + RouteStepsView.PICTURE;

	/** What the cards asked the client's cache for, in order; each answers at once with a blank picture. */
	private final List<String> asked = new ArrayList<>();
	private final GameSprites sprites = new GameSprites(null, null, null)
	{
		@Override
		void item(JLabel label, int itemId, int box)
		{
			asked.add("item " + itemId + " at " + box);
			label.setIcon(new ImageIcon(new BufferedImage(box, box, BufferedImage.TYPE_INT_ARGB)));
		}

		@Override
		void sprite(JLabel label, int spriteId, int box)
		{
			asked.add("sprite " + spriteId + " at " + box);
			label.setIcon(new ImageIcon(new BufferedImage(box, box, BufferedImage.TYPE_INT_ARGB)));
		}
	};
	/** The clue the inventory holds, as the reader last read it. */
	@Nullable
	private CompanionSnapshot.Clue carried;
	private RoutePress press;
	private NowCards cards;

	public DestinationCardTest() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		DeathWatcher deaths = construct(DeathWatcher.class);
		press = new RoutePress(answering(), gson, game.client(), thread, pairing, identity, route, destinations, marker);
		FarmRunRoute runs = new FarmRunRoute(new FarmRunState(), destinations, route, thread);
		ClueReader clues = new ClueReader(game.client())
		{
			@Override
			CompanionSnapshot.Clue current()
			{
				return carried;
			}
		};
		SwingUtilities.invokeAndWait(() -> cards = new NowCards(config, destinations, deaths, marker, route,
			shortestPath, press, clues, runs, sprites, this::refreshNow, () -> MapCompanionConfig.DEFAULT_MAP_URL));
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
		assertSame("the Follow key presses it too", button("Follow"), cards.actButton(MapCompanionPanel.Act.FOLLOW));
		assertNull("a route that merely arrived is not followed", RouteStepOverlay.box(route.view(), NAME));

		press("Follow");
		assertNotNull("the box over the game shows the step", RouteStepOverlay.box(route.view(), NAME));
		assertEquals(Arrays.asList("Pause", "Reroute", "Dismiss"), actions());
		assertSame("the box's menu presses the card's Reroute", button("Reroute"), cards.actButton(MapCompanionPanel.Act.REROUTE));
		assertSame("and its Dismiss", button("Dismiss"), cards.actButton(MapCompanionPanel.Act.END));
		press("Pause");
		assertNull(RouteStepOverlay.box(route.view(), NAME));
	}

	/**
	 * While following, the button reads Pause, as a run's card and the "Follow and pause key" name
	 * the same act; it read Stop here alone. Its tip still says the path Shortest Path drew goes.
	 */
	@Test
	public void followingReadsPauseAsARunsCardAndTheKeyDo() throws Exception
	{
		shortestPathRunning = true;
		received();
		refresh();
		press("Follow");
		assertEquals(Arrays.asList("Pause", "Reroute", "Dismiss"), actions());
		assertEquals("Keep this route and stop following it until you press Follow, and clear the path Shortest Path drew",
			button("Pause").getToolTipText());
		assertSame("the key presses the same button", button("Pause"), cards.actButton(MapCompanionPanel.Act.FOLLOW));

		press("Pause");
		assertFalse(route.view().following);
		assertEquals(Arrays.asList("Follow", "Reroute", "Dismiss"), actions());
		shortestPathRunning = false;
		press("Follow");
		assertEquals("without Shortest Path there is no path to clear", "Keep this route and stop following it until you press Follow",
			button("Pause").getToolTipText());
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
		assertEquals("Have Shortest Path draw each walking step of this route.",
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

	/**
	 * A destination with no way yet — here a star pinned on Stars — asks for one where it is shown:
	 * Route, in the cell Reroute takes once there are steps, by the destination's own tile. Its card
	 * had Dismiss alone, and the way there was Unpin, then Route, on another tab.
	 */
	@Test
	public void aDestinationWithNoWayYetOffersRouteToItsOwnTile() throws Exception
	{
		destinations.selectLocal(mine());
		route.sync(destinations.getDestination());
		refresh();
		assertEquals(Arrays.asList("Route", "Dismiss"), actions());

		body = MINE_ANSWER;
		press("Route");
		assertEquals(1, sent.size());
		JsonObject question = question(sent.get(0));
		assertEquals("its own tile", 2974, question.getAsJsonObject("target").get("x").getAsInt());
		assertEquals(3240, question.getAsJsonObject("target").get("y").getAsInt());
		assertEquals("and where the player stands", 3222, question.getAsJsonObject("from").get("x").getAsInt());

		refresh();
		RelayResponse.Destination routed = destinations.getDestination();
		assertEquals("the same place, with its way", "Rimmington mine", routed.name);
		assertEquals(Integer.valueOf(302), routed.world);
		assertEquals("T7 · 16m left", routed.note);
		assertTrue(route.view().hasRoute());
		assertEquals("a press of the player's own is followed", Arrays.asList("Pause", "Reroute", "Dismiss"), actions());
		assertFalse("a press that drew nothing beside it shows no picture", picture().isVisible());
	}

	/** A refusal is said under the card, and Route stays for another try once the map's wait is over. */
	@Test
	public void aRefusedRouteIsSaidUnderTheCard() throws Exception
	{
		destinations.selectLocal(mine());
		route.sync(destinations.getDestination());
		refresh();
		status = 404;
		press("Route");
		refresh();
		assertEquals(Arrays.asList("Route", "Dismiss"), actions());
		assertTrue(words().toString(), words().contains(RoutePress.NOT_FOUND));
		assertEquals("no way came, so nothing is followed", null, route.view().way());
	}

	/** Route asks the map, so it waits for a group the map has let in, as Route back does. */
	@Test
	public void noRouteUntilConnected() throws Exception
	{
		destinations.selectLocal(mine());
		route.sync(destinations.getDestination());
		SwingUtilities.invokeAndWait(() -> cards.refresh(true, false));
		assertEquals(Arrays.asList("Dismiss"), actions());
	}

	/** A destination whose way arrives with it never offers Route, even before the next tick reads it. */
	@Test
	public void aDestinationArrivingWithItsWayDoesNotOfferRoute() throws Exception
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")),
			StandardCharsets.UTF_8);
		destinations.accept(gson.fromJson(reply, RelayResponse.class).directive);
		refresh();
		assertEquals(Arrays.asList("Dismiss"), actions());
	}

	/**
	 * The card shows what it leads to, drawn by the row that asked: a clue's Route answered with the
	 * place the map read from its words draws the scroll beside that place's name, at a step's size.
	 */
	@Test
	public void aCluesAnswerShowsItsScroll() throws Exception
	{
		carried = new CompanionSnapshot.Clue();
		carried.tier = "medium";
		carried.itemId = 2801;
		carried.text = "Speak to the bartender of the Rusty Anchor in Port Sarim.";
		refresh();
		assertEquals("the clue card's own scroll", Arrays.asList("item 2801 at 32"), asked);
		asked.clear();

		body = answer("Bartender, the Rusty Anchor", 3045, 3257);
		press("Route");
		refresh();
		assertEquals("Bartender, the Rusty Anchor", destinations.getDestination().name);
		assertEquals(Arrays.asList("item 2801 at " + RouteStepsView.PICTURE, WALK), asked);
		assertNotNull(picture().getIcon());
		assertTrue(picture().isVisible());

		refresh();
		assertEquals("each asked once, not every second", 2, asked.size());
	}

	/** A search pick draws its row's own picture: here a bank's world-map icon. */
	@Test
	public void aSearchPickShowsItsRowsPicture() throws Exception
	{
		body = answer("Lumbridge Castle bank", 3208, 3220);
		SwingUtilities.invokeAndWait(() ->
		{
			SearchPanel search = new SearchPanel(new SearchClient(new OkHttpClient(), gson, pairing, identity), press, sprites);
			search.pick(new SearchClient.Hit("bank:lumbridge", "Lumbridge Castle bank", "Bank · 2nd floor", null,
				new int[]{3208, 3220, 2}, "bank", null));
		});
		refresh();
		assertEquals(Arrays.asList("sprite " + SearchPanel.spriteFor("bank", null) + " at " + RouteStepsView.PICTURE, WALK), asked);
		assertNotNull(picture().getIcon());
	}

	/** A friend's Route draws their disc, as the world map draws them, without a presence dot. */
	@Test
	public void aFriendsAnswerShowsTheirDisc() throws Exception
	{
		RelayResponse.MemberDot dot = new RelayResponse.MemberDot();
		dot.id = "cara";
		dot.name = "Cara";
		dot.colour = "#38bdf8";
		dot.x = 3215;
		dot.y = 3226;
		dot.world = 302;
		RelayResponse.RosterRow row = new RelayResponse.RosterRow();
		row.id = "cara";
		row.name = "Cara";
		row.colour = "#38bdf8";
		row.near = "near Lumbridge Castle";
		body = answer(null, 3215, 3226);
		press.press(GroupState.routeTarget(dot, Collections.singletonList(row)));
		refresh();
		/* The disc is drawn now and set on Swing's next turn, as a picture from the cache is. */
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals("Cara", destinations.getDestination().name);
		assertNotNull(picture().getIcon());
		assertEquals(RouteStepsView.PICTURE, picture().getIcon().getIconWidth());
	}

	/**
	 * The picture is the press's alone: it goes when the card holds another destination, and nothing
	 * of it is in what the press sends. A press that drew none shows none; see the star's Route above.
	 */
	@Test
	public void thePictureStaysWithItsOwnAnswer() throws Exception
	{
		aCluesAnswerShowsItsScroll();
		received();
		refresh();
		assertFalse("a destination the map sent since has no picture", picture().isVisible());

		RoutePress.Target bare = RoutePress.Target.tile("star", new WorldPoint(2974, 3240, 0), "Rimmington mine", 302, null);
		RoutePress.Target pictured = bare.showing((label, box) -> label.setIcon(null));
		assertEquals("the request is built without it", bodyOf(bare), bodyOf(pictured));
	}

	/** The map's answer to a press: a one-step walk to the tile it planned to, and its name for it. */
	private static String answer(@Nullable String name, int x, int y)
	{
		return "{\"target\":{\"x\":" + x + ",\"y\":" + y + ",\"plane\":0" + (name == null ? "" : ",\"name\":\"" + name + "\"")
			+ "},\"origin\":\"request\",\"options\":[{\"id\":\"walk\",\"label\":\"Walk\",\"best\":true,\"blocked\":false,"
			+ "\"tiles\":30,\"steps\":[{\"slot\":\"walk-leg\",\"title\":\"Walk 30 tiles\",\"walk\":true,"
			+ "\"at\":{\"x\":" + x + ",\"y\":" + y + ",\"plane\":0}}]}]}";
	}

	/** The body a press would send for a target, from where the test's player stands. */
	private String bodyOf(RoutePress.Target target) throws Exception
	{
		Buffer buffer = new Buffer();
		press.request("https://osrs-map.com", "ABC234", identity.id(), SECRET, target, new WorldPoint(3222, 3218, 0))
			.body().writeTo(buffer);
		return buffer.readString(StandardCharsets.UTF_8);
	}

	/** The picture's place beside the destination's name. */
	private JLabel picture()
	{
		JLabel found = named(cards, "now.destination.picture");
		assertNotNull(found);
		return found;
	}

	private static JLabel named(Component component, String name)
	{
		if (component instanceof JLabel && name.equals(component.getName()))
		{
			return (JLabel) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JLabel found = named(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Pin destination on a precise star, as StarsPanel makes it: no route. */
	private static RelayResponse.Destination mine()
	{
		RelayResponse.Destination star = new RelayResponse.Destination();
		star.x = 2974;
		star.y = 3240;
		star.plane = 0;
		star.name = "Rimmington mine";
		star.world = 302;
		star.note = "T7 · 16m left";
		return star;
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

	/** The words the cards show, in order: every label shown, inside parents that are shown. */
	private List<String> words()
	{
		List<String> out = new ArrayList<>();
		collectWords(cards, out);
		return out;
	}

	private static void collectWords(Component component, List<String> out)
	{
		if (!component.isVisible())
		{
			return;
		}
		if (component instanceof JLabel && ((JLabel) component).getText() != null)
		{
			out.add(((JLabel) component).getText().replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">")
				.replace("&amp;", "&").trim());
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collectWords(child, out);
			}
		}
	}

	/** What a press asked the map, as its body says it. */
	private JsonObject question(Request request) throws Exception
	{
		Buffer buffer = new Buffer();
		request.body().writeTo(buffer);
		return gson.fromJson(buffer.readString(StandardCharsets.UTF_8), JsonObject.class);
	}

	private <T> T construct(Class<T> type) throws Exception
	{
		Constructor<T> make = type.getDeclaredConstructor(Client.class);
		make.setAccessible(true);
		return make.newInstance(game.client());
	}

	/**
	 * Calls run on the thread that makes them and are answered here, never sent: a press has landed
	 * when it returns, and no test waits on another thread for it.
	 */
	private OkHttpClient answering()
	{
		AbstractExecutorService inline = new AbstractExecutorService()
		{
			@Override
			public void execute(Runnable command)
			{
				command.run();
			}

			@Override
			public void shutdown()
			{
			}

			@Override
			public List<Runnable> shutdownNow()
			{
				return Collections.emptyList();
			}

			@Override
			public boolean isShutdown()
			{
				return false;
			}

			@Override
			public boolean isTerminated()
			{
				return false;
			}

			@Override
			public boolean awaitTermination(long timeout, TimeUnit unit)
			{
				return true;
			}
		};
		return new OkHttpClient.Builder().dispatcher(new Dispatcher(inline)).addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("")
				.body(ResponseBody.create(MediaType.get("application/json"), body)).build();
		}).build();
	}
}
