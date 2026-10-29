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

/**
 * The plugin's settings and the player's choices about sharing.
 *
 * <p>An empty pairing code opens no connection. Joining chooses a group; it does not choose
 * which character data that group may see. Each category starts off and carries RuneLite's
 * third-party warning, so enabling it is a separate decision.
 *
 * <p>The sidebar's sharing checkboxes write the same settings as RuneLite's config panel without
 * a warning of their own: the sidebar's Connect dialog shows this warning, with what connecting
 * sends, before any request leaves, and a checkbox only chooses what goes to a server already
 * approved there. A nickname can label the member without naming an account. Saved choices keep their existing keys so an update does not reset them.
 *
 * <p>Receiving destinations opens the relay stream and is also opt-in. The client section
 * keeps that choice beside the local notification controls.
 */
@ConfigGroup(MapCompanionConfig.GROUP)
public interface MapCompanionConfig extends Config
{
	String GROUP = "mapcompanion";
	String DEFAULT_MAP_URL = "https://osrs-map.com";

	/** Shown by RuneLite's config panel on each sharing setting, and by the sidebar's Connect dialog. */
	String NETWORK_WARNING =
		"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

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

	@ConfigSection(
		name = "Map presence",
		description = "Your dot, name and current activity",
		position = 0,
		closedByDefault = true
	)
	String presenceSection = "presence";

	@ConfigSection(
		name = "Progress & travel",
		description = "Levels, completed quests and available travel",
		position = 1,
		closedByDefault = true
	)
	String progressSection = "progress";

	@ConfigSection(name = "Tasks & timers", description = "Assignments and observed patch states",
		position = 2, closedByDefault = true)
	String tasksSection = "tasks";

	@ConfigSection(name = "Loot & milestones", description = "Observed kills, drops and levels",
		position = 3, closedByDefault = true)
	String lootSection = "loot";

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
		name = "Name to show",
		description =
			"What friends sharing your code see beside your dot. It takes precedence over your "
			+ "character name, so it is also how to appear as something other than your name "
			+ "while the name toggle is on.",
		position = 2,
		section = presenceSection
	)
	default String nickname()
	{
		return "Me";
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
		name = "Show my character name to the group",
		description =
			"Send the display name of the character you are logged in as, so friends holding your "
			+ "code see who each dot is. Only ever the name — nothing is looked up by it, and "
			+ "nothing is sent while you are logged out. Leave it off and your nickname is all "
			+ "they get.",
		position = 9,
		warning = NETWORK_WARNING,
		section = presenceSection
	)
	default boolean shareName()
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
		warning = NETWORK_WARNING,
		section = presenceSection
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
		warning = NETWORK_WARNING,
		section = progressSection
	)
	default boolean shareSkills()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareActivity",
		name = "What you're training",
		description =
			"The skill you last gained experience in, how much in the last five minutes, and how long "
			+ "since you last moved — so the map can show the rocks or trees for it, and tell your "
			+ "friends you are idle rather than ignoring them.",
		position = 12,
		warning = NETWORK_WARNING,
		section = presenceSection
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
		warning = NETWORK_WARNING,
		section = presenceSection
	)
	default boolean shareAccountType()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareQuests",
		name = "Quest completion",
		description =
			"Whether you have started or finished the handful of quests that unlock travel (fairy "
			+ "rings, spirit trees and the like). Nothing else about your quest log is read, and "
			+ "no individual step of one is.",
		position = 14,
		warning = NETWORK_WARNING,
		section = progressSection
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
		warning = NETWORK_WARNING,
		section = progressSection
	)
	default boolean shareDiaries()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sharePermanentTravel",
		name = "Permanent travel unlocks and house",
		description = "Built landing sites, activated statues and other permanent transport unlocks, "
			+ "plus the town your house is in. Unverified flags are not sent.",
		position = 25,
		warning = NETWORK_WARNING,
		section = progressSection
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
		warning = NETWORK_WARNING,
		section = progressSection
	)
	default boolean shareStash()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareBirdhouses",
		name = "Birdhouse observations",
		description = "Birdhouse states observed by RuneLite Time Tracking for this character, with their age. Enable Time Tracking to collect observations.",
		position = 27,
		warning = NETWORK_WARNING,
		section = tasksSection
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
		warning = NETWORK_WARNING,
		section = tasksSection
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
		warning = NETWORK_WARNING,
		section = tasksSection
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
		warning = NETWORK_WARNING,
		section = tasksSection
	)
	default boolean shareFarmingContracts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareQuestLog",
		name = "Full quest progress",
		description = "Started, finished or not started for the full quest and miniquest log, including subquests. Separate from travel-quest sharing; refreshed at most every thirty seconds.",
		position = 31,
		warning = NETWORK_WARNING,
		section = progressSection
	)
	default boolean shareQuestLog()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareCapabilities",
		name = "Travel unlocks",
		description =
			"Whether you are carrying the items that unlock a route — a dramen staff, a slayer ring. "
			+ "Evaluated here as yes/no answers; your inventory itself never leaves the client.",
		position = 16,
		warning = NETWORK_WARNING,
		section = progressSection
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
		warning = NETWORK_WARNING,
		section = tasksSection
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
		warning = NETWORK_WARNING,
		section = tasksSection
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
		warning = NETWORK_WARNING,
		section = tasksSection
	)
	default boolean shareFarming()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareLoot",
		name = "Observed loot kills",
		description =
			"Send observed loot kills, how long ago each monster was last killed, which notable drops "
			+ "you have seen, the monster you are killing now and the drop you pinned, so the map can "
			+ "show your drop luck. The log itself always stays on this computer; this only controls "
			+ "whether it is shared. Normal members worlds only; these are not your lifetime game kill counts.",
		position = 20,
		warning = NETWORK_WARNING,
		section = lootSection
	)
	default boolean shareLoot()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_MILESTONES,
		name = "Drops and levels as they happen",
		description =
			"Announce notable NPC and supported activity rewards, and real levels. Collection items "
			+ "and a small curated exception list qualify; repeated supplies do not. Activity and chest "
			+ "rewards require RuneLite's Loot Tracker. Pets and collection additions also have their "
			+ "own switches below. Nothing is queued while unpaired or logged out.",
		position = 21,
		warning = NETWORK_WARNING,
		section = lootSection
	)
	default boolean shareMilestones()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_PETS,
		name = "Include pet rewards",
		description = "Include observed local pet rewards while Drops and levels is on. A pet message "
					  + "without a confirmed item stays unnamed; no boss or pet is guessed.",
		position = 23,
		warning = NETWORK_WARNING,
		section = lootSection
	)
	default boolean sharePets()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE_COLLECTION,
		name = "Include collection log additions",
		description = "Include new collection log notifications while Drops and levels is on. Enable "
					  + "collection log chat or popup notifications in the game's settings so additions can be "
					  + "observed. Your collection log contents and previously filled slots are never read.",
		position = 24,
		warning = NETWORK_WARNING,
		section = lootSection
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
		position = 22,
		warning = NETWORK_WARNING,
		section = presenceSection
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
		warning = NETWORK_WARNING,
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
		warning = NETWORK_WARNING,
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
		keyName = "notifyMeetups",
		name = "Notify before a meetup",
		description =
			"Raise a RuneLite notification a couple of minutes before a meetup starts, so one does not "
			+ "pass while the sidebar is closed.",
		position = 31,
		warning = NETWORK_WARNING,
		section = clientSection
	)
	default boolean notifyMeetups()
	{
		return false;
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
