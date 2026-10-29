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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;

/**
 * Assembling one snapshot — and the only place that decides what leaves this client.
 *
 * <p>Every field is gated on its own setting, so the disclosure in the README is enforced here
 * rather than merely described. A reviewer wanting to know what this plugin sends should be able to
 * read this file and {@link RelayClient} and know the whole answer.
 *
 * <p>Must run on the client thread: every read below is live game state.
 */
@Singleton
public class SnapshotBuilder
{
	/**
	 * Every name the game can issue, and nothing else — the same pattern the map validates against,
	 * written here so the two halves agree in one glance rather than in two guesses.
	 */
	private static final Pattern CHARACTER_NAME = Pattern.compile("[A-Za-z0-9 _-]{1,12}");

	private final Client client;
	private final MapCompanionConfig config;
	private final MemberIdentity identity;
	private final SlayerTaskReader slayerReader;
	private final ClueReader clueReader;
	private final QuestReader questReader;
	private final DiaryReader diaryReader;
	private final TravelUnlockReader travelUnlockReader;
	private final StashReader stashReader;
	private final BirdhouseReader birdhouseReader;
	private final AssignmentReader assignmentReader;
	private final CapabilityEvaluator capabilities;
	private final FarmingObserver farming;
	private final XpTracker xpTracker;
	private final LootLog lootLog;
	private final MilestoneOutbox milestones;
	private final SnapshotTrimmer trimmer;
	private final DeathWatcher deathWatcher;

	@Inject
	private SnapshotBuilder(
		Client client,
		MapCompanionConfig config,
		MemberIdentity identity,
		SlayerTaskReader slayerReader,
		ClueReader clueReader,
		QuestReader questReader,
		DiaryReader diaryReader,
		TravelUnlockReader travelUnlockReader,
		StashReader stashReader,
		BirdhouseReader birdhouseReader,
		AssignmentReader assignmentReader,
		CapabilityEvaluator capabilities,
		FarmingObserver farming,
		XpTracker xpTracker,
		LootLog lootLog,
		MilestoneOutbox milestones,
		SnapshotTrimmer trimmer,
		DeathWatcher deathWatcher)
	{
		this.client = client;
		this.config = config;
		this.identity = identity;
		this.slayerReader = slayerReader;
		this.clueReader = clueReader;
		this.questReader = questReader;
		this.diaryReader = diaryReader;
		this.travelUnlockReader = travelUnlockReader;
		this.stashReader = stashReader;
		this.birdhouseReader = birdhouseReader;
		this.assignmentReader = assignmentReader;
		this.capabilities = capabilities;
		this.farming = farming;
		this.xpTracker = xpTracker;
		this.lootLog = lootLog;
		this.milestones = milestones;
		this.trimmer = trimmer;
		this.deathWatcher = deathWatcher;
	}

	@Nullable
	public CompanionSnapshot build(long nowMs)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		CompanionSnapshot snapshot = new CompanionSnapshot();
		snapshot.member = new CompanionSnapshot.Member(identity.id(), nickname());
		snapshot.sentAtMs = nowMs;

		/*
		 * The one field that names the account rather than describing it, so it is gated like every
		 * other category — and read from the live player rather than remembered. Reading it here is
		 * what makes "refreshed on login and on a change of character" true with no machinery for
		 * either: the name comes off whoever is logged in at the moment the snapshot is built, and
		 * this method has already returned null if that is nobody.
		 */
		if (config.shareName())
		{
			snapshot.member.characterName = sanitizeCharacterName(local.getName());
		}

		/*
		 * These two used to be sent unconditionally, which quietly made them the exception to this
		 * plugin's own rule that every category is separately refusable — and neither appeared in the
		 * README's disclosure or in the sidebar's summary of what is going out. Both have a setting
		 * now. The wire fields are boxed so that "not shared" is absent rather than a default: an
		 * idleMs of 0 would have been a claim that the player just moved.
		 */
		if (config.shareAccountType())
		{
			/* Varbits read zero for a moment after a login, and zero is "normal": wait it out. */
			snapshot.accountType = questReader.settled(nowMs) ? accountType() : null;
		}

		if (config.shareActivity())
		{
			snapshot.idleMs = xpTracker.idleMs(nowMs);
			snapshot.activeSkill = xpTracker.snapshot(nowMs);
		}

		if (config.sharePosition())
		{
			snapshot.world = client.getWorld();
			snapshot.worldFlags = worldFlags();
			snapshot.instanced = WorldTiles.isInstance(client);
			snapshot.instanceLayout = WorldTiles.layout(client);
			snapshot.pos = position(local);
		}

		if (config.shareSkills())
		{
			snapshot.skills = skills();
		}

