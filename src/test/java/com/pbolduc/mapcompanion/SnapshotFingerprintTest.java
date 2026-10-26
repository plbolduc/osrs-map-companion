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
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

/**
 * Time-only fields must not make two snapshots look different.
 *
 * <p>Varbit churn used to post at the two-second floor while the player stood still, because
 * {@code dirty} was the authority and clocks always change. The fingerprint is the authority
 * now; these cases are the ones that would have kept the thirty-requests-a-minute idle.
 */
public class SnapshotFingerprintTest
{
	private final SnapshotFingerprint fingerprint = new SnapshotFingerprint(new Gson());

	@Test
	public void ignoresClocks()
	{
		CompanionSnapshot a = base();
		a.sentAtMs = 1_000L;
		a.idleMs = 5L;
		a.activeSkill = new CompanionSnapshot.ActiveSkill();
		a.activeSkill.skill = "Mining";
		a.activeSkill.xpGained5m = 100;
		a.activeSkill.msSinceLastDrop = 250;
		a.farming = new CompanionSnapshot.Farming();
		a.farming.patches = new ArrayList<>();
		CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
		patch.id = "falador-herb";
		patch.state = "growing";
		patch.observedMsAgo = 60_000L;
		a.farming.patches.add(patch);
		a.death = new CompanionSnapshot.Death();
		a.death.tile = new CompanionSnapshot.Tile(3222, 3218, 0);
		a.death.msAgo = 12_000L;

		CompanionSnapshot b = base();
		b.sentAtMs = 9_000L;
		b.idleMs = 80L;
		b.activeSkill = new CompanionSnapshot.ActiveSkill();
		b.activeSkill.skill = "Mining";
		b.activeSkill.xpGained5m = 100;
		b.activeSkill.msSinceLastDrop = 8_000L;
		b.farming = new CompanionSnapshot.Farming();
		b.farming.patches = new ArrayList<>();
		CompanionSnapshot.Patch patchB = new CompanionSnapshot.Patch();
		patchB.id = "falador-herb";
		patchB.state = "growing";
		patchB.observedMsAgo = 120_000L;
		b.farming.patches.add(patchB);
		b.death = new CompanionSnapshot.Death();
		b.death.tile = new CompanionSnapshot.Tile(3222, 3218, 0);
		b.death.msAgo = 90_000L;

		assertEquals(fingerprint.of(a), fingerprint.of(b));
	}

	@Test
	public void aOneTileMoveIsADifferentSnapshot()
	{
		CompanionSnapshot a = base();
		a.pos = new CompanionSnapshot.Tile(3185, 3436, 0);
		CompanionSnapshot b = base();
		b.pos = new CompanionSnapshot.Tile(3186, 3436, 0);
		assertNotEquals(fingerprint.of(a), fingerprint.of(b));
	}

	@Test
	public void nullQuestsAndAnEmptyListAreDifferentThings()
	{
		/**
		 * The map's router treats [] as "none finished" and null as "not shared". Collapsing
		 * them in the fingerprint would make turning the quests toggle off look like no
		 * change, and the map would keep routing on a list the player just refused to send.
		 */
		CompanionSnapshot absent = base();
		absent.questsCompleted = null;
		CompanionSnapshot empty = base();
		empty.questsCompleted = Collections.emptyList();
		assertNotEquals(fingerprint.of(absent), fingerprint.of(empty));
	}

	private static CompanionSnapshot base()
	{
		CompanionSnapshot snapshot = new CompanionSnapshot();
		snapshot.member = new CompanionSnapshot.Member("x", "A");
		return snapshot;
	}
}
