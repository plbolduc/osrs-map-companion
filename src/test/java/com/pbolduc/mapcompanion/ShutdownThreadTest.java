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
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ClientShutdown;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Turning the plugin off leaves everything a game tick owns to the client thread.
 *
 * <p>RuneLite calls shutDown on the EDT without waiting for a tick already running, and a tick
 * past its checks can open the group stream or post a snapshot. Torn down on the EDT, that tick
 * could reopen the stream after it was stopped; queued, the teardown runs after it.
 *
 * <p>Every reader, the relay and the stream are left null here, so anything shutDown touched on
 * the calling thread would throw. It must touch none of them, and queue exactly one task.
 */
public class ShutdownThreadTest
{
	@Test
	public void shutDownQueuesTheTeardownRatherThanRunningIt() throws Exception
	{
		List<Runnable> queued = new ArrayList<>();
		MapCompanionPlugin plugin = new MapCompanionPlugin()
		{
			@Override
			void removeInterface()
			{
				/* RuneLite's toolbar and overlay manager need a running client to construct. */
			}
		};
		Field field = MapCompanionPlugin.class.getDeclaredField("clientThread");
		field.setAccessible(true);
		field.set(plugin, new ClientThread()
		{
			@Override
			public void invoke(Runnable r)
			{
				queued.add(r);
			}

			@Override
			public void invokeLater(Runnable r)
			{
				queued.add(r);
			}
		});

		plugin.shutDown();

		assertEquals("the stream, relay and readers wait for the client thread", 1, queued.size());
	}

	/** Closing RuneLite skips shutDown, so the event it does post is the last chance to save. */
	@Test
	public void closingRuneLiteWritesWhatTheSaveWindowsHeld() throws Exception
	{
		AtomicInteger flushed = new AtomicInteger();
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		set(plugin, "lootLog", new LootLog(new HarnessGame().client(), new HarnessStore(), new Gson())
		{
			@Override
			public synchronized void flush()
			{
				flushed.incrementAndGet();
			}
		});
		set(plugin, "farmingObserver", new FarmingObserver(null, new HarnessStore(), new Gson())
		{
			@Override
			void flush()
			{
				flushed.incrementAndGet();
			}
		});

		plugin.onClientShutdown(new ClientShutdown());

		assertEquals("the kill log and the patch history", 2, flushed.get());
	}

	private static void set(MapCompanionPlugin plugin, String name, Object value) throws Exception
	{
		Field field = MapCompanionPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
