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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * The wire format, as two files both repositories read.
 *
 * <p>{@code snapshot.json} is what this plugin sends, produced here from the real
 * {@link CompanionSnapshot} with every field populated and serialized by Gson exactly as
 * {@link RelayClient} does. {@code reply.json} is what this plugin reads back, and it is parsed
 * here into the real {@link RelayResponse}. The map's {@code lib/plugin-contract.test.ts} reads
 * the same two files from this repository: it runs the snapshot through its normalizer and
 * asserts nothing is dropped, and it asserts its own reply carries every key the reply fixture
 * does. That replaces the map reading these {@code .java} files with regular expressions, which
 * broke on formatting and could not see a field it did not already know to look for.
 *
 * <p>Both checks here are about completeness, because an incomplete fixture proves nothing: a
 * field added to either class and left out of its fixture fails, naming the field. When the
 * snapshot's shape changes on purpose, regenerate its fixture with {@code CONTRACT_WRITE=1
 * ./gradlew test --tests '*WireContractTest*'} and commit it — the map's check then says whether
 * the map still reads everything, which is the point of having the file.
 */
public class WireContractTest
{
	private static final Path SNAPSHOT = Paths.get("src", "test", "resources", "contract", "snapshot.json");
	private static final Path REPLY = Paths.get("src", "test", "resources", "contract", "reply.json");
	/** A frame on the held stream with members asked for; the map's contract test reads it too. */
	private static final Path STREAM = Paths.get("src", "test", "resources", "contract", "stream.json");

	@Test
	public void theSnapshotFixtureIsWhatThisPluginSends() throws IOException
	{
		CompanionSnapshot snapshot = everyFieldSet();
		assertEveryFieldSet("CompanionSnapshot", snapshot);
		String sent = new Gson().toJson(snapshot);
		if ("1".equals(System.getenv("CONTRACT_WRITE")))
		{
			JsonElement tree = parse(sent);
			String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(tree);
			Files.createDirectories(SNAPSHOT.getParent());
			Files.write(SNAPSHOT, (pretty + "\n").getBytes(StandardCharsets.UTF_8));
		}
		String committed = new String(Files.readAllBytes(SNAPSHOT), StandardCharsets.UTF_8);
		assertEquals("the snapshot's shape changed; regenerate the fixture (see the class comment) and "
					 + "let the map's contract test say whether it still reads everything",
					 parse(committed), parse(sent));
	}

	@Test
	public void theReplyFixtureFillsEveryFieldThisPluginReads() throws IOException
	{
		String committed = new String(Files.readAllBytes(REPLY), StandardCharsets.UTF_8);
		RelayResponse reply = new Gson().fromJson(committed, RelayResponse.class);
		assertEveryFieldSet("RelayResponse", reply);
		assertTrue(reply.claimed);
		assertEquals("Sunday hunt", reply.groupName);
		assertEquals(Integer.valueOf(302), reply.directive.destination.world);
	}

	@Test
	public void theStreamFixtureFillsEveryFieldThisPluginReads() throws IOException
	{
		String committed = new String(Files.readAllBytes(STREAM), StandardCharsets.UTF_8);
		RelayResponse.StreamDirective frame = new Gson().fromJson(committed, RelayResponse.StreamDirective.class);
		assertEveryFieldSet("StreamDirective", frame);
		assertEquals("Big Rob", frame.members.get(0).name);
		assertEquals(Integer.valueOf(302), frame.members.get(0).world);
	}

