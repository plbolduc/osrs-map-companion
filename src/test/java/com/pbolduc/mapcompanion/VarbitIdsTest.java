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

import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Every game variable this plugin reads, against the client's own name for it.
 *
 * <p><b>Why this is a test and not a comment.</b> {@link VarbitIds} and {@link VarPlayerIds} are
 * literals, deliberately — that is what lets this repository compile against a range of client
 * releases rather than only the one whose generated constants happen to be on the classpath. The
 * price of a literal is that nothing checks it, and a wrong one has no symptom: an id the game
 * does not recognise reads as some other flag entirely, and every reader here is written to
 * degrade quietly rather than to complain. So a mistyped number becomes a feature that is
 * confidently wrong for as long as nobody re-checks it by hand.
 *
 * <p>That is not hypothetical. {@code SLAYER_TARGET_BOSS} said 4145 for two releases, which is
 * {@code VarbitID.FOUNTAIN_OF_RUNE_ACTIVE} — so a boss assignment was named from whether a
 * fountain was lit, and the map simply showed no task. This file is what would have caught it,
 * and it costs one comparison per constant.
 *
 * <p>It compiles against the constants rather than repeating their values, so it cannot drift
 * with the table it guards: if a client release moves an id, this stops compiling or starts
 * failing, which is the notice a plugin wants before the game gives it one.
 *
 * <p>Against {@code gameval} rather than {@code net.runelite.api.Varbits}, which names the same
 * diary flags and is deprecated — a test that spends forty-eight deprecation warnings to make its
 * point is a test that buries the ones a client update raises.
 */
public class VarbitIdsTest
{
	@Test
	public void namesTheSlayerVariablesTheClientNames()
	{
		assertEquals(VarPlayerID.SLAYER_COUNT, VarPlayerIds.SLAYER_COUNT);
		assertEquals(VarPlayerID.SLAYER_TARGET, VarPlayerIds.SLAYER_TARGET);
		assertEquals(VarPlayerID.SLAYER_AREA, VarPlayerIds.SLAYER_AREA);
		/** The one that was wrong. {@code Varbits} spells the same id {@code SLAYER_TASK_BOSS}. */
		assertEquals(VarbitID.SLAYER_TARGET_BOSSID, VarbitIds.SLAYER_TARGET_BOSS);
	}

	@Test
	public void namesTheFarmingTransmitSlotsTheClientNames()
	{
		/**
		 * Not per-patch ids. {@link FarmingPatchCatalog} is what pairs one of these with a region;
		 * on its own a slot names nothing, which is exactly why a wrong number here would be read
		 * as a real patch state rather than as an error.
		 */
		assertEquals(VarbitID.FARMING_TRANSMIT_A, VarbitIds.FARMING_TRANSMIT_A);
		assertEquals(VarbitID.FARMING_TRANSMIT_B, VarbitIds.FARMING_TRANSMIT_B);
		assertEquals(VarbitID.FARMING_TRANSMIT_D, VarbitIds.FARMING_TRANSMIT_D);
		assertEquals(VarbitID.FARMING_TRANSMIT_E, VarbitIds.FARMING_TRANSMIT_E);
		assertEquals(VarbitID.FARMING_TRANSMIT_G, VarbitIds.FARMING_TRANSMIT_G);
		assertEquals(VarbitID.FARMING_TRANSMIT_K, VarbitIds.FARMING_TRANSMIT_K);
	}

