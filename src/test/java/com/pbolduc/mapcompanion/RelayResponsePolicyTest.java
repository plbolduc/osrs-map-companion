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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the plugin does with each reply the map can send.
 *
 * <p><b>Why this can be a unit test after all.</b> The note on {@code RelayClientRefusalsTest} says
 * {@code RelayClient} is not testable without mocking OkHttp, and that is true of <i>making</i> a
 * request. It is not true of handling a reply: an {@code okhttp3.Response} is an ordinary object
 * with a builder, and nothing about constructing one opens a socket. So everything this class
 * decides after a request lands — the cadence, the latch, {@code claimed}, the acknowledgements —
 * is driven here directly, and none of it was reachable from a test before.
 *
 * <p>The case that motivated the file is the one about slowing down. The map states an interval on
 * every reply and a {@code Retry-After} when it is unhappy, and this plugin's whole standing with
 * the servers it talks to rests on honouring that. Clearing a refusal used to zero it, and a paste
 * into the Join field is exactly what somebody does when their dot has stopped — so the moment the
 * map was asking everybody to back off was the moment this sped up.
 */
public class RelayResponsePolicyTest
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private final RelayClient relay = new RelayClient(new OkHttpClient(), new Gson());

	@Test
	public void keepsTheServersOwnBackOffWhenARefusalIsCleared() throws IOException
	{
		relay.handle(reply(429, "{}", "Retry-After", "120"), null);
		long backOff = relay.getNextAllowedPostMs();
		assertTrue("a 429 should push the next post out by what Retry-After asked for",
				   backOff > System.currentTimeMillis() + 100_000L);

		/* The player pastes their RuneLite code, which is what clears a needs_code refusal. */
		relay.clearRefusal();

		assertEquals("clearing a refusal must not cancel a back-off the map asked for",
					 backOff, relay.getNextAllowedPostMs());
		assertTrue("and the plugin must still be holding off", !relay.isReady(System.currentTimeMillis()));
	}

	@Test
	public void clearingARefusalStillLetsARefusedMemberTryAgain() throws IOException
	{
		relay.handle(reply(403, "{\"reason\":\"needs_code\"}"), null);
		assertNotNull("403 needs_code is the map asking for the RuneLite code", relay.getFatalError());

		relay.clearRefusal();

		assertNull("pasting the code is the answer to it, so the latch goes", relay.getFatalError());
		assertTrue(relay.isReady(System.currentTimeMillis()));
	}

	@Test
	public void obeysTheIntervalTheMapStatesOnASuccess() throws IOException
	{
		long before = System.currentTimeMillis();
		relay.handle(reply(200, "{\"pollIntervalMs\":9000}"), null);
		assertTrue("the map's stated cadence is the one that is honoured",
				   relay.getNextAllowedPostMs() >= before + 9_000L);
		assertTrue(relay.isEverSucceeded());
	}

	@Test
	public void readsTheClaimAndTheAcknowledgementsOutOfASuccess() throws IOException
	{
		/**
		 * The two fields the outbox and the RuneLite code's lifecycle hang off. {@code claimed} is
		 * what tells the plugin to delete its suffix and stop sending it; an id in
		 * {@code acknowledgedMilestones} is the only thing that ends a retry. Both are boxed or
		 * absent on an older map, and both have to survive being read out of real JSON.
		 */
		List<RelayResponse> seen = new ArrayList<>();
		relay.handle(
			reply(200, "{\"claimed\":true,\"acknowledgedMilestones\":[\"a\",\"b\"],\"pollIntervalMs\":2000}"),
			seen::add);

		assertEquals(1, seen.size());
		assertEquals(Boolean.TRUE, seen.get(0).claimed);
		assertEquals(2, seen.get(0).acknowledgedMilestones.size());
	}

	@Test
	public void handsOverTheMemberSecretTheMapIssues() throws IOException
	{
		/**
		 * The map issues this once, in the reply to a post it accepted, and then — having seen the
		 * plugin present it — never sends it again. So it has to survive the one journey it gets:
		 * out of the JSON, through here, and into {@code Pairing} on the next tick. An absent field
		 * means "keep what you have", which is why nothing in this method treats null as an answer.
		 */
		List<RelayResponse> seen = new ArrayList<>();
		relay.handle(
			reply(200, "{\"pollIntervalMs\":2000,\"memberSecret\":\"K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23\"}"),
			seen::add);

		assertEquals(1, seen.size());
		assertEquals("K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23", seen.get(0).memberSecret);
		assertNull("a reply that issues one says nothing about the RuneLite code", seen.get(0).claimed);
	}

	@Test
	public void anOlderMapSaysNothingAboutTheClaimAndThatIsNotADenial()
	{
		/**
		 * Boxed on purpose: a map that has never heard of the RuneLite code answers without the
		 * field, and reading absent as false is what keeps the suffix being sent. Reading it as a
		 * claim would delete the one thing that says which dot belongs to which browser, on the
		 * first reply, forever.
		 */
		RelayResponse older = new Gson().fromJson("{\"pollIntervalMs\":2000}", RelayResponse.class);
		assertNull(older.claimed);
		assertNull(older.acknowledgedMilestones);
		/** And the same reading, for the same reason, of the secret a map that has one stops repeating. */
		assertNull(older.memberSecret);
	}

	@Test
	public void a410StopsThePluginAndSaysWhichKindOfGoneItIs() throws IOException
	{
		relay.handle(reply(410, "{\"reason\":\"removed\"}"), null);
		assertEquals("You were removed from this group.", relay.getFatalError());
	}

	/**
	 * A full relay is not a full group, and the sentence has to say which: somebody refused by the
	 * first cannot fix it by asking an owner to remove anybody, and waiting is the whole answer.
	 * Both still latch — a full relay asked again every two seconds by every plugin turned away is
	 * load on exactly the machine that has none to spare.
	 */
	@Test
	public void a507SaysWhetherTheGroupOrTheRelayIsFull() throws IOException
	{
		relay.handle(reply(507, "{\"reason\":\"capacity\",\"error\":\"There is no room for that right now.\"}"), null);
		assertEquals("The map has no room for anyone new right now. This will try again in five minutes.",
					 relay.getFatalError());
		assertTrue(!relay.isReady(System.currentTimeMillis()));

		RelayClient other = new RelayClient(new OkHttpClient(), new Gson());
		other.handle(reply(507, "{\"reason\":\"group_full\",\"error\":\"That group is full.\"}"), null);
		assertEquals("That group is full.", other.getFatalError());
	}

	/** A map older than the words says nothing, and gets the sentence every 507 used to get. */
	@Test
	public void a507WithNoWordIsStillAFullGroup() throws IOException
	{
		relay.handle(reply(507, "{\"error\":\"That group is full, or the relay has no room for a new one right now.\"}"), null);
		assertEquals("That group is full.", relay.getFatalError());
	}

	@Test
	public void aProxysHtml403IsRetriedRatherThanLatched() throws IOException
	{
		/** The failure the asymmetry exists for: a deploy answering 403 with a page, not a reason. */
		relay.handle(reply(403, "<html><body>Forbidden</body></html>"), null);
		assertNull(relay.getFatalError());
		assertEquals(1, relay.getConsecutiveFailures());
	}

	@Test
	public void aReplyWithNothingToSayIsStillASuccess() throws IOException
	{
		/** No body at all, which is what a 204-shaped reply from a future map would look like. */
		relay.handle(reply(200, ""), seen ->
		{
			throw new AssertionError("nothing to hand over, so nothing should be handed over");
		});
		assertTrue(relay.isEverSucceeded());
		assertEquals(0, relay.getConsecutiveFailures());
	}

	private static Response reply(int code, String body)
	{
		return reply(code, body, null, null);
	}

	/** A reply as OkHttp would hand it over, built without a server. */
	private static Response reply(int code, String body, @Nullable String header, @Nullable String value)
	{
		Response.Builder builder = new Response.Builder()
		.request(new Request.Builder().url("https://example.invalid/api/companion/snapshot").build())
		.protocol(Protocol.HTTP_1_1)
		.code(code)
		.message("")
		.body(ResponseBody.create(JSON, body));
		if (header != null)
		{
			builder.header(header, value);
		}
		return builder.build();
	}
}
