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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
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
 * The Group section's buttons: what each press sends, and that nothing is sent or followed without
 * one — the roster arriving routes nobody, and a friend's dot arriving routes only a friend asked for.
 */
public class GroupPanelTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationState destinations = new DestinationState(new Gson());
	private final DestinationRoute route = new DestinationRoute();
	private final GroupState group = new GroupState(destinations);
	private final List<Request> sent = new CopyOnWriteArrayList<>();

	private OkHttpClient http(String body)
	{
		return new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(200).message("").body(ResponseBody.create(JSON, body)).build();
		}).build();
	}

	private static ClientThread now()
	{
		return new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		};
	}

	private GroupPanel panel()
	{
		return panel(http("{\"eventId\":\"star-hunt\",\"answer\":\"going\"}"));
	}

	/** The panel, its meetup answers and Meet here going through {@code meetupHttp}. */
	private GroupPanel panel(OkHttpClient meetupHttp)
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		RoutePress press = new RoutePress(http(RoutePressTest.ANSWER), new Gson(), MeetupClientTest.game(false), now(),
			pairing, identity, route, destinations, new DestinationMarker(null, null, null, null, null, null)
			{
				@Override
				void sync()
				{
				}
			});
		meetups = new MeetupClient(meetupHttp, new Gson(),
			MeetupClientTest.game(false), now(), pairing, identity, destinations, group, link -> { });
		return new GroupPanel(destinations, group, press, meetups, config, pictures);
	}

	/** The panel's meetup writes, for a test that waits on one. */
	private MeetupClient meetups;

	/** What the panel asked the game's sprites for, by the label it asked for. */
	private final List<String> drawn = new CopyOnWriteArrayList<>();
	private final GameSprites pictures = new GameSprites(null, null, null)
	{
		@Override
		void item(javax.swing.JLabel label, int itemId, int box)
		{
			drawn.add(label.getName() + " item " + itemId);
		}

		@Override
		void skill(javax.swing.JLabel label, String name, int box)
		{
			drawn.add(label.getName() + " skill " + name);
		}

		@Override
		void sprite(javax.swing.JLabel label, int spriteId, int box)
		{
			drawn.add(label.getName() + " sprite " + spriteId);
		}
	};

	/**
	 * A friend's news carries its own picture, as the website's friend rows do: the item a drop
	 * names, the skill a level names, and for a quest or a diary, which name neither, the game's own
	 * mark for one — so a quest's line starts in the same column as the rest rather than at the edge.
	 */
	@Test
	public void aFriendsNewsAsksForTheItemOrTheSkillItNames() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		assertTrue(drawn.toString(), drawn.contains("group.news.cara skill Woodcutting"));
		assertTrue(drawn.toString(), drawn.contains("group.news.dax item 4151"));
		assertTrue("Rob's quest has the world map's quest-start mark: " + drawn,
			drawn.contains("group.news.rob sprite " + net.runelite.api.gameval.SpriteID.Mapfunction.QUEST_START));
		assertNotNull("and holds its place before it lands, as a pictured line does",
			((javax.swing.JLabel) namedComponent(panel.get(), "group.news.rob")).getIcon());
		assertEquals("a diary has the diary tab's icon", net.runelite.api.gameval.SpriteID.SideIcons.ACHIEVEMENT_DIARIES,
			GroupPanel.progressSprite("diary"));
		assertEquals("a level or a drop names its own picture", 0, GroupPanel.progressSprite("level"));
	}

	/**
	 * A group of one says so where the friends will be. Most groups start this way now that Connect
	 * makes one for a player alone; a blank tab read as "nothing works". The card comes with the map's
	 * empty roster, not when the next minute redraws the rows, and goes the moment a friend arrives.
	 *
	 * <p>The card held Copy invite while the group's buttons were the tab's foot, a section further
	 * down. They sit over the tabs now, in view from the card, so this asserts the card is the
	 * sentence alone: a second Copy invite two rows under the first would be the same button twice.
	 */
	@Test
	public void aGroupOfOneSaysSoAtOnce() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.set(panel());
			/* Drawn before the map has sent a roster at all: nothing to say yet. */
			panel.get().refresh(true);
		});
		assertFalse(SidebarWords.words(panel.get()).contains(GroupPanel.ALONE));

		RelayResponse.StreamDirective alone = new Gson().fromJson("{\"roster\":[],\"events\":[]}",
			RelayResponse.StreamDirective.class);
		destinations.acceptSocial(alone, true);
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		assertTrue("the card comes with the empty roster, within the same minute: " + SidebarWords.words(panel.get()),
			SidebarWords.words(panel.get()).contains(GroupPanel.ALONE));
		assertFalse("the card is the sentence alone", holdsButton(namedComponent(panel.get(), "group.alone")));

		social();
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		assertFalse(SidebarWords.words(panel.get()).contains(GroupPanel.ALONE));
	}

	/** Whether {@code component} is a button or holds one. */
	private static boolean holdsButton(Component component)
	{
		if (component instanceof AbstractButton)
		{
			return true;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				if (holdsButton(child))
				{
					return true;
				}
			}
		}
		return false;
	}

	private void social() throws Exception
	{
		social(System.currentTimeMillis() + 3_600_000L);
	}

	/** The contract's group of three with two meetups: its star hunt in an hour, and a raid tomorrow on W416. */
	private void twoMeetups() throws Exception
	{
		String stream = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "stream.json")),
			StandardCharsets.UTF_8);
		RelayResponse.StreamDirective directive = new Gson().fromJson(stream, RelayResponse.StreamDirective.class);
		long now = System.currentTimeMillis();
		directive.events.get(0).startsAtMs = now + 3_600_000L;
		RelayResponse.Meetup raid = new Gson().fromJson(new Gson().toJson(directive.events.get(0)), RelayResponse.Meetup.class);
		raid.id = "raids";
		raid.title = "Chambers of Xeric";
		raid.world = 416;
		raid.startsAtMs = now + 26 * 3_600_000L;
		directive.events.add(raid);
		destinations.acceptSocial(directive, true);
	}

	/** The contract's group of three, its one meetup starting at {@code startsAtMs}. */
	private void social(long startsAtMs) throws Exception
	{
		String stream = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "stream.json")),
			StandardCharsets.UTF_8);
		RelayResponse.StreamDirective directive = new Gson().fromJson(stream, RelayResponse.StreamDirective.class);
		directive.events.get(0).startsAtMs = startsAtMs;
		destinations.acceptSocial(directive, true);
	}

	private static AbstractButton named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof AbstractButton && name.equals(child.getName()))
			{
				return (AbstractButton) child;
			}
			if (child instanceof Container)
			{
				AbstractButton found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void until(java.util.function.BooleanSupplier done) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while (!done.getAsBoolean() && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertTrue(done.getAsBoolean());
	}

	@Test
	public void theRosterArrivingRoutesNobodyAndFollowsNothing() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		assertNotNull("Cara's row has Route", named(panel.get(), "group.route.cara"));
		assertNull("Rob shares no position, so his row has no buttons", named(panel.get(), "group.show.rob"));
		assertTrue("nothing goes until a press", sent.isEmpty());
		assertNull(destinations.getDestination());

		group.dots(Collections.singletonList(dot("cara", false)), System.currentTimeMillis());
		assertNull("a dot arriving routes only a friend asked for", group.arrived(Collections.singletonList(dot("cara", false)),
			System.currentTimeMillis()));
		assertTrue(sent.isEmpty());
	}

	/**
	 * A friend's Route is on their name's line, as a meetup's is beside its title, so Finding…,
	 * Planning… and Reroute read beside the name they are about. World map sits under Route in the
	 * same first row: it had a full-width row of its own under the card, a row per friend.
	 */
	@Test
	public void aFriendsButtonsSitBesideTheirName() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			Container card = (Container) namedComponent(panel.get(), "group.friend.cara");
			assertTrue("Route is on the name's line, the card's first row",
				SwingUtilities.isDescendingFrom(named(panel.get(), "group.route.cara"), card.getComponent(0)));
			assertTrue("World map is in the same first row, under Route",
				SwingUtilities.isDescendingFrom(named(panel.get(), "group.show.cara"), card.getComponent(0)));
			assertSame("one stack with Route, under it",
				named(panel.get(), "group.route.cara").getParent(), named(panel.get(), "group.show.cara").getParent());
		});
	}

	/**
	 * Remove from group… is a friend card's right-click, offered only while the status card says this
	 * RuneLite runs the group, and asked at the click: the rows are not rebuilt when that changes.
	 */
	@Test
	public void aFriendsCardOffersRemoveOnlyToWhoeverRunsTheGroup() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		java.util.concurrent.atomic.AtomicBoolean runs = new java.util.concurrent.atomic.AtomicBoolean(false);
		List<String> removed = new CopyOnWriteArrayList<>();
		RelayResponse.RosterRow cara = new RelayResponse.RosterRow();
		cara.id = "cara";
		cara.name = "Cara";
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().remover(new GroupPanel.Remover()
			{
				@Override
				public boolean offered()
				{
					return runs.get();
				}

				@Override
				public void remove(String memberId, String name)
				{
					removed.add(memberId + "/" + name);
				}
			});
			panel.get().refresh(true);
			Component name = named(panel.get(), "group.route.cara");
			assertTrue("the card's parts answer a right-click", name.getMouseListeners().length > 0);
			assertNull("nothing for a member who does not run the group", panel.get().friendMenu(cara));
			runs.set(true);
			javax.swing.JPopupMenu menu = panel.get().friendMenu(cara);
			assertNotNull(menu);
			javax.swing.JMenuItem item = (javax.swing.JMenuItem) menu.getComponent(0);
			assertEquals(GroupPanel.REMOVE, item.getText());
			item.doClick();
		});
		assertEquals(Collections.singletonList("cara/Cara"), removed);
	}

	@Test
	public void routeToAFriendWaitsForTheirDotThenGoesOnceToWhereTheyStand() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		long now = System.currentTimeMillis();
		assertFalse(group.wantsPositions(now));
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.route.cara").doClick();
		});
		assertTrue("no dot yet: the stream is asked for positions", group.wantsPositions(now));
		assertTrue(group.finding("cara", now));
		assertTrue(sent.isEmpty());

		RelayResponse.MemberDot dot = group.arrived(Collections.singletonList(dot("cara", false)), now);
		assertNotNull(dot);
		assertNull("once", group.arrived(Collections.singletonList(dot("cara", false)), now));
		RoutePress.Target target = GroupState.routeTarget(dot, destinations.getRoster());
		assertEquals("Cara", target.name);
		assertEquals("near Lumbridge Castle", target.note);
		assertEquals(3210, target.tile.getX());
		assertEquals(Integer.valueOf(302), target.world);
		assertFalse("asking for positions stops once the dot came", group.wantsPositions(now));
	}

	/**
	 * A friend inside an instance has no tile the map can plan to, so Route asks nothing. Their row
	 * says so and what fixes it, where Route used to go back to Route without a word — whether their
	 * dot was fresh at the press or came with the frame the press asked for.
	 */
	@Test
	public void aFriendInsideAnInstanceIsNotRoutedAndTheirRowSaysWhy() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		group.dots(Collections.singletonList(dot("cara", true)), System.currentTimeMillis());
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.route.cara").doClick();
		});
		assertTrue("nothing is asked for a friend the map cannot place", sent.isEmpty());
		assertTrue(group.inside("cara"));
		String said = "Cara is inside an instance. Route works once they're out.";
		assertTrue(words(panel.get()), words(panel.get()).contains(said));

		/* Out again and pressed again: the line goes with the press. */
		group.dots(Collections.singletonList(dot("cara", false)), System.currentTimeMillis());
		SwingUtilities.invokeAndWait(() -> named(panel.get(), "group.route.cara").doClick());
		assertFalse(group.inside("cara"));

		/* The same when the press had to wait for the dot, and it came from inside. */
		group.stream(false, 0L);
		long now = System.currentTimeMillis();
		assertNull(group.routeTo("cara", now));
		RelayResponse.MemberDot arrived = group.arrived(Collections.singletonList(dot("cara", true)), now);
		assertTrue(arrived.instance);
		assertTrue(group.inside("cara"));
	}

	/** Every label's words, for a sentence that has no button to be found by. */
	private static String words(Container parent)
	{
		StringBuilder sb = new StringBuilder();
		for (Component child : parent.getComponents())
		{
			if (child instanceof javax.swing.JLabel && child.isVisible())
			{
				sb.append(((javax.swing.JLabel) child).getText()).append('\n');
			}
			if (child instanceof Container)
			{
				sb.append(words((Container) child));
			}
		}
		return sb.toString();
	}

	@Test
	public void keepAlivesKeepAStationaryFriendRoutableWithoutNewFrames()
	{
		RelayResponse.MemberDot cara = dot("cara", false);
		group.dots(Collections.singletonList(cara), 1_000L);
		group.stream(true, 62_000L);
		org.junit.Assert.assertSame(cara, group.routeTo("cara", 62_001L));
		assertFalse(group.finding("cara", 62_001L));
		assertFalse(group.wantsPositions(62_001L));
		assertNull("a connection that stopped delivering cannot renew a dot forever",
			group.routeTo("cara", 62_000L + GroupState.DOT_FRESH_MS));
	}

	@Test
	public void aDisconnectedOrReplacedStreamCannotKeepOldDots()
	{
		group.dots(Collections.singletonList(dot("cara", false)), 1_000L);
		group.stream(false, 0L);
		assertNull(group.routeTo("cara", 1_001L));
		group.stream(true, 1_002L);
		assertNull("a new connection's keep-alive is not a new position frame", group.routeTo("cara", 1_003L));
	}

	@Test
	public void showOnMapAsksForPositionsAndDrawsOnlyThatFriend() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			/* Named for the game's world map it draws on; the website's buttons say Open website. */
			assertEquals("World map", named(panel.get(), "group.show.cara").getText());
			named(panel.get(), "group.show.cara").doClick();
		});
		assertTrue(group.shown("cara"));
		assertTrue(group.wantsPositions(System.currentTimeMillis()));
		List<RelayResponse.MemberDot> drawn = group.shownDots(Arrays.asList(dot("cara", false), dot("dax", true)));
		assertEquals(1, drawn.size());
		assertEquals("cara", drawn.get(0).id);
		assertTrue("showing sends nothing of the player's", sent.isEmpty());

		destinations.clear();
		assertFalse("a friend shown in one group is not asked for in the next", group.shown("cara"));
	}

	/** On my way is one of the four answers; there is no second button that says it. */
	@Test
	public void theAnswerButtonsSendTheMapsWordsAndRouteAnswersNothing() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		for (String answer : SocialWire.ANSWERS)
		{
			assertNotNull(answer, named(panel.get(), "group.answer.star-hunt." + answer));
		}
		SwingUtilities.invokeAndWait(() -> named(panel.get(), "group.answer.star-hunt.no").doClick());
		until(() -> sent.size() == 1);
		Request request = sent.get(0);
		assertEquals("/api/companion/event/answer", request.url().encodedPath());
		assertEquals("{\"eventId\":\"star-hunt\",\"answer\":\"no\"}", MeetupClientTest.body(request).toString());

		SwingUtilities.invokeAndWait(() ->
		{
			/* Route is on the title's line, the card's first row, rather than a row of its own. */
			Container card = (Container) namedComponent(panel.get(), "group.meetup.star-hunt");
			Component route = named(panel.get(), "group.meetupRoute.star-hunt");
			assertTrue(javax.swing.SwingUtilities.isDescendingFrom(route, card.getComponent(0)));
			named(panel.get(), "group.meetupRoute.star-hunt").doClick();
		});
		until(() -> sent.size() == 2);
		assertEquals("Route goes to the route endpoint and answers nothing", "/api/companion/route",
			sent.get(1).url().encodedPath());
	}

	/**
	 * The brown fill means this member's pick, and keeps meaning it while the pick is sent and in the
	 * pause after. The answer pressed is filled while it is on its way, where every answer greyed and
	 * none was filled; it stays enabled, because FlatLaf paints a disabled button without its own
	 * background, and ignores presses, and only the other three are disabled.
	 */
	@Test
	public void thePressedAnswerKeepsItsFillWhileItIsSent() throws Exception
	{
		java.util.concurrent.CountDownLatch mapAnswers = new java.util.concurrent.CountDownLatch(1);
		OkHttpClient held = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			try
			{
				mapAnswers.await(5, java.util.concurrent.TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
				.body(ResponseBody.create(JSON, "{\"eventId\":\"star-hunt\",\"answer\":\"going\"}")).build();
		}).build();
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel(held)));
		social();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertEquals("Maybe is this member's answer", GroupPanel.CHOSEN, named(panel.get(), "group.answer.star-hunt.maybe").getBackground());
			named(panel.get(), "group.answer.star-hunt.going").doClick();
		});
		until(() -> sent.size() == 1);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			AbstractButton going = named(panel.get(), "group.answer.star-hunt.going");
			assertEquals("the answer on its way is the one filled", GroupPanel.CHOSEN, going.getBackground());
			assertTrue("and enabled, so its fill is painted", going.isEnabled());
			for (String other : Arrays.asList("maybe", "no", "on_my_way"))
			{
				AbstractButton button = named(panel.get(), "group.answer.star-hunt." + other);
				assertFalse(other + " waits", button.isEnabled());
				assertNotEquals(other + " is not filled", GroupPanel.CHOSEN, button.getBackground());
			}
			going.doClick();
		});
		assertEquals("pressing the pick while it is sent sends nothing", 1, sent.size());

		mapAnswers.countDown();
		until(() ->
		{
			MeetupClient.Said said = meetups.said("answer:star-hunt");
			return said != null && !said.sending;
		});
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			AbstractButton going = named(panel.get(), "group.answer.star-hunt.going");
			assertEquals("the map took it, and it stays filled through the pause after", GroupPanel.CHOSEN, going.getBackground());
			assertTrue(going.isEnabled());
		});
	}

	/**
	 * The next meetup whole, with its answers; each after it one line, its title, day, clock and world,
	 * that opens to the same card in place and folds again, as the website lists a group's meetups.
	 */
	@Test
	public void aLaterMeetupIsOneLineThatOpensToItsCard() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		twoMeetups();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertNotNull("the next meetup is whole", named(panel.get(), "group.answer.star-hunt.going"));
			assertNull("and has no line of its own", namedComponent(panel.get(), "group.meetupFold.star-hunt"));
			assertNull("the later one is folded", namedComponent(panel.get(), "group.meetup.raids"));
			AbstractButton line = named(panel.get(), "group.meetupFold.raids");
			long raidAt = destinations.getEvents().stream().filter(m -> m.id.equals("raids")).findFirst().get().startsAtMs;
			String at = GroupPanel.dayAndClock(raidAt, System.currentTimeMillis(), ZoneId.systemDefault(),
				Locale.getDefault(Locale.Category.FORMAT));
			assertEquals("▸ Chambers of Xeric · " + at + " · W416", plain(line));
			line.doClick();
			assertNotNull("pressed, it opens to the same card", namedComponent(panel.get(), "group.meetup.raids"));
			assertNotNull(named(panel.get(), "group.answer.raids.going"));
			assertTrue(plain(named(panel.get(), "group.meetupFold.raids")).startsWith("▾ "));
			named(panel.get(), "group.meetupFold.raids").doClick();
			assertNull("and folds again", namedComponent(panel.get(), "group.meetup.raids"));
		});
	}

	/** Meet here is on the Meetups heading, which is drawn with no meetup under it, so the press is always there. */
	@Test
	public void meetHereIsOnTheMeetupsHeadingEvenWithNoMeetup() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.set(panel());
			panel.get().refresh(true);
			Component head = namedComponent(panel.get(), "group.meetupsHead");
			assertNotNull(head);
			assertTrue("the heading is drawn with nothing under it", head.isVisible());
			assertSame("Meet here is the heading's own link", head, named(panel.get(), "group.meetHere").getParent());
		});
	}

	/**
	 * Meet here… opens its form under the heading it is on, above the meetups, and the form's answer is
	 * said there too, rather than under the last meetup card, a screen below the press with a few.
	 */
	@Test
	public void meetHereOpensItsFormUnderItsLink() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		twoMeetups();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.meetHere").doClick();
			int head = row(panel.get(), namedComponent(panel.get(), "group.meetupsHead"));
			int form = row(panel.get(), namedComponent(panel.get(), "group.meetTitle"));
			int said = row(panel.get(), namedComponent(panel.get(), "group.meetSaid"));
			int meetups = row(panel.get(), namedComponent(panel.get(), "group.meetup.star-hunt"));
			assertEquals("the form is the row under the heading", head + 1, form);
			assertTrue("and what it says is above the meetups too", said < meetups);
		});
	}

	/**
	 * "Meetup made. Its link is copied." closed the form and then stayed under the heading for good,
	 * and kept closing the form: opened again for a second meetup, it shut on the next refresh. The
	 * confirmation is news for ten seconds, as a run's "Run ended." is, and closes the form once.
	 */
	@Test
	public void aMeetupMadeIsSaidForAMomentAndTheFormOpensAgain() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel(http("{\"path\":\"/map?event=abc123&task=companion\"}"))));
		twoMeetups();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.meetHere").doClick();
			panel.get().title().setText("Barrows run");
			named(panel.get(), "group.meetMake").doClick();
		});
		until(() -> meetups.said("meet") != null && !meetups.said("meet").sending);
		long made = meetups.said("meet").atMs;
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true, made);
			assertFalse("made, the form puts itself away", shownIn(panel.get(), panel.get().title()));
			Component said = namedComponent(panel.get(), "group.meetSaid");
			assertTrue(shownIn(panel.get(), said));
			assertEquals(Collections.singletonList(MeetupClient.MADE), SidebarWords.words(said));
			panel.get().refresh(true, made + FarmRunClient.Said.CONFIRMATION_MS);
			assertFalse("ten seconds on it has given its line back", shownIn(panel.get(), said));

			named(panel.get(), "group.meetHere").doClick();
			panel.get().refresh(true, made + FarmRunClient.Said.CONFIRMATION_MS);
			assertTrue("the form opens for the next meetup and stays open", shownIn(panel.get(), panel.get().title()));
		});
	}

	/**
	 * A refusal the form said stayed under the heading once Cancel had put the form away, about a
	 * form no longer there. It stays in the form until the next press, and goes with Cancel.
	 */
	@Test
	public void aRefusalTheFormSaidGoesWithCancel() throws Exception
	{
		OkHttpClient refusing = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(400).message("").body(ResponseBody.create(JSON, "{\"reason\":\"invalid\"}")).build();
		}).build();
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel(refusing)));
		twoMeetups();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.meetHere").doClick();
			panel.get().title().setText("Barrows run");
			named(panel.get(), "group.meetMake").doClick();
		});
		until(() -> meetups.said("meet") != null && !meetups.said("meet").sending);
		SwingUtilities.invokeAndWait(() ->
		{
			long minuteOn = System.currentTimeMillis() + 60_000L;
			panel.get().refresh(true, minuteOn);
			assertTrue("a refusal is still the form's a minute on", SidebarWords.words(panel.get()).contains(MeetupClient.REFUSED));
			assertTrue(SidebarWords.press(panel.get(), "Cancel"));
			panel.get().refresh(true, minuteOn);
			assertFalse(shownIn(panel.get(), namedComponent(panel.get(), "group.meetSaid")));
		});
	}

	/**
	 * Every line under a Group press was one grey, so "Cara is not sharing a position just now." read
	 * like her row's "near Lumbridge Castle". A press that did not happen is said in the refusal pink;
	 * "Meetup made. Its link is copied." keeps the line's grey, since that press did what it asked.
	 */
	@Test
	public void aRefusedPressIsPinkAndAMeetupMadeIsNot() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel(http("{\"path\":\"/map?event=abc123&task=companion\"}"))));
		social();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.route.cara").doClick();
		});
		/* A frame with positions came, and Cara was not in it. */
		group.arrived(Collections.emptyList(), System.currentTimeMillis());
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertEquals(MapCompanionPanel.REFUSED, saying(panel.get(), "Cara is not sharing a position just now.").getForeground());
			named(panel.get(), "group.meetHere").doClick();
			panel.get().title().setText("Barrows run");
			named(panel.get(), "group.meetMake").doClick();
		});
		until(() -> meetups.said("meet") != null && !meetups.said("meet").sending);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertEquals(ColorScheme.LIGHT_GRAY_COLOR, namedComponent(panel.get(), "group.meetSaid").getForeground());
		});
	}

	/**
	 * A meetup answer refused while the map was busy said "Try again in a few seconds." for twenty.
	 * The line under the answers counts the meetups' own wait down on every refresh, between two
	 * rebuilds of the card, and says so once it is over.
	 */
	@Test
	public void aBusyAnswerCountsTheMeetupsWaitDown() throws Exception
	{
		OkHttpClient busy = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("")
				.header("Retry-After", "20").body(ResponseBody.create(JSON, "{\"reason\":\"busy\"}")).build();
		}).build();
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel(busy)));
		social();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			named(panel.get(), "group.answer.star-hunt.going").doClick();
		});
		until(() -> meetups.said("answer:star-hunt") != null && !meetups.said("answer:star-hunt").sending);
		long now = System.currentTimeMillis();
		long opens = now + meetups.waitMs(now);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true, opens - 12_000L);
			assertNotNull(words(panel.get()), saying(panel.get(), "The map is busy. Try again in 12s."));
			panel.get().refresh(true, opens);
			assertNotNull(words(panel.get()), saying(panel.get(), "The map was busy."));
		});
	}

	/**
	 * At the login screen the Group tab's presses that ask the map as a member wait for a character:
	 * a friend's Route, a meetup's answers and Meet here, where each answered "Connect to your map…".
	 * World map, which draws here, stays. A friend's Route pressed then says to log in, where it waited
	 * half a minute for a position the logout had stopped and then blamed the friend for it.
	 */
	@Test
	public void atTheLoginScreenThePressesWaitForACharacter() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		long now = System.currentTimeMillis();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true, false, now);
			AbstractButton route = named(panel.get(), "group.route.cara");
			assertFalse(route.isEnabled());
			assertEquals(RoutePress.LOG_IN, route.getToolTipText());
			assertFalse(named(panel.get(), "group.answer.star-hunt.going").isEnabled());
			assertFalse(named(panel.get(), "group.meetHere").isEnabled());
			assertTrue("World map draws here", named(panel.get(), "group.show.cara").isEnabled());

			panel.get().routeToFriend("cara");
			assertFalse("no position is waited for", group.finding("cara", now));
			assertNotNull(words(panel.get()), saying(panel.get(), RoutePress.LOG_IN));
		});
	}

	/**
	 * A friend's Route pressed just before a logout waited on the stream the logout closed, then half
	 * a minute later said the friend shares no position. At the login screen it stops waiting and says
	 * to log in, as a press made there does, and the friend is not blamed.
	 */
	@Test
	public void aRouteStillFindingAtTheLogoutSaysToLogIn() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		long now = System.currentTimeMillis();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true, true, now);
			named(panel.get(), "group.route.cara").doClick();
		});
		assertTrue("no dot yet: the Route waits for one", group.finding("cara", now));
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true, false, now);
			assertEquals("Route", named(panel.get(), "group.route.cara").getText());
			assertNotNull(words(panel.get()), saying(panel.get(), RoutePress.LOG_IN));
		});
		long later = now + GroupState.PENDING_MS + 1_000L;
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true, false, later);
			assertNull(words(panel.get()), saying(panel.get(), "Cara is not sharing a position just now."));
		});
		assertFalse("the friend is not blamed", group.lost("cara"));
	}

	/** The shown label whose words, markup taken out, are these. */
	private static javax.swing.JLabel saying(Container parent, String words)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof javax.swing.JLabel && child.isVisible() && ((javax.swing.JLabel) child).getText() != null
				&& words.equals(((javax.swing.JLabel) child).getText().replaceAll("<[^>]+>", "")))
			{
				return (javax.swing.JLabel) child;
			}
			if (child instanceof Container)
			{
				javax.swing.JLabel found = saying((Container) child, words);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Whether nothing between a component and the panel is hidden. */
	private static boolean shownIn(Container panel, Component component)
	{
		for (Component at = component; at != null && at != panel; at = at.getParent())
		{
			if (!at.isVisible())
			{
				return false;
			}
		}
		return true;
	}

	/** Which of the panel's own rows holds a component. */
	private static int row(Container panel, Component component)
	{
		Component at = component;
		while (at.getParent() != panel)
		{
			at = at.getParent();
		}
		return Arrays.asList(panel.getComponents()).indexOf(at);
	}

	/**
	 * Destinations and meetups is off until the player turns it on, and while it is off the map's meetups
	 * are not taken, so the section under the heading is empty and a meetup made here never shows.
	 * One line says why and where the setting is, and goes once the setting is on.
	 */
	@Test
	public void meetupsSayWhyTheyAreMissingWhileDestinationsAreOff() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		String stream = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "stream.json")),
			StandardCharsets.UTF_8);
		RelayResponse.StreamDirective directive = new Gson().fromJson(stream, RelayResponse.StreamDirective.class);
		directive.events.get(0).startsAtMs = System.currentTimeMillis() + 3_600_000L;
		/* As the plugin takes a directive: the meetups only with the setting on. */
		assertFalse(config.showDestination());
		destinations.acceptSocial(directive, config.showDestination());
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		Component off = namedComponent(panel.get(), "group.meetupsOff");
		assertTrue("the line is up while the meetups are kept out", off.isVisible());
		assertEquals(Collections.singletonList(GroupPanel.MEETUPS_OFF), SidebarWords.words(off));
		assertNull(namedComponent(panel.get(), "group.meetup.star-hunt"));

		config.set("showDestination", true);
		destinations.acceptSocial(directive, config.showDestination());
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		assertFalse(off.isVisible());
		assertNotNull(namedComponent(panel.get(), "group.meetup.star-hunt"));
	}

	private static Component namedComponent(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = namedComponent((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void meetHereOffersTodayAndASensibleTime() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		ZoneId zone = ZoneId.of("Europe/London");
		long now = ZonedDateTime.of(2026, 10, 29, 18, 7, 0, 0, zone).toInstant().toEpochMilli();
		SwingUtilities.invokeAndWait(() -> panel.get().fillTimes(now, zone, Locale.UK));
		assertEquals("half an hour from now, at the next five minutes",
			ZonedDateTime.of(2026, 10, 29, 18, 40, 0, 0, zone).toInstant().toEpochMilli(), panel.get().chosenStart(zone));
	}

	@Test
	public void saysWhenAsAPersonWould()
	{
		ZoneId zone = ZoneId.of("Europe/London");
		long now = ZonedDateTime.of(2026, 10, 29, 18, 0, 0, 0, zone).toInstant().toEpochMilli();
		assertEquals("Today 7:20 PM · in 1h 20m", GroupPanel.when(now + 80 * 60_000L, now, zone, Locale.US));
		assertEquals("Tomorrow 05:59 · in 11h 59m", GroupPanel.when(now + 719 * 60_000L, now, zone, Locale.UK));
		/*
		 * Past twelve hours the day and the clock say it alone, as the Now line does; this read
		 * "Tomorrow 19:00 · in 1d", the day said twice.
		 */
		assertEquals("Tomorrow 06:00", GroupPanel.when(now + 12 * 3_600_000L, now, zone, Locale.UK));
		assertEquals("Tomorrow 19:00", GroupPanel.when(now + 25 * 3_600_000L, now, zone, Locale.UK));
	}

	/**
	 * Who answered a meetup, as the website rolls them up: each answer somebody gave, its word, then
	 * those people's discs with their initials, Going, On my way, Maybe, Can't and Here in that order,
	 * each disc's name in its tooltip. It was counts, "1 here · 1 coming · 1 maybe", with the names only
	 * in a tooltip, so whether Rob was coming meant hovering over a row of discs.
	 */
	@Test
	public void aMeetupsAnswersAreEachAWordThenItsPeoplesDiscs() throws Exception
	{
		List<RelayResponse.MeetupAnswer> who = Arrays.asList(face("Cara", "arrived"), face("Rob", "on_my_way"),
			face("Sunday", "maybe"), face("Dax", "no"), face("Ash", "going"), face("Tove", "going"));
		assertEquals(Arrays.asList("Going", "On my way", "Maybe", "Can't", "Here"),
			new java.util.ArrayList<>(GroupPanel.rollUp(who).keySet()));
		assertEquals("Going: Ash, Tove · On my way: Rob · Maybe: Sunday · Can't: Dax · Here: Cara", GroupPanel.rollUpWords(who));
		/* A tooltip that starts with <html> is markup to Swing, and a disc's starts with the name a member typed. */
		GroupPanel.RollUp typed = new GroupPanel.RollUp(Collections.singletonList(
			face("<html><img src='https://example.invalid/x.png'>Rob", "going")), MapCompanionPanel.CARD_TEXT_WIDTH);
		for (Component part : typed.getComponents())
		{
			String tip = ((javax.swing.JLabel) part).getToolTipText();
			assertFalse(tip, tip.contains("<") || tip.contains(">"));
		}

		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			Container rollUp = (Container) namedComponent(panel.get(), "group.who.star-hunt");
			List<String> seen = new java.util.ArrayList<>();
			for (Component part : rollUp.getComponents())
			{
				javax.swing.JLabel label = (javax.swing.JLabel) part;
				seen.add(label.getIcon() == null ? label.getText() : "(" + label.getToolTipText() + ")");
				assertTrue(label.getToolTipText() + " stays inside the card",
					part.getX() + part.getWidth() <= MapCompanionPanel.CARD_TEXT_WIDTH);
			}
			assertEquals(Arrays.asList("On my way", "(Rob)", "Maybe", "(Sunday (you))", "Can't", "(Dax)", "Here", "(Cara)"), seen);
		});
	}

	/**
	 * A meetup about to start says so in the map's amber, as the map's own meetup card does: its
	 * countdown, from a quarter of an hour before the start until the line stops saying now, on the
	 * Group tab and on the one meetup under Now. The day, the clock and the world keep the line's own
	 * colour, and a meetup further off is all one colour.
	 */
	@Test
	public void aMeetupAboutToStartHasItsCountdownInAmber() throws Exception
	{
		long now = System.currentTimeMillis();
		assertTrue(GroupPanel.imminent(now + 14 * 60_000L, now));
		assertFalse(GroupPanel.imminent(now + 16 * 60_000L, now));
		assertTrue("under way", GroupPanel.imminent(now - 30 * 60_000L, now));
		assertFalse("started over an hour ago", GroupPanel.imminent(now - 61 * 60_000L, now));

		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social(now + 10 * 60_000L);
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		String card = String.join("\n", labelTexts((Container) namedComponent(panel.get(), "group.meetup.star-hunt")));
		assertTrue(card, card.contains(" · <font color='#f59e0b'>in 10m</font></body>"));

		ZoneId zone = ZoneId.of("Europe/London");
		javax.swing.JLabel line = new javax.swing.JLabel();
		GroupPanel.setWhen(line, MapCompanionPanel.meetupWhen(now + 10 * 60_000L, now, zone, Locale.UK, 302),
			now + 10 * 60_000L, now, 100);
		assertTrue(line.getText(), line.getText().contains("Meetup <font color='#f59e0b'>in 10m</font> · W302"));
		GroupPanel.setWhen(line, MapCompanionPanel.meetupWhen(now + 25 * 60_000L, now, zone, Locale.UK, 302),
			now + 25 * 60_000L, now, 100);
		assertFalse("twenty-five minutes off is only a time: " + line.getText(), line.getText().contains("<font"));
	}

	/**
	 * A meetup's world is green while this client is on it, on its card and on a later meetup's line,
	 * as the game's own friends list greens the world a player is on; on another world, or on none, it
	 * keeps the line's grey. The world comes from the game tick, so a hop recolours it at the next refresh.
	 */
	@Test
	public void aMeetupsWorldIsGreenWhileYouAreOnIt() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		twoMeetups();
		group.onWorld(330);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertFalse(String.join("\n", labelTexts(panel.get())).contains("#34d399"));
			assertFalse(named(panel.get(), "group.meetupFold.raids").getText().contains("#34d399"));
		});
		group.onWorld(302);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			String card = String.join("\n", labelTexts((Container) namedComponent(panel.get(), "group.meetup.star-hunt")));
			assertTrue("on W302 the star hunt's world is green: " + card,
				card.contains("near Lumbridge Castle · <font color='#34d399'>W302</font>"));
			assertFalse(named(panel.get(), "group.meetupFold.raids").getText().contains("#34d399"));
		});
		group.onWorld(416);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertTrue("a hop to W416 greens the raid's line",
				named(panel.get(), "group.meetupFold.raids").getText().contains("<font color='#34d399'>W416</font>"));
			assertFalse(String.join("\n", labelTexts(panel.get())).contains("#34d399"));
		});
		group.onWorld(0);
		SwingUtilities.invokeAndWait(() ->
		{
			panel.get().refresh(true);
			assertFalse("on no world, no world is green", String.join("\n", labelTexts(panel.get())).contains("#34d399"));
			assertFalse(named(panel.get(), "group.meetupFold.raids").getText().contains("#34d399"));
		});
	}

	/** A button's words without the markup that wraps them. */
	private static String plain(AbstractButton button)
	{
		return button.getText().replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
	}

	/** Every label's text under a component, markup and all. */
	private static List<String> labelTexts(Container parent)
	{
		List<String> texts = new java.util.ArrayList<>();
		for (Component child : parent.getComponents())
		{
			if (child instanceof javax.swing.JLabel)
			{
				texts.add(((javax.swing.JLabel) child).getText());
			}
			if (child instanceof Container)
			{
				texts.addAll(labelTexts((Container) child));
			}
		}
		return texts;
	}

	/**
	 * A friend's presence reads on every disc colour: the dot is never one of the map's disc colours,
	 * and a halo of the card's colour two pixels wide cuts it out of the disc where the two meet.
	 */
	@Test
	public void aPresenceDotReadsOnEveryDiscColour()
	{
		/* The map's eight disc colours, as osrs-stars-map's lib/companion-disc.ts lists them. */
		String[] discs = {"#38bdf8", "#a78bfa", "#34d399", "#fb7185", "#facc15", "#f472b6", "#4ade80", "#60a5fa"};
		for (String presence : new String[]{"online", "idle", "offline"})
		{
			Color dot = GroupPanel.presenceColour(presence);
			for (String hex : discs)
			{
				Color disc = GroupPanel.colour(hex);
				String what = presence + " on " + hex;
				assertNotEquals(what, disc.getRGB(), dot.getRGB());
				/*
				 * The disc is the world map's, 18px with initials where it was a plain 13px one, so the
				 * dot sits further in; it is the same dot in the same halo, measured from its corner.
				 */
				BufferedImage drawn = painted(new GroupPanel.Disc(disc, "CA", dot));
				int corner = GroupPanel.Disc.DOT_AT;
				assertEquals(what + ": the middle of the dot", dot.getRGB(), drawn.getRGB(corner + 2, corner + 2));
				/* Left of the dot and above it, where it sits on the disc. */
				for (int[] at : new int[][]{{corner - 2, corner + 2}, {corner - 1, corner + 2}, {corner + 2, corner - 2},
					{corner + 2, corner - 1}})
				{
					assertCard(what + " at " + at[0] + "," + at[1], drawn.getRGB(at[0], at[1]));
				}
			}
		}
	}

	/**
	 * A friend's disc is the one the world map draws for them, so the sidebar and the map show one
	 * picture, and two friends of one colour are told apart by the initials on it.
	 */
	@Test
	public void aFriendsDiscIsTheWorldMapsWithTheirInitials()
	{
		Color pink = GroupPanel.colour("#f472b6");
		Color online = GroupPanel.presenceColour("online");
		BufferedImage cara = painted(new GroupPanel.Disc(pink, MapPin.initials("Cara"), online));
		BufferedImage map = new BufferedImage(cara.getWidth(), cara.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = map.createGraphics();
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(0, 0, map.getWidth(), map.getHeight());
		g.drawImage(MapPin.disc(pink, "CA", false), 0, 0, null);
		g.dispose();
		/* Away from the presence dot and its halo, every pixel is the world map's. */
		double middle = GroupPanel.Disc.DOT_AT + GroupPanel.Disc.DOT / 2.0;
		int compared = 0;
		for (int y = 0; y < cara.getHeight(); y++)
		{
			for (int x = 0; x < cara.getWidth(); x++)
			{
				if (Math.hypot(x + 0.5 - middle, y + 0.5 - middle) > GroupPanel.Disc.DOT / 2.0 + 3)
				{
					assertEquals("at " + x + "," + y, map.getRGB(x, y), cara.getRGB(x, y));
					compared++;
				}
			}
		}
		assertTrue(compared > 300);
		BufferedImage rob = painted(new GroupPanel.Disc(pink, MapPin.initials("Rob"), online));
		boolean told = false;
		for (int y = 0; y < rob.getHeight() && !told; y++)
		{
			for (int x = 0; x < rob.getWidth() && !told; x++)
			{
				told = rob.getRGB(x, y) != cara.getRGB(x, y);
			}
		}
		assertTrue("Cara and Rob are both pink, and their letters tell them apart", told);
	}

	private static BufferedImage painted(GroupPanel.Disc icon)
	{
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		icon.paintIcon(null, g, 0, 0);
		g.dispose();
		return image;
	}

	/** The card's own colour, give or take the antialiasing of the halo's edge. */
	private static void assertCard(String what, int rgb)
	{
		Color seen = new Color(rgb);
		Color card = ColorScheme.DARKER_GRAY_COLOR;
		assertTrue(what + ": " + seen, Math.abs(seen.getRed() - card.getRed()) <= 16
			&& Math.abs(seen.getGreen() - card.getGreen()) <= 16 && Math.abs(seen.getBlue() - card.getBlue()) <= 16);
	}

	private static RelayResponse.MeetupAnswer face(String name, String answer)
	{
		RelayResponse.MeetupAnswer face = new RelayResponse.MeetupAnswer();
		face.name = name;
		face.answer = answer;
		return face;
	}

	private static RelayResponse.MemberDot dot(String id, boolean instance)
	{
		RelayResponse.MemberDot dot = new RelayResponse.MemberDot();
		dot.id = id;
		dot.name = id;
		dot.x = 3210;
		dot.y = 3215;
		dot.world = 302;
		dot.instance = instance;
		return dot;
	}
}
