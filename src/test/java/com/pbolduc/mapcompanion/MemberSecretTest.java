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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import okhttp3.Interceptor;
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
 * The credential this plugin presents: where it comes from, which doors it goes through, and the
 * places it must never appear.
 *
 * <p><b>Why it exists at all.</b> A member id is not a secret — the map shows every member's id to
 * every other member of the group — so an invite request authenticated by an id alone let a removed
 * player mint a fresh invite under a name the group still trusted, and the directive stream carried
 * no credential whatsoever. The map now issues a secret on a snapshot reply and refuses those two
 * doors without it, which makes "the header is actually on the request" a thing this plugin has to
 * be right about rather than a nicety: get it wrong and the sidebar shows a connected dot with a
 * stream that reconnects on its back-off forever.
 *
 * <p><b>Why this can be a unit test.</b> Every request here is built by OkHttp, and an OkHttp
 * client will hand a request to an {@link Interceptor} without opening anything. So the three doors
 * are driven exactly as the plugin drives them and the request that <i>would</i> have gone out is
 * read back — headers, URL and all — with no server, no client and no game.
 *
 * <p>The last three cases read the sources as text, in the manner of {@code NetworkDisclosureTest}
 * and for the same reason: "it is never logged" and "the window that shows what is sent does not
 * print it" are promises made in prose, and prose does not fail to compile.
 */
public class MemberSecretTest
{
	/** The member every secret here is filed for; a member is one character. */
	private static final String MEMBER = "member-1";

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private static final String HEADER = "X-Companion-Member-Secret";

	/** Thirty-two characters of the map's alphabet, which is the shape the map issues. */
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	private static final String MAP = "https://example.invalid";

	private final Gson gson = new Gson();

	@Test
	public void theSnapshotPostCarriesIt() throws InterruptedException
	{
		Doorman door = new Doorman(200, "{\"pollIntervalMs\":2000}");
		RelayClient relay = new RelayClient(door.client(), gson);

		assertTrue(relay.post(MAP, "ABC234", null, SECRET, new CompanionSnapshot(), r ->
		{
		}));

		Request sent = door.awaited();
		assertEquals(SECRET, sent.header(HEADER));
		/** Beside the code, not instead of it: the map still has to know which group this is. */
		assertEquals("ABC234", sent.header("X-Companion-Code"));
	}

	@Test
	public void aPostWithNoSecretYetSendsNoHeaderAndIsStillAPost() throws InterruptedException
	{
		/**
		 * The bootstrap, and the reason a missing secret can never stop a snapshot: the reply to one
		 * of these posts is where the first secret comes from, so a post that refused to go without
		 * one could never get one.
		 */
		Doorman door = new Doorman(200, "{}");
		RelayClient relay = new RelayClient(door.client(), gson);

		assertTrue(relay.post(MAP, "ABC234", null, null, new CompanionSnapshot(), r ->
		{
		}));

		assertNull(door.awaited().header(HEADER));
	}

	@Test
	public void aSecretThatIsNotOneIsNotSent() throws InterruptedException
	{
		/** It is read out of a config key, and a header this plugin can tell is nonsense is not one. */
		Doorman door = new Doorman(200, "{}");
		RelayClient relay = new RelayClient(door.client(), gson);

		relay.post(MAP, "ABC234", null, "K7PQ2M3X", new CompanionSnapshot(), r ->
		{
		});

		assertNull(door.awaited().header(HEADER));
	}

	@Test
	public void theInviteRequestCarriesIt() throws InterruptedException
	{
		/** The door it was invented for: an invite is the one thing a member can make that admits
		 * a stranger, and until now the only proof it came from a member was that member's id. */
		Doorman door = new Doorman(201, "{\"url\":\"https://example.invalid/map?join=ABC234\"}");
		RelayClient relay = new RelayClient(door.client(), gson);

		relay.requestInvite(MAP, "ABC234", "member-1", SECRET, invite ->
		{
		});

		assertEquals(SECRET, door.awaited().header(HEADER));
	}

	@Test
	public void theStreamCarriesItAsAHeaderAndNeverInTheAddress() throws InterruptedException
	{
		Doorman door = new Doorman(200, "");
		DirectiveStream stream = new DirectiveStream(door.client(), gson,
			(action, delay) -> new java.util.concurrent.FutureTask<>(action, null));
		try
		{
			stream.start(MAP, "ABC234", "member-1", SECRET, directive ->
			{
			});

			Request sent = door.awaited();
			assertEquals(SECRET, sent.header(HEADER));
			/**
			 * The code is allowed in the query and this is not, which is the whole of the
			 * difference between them: a code is rotatable and a standing credential in somebody's
			 * access log is not.
			 */
			assertFalse("the secret must not be anywhere in the URL",
						sent.url().toString().contains(SECRET));
			assertEquals("ABC234", sent.url().queryParameter("code"));
		}
		finally
		{
			stream.stop();
		}
	}

