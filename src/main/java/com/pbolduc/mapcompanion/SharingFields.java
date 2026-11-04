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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Every sharing choice the sidebar offers, by its setting key and in the words the sidebar uses.
 *
 * <p>Two places draw these: Settings, and the step after a first Connect, which lists what one
 * press will share. Both draw a switch for the name, each group and the death, in that order, and
 * name every field of a group under <i>What each one sends</i>. Both read this list, so the step
 * cannot name a field differently from the switch that later turns it off, and a field cannot be
 * added to a group in one without the other. RuneLite's list names them the same way, which
 * {@code SharingGroupsTest} holds it to. The keys are the existing settings; nothing here is stored.
 * The fields under the groups but the position are read by key, as {@link MapCompanionConfig} says why.
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
		/** The config's own getter, or null for a field RuneLite does not declare, which is read by key. */
		@Nullable
		private final Predicate<MapCompanionConfig> read;
		/** A RuneLite notification setting, which a press writes as one; see {@link NotificationChoice}. */
		final boolean notification;

		private Field(String key, @Nullable Predicate<MapCompanionConfig> read, boolean notification)
		{
			this.key = key;
			this.label = WORDS.get(key)[1];
			this.detail = WORDS.get(key)[2];
			this.read = read;
			this.notification = notification;
		}

		/** Whether it is on now. A getter and a key read the same stored value; see {@link MapCompanionConfig}. */
		boolean on(MapCompanionConfig config, ConfigStore store)
		{
			return read != null ? read.test(config) : store.on(key);
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

		private Section(String id, String key, Predicate<MapCompanionConfig> read, Field... fields)
		{
			this.id = id;
			this.title = WORDS.get(key)[1];
			this.key = key;
			this.read = read;
			this.fields = Collections.unmodifiableList(Arrays.asList(fields));
		}
	}

	/**
	 * Every title, label and line here, by setting key, from sharing-fields.txt. Words a player
	 * reads, kept out of the source because the Plugin Hub counts every character of it when it
	 * decides whether an update can be reviewed automatically. {FEED_KEPT} in a line is
	 * {@link MapCompanionConfig#FEED_KEPT}, so that sentence is still written once.
	 */
	private static final Map<String, String[]> WORDS = new HashMap<>();

	static
	{
		for (String line : Resources.lines("/sharing-fields.txt"))
		{
			String[] row = line.replace("{FEED_KEPT}", MapCompanionConfig.FEED_KEPT).split("\\|", 3);
			WORDS.put(row[0], row);
		}
	}

	private static Field field(String key, Predicate<MapCompanionConfig> read)
	{
		return new Field(key, read, false);
	}

	/** A field RuneLite does not declare, read by its key. */
	private static Field field(String key)
	{
		return new Field(key, null, false);
	}

	static final Section PRESENCE = new Section("presence", MapCompanionConfig.KEY_SHARE_MAP_PRESENCE,
		MapCompanionConfig::shareMapPresence,
		field("sharePosition", MapCompanionConfig::sharePosition), field("shareActivity"), field("shareAccountType"));

	static final Section PROGRESS = new Section("progress", MapCompanionConfig.KEY_SHARE_PROGRESS,
		MapCompanionConfig::shareProgressAndTravel,
		field("shareSkills"), field("shareQuests"), field("shareQuestLog"), field("shareDiaries"), field("sharePermanentTravel"),
		field("shareCapabilities"), field("shareStash"), field("shareBoats"));

	static final Section TASKS = new Section("tasks", MapCompanionConfig.KEY_SHARE_TASKS,
		MapCompanionConfig::shareTasksAndTimers,
		field("shareSlayer"), field("shareClue"), field("shareFarming"), field("shareBirdhouses"), field("shareHunterRumours"),
		field("shareMahoganyHomes"), field("shareFarmingContracts"), field("sharePortTasks"));

	static final Section LOOT = new Section("loot", MapCompanionConfig.KEY_SHARE_LOOT_GROUP,
		MapCompanionConfig::shareLootAndMilestones,
		field("shareLoot"), field(MapCompanionConfig.KEY_SHARE_MILESTONES), field(MapCompanionConfig.KEY_SHARE_PETS),
		field(MapCompanionConfig.KEY_SHARE_COLLECTION));

	/** The four groups, in the order both Settings and the Connect step draw them. */
	static final List<Section> GROUPS = Collections.unmodifiableList(Arrays.asList(PRESENCE, PROGRESS, TASKS, LOOT));

	static final Field NAME = field(MapCompanionConfig.KEY_SHARE_NAME, MapCompanionConfig::shareName);
	static final Field DEATH = field("shareDeath", MapCompanionConfig::shareDeath);

	/*
	 * What this client draws or does. The first two open the receive stream, and Route here asks the map
	 * on a press, as every Route does; the rest stay here.
	 */
	static final Field DESTINATIONS = field("showDestination", MapCompanionConfig::showDestination);
	static final Field GROUP_ON_WORLD_MAP = field("showGroupOnWorldMap", MapCompanionConfig::showGroupOnWorldMap);
	static final Field HINT_ARROW = field("destinationHintArrow", MapCompanionConfig::destinationHintArrow);
	static final Field STEP_ON_SCREEN = field("showStepOnScreen", MapCompanionConfig::showStepOnScreen);
	static final Field STEP_TELEPORT = field("outlineStepTeleport", MapCompanionConfig::outlineStepTeleport);
	static final Field SHORTEST_PATH = field(MapCompanionConfig.KEY_SHORTEST_PATH, MapCompanionConfig::drawWithShortestPath);
	static final Field ROUTE_HERE = field("routeHereOnWorldMap", MapCompanionConfig::routeHereOnWorldMap);
	static final Field NOTIFY_MEETUPS = new Field("notifyMeetups", config -> config.notifyMeetups().isEnabled(), true);
	static final Field DROP_LUCK_CHAT = field("dropLuckChat", MapCompanionConfig::dropLuckChat);
	static final Field BANK_MARKS = field("markFarmRunInBank", MapCompanionConfig::markFarmRunInBank);

	static final List<Field> CLIENT = Collections.unmodifiableList(Arrays.asList(
		DESTINATIONS, GROUP_ON_WORLD_MAP, HINT_ARROW, STEP_ON_SCREEN, STEP_TELEPORT, SHORTEST_PATH, ROUTE_HERE, NOTIFY_MEETUPS,
		DROP_LUCK_CHAT, BANK_MARKS));
}
