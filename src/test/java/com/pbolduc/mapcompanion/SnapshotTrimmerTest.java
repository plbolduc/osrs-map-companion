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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The snapshot fits, and says when fitting it cost something.
 *
 * <p>What makes this worth testing rather than eyeballing is the shape of the failure it prevents.
 * Going over the cap does not degrade anything: the relay answers 413, {@link RelayClient} treats
 * that as permanent, and the player's dot stops for good with a sidebar message blaming the map.
 * It would have arrived quietly, in somebody's second year of play, when their kill log finally
 * grew past sixteen kilobytes.
 *
 * <p>The other half is the honesty. Trimming to the map's own caps loses nothing, because the map
 * applies exactly those caps on arrival — so it must not raise the flag. Trimming past them does
 * lose something, and a panel showing six of somebody's forty monsters without saying so is worse
 * than one that shows fewer and admits it.
 */
public class SnapshotTrimmerTest
{
	/**
	 * Enough untrimmable bulk that the patch stage has to run, and not so much that it empties.
	 * A trimmer that dropped every patch would compare two empty lists and prove nothing.
	 */
	private static final int FILLER_QUESTS = 60;

	private final SnapshotTrimmer trimmer = new SnapshotTrimmer(new Gson());

	@Test
	public void leavesASmallSnapshotAloneAndClaimsNothing()
	{
		CompanionSnapshot snapshot = snapshotWith(3, 2);
		trimmer.fit(snapshot);
		assertEquals(3, snapshot.loot.npcs.size());
		assertNull(snapshot.detailsTruncated);
	}

	@Test
	public void capsTheMonsterListAndDisclosesMissingHistory()
	{
		/** Receiver caps are still omissions: the reader must know this is only part of the log. */
		CompanionSnapshot snapshot = snapshotWith(45, 1);
		trimmer.fit(snapshot);
		assertEquals(SnapshotTrimmer.MAX_LOOT_NPCS, snapshot.loot.npcs.size());
		assertEquals(Boolean.TRUE, snapshot.detailsTruncated);
	}

	@Test
	public void capsTheDropsPerMonsterWithoutCallingTheMissingItemsNeverObtained()
	{
		CompanionSnapshot snapshot = snapshotWith(10, 12);
		trimmer.fit(snapshot);
		assertEquals(SnapshotTrimmer.MAX_DROPS_PER_NPC, snapshot.loot.npcs.get(0).drops.size());
		assertEquals(Boolean.TRUE, snapshot.detailsTruncated);
		assertEquals(Boolean.FALSE, snapshot.loot.npcs.get(0).dropsComplete);
	}

	@Test
	public void getsAnOversizeSnapshotUnderTheCapAndSaysSo()
	{
		CompanionSnapshot snapshot = snapshotWith(40, 12);
		assertTrue("the fixture should not already fit",
				   trimmer.sizeOf(snapshot) > SnapshotTrimmer.BUDGET_BYTES);

		trimmer.fit(snapshot);

		assertTrue(trimmer.sizeOf(snapshot) <= SnapshotTrimmer.BUDGET_BYTES);
		assertEquals(Boolean.TRUE, snapshot.detailsTruncated);
	}

	@Test
	public void keepsTheMostKilledMonstersRatherThanWhicheverCameFirst()
	{
		/**
		 * The log arrives most-killed first, and the drought somebody actually cares about is on the
		 * monster they have fought ten thousand times — so the end of the list is what goes.
		 */
		CompanionSnapshot snapshot = snapshotWith(40, 12);
		trimmer.fit(snapshot);
		assertEquals("npc-0", snapshot.loot.npcs.get(0).npc);
	}

	@Test
	public void dropsTheOldestObservationsBeforeTheFreshOnes()
	{
		CompanionSnapshot snapshot = snapshotWith(0, 0);
		snapshot.farming = new CompanionSnapshot.Farming();
		snapshot.farming.progressVersion = 1;
		snapshot.farming.patches = new ArrayList<>();
		for (int i = 0; i < 24; i++)
		{
			CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
			patch.id = "patch-" + i;
			patch.state = PatchStates.GROWING;
			/** Ascending age, so the last rows are the ones seen longest ago. */
			patch.observedMsAgo = i * 60_000L;
			snapshot.farming.patches.add(patch);
		}
		/** Something bulky and unbounded that is not loot, so the patch stage is what has to run. */
		snapshot.questStates = new LinkedHashMap<>();
		for (int i = 0; i < 400; i++)
		{
			snapshot.questStates.put("A quest with quite a long name number " + i, "not_started");
		}

		trimmer.fit(snapshot);

		assertEquals(Boolean.TRUE, snapshot.detailsTruncated);
		if (snapshot.farming != null)
		{
			for (CompanionSnapshot.Patch kept : snapshot.farming.patches)
			{
				assertTrue("an older observation outlived a fresher one",
						   kept.observedMsAgo <= 12 * 60_000L);
			}
		}
	}

