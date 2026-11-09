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

import java.util.Map;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The dailies as RuneLite's Daily Task Indicators reads them, and only on the day the session began. */
public class DailyReaderTest
{
	private static final long NOON = 20_400L * 86_400_000L + 12 * 3_600_000L;

	private final HarnessGame game = new HarnessGame();
	private final DailyReader reader = new DailyReader(game.client());

	@Test
	public void saysNothingUntilTheSessionHasSettled()
	{
		reader.onSessionResumed(NOON);
		assertNull("every varbit reads zero for a moment after a login, which would be all waiting", reader.read(NOON + 1_000));
		assertTrue(reader.read(NOON + 15_000) != null);
		reader.onSessionEnded();
		assertNull(reader.read(NOON + 20_000));
	}

	@Test
	public void aClaimVarbitThatIsNotZeroIsCollected()
	{
		game.setVarbit(VarbitID.ZAFF_LAST_CLAIMED, 1);
		game.setVarbit(VarbitID.SEERS_FREE_FLAX, 1);
		Map<String, Boolean> read = settledRead();
		assertTrue(read.get("zaff-battlestaves"));
		assertTrue(read.get("flax-keeper-bow-strings"));
		assertFalse(read.get("cromperty-essence"));
		assertFalse(read.get("bert-sand"));
		assertFalse(read.get("rantz-ogre-arrows"));
		assertFalse(read.get("thirus-dynamite"));
		assertFalse("the kingdom has no claim varbit, so it is never answered", read.containsKey("miscellania-kingdom"));
	}

	@Test
	public void herbBoxesAndBonemealAreCollectedOnlyOnceTheDaysAllowanceIsTaken()
	{
		game.setVarbit(VarbitID.NZONE_HERBBOXES_PURCHASED, 14);
		assertFalse("no Morytania medium diary, so Robin offers nothing to have collected", settledRead().containsKey("robin-bonemeal"));
		assertFalse("one box left", settledRead().get("nmz-herb-boxes"));
		game.setVarbit(VarbitID.NZONE_HERBBOXES_PURCHASED, 15);
		assertTrue(settledRead().get("nmz-herb-boxes"));

		game.setVarbit(VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, 1);
		game.setVarbit(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, 1);
		game.setVarbit(VarbitID.MORYTANIA_SLIME_CLAIMED, 13);
		assertFalse("the hard diary doubles the allowance", settledRead().get("robin-bonemeal"));
		game.setVarbit(VarbitID.MORYTANIA_SLIME_CLAIMED, 26);
		assertTrue(settledRead().get("robin-bonemeal"));
	}

	@Test
	public void aSessionThatCrossesMidnightUtcSaysNothingUntilTheNextLogin()
	{
		game.setVarbit(VarbitID.ZAFF_LAST_CLAIMED, 1);
		reader.onSessionResumed(NOON);
		assertTrue(reader.read(NOON + 15_000).get("zaff-battlestaves"));
		long tomorrow = NOON + 12 * 3_600_000L + 60_000L;
		assertNull("yesterday's claim is not today's", reader.read(tomorrow));
		reader.onSessionEnded();
		reader.onSessionResumed(tomorrow);
		assertTrue(reader.read(tomorrow + 15_000) != null);
	}

	private Map<String, Boolean> settledRead()
	{
		reader.onSessionResumed(NOON);
		return reader.read(NOON + 15_000);
	}
}
