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
 * <p>Two places draw these: Settings, and the step after a first Connect, which lists what one
 * press will share. Both draw a switch for the name, each group and the death, in that order, and
 * name every field of a group under <i>What each one sends</i>. Both read this list, so the step
 * cannot name a field differently from the switch that later turns it off, and a field cannot be
 * added to a group in one without the other. RuneLite's list names them the same way, which
 * {@code SharingGroupsTest} holds it to. The keys are the existing settings; nothing here is stored.
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
		/** A RuneLite notification setting, which a press writes as one; see {@link NotificationChoice}. */
		final boolean notification;

		private Field(String key, String label, Predicate<MapCompanionConfig> read, String detail, boolean notification)
		{
			this.key = key;
			this.label = label;
			this.detail = detail;
			this.read = read;
			this.notification = notification;
		}
	}

	/**
	 * A group of fields, and the switch RuneLite's list shows for it: {@link #key} is that switch's
	 * own stored setting, which {@link SharingGroups} keeps in step with the fields. The fields are
	 * what is sent; the switch is only what the list draws.
	 */
	static final class Section
	{
		final String id;
		final String title;
		final String key;
		final Predicate<MapCompanionConfig> read;
		final List<Field> fields;

		private Section(String id, String title, String key, Predicate<MapCompanionConfig> read, Field... fields)
		{
			this.id = id;
			this.title = title;
			this.key = key;
			this.read = read;
			this.fields = Collections.unmodifiableList(Arrays.asList(fields));
		}
	}

	private static Field field(String key, String label, Predicate<MapCompanionConfig> read, String detail)
	{
		return new Field(key, label, read, detail, false);
	}

	static final Section PRESENCE = new Section("presence", "Map presence", MapCompanionConfig.KEY_SHARE_MAP_PRESENCE,
		MapCompanionConfig::shareMapPresence,
		field("sharePosition", "Position and world", MapCompanionConfig::sharePosition,
			"Your tile, world and instance type. Shows your dot where its location is known."),
		field("shareActivity", "Training and idle time", MapCompanionConfig::shareActivity,
			"The skill last trained, recent experience and time since moving."),
		field("shareAccountType", "Account type", MapCompanionConfig::shareAccountType,
			"Your ironman or other account badge."));

	static final Section PROGRESS = new Section("progress", "Progress and travel", MapCompanionConfig.KEY_SHARE_PROGRESS,
		MapCompanionConfig::shareProgressAndTravel,
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
			"Built STASH units; stored equipment is never read."),
		field("shareBoats", "Boats and moorings", MapCompanionConfig::shareBoats,
			"Where each boat is moored and its teleport focus, so voyages start at your boat; boat names are never sent."));

	static final Section TASKS = new Section("tasks", "Tasks and timers", MapCompanionConfig.KEY_SHARE_TASKS,
		MapCompanionConfig::shareTasksAndTimers,
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
			"The crop requested by your current Farming Guild contract."),
		field("sharePortTasks", "Port tasks", MapCompanionConfig::sharePortTasks,
			"Your Sailing port task slots and their counters; cargo is never read."));

	static final Section LOOT = new Section("loot", "Loot and milestones", MapCompanionConfig.KEY_SHARE_LOOT_GROUP,
		MapCompanionConfig::shareLootAndMilestones,
		field("shareLoot", "Observed kills and drops", MapCompanionConfig::shareLoot,
			"Locally observed tracked monster kills and rated drop totals on normal members worlds; not lifetime kill counts."),
		field(MapCompanionConfig.KEY_SHARE_MILESTONES, "Notable drops and levels", MapCompanionConfig::shareMilestones,
			"Notable observed rewards and real levels, and quests and diary tiers finished while you play, "
			+ "if those are shared. Activity rewards require Loot Tracker. " + MapCompanionConfig.FEED_KEPT),
		field(MapCompanionConfig.KEY_SHARE_PETS, "Include pet rewards", MapCompanionConfig::sharePets,
			"Observed pet rewards while Notable drops and levels is enabled; unknown pets stay unnamed."),
		field(MapCompanionConfig.KEY_SHARE_COLLECTION, "Include collection log additions", MapCompanionConfig::shareCollectionLog,
			"New collection notifications while Notable drops and levels is enabled; existing slots are never read."));

	/** The four groups, in the order both Settings and the Connect step draw them. */
	static final List<Section> GROUPS = Collections.unmodifiableList(Arrays.asList(PRESENCE, PROGRESS, TASKS, LOOT));

	static final Field NAME = field(MapCompanionConfig.KEY_SHARE_NAME, "Character name", MapCompanionConfig::shareName,
		"The display name of the character you are logged in as.");
	static final Field DEATH = field("shareDeath", "Where you died", MapCompanionConfig::shareDeath,
		"The tile of your most recent death, forgotten after an hour.");

	/*
	 * What this client draws or does. The first two open the receive stream, and Route here asks the map
	 * on a press, as every Route does; the rest stay here.
	 */
	static final Field DESTINATIONS = field("showDestination", "Show map destinations", MapCompanionConfig::showDestination,
		"Receive destinations and meetup details. Nothing is clicked or walked for you.");
	static final Field GROUP_ON_WORLD_MAP = field("showGroupOnWorldMap", "Group on the world map", MapCompanionConfig::showGroupOnWorldMap,
		"Receive the positions your group already shares and draw them on the world map.");
	static final Field HINT_ARROW = field("destinationHintArrow", "Hint arrow to a destination", MapCompanionConfig::destinationHintArrow,
		"Point the game's own arrow at a destination once it is nearby. Nothing leaves this computer.");
	static final Field STEP_ON_SCREEN = field("showStepOnScreen", "Step on screen", MapCompanionConfig::showStepOnScreen,
		"While you follow a route, the step you are on in a small box over the game. Nothing leaves this computer.");
	static final Field SHORTEST_PATH = field(MapCompanionConfig.KEY_SHORTEST_PATH, "Draw paths with Shortest Path", MapCompanionConfig::drawWithShortestPath,
		"When you follow a route, the Shortest Path plugin draws each walking step. Nothing leaves this computer.");
	static final Field ROUTE_HERE = field("routeHereOnWorldMap", "Route here on the world map", MapCompanionConfig::routeHereOnWorldMap,
		"Right-click the world map for the way to that spot. A press sends that spot and where you stand, as Route does.");
	static final Field NOTIFY_MEETUPS = new Field("notifyMeetups", "Meetup notifications", config -> config.notifyMeetups().isEnabled(),
		"A RuneLite notification before a meetup starts. Ticked here, it comes while you play too; how it arrives follows RuneLite's own notification settings.",
		true);
	static final Field DROP_LUCK_CHAT = field("dropLuckChat", "Drop luck in chat", MapCompanionConfig::dropLuckChat,
		"One chat line when a drop with a known rate lands. Nothing leaves this computer.");
	static final Field BANK_MARKS = field("markFarmRunInBank", "Farm run teleports in the bank", MapCompanionConfig::markFarmRunInBank,
		"Outline in the bank the teleports a farm run needs that you are not carrying. Nothing leaves this computer.");

	static final List<Field> CLIENT = Collections.unmodifiableList(Arrays.asList(
		DESTINATIONS, GROUP_ON_WORLD_MAP, HINT_ARROW, STEP_ON_SCREEN, SHORTEST_PATH, ROUTE_HERE, NOTIFY_MEETUPS, DROP_LUCK_CHAT,
		BANK_MARKS));
}
