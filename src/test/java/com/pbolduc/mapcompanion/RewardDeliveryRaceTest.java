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
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import static org.junit.Assert.*;
import org.junit.Test;

/** Pause real observation/delivery boundaries so a settings thread can race them deterministically. */
public class RewardDeliveryRaceTest
{
	@Test
	public void aGroupResetCannotFinishBetweenTakingARewardAndEnqueuingIt() throws Exception
	{
		Pause enqueue = new Pause();
		MilestoneOutbox outbox = new MilestoneOutbox()
		{
			@Override
			public void reward(RewardObservation reward, long now)
			{
				// Deliberately before the outbox's own monitor: the plugin must protect this handoff.
				enqueue.awaitRelease();
				super.reward(reward, now);
			}
		};
		Fixture fixture = new Fixture(outbox);
		fixture.loot();
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try
		{
			Future<?> flush = threads.submit(fixture::settle);
			enqueue.awaitEntered();
			CountDownLatch resetStarted = new CountDownLatch(1);
			Future<?> reset = threads.submit(() ->
			{
				resetStarted.countDown();
				fixture.plugin.onGroupChanged();
			});
			assertTrue(resetStarted.await(5, TimeUnit.SECONDS));
			assertWaiting(reset);
			enqueue.release.countDown();
			flush.get(5, TimeUnit.SECONDS);
			reset.get(5, TimeUnit.SECONDS);
			assertEquals("the old batch cannot refill the reset outbox", 0, outbox.pendingCount());
		}
		finally
		{
			enqueue.release.countDown();
			threads.shutdownNow();
		}
	}

	@Test
	public void aVisiblePrivacyWriteInvalidatesAFlushBeforeItsConfigEventGetsTheLock() throws Exception
	{
		AtomicInteger enqueued = new AtomicInteger();
		MilestoneOutbox outbox = new MilestoneOutbox()
		{
			@Override
			public void reward(RewardObservation reward, long now)
			{
				enqueued.incrementAndGet();
				super.reward(reward, now);
			}
		};
		Fixture fixture = new Fixture(outbox);
		fixture.loot();
		fixture.deferTick.set(true);
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try
		{
			Future<?> flush = threads.submit(fixture::settle);
			fixture.tickPause.awaitEntered();
			fixture.pets.set(false);
			CountDownLatch resetStarted = new CountDownLatch(1);
			Future<?> reset = threads.submit(() ->
			{
				ConfigChanged changed = new ConfigChanged();
				changed.setGroup(MapCompanionConfig.GROUP);
				changed.setKey(MapCompanionConfig.KEY_SHARE_PETS);
				resetStarted.countDown();
				fixture.plugin.onConfigChanged(changed);
			});
			assertTrue(resetStarted.await(5, TimeUnit.SECONDS));
			assertWaiting(reset);
			fixture.tickPause.release.countDown();
			flush.get(5, TimeUnit.SECONDS);
			reset.get(5, TimeUnit.SECONDS);
			assertEquals("the obsolete batch must be rejected before enqueue", 0, enqueued.get());
			assertEquals(0, outbox.pendingCount());
			fixture.loot();
			fixture.settle();
			assertEquals("a new ordinary reward still follows the new sharing choices", 1, enqueued.get());
		}
		finally
		{
			fixture.tickPause.release.countDown();
			threads.shutdownNow();
		}
	}

