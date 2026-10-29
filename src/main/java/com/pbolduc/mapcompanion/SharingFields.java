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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Every sharing choice the sidebar offers, by its setting key and in the words the sidebar uses.
 *
 * <p>Two places name these: the Sharing tab, one checkbox each, and the step after a first
 * Connect, which lists what one press will share. Both read this list, so the step cannot name a
 * field differently from the checkbox that later turns it off, and a field cannot be added to a
 * group in one without the other. The keys are the existing settings; nothing here is stored.
 */
final class SharingFields
{
	private SharingFields()
	{
	}

	/** One setting: its key, the sidebar's label for it, and the line that says what it sends. */
	static final class Field
	{
		final String key;
		final String label;
		final String detail;
		final Predicate<MapCompanionConfig> read;

		private Field(String key, String label, Predicate<MapCompanionConfig> read, String detail)
		{
			this.key = key;
			this.label = label;
			this.detail = detail;
			this.read = read;
		}
	}

	/** A group of fields with one shortcut, as the Sharing tab draws it. */
	static final class Section
	{
		final String id;
		final String title;
		final List<Field> fields;

		private Section(String id, String title, Field... fields)
		{
			this.id = id;
			this.title = title;
			this.fields = Collections.unmodifiableList(Arrays.asList(fields));
		}
	}

	private static Field field(String key, String label, Predicate<MapCompanionConfig> read, String detail)
	{
		return new Field(key, label, read, detail);
	}

	static final Section PRESENCE = new Section("presence", "Map presence",
		field("sharePosition", "Position and world", MapCompanionConfig::sharePosition,
			"Your tile, world and instance type. Shows your dot where its location is known."),
		field("shareActivity", "Training and idle time", MapCompanionConfig::shareActivity,
			"The skill last trained, recent experience and time since moving."),
		field("shareAccountType", "Account type", MapCompanionConfig::shareAccountType,
			"Your ironman or other account badge."));

	static final Section PROGRESS = new Section("progress", "Progress and travel",
		field("shareSkills", "Skill levels", MapCompanionConfig::shareSkills, "Your levels, for usable routes."),
		field("shareQuests", "Travel quest completion", MapCompanionConfig::shareQuests,
			"Progress in the quests that unlock travel; no individual steps."),
		field("shareQuestLog", "Full quest progress", MapCompanionConfig::shareQuestLog,
			"Started or finished states for the full quest and miniquest log; no individual steps."),
		field("shareDiaries", "Achievement diaries", MapCompanionConfig::shareDiaries,
			"Completed tiers and collected rewards; no individual tasks."),
		field("sharePermanentTravel", "Permanent travel and house", MapCompanionConfig::sharePermanentTravel,
			"Verified permanent travel unlocks and the town your house is in."),
		field("shareCapabilities", "Carried travel items", MapCompanionConfig::shareCapabilities,
			"Derived yes/no travel flags; your inventory is never sent."),
		field("shareStash", "STASH construction", MapCompanionConfig::shareStash,
			"Built STASH units; stored equipment is never read."));

	static final Section TASKS = new Section("tasks", "Tasks and timers",
		field("shareSlayer", "Slayer task", MapCompanionConfig::shareSlayer, "Your current assignment and remaining count."),
		field("shareClue", "Clue step", MapCompanionConfig::shareClue, "Your current clue scroll step."),
		field("shareFarming", "Farming patches", MapCompanionConfig::shareFarming,
			"Patch states you have observed, for planning runs."),
		field("shareBirdhouses", "Birdhouses", MapCompanionConfig::shareBirdhouses,
			"States observed by RuneLite Time Tracking, with their age."),
		field("shareHunterRumours", "Hunter rumours", MapCompanionConfig::shareHunterRumours,
			"The rumour observed after checking a quetzal whistle; recheck after login or hopping."),
		field("shareMahoganyHomes", "Mahogany Homes", MapCompanionConfig::shareMahoganyHomes,
			"The customer and tier observed in dialogue or game messages."),
		field("shareFarmingContracts", "Farming contracts", MapCompanionConfig::shareFarmingContracts,
			"The crop requested by your current Farming Guild contract."));

	static final Section LOOT = new Section("loot", "Loot and milestones",
		field("shareLoot", "Observed kills and drops", MapCompanionConfig::shareLoot,
			"Locally observed tracked monster kills and rated drop totals on normal members worlds; not lifetime kill counts."),
		field(MapCompanionConfig.KEY_SHARE_MILESTONES, "Notable drops and levels", MapCompanionConfig::shareMilestones,
			"Notable observed rewards and real levels. Activity rewards require Loot Tracker. " + MapCompanionConfig.FEED_KEPT),
		field(MapCompanionConfig.KEY_SHARE_PETS, "Include pet rewards", MapCompanionConfig::sharePets,
			"Observed pet rewards while Notable drops and levels is enabled; unknown pets stay unnamed."),
		field(MapCompanionConfig.KEY_SHARE_COLLECTION, "Include collection additions", MapCompanionConfig::shareCollectionLog,
			"New collection notifications while Notable drops and levels is enabled; existing slots are never read."));

	/** The four groups, in the order both the Sharing tab and the Connect step draw them. */
	static final List<Section> GROUPS = Collections.unmodifiableList(Arrays.asList(PRESENCE, PROGRESS, TASKS, LOOT));

	static final Field NAME = field(MapCompanionConfig.KEY_SHARE_NAME, "Character name", MapCompanionConfig::shareName,
		"The display name of the character you are logged in as.");
	static final Field DEATH = field("shareDeath", "Where you died", MapCompanionConfig::shareDeath,
		"The tile of your most recent death, forgotten after an hour.");

	/* What this client draws or does. The first two open the receive stream; the rest stay here. */
	static final Field DESTINATIONS = field("showDestination", "Show map destinations", MapCompanionConfig::showDestination,
		"Receive destinations and meetup details. Nothing is clicked or walked for you.");
	static final Field GROUP_ON_WORLD_MAP = field("showGroupOnWorldMap", "Group on the world map", MapCompanionConfig::showGroupOnWorldMap,
		"Receive the positions your group already shares and draw them on the world map.");
	static final Field HINT_ARROW = field("destinationHintArrow", "Hint arrow to a destination", MapCompanionConfig::destinationHintArrow,
		"Point the game's own arrow at a destination once it is nearby. Nothing leaves this computer.");
	static final Field SHORTEST_PATH = field(MapCompanionConfig.KEY_SHORTEST_PATH, "Draw paths with Shortest Path", MapCompanionConfig::drawWithShortestPath,
		"When you follow a route, the Shortest Path plugin draws each walking step. Nothing leaves this computer.");
	static final Field NOTIFY_MEETUPS = field("notifyMeetups", "Meetup notifications", MapCompanionConfig::notifyMeetups,
		"Receive a notification before a meetup starts.");
	static final Field DROP_LUCK_CHAT = field("dropLuckChat", "Drop luck in chat", MapCompanionConfig::dropLuckChat,
		"One chat line when a drop with a known rate lands. Nothing leaves this computer.");

	static final List<Field> CLIENT = Collections.unmodifiableList(Arrays.asList(
		DESTINATIONS, GROUP_ON_WORLD_MAP, HINT_ARROW, SHORTEST_PATH, NOTIFY_MEETUPS, DROP_LUCK_CHAT));
}
