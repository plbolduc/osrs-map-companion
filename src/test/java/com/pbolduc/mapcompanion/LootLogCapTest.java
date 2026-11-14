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
import java.util.Collections;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The kill log on the wire is cut to forty monsters, and the log itself can hold hundreds. The
 * order the log gives them in decides which forty, so the log may stop building rows once the cut
 * is certain, as long as what is sent is what building every row would have sent.
 *
 * <p>The real log over the fake client, and the real trimmer after it.
 */
public class LootLogCapTest
{
	private final HarnessGame game = new HarnessGame();
	private final LootLog log = new LootLog(game.client(), new HarnessStore(), new Gson());
	private final long[] now = {1_000_000L};

	public LootLogCapTest()
	{
		game.nameItem(995, "Coins");
		log.clock(() -> now[0]);
	}

	/** One kill each of {@code monsters}, a second apart, "Monster 0" first. */
	private void kill(int monsters)
	{
		for (int i = 0; i < monsters; i++)
		{
			now[0] += 1_000L;
			log.record(new ServerNpcLoot(game.npc("Monster " + i),
				Collections.singletonList(new ItemStack(995, 1))));
		}
	}

	@Test
	public void aLongLogSendsTheFortyMostRecentAndSaysItWasCut()
	{
		int monsters = SnapshotTrimmer.MAX_LOOT_NPCS + 25;
		kill(monsters);

		CompanionSnapshot snapshot = new CompanionSnapshot();
		snapshot.loot = log.snapshot();
		assertEquals("the forty, and the one row more that says there were more",
			SnapshotTrimmer.MAX_LOOT_NPCS + 1, snapshot.loot.npcs.size());

		new SnapshotTrimmer(new Gson()).fit(snapshot);
		assertEquals(SnapshotTrimmer.MAX_LOOT_NPCS, snapshot.loot.npcs.size());
		assertEquals("a reader is told the list is not the whole log", Boolean.TRUE, snapshot.detailsTruncated);
		for (int i = 0; i < SnapshotTrimmer.MAX_LOOT_NPCS; i++)
		{
			assertEquals("most recently killed first", "Monster " + (monsters - 1 - i), snapshot.loot.npcs.get(i).npc);
		}
	}

	/** At the cap nothing is cut, and nothing says it was. */
	@Test
	public void aLogOfFortyIsSentWhole()
	{
		kill(SnapshotTrimmer.MAX_LOOT_NPCS);

		CompanionSnapshot snapshot = new CompanionSnapshot();
		snapshot.loot = log.snapshot();
		new SnapshotTrimmer(new Gson()).fit(snapshot);
		assertEquals(SnapshotTrimmer.MAX_LOOT_NPCS, snapshot.loot.npcs.size());
		assertNull(snapshot.detailsTruncated);
	}
}
