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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * What the Drop luck card and list say, decided without Swing.
 *
 * <p>Every word and every choice of what to show is made here, so it can be tested as text; the
 * panel only lays the answers out. The map's {@code lib/drop-luck.ts} makes the same choices from
 * the same snapshot, and the rules below are written to be read side by side with it.
 *
 * <h2>Which monster the card shows</h2>
 *
 * <ol>
 * <li>One the reader opened from the list — browsing is never pulled away from under them.</li>
 * <li>Otherwise the one being killed now.</li>
 * <li>Otherwise the pinned drop's monster: pinning means "this is my grind".</li>
 * <li>Otherwise the last one killed.</li>
 * </ol>
 */
final class DropLuckView
{
	private DropLuckView()
	{
	}

	enum Sort
	{
		RECENT("Recent"),
		KILLS("Most killed"),
		DRIEST("Driest");

		final String label;

		Sort(String label)
		{
			this.label = label;
		}
	}

	/** The monster the card shows when the reader has not chosen one. */
	@Nullable
	static LootLog.Monster defaultMonster(List<LootLog.Monster> monsters, @Nullable String current, @Nullable String[] pin)
	{
		LootLog.Monster now = find(monsters, current);
		if (now != null)
		{
			return now;
		}
		LootLog.Monster pinned = pin == null ? null : find(monsters, pin[0]);
		if (pinned != null)
		{
			return pinned;
		}
		LootLog.Monster recent = null;
		for (LootLog.Monster monster : monsters)
		{
			if (recent == null || monster.lastKillMs > recent.lastKillMs)
			{
				recent = monster;
			}
		}
		return recent;
	}

	@Nullable
	static LootLog.Monster find(List<LootLog.Monster> monsters, @Nullable String npc)
	{
		if (npc == null)
		{
			return null;
		}
		for (LootLog.Monster monster : monsters)
		{
			if (monster.npc.equalsIgnoreCase(npc))
			{
				return monster;
			}
		}
		return null;
	}

	/** The row the details open on: the reader's, else the pinned one here, else the first. */
	@Nullable
	static LootLog.Drop selected(LootLog.Monster monster, @Nullable String chosen, @Nullable String[] pin)
	{
		for (LootLog.Drop drop : monster.drops)
		{
			if (drop.item.equalsIgnoreCase(chosen))
			{
				return drop;
			}
		}
		if (pin != null && monster.npc.equalsIgnoreCase(pin[0]))
		{
			for (LootLog.Drop drop : monster.drops)
			{
				if (drop.item.equalsIgnoreCase(pin[1]))
				{
					return drop;
				}
			}
		}
		return monster.drops.isEmpty() ? null : monster.drops.get(0);
	}

	/**
	 * The small grey line over the monster's name: why this monster is the one showing, when that
	 * needs saying.
	 *
	 * <p>Nothing while it is the monster being killed or one opened from the list: the card's own
	 * line already says "Killing now" or "Last killed", and the sidebar heads the section "Drop
	 * luck", so saying that again here printed the section's name twice, one line apart.
	 */
	static String eyebrow(boolean opened, boolean isNow, boolean pinnedHere)
	{
		if (opened || isNow)
		{
			return "";
		}
		return pinnedHere ? "Your pinned drop" : "Last monster killed";
	}

	/** "Slayer task · 143 left · 1,024 recorded kills", or the last kill's age. */
	static String context(LootLog.Monster monster, boolean isNow, @Nullable Integer taskLeft, long now)
	{
		String kills = DropOdds.count(monster.kills) + " recorded " + (monster.kills == 1 ? "kill" : "kills");
		if (isNow && taskLeft != null)
		{
			return "Slayer task · " + DropOdds.count(taskLeft) + " left · " + kills;
		}
		if (isNow)
		{
			return "Killing now · " + kills;
		}
		return monster.lastKillMs > 0 ? "Last killed " + ago(now - monster.lastKillMs) + " · " + kills : kills;
	}

	/** A drop row's second line: the average, or the rate where the average would be a fraction. */
	static String rowDetail(LootLog.Monster monster, LootLog.Drop drop)
	{
		return rowDetail(monster, drop, false);
	}