	@Test
	public void omitsAFullLogWholeBeforeDroppingFreshFarmObservations()
	{
		CompanionSnapshot snapshot = worstCase();
		snapshot.loot = null;
		trimmer.fit(snapshot);
		assertNull(snapshot.questLog);
		assertEquals(Boolean.TRUE, snapshot.detailsTruncated);
		assertTrue(trimmer.sizeOf(snapshot) <= SnapshotTrimmer.BUDGET_BYTES);
	}

	@Test
	public void fitsTheWorstSnapshotThisPluginCanBuild()
	{
		/**
		 * The claim in {@link SnapshotTrimmer}'s docblock — "between them they can always get the
		 * snapshot under the cap" — is only true while everything the trimmer <i>cannot</i> touch
		 * stays smaller than the budget. This is that, maximised: every category on at once, with
		 * the longest strings each one can carry, the map's own ceilings on the kill log, every
		 * patch in the catalog carrying a replant receipt, and a full batch of milestones.
		 *
		 * <p>If a future field pushes the untouchable part over the budget, this fails here rather
		 * than as a 413 in somebody's client — which is a refusal {@link RelayClient} treats as
		 * permanent, so the real symptom would be a dot that stops for good.
		 */
		CompanionSnapshot snapshot = worstCase();
		assertTrue("the fixture should not already fit",
				   trimmer.sizeOf(snapshot) > SnapshotTrimmer.BUDGET_BYTES);

		trimmer.fit(snapshot);

		assertTrue(
			"trimmed to " + trimmer.sizeOf(snapshot) + " bytes, over the " + SnapshotTrimmer.BUDGET_BYTES
			+ " budget — something the trimmer cannot touch has outgrown it",
			trimmer.sizeOf(snapshot) <= SnapshotTrimmer.BUDGET_BYTES);
		assertEquals(Boolean.TRUE, snapshot.detailsTruncated);
	}

	@Test
	public void aRealisticFullKillLogFitsWithoutTheOmissionNotice()
	{
		/**
		 * Every rated drop used to travel, obtained or not, and the forty rate-heaviest monsters in
		 * the bundled table came to 16,456 bytes of loot against a 15,872-byte budget for the whole
		 * snapshot. The readers with the most history were therefore the ones permanently told part
		 * of it was missing, and a notice that is always on stops meaning anything on the occasion
		 * it is true.
		 *
		 * <p>{@link LootLog} now sends only rows saying something the bundled rates do not already
		 * imply. This is a generous reading of what that leaves: forty monsters at the map's own
		 * ceiling, three obtained uniques on each, with names as long as real ones. It has to fit
		 * untouched. {@link #fitsTheWorstSnapshotThisPluginCanBuild} still covers the case where it
		 * does not, so this one is about the ordinary reader rather than the survivable extreme.
		 */
		CompanionSnapshot snapshot = snapshotWith(SnapshotTrimmer.MAX_LOOT_NPCS, 3);
		for (CompanionSnapshot.Npc npc : snapshot.loot.npcs)
		{
			npc.npc = pad(npc.npc, 24);
			for (int d = 0; d < npc.drops.size(); d++)
			{
				npc.drops.get(d).item = pad("Notable drop " + d, 24);
			}
		}

		int before = trimmer.sizeOf(snapshot);
		trimmer.fit(snapshot);

		assertNull("a realistic full kill log measured " + before + " bytes against a budget of "
				   + SnapshotTrimmer.BUDGET_BYTES + ", and was trimmed", snapshot.detailsTruncated);
		assertEquals(SnapshotTrimmer.MAX_LOOT_NPCS, snapshot.loot.npcs.size());
	}

	@Test
	public void staysUnderTheCapTheMapEnforces()
	{
		/**
		 * {@code MAX_SNAPSHOT_BYTES} in the map's {@code lib/companion-snapshot.ts}. The margin is
		 * not for measurement error — the measurement is the same string {@link RelayClient} posts
		 * — it is so that one added header or wrapper cannot turn a snapshot that just fitted into
		 * a permanent refusal.
		 */
		assertTrue(SnapshotTrimmer.BUDGET_BYTES < 16_384);
	}

