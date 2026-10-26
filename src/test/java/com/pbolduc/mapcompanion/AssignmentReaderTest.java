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
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.timetracking.farming.Produce;
import org.junit.Test;
import static org.junit.Assert.*;

/** Accepted assignments, consent withdrawal and session gaps are different evidence. */
public class AssignmentReaderTest
{
	private final Map<String, Boolean> enabled = new HashMap<>();
	private final Map<Integer, Integer> bits = new HashMap<>();
	private final MapCompanionConfig config = new MapCompanionConfig()
	{
		@Override public boolean shareHunterRumours() { return enabled.getOrDefault("rumour", false); }
		@Override public boolean shareMahoganyHomes() { return enabled.getOrDefault("home", false); }
		@Override public boolean shareFarmingContracts() { return enabled.getOrDefault("farm", false); }
	};
	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
		(proxy, method, args) ->
		{
			if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
			if (method.getName().equals("getWidget")) return null;
			if (method.getName().equals("getVarbitValue")) return bits.getOrDefault((Integer) args[0], -1);
			throw new UnsupportedOperationException(method.getName());
		});
	private final AssignmentReader reader = new AssignmentReader(client, config);

	private void message(ChatMessageType type, String text, long now)
	{
		ChatMessage event = new ChatMessage();
		event.setType(type);
		event.setMessage(text);
		reader.onChatMessage(event, now);
	}

	@Test
	public void onlyTheWhistlesServerMessageConfirmsARumour()
	{
		String text = "Your current rumour target is a red salamander, assigned by Wolf.";
		message(ChatMessageType.GAMEMESSAGE, text, 100);
		enabled.put("rumour", true);
		assertNull(reader.rumour(101));
		message(ChatMessageType.PUBLICCHAT, text, 100);
		assertNull(reader.rumour(101));
		message(ChatMessageType.GAMEMESSAGE, text, 100);
		assertEquals("Red Salamander", reader.rumour(200).name);
		assertEquals("Wolf", reader.rumour(200).detail);
		assertEquals(100, reader.rumour(200).observedMsAgo);
		message(ChatMessageType.GAMEMESSAGE, "You find a rare piece of the creature! You should take it back to the Hunter Guild.", 300);
		assertEquals("return", reader.rumour(301).state);
		message(ChatMessageType.GAMEMESSAGE, text, 300);
		assertEquals("return", reader.rumour(301).state);
		assertNull(reader.rumour(7_200_301));
		enabled.put("rumour", false);
		reader.onSharingChanged();
		enabled.put("rumour", true);
		assertNull(reader.rumour(400));
		message(ChatMessageType.GAMEMESSAGE, text, 500);
		reader.onSessionEnded();
		assertNull(reader.rumour(501));
	}

	@Test
	public void acceptedHomeAndReminderAreBoundedAndCompletionClearsThem()
	{
		enabled.put("home", true);
		reader.readHomeDialog("Go see Jess, north of Ardougne church. You can get another job once you have furnished her home.", 1_000);
		assertEquals("Jess", reader.home(1_001).name);
		reader.readHomeDialog("You're currently on an Expert Contract. Go see Norman, in Falador. You can get another job once you have furnished his home.", 2_000);
		assertEquals("Norman", reader.home(2_001).name);
		assertEquals("Expert", reader.home(2_001).detail);
		message(ChatMessageType.GAMEMESSAGE, "You have completed 1,234 contracts with a total of 10 points.", 3_000);
		assertNull(reader.home(3_001));
		message(ChatMessageType.GAMEMESSAGE, "Expert Contract: Go see Sarah, in Varrock.", 4_000);
		assertEquals("Sarah", reader.home(4_001).name);
		message(ChatMessageType.GAMEMESSAGE, "Expert Contract: Go see Someone, in Varrock.", 5_000);
		assertNull(reader.home(5_001));
	}

	@Test
	public void farmingUsesTheCoreCatalogueAfterEachLoginSettles()
	{
		enabled.put("farm", true);
		reader.observe(10_000);
		bits.put(VarbitID.FARMGUILD_CONTRACT_COMPLETE, 0);
		bits.put(VarbitID.FARMGUILD_CONTRACT_TYPE, Produce.SNAPDRAGON.getContractVarbitValue());
		assertNull(reader.farming(24_999));
		assertEquals("Snapdragon", reader.farming(25_000).name);
		assertEquals("herb", reader.farming(25_000).detail);
		bits.put(VarbitID.FARMGUILD_CONTRACT_COMPLETE, 1);
		assertNull(reader.farming(25_000));
		bits.put(VarbitID.FARMGUILD_CONTRACT_COMPLETE, 0);
		bits.put(VarbitID.FARMGUILD_CONTRACT_TYPE, 100_000);
		assertNull(reader.farming(25_000));
		bits.put(VarbitID.FARMGUILD_CONTRACT_TYPE, Produce.SNAPDRAGON.getContractVarbitValue());
		reader.onSessionEnded();
		assertNull(reader.farming(25_000));
	}
}