		/**
		 * Null when not shared, and that is not the same as an empty list — the map's router treats
		 * an empty list as "none finished" and marks quest-gated routes unusable, while null leaves
		 * them merely uncertain. Leaving the field unset is how "don't assume" is expressed.
		 */
		if (config.shareQuestLog())
		{
			snapshot.questLog = questReader.readLog(nowMs);
		}
		if (config.shareQuests() && questReader.settled(nowMs))
		{
			snapshot.questsCompleted = questReader.readFinished(nowMs);
			/*
			 * Sent alongside the list rather than instead of it. An older map reads the list and
			 * ignores the map of states; a current one prefers the states, because only they can
			 * say that a quest was asked about and is not done — which reads differently from a
			 * quest nobody asked about, all the way through to whether a route is offered.
			 */
			snapshot.questStates = questReader.readStates(nowMs);
		}

		/*
		 * Its own toggle rather than riding on the quest one. A diary is a different kind of fact
		 * about an account and somebody may reasonably want their friends to see one and not the
		 * other, and the rule this plugin holds is that every category is separately refusable.
		 */
		if (config.shareDiaries())
		{
			snapshot.diaries = diaryReader.readCompleted(nowMs);
			snapshot.diaryRewards = diaryReader.readRewards(nowMs);
		}

		if (config.sharePermanentTravel())
		{
			snapshot.travelUnlocks = travelUnlockReader.read(nowMs);
			snapshot.house = travelUnlockReader.house(nowMs);
		}

		if (config.shareStash())
		{
			snapshot.stashUnits = stashReader.read(nowMs);
		}

		if (config.shareCapabilities())
		{
			snapshot.capabilities = capabilities.evaluate();
		}

		if (config.shareSlayer())
		{
			snapshot.slayer = slayerReader.read();
		}

		if (config.shareClue())
		{
			snapshot.clue = clueReader.read();
		}

		if (config.shareHunterRumours())
		{
			snapshot.hunterRumour = assignmentReader.rumour(nowMs);
		}
		if (config.shareMahoganyHomes())
		{
			snapshot.mahoganyHome = assignmentReader.home(nowMs);
		}
		if (config.shareFarmingContracts())
		{
			snapshot.farmingContract = assignmentReader.farming(nowMs);
		}
		if (config.shareBirdhouses())
		{
			snapshot.birdhouses = birdhouseReader.read(nowMs);
		}

		if (config.shareFarming())
		{
			List<CompanionSnapshot.Patch> patches = farming.observations(nowMs);
			if (!patches.isEmpty())
			{
				CompanionSnapshot.Farming f = new CompanionSnapshot.Farming();
				f.patches = patches;
				/*
				 * Stated by the sender rather than inferred by the receiver from a field being
				 * present. The map has to be able to read an absent completionId as "this patch was
				 * not replanted while I was watching" instead of "this plugin cannot tell", and only
				 * the sender knows which of those is true.
				 */
				f.progressVersion = 1;
				snapshot.farming = f;
			}
		}

		/*
		 * The tallies are gathered whether or not they are shared — the log is the player's own and
		 * lives on their disk either way. This toggle decides only whether the totals leave the
		 * machine, which is the distinction the setting text makes too.
		 */
		if (config.shareLoot())
		{
			snapshot.loot = lootLog.snapshot();
		}

		/*
		 * Taken, not drained. An event stays in the outbox until the reply names its id, so a post
		 * that never lands carries the same events again next time rather than losing them.
		 */
		if (config.shareMilestones())
		{
			List<CompanionSnapshot.Milestone> batch = milestones.take(nowMs);
			if (!batch.isEmpty())
			{
				snapshot.milestones = batch;
			}
		}

		if (config.shareDeath())
		{
			snapshot.death = deathWatcher.snapshot(nowMs);
		}

		/*
		 * Last, because it can only judge the finished thing. Two sections here have no natural
		 * ceiling — the kill log grows with every new monster somebody fights — and the relay's
		 * refusal for an oversize body is one this plugin treats as permanent, so an untrimmed
		 * snapshot is a dot that stops for good in somebody's second year of play.
		 */
		trimmer.fit(snapshot);

