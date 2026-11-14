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

	private VarbitIds()
	{
	}
}
