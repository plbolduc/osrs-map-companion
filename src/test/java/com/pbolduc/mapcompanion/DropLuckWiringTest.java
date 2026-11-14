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
import net.runelite.api.ChatMessageType;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Drop luck through the plugin's own subscriptions, not {@link LootLog}'s methods: which chat type,
 * which text and which event fields reach the log. Every LootLog test calls the log directly, so a
 * wrong message type or an uncleaned line would pass all of them.
 */
public class DropLuckWiringTest
{
	private static final int BONES = 526;
	private static final char NBSP = (char) 0xA0;

	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, "http://localhost");
	private final LootLog log = new LootLog(game.client(), store, new Gson());
	private final MapCompanionPlugin plugin = TestLoop.in(new MapCompanionPlugin());

	public DropLuckWiringTest()
	{
		game.login("Example", 444, 3222, 3218, 0);
		game.nameItem(BONES, "Bones");
		/* Unpaired, so no reward is observed; the chat line it would print needs a chat manager. */
		config.set("dropLuckChat", false);
		plugin.loop.rewardObserver = new RewardObserver(game.client(), new RewardTestClient().catalog());
		TestLoop.set(plugin, "client", game.client());
		TestLoop.set(plugin, "config", config);
		TestLoop.set(plugin, "settings", store);
		TestLoop.set(plugin, "assignmentReader", new AssignmentReader(game.client(), store));
		TestLoop.set(plugin, "lootLog", log);
	}

	private void kill(String npc)
	{
		plugin.onServerNpcLoot(new ServerNpcLoot(game.npc(npc), Collections.singletonList(new ItemStack(BONES, 1))));
	}

	private void chat(String text)
	{
		plugin.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, "", 0));
	}

	private LootLog.Drop drop(String npc, String item)
	{
		LootLog.Monster monster = DropLuckView.find(log.monsters(), npc);
		assertNotNull(npc, monster);
		return monster.drops.stream().filter(d -> d.item.equals(item)).findFirst().orElse(null);
	}

	/** The game's line may carry its colour, or a non-breaking space; the pet is counted either way. */
	@Test
	public void aPetLineIsReadWithoutItsTags()
	{
		kill("Cerberus");
		chat("<col=ef1020>You have a funny feeling like you're being followed.</col>");
		assertEquals(1, drop("Cerberus", "Hellpuppy").obtained);
		game.tick();
		game.tick();
		kill("Cerberus");
		/* Said in public chat beside a kill no line has taken yet, so only the message type stops it. */
		plugin.onChatMessage(new ChatMessage(null, ChatMessageType.PUBLICCHAT, "Someone",
			"You have a funny feeling like you're being followed.", "", 0));
		assertEquals("another player saying it is no pet", 1, drop("Cerberus", "Hellpuppy").obtained);
		chat("You have a funny feeling like you're" + NBSP + "being followed.");
		assertEquals(2, drop("Cerberus", "Hellpuppy").obtained);
		chat("You have a funny feeling like you're being followed.");
		assertEquals("one line, one pet", 2, drop("Cerberus", "Hellpuppy").obtained);
	}

	/** The MVP line with a non-breaking space in it still restores Nex's 10%. */
	@Test
	public void theMvpLineIsReadWithoutItsTags()
	{
		log.onHitsplat("Nex", true, false, 50, game.client().getTickCount());
		log.onHitsplat("Nex", false, true, 50, game.client().getTickCount());
		kill("Nex");
		chat("<col=ff0000>You were the MVP for" + NBSP + "this fight.</col>");
		assertEquals(0.5, drop("Nex", "Ancient hilt").shareWatched, 1e-9);
	}

	/** A hitsplat on an NPC, read as the game marks it: the player's own, or another player's. */
	private void hit(String npc, int type, int amount)
	{
		HitsplatApplied event = new HitsplatApplied();
		event.setActor((NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class},
			(proxy, method, args) -> "getName".equals(method.getName()) ? npc : null));
		event.setHitsplat(new Hitsplat()
		{
			@Override
			public int getHitsplatType()
			{
				return type;
			}

			@Override
			public int getAmount()
			{
				return amount;
			}

			@Override
			public int getDisappearsOnGameCycle()
			{
				return 0;
			}
		});
		plugin.onHitsplatApplied(event);
	}

	/** Callisto's hits arrive as the game's own types, and a share is read from them in the right order. */
	@Test
	public void hitsplatsGiveASharedKillItsShare()
	{
		hit("Callisto", HitsplatID.DAMAGE_ME, 30);
		hit("Callisto", HitsplatID.DAMAGE_OTHER, 70);
		hit("Callisto", HitsplatID.POISON, 50);
		kill("Callisto");
		assertEquals("a poison is nobody's share", 0.3, drop("Callisto", "Claws of Callisto").shareWatched, 1e-9);
	}

	private void reward(String name, Object metadata)
	{
		plugin.onLootReceived(new LootReceived(name, -1, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(BONES, 1)), 1, metadata));
	}

	/** The chest counts only once the game says all six brothers died; five is fewer rolls. */
	@Test
	public void aBarrowsChestReadsTheSixBrothersFromTheGame()
	{
		int[] brothers = {VarbitID.BARROWS_KILLED_AHRIM, VarbitID.BARROWS_KILLED_DHAROK, VarbitID.BARROWS_KILLED_GUTHAN,
			VarbitID.BARROWS_KILLED_KARIL, VarbitID.BARROWS_KILLED_TORAG, VarbitID.BARROWS_KILLED_VERAC};
		for (int i = 0; i < 5; i++)
		{
			game.setVarbit(brothers[i], 1);
		}
		reward("Barrows", null);
		assertNull("five brothers", DropLuckView.find(log.monsters(), "Barrows"));
		game.setVarbit(brothers[5], 1);
		reward("Barrows", null);
		assertEquals(1, DropLuckView.find(log.monsters(), "Barrows").kills);
	}

	/** The Doom's run is counted at the delve the loot tracker's event carries, and not without one. */
	@Test
	public void aDoomRunIsCountedAtTheDelveItsEventCarries()
	{
		reward("Doom of Mokhaiotl", null);
		assertNull(DropLuckView.find(log.monsters(), "Doom of Mokhaiotl"));
		reward("Doom of Mokhaiotl", 3);
		double per = Math.log1p(-1 / 540.0);
		assertEquals((Math.log1p(-1 / 2500.0) + Math.log1p(-1 / 2000.0)) / per,
			drop("Doom of Mokhaiotl", "Mokhaiotl cloth").shareWatched, 1e-9);
	}
}