		return snapshot;
	}

	/**
	 * A character name fit to put on the wire, or null.
	 *
	 * <p>The shape the map accepts is {@code ^[A-Za-z0-9 _-]{1,12}$}, which is every name the game
	 * can issue and nothing else. A name that does not match is <b>dropped rather than repaired</b>,
	 * and that is the decision worth reading: the only way to reach this method with something
	 * unmatchable is for the client to have handed over something that is not a name — a partially
	 * loaded player, a future client's placeholder — and a truncated or stripped version of that is
	 * a wrong name shown confidently beside somebody's dot. Absent means "not known", which is true.
	 *
	 * <p>The one repair made is the non-breaking space the client uses inside names, which is a real
	 * name written the way Jagex writes it rather than an oddity: without this every two-word name
	 * would fail the pattern and quietly never be sent.
	 *
	 * <p>Package-private and static so the rule can be pinned without a client, which matters
	 * because this is the field a reader turned a toggle on to allow.
	 */
	@Nullable
	static String sanitizeCharacterName(@Nullable String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String cleaned = raw.replace('\u00A0', ' ').trim();
		return CHARACTER_NAME.matcher(cleaned).matches() ? cleaned : null;
	}

	/**
	 * The nickname the map reads as none (its {@code DEFAULT_NICKNAME}), so it labels the member
	 * by their shared character name, else "Member". Any other value wins over the name, so an
	 * empty box sends this rather than a placeholder of its own.
	 */
	static final String NO_NICKNAME = "Me";

	/** The alias the player typed, or "" for none, a saved {@link #NO_NICKNAME} included. */
	static String alias(MapCompanionConfig config)
	{
		String nick = config.nickname();
		String trimmed = nick == null ? "" : nick.trim();
		return trimmed.equals(NO_NICKNAME) ? "" : trimmed;
	}

	/**
	 * The alias only while the character name is not shared. One choice decides what the group
	 * sees, so a name ticked to be shown is never sent only to be hidden behind an alias. Never
	 * falls back to the RuneScape name either: that travels as {@code characterName} under its own
	 * toggle, or not at all.
	 */
	private String nickname()
	{
		String alias = alias(config);
		return config.shareName() || alias.isEmpty() ? NO_NICKNAME : alias;
	}

	/**
	 * The player's own position in world coordinates.
	 *
	 * <p>Inside an instance the local coordinates mean nothing on a world map, so {@link WorldTiles}
	 * resolves the template tile where the client can and the position is withheld otherwise.
	 * {@code instanced} is set either way, so the map can say "in an instance" rather than showing a
	 * dot in a cave that is not the cave anybody else is in.
	 */
	@Nullable
	private CompanionSnapshot.Tile position(Player local)
	{
		WorldPoint where = WorldTiles.of(client, local);
		return where == null
			   ? null
			   : new CompanionSnapshot.Tile(where.getX(), where.getY(), where.getPlane());
	}

	/** Real levels, not boosted ones: the map is asking what this account can do, not right now. */
	private Map<String, Integer> skills()
	{
		Map<String, Integer> levels = new HashMap<>();
		int total = 0;
		for (Skill skill : Skill.values())
		{
			if (XpTracker.isOverallSentinel(skill))
			{
				/**
				 * A sentinel, never a stat: {@code getRealSkillLevel(OVERALL)} indexed the raw
				 * level array past the game's skills and returned rubbish. {@code Total} is the
				 * sum computed below instead, and Sailing is a real skill that is sent.
				 *
				 * <p>Against the client this compiles to, 1.12.38, {@code OVERALL} is a deprecated
				 * field holding null rather than an enum constant, so it is absent from
				 * {@code values()} and this branch never runs. It stays because the shape has
				 * already moved once and could move back — a client that restores the constant
				 * would otherwise put that rubbish level in the map's skill list under whatever
				 * name it carries. {@code SkillContractTest} fails the moment either shape changes.
				 */
				continue;
			}
			try
			{
				int level = client.getRealSkillLevel(skill);
				if (level <= 0)
				{
					continue;
				}
				levels.put(displayName(skill), level);
				total += level;
			}
			catch (RuntimeException e)
			{
				/** A skill the client cannot read is left out; absent means unknown, which is true. */
			}
		}
		if (total > 0)
		{
			/** The star filters ask for a total, and computing it here saves the map guessing. */
			levels.put("Total", total);
		}
		return levels;
	}

	/**
	 * The world properties that change what the map should say. Anything else Jagex flags a world
	 * with is not this feature's business and is not sent.
	 */
	private List<String> worldFlags()
	{
		List<String> flags = new ArrayList<>();
		try
		{
			EnumSet<WorldType> types = client.getWorldType();
			if (types == null)
			{
				return flags;
			}
			if (types.contains(WorldType.MEMBERS))
			{
				flags.add("members");
			}
			if (types.contains(WorldType.PVP))
			{
				flags.add("pvp");
			}
			if (types.contains(WorldType.HIGH_RISK))
			{
				flags.add("high_risk");
			}
			if (types.contains(WorldType.DEADMAN))
			{
				flags.add("deadman");
			}
			if (types.contains(WorldType.SEASONAL))
			{
				flags.add("league");
			}
			if (types.contains(WorldType.TOURNAMENT_WORLD))
			{
				flags.add("tournament");
			}
			if (types.contains(WorldType.BETA_WORLD))
			{
				flags.add("beta");
			}
			if (types.contains(WorldType.QUEST_SPEEDRUNNING))
			{
				flags.add("speedrun");
			}
			/**
			 * Fresh Start worlds no longer exist. The map still accepts {@code fresh_start}
			 * on the wire so an older plugin is readable; this client has nothing to send.
			 */
		}
		catch (RuntimeException | LinkageError e)
		{
			/** A world type this client version does not name is simply not reported. */
		}
		return flags;
	}

	/**
	 * The account's own type — the badge the game already prints beside this player's name in every
	 * chat line, so it is public about them by their client's own doing. It earns its place because
	 * a group ironman's answers genuinely differ from a main's.
	 */
	@Nullable
	private String accountType()
	{
		try
		{
			int type = client.getVarbitValue(AccountTypes.VARBIT);
			return AccountTypes.name(type);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static String displayName(Skill skill)
	{
		String name = skill.name().toLowerCase();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}