	@Test
	public void keepsTheSamePatchesWhicheverOrderTheyArriveIn()
	{
		/**
		 * The rows that survive must be a function of the observations, not of the order they came
		 * out of a hash map. Every patch here is the same age — which is the ordinary case, since
		 * a farm run reads a region's patches on one tick — so with nothing else to sort on, the
		 * order in was the order kept, and two clients holding the same knowledge sent different
		 * halves of it. Worse, one client's own map reorders itself as it fills up, so the rows it
		 * dropped changed between two posts with nothing having happened.
		 */
		CompanionSnapshot forwards = patchesOfOneAge(FILLER_QUESTS);
		CompanionSnapshot backwards = patchesOfOneAge(FILLER_QUESTS);
		Collections.reverse(backwards.farming.patches);

		trimmer.fit(forwards);
		trimmer.fit(backwards);

		assertNotNull("the fixture should have left some patches to compare", forwards.farming);
		assertTrue("the fixture should have forced the patch stage to run",
				   forwards.farming.patches.size() < 24 && !forwards.farming.patches.isEmpty());
		assertEquals(survivingPatches(forwards), survivingPatches(backwards));
	}

	/**
	 * Every patch in the catalog, read on the same tick, with nothing else to trim.
	 *
	 * <p>Which is what a farm run actually looks like: the game reports a region's patches
	 * together, so the ages that decide what survives are equal far more often than not.
	 */
	private static CompanionSnapshot patchesOfOneAge(int filler)
	{
		CompanionSnapshot snapshot = worstCase();
		snapshot.questLog = null;
		snapshot.stashUnits = null;
		snapshot.travelUnlocks = null;
		snapshot.house = null;
		snapshot.hunterRumour = null;
		snapshot.mahoganyHome = null;
		snapshot.farmingContract = null;
		snapshot.birdhouses = null;
		/** No kill log, so the stage that would otherwise get under the cap first cannot. */
		snapshot.loot = null;
		/** Bulk the trimmer cannot touch, sized so the patch stage has to drop some but not all. */
		for (int i = 0; i < filler; i++)
		{
			snapshot.questStates.put("A quest with quite a long name number " + i, "not_started");
		}
		return snapshot;
	}

	private static List<String> survivingPatches(CompanionSnapshot snapshot)
	{
		List<String> ids = new ArrayList<>();
		if (snapshot.farming != null)
		{
			for (CompanionSnapshot.Patch patch : snapshot.farming.patches)
			{
				ids.add(patch.id);
			}
		}
		Collections.sort(ids);
		return ids;
	}

	/**
	 * Everything this plugin can send, all at once, at the longest each field can be.
	 *
	 * <p>The lengths are the real ones: 25 is the longest monster name in {@code unique-rates.json}
	 * and 30 the longest drop, 38 is "Desert Treasure II - The Fallen Empire", and the patch ids
	 * are the catalog's own.
	 */
	private static CompanionSnapshot worstCase()
	{
		CompanionSnapshot snapshot = snapshotWith(SnapshotTrimmer.MAX_LOOT_NPCS, 12);
		snapshot.member.characterName = "Abcdefghijkl";
		snapshot.member.nick = "Abcdefghijklmnopqrst";
		snapshot.accountType = "ultimate_ironman";
		snapshot.world = 302;
		snapshot.worldFlags = new ArrayList<>(Arrays.asList(
				"members", "pvp", "high_risk", "deadman", "league", "tournament", "beta", "speedrun"));
		snapshot.pos = new CompanionSnapshot.Tile(3200, 3200, 0);
		snapshot.instanced = Boolean.FALSE;
		snapshot.idleMs = 1_234_567L;

		snapshot.skills = new LinkedHashMap<>();
		for (int i = 0; i < 24; i++)
		{
			snapshot.skills.put("Skillnamenumber" + i, 99);
		}

		snapshot.questsCompleted = new ArrayList<>();
		snapshot.questStates = new LinkedHashMap<>();
		for (int i = 0; i < 49; i++)
		{
			String quest = pad("Quest number " + i, 38);
			snapshot.questsCompleted.add(quest);
			snapshot.questStates.put(quest, "not_started");
		}

		snapshot.questLog = new LinkedHashMap<>();
		for (net.runelite.api.Quest quest : net.runelite.api.Quest.values())
		{
			snapshot.questLog.put(quest.getName(), "not_started");
		}
		snapshot.stashUnits = new LinkedHashMap<>();
		for (net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit stash : net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit.values())
		{
			snapshot.stashUnits.put(stash.getObjectId(), false);
		}
		snapshot.travelUnlocks = new LinkedHashMap<>();
		for (int i = 0; i < 25; i++) snapshot.travelUnlocks.put(pad("unlock-" + i, 32), false);
		snapshot.house = "pollnivneach";
		snapshot.hunterRumour = AssignmentReader.assignment("Carnivorous Chinchompa", "Cervus");
		snapshot.mahoganyHome = AssignmentReader.assignment("Barbara", "Expert");
		snapshot.farmingContract = AssignmentReader.assignment("Poison ivy berries", "bush");
		snapshot.birdhouses = new ArrayList<>();
		for (String id : new String[] {"meadow-north", "meadow-south", "valley-north", "valley-south"})
		{
			CompanionSnapshot.Birdhouse house = new CompanionSnapshot.Birdhouse();
			house.id = id;
			house.state = "seeded";
			house.observedMsAgo = 21_600_000;
			snapshot.birdhouses.add(house);
		}

		snapshot.diaries = new LinkedHashMap<>();
		snapshot.diaryRewards = new LinkedHashMap<>();
		for (int i = 0; i < 12; i++)
		{
			String region = pad("Region name " + i, 19);
			snapshot.diaries.put(region, "unknown");
			snapshot.diaryRewards.put(region, "unknown");
		}

		snapshot.capabilities = new ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			snapshot.capabilities.add(pad("capabilityFlag" + i, 22));
		}

