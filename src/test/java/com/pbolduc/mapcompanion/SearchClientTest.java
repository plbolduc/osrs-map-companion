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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
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
 * The search box asks once the typing stops, one search at a time, and not before the map's wait.
 *
 * <p>Driven through a real OkHttp call answered by an interceptor, with the debounce as a list of
 * waiting actions the test fires, so what is checked is what would leave the machine and when.
 */
public class SearchClientTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String BANKS = "{\"rows\":["
		+ "{\"id\":\"bank:lumbridge\",\"kind\":\"bank\",\"name\":\"Lumbridge Castle bank\",\"line\":\"Bank · 2nd floor\","
		+ "\"x\":3208,\"y\":3220,\"plane\":2,\"effortTiles\":40,\"section\":\"Places\"},"
		+ "{\"id\":\"npc:duradel\",\"kind\":\"npc\",\"name\":\"Duradel\",\"line\":\"Character\",\"x\":2869,\"y\":2982,"
		+ "\"plane\":1,\"spawnCount\":2,\"section\":\"Dropped by\"}],"
		+ "\"items\":[{\"id\":\"item:Abyssal whip\",\"name\":\"Abyssal whip\",\"droppedBy\":3,\"soldAt\":0}],"
		+ "\"suggestion\":null,\"origin\":\"shared\"}";

	private final List<Request> sent = new CopyOnWriteArrayList<>();
	/** The debounce's waiting actions, with the delay each asked for. */
	private final List<Object[]> waiting = new ArrayList<>();
	@Nullable
	private String[] held = {"https://osrs-map.com", "ABC234", "member-1", SECRET};

	private SearchClient client(int status, String body, @Nullable String retryAfter, @Nullable CountDownLatch gate)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			if (gate != null)
			{
				try
				{
					gate.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			Response.Builder reply = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("").body(ResponseBody.create(JSON, body));
			if (retryAfter != null)
			{
				reply.header("Retry-After", retryAfter);
			}
			return reply.build();
		}).build();
		return new SearchClient(http, new Gson(), () -> held, (action, delayMs) ->
		{
			Object[] entry = {action, delayMs};
			waiting.add(entry);
			return () -> waiting.remove(entry);
		}, Runnable::run);
	}

	/** Fire every action the debounce is holding, as its timer would. */
	private void elapse()
	{
		List<Object[]> due = new ArrayList<>(waiting);
		waiting.clear();
		for (Object[] entry : due)
		{
			((Runnable) entry[0]).run();
		}
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

	@Test
	public void asksOnceTheTypingStopsWithTheMembersOwnCredentials() throws Exception
	{
		SearchClient search = client(200, BANKS, null, null);
		search.typed("b");
		search.typed("ba");
		search.typed("ban");
		search.typed("bank");
		assertTrue("nothing goes while the keys are still coming", sent.isEmpty());
		assertEquals("one wait, restarted by each key", 1, waiting.size());
		assertEquals(SearchClient.DEBOUNCE_MS, waiting.get(0)[1]);

		elapse();
		until(() -> !search.result().searching);
		assertEquals(1, sent.size());
		Request request = sent.get(0);
		assertEquals("GET", request.method());
		assertEquals("/api/companion/search", request.url().encodedPath());
		assertEquals("bank", request.url().queryParameter("q"));
		assertEquals("ABC234", request.header("X-Companion-Code"));
		assertEquals("member-1", request.header("X-Companion-Member"));
		assertEquals(SECRET, request.header("X-Companion-Member-Secret"));
		assertEquals(CompanionVersion.USER_AGENT, request.header("User-Agent"));

		List<SearchClient.Hit> hits = search.result().hits;
		assertEquals(3, hits.size());
		assertEquals("Lumbridge Castle bank", hits.get(0).name);
		assertEquals("Bank · 2nd floor · ~40 tiles", hits.get(0).line);
		assertNull("Places carries no heading of its own", hits.get(0).section);
		assertEquals("Dropped by", hits.get(1).section);
		assertEquals("item:Abyssal whip", hits.get(2).id);
		/* The row sits under the Items heading now, so its line no longer starts by saying it is one. */
		assertEquals("Dropped by 3", hits.get(2).line);
		assertNull("an item stands nowhere until the map picks its source", hits.get(2).tile);
	}

	@Test
	public void enterAsksAtOnceAndAShortQueryAsksNothing() throws Exception
	{
		SearchClient search = client(200, BANKS, null, null);
		search.enter("b");
		assertTrue(sent.isEmpty());
		assertTrue(waiting.isEmpty());

		search.enter("bank");
		until(() -> !search.result().searching);
		assertEquals(1, sent.size());
	}

	@Test
	public void clearingTheBoxCancelsTheSearchAndForgetsItsAnswer() throws Exception
	{
		CountDownLatch gate = new CountDownLatch(1);
		SearchClient search = client(200, BANKS, null, gate);
		search.enter("bank");
		until(() -> sent.size() == 1);
		search.typed("");
		gate.countDown();
		Thread.sleep(100);
		assertTrue("the answer to a cleared box is not drawn", search.result().hits.isEmpty());
		assertFalse(search.result().searching);
		assertEquals("", search.result().query);
	}

	@Test
	public void oneSearchAtATimeAndTheOldAnswerIsDroppedWhenTheBoxMovedOn() throws Exception
	{
		CountDownLatch gate = new CountDownLatch(1);
		SearchClient search = client(200, BANKS, null, gate);
		search.enter("bank");
		until(() -> sent.size() == 1);
		search.enter("banker");
		assertEquals("a second search waits for the first", 1, sent.size());
		gate.countDown();
		until(() -> sent.size() == 2 && !search.result().searching);
		assertEquals("then asks what the box says now", "banker", sent.get(1).url().queryParameter("q"));
		assertEquals("banker", search.result().query);
	}

	@Test
	public void waitsAsLongAsTheMapAsks() throws Exception
	{
		SearchClient search = client(429, "{\"reason\":\"busy\"}", "20", null);
		search.enter("bank");
		until(() -> !search.result().searching);
		assertTrue(search.result().message.startsWith("The map is busy."));

		search.enter("banker");
		assertEquals("Enter inside the wait sends nothing", 1, sent.size());
		assertEquals(1, waiting.size());
		assertTrue("it waits for the map's twenty seconds", (long) waiting.get(0)[1] > 19_000L);
		search.typed("bankers");
		assertTrue("typing inside the wait waits as long", (long) waiting.get(0)[1] > 19_000L);
	}

	@Test
	public void sendsNothingWithoutAMemberToAskAs() throws Exception
	{
		held = null;
		SearchClient search = client(200, BANKS, null, null);
		search.enter("bank");
		assertTrue(sent.isEmpty());
		assertEquals(SearchClient.NEEDS_CONNECTION, search.result().message);
	}

	@Test
	public void forgetsAListSearchedAsAnotherMember() throws Exception
	{
		SearchClient search = client(200, BANKS, null, null);
		search.enter("bank");
		until(() -> !search.result().searching);
		assertEquals(3, search.result().hits.size());
		search.checkScope();
		assertEquals("the same member keeps the list", 3, search.result().hits.size());
		held = new String[]{"https://osrs-map.com", "DEF567", "member-1", SECRET};
		search.checkScope();
		assertTrue("another group's list is not offered", search.result().hits.isEmpty());
	}

	@Test
	public void readsAMisspellingAsDidYouMean() throws Exception
	{
		SearchClient search = client(200, "{\"rows\":[],\"items\":[],\"suggestion\":\"abyssal whip\",\"origin\":null}", null, null);
		search.enter("abysal whip");
		until(() -> !search.result().searching);
		assertEquals("abyssal whip", search.result().suggestion);
		assertNull(search.result().message);
	}

	@Test
	public void boundsWhatItDraws()
	{
		SearchClient.Answer answer = new SearchClient.Answer();
		answer.rows = new ArrayList<>();
		for (int i = 0; i < 12; i++)
		{
			SearchClient.Row row = new SearchClient.Row();
			row.id = i == 0 ? "bad\nid" : "row:" + i;
			row.name = i == 1 ? "  " : "Row\t" + i;
			row.x = 3200;
			row.y = 3200;
			answer.rows.add(row);
		}
		List<SearchClient.Hit> hits = SearchClient.normalize(answer);
		assertEquals("eight rows at most, the unreadable ones skipped", 8, hits.size());
		assertEquals("row:2", hits.get(0).id);
		assertEquals("Row 2", hits.get(0).name);
		assertNotNull(hits.get(0).tile);
	}

	/**
	 * An item follows the item's sources, and without a heading of its own it was drawn under
	 * theirs: "Bank filler" read as one more counter under Sold at. Items have their own heading.
	 */
	@Test
	public void itemsSitUnderTheirOwnHeadingNotTheLastSourcesOne()
	{
		SearchClient.Answer answer = new com.google.gson.Gson().fromJson(BANKS, SearchClient.Answer.class);
		List<SearchClient.Hit> hits = SearchClient.normalize(answer);
		assertEquals("Dropped by", hits.get(1).section);
		assertEquals(SearchClient.ITEMS, hits.get(2).section);
		assertEquals("bank", hits.get(0).kind);
		assertNull("an item is no kind of place", hits.get(2).kind);
		assertNull("the map named no id for a whip, so none is made up", hits.get(2).itemId);
		assertEquals("Sold at 1", SearchClient.itemLine(item(0, 1)));
		assertEquals("Dropped by 3 · sold at 1", SearchClient.itemLine(item(3, 1)));
	}

	/** The map's own description of an answer (lib/contract/search.json), read whole: the item's id picks its sprite. */
	@Test
	public void theContractFixtureIsReadWithTheItemsGameId() throws Exception
	{
		String fixture = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/contract/search.json")),
			java.nio.charset.StandardCharsets.UTF_8);
		List<SearchClient.Hit> hits = SearchClient.normalize(new com.google.gson.Gson().fromJson(fixture, SearchClient.Answer.class));
		assertEquals(2, hits.size());
		assertEquals("monster", hits.get(0).kind);
		assertEquals("Dropped by", hits.get(0).section);
		assertEquals("item:Feather", hits.get(1).id);
		assertEquals(Integer.valueOf(314), hits.get(1).itemId);
		assertEquals(SearchClient.ITEMS, hits.get(1).section);
	}

	/** Each kind's icon is the world map's own, and a kind with none draws none rather than another's. */
	@Test
	public void aRowsIconIsTheWorldMapsForItsKind()
	{
		assertEquals(net.runelite.api.gameval.SpriteID.Mapfunction.BANK, SearchPanel.spriteFor("bank", null));
		assertEquals(net.runelite.api.gameval.SpriteID.SideIcons.COMBAT, SearchPanel.spriteFor("monster", "Dropped by"));
		assertEquals("a shop's sign, whichever shop it is", net.runelite.api.gameval.SpriteID.Mapfunction.GENERAL_STORE,
			SearchPanel.spriteFor("poi", "Sold at"));
		assertEquals(0, SearchPanel.spriteFor("place", null));
		assertEquals(0, SearchPanel.spriteFor(null, null));
	}

	private static SearchClient.Item item(int droppedBy, int soldAt)
	{
		SearchClient.Item item = new SearchClient.Item();
		item.droppedBy = droppedBy;
		item.soldAt = soldAt;
		return item;
	}

	/** What a person types is theirs: nothing in this class can write it to a log. */
	@Test
	public void hasNoLogger()
	{
		for (Field field : SearchClient.class.getDeclaredFields())
		{
			assertFalse(field.getName(), field.getType().getName().startsWith("org.slf4j"));
		}
	}
}
