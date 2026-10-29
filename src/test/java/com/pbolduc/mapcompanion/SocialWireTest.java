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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The friends and the meetups are read the way a route is: a bad row costs that row, a bad value
 * costs only itself, and a map that sends neither is today's sidebar.
 */
public class SocialWireTest
{
	private final Gson gson = new Gson();

	private RelayResponse read(String directive)
	{
		return gson.fromJson("{\"memberSecret\":\"kept\",\"directive\":" + directive + "}", RelayResponse.class);
	}

	@Test
	public void aMapThatSendsNeitherIsTodaysSidebar()
	{
		RelayResponse reply = read("{\"event\":{\"title\":\"Star hunt\",\"x\":3222,\"y\":3218,\"startsAtMs\":1800000000000}}");
		assertNull(reply.directive.roster);
		assertNull(reply.directive.events);
		assertEquals("the single meetup is still read", "Star hunt", reply.directive.event.title);

		DestinationState state = new DestinationState(gson);
		state.accept(reply.directive);
		state.acceptSocial(reply.directive, true);
		assertNull(state.getRoster());
		assertNull(state.getEvents());
		assertNotNull(state.getEvent());
	}

	@Test
	public void aBadRowCostsThatRowAndNotTheReply()
	{
		RelayResponse reply = read("{\"roster\":["
			+ "{\"id\":\"cara\",\"name\":\"Cara\",\"colour\":\"#f472b6\",\"presence\":\"online\",\"routable\":true},"
			+ "{\"id\":7,\"name\":{\"not\":\"a name\"},\"routable\":\"yes\"},"
			+ "{\"name\":\"no id\"},"
			+ "{\"id\":\"dax\",\"name\":\"<b>Dax</b>\\n\",\"colour\":\"red\",\"presence\":\"asleep\",\"world\":99999,\"nearYou\":false}"
			+ "],\"events\":{\"not\":\"a list\"}}");
		assertEquals("the reply around them stands", "kept", reply.memberSecret);
		assertEquals(2, reply.directive.roster.size());
		assertEquals("cara", reply.directive.roster.get(0).id);
		RelayResponse.RosterRow dax = reply.directive.roster.get(1);
		assertEquals("<b>Dax</b>", dax.name);
		assertNull("a colour that is not #rrggbb is dropped for the fallback", dax.colour);
		assertNull("a presence outside the map's words is not guessed", dax.presence);
		assertNull(dax.world);
		assertNull(dax.nearYou);
		assertNull("a value that is not a list reads as absent", reply.directive.events);
	}

	@Test
	public void aMeetupIsBoundedAndItsAnswersAreTheMapsOwn()
	{
		RelayResponse reply = read("{\"events\":["
			+ "{\"id\":\"hunt\",\"title\":\"  Star\\u0007 hunt  \",\"x\":3222,\"y\":3218,\"plane\":0,\"world\":302,"
			+ "\"startsAtMs\":1800003600000,\"mine\":\"definitely\",\"who\":["
			+ "{\"name\":\"Rob\",\"colour\":\"#f472b6\",\"answer\":\"on_my_way\"},"
			+ "{\"name\":\"Eve\",\"colour\":\"#38bdf8\",\"answer\":\"teleporting\"},"
			+ "{\"name\":\"Cara\",\"colour\":\"#f472b6\",\"answer\":\"arrived\",\"you\":true}]},"
			+ "{\"id\":\"nowhere\",\"title\":\"Off the map\",\"x\":0,\"y\":0,\"startsAtMs\":1800003600000},"
			+ "{\"id\":\"untitled\",\"title\":\"\",\"x\":3222,\"y\":3218,\"startsAtMs\":1800003600000}"
			+ "]}");
		assertEquals(1, reply.directive.events.size());
		RelayResponse.Meetup hunt = reply.directive.events.get(0);
		assertEquals("Star hunt", hunt.title);
		assertNull("an answer outside the four is not this member's", hunt.mine);
		assertEquals(2, hunt.who.size());
		assertEquals("arrived", hunt.who.get(1).answer);
		assertEquals(Boolean.TRUE, hunt.who.get(1).you);
	}

	@Test
	public void theRosterStaysWhenADirectiveCarriesNoneAndTheMeetupsFollowTheirSetting()
	{
		DestinationState state = new DestinationState(gson);
		RelayResponse full = read("{\"roster\":[{\"id\":\"cara\",\"name\":\"Cara\",\"routable\":true}],"
			+ "\"events\":[{\"id\":\"hunt\",\"title\":\"Star hunt\",\"x\":3222,\"y\":3218,\"startsAtMs\":1800003600000}]}");
		state.acceptSocial(full.directive, true);
		assertEquals(1, state.getRoster().size());
		assertEquals(1, state.getEvents().size());

		state.acceptSocial(read("{}").directive, true);
		assertEquals("a directive without them does not empty the list", 1, state.getRoster().size());
		assertEquals(1, state.getEvents().size());

		state.acceptSocial(full.directive, false);
		assertEquals("the roster is read whatever the destination setting", 1, state.getRoster().size());
		assertNull("meetups only while destinations and meetups are shown", state.getEvents());

		state.clear();
		assertNull(state.getRoster());
	}

	@Test
	public void aGroupIsAtMostTwentyFiveRows()
	{
		StringBuilder rows = new StringBuilder("{\"roster\":[");
		for (int i = 0; i < 40; i++)
		{
			rows.append(i == 0 ? "" : ",").append("{\"id\":\"m").append(i).append("\",\"name\":\"M\",\"routable\":false}");
		}
		RelayResponse reply = read(rows.append("]}").toString());
		assertEquals(GroupMarkers.MAX_MEMBERS, reply.directive.roster.size());
		assertTrue(reply.directive.roster.get(0).name.equals("M"));
	}
}
