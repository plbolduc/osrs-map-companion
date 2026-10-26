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

import java.util.List;
import java.util.Map;

/**
 * The v1 wire contract, mirrored from {@code lib/companion-snapshot.ts} in the map's repository.
 *
 * <p>Two programs in two languages have to agree on this shape, and this half is maintained by
 * hand — so the protocol is built to make drift harmless rather than to prevent it. Two rules do
 * that, and both live on the other side: unknown fields are ignored, and any field that is absent
 * or unreadable becomes "unknown" rather than a default. So a plugin newer than the site loses
 * only its new field, and a site newer than the plugin simply has less to say.
 *
 * <p>The one thing that must not drift is {@link #v}. A different major version means the same
 * bytes mean something else, and the relay refuses it outright rather than guessing.
 *
 * <p><b>What is deliberately absent:</b> there is no field for an account hash, for chat, or for
 * bank or inventory contents. Not "unused" — absent, so no bug or misconfiguration can populate
 * one. The one identifying thing this protocol carries is {@link Member#characterName}, and the
 * difference is the point: a name is a label the player can change, shown to a group they joined,
 * present only while its own toggle is on. A hash is permanent and would tie every group somebody
 * ever joins to one account, which is what the absent field above is absent for.
 *
 * <p>Every duration here is <i>relative</i> (how long ago), never a timestamp. The player's clock,
 * the server's and the reader's phone all disagree; the relay stamps arrival with its own clock and
 * every age shown to anybody is measured from that one.
 */
public class CompanionSnapshot
{
	public int v = 1;
	public Member member;
	public String accountType;
	public Integer world;
	public List<String> worldFlags;
	public Tile pos;
	/** Boxed like every other optional: absent means "not shared", where false would mean "not in one". */
	public Boolean instanced;
	/**
	 * How that instance was built, while {@link #instanced} is true: {@code copy} when it is one place
	 * copied whole, so {@link #pos} is where the player stands somewhere the map draws, or
	 * {@code assembled} when it is rooms put together, so {@link #pos} is a room in the store every
	 * variant of it is kept in. Absent outside an instance, when the client will not say, and from
	 * older plugins — each of which a map reads as it read an instance before. See
	 * {@link WorldTiles#layout(int[][][])}.
	 */
	public String instanceLayout;
	public Map<String, Integer> skills;
	/** Null means "not shared", which is not the same as "none finished" and must stay distinct. */
	public List<String> questsCompleted;
	/**
	 * The same quests, with the state of each rather than only the finished ones.
	 *
	 * <p>Quest name to {@code finished}, {@code in_progress}, {@code not_started} or
	 * {@code unknown}. A list of the finished ones cannot say the difference between a quest that
	 * is not done and one this client could not read, and the map's router treats those two
	 * oppositely — so naming every quest asked about is how coverage travels alongside the answer.
	 * {@code questsCompleted} stays as it is for older maps.
	 */
	public Map<String, String> questStates;
	/** Separate full-log consent, with unknown states kept explicit. */
	public Map<String, String> questLog;
	/**
	 * The highest achievement diary tier finished in each of the twelve regions.
	 *
	 * <p>Region name to a tier, {@code none} or {@code unknown}. Null while the field is not being
	 * shared, or while the session is too young for the flags to mean anything — see
	 * {@link DiaryReader}, which explains why an over-eager read here would tell the map that a
	 * player who has done every diary has done none.
	 */
	public Map<String, String> diaries;
	/** Verified permanent unlocks; an omitted id was not read. */
	public Map<String, Boolean> travelUnlocks;
	/** Outside-house destination only, never the contents of a private house. */
	public String house;
	/** Construction only; missing ids have no reading. */
	public Map<Integer, Boolean> stashUnits;
	public List<Birdhouse> birdhouses;
	public Assignment hunterRumour;
	public Assignment mahoganyHome;
	public Assignment farmingContract;
	/**
	 * The highest diary reward tier actually collected in each region, on the same terms.
	 *
	 * <p>Separate from {@link #diaries} because finishing the tasks and going to take the cloak are
	 * separate things, and only the second one makes the teleport on it work.
	 */
	public Map<String, String> diaryRewards;
	/**
	 * Things that just happened, rather than things that are true.
	 *
	 * <p>Everything else here is a state the next post can repair; a drop or a level is an event,
	 * and one that is missed never happened as far as the group is concerned. These ride along on a
	 * post that was going out anyway, keep their ids until the reply names them, and are bounded —
	 * see {@link MilestoneOutbox}. Absent means nothing to say or nothing shared.
	 */
	public List<Milestone> milestones;
	/**
	 * Set when something a reader would have seen was dropped to fit the wire cap.
	 *
	 * <p>The map prints it: "Some optional companion details were omitted to keep live sharing
	 * responsive." A panel quietly showing six of somebody's forty tracked monsters is a panel
	 * lying about how much it knows, and this is the field that stops it. See
	 * {@link SnapshotTrimmer} for what goes and in what order.
	 *
	 * <p>Boxed and set only when true. Absent and false say the same thing, and this is a field on
	 * a snapshot that is only ever populated because the snapshot was too big.
	 */
	public Boolean detailsTruncated;
	public List<String> capabilities;
	public Slayer slayer;
	public Clue clue;
	public Farming farming;
	public Loot loot;
	public Death death;
	public ActiveSkill activeSkill;
	/**
	 * How long since the player last moved or gained experience.
	 *
	 * <p>Boxed, because a primitive zero here is not "unknown" — it is the claim that they moved this
	 * instant, which is the opposite of what an unshared field should say.
	 */
	public Long idleMs;
	/** Debug only. The relay's own receipt time is what every age is measured from. */
	public long sentAtMs;