	/** The same, knowing whether the next kill gets the drop's better rate. */
	static String rowDetail(LootLog.Monster monster, LootLog.Drop drop, boolean boostedNow)
	{
		if (drop.oneIn == null)
		{
			return drop.obtained == 0 ? "Not had yet" : DropOdds.count(drop.sinceLast) + " kills since the last";
		}
		return DropOdds.expectation(drop.watchedKills(), drop.rate(), boostedNow);
	}

	/** The chip beside a drop's count, or NONE where there is no rate or nothing to say yet. */
	static DropOdds.Luck rowLuck(LootLog.Monster monster, LootLog.Drop drop)
	{
		/* Over the kills this drop was watched for, never every kill: see LootLog.Drop#watched. */
		return drop.oneIn == null ? DropOdds.Luck.NONE : DropOdds.luck(drop.obtained, drop.watchedKills(), drop.rate());
	}

	/** The details under the rows, for one drop. */
	static final class Detail
	{
		final String verdict;
		final String sinceLabel;
		final String sinceValue;
		/** "drier than 75% of players", or null with no rate. */
		@Nullable
		final String sinceComparison;
		/** Where the strip's dot sits, 0–1; negative with no rate, and the strip is not drawn. */
		final double drierThan;
		@Nullable
		final String aheadLabel;
		@Nullable
		final String aheadValue;
		@Nullable
		final String median;

		Detail(String verdict, String sinceLabel, String sinceValue, @Nullable String sinceComparison,
			double drierThan, @Nullable String aheadLabel, @Nullable String aheadValue, @Nullable String median)
		{
			this.verdict = verdict;
			this.sinceLabel = sinceLabel;
			this.sinceValue = sinceValue;
			this.sinceComparison = sinceComparison;
			this.drierThan = drierThan;
			this.aheadLabel = aheadLabel;
			this.aheadValue = aheadValue;
			this.median = median;
		}
	}

	/**
	 * The details for one drop.
	 *
	 * <p>The forward line is the rest of the slayer task where the monster is on it — the number a
	 * reader decides "keep going or not" by — and the next hundred kills otherwise, which is a size
	 * of session rather than a promise.
	 */
	static Detail detail(LootLog.Monster monster, LootLog.Drop drop, @Nullable Integer taskLeft)
	{
		return detail(monster, drop, taskLeft, false);
	}

	/**
	 * The same, knowing whether the drop's better rate applies to the next kill — on the task, with
	 * the diary — which is the rate the forward line and the median are priced at.
	 */
	static Detail detail(LootLog.Monster monster, LootLog.Drop drop, @Nullable Integer taskLeft, boolean boostedNow)
	{
		String sinceLabel = drop.obtained > 0 ? "Since last drop" : "No drop recorded yet";
		String sinceValue = DropOdds.count(drop.sinceLast) + (drop.sinceLast == 1 ? " kill" : " kills");
		if (drop.oneIn == null)
		{
			return new Detail(drop.obtained > 0
				? DropOdds.count(drop.obtained) + " recorded. No drop rate on file, so no odds."
				: "No drop rate on file, so no odds.", sinceLabel, sinceValue, null, -1, null, null, null);
		}
		DropOdds.Rate rate = drop.rate();
		/* The rest of a task is task kills by definition; otherwise the rate that applies next. */
		boolean taskAhead = taskLeft != null && taskLeft > 0;
		boolean boostAhead = LootLog.SLAYER_TASK.equals(drop.boostWhen) ? taskAhead || boostedNow : boostedNow;
		double ahead = boostAhead ? rate.boosted() : rate.oneIn;
		double drierThan = DropOdds.within(rate, drop.sinceKills());
		String another = drop.obtained > 0 ? "another" : "one";
		String aheadLabel;
		String aheadValue;
		if (taskAhead)
		{
			aheadLabel = "Rest of this Slayer task (" + DropOdds.count(taskLeft) + " kills)";
			aheadValue = DropOdds.percent(DropOdds.within(ahead, taskLeft)) + "% chance of " + another;
		}
		else
		{
			aheadLabel = "Next 100 kills" + (ahead != rate.oneIn ? " " + rate.boostLabel : "");
			aheadValue = DropOdds.percent(DropOdds.within(ahead, 100)) + "% chance of " + another;
		}
		return new Detail(
			DropOdds.verdict(drop.obtained, drop.watchedKills(), drop.sinceKills(), rate),
			sinceLabel, sinceValue,
			DropOdds.waitSoFar(drop.sinceKills(), rate),
			drierThan, aheadLabel, aheadValue,
			"Half of players get one within " + DropOdds.count(DropOdds.killsFor(0.5, ahead)) + " kills"
				+ (ahead != rate.oneIn ? " " + rate.boostLabel : "") + ".");
	}

