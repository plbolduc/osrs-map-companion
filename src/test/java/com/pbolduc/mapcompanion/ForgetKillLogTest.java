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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Forget kill log asks before it clears, as Leave group beside it does.
 *
 * <p>The log is months of counting that nothing else keeps, and the button sits in one row with
 * Leave group, where a press meant for one lands on the other. A press that is not followed by a
 * yes leaves every count where it was.
 */
public class ForgetKillLogTest
{
	private final HarnessGame game = new HarnessGame();
	private final LootLog log = new LootLog(game.client(), new HarnessStore(), new Gson());

	public ForgetKillLogTest()
	{
		game.nameItem(995, "Coins");
		log.record(new ServerNpcLoot(game.npc("Abyssal demon"), Collections.singletonList(new ItemStack(995, 1))));
	}

	@Test
	public void onePressAloneDoesNotClearTheLog()
	{
		assertTrue(log.hasLog());
		assertFalse(SettingsPage.forgetKillLog(log, () -> false));
		assertTrue("the press was not followed by a yes", log.hasLog());
		assertEquals(1, log.snapshot().npcs.get(0).kills);
	}

	@Test
	public void aYesClearsIt()
	{
		assertTrue(SettingsPage.forgetKillLog(log, () -> true));
		assertFalse(log.hasLog());
	}
}
