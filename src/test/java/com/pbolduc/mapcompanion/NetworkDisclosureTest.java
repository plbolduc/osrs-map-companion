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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The README says what this plugin sends, and it says all of it.
 *
 * <p><b>Why this is the test the disclosure needs.</b> The Plugin Hub's one rule for a plugin that
 * talks to a third-party server is that it explains what it sends, and the explanation is prose
 * a hundred lines away from the code that does the sending. Prose does not fail to compile. A
 * field added to {@link SnapshotBuilder} in one commit and a paragraph updated in another is a
 * disclosure that is wrong in between, and nothing in a green build has an opinion about it —
 * which is exactly how this repository's storage paragraph came to describe a server that no
 * longer existed.
 *
 * <p>So the builder is read as text and every field it sets has to be accounted for here: either
 * with the phrase the README uses for it, which must actually be in the README, or as plumbing
 * with a reason. A new field fails this test until somebody decides which of those it is, and
 * that decision is the disclosure.
 *
 * <p>It matches against the README with its line breaks and blockquote markers flattened, because
 * where a sentence happens to wrap is not something a test should have an opinion about.
 */
public class NetworkDisclosureTest
{
	/** Every field the builder sets, and the words the README uses to tell a reader about it. */
	private static final Map<String, String> DISCLOSED = new LinkedHashMap<>();

	/**
	 * The fields with nothing to disclose, and why each one is not a category.
	 *
	 * <p>Listed rather than skipped by a rule, so that "there is nothing to say about this one" is
	 * a decision somebody wrote down instead of a gap in a regular expression.
	 */
	private static final Map<String, String> PLUMBING = new LinkedHashMap<>();

	static
	{
		DISCLOSED.put("member.characterName",
					  "the display name of the character you are logged in as");
		DISCLOSED.put("accountType", "your account type");
		DISCLOSED.put("idleMs", "how long since you last moved");
		DISCLOSED.put("activeSkill", "which skill you are currently training");
		DISCLOSED.put("world", "your world");
		DISCLOSED.put("worldFlags", "what kind of world it is");
		DISCLOSED.put("instanced", "whether you are inside an instance");
		DISCLOSED.put("instanceLayout", "whether that instance is one place or rooms put together");
		DISCLOSED.put("pos", "your position");
		DISCLOSED.put("skills", "skill levels");
		DISCLOSED.put("questsCompleted", "the 49 quests that unlock travel");
		DISCLOSED.put("questLog", "the full quest and miniquest log");
		DISCLOSED.put("questStates", "started, finished, or neither");
		DISCLOSED.put("travelUnlocks", "verified permanent transport unlocks");
		DISCLOSED.put("house", "your house town");
		DISCLOSED.put("diaries", "which achievement diary tier you have finished");
		DISCLOSED.put("diaryRewards", "which of those rewards you have collected");
		DISCLOSED.put("capabilities", "derived travel unlocks");
		DISCLOSED.put("slayer", "your current slayer task");
		DISCLOSED.put("stashUnits", "which STASH units you have built");
		DISCLOSED.put("clue", "your active clue step");
		DISCLOSED.put("hunterRumour", "your observed hunter rumour");
		DISCLOSED.put("mahoganyHome", "your Mahogany Homes customer and tier");
		DISCLOSED.put("farmingContract", "the crop requested by your farming contract");
		DISCLOSED.put("birdhouses", "birdhouse states and observation ages");
		DISCLOSED.put("farming", "the state of farming patches you have walked past");
		DISCLOSED.put("loot", "observed loot kills for every monster, how long ago each was last killed");
		DISCLOSED.put("milestones", "an announcement of a notable reward or a real level gained");
		DISCLOSED.put("death", "the tile where you most recently died");
		/** Set by {@link SnapshotTrimmer} rather than the builder, and disclosed all the same. */
		DISCLOSED.put("detailsTruncated", "a flag saying some of it was left out");

		PLUMBING.put("member", "the container; its own fields are disclosed one at a time");
		PLUMBING.put("sentAtMs", "the sender's clock, which the map replaces with its own");
	}

