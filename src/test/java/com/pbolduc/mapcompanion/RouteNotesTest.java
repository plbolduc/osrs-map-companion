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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The line a Route press writes under the destination's name is written once and never redrawn, so
 * it says nothing that ages: no star's tier or time left, no countdown to a meetup, and no distance a
 * search measured from where it was typed.
 *
 * <p>Each press goes through its own button and a real {@link RoutePress}, answered by an
 * interceptor, and the test reads the destination the card then shows.
 */
public class RouteNotesTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final DestinationState destinations = new DestinationState(new Gson());
	private final DestinationRoute route = new DestinationRoute();

	@Test
	public void aMeetupsDayAndClockAreSaidWithoutACountdown()
	{
		/* A Wednesday evening. */
		long now = at(9, 30, 18, 10);
		assertEquals("Today 7:30 PM", MapCompanionPanel.meetupStarts(at(9, 30, 19, 30), now, LONDON, Locale.US));
		assertEquals("Tomorrow 6:42 AM", MapCompanionPanel.meetupStarts(at(10, 1, 6, 42), now, LONDON, Locale.US));
		assertEquals("Saturday 20:00", MapCompanionPanel.meetupStarts(at(10, 3, 20, 0), now, LONDON, Locale.UK));
		assertEquals("a week out is a date, never a weekday that could mean two", "8 Oct 9:00 AM",
			MapCompanionPanel.meetupStarts(at(10, 8, 9, 0), now, LONDON, Locale.US));
	}

	@Test
	public void aStarIsPinnedAndRoutedWithNoNoteThatAges() throws Exception
	{
		connect();
		RoutePress press = press();
		AtomicReference<StarsPanel> stars = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, now(), config, pairing, identity, store);
			stars.set(new StarsPanel(service, destinations, now(), marker(), press, () -> MapCompanionConfig.DEFAULT_MAP_URL,
				route, null, null, null, rerouting -> null, () -> { }));
			service.model.accept(new Gson().fromJson(new InputStreamReader(RouteNotesTest.class.getResourceAsStream(
				"/companion-stars.sample.json"), StandardCharsets.UTF_8), StarsModel.Feed.class));
			stars.get().refresh();
			((AbstractButton) named(stars.get(), "stars.site.tile:2974,3241")).doClick();
			((AbstractButton) named(stars.get(), "stars.pin")).doClick();
			assertEquals(Integer.valueOf(302), destinations.getDestination().world);
			assertNull("the pin's card says its world and nothing that ages", destinations.getDestination().note);

			((AbstractButton) named(stars.get(), "stars.route")).doClick();
		});
		settle(press, StarsPanel.SOURCE);
		RelayResponse.Destination answered = destinations.getDestination();
		assertEquals("Rimmington mine", answered.name);
		assertEquals(Integer.valueOf(302), answered.world);
		assertNull("nor does the route's", answered.note);
	}

	@Test
	public void aMeetupOnGroupIsRoutedWithItsDayAndClock() throws Exception
	{
		connect();
		RoutePress press = press();
		long startsAt = System.currentTimeMillis() + 80 * 60_000L;
		RelayResponse.Directive directive = new RelayResponse.Directive();
		directive.events = Collections.singletonList(meetup(startsAt));
		destinations.acceptSocial(directive, true);
		SwingUtilities.invokeAndWait(() ->
		{
			GroupState group = new GroupState(destinations);
			MeetupClient meetups = new MeetupClient(new OkHttpClient(), new Gson(), MeetupClientTest.game(false), now(), pairing,
				identity, destinations, group, link -> { });
			GroupPanel panel = new GroupPanel(destinations, group, press, meetups, config, new GameSprites(null, null, null),
				MapCompanionPanel.smallButton("Copy invite"));
			panel.refresh(true);
			((AbstractButton) named(panel, "group.meetupRoute.star-hunt")).doClick();
		});
		settle(press, "meetup:star-hunt");
		String note = destinations.getDestination().note;
		assertEquals(MapCompanionPanel.meetupStarts(startsAt, System.currentTimeMillis(), ZoneId.systemDefault(),
			Locale.getDefault(Locale.Category.FORMAT)), note);
		assertFalse("no countdown: " + note, note.contains(" in "));
	}

	@Test
	public void theMeetupUnderNowIsRoutedWithItsDayAndClock() throws Exception
	{
		connect();
		RoutePress press = press();
		long startsAt = System.currentTimeMillis() + 45 * 60_000L;
		RelayResponse.Directive directive = new RelayResponse.Directive();
		directive.event = new RelayResponse.Event();
		directive.event.title = "Star hunt";
		directive.event.x = 3222;
		directive.event.y = 3218;
		directive.event.world = 302;
		directive.event.startsAtMs = startsAt;
		destinations.accept(directive);
		SwingUtilities.invokeAndWait(() ->
		{
			/* Only the meetup card is pressed, so the cards it never reaches are left out. */
			NowCards now = new NowCards(config, destinations, null, marker(), route, null, null, press, null, null, null,
				() -> { }, () -> MapCompanionConfig.DEFAULT_MAP_URL);
			List<JButton> routes = new ArrayList<>();
			buttons(now, "Route", routes);
			/* The meetup's card comes before the clue's. */
			routes.get(0).doClick();
		});
		settle(press, "meetup");
		String note = destinations.getDestination().note;
		assertEquals(MapCompanionPanel.meetupStarts(startsAt, System.currentTimeMillis(), ZoneId.systemDefault(),
			Locale.getDefault(Locale.Category.FORMAT)), note);
		assertFalse("no countdown: " + note, note.contains(" in "));
	}

	/** The row keeps the distance it measured; the card under it carries the route's own length instead. */
	@Test
	public void aSearchPickWritesTheRowsWordsWithoutItsDistance() throws Exception
	{
		connect();
		RoutePress press = press();
		SearchClient.Answer answer = new Gson().fromJson("{\"rows\":[{\"id\":\"bank:lumbridge\",\"kind\":\"bank\","
			+ "\"name\":\"Lumbridge Castle bank\",\"line\":\"Bank · Lumbridge Castle · 2nd floor\",\"x\":3208,\"y\":3220,"
			+ "\"plane\":2,\"effortTiles\":40}]}", SearchClient.Answer.class);
		SearchClient.Hit hit = SearchClient.normalize(answer).get(0);
		assertEquals("the row still says how far", "Bank · Lumbridge Castle · 2nd floor · ~40 tiles", hit.line);
		SwingUtilities.invokeAndWait(() -> new SearchPanel(new SearchClient(new OkHttpClient(), new Gson(), pairing, identity), press, null)
			.pick(hit));
		settle(press, SearchPanel.SOURCE);
		assertEquals("Bank · Lumbridge Castle · 2nd floor", destinations.getDestination().note);
	}

	private static long at(int month, int day, int hour, int minute)
	{
		return LocalDateTime.of(2026, month, day, hour, minute).atZone(LONDON).toInstant().toEpochMilli();
	}

	private static RelayResponse.Meetup meetup(long startsAt)
	{
		RelayResponse.Meetup meetup = new RelayResponse.Meetup();
		meetup.id = "star-hunt";
		meetup.title = "Star hunt";
		meetup.x = 3222;
		meetup.y = 3218;
		meetup.world = 302;
		meetup.startsAtMs = startsAt;
		meetup.who = new ArrayList<>();
		return meetup;
	}

	private void connect()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
	}

	/** A press as the sidebar makes one, its call answered at once with RoutePressTest's one way there. */
	private RoutePress press()
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder().request(chain.request())
			.protocol(Protocol.HTTP_1_1).code(200).message("").body(ResponseBody.create(JSON, RoutePressTest.ANSWER)).build()).build();
		return new RoutePress(http, new Gson(), RoutePressTest.game(), now(), pairing, identity, route, destinations, marker());
	}

	private static DestinationMarker marker()
	{
		return new DestinationMarker(null, null, null, null, null, null)
		{
			@Override
			void sync()
			{
			}
		};
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

	private static void settle(RoutePress press, String source) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 3_000;
		while ((press.said(source) == null || press.said(source).planning) && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertNotNull("the press was made", press.said(source));
		assertFalse("the answer landed", press.said(source).planning);
		assertNull(press.said(source).message);
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

	private static void buttons(Container parent, String text, List<JButton> found)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && text.equals(((JButton) child).getText()))
			{
				found.add((JButton) child);
			}
			if (child instanceof Container)
			{
				buttons((Container) child, text, found);
			}
		}
	}
}
