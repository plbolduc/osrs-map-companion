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

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Mortimer's offer as the game's task choice interface reads it. The fake table's row ids differ
 * from its id column, as the real one's do, so a task varbit read as a row would name nothing.
 */
public class MortimerOfferReaderTest
{
	private static final int GARGOYLES = 46;
	private static final int KURASK = 45;

	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, String> names = new HashMap<>();
	private int slayerCount;
	private String[] shown;

	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
		(proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) args[0], 0);
				case "getVarpValue":
					return (Integer) args[0] == VarPlayerID.SLAYER_COUNT ? slayerCount : 0;
				case "getDBRowsByValue":
					int value = (Integer) args[3];
					return (Integer) args[0] == DBTableID.SlayerTask.ID && names.containsKey(value)
						? Collections.singletonList(6000 + value) : Collections.emptyList();
				case "getDBTableField":
					String name = names.get((Integer) args[0] - 6000);
					return name == null ? null : new Object[] {name};
				case "getWidget":
					return (Integer) args[0] == InterfaceID.SlayerTaskChoice.CONTENT && shown != null ? content() : null;
				default:
					return HarnessGame.zero(method.getReturnType());
			}
		});

	private final MortimerOfferReader reader = new MortimerOfferReader(client);

	@Test
	public void readsTheOfferTheInterfaceShowsAndHoldsItWhenItCloses()
	{
		offer();
		reader.onSessionResumed(0);
		reader.observe(1_000);
		assertNull("nothing until the session has settled", reader.offer());
		reader.observe(15_000);
		List<CompanionSnapshot.MortimerTask> offer = reader.offer();
		assertEquals(2, offer.size());
		assertEquals("Gargoyles", offer.get(0).taskName);
		assertEquals(GARGOYLES, offer.get(0).taskId);
		assertEquals("superior", offer.get(0).mortifier);
		assertEquals(150, offer.get(0).value);
		assertEquals("quantity", offer.get(1).mortifier);
		assertEquals("a negative quantity is a smaller task", -20, offer.get(1).value);

		shown = null;
		reader.observe(16_000);
		assertEquals("closing the interface keeps the offer it showed", 2, reader.offer().size());
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_1, KURASK);
		reader.observe(17_000);
		assertNull("a new roll the interface has not shown is no offer", reader.offer());
	}

	@Test
	public void sendsNothingTheInterfaceDoesNotName()
	{
		offer();
		shown = new String[] {"Kurask", "Amount: 50"};
		reader.onSessionResumed(0);
		reader.observe(15_000);
		assertNull("old varbits behind a different offer", reader.offer());
	}

	@Test
	public void aTaskTakenEndsTheOffer()
	{
		offer();
		reader.onSessionResumed(0);
		reader.observe(15_000);
		slayerCount = 120;
		reader.observe(16_000);
		assertNull(reader.offer());
	}

	@Test
	public void anUnknownMortifierOrABossTaskIsNoOffer()
	{
		offer();
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID, 9);
		reader.onSessionResumed(0);
		reader.observe(15_000);
		assertNull(reader.offer());
		offer();
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_1_BOSS_ID, 3);
		reader.observe(16_000);
		assertNull(reader.offer());
	}

	private void offer()
	{
		names.put(GARGOYLES, "Gargoyles");
		names.put(KURASK, "Kurask");
		varbits.clear();
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_1, GARGOYLES);
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, 4);
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE, 150);
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_2, KURASK);
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID, 2);
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_VALUE, 20);
		varbits.put(VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_NEGATIVE, 1);
		shown = new String[] {"<col=ff981f>Gargoyles</col>", "+150% Superior unique chance", "Kurask", "-20 Assigned"};
	}

	private Widget content()
	{
		Widget[] children = new Widget[shown.length];
		for (int i = 0; i < shown.length; i++)
		{
			String text = shown[i];
			children[i] = widget(text, null);
		}
		return widget(null, children);
	}

	private static Widget widget(String text, Widget[] children)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getText":
						return text;
					case "getDynamicChildren":
						return children;
					case "isHidden":
						return false;
					default:
						return HarnessGame.zero(method.getReturnType());
				}
			});
	}
}