	public static class Member
	{
		public String id;
		public String nick;

		/**
		 * The display name of the character currently logged in, or absent.
		 *
		 * <p>Absent whenever the <i>Show my character name to the group</i> toggle is off, whenever
		 * the client cannot yet name the local player, and — because a snapshot is only built while
		 * logged in at all — whenever nobody is playing. Sanitised to
		 * {@code ^[A-Za-z0-9 _-]{1,12}} before it is set; see {@code SnapshotBuilder.characterName}
		 * for why a name that does not match is dropped rather than mangled into fitting.
		 *
		 * <p>It is a <b>label</b>. The map draws it beside a dot and keeps the last one it saw so an
		 * offline clanmate still has a name; nothing is looked up, joined or searched by it, which is
		 * the line this project holds and the reason there is no field for an account hash.
		 *
		 * <p>{@link #nick} stays as it is and stays the override: a reader who wants to be "Scout"
		 * rather than their name sets one, and the map prefers it.
		 */
		public String characterName;

		public Member(String id, String nick)
		{
			this.id = id;
			this.nick = nick;
		}
	}

	public static class Tile
	{
		public int x;
		public int y;
		public int plane;

		public Tile(int x, int y, int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}

	public static class Slayer
	{
		public String taskName;
		public int remaining;
		public String areaName;
	}

	/** A resolved task name, never the chat or dialogue it was read from. */
	public static class Assignment
	{
		public String name;
		public String detail;
		public String state;
		public long observedMsAgo;
	}

	public static class Birdhouse
	{
		public String id;
		public String state;
		public long observedMsAgo;
	}

	public static class Clue
	{
		public String tier;
		public String text;
		public Integer itemId;
		public Tile coordTile;
		/** Opened beginner picture; its item id alone is ambiguous. */
		public Tile mapTile;
	}

	public static class Farming
	{
		public List<Patch> patches;
		/**
		 * Which promises this release's farming observer makes, so the map can tell a plugin that
		 * witnesses a replant from one that only ever restamped varbits.
		 *
		 * <p>Version 1 means: a patch carrying a {@code completionId} was watched being replanted by
		 * this client, against a reading taken moments before. Absent means an older plugin, whose
		 * patches are still perfectly good observations and simply cannot finish a farm run's stop.
		 * Boxed, so absent and 0 stay different answers.
		 */
		public Integer progressVersion;
	}