		snapshot.slayer = new CompanionSnapshot.Slayer();
		snapshot.slayer.taskName = pad("Task name", 25);
		snapshot.slayer.remaining = 142;
		snapshot.slayer.areaName = pad("Area name", 25);

		snapshot.clue = new CompanionSnapshot.Clue();
		snapshot.clue.tier = "master";
		snapshot.clue.itemId = 19_835;
		snapshot.clue.coordTile = new CompanionSnapshot.Tile(2200, 3200, 0);
		snapshot.clue.text = pad("A cryptic clue step as the interface printed it", 250);

		snapshot.farming = new CompanionSnapshot.Farming();
		snapshot.farming.progressVersion = 1;
		snapshot.farming.patches = new ArrayList<>();
		for (FarmingPatchCatalog.Patch known : FarmingPatchCatalog.PATCHES)
		{
			CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
			patch.id = known.id;
			patch.state = PatchStates.GROWING;
			patch.produce = pad("Produce name", 22);
			patch.stagesLeft = 3;
			/** Every patch the same age, so the tie-break is what decides which ones survive. */
			patch.observedMsAgo = 3_600_000L;
			patch.completionId = "b3f1c2d4-0000-4000-8000-000000000000";
			patch.completedMsAgo = 60_000L;
			snapshot.farming.patches.add(patch);
		}

		MilestoneOutbox outbox = new MilestoneOutbox();
		for (int i = 0; i < MilestoneOutbox.MAX_BATCH; i++)
		{
			outbox.reward(new RewardObservation("item", pad("Drop name " + i, 80), 12_922, 10_000_000,
												"activity", pad("Activity name " + i, 80), true, 1_000L), 1_000L);
		}
		snapshot.milestones = outbox.take(2_000L);

		snapshot.death = new CompanionSnapshot.Death();
		snapshot.death.tile = new CompanionSnapshot.Tile(3222, 3222, 0);
		snapshot.death.msAgo = 900_000L;

		snapshot.activeSkill = new CompanionSnapshot.ActiveSkill();
		snapshot.activeSkill.skill = "Runecraft";
		snapshot.activeSkill.xpGained5m = 40_000;
		snapshot.activeSkill.msSinceLastDrop = 4_000L;

		snapshot.detailsTruncated = null;
		snapshot.sentAtMs = 1_700_000_000_000L;
		return snapshot;
	}

	/** {@code text} padded out to exactly {@code length} characters. */
	private static String pad(String text, int length)
	{
		StringBuilder sb = new StringBuilder(text);
		while (sb.length() < length)
		{
			sb.append('x');
		}
		return sb.substring(0, length);
	}

	/** A kill log of {@code npcs} monsters carrying {@code drops} notable drops each. */
	private static CompanionSnapshot snapshotWith(int npcs, int drops)
	{
		CompanionSnapshot snapshot = new CompanionSnapshot();
		snapshot.member = new CompanionSnapshot.Member(
			"b3f1c2d4-0000-4000-8000-000000000000", "Someone");
		if (npcs == 0)
		{
			return snapshot;
		}
		snapshot.loot = new CompanionSnapshot.Loot();
		snapshot.loot.npcs = new ArrayList<>();
		for (int i = 0; i < npcs; i++)
		{
			CompanionSnapshot.Npc npc = new CompanionSnapshot.Npc();
			npc.npc = "npc-" + i;
			npc.kills = 10_000 - i;
			npc.drops = new ArrayList<>();
			for (int d = 0; d < drops; d++)
			{
				CompanionSnapshot.Drop drop = new CompanionSnapshot.Drop();
				drop.item = "A notable drop with a long name " + d;
				drop.obtained = 1;
				drop.killsAtLast = 900;
				/** Both travel on every row now, so a fixture without them under-measures the wire. */
				drop.trackedSinceKill = 0;
				npc.drops.add(drop);
			}
			npc.dropsComplete = Boolean.TRUE;
			snapshot.loot.npcs.add(npc);
		}
		return snapshot;
	}
}