	@Test
	public void aStreamWithNoSecretStillOpensAndSendsNoHeader() throws InterruptedException
	{
		/**
		 * A legacy group asks for nothing, and an owned one refuses this — with the back-off it
		 * already has, since the refusal is mostly the second or two before the first secret
		 * arrives. Either way the connection is attempted rather than withheld.
		 */
		Doorman door = new Doorman(403, "{}");
		DirectiveStream stream = new DirectiveStream(door.client(), gson,
			(action, delay) -> new java.util.concurrent.FutureTask<>(action, null));
		try
		{
			stream.start(MAP, "ABC234", "member-1", null, directive ->
			{
			});

			assertNull(door.awaited().header(HEADER));
		}
		finally
		{
			stream.stop();
		}
	}

	@Test
	public void theSnapshotSaysWhichRuneLiteItCameFromAndNothingMalformed() throws InterruptedException
	{
		Doorman door = new Doorman(200, "{}");
		RelayClient relay = new RelayClient(door.client(), gson);
		relay.post(MAP, "ABC234", null, null, SECRET, new CompanionSnapshot(), r ->
		{
		});
		assertEquals(SECRET, door.awaited().header("X-Companion-Installation"));

		Doorman typo = new Doorman(200, "{}");
		RelayClient other = new RelayClient(typo.client(), gson);
		other.post(MAP, "ABC234", null, null, "not a key", new CompanionSnapshot(), r ->
		{
		});
		assertNull(typo.awaited().header("X-Companion-Installation"));
	}

	@Test
	public void aReplyThatIssuesOneIsStoredAndSentOnTheNextPost() throws InterruptedException
	{
		/** The whole loop, in the order it happens: the map answers, the tick writes it down, and
		 * the request after that carries it. */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		RelayResponse issued = gson.fromJson(
								   "{\"pollIntervalMs\":2000,\"memberSecret\":\"" + SECRET + "\"}", RelayResponse.class);

		plugin.noteIssuedSecret("ABC234", MEMBER, issued.memberSecret);
		plugin.storeIssuedSecret();

		assertEquals(SECRET, plugin.pairing.secret(MEMBER));

		Doorman door = new Doorman(200, "{}");
		RelayClient relay = new RelayClient(door.client(), gson);
		relay.post(MAP, "ABC234", null, plugin.pairing.secret(MEMBER), new CompanionSnapshot(), r ->
		{
		});

		assertEquals(SECRET, door.awaited().header(HEADER));
	}

	@Test
	public void aReplyWithoutTheFieldKeepsTheOneWeHold()
	{
		/**
		 * Absent means "keep what you have" and never "you have none". The map stops repeating the
		 * secret once it has seen the plugin present one, so reading silence as a revocation would
		 * delete the credential on the first reply after it started working — and the map would
		 * never send another.
		 */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		plugin.noteIssuedSecret("ABC234", MEMBER, SECRET);
		plugin.storeIssuedSecret();

		RelayResponse quiet = gson.fromJson("{\"pollIntervalMs\":2000}", RelayResponse.class);
		assertNull(quiet.memberSecret);
		plugin.noteIssuedSecret("ABC234", MEMBER, quiet.memberSecret);
		plugin.storeIssuedSecret();

		assertEquals(SECRET, plugin.pairing.secret(MEMBER));
	}

	@Test
	public void aReplyThatLandsAfterAGroupChangeIsFiledUnderTheGroupItAnswered()
	{
		/**
		 * The reply arrives on an OkHttp thread and the tick stores it, and a player can join
		 * somewhere else in between. Filing it under whatever the code happens to be by then would
		 * offer one group's credential to another group on every request it ever made.
		 */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		plugin.noteIssuedSecret("ABC234", MEMBER, SECRET);

		plugin.pairing.join(JoinCode.parse("DEF567"));
		plugin.onGroupChanged();
		plugin.storeIssuedSecret();

		assertNull("nothing of ABC234's is presented to DEF567", plugin.pairing.secret(MEMBER));
		plugin.pairing.switchTo("ABC234");
		assertEquals("and it is still there for the group that issued it", SECRET, plugin.pairing.secret(MEMBER));
	}

	@Test
	public void leavingTheGroupForgetsEveryOneOfThem()
	{
		/** The member id goes at the same moment, and a secret says which member of a group this
		 * installation is — so an id that no longer exists leaves nothing for any of them to prove. */
		MapCompanionPlugin plugin = pluginJoinedTo("ABC234");
		plugin.noteIssuedSecret("ABC234", MEMBER, SECRET);
		plugin.storeIssuedSecret();
		plugin.pairing.join(JoinCode.parse("DEF567"));
		plugin.noteIssuedSecret("DEF567", MEMBER, SECRET);
		plugin.storeIssuedSecret();

		plugin.pairing.leave();

		assertNull(plugin.pairing.secret(MEMBER));
		plugin.pairing.switchTo("ABC234");
		assertNull("Leave is not a switch: it forgets what every group issued", plugin.pairing.secret(MEMBER));
	}

