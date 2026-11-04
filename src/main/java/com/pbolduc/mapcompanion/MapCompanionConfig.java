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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * The plugin's settings and the player's choices about sharing.
 *
 * <p>An empty pairing code opens no connection. Joining chooses a group; it does not choose
 * which character data that group may see. Each category starts off, so enabling it is a separate
 * decision.
 *
 * <p><b>Why no setting carries RuneLite's {@code warning}.</b> RuneLite asks a plugin that talks to
 * a server for a warning "either on the plugin, or on the configuration option", and this plugin's
 * is on the plugin: the Hub manifest's {@code warning=} line, which RuneLite shows when somebody
 * presses Install (`docs/plugin-hub-submission.md`). RuneLite turns a setting's warning into an
 * "Are you sure?" on every change, which would ask that one decision again on every setting, so
 * the sidebar's checkboxes and RuneLite's config panel write these settings the same way, with
 * nothing in between.
 *
 * <p>A nickname can label the member without naming an account. Saved choices keep their existing
 * keys so an update does not reset them.
 *
 * <p><b>Six switches, and the fields under them.</b> RuneLite's list shows sharing as the sidebar
 * does: the character name and where you died, and a switch for each of the four groups in
 * {@link SharingFields}. The 23 fields under those groups are still settings of their own, under the
 * keys they have always had, and still what {@link SnapshotBuilder} and the readers read; a group's
 * switch is stored only because RuneLite draws a box from a stored value. {@link SharingGroups}
 * keeps each switch in step with its fields, and says which wins when they disagree.
 *
 * <p><b>Why only one field is declared here.</b> RuneLite's list never draws the fields, so a
 * declaration bought them nothing but a getter, at the length of a setting, for each of 23, and the
 * Plugin Hub counts every character of source when it decides whether an update can be reviewed
 * automatically. They are read by key ({@link ConfigStore#on}), what each sends is said in
 * {@code sharing-fields.txt} and the README's <i>What you share</i>, and Reset clears them in
 * {@link MapCompanionPlugin#resetConfiguration}, since RuneLite resets only what is declared.
 * {@link #sharePosition} stays, because the map's contract test reads its gate in the builder.
 *
 * <p>Receiving destinations opens the relay stream and is also opt-in. The client section
 * keeps that choice beside the local notification controls.
 */
@ConfigGroup(MapCompanionConfig.GROUP)
public interface MapCompanionConfig extends Config
{
	String GROUP = "mapcompanion";
	String DEFAULT_MAP_URL = "https://osrs-map.com";

	/**
	 * What the map keeps of a drop or a level, said beside the choice that sends one. A group made
	 * on the map keeps two weeks of its members' notable drops and levels unless its owner turns
	 * that off, so the choice and the step after Connect say so rather than leave it to the
	 * privacy page.
	 */
	String FEED_KEPT = "Your group keeps the last two weeks.";

	/**
	 * Connection and sharing keys that must be recognised by configuration listeners.
	 *
	 * <p>Named constants because {@link MapCompanionPlugin} has to recognise them in a
	 * {@code ConfigChanged} event, and a string typed twice is a string that can be typed wrong
	 * once. The failure would be silent — a connection that stops resetting when the code changes.
	 *
	 * <p>{@code KEY_PAIRING_SUFFIX} is not a third address: it is the half of a RuneLite code that
	 * says <i>which browser</i> this plugin belongs to, sent as a header until the map answers that
	 * it has been claimed. {@link Pairing} owns its lifecycle.
	 */
	String KEY_PAIRING_CODE = "pairingCode";
	String KEY_RELAY_URL = "relayUrl";
	String KEY_PAIRING_SUFFIX = "pairingSuffix";
	String KEY_SHARE_NAME = "shareName";
	String KEY_SHARE_MILESTONES = "shareMilestones";
	String KEY_SHARE_PETS = "sharePets";
	String KEY_SHARE_COLLECTION = "shareCollectionLog";
	String KEY_SHORTEST_PATH = "drawWithShortestPath";

	/**
	 * Each sharing group's switch, as RuneLite's list stores it. Written into profiles, so a wire
	 * format of their own: never renamed. {@link SharingFields} pairs each with its group.
	 */
	String KEY_SHARE_MAP_PRESENCE = "shareMapPresence";
	String KEY_SHARE_PROGRESS = "shareProgressAndTravel";
	String KEY_SHARE_TASKS = "shareTasksAndTimers";
	String KEY_SHARE_LOOT_GROUP = "shareLootAndMilestones";

	@ConfigSection(
		name = "What you share",
		description = "What your group sees. Each switch shares everything named in it; the sidebar's Settings "
			+ "names what is on.",
		position = 0
	)
	String sharingSection = "sharing";

	@ConfigSection(name = "Advanced", description = "The map server to connect to",
		position = 5, closedByDefault = true)
	String advancedSection = "advanced";

	@ConfigSection(
		name = "In RuneLite",
		description = "What the plugin shows and does in RuneLite",
		position = 4,
		closedByDefault = true
	)
	String clientSection = "client";

	/** Internal group code, filled by Connect in the sidebar. An empty code sends nothing. */
	@ConfigItem(
		keyName = KEY_PAIRING_CODE,
		name = "Pairing code",
		description = "",
		position = 0,
		hidden = true
	)
	default String pairingCode()
	{
		return "";
	}

	/**
	 * The eight characters after the dash in the RuneLite code on the map's Companion panel. Filled in
	 * by Connect and sent as a header until the map says it has connected your dot to your browser,
	 * after which it is deleted. Nothing to type here by hand; clearing it only means the map has to
	 * ask who you are.
	 */
	@ConfigItem(
		keyName = KEY_PAIRING_SUFFIX,
		name = "Your RuneLite code's suffix",
		description = "",
		position = 1,
		hidden = true
	)
	default String pairingSuffix()
	{
		return "";
	}

	@ConfigItem(
		keyName = "nickname",
		name = "Name to show instead",
		description = "What your group sees beside your dot while Character name is off. Leave it empty to show as Member.",
		position = 1,
		section = sharingSection
	)
	default String nickname()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_RELAY_URL,
		name = "Map address",
		description = "Where snapshots are sent. Change this only if you run your own copy of the map.",
		position = 3,
		section = advancedSection
	)
	default String relayUrl()
	{
		return DEFAULT_MAP_URL;
	}

	@ConfigItem(
		keyName = KEY_SHARE_NAME,
		name = "Character name",
		description =
			"Send the display name of the character you are logged in as, so your group sees who each "
			+ "dot is. Only ever the name — nothing is looked up by it, and nothing is sent while you "
			+ "are logged out. Leave it off and they see your Name to show instead, or Member.",
		position = 0,
		section = sharingSection
	)
	default boolean shareName()
	{
		return false;
	}

	/*
	 * The four groups' switches. Each names its fields in the sidebar's words, which
	 * SharingGroupsTest holds to SharingFields, and none is read to decide what is sent.
	 */
	@ConfigItem(
		keyName = KEY_SHARE_MAP_PRESENCE,
		name = "Map presence",
		description = "Your dot on the map: Position and world, Training and idle time, and Account type. "
			+ "On shares all three; off stops all three.",
		position = 2,
		section = sharingSection
	)
	default boolean shareMapPresence()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_PROGRESS,
		name = "Progress and travel",
		description = "What the map ranks your routes by: Skill levels, Travel quest completion, Full quest progress, "
			+ "Achievement diaries, Permanent travel and house, Carried travel items, STASH construction, and "
			+ "Boats and moorings. On shares all eight; off stops all eight.",
		position = 3,
		section = sharingSection
	)
	default boolean shareProgressAndTravel()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_TASKS,
		name = "Tasks and timers",
		description = "Slayer task, Clue step, Farming patches, Birdhouses, Hunter rumours, Mahogany Homes, "
			+ "Farming contracts, and Port tasks. On shares all eight; off stops all eight.",
		position = 4,
		section = sharingSection
	)
	default boolean shareTasksAndTimers()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_LOOT_GROUP,
		name = "Loot and milestones",
		description = "Observed kills and drops, Notable drops and levels, Include pet rewards, and Include "
			+ "collection log additions. On shares all four; off stops all four. " + FEED_KEPT,
		position = 5,
		section = sharingSection
	)
	default boolean shareLootAndMilestones()
	{
		return false;
	}

	/**
	 * Where you are standing, which world you are on and what kind of world it is (members, PvP,
	 * league and so on), and whether you are inside an instance and whether it is one place or rooms
	 * put together — in a raid or a house your position means nothing on a shared map, so the map says
	 * so instead of drawing your dot in a room nobody else is in. Hidden, and empty of words, because
	 * RuneLite draws no hidden setting.
	 */
	@ConfigItem(
		keyName = "sharePosition",
		name = "Position and world",
		description = "",
		position = 10,
		hidden = true
	)
	default boolean sharePosition()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareDeath",
		name = "Where you died",
		description =
			"Send the tile of your most recent death so the map can offer a route back to it. Forgotten "
			+ "after an hour.",
		position = 6,
		section = sharingSection
	)
	default boolean shareDeath()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDestination",
		name = "Destinations and meetups",
		description =
			"The places your group sends you and the group's meetups: a destination's card, pin and line in "
			+ "the chat box, the meetups in the sidebar, and their reminder. "
			+ "Nothing is ever clicked or walked for you.",
		position = 30,
		section = clientSection
	)
	default boolean showDestination()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showGroupOnWorldMap",
		name = "Group on the world map",
		description =
			"Draw the other members of your group on the game's world map, in the colours the map gives "
			+ "them. Only members who share their position with the group appear. Nothing about you is sent "
			+ "for this; it receives what the group already shares.",
		position = 34,
		section = clientSection
	)
	default boolean showGroupOnWorldMap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "destinationHintArrow",
		name = "Hint arrow to a destination",
		description =
			"When a destination is in the area the game has loaded, point the game's own hint arrow at it, "
			+ "as RuneLite's clue plugin does. Never replaces an arrow the game or another plugin set.",
		position = 35,
		section = clientSection
	)
	default boolean destinationHintArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHORTEST_PATH,
		name = "Draw paths with Shortest Path",
		description =
			"When you follow a route the map sent, have the Shortest Path plugin, if you have it, draw each "
			+ "walking step. A destination somebody sends never changes its path until you press Follow or Reroute.",
		position = 36,
		section = clientSection
	)
	default boolean drawWithShortestPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeHereOnWorldMap",
		name = "Route here on the world map",
		description =
			"Add Route here to a right-click of the game's world map, below Cancel. Choosing it asks the map "
			+ "for the way to that spot and sends that spot and where you stand, as Route does.",
		position = 37,
		section = clientSection
	)
	default boolean routeHereOnWorldMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStepOnScreen",
		name = "Step on screen",
		description =
			"While you follow a route, show the step you are on in a small box over the game, so the sidebar "
			+ "can stay closed. Hidden inside instances. Nothing leaves this computer.",
		position = 38,
		section = clientSection
	)
	default boolean showStepOnScreen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rerouteHotkey",
		name = "Reroute key",
		description =
			"A key that presses Reroute on the run or route you follow, as the sidebar's button would, with "
			+ "its same wait between presses. Unset until you choose one; pick one with Ctrl, Alt or Shift so "
			+ "typing in chat does not press it.",
		position = 39,
		section = clientSection
	)
	default Keybind rerouteHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "followHotkey",
		name = "Follow and pause key",
		description =
			"A key that presses Follow, or Pause while you follow, on the run or route the sidebar shows, "
			+ "where the card offers it. Unset until you choose one.",
		position = 40,
		section = clientSection
	)
	default Keybind followHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "markFarmRunInBank",
		name = "Farm run teleports in the bank",
		description =
			"While a farm run is going, outline in your bank the teleports its Before you leave list names "
			+ "that you are not carrying. Nothing in the bank moves, and nothing leaves this computer.",
		position = 41,
		section = clientSection
	)
	default boolean markFarmRunInBank()
	{
		return true;
	}

	/**
	 * A RuneLite notification rather than a plain box, so RuneLite's panel offers how it arrives behind
	 * a cog. A "true" or "false" stored before reads as on or off; see {@link NotificationChoice} for
	 * what the sidebar's box writes.
	 */
	@ConfigItem(
		keyName = "notifyMeetups",
		name = "Meetup reminder",
		description =
			"Raise a RuneLite notification before each of your group's meetups starts, as many minutes "
			+ "ahead as the setting below says, so one does not pass while the sidebar is closed. RuneLite "
			+ "holds notifications while its window is focused unless they are sent when focused. Turned on "
			+ "in the sidebar, this one is, and otherwise arrives as your other notifications do; the cog "
			+ "beside this box changes how.",
		position = 31,
		section = clientSection
	)
	default Notification notifyMeetups()
	{
		return Notification.OFF;
	}

	@Range(
		min = 1,
		max = 120
	)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "meetupReminderMinutes",
		name = "Minutes before a meetup",
		description = "How long before a meetup starts the notification above is raised.",
		position = 32,
		section = clientSection
	)
	default int meetupReminderMinutes()
	{
		return 5;
	}

	/** The sidebar's kept ranking choice */
	@ConfigItem(
		keyName = "slayerOrder",
		name = "Slayer order",
		description = "",
		hidden = true
	)
	default String slayerOrder()
	{
		return "best";
	}

	/** The sidebar's kept filter */
	@ConfigItem(
		keyName = "slayerHideWilderness",
		name = "Hide Wilderness Slayer places",
		description = "",
		hidden = true
	)
	default boolean slayerHideWilderness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropLuckChat",
		name = "Drop luck in chat",
		description =
			"When a drop with a known rate lands, add one line to the game chat saying how lucky it was: "
			+ "\"Abyssal whip after 780 kills — drier than 78% of players.\" Nothing leaves this computer.",
		position = 33,
		section = clientSection
	)
	default boolean dropLuckChat()
	{
		return true;
	}
}
