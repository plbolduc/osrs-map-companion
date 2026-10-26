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

import com.google.inject.Singleton;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Skill;

/**
 * What the player is training, and how fast.
 *
 * <p>This lives in the plugin rather than on the map for a reason worth stating: the client sees
 * every experience drop at tick resolution and can attribute it exactly, while the map sees
 * two-second samples and would be inferring. Given a choice between measuring and guessing, the
 * side that can measure should.
 *
 * <p>What it reports is deliberately thin — the most recent skill, the experience in the last five
 * minutes, and how long since the last drop. There is no confidence score, because the recency is
 * the confidence: the map shows an active skill for two minutes, calls it "recently" for ten, and
 * then stops claiming anything at all.
 */
@Singleton
public class XpTracker
{
	private static final long WINDOW_MS = 5 * 60_000L;

	private static class Drop
	{
		final Skill skill;
		final int amount;
		final long atMs;

		Drop(Skill skill, int amount, long atMs)
		{
			this.skill = skill;
			this.amount = amount;
			this.atMs = atMs;
		}
	}

	private final Deque<Drop> window = new ArrayDeque<>();
	private final Map<Skill, Integer> lastSeenXp = new HashMap<>();
	/**
	 * The level each skill was at when this session last looked.
	 *
	 * <p>Separate from {@link #lastSeenXp} because it answers a different question and must survive
	 * a skill whose experience did not change. Its first entry per skill is a baseline, never a
	 * level-up, for exactly the reason the experience window swallows its first sighting.
	 */
	private final Map<Skill, Integer> lastSeenLevel = new HashMap<>();

	private Skill lastSkill = null;
	private long lastDropAtMs = 0L;
	/** Any movement or experience gain; what the map's "idle 12m" chip counts from. */
	private long lastActivityAtMs = 0L;

	/**
	 * The overall sentinel, under either shape the client has given it: an enum constant before
	 * 1.12.38, a deprecated field holding {@code null} since. Asked by name rather than through
	 * {@code Skill.OVERALL} so the check compiles without a deprecation warning today and keeps
	 * compiling the day the field is removed — which, with the Plugin Hub building against the
	 * latest client, would otherwise be the day this plugin stops building. {@code
	 * SkillContractTest} pins which shape is current.
	 */
	static boolean isOverallSentinel(Skill skill)
	{
		return skill == null || "OVERALL".equals(skill.name());
	}

	/**
	 * Record a skill's new total.
	 *
	 * <p>The first sighting of each skill is swallowed rather than counted: on login the client
	 * reports every skill's current experience, and treating those as drops would announce that
	 * somebody had just trained all twenty-three at once.
	 */
	public void onXpChanged(Skill skill, int totalXp, long nowMs)
	{
		if (isOverallSentinel(skill))
		{
			/**
			 * The sentinel, which is not a stat: a client posting StatChanged for it must not
			 * announce that every skill moved at once.
			 *
			 * <p>Against 1.12.38 {@code OVERALL} is a deprecated null rather than an enum constant,
			 * so this reads as "not a skill at all" and catches a null the event hands us — which is
			 * what {@code XpTrackerLevelTest.theSentinelIsNotASkill} actually passes it. Unlike the
			 * matching guard in {@link SnapshotBuilder}, which walks {@code values()} and therefore
			 * never sees one, this one is reachable. {@code SkillContractTest} pins the shape both
			 * of them depend on.
			 */
			return;
		}
		Integer previous = lastSeenXp.put(skill, totalXp);
		if (previous == null || totalXp <= previous)
		{
			return;
		}

		int gained = totalXp - previous;
		window.addLast(new Drop(skill, gained, nowMs));
		lastSkill = skill;
		lastDropAtMs = nowMs;
		lastActivityAtMs = nowMs;
		prune(nowMs);
	}

	/**
	 * Note a skill's current level, and say whether this is a level the player just gained.
	 *
	 * <p>False the first time each skill is seen, which is the whole difficulty: on login the client
	 * reports every skill at once, and treating those as increases would announce that somebody had
	 * just levelled twenty-three skills. False again for a level that has not moved, and for one
	 * that went down — a level going down is a client that has been told about a different account,
	 * not an achievement.
	 *
	 * <p>Real level, never boosted: a stew is not a milestone. {@code StatChanged} carries both and
	 * the caller passes the real one.
	 */
	public boolean observeLevel(Skill skill, int level)
	{
		if (isOverallSentinel(skill) || level <= 0)
		{
			return false;
		}
		Integer previous = lastSeenLevel.put(skill, level);
		return previous != null && level > previous;
	}

	/** Called when the player moves, so standing still fishing is not counted as idling. */
	public void onActivity(long nowMs)
	{
		lastActivityAtMs = nowMs;
	}

	/** A logout or hop invalidates the window; the next session starts its own. */
	public void reset()
	{
		window.clear();
		lastSeenXp.clear();
		lastSeenLevel.clear();
		lastSkill = null;
		lastDropAtMs = 0L;
		lastActivityAtMs = 0L;
	}

	private void prune(long nowMs)
	{
		while (!window.isEmpty() && nowMs - window.peekFirst().atMs > WINDOW_MS)
		{
			window.removeFirst();
		}
	}

	@Nullable
	public CompanionSnapshot.ActiveSkill snapshot(long nowMs)
	{
		prune(nowMs);
		if (lastSkill == null || lastDropAtMs == 0L)
		{
			return null;
		}

		int gained = 0;
		for (Drop d : window)
		{
			if (d.skill == lastSkill)
			{
				gained += d.amount;
			}
		}

		CompanionSnapshot.ActiveSkill active = new CompanionSnapshot.ActiveSkill();
		active.skill = displayName(lastSkill);
		active.xpGained5m = gained;
		active.msSinceLastDrop = nowMs - lastDropAtMs;
		return active;
	}

	@Nullable
	public Long idleMs(long nowMs)
	{
		/**
		 * Unknown is absent, not zero. Zero on the wire is the claim that the player moved
		 * this instant, which is the opposite of "we have not seen them yet".
		 */
		return lastActivityAtMs == 0L ? null : nowMs - lastActivityAtMs;
	}

	/**
	 * Hiscores spelling, which is the vocabulary the map's travel profile and skill filters speak.
	 * A plugin sending "RUNECRAFT" and a profile storing "Runecraft" would silently never join.
	 *
	 * <p>Package-private because a level milestone has to be spelled the same way, and the map
	 * rejects a milestone whose skill it cannot match against that same list.
	 */
	static String displayName(Skill skill)
	{
		String name = skill.name().toLowerCase();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}
