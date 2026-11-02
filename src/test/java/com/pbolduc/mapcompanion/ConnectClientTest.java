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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Connect, Join, Open the map and Rename group, as the map's three doors answer them, read the way
 * the sidebar reads them.
 */
public class ConnectClientTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String MAP = "https://map.example.test";
	private static final String INSTALLATION = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ24";
	private static final String SECRET = "ZYXWVTSRQPNMKJHGFEDCBA98765432KM";

	private final List<Request> sent = new CopyOnWriteArrayList<>();

	private ConnectClient client(int status, String body)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("").body(ResponseBody.create(JSON, body)).build();
		}).build();
		return new ConnectClient(http, new Gson());
	}

	/** What a press came back with, waited for: the sidebar hands it to Swing, a test waits. */
	private static ConnectClient.Outcome outcome(Consumer<Consumer<ConnectClient.Outcome>> ask) throws Exception
	{
		CompletableFuture<ConnectClient.Outcome> answer = new CompletableFuture<>();
		ask.accept(answer::complete);
		return answer.get(5, TimeUnit.SECONDS);
	}

	/** And Open the map's link, or null for the plain map. */
	private static String url(Consumer<Consumer<String>> ask) throws Exception
	{
		CompletableFuture<String> answer = new CompletableFuture<>();
		ask.accept(answer::complete);
		return answer.get(5, TimeUnit.SECONDS);
	}

	private static JsonObject body(Request request) throws Exception
	{
		Buffer buffer = new Buffer();
		request.body().writeTo(buffer);
		return new Gson().fromJson(buffer.readString(StandardCharsets.UTF_8), JsonObject.class);
	}

	@Test
	public void connectKeepsTheRuneLiteCodeTheMapHandsBackAsIfItHadBeenPasted() throws Exception
	{
		ConnectClient client = client(201, "{\"code\":\"ABC234\",\"name\":\"Our group\",\"runeliteCode\":\"ABC234-K7PQ2M3X\"}");
		ConnectClient.Outcome outcome = outcome(done -> client.start(MAP, INSTALLATION, done));

		assertEquals("ABC234", outcome.joined.code);
		assertEquals("K7PQ2M3X", outcome.joined.suffix);
		assertEquals("Our group", outcome.name);
		/** What went out: the installation key the snapshot already carries, and no name at all. */
		Request request = sent.get(0);
		assertEquals(MAP + "/api/companion/connect", request.url().toString());
		assertEquals(INSTALLATION, request.header("X-Companion-Installation"));
		assertEquals("{}", body(request).toString());
	}

	@Test
	public void joinSpendsThePastedInviteAndKeepsABareCodeWhenTheMapNeedsNoTicket() throws Exception
	{
		ConnectClient client = client(200, "{\"code\":\"ABC234\",\"name\":\"Sunday hunt\"}");
		ConnectClient.Outcome outcome = outcome(done -> client.join(MAP, INSTALLATION, "ABC234", "ABCDEFGHJKMNPQRS", done));

		assertEquals("ABC234", outcome.joined.code);
		assertNull("already in: the group lets this RuneLite in without a ticket", outcome.joined.suffix);
		JsonObject asked = body(sent.get(0));
		assertEquals("ABC234", asked.get("code").getAsString());
		assertEquals("ABCDEFGHJKMNPQRS", asked.get("invite").getAsString());
	}

	/**
	 * A code the player did not paste, or one that disagrees with itself, is a map this build does not
	 * understand — and keeping it would post this player into a group they never chose.
	 */
	@Test
	public void refusesACodeThatIsNotTheOneAskedFor() throws Exception
	{
		ConnectClient other = client(200, "{\"code\":\"XYZ789\",\"runeliteCode\":\"XYZ789-K7PQ2M3X\"}");
		assertNull(outcome(done -> other.join(MAP, INSTALLATION, "ABC234", "ABCDEFGHJKMNPQRS", done)).joined);

		ConnectClient split = client(201, "{\"code\":\"ABC234\",\"runeliteCode\":\"XYZ789-K7PQ2M3X\"}");
		ConnectClient.Outcome outcome = outcome(done -> split.start(MAP, INSTALLATION, done));
		assertNull(outcome.joined);
		assertEquals(ConnectClient.FAILED, outcome.message);
	}

	@Test
	public void aRefusalIsTheMapsOwnSentence() throws Exception
	{
		ConnectClient client = client(403, "{\"reason\":\"expired\",\"error\":\"That invite has expired. Ask for a new one.\"}");
		ConnectClient.Outcome outcome = outcome(done -> client.join(MAP, INSTALLATION, "ABC234", "ABCDEFGHJKMNPQRS", done));
		assertNull(outcome.joined);
		assertEquals("That invite has expired. Ask for a new one.", outcome.message);
	}

	@Test
	public void aSentenceWithMarkupOrNoneAtAllIsNotPrintedAsOne() throws Exception
	{
		ConnectClient tagged = client(403, "{\"reason\":\"expired\",\"error\":\"<b>That</b> invite <img src=x>expired.\"}");
		assertEquals("That invite expired.", outcome(done -> tagged.start(MAP, INSTALLATION, done)).message);

		ConnectClient wordless = client(500, "<html>proxy</html>");
		assertEquals(ConnectClient.FAILED, outcome(done -> wordless.start(MAP, INSTALLATION, done)).message);

		ConnectClient busy = client(429, "{\"reason\":\"busy\",\"error\":\"Slow down a moment.\"}");
		assertEquals(ConnectClient.BUSY, outcome(done -> busy.start(MAP, INSTALLATION, done)).message);
	}

	/** Without the installation key the map refuses anyway; nothing is sent to be refused. */
	@Test
	public void sendsNothingWithoutAnInstallationKey() throws Exception
	{
		ConnectClient client = client(201, "{}");
		ConnectClient.Outcome outcome = outcome(done -> client.start(MAP, "not-a-key", done));
		assertNull(outcome.joined);
		assertTrue(sent.isEmpty());
	}

	/**
	 * Rename group: the member's own headers and the name, and the name the map answers with is the
	 * one kept. A name the map did not take is never kept, and a refusal is the map's own sentence.
	 */
	@Test
	public void renameSendsTheNameAsTheMemberAndKeepsOnlyWhatTheMapTook() throws Exception
	{
		ConnectClient client = client(200, "{\"code\":\"ABC234\",\"name\":\"Sunday raids\"}");
		ConnectClient.Outcome outcome = outcome(done -> client.rename(MAP, "ABC234", "zezima", SECRET, "Sunday raids", done));
		assertEquals("Sunday raids", outcome.name);
		assertNull(outcome.joined);
		Request request = sent.get(0);
		assertEquals("PATCH", request.method());
		assertEquals(MAP + "/api/companion/group", request.url().toString());
		assertEquals("ABC234", request.header("X-Companion-Code"));
		assertEquals("zezima", request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		assertEquals("Sunday raids", body(request).get("name").getAsString());

		ConnectClient refused = client(403, "{\"reason\":\"not_owner\",\"error\":\"Only an owner of this group can do that.\"}");
		ConnectClient.Outcome no = outcome(done -> refused.rename(MAP, "ABC234", "zezima", SECRET, "Mine now", done));
		assertNull(no.name);
		assertEquals("Only an owner of this group can do that.", no.message);
	}

	/** Rename reads a refusal as Connect and Join do: a busy map is busy, and no sentence of its own is FAILED. */
	@Test
	public void renameReadsARefusalAsConnectDoes() throws Exception
	{
		ConnectClient busy = client(503, "");
		assertEquals(ConnectClient.BUSY, outcome(done -> busy.rename(MAP, "ABC234", "zezima", SECRET, "Sunday raids", done)).message);
		ConnectClient slow = client(429, "{\"reason\":\"busy\",\"error\":\"Slow down a moment.\"}");
		assertEquals(ConnectClient.BUSY, outcome(done -> slow.rename(MAP, "ABC234", "zezima", SECRET, "Sunday raids", done)).message);
		ConnectClient wordless = client(500, "<html>proxy</html>");
		ConnectClient.Outcome failed = outcome(done -> wordless.rename(MAP, "ABC234", "zezima", SECRET, "Sunday raids", done));
		assertNull(failed.name);
		assertEquals(ConnectClient.FAILED, failed.message);
	}

	/** No secret means a request certain to be refused, so none is sent. */
	@Test
	public void renameAsksNothingWithoutASecret() throws Exception
	{
		ConnectClient client = client(200, "{}");
		assertNull(outcome(done -> client.rename(MAP, "ABC234", "zezima", null, "Sunday raids", done)).name);
		assertTrue(sent.isEmpty());
	}

	@Test
	public void openMapOpensTheLinkTheMapMadeForThisMember() throws Exception
	{
		String link = MAP + "/map?task=companion#link=K7PQ2M3XABCDEFGHJKMNPQRS";
		ConnectClient client = client(201, "{\"url\":\"" + link + "\",\"expiresAt\":1}");
		assertEquals(link, url(done -> client.link(MAP, "ABC234", "zezima", SECRET, done)));

		Request request = sent.get(0);
		assertEquals(MAP + "/api/companion/link", request.url().toString());
		assertEquals("ABC234", request.header("X-Companion-Code"));
		assertEquals("zezima", request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
	}

	/** A member the map has not issued a secret is certain to be refused, so it is not asked at all. */
	@Test
	public void openMapAsksNothingBeforeTheMapHasIssuedASecret() throws Exception
	{
		ConnectClient client = client(201, "{}");
		assertNull(url(done -> client.link(MAP, "ABC234", "zezima", null, done)));
		assertNull(url(done -> client.link(MAP, "ABC234", null, SECRET, done)));
		assertTrue(sent.isEmpty());
	}

	/**
	 * The browser this opens is about to be made into the player, so a link anywhere but the map that
	 * was asked, or in any shape but the map's own, is not opened: the plain map is, instead.
	 */
	@Test
	public void opensOnlyALinkOnTheMapThatWasAsked() throws Exception
	{
		HttpUrl asked = HttpUrl.get(MAP + "/api/companion/link");
		assertTrue(ConnectClient.isMapLink(asked, MAP + "/map?task=companion#link=K7PQ2M3XABCDEFGHJKMNPQRS"));
		assertFalse(ConnectClient.isMapLink(asked, "https://elsewhere.test/map?task=companion#link=K7PQ2M3XABCDEFGHJKMNPQRS"));
		assertFalse(ConnectClient.isMapLink(asked, MAP + "/map?task=companion&link=K7PQ2M3XABCDEFGHJKMNPQRS"));
		assertFalse(ConnectClient.isMapLink(asked, MAP + "/login#link=K7PQ2M3XABCDEFGHJKMNPQRS"));
		assertFalse(ConnectClient.isMapLink(asked, MAP + "/map#link=short"));
		assertFalse(ConnectClient.isMapLink(asked, null));

		ConnectClient elsewhere = client(201, "{\"url\":\"https://elsewhere.test/map#link=K7PQ2M3XABCDEFGHJKMNPQRS\"}");
		assertNull(url(done -> elsewhere.link(MAP, "ABC234", "zezima", SECRET, done)));
		ConnectClient legacy = client(404, "{\"reason\":\"not_found\",\"error\":\"There is no group with that code.\"}");
		assertNull(url(done -> legacy.link(MAP, "ABC234", "zezima", SECRET, done)));
	}
}
