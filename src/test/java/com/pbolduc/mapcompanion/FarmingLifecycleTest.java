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
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.WorldView;
import static org.junit.Assert.*;
import org.junit.Test;

/** Exercise the production entry point as well as recorded traces: login delay, modal and
 * instance guards must cancel evidence before the decoder ever sees a shared transmit slot. */
public class FarmingLifecycleTest
{
	private final HarnessGame game = new HarnessGame();
	private boolean modal;
	private boolean instanced;
	private boolean missingComponents;
	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class}, (proxy, method, args) ->
	{
		if (method.getName().equals("getTopLevelWorldView")) return Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[]{WorldView.class},
		(view, called, values) -> called.getName().equals("isInstance") ? instanced : HarnessGame.zero(called.getReturnType()));
		if (method.getName().equals("getComponentTable"))
		{
			if (missingComponents)
			{
				return null;
			}
			WidgetNode widget = (WidgetNode) Proxy.newProxyInstance(WidgetNode.class.getClassLoader(), new Class<?>[] {WidgetNode.class},
				(node, called, values) -> called.getName().equals("getModalMode") ? WidgetModalMode.NON_MODAL + 1 : HarnessGame.zero(called.getReturnType()));
			return new HashTable<WidgetNode>()
			{
				public WidgetNode get(long key)
				{
					return null;
				}

				public java.util.Iterator<WidgetNode> iterator()
				{
					return modal ? Collections.singleton(widget).iterator() : Collections.emptyIterator();
				}
			};
		}
		return method.invoke(game.client(), args);
	});
	private final ConfigStore store = new ConfigStore()
	{
		private final Map<String, String> values = new HashMap<>();
		public String get(String key)
		{
			return values.get(key);
		}

		public void set(String key, String value)
		{
			values.put(key, value);
		}

		public void unset(String key)
		{
			values.remove(key);
		}
	};
	private final FarmingObserver observer = new FarmingObserver(client, store, new Gson());
	private long now = 16_000L;
	private void read(int value)
	{
		game.setVarbit(VarbitID.FARMING_TRANSMIT_D, value);
		game.tick();
		observer.observe(now, 12083);
		now += 600L;
	}

	private void begin()
	{
		game.login("Test", 302, 3056, 3307, 0);
		observer.onSessionResumed(0);
		read(3);
		read(3);
		read(3);
	}

	private String receipt()
	{
		return observer.observations(now).get(0).completionId;
	}

	@Test
	public void theProductionEntryPointEmitsOnlyALocallyWitnessedReplant()
	{
		begin();
		assertNull(receipt());
		read(4);
		assertNotNull(receipt());
	}

	@Test
	public void theLoginDelayDoesNotBelieveInitialZeroes()
	{
		game.login("Test", 302, 3056, 3307, 0);
		observer.onSessionResumed(0);
		now = 1_000L;
		read(0);
		read(0);
		read(0);
		assertTrue(observer.observations(now).isEmpty());
		now = 16_000L;
		read(4);
		read(4);
		read(4);
		assertNull(receipt());
	}

	@Test
	public void aModalOrUnloadedComponentTableCannotWitnessMissedFarming()
	{
		begin();
		modal = true;
		read(4);
		modal = false;
		read(4);
		read(4);
		read(4);
		assertNull(receipt());
		read(3);
		missingComponents = true;
		read(4);
		missingComponents = false;
		read(4);
		read(4);
		read(4);
		assertNull(receipt());
	}

	@Test
	public void anInstanceOrLoadingGapCannotJoinTwoObservations()
	{
		begin();
		instanced = true;
		read(4);
		instanced = false;
		read(4);
		read(4);
		read(4);
		assertNull(receipt());
		read(3);
		observer.resetSession(); // The production loading/sharing handlers call this.
		read(4);
		read(4);
		read(4);
		assertNull(receipt());
	}
}