	@Test
	public void tellsAReaderAboutEveryFieldTheBuilderSets()
	{
		Set<String> set = fieldsSetBySnapshotBuilder();
		List<String> undisclosed = new ArrayList<>();
		for (String field : set)
		{
			if (!DISCLOSED.containsKey(field) && !PLUMBING.containsKey(field))
			{
				undisclosed.add(field);
			}
		}
		assertEquals(
			"SnapshotBuilder sets fields this test has never been told what the README says about: "
			+ undisclosed + ". Add the phrase to DISCLOSED and to the README, or say in "
			+ "PLUMBING why it is not a category a reader would want named.",
			new ArrayList<String>(),
			undisclosed);
	}

	@Test
	public void saysAllOfItInTheReadme()
	{
		String readme = flattened(read("README.md"));
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, String> entry : DISCLOSED.entrySet())
		{
			if (!readme.contains(entry.getValue()))
			{
				missing.add(entry.getKey() + " (\"" + entry.getValue() + "\")");
			}
		}
		assertEquals(
			"the README's network disclosure no longer says these: " + missing,
			new ArrayList<String>(),
			missing);
	}

	@Test
	public void doesNotClaimToSendFieldsItHasStoppedSending()
	{
		/**
		 * The other direction, and the one the plan found wrong four times over: a disclosure that
		 * describes something the code no longer does. A field named here but no longer set by the
		 * builder means the paragraph is now over-stating what leaves the client.
		 */
		Set<String> set = fieldsSetBySnapshotBuilder();
		List<String> stale = new ArrayList<>();
		for (String field : DISCLOSED.keySet())
		{
			if (!set.contains(field) && !"detailsTruncated".equals(field))
			{
				stale.add(field);
			}
		}
		assertEquals("the README describes fields SnapshotBuilder no longer sets: " + stale,
					 new ArrayList<String>(), stale);
	}

	@Test
	public void theTrimmerIsStillWhatSetsTheOneFieldTheBuilderDoesNot()
	{
		assertTrue("detailsTruncated is disclosed as something the plugin sends; if it has moved, "
				   + "this test's exception for it has to move too",
				   read("SnapshotTrimmer.java").contains("snapshot.detailsTruncated = true;"));
	}

	/**
	 * The shape of the wire itself, because the builder is no longer the whole story.
	 *
	 * <p>Every test above reads {@link SnapshotBuilder}'s own assignments, which was the entire
	 * protocol while the builder set all of it. It is not any more. A drop's tracking baseline, a
	 * monster's completeness flag and a reward's type and source are assigned in {@link LootLog} and
	 * {@link MilestoneOutbox}, so the largest expansion of nested content since this file was
	 * written went straight past every test in it. None of them failed. None of them could see it,
	 * and a test that passes because it is looking somewhere else is worse than no test, because it
	 * is read as a guarantee.
	 *
	 * <p>This one cannot be blinded the same way: it asks the wire classes what they are, rather
	 * than asking one file what it writes. It has no opinion about the prose — the tests above hold
	 * the top level to the README's actual words. What it does is make adding a field to the
	 * protocol a deliberate line in a diff, sitting next to the paragraph that has to answer for it.
	 */
	@Test
	public void everyFieldOnTheWireIsAccountedFor()
	{
		Map<String, String> expected = new TreeMap<>();
		expected.put("ActiveSkill", "msSinceLastDrop, skill, xpGained5m");
		expected.put("Assignment", "detail, name, observedMsAgo, state");
		expected.put("Birdhouse", "id, observedMsAgo, state");
		expected.put("Clue", "coordTile, itemId, mapTile, text, tier");
		expected.put("CompanionSnapshot", "accountType, activeSkill, birdhouses, capabilities, clue, death, "
					 + "detailsTruncated, diaries, diaryRewards, farming, farmingContract, house, hunterRumour, idleMs, instanceLayout, instanced, loot, mahoganyHome, member, "
					 + "milestones, pos, questLog, questStates, questsCompleted, sentAtMs, skills, slayer, stashUnits, travelUnlocks, v, world, worldFlags");
		expected.put("Death", "msAgo, tile");
		expected.put("Drop", "boostedSinceLast, boostedWatched, item, killsAtLast, obtained, trackedSinceKill");
		expected.put("Farming", "patches, progressVersion");
		expected.put("Loot", "current, currentOnTask, npcs, pinned");
		expected.put("Member", "characterName, id, nick");
		expected.put("Milestone", "collectionNew, id, item, itemId, kind, level, msAgo, npc, quantity, "
					 + "rewardType, sequence, session, skill, source");
		expected.put("Npc", "drops, dropsComplete, kills, lastKillMsAgo, npc, ratedKey");
		expected.put("Pin", "item, npc");
		expected.put("Patch", "completedMsAgo, completionId, id, observedMsAgo, produce, readyInMaxMs, readyInMinMs, stagesLeft, state");
		expected.put("RewardSource", "kind, name");
		expected.put("Slayer", "areaName, remaining, taskName");
		expected.put("Tile", "plane, x, y");

		assertEquals("the shape of the wire changed. Every field here leaves the player's machine, so "
					 + "the README's disclosure has to answer for the new one in this same commit — then pin "
					 + "the new shape here.", expected, wireShape());
	}

	/** Each wire class and its instance fields, sorted, read from the classes rather than a writer. */
	private static Map<String, String> wireShape()
	{
		List<Class<?>> types = new ArrayList<>();
		types.add(CompanionSnapshot.class);
		types.addAll(Arrays.asList(CompanionSnapshot.class.getDeclaredClasses()));
		Map<String, String> shape = new TreeMap<>();
		for (Class<?> type : types)
		{
			Set<String> names = new TreeSet<>();
			for (Field field : type.getDeclaredFields())
			{
				if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()))
				{
					continue;
				}
				names.add(field.getName());
			}
			if (!names.isEmpty())
			{
				shape.put(type.getSimpleName(), String.join(", ", names));
			}
		}
		return shape;
	}

	/**
	 * The fields {@link SnapshotBuilder} assigns, read out of its source.
	 *
	 * <p>Its source rather than the wire class, because {@code CompanionSnapshot} is the shape of
	 * the protocol and this is a question about what is actually put in it — a field that exists
	 * and is never populated is nothing a reader needs told about.
	 */
	private static Set<String> fieldsSetBySnapshotBuilder()
	{
		Set<String> fields = new TreeSet<>();
		Matcher matcher = Pattern
						  .compile("\\bsnapshot\\.((?:member\\.)?[A-Za-z][A-Za-z0-9]*)\\s*=[^=]")
						  .matcher(read("SnapshotBuilder.java"));
		while (matcher.find())
		{
			fields.add(matcher.group(1));
		}
		assertTrue("nothing matched — the builder's shape has changed and so must this pattern",
				   fields.size() > 10);
		return fields;
	}

	/** Blockquote markers and line breaks flattened, so a rewrapped paragraph is the same text. */
	private static String flattened(String markdown)
	{
		StringBuilder sb = new StringBuilder();
		for (String line : markdown.split("\n"))
		{
			sb.append(line.startsWith("> ") ? line.substring(2) : line).append(' ');
		}
		return sb.toString().replaceAll("\\s+", " ");
	}

	private static String read(String name)
	{
		String path = name.endsWith(".java")
					  ? "src/main/java/com/pbolduc/mapcompanion/" + name
					  : name;
		try
		{
			return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new AssertionError("could not read " + path + " — this test runs from the "
									 + "project directory, which is where Gradle puts it", e);
		}
	}
}
