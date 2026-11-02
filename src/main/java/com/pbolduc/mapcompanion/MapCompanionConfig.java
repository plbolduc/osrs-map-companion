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
 * {@link SharingFields}. The 23 fields under those groups are still settings of their own, hidden
 * from the list, and still what {@link SnapshotBuilder} and the readers read; a group's switch is
 * stored only because RuneLite draws a box from a stored value. {@link SharingGroups} keeps each
 * switch in step with its fields, and says which wins when they disagree.
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
		description = "What your group sees. Each switch shares everything named in it. A group an earlier version left "
			+ "partly on shows off here, and the sidebar's Settings names what is on.",
		position = 0
	)
	String sharingSection = "sharing";

	@ConfigSection(name = "Advanced", description = "The map server to connect to",
		position = 5, closedByDefault = true)
	String advancedSection = "advanced";

	@ConfigSection(
		name = "In the client",
		description = "What the paired map sends to your screen, and the actions you allow here",
		position = 4,
		closedByDefault = true
	)
	String clientSection = "client";

	@ConfigItem(
		keyName = KEY_PAIRING_CODE,
		name = "Pairing code",
		description = "Internal group code, filled by Connect in the sidebar. An empty code sends nothing.",
		position = 0,
		hidden = true
	)
	default String pairingCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_PAIRING_SUFFIX,
		name = "Your RuneLite code's suffix",
		description =
			"The eight characters after the dash in the RuneLite code on the map's Companion panel. "
			+ "Filled in by Connect and sent as a header until the map says it has connected your "
			+ "dot to your browser, after which it is deleted. Nothing to type here by hand; "
			+ "clearing it only means the map has to ask who you are.",
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

	@ConfigItem(
		keyName = "sharePosition",
		name = "Position and world",
		description =
			"Where you are standing, which world you are on and what kind of world it is (members, "
			+ "PvP, league and so on), and whether you are inside an instance and whether it is "
			+ "one place or rooms put together — in a raid or a house your position means nothing "
			+ "on a shared map, so the map says so instead of drawing your dot in a room nobody "
			+ "else is in.",
		position = 10,
		hidden = true
	)
	default boolean sharePosition()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareSkills",
		name = "Skill levels",
		description =
			"Your levels, so the map can rank routes you can actually use. These are already public "
			+ "on the hiscores.",
		position = 11,
		hidden = true
	)
	default boolean shareSkills()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareActivity",
		name = "Training and idle time",
		description =
			"The skill you last gained experience in, how much in the last five minutes, and how long "
			+ "since you last moved — so the map can show the rocks or trees for it, and tell your "
			+ "friends you are idle rather than ignoring them.",
		position = 12,
		hidden = true
	)
	default boolean shareActivity()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareAccountType",
		name = "Account type",
		description =
			"Whether this account is an ironman, a group ironman, and so on. The map's answers genuinely "
			+ "differ — an ironman cannot buy the teleport a main would. This is the badge the game "
			+ "already prints beside your name in every line of chat.",
		position = 13,
		hidden = true
	)
	default boolean shareAccountType()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareQuests",
		name = "Travel quest completion",
		description =
			"Whether you have started or finished the handful of quests that unlock travel (fairy "
			+ "rings, spirit trees and the like). Nothing else about your quest log is read, and "
			+ "no individual step of one is.",
		position = 14,
		hidden = true
	)
	default boolean shareQuests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareDiaries",
		name = "Achievement diaries",
		description =
			"Which achievement diary tier you have finished in each of the 12 regions, and which of "
			+ "their rewards you have collected — so the map can rank the teleports those unlock "
			+ "instead of asking you to tick them by hand. No individual task is read.",
		position = 15,
		hidden = true
	)
	default boolean shareDiaries()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sharePermanentTravel",
		name = "Permanent travel and house",
		description = "Built landing sites, activated statues and other permanent transport unlocks, "
			+ "plus the town your house is in. Unverified flags are not sent.",
		position = 25,
		hidden = true
	)
	default boolean sharePermanentTravel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareStash",
		name = "STASH construction",
		description = "Which STASH units you have built. Does not read or share stored equipment.",
		position = 26,
		hidden = true
	)
	default boolean shareStash()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareBoats",
		name = "Boats and moorings",
		description = "Where each of your boats is moored and whether it carries a teleport focus, so the map's "
			+ "voyages start at your boat. Boat names are not sent.",
		position = 26,
		hidden = true
	)
	default boolean shareBoats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareBirdhouses",
		name = "Birdhouses",
		description = "Birdhouse states observed by RuneLite Time Tracking for this character, with their age. Enable Time Tracking to collect observations.",
		position = 27,
		hidden = true
	)
	default boolean shareBirdhouses()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareHunterRumours",
		name = "Hunter rumours",
		description = "Your current rumour, after using the Rumour option on a quetzal whistle. Recheck after logging in or hopping.",
		position = 28,
		hidden = true
	)
	default boolean shareHunterRumours()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareMahoganyHomes",
		name = "Mahogany Homes",
		description = "Your customer and contract tier, when observed in game messages or NPC dialogue.",
		position = 29,
		hidden = true
	)
	default boolean shareMahoganyHomes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareFarmingContracts",
		name = "Farming contracts",
		description = "The crop requested by your current Farming Guild contract.",
		position = 30,
		hidden = true
	)
	default boolean shareFarmingContracts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sharePortTasks",
		name = "Port tasks",
		description = "Your five Sailing port task slots: which task each holds, crates collected and delivered, "
			+ "and bounty parts still owed. Cargo itself is never read.",
		position = 37,
		hidden = true
	)
	default boolean sharePortTasks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareQuestLog",
		name = "Full quest progress",
		description = "Started, finished or not started for the full quest and miniquest log, including subquests. Separate from travel-quest sharing; refreshed at most every thirty seconds.",
		position = 31,
		hidden = true
	)
	default boolean shareQuestLog()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareCapabilities",
		name = "Carried travel items",
		description =
			"Whether you are carrying the items that unlock a route — a dramen staff, a slayer ring. "
			+ "Evaluated here as yes/no answers; your inventory itself never leaves the client.",
		position = 16,
		hidden = true
	)
	default boolean shareCapabilities()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareSlayer",
		name = "Slayer task",
		description = "Your current assignment and how many are left.",
		position = 17,
		hidden = true
	)
	default boolean shareSlayer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareClue",
		name = "Clue step",
		description = "The clue scroll step you are on, so the map can route you to it.",
		position = 18,
		hidden = true
	)
	default boolean shareClue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareFarming",
		name = "Farming patches",
		description =
			"The state of patches you have walked past, so the map can build a run. Only patches you "
			+ "have actually seen are known.",
		position = 19,
		hidden = true
	)
	default boolean shareFarming()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareLoot",
		name = "Observed kills and drops",
		description =
			"Send observed loot kills, how long ago each monster was last killed, which notable drops "
			+ "you have seen, the monster you are killing now and the drop you pinned, so the map can "
			+ "show your drop luck. The log itself always stays on this computer; this only controls "
			+ "whether it is shared. Normal members worlds only; these are not your lifetime game kill counts.",
		position = 20,
		hidden = true
	)
	default boolean shareLoot()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_MILESTONES,
		name = "Notable drops and levels",
		description =
			"Announce notable NPC and supported activity rewards, and real levels. Collection items "
			+ "and a small curated exception list qualify; repeated supplies do not. Activity and chest "
			+ "rewards require RuneLite's Loot Tracker. Pets and collection additions are fields of their "
			+ "own beside it. A quest or diary tier you finish while logged in is announced too when "
			+ "its own quest or diary choice is on; nothing done before is. Nothing is queued while "
			+ "unpaired or logged out. " + FEED_KEPT,
		position = 21,
		hidden = true
	)
	default boolean shareMilestones()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_PETS,
		name = "Include pet rewards",
		description = "Include observed local pet rewards while Notable drops and levels is on. A pet message "
					  + "without a confirmed item stays unnamed; no boss or pet is guessed.",
		position = 23,
		hidden = true
	)
	default boolean sharePets()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_COLLECTION,
		name = "Include collection log additions",
		description = "Include new collection log notifications while Notable drops and levels is on. Enable "
					  + "collection log chat or popup notifications in the game's settings so additions can be "
					  + "observed. Your collection log contents and previously filled slots are never read.",
		position = 24,
		hidden = true
	)
	default boolean shareCollectionLog()
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
		name = "Show destinations from the map",
		description =
			"Draw a marker on the world map when somebody sends you one, and count down to meetups. "
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
		name = "Show my group on the world map",
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
		name = "Point the game's arrow at a destination",
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
			+ "walking step. It is sent nothing for a teleport or a ride, and a destination somebody sends "
			+ "never changes its path until you press Follow or Reroute.",
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
		name = "Show the step on screen",
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
		name = "Mark a farm run's teleports in the bank",
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
		name = "Notify before a meetup",
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

	@ConfigItem(
		keyName = "slayerOrder",
		name = "Slayer order",
		description = "The sidebar's kept ranking choice",
		hidden = true
	)
	default String slayerOrder()
	{
		return "best";
	}

	@ConfigItem(
		keyName = "slayerHideWilderness",
		name = "Hide Wilderness Slayer places",
		description = "The sidebar's kept filter",
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
