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
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		RoutePress press = new RoutePress(http(RoutePressTest.ANSWER), new Gson(), MeetupClientTest.game(false), now(),
			pairing, identity, route, destinations, new DestinationMarker(null, null, null, null, null)
			{
				@Override
				void sync()
				{
				}
			});
		MeetupClient meetups = new MeetupClient(http("{\"eventId\":\"star-hunt\",\"answer\":\"going\"}"), new Gson(),
			MeetupClientTest.game(false), now(), pairing, identity, destinations, group, link -> { });
		return new GroupPanel(destinations, group, press, meetups, config);
	}

	private void social() throws Exception
	{
		String stream = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "stream.json")),
			StandardCharsets.UTF_8);
		RelayResponse.StreamDirective directive = new Gson().fromJson(stream, RelayResponse.StreamDirective.class);
		directive.events.get(0).startsAtMs = System.currentTimeMillis() + 3_600_000L;
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

	@Test
	public void theAnswerButtonsSendTheMapsWordsAndOnMyWayIsAPress() throws Exception
	{
		AtomicReference<GroupPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(panel()));
		social();
		SwingUtilities.invokeAndWait(() -> panel.get().refresh(true));
		assertNull("answered maybe already, so no Say on my way", named(panel.get(), "group.onMyWay.star-hunt"));
		SwingUtilities.invokeAndWait(() -> named(panel.get(), "group.answer.star-hunt.no").doClick());
		until(() -> sent.size() == 1);
		Request request = sent.get(0);
		assertEquals("/api/companion/event/answer", request.url().encodedPath());
		assertEquals("{\"eventId\":\"star-hunt\",\"answer\":\"no\"}", MeetupClientTest.body(request).toString());

		SwingUtilities.invokeAndWait(() -> named(panel.get(), "group.meetupRoute.star-hunt").doClick());
		until(() -> sent.size() == 2);
		assertEquals("Route goes to the route endpoint and answers nothing", "/api/companion/route",
			sent.get(1).url().encodedPath());
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
		assertEquals("Tomorrow 19:00 · in 1d", GroupPanel.when(now + 25 * 3_600_000L, now, zone, Locale.UK));
		assertEquals("1 here · 1 coming · 1 maybe", GroupPanel.whoLine(Arrays.asList(face("arrived"), face("on_my_way"),
			face("maybe"), face("no"))));
	}

	private static RelayResponse.MeetupAnswer face(String answer)
	{
		RelayResponse.MeetupAnswer face = new RelayResponse.MeetupAnswer();
		face.name = "M";
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
