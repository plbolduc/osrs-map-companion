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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * How often a tick builds a snapshot, which is the dear half of deciding whether to post one.
 *
 * <p>A build reads every shared reader on the client thread and is serialised to be compared with
 * the last one sent. The floor between posts was measured from the last post, so a snapshot that
 * said nothing new, and so was not posted, left the floor open: standing in combat, where a varbit
 * or a hitpoint marks the plugin dirty every tick, every tick built one. The floor is now from the
 * last build. These drive the plugin's own step with the real builder, relay and fingerprint, and
 * a map that answers every post.
 */
public class SnapshotCadenceTest
{
	/** Far from the real clock, so the relay's own wait after a reply, which reads it, is always over. */
	private static final long START = System.currentTimeMillis() + 3_600_000L;

	private final Gson gson = new Gson();
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final AtomicInteger builds = new AtomicInteger();
	private final AtomicInteger posts = new AtomicInteger();
	/** While set, the map answers every post as it does over its budget: 429, try again in five seconds. */
	private volatile boolean busy;
	private final MapCompanionPlugin plugin = TestLoop.in(new MapCompanionPlugin()
	{
		@Override
		ConfigStore settings()
		{
			return store;
		}
	});
	private final RelayClient relay = new RelayClient(new OkHttpClient.Builder().addInterceptor(chain ->
	{
		posts.incrementAndGet();
		return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(busy ? 429 : 200)
			.message("").header("Retry-After", "5")
			.body(ResponseBody.create(MediaType.get("application/json"), "{}")).build();
	}).build(), gson);
	private String server;

	public SnapshotCadenceTest() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		/* A snapshot names its member once, so each build asks who that is once: the count of builds. */
		MemberIdentity identity = new MemberIdentity(store)
		{
			@Override
			public synchronized String id()
			{
				builds.incrementAndGet();
				return super.id();
			}
		};
		plugin.loop.pairing = new Pairing(store, gson);
		plugin.loop.pairing.connect(JoinCode.parse("ABC234"), () -> true);
		server = plugin.loop.pairing.serverUrl();
		plugin.loop.milestones = new MilestoneOutbox();
		plugin.loop.destinationState = new DestinationState(gson);
		plugin.destinationMarker = new TestDestinationMarker();
		TestFarmRuns.attach(plugin);
		set("config", config);
		set("identity", identity);
		set("relay", relay);
		set("snapshotFingerprint", new SnapshotFingerprint(gson));
		set("directiveStream", new DirectiveStream(new OkHttpClient(), gson, (action, delay) -> null));
		set("snapshotBuilder", Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(game.client());
				bind(Gson.class).toInstance(gson);
				bind(MapCompanionConfig.class).toInstance(config);
				bind(ConfigStore.class).toInstance(store);
				bind(MemberIdentity.class).toInstance(identity);
				bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
				bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
				bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
			}
		}).getInstance(SnapshotBuilder.class));
	}

	@Test
	public void aTickThatHasNothingNewToSayBuildsAtMostOnceInTheFloor() throws Exception
	{
		tick(0);
		assertEquals("the first tick builds", 1, builds.get());
		assertEquals("and posts what it built", 1, posts.get());

		tick(600);
		tick(1_200);
		tick(1_800);
		assertEquals("nothing is built inside the floor after a post", 1, builds.get());

		tick(2_400);
		assertEquals("past the floor a dirty tick builds", 2, builds.get());
		assertEquals("and finds nothing new to post", 1, posts.get());

		tick(3_000);
		tick(3_600);
		tick(4_200);
		assertEquals("the floor is from that build, though it posted nothing: three dirty ticks build none", 2, builds.get());
		assertEquals(1, posts.get());

		/* Something real: the name the group sees. It goes on the first tick the floor allows, well before a heartbeat. */
		config.nickname("Another name");
		tick(4_400);
		assertEquals("two seconds after the last build, a change is built", 3, builds.get());
		assertEquals("and posted", 2, posts.get());
	}

	/** A heartbeat is posted with nothing new in it, and is held to the same floor as any other build. */
	@Test
	public void aHeartbeatStillGoesWhenNothingHasChanged() throws Exception
	{
		tick(0);
		tick(5_400);
		assertEquals("a dirty tick with nothing new", 1, posts.get());
		tick(6_000);
		assertEquals("the heartbeat is due, inside the floor of the build before it", 1, posts.get());
		tick(7_400);
		assertEquals("and goes on the first tick the floor allows", 2, posts.get());
		assertEquals(3, builds.get());
	}

	/**
	 * A post the map refused as busy was not heard, so what it carried is still news when the wait
	 * the map asked for ends. The fingerprint was recorded when the post was handed over, so a
	 * refused rename read as already said and waited for the next heartbeat. Nothing here marks the
	 * plugin dirty after the refusal: only the refusal itself can bring the change back.
	 */
	@Test
	public void aChangeRefusedAsBusyGoesWhenTheWaitEnds() throws Exception
	{
		tick(0);
		config.nickname("Another name");
		busy = true;
		tick(2_400);
		assertEquals("the change is posted, and refused", 2, posts.get());

		busy = false;
		quietTick(4_800);
		assertEquals("the same change goes again once the map is ready, well before a heartbeat", 3, posts.get());
		quietTick(7_200);
		assertEquals("and is not repeated once it has landed", 3, posts.get());
	}

	/**
	 * The other half: a refused heartbeat that said nothing new is not sent again when the wait ends.
	 * The map said it was busy, and it has already heard everything this post held.
	 */
	@Test
	public void aRefusedPostWithNothingNewWaitsForTheNextHeartbeat() throws Exception
	{
		tick(0);
		busy = true;
		tick(6_000);
		assertEquals("the heartbeat is posted, and refused", 2, posts.get());

		busy = false;
		quietTick(8_400);
		quietTick(10_800);
		assertEquals("nothing has changed, so nothing goes before the next heartbeat", 2, posts.get());
		quietTick(12_000);
		assertEquals("which goes as it always did", 3, posts.get());
	}

	/** One game tick's post step at {@code START + at}, dirty as a varbit would leave it, and its reply landed. */
	private void tick(long at) throws Exception
	{
		set("dirty", true);
		quietTick(at);
	}

	/** The same step on a tick where nothing in the game moved. */
	private void quietTick(long at) throws Exception
	{
		plugin.loop.postIfDue(START + at, server, "ABC234");
		long deadline = System.currentTimeMillis() + 5_000L;
		while (!relay.isReady(START + at))
		{
			assertTrue("the map's reply never landed", System.currentTimeMillis() < deadline);
			Thread.sleep(5L);
		}
	}

	private void set(String name, Object value) throws Exception
	{
		TestLoop.set(plugin, name, value);
	}
}