	@Test
	public void aVisibleGroupWriteDropsOldObservationsEvenBeforeTheChangeCallback() throws Exception
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		Fixture fixture = new Fixture(outbox);
		fixture.loot();
		fixture.code.set("DEF567");
		fixture.settle();
		assertEquals(0, outbox.pendingCount());
		fixture.loot();
		fixture.settle();
		assertEquals(1, outbox.pendingCount());
	}

	@Test
	public void aVisibleServerWriteDropsOldObservationsBeforeTheChangeCallback() throws Exception
	{
		MilestoneOutbox outbox = new MilestoneOutbox();
		Fixture fixture = new Fixture(outbox);
		fixture.loot();
		fixture.server.set("http://localhost:3000");
		fixture.settle();
		assertEquals(0, outbox.pendingCount());
		fixture.loot();
		fixture.settle();
		assertEquals(1, outbox.pendingCount());
	}

	@Test
	public void anObserverResetWaitsForAnAlreadyRunningGameCallback() throws Exception
	{
		RewardTestClient game = new RewardTestClient();
		CollectionItemCatalog catalog = game.catalog();
		Pause observing = new Pause();
		RewardObserver observer = new RewardObserver(game.client, catalog, catalog::contains, () ->
		{
			observing.awaitRelease();
			return 1000L;
		});
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try
		{
			Future<?> callback = threads.submit(() -> observer.onLootReceived(receipt()));
			observing.awaitEntered();
			CountDownLatch resetStarted = new CountDownLatch(1);
			Future<?> reset = threads.submit(() ->
			{
				resetStarted.countDown();
				observer.reset();
			});
			assertTrue(resetStarted.await(5, TimeUnit.SECONDS));
			assertWaiting(reset);
			observing.release.countDown();
			callback.get(5, TimeUnit.SECONDS);
			reset.get(5, TimeUnit.SECONDS);
			assertTrue(observer.onGameTick(5000L, RewardObserver.SETTLE_TICKS).isEmpty());
		}
		finally
		{
			observing.release.countDown();
			threads.shutdownNow();
		}
	}

	private static void assertWaiting(Future<?> task) throws Exception
	{
		try
		{
			task.get(100, TimeUnit.MILLISECONDS);
			fail("Reset passed an in-progress observation or delivery boundary");
		}
		catch (TimeoutException expected)
		{
			// The other task has announced its attempt; this timeout proves it cannot finish yet.
		}
	}

	private static LootReceived receipt()
	{
		return new LootReceived("Lunar Chest", -1, LootRecordType.EVENT,
								Collections.singletonList(new ItemStack(10020, 1)), 1, null);
	}

	private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> answer)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
												(instance, method, args) -> answer.apply(method.getName(), args)));
	}

	private static final class Pause
	{
		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);

		void awaitEntered() throws InterruptedException
		{
			assertTrue("the production boundary was never reached", entered.await(5, TimeUnit.SECONDS));
		}

		void awaitRelease()
		{
			entered.countDown();
			try
			{
				assertTrue("the test did not release its boundary", release.await(5, TimeUnit.SECONDS));
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
		}
	}

	private static final class Fixture
	{
		final RewardTestClient game = new RewardTestClient();
		final CollectionItemCatalog catalog = game.catalog();
		final MapCompanionPlugin plugin = new MapCompanionPlugin();
		final AtomicReference<String> code = new AtomicReference<>("ABC234");
		final AtomicReference<String> server = new AtomicReference<>(MapCompanionConfig.DEFAULT_MAP_URL);
		final AtomicBoolean pets = new AtomicBoolean(true);
		final AtomicBoolean deferTick = new AtomicBoolean();
		final Pause tickPause = new Pause();

		Fixture(MilestoneOutbox outbox) throws Exception
		{
			game.state = GameState.LOGGED_IN;
			Client client = proxy(Client.class, (method, args) ->
			{
				if ("getGameState".equals(method)) return game.state;
				if ("getTickCount".equals(method))
				{
					if (deferTick.getAndSet(false))
					{
						tickPause.awaitRelease();
					}
					return game.tick;
				}
				throw new AssertionError("Unexpected client access: " + method);
			});
			MapCompanionConfig config = proxy(MapCompanionConfig.class, (method, args) ->
			{
				switch (method)
				{
					case "relayUrl":
						return server.get();
					case "pairingCode":
						return code.get();
					case "shareMilestones":
					case "shareCollectionLog":
						return true;
					case "sharePets":
						return pets.get();
					default:
						throw new AssertionError("Unexpected setting access: " + method);
				}
			});
			plugin.milestones = outbox;
			plugin.destinationState = new DestinationState(new Gson());
			plugin.destinationMarker = new TestDestinationMarker();
			plugin.groupMarkers = new GroupMarkers(null, null);
			plugin.pairing = new Pairing(new HarnessStore(), new Gson());
			plugin.rewardObserver = new RewardObserver(game.client, catalog, catalog::contains, () -> 1000L);
			set("client", client);
			set("config", config);
			set("assignmentReader", new AssignmentReader(client, config));
			set("collectionItems", catalog);
		}

		void loot()
		{
			plugin.onLootReceived(receipt());
		}
		void settle()
		{
			game.tick += RewardObserver.SETTLE_TICKS;
			plugin.flushRewards(5000L);
		}

		private void set(String name, Object value) throws Exception
		{
			Field field = MapCompanionPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(plugin, value);
		}
	}
}
