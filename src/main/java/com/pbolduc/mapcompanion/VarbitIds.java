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

/**
 * Varbits, kept beside {@link VarPlayerIds} and for the same reason — see that file's note on why
 * this surface is isolated and how to verify it against a new client release.
 *
 * <p>Every id below is transcribed from the RuneLite artefacts this build compiles against
 * ({@code net.runelite:client:1.12.38}), with the symbol it came from named beside it, so a
 * reviewer can check a number against its source rather than trusting anybody's memory. None of
 * these were written from recall.
 *
 * <p>{@code VarbitIdsTest} compiles those symbols and compares them, because a claim of that shape
 * is worth exactly as much as the last person who checked it — and one of these numbers was wrong
 * while the claim was already written above it.
 */
final class VarbitIds
{
	/**
	 * Which boss, when {@code SLAYER_TARGET} holds the boss sentinel.
	 *
	 * <p>{@code net.runelite.api.gameval.VarbitID.SLAYER_TARGET_BOSSID}, which
	 * {@code net.runelite.api.Varbits} spells {@code SLAYER_TASK_BOSS} and RuneLite's own
	 * {@code SlayerPlugin} reads to name a boss assignment. It used to say 4145 here, which is
	 * {@code FOUNTAIN_OF_RUNE_ACTIVE} — so every boss task this plugin reported was named from
	 * whether a fountain was lit, and the failure was invisible because an unresolvable id simply
	 * produces no task name at all.
	 */
	static final int SLAYER_TARGET_BOSS = 4723;

	/**
	 * The farming "transmit" slots.
	 *
	 * <p>These are <b>not</b> per-patch ids, and reading them as if they were is the mistake this
	 * comment exists to prevent. The game reports the patches of whichever region you are standing
	 * in through a small window of shared varbits, so 4774 is Falador's herb patch in Falador and
	 * Catherby's herb patch in Catherby. {@link FarmingPatchCatalog} is what pairs a slot with a
	 * region; a slot on its own names nothing.
	 *
	 * <p>Names and values from {@code net.runelite.api.gameval.VarbitID.FARMING_TRANSMIT_*}. Only
	 * the slots this plugin actually reads are listed — an unused constant is one more number
	 * nobody will re-check on the next client release.
	 */
	static final int FARMING_TRANSMIT_A = 4771;
	static final int FARMING_TRANSMIT_B = 4772;
	static final int FARMING_TRANSMIT_D = 4774;
	static final int FARMING_TRANSMIT_E = 4775;
	static final int FARMING_TRANSMIT_G = 7905;
	static final int FARMING_TRANSMIT_K = 7909;

	/**
	 * Achievement diary completion, four tiers per region in easy, medium, hard, elite order.
	 *
	 * <p>From {@code net.runelite.api.Varbits.DIARY_*}, which is also where the map's own
	 * {@code lib/diaries.ts} took its copy — the two tables have to agree, or a diary read here
	 * would be looked up there under a different number and silently never join.
	 *
	 * <p>Karamja is the odd one out and stays that way: its three lower tiers predate the rest and
	 * are named {@code ATJUN_*_DONE} in {@code gameval.VarbitID}, which is why they do not follow
	 * the four-in-a-row pattern every other region does. {@link DiaryReader} also reads their
	 * values differently, and says there why.
	 */
	static final int[] DIARY_ARDOUGNE = {4458, 4459, 4460, 4461};
	static final int[] DIARY_DESERT = {4483, 4484, 4485, 4486};
	static final int[] DIARY_FALADOR = {4462, 4463, 4464, 4465};
	static final int[] DIARY_FREMENNIK = {4491, 4492, 4493, 4494};
	static final int[] DIARY_KANDARIN = {4475, 4476, 4477, 4478};
	/** {@code ATJUN_EASY_DONE}, {@code ATJUN_MED_DONE}, {@code ATJUN_HARD_DONE}, then the modern elite flag. */
	static final int[] DIARY_KARAMJA = {3578, 3599, 3611, 4566};
	static final int[] DIARY_KOUREND = {7925, 7926, 7927, 7928};
	static final int[] DIARY_LUMBRIDGE = {4495, 4496, 4497, 4498};
	static final int[] DIARY_MORYTANIA = {4487, 4488, 4489, 4490};
	static final int[] DIARY_VARROCK = {4479, 4480, 4481, 4482};
	static final int[] DIARY_WESTERN = {4471, 4472, 4473, 4474};
	static final int[] DIARY_WILDERNESS = {4466, 4467, 4468, 4469};

	/**
	 * Achievement diary <i>rewards</i>, in the same tier order.
	 *
	 * <p>A separate flag from completion, and the distinction is the whole reason both are sent:
	 * finishing the tasks is not the same as having gone to the diary's NPC and taken the cloak,
	 * and only the second one means the teleport on it actually works. The map ranks routes on
	 * what a player can use, so guessing one from the other would recommend a teleport nobody
	 * owns.
	 *
	 * <p>From {@code net.runelite.api.gameval.VarbitID.<REGION>_<TIER>_REWARD}, and for Karamja
	 * from {@code ATJUN_EASY_REWARD}, {@code ATJUN_MED_REWARD}, {@code ATJUN_HARD_REWARD} and
	 * {@code KARAMJA_ELITE_REWARD}.
	 */
	static final int[] DIARY_ARDOUGNE_REWARDS = {4499, 4500, 4501, 4502};
	static final int[] DIARY_DESERT_REWARDS = {4523, 4524, 4525, 4526};
	static final int[] DIARY_FALADOR_REWARDS = {4503, 4504, 4505, 4506};
	static final int[] DIARY_FREMENNIK_REWARDS = {4531, 4532, 4533, 4534};
	static final int[] DIARY_KANDARIN_REWARDS = {4515, 4516, 4517, 4518};
	static final int[] DIARY_KARAMJA_REWARDS = {3577, 3598, 3610, 4567};
	static final int[] DIARY_KOUREND_REWARDS = {7929, 7930, 7931, 7932};
	static final int[] DIARY_LUMBRIDGE_REWARDS = {4535, 4536, 4537, 4538};
	static final int[] DIARY_MORYTANIA_REWARDS = {4527, 4528, 4529, 4530};
	static final int[] DIARY_VARROCK_REWARDS = {4519, 4520, 4521, 4522};
	static final int[] DIARY_WESTERN_REWARDS = {4511, 4512, 4513, 4514};
	static final int[] DIARY_WILDERNESS_REWARDS = {4507, 4508, 4509, 4510};

	private VarbitIds()
	{
	}
}