	@Test
	public void noLogLineAnywhereCanPrintIt()
	{
		/**
		 * A log file is shared far more casually than a password — a RuneLite log lands in a support
		 * channel the first time anything goes wrong. The pairing code has been kept out of them
		 * since this plugin's first release; the secret is a stronger version of the same rule,
		 * because a code can be rotated by making a new group and this cannot be rotated at all.
		 */
		Pattern logCall = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(");
		List<String> offenders = new ArrayList<>();
		for (File source : mainSources())
		{
			int number = 0;
			for (String line : read(source.getPath()).split("\n"))
			{
				number++;
				if (logCall.matcher(line).find() && line.toLowerCase(Locale.ROOT).contains("secret"))
				{
					offenders.add(source.getName() + ":" + number);
				}
			}
		}
		assertEquals("a log statement mentions the member secret: " + offenders,
					 new ArrayList<String>(), offenders);
	}

	@Test
	public void theWindowThatShowsWhatIsSentNamesItWithoutPrintingIt()
	{
		/**
		 * <i>Show what's sent</i> prints the RuneLite code's suffix, because that one is on the
		 * reader's own card on the map and they may want to check the two agree. This one is a
		 * standing credential, and the window exists to be read — and screenshotted into a channel
		 * when somebody is asking for help. So it is named and never spelled out.
		 */
		String panel = read("src/main/java/com/pbolduc/mapcompanion/MapCompanionPanel.java");

		assertTrue("the preview should say which header a snapshot carries", panel.contains(HEADER));
		assertFalse("the preview must not print the secret itself",
					java.util.regex.Pattern.compile("[+][ ]*pairing[.]secret[(]|pairing[.]secret[(][^)]*[)][ ]*[+]").matcher(panel).find());
	}

	@Test
	public void theReadmeSaysWhatItIsAndWhatCarriesIt()
	{
		/** The disclosure and the behaviour change together or the disclosure is wrong in between —
		 * which is the whole argument of {@code NetworkDisclosureTest}, applied to a header. */
		String readme = read("README.md").replaceAll("\\s+", " ");

		assertTrue("the network disclosure should name the header", readme.contains(HEADER));
		/* The button is "Leave group" now, two words so it sits beside "Forget kill log" on one
		 * row; the README names it as it is printed, or a reader looks for a button that is not there. */
		assertTrue("and say that Leave group deletes it",
				   readme.contains("**Leave group** deletes every one"));
	}

	/** The plugin with the two collaborators these rules touch, as {@code GroupChangeTest} builds it. */
	private MapCompanionPlugin pluginJoinedTo(String pasted)
	{
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		plugin.pairing = new Pairing(new FakeStore(), gson);
		plugin.milestones = new MilestoneOutbox();
		plugin.destinationState = new DestinationState(gson);
		plugin.destinationMarker = new TestDestinationMarker();
		plugin.groupMarkers = new GroupMarkers(null, null);
		HarnessGame game = new HarnessGame();
		plugin.rewardObserver = new RewardObserver(game.client(), new CollectionItemCatalog(game.client()));
		plugin.pairing.join(JoinCode.parse(pasted));
		return plugin;
	}

	private static File[] mainSources()
	{
		File[] sources = new File("src/main/java/com/pbolduc/mapcompanion").listFiles(
			(dir, name) -> name.endsWith(".java"));
		assertNotNull("no sources found — this test runs from the project directory", sources);
		assertTrue(sources.length > 10);
		return sources;
	}

	private static String read(String path)
	{
		try
		{
			return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new AssertionError("could not read " + path, e);
		}
	}

	/**
	 * A door that lets a request through, keeps it, and answers without a socket.
	 *
	 * <p>An application interceptor sees exactly what OkHttp would have written, headers included,
	 * so this is the real request rather than a re-derivation of one. The queue is because every
	 * caller here is asynchronous: the plugin never blocks the game thread on a request, so a test
	 * has to wait for the thread that made it.
	 */
	private static final class Doorman implements Interceptor
	{
		private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
		private final int status;
		private final String body;

		private Doorman(int status, String body)
		{
			this.status = status;
			this.body = body;
		}

		private OkHttpClient client()
		{
			return new OkHttpClient.Builder().addInterceptor(this).build();
		}

		@Override
		public Response intercept(Chain chain)
		{
			Request request = chain.request();
			requests.add(request);
			return new Response.Builder()
				   .request(request)
				   .protocol(Protocol.HTTP_1_1)
				   .code(status)
				   .message("")
				   .body(ResponseBody.create(JSON, body))
				   .build();
		}

		/** The request that was made, or a failure saying it never was. */
		private Request awaited() throws InterruptedException
		{
			Request request = requests.poll(5, TimeUnit.SECONDS);
			assertNotNull("no request was made", request);
			return request;
		}
	}

	/** The two lines of key-value store {@link Pairing} uses, in a map. */
	private static final class FakeStore implements ConfigStore
	{
		private final Map<String, String> values = new HashMap<>();

		@Nullable
		@Override
		public String get(String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String key, String value)
		{
			values.put(key, value);
		}

		@Override
		public void unset(String key)
		{
			values.remove(key);
		}
	}
}