	/**
	 * A snapshot with every field populated, using values the map's normalizer accepts — an
	 * unknown account type or patch state would be dropped on the map's side for the right
	 * reason and read as drift.
	 */
	static CompanionSnapshot everyFieldSet()
	{
		CompanionSnapshot s = new CompanionSnapshot();
		s.member = new CompanionSnapshot.Member("b3f1c2d4-0000-4000-8000-000000000000", "Zezima");
		s.member.characterName = "Zezima";
		s.accountType = "gim";
		s.world = 444;
		s.worldFlags = Arrays.asList("members");
		s.pos = tile(3185, 3436, 1);
		s.instanced = true;
		s.instanceLayout = "copy";
		Map<String, Integer> skills = new LinkedHashMap<>();
		skills.put("Mining", 72);
		skills.put("Agility", 61);
		skills.put("Total", 1650);
		s.skills = skills;
		s.questsCompleted = Arrays.asList("Lost City");
		Map<String, String> quests = new LinkedHashMap<>();
		quests.put("Lost City", "finished");
		quests.put("Fairytale II - Cure a Queen", "in_progress");
		s.questStates = quests;
		s.questLog = new LinkedHashMap<>(quests);
		s.questLog.put("Cook's Assistant", "finished");
		Map<String, String> diaries = new HashMap<>();
		diaries.put("Falador", "hard");
		s.diaries = diaries;
		s.travelUnlocks = new HashMap<>();
		s.travelUnlocks.put("ates-darkfrost", true);
		s.travelUnlocks.put("ates-ralos-rise", false);
		s.house = "rellekka";
		CompanionSnapshot.Birdhouse house = new CompanionSnapshot.Birdhouse();
		house.id = "meadow-north";
		house.state = "seeded";
		house.observedMsAgo = 120_000;
		s.birdhouses = Arrays.asList(house);
		s.hunterRumour = AssignmentReader.assignment("Red salamander", "Wolf");
		s.mahoganyHome = AssignmentReader.assignment("Jess", "Expert");
		s.farmingContract = AssignmentReader.assignment("Snapdragon", "herb");
		s.stashUnits = new HashMap<>();
		s.stashUnits.put(28960, true);
		Map<String, String> rewards = new HashMap<>();
		rewards.put("Varrock", "medium");
		s.diaryRewards = rewards;

		/**
		 * Built by the real outbox rather than by hand, so the fixture carries the kinds and the
		 * field sets the plugin actually emits — `npc_drop`, `reward` and `level` each fill a
		 * different subset of one class. Only the two random values, the event id and the
		 * session, are pinned — to fixed UUIDs, because the map refuses anything else there and
		 * `UUID.randomUUID()` is what the outbox really sends.
		 */
		MilestoneOutbox outbox = new MilestoneOutbox();
		outbox.npcDrop("Abyssal demon", "Abyssal whip", 4151, 1, 1_000L);
		outbox.reward(new RewardObservation("item", "Abyssal whip", 4151, 1, "npc", "Abyssal demon", true, 1_000L), 1_000L);
		outbox.levelUp("Mining", 72, 2_000L);
		List<CompanionSnapshot.Milestone> milestones = outbox.take(5_000L);
		for (CompanionSnapshot.Milestone m : milestones)
		{
			m.id = String.format("00000000-0000-4000-8000-%012d", m.sequence);
			m.session = "11111111-1111-4111-8111-111111111111";
		}
		s.milestones = milestones;
		s.detailsTruncated = true;
		s.capabilities = Arrays.asList("amuletOfGlory", "dramenStaff");

		s.slayer = new CompanionSnapshot.Slayer();
		s.slayer.taskName = "Kalphites";
		s.slayer.remaining = 43;
		s.slayer.areaName = "Kalphite Lair";

		s.clue = new CompanionSnapshot.Clue();
		s.clue.tier = "hard";
		s.clue.text = "Talk to the bartender.";
		s.clue.itemId = 2722;
		s.clue.coordTile = tile(3094, 3491, 0);
		s.clue.mapTile = tile(3167, 3360, 0);

		CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
		patch.id = "falador-herb";
		patch.state = "growing";
		patch.produce = "Ranarr weed";
		patch.stagesLeft = 4;
		patch.readyInMinMs = 60 * 60_000L;
		patch.readyInMaxMs = 80 * 60_000L;
		patch.observedMsAgo = 1_000L;
		patch.completionId = "replant-26";
		patch.completedMsAgo = 5_000L;
		s.farming = new CompanionSnapshot.Farming();
		s.farming.patches = Arrays.asList(patch);
		s.farming.progressVersion = 1;

		CompanionSnapshot.Drop whip = new CompanionSnapshot.Drop();
		whip.item = "Abyssal whip";
		whip.obtained = 1;
		whip.killsAtLast = 100;
		whip.trackedSinceKill = 3;
		whip.boostedSinceLast = 12;
		whip.boostedWatched = 40;
		CompanionSnapshot.Npc demon = new CompanionSnapshot.Npc();
		demon.npc = "Abyssal demon";
		demon.kills = 120;
		demon.drops = Arrays.asList(whip);
		demon.dropsComplete = true;
		demon.lastKillMsAgo = 90_000L;
		demon.ratedKey = "0a1b2c3d";
		s.loot = new CompanionSnapshot.Loot();
		s.loot.npcs = Arrays.asList(demon);
		s.loot.current = "Abyssal demon";
		s.loot.currentOnTask = true;
		s.loot.pinned = new CompanionSnapshot.Pin();
		s.loot.pinned.npc = "Abyssal demon";
		s.loot.pinned.item = "Abyssal whip";

		s.death = new CompanionSnapshot.Death();
		s.death.tile = tile(3222, 3218, 0);
		s.death.msAgo = 60_000L;

		s.activeSkill = new CompanionSnapshot.ActiveSkill();
		s.activeSkill.skill = "Mining";
		s.activeSkill.xpGained5m = 22_000;
		s.activeSkill.msSinceLastDrop = 1_500L;
		s.idleMs = 0L;
		s.sentAtMs = 1_800_000_000_000L;
		return s;
	}