	@Test
	public void namesTheDiaryCompletionFlagsTheClientNames()
	{
		assertArrayEquals(tiers(
							  VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
							  VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_ARDOUGNE);
		assertArrayEquals(tiers(
							  VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
							  VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_DESERT);
		assertArrayEquals(tiers(
							  VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
							  VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_FALADOR);
		assertArrayEquals(tiers(
							  VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
							  VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE),
						  VarbitIds.DIARY_FREMENNIK);
		assertArrayEquals(tiers(
							  VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
							  VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_KANDARIN);
		/** Karamja's three older flags are {@code ATJUN_*_DONE}; only the elite one is modern. */
		assertArrayEquals(tiers(
							  VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE,
							  VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_KARAMJA);
		assertArrayEquals(tiers(
							  VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
							  VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_KOUREND);
		assertArrayEquals(tiers(
							  VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
							  VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE),
						  VarbitIds.DIARY_LUMBRIDGE);
		assertArrayEquals(tiers(
							  VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
							  VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE),
						  VarbitIds.DIARY_MORYTANIA);
		assertArrayEquals(tiers(
							  VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
							  VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_VARROCK);
		assertArrayEquals(tiers(
							  VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
							  VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE), VarbitIds.DIARY_WESTERN);
		assertArrayEquals(tiers(
							  VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
							  VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE),
						  VarbitIds.DIARY_WILDERNESS);
	}

	@Test
	public void namesTheDiaryRewardFlagsTheClientNames()
	{
		assertArrayEquals(tiers(
							  VarbitID.ARDOUGNE_EASY_REWARD, VarbitID.ARDOUGNE_MEDIUM_REWARD,
							  VarbitID.ARDOUGNE_HARD_REWARD, VarbitID.ARDOUGNE_ELITE_REWARD),
						  VarbitIds.DIARY_ARDOUGNE_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.DESERT_EASY_REWARD, VarbitID.DESERT_MEDIUM_REWARD,
							  VarbitID.DESERT_HARD_REWARD, VarbitID.DESERT_ELITE_REWARD),
						  VarbitIds.DIARY_DESERT_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.FALADOR_EASY_REWARD, VarbitID.FALADOR_MEDIUM_REWARD,
							  VarbitID.FALADOR_HARD_REWARD, VarbitID.FALADOR_ELITE_REWARD),
						  VarbitIds.DIARY_FALADOR_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.FREMENNIK_EASY_REWARD, VarbitID.FREMENNIK_MEDIUM_REWARD,
							  VarbitID.FREMENNIK_HARD_REWARD, VarbitID.FREMENNIK_ELITE_REWARD),
						  VarbitIds.DIARY_FREMENNIK_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.KANDARIN_EASY_REWARD, VarbitID.KANDARIN_MEDIUM_REWARD,
							  VarbitID.KANDARIN_HARD_REWARD, VarbitID.KANDARIN_ELITE_REWARD),
						  VarbitIds.DIARY_KANDARIN_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.ATJUN_EASY_REWARD, VarbitID.ATJUN_MED_REWARD,
							  VarbitID.ATJUN_HARD_REWARD, VarbitID.KARAMJA_ELITE_REWARD),
						  VarbitIds.DIARY_KARAMJA_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.KOUREND_EASY_REWARD, VarbitID.KOUREND_MEDIUM_REWARD,
							  VarbitID.KOUREND_HARD_REWARD, VarbitID.KOUREND_ELITE_REWARD),
						  VarbitIds.DIARY_KOUREND_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.LUMBRIDGE_EASY_REWARD, VarbitID.LUMBRIDGE_MEDIUM_REWARD,
							  VarbitID.LUMBRIDGE_HARD_REWARD, VarbitID.LUMBRIDGE_ELITE_REWARD),
						  VarbitIds.DIARY_LUMBRIDGE_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.MORYTANIA_EASY_REWARD, VarbitID.MORYTANIA_MEDIUM_REWARD,
							  VarbitID.MORYTANIA_HARD_REWARD, VarbitID.MORYTANIA_ELITE_REWARD),
						  VarbitIds.DIARY_MORYTANIA_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.VARROCK_EASY_REWARD, VarbitID.VARROCK_MEDIUM_REWARD,
							  VarbitID.VARROCK_HARD_REWARD, VarbitID.VARROCK_ELITE_REWARD),
						  VarbitIds.DIARY_VARROCK_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.WESTERN_EASY_REWARD, VarbitID.WESTERN_MEDIUM_REWARD,
							  VarbitID.WESTERN_HARD_REWARD, VarbitID.WESTERN_ELITE_REWARD),
						  VarbitIds.DIARY_WESTERN_REWARDS);
		assertArrayEquals(tiers(
							  VarbitID.WILDERNESS_EASY_REWARD, VarbitID.WILDERNESS_MEDIUM_REWARD,
							  VarbitID.WILDERNESS_HARD_REWARD, VarbitID.WILDERNESS_ELITE_REWARD),
						  VarbitIds.DIARY_WILDERNESS_REWARDS);
	}

	private static int[] tiers(int easy, int medium, int hard, int elite)
	{
		return new int[] {easy, medium, hard, elite};
	}
}