	/**
	 * A decoded patch, not a raw varbit.
	 *
	 * <p>Same reasoning as sending a resolved slayer task name: the encoding differs by patch type
	 * and moves with game updates, and the client is the side that already has the game's tables.
	 * Sending the raw number would mean the map keeping its own copy of the encoding, wrong in a way
	 * nobody notices until a herb run is planned around a patch that is actually dead.
	 */
	public static class Patch
	{
		public String id;
		/** empty | growing | ready | diseased | dead | unknown */
		public String state;
		public String produce;
		public Integer stagesLeft;
		/** Relative to this observation, assuming healthy crops and ordinary growth.
		 * The current cycle's phase is unknown, so the estimate is a window. */
		public Long readyInMinMs;
		public Long readyInMaxMs;
		/**
		 * Farming varbits only update while their region is loaded, so this is genuinely "when I
		 * last saw it" and is frequently hours. The map prints it rather than hiding it.
		 */
		public long observedMsAgo;
		/**
		 * The replant this client watched happen here, if it watched one.
		 *
		 * <p>A state reading can say a patch is growing; it can never say who planted it or when,
		 * and a farm run's stop is only finished by somebody doing the work. So this is set from a
		 * witnessed transition and from nothing else — see {@link FarmingObserver}. The id is
		 * stable across every post that carries it, which is what stops a retry, a reconnect or a
		 * reloaded browser counting one replant as two.
		 *
		 * <p>Both are absent unless {@code Farming.progressVersion} is set, and older releases send
		 * neither.
		 */
		public String completionId;
		public Long completedMsAgo;
	}

	/**
	 * Observed ordinary-world loot kills and per-item observation histories.
	 *
	 * <p>Every monster is tracked; a monster with no supported rate carries its kills and the
	 * collection items actually seen, and the map gives it no odds. These counts are never backfilled
	 * or called lifetime game KC, and a newly catalogued item does not inherit earlier kills as dry
	 * evidence.
	 */
	public static class Loot
	{
		public List<Npc> npcs;
		/** The monster being killed now, when the last kill was recent. Absent between grinds. */
		public String current;
		/**
		 * Whether {@link #current} is the slayer task: true only where the task's own counter was
		 * seen to fall with one of its kills. Absent is unknown, never "no".
		 */
		public Boolean currentOnTask;
		/** The drop the player pinned in the sidebar, so the map can show the same one. */
		public Pin pinned;
	}

	public static class Pin
	{
		public String npc;
		public String item;
	}

	public static class Npc
	{
		public String npc;
		public int kills;
		/** How long ago it was last killed. Relative like every other age on this wire; absent is unknown. */
		public Long lastKillMsAgo;
		/** Which rated drops this plugin watches for it; see {@code LootLog.ratedKey}. Absent with none. */
		public String ratedKey;
		public List<Drop> drops;
		/** True only while every tracked item history is present; legacy absence is unknown. */
		public Boolean dropsComplete;
	}

	public static class Drop
	{
		public String item;
		public int obtained;
		/** The kill count when it last fell. Null means never, which is a different thing from zero. */
		public Integer killsAtLast;
		/** First observed opportunity for this item's current history, not an assumed install date. */
		public Integer trackedSinceKill;
		/**
		 * For a drop with a better rate under a condition — on a slayer task, with a diary — how many
		 * kills since its last reset, and since its watching began, were under it. Absent for a drop
		 * with one rate. The map prices the wait by both rates from these.
		 */
		public Integer boostedSinceLast;
		public Integer boostedWatched;
	}

	/**
	 * Where this client last died.
	 *
	 * <p>Relative like every other age on this wire. It survives logging back in on purpose — the
	 * moment you most want to know where your gravestone is comes after the death screen.
	 */
	public static class Death
	{
		public Tile tile;
		public long msAgo;
	}

	public static class ActiveSkill
	{
		public String skill;
		public int xpGained5m;
		public long msSinceLastDrop;
	}

	/**
	 * One announcement: an observed reward, or a level somebody just got.
	 *
	 * <p>One class for both kinds, with the fields of the other kind left null, because the wire is
	 * gson of a POJO and a null field is simply absent from it. {@code kind} says which half to
	 * read; the map ignores a kind it does not know, which is what lets a later release add one
	 * without a flag day.
	 */
	public static class Milestone
	{
		/** Random, minted when the event happened, and kept across every retry of it. */
		public String id;
		/** Which run of the plugin this belongs to. New on a restart and on a character change. */
		public String session;
		public int sequence;
		/** npc_drop (legacy) | level | reward */
		public String kind;
		public long msAgo;

		/** npc_drop. */
		public String npc;
		public String item;
		public Integer itemId;
		public Integer quantity;

		/** reward. Unknown item ids, quantities and sources stay absent. */
		public String rewardType;
		public RewardSource source;
		public Boolean collectionNew;

		/** level. */
		public String skill;
		public Integer level;
	}

	public static class RewardSource
	{
		/** npc | activity */
		public String kind;
		public String name;
	}
}