	/** The list, filtered by a query on a monster's name or any of its drops, in the chosen order. */
	static List<LootLog.Monster> list(List<LootLog.Monster> monsters, String query, Sort sort)
	{
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<LootLog.Monster> out = new ArrayList<>();
		for (LootLog.Monster monster : monsters)
		{
			if (q.isEmpty() || monster.npc.toLowerCase(Locale.ROOT).contains(q) || anyDrop(monster, q))
			{
				out.add(monster);
			}
		}
		Comparator<LootLog.Monster> order;
		switch (sort)
		{
			case KILLS:
				order = Comparator.comparingInt((LootLog.Monster m) -> m.kills).reversed();
				break;
			case DRIEST:
				order = Comparator.comparingDouble(DropLuckView::driest).reversed();
				break;
			default:
				order = Comparator.comparingLong((LootLog.Monster m) -> m.lastKillMs).reversed();
		}
		out.sort(order.thenComparing(m -> m.npc));
		return out;
	}

	private static boolean anyDrop(LootLog.Monster monster, String q)
	{
		for (LootLog.Drop drop : monster.drops)
		{
			if (drop.item.toLowerCase(Locale.ROOT).contains(q))
			{
				return true;
			}
		}
		return false;
	}

	/** The monster's most unusual current wait, as a share of players it is drier than; 0 with no rate. */
	static double driest(LootLog.Monster monster)
	{
		double worst = 0;
		for (LootLog.Drop drop : monster.drops)
		{
			if (drop.oneIn != null)
			{
				worst = Math.max(worst, DropOdds.within(drop.rate(), drop.sinceKills()));
			}
		}
		return worst;
	}

	/** The list row's chip: "Dry" or "Very dry" when the monster's worst wait earns it. */
	static DropOdds.Luck dryNow(LootLog.Monster monster)
	{
		int shown = DropOdds.shown(driest(monster));
		return shown >= DropOdds.VERY ? DropOdds.Luck.VERY_DRY : shown >= DropOdds.NOTABLE ? DropOdds.Luck.DRY : DropOdds.Luck.NONE;
	}

	/** "1,024 kills · 2 h ago". */
	static String listMeta(LootLog.Monster monster, long now)
	{
		String kills = DropOdds.count(monster.kills) + (monster.kills == 1 ? " kill" : " kills");
		return monster.lastKillMs > 0 ? kills + " · " + ago(now - monster.lastKillMs) : kills;
	}

	/** "just now", "12 min ago", "2 h ago", "yesterday", "3 days ago", "5 weeks ago", "4 months ago". */
	static String ago(long ms)
	{
		long minutes = Math.max(0, ms) / 60_000L;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + " min ago";
		}
		long hours = Math.round(minutes / 60.0);
		if (hours < 24)
		{
			return hours + " h ago";
		}
		long days = Math.round(minutes / 1440.0);
		if (days < 2)
		{
			return "yesterday";
		}
		if (days < 14)
		{
			return days + " days ago";
		}
		if (days < 60)
		{
			return Math.round(days / 7.0) + " weeks ago";
		}
		return Math.round(days / 30.0) + " months ago";
	}

	/**
	 * The chat line for a rated drop: "Abyssal whip after 780 kills — drier than 78% of players."
	 *
	 * <p>Null for a drop with no rate: the game prints its own collection-log line for those, and a
	 * second line with no number in it would only repeat it.
	 */
	@Nullable
	static String chatLine(LootLog.Notable drop)
	{
		if (drop.oneIn == null || drop.wait <= 0)
		{
			return null;
		}
		return drop.item + " after " + DropOdds.count(drop.wait) + (drop.wait == 1 ? " kill" : " kills")
			+ " — " + DropOdds.waitComparison(new DropOdds.Kills(drop.wait - drop.waitBoosted, drop.waitBoosted), drop.rate()) + ".";
	}
}