	private static CompanionSnapshot.Tile tile(int x, int y, int plane)
	{
		return new CompanionSnapshot.Tile(x, y, plane);
	}

	/** The instance parser, because the client's Gson (2.8.5) predates the static one. */
	@SuppressWarnings("deprecation")
	private static JsonElement parse(String json)
	{
		return new JsonParser().parse(json);
	}

	/**
	 * Every public instance field of this plugin's own classes is non-null, walking into nested
	 * objects, lists and maps. For a list of one class, a field counts as set when any element
	 * sets it — which is how the two kinds of milestone share one class.
	 */
	private static void assertEveryFieldSet(String path, Object value)
	{
		List<String> missing = new ArrayList<>();
		collectUnset(path, value, missing);
		if (!missing.isEmpty())
		{
			fail("fields the fixture leaves unset, so the contract cannot vouch for them: " + missing);
		}
	}

	private static void collectUnset(String path, Object value, List<String> missing)
	{
		if (value == null)
		{
			missing.add(path);
			return;
		}
		if (value instanceof Map)
		{
			if (((Map<?, ?>) value).isEmpty())
			{
				missing.add(path + " (empty)");
			}
			return;
		}
		if (value instanceof Collection)
		{
			Collection<?> items = (Collection<?>) value;
			if (items.isEmpty())
			{
				missing.add(path + " (empty)");
				return;
			}
			Object first = items.iterator().next();
			if (!ours(first.getClass()))
			{
				return;
			}
			for (Field field : publicFields(first.getClass()))
			{
				boolean setSomewhere = false;
				for (Object item : items)
				{
					if (read(field, item) != null)
					{
						setSomewhere = true;
						List<String> nested = new ArrayList<>();
						collectUnset(path + "[]." + field.getName(), read(field, item), nested);
						missing.addAll(nested);
						break;
					}
				}
				if (!setSomewhere)
				{
					missing.add(path + "[]." + field.getName());
				}
			}
			return;
		}
		if (!ours(value.getClass()))
		{
			return;
		}
		for (Field field : publicFields(value.getClass()))
		{
			collectUnset(path + "." + field.getName(), read(field, value), missing);
		}
	}

	private static boolean ours(Class<?> type)
	{
		return type.getName().startsWith("com.pbolduc.mapcompanion.");
	}

	private static List<Field> publicFields(Class<?> type)
	{
		List<Field> out = new ArrayList<>();
		for (Field field : type.getFields())
		{
			if (!Modifier.isStatic(field.getModifiers()))
			{
				out.add(field);
			}
		}
		return out;
	}

	private static Object read(Field field, Object owner)
	{
		try
		{
			return field.get(owner);
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}
}
