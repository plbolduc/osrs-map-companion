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

import java.util.*;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import static com.pbolduc.mapcompanion.DropOdds.count;
import static com.pbolduc.mapcompanion.LootLog.Drop;
import static com.pbolduc.mapcompanion.LootLog.Monster;

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
	static Monster defaultMonster(List<Monster> monsters, @Nullable String current, @Nullable String[] pin)
	{
		Monster now = find(monsters, current);
		if (now != null)
		{
			return now;
		}
		Monster pinned = pin == null ? null : find(monsters, pin[0]);
		if (pinned != null)
		{
			return pinned;
		}
		Monster recent = null;
		for (Monster monster : monsters)
		{
			if (recent == null || monster.lastKillMs > recent.lastKillMs)
			{
				recent = monster;
			}
		}
		return recent;
	}

	@Nullable
	static Monster find(List<Monster> monsters, @Nullable String npc)
	{
		if (npc == null)
		{
			return null;
		}
		for (Monster monster : monsters)
		{
			if (monster.npc.equalsIgnoreCase(npc))
			{
				return monster;
			}
		}
		return null;
	}

	/** The name of the row that adds a monster's rated drops up. No item is called this. */
	static final String ANY = "Any unique";

	/**
	 * "Any unique": every rated drop but the pet, as one drop. Nex's six add up to the wiki's own
	 * 1/43, so the row is the roll the page describes. Three in 212 kills is dry while each item
	 * alone is normal, and only this row can say so.
	 *
	 * <p>Its rate is the sum, its count the sum, its wait the shortest. It abstains where those would
	 * not be one wait — a task or diary rate, a Doom run worth a different amount to each drop, rows
	 * watched over different kills — and with fewer than two drops it would repeat the one row. The
	 * map's {@code anyUnique}, rule for rule.
	 */
	@Nullable
	static Drop anyUnique(Monster monster)
	{
		List<Drop> of = new ArrayList<>();
		for (Drop drop : monster.drops)
		{
			if (drop.oneIn != null && !drop.pet)
			{
				of.add(drop);
			}
		}
		if (of.size() < 2 || "run".equals(monster.unit))
		{
			return null;
		}
		Drop first = of.get(0);
		Drop latest = first;
		int obtained = 0;
		double p = 0;
		for (Drop drop : of)
		{
			if (drop.boostOneIn != null || drop.watched != first.watched || drop.mvp != first.mvp
				|| drop.sharedWatched != first.sharedWatched)
			{
				return null;
			}
			obtained += drop.obtained;
			p += 1 / drop.oneIn;
			latest = drop.sinceLast < latest.sinceLast ? drop : latest;
		}
		return new Drop(ANY, -1, obtained, latest.sinceLast, first.watched, 0, 0, 1 / p, null, null,
			latest.sharedSinceLast, latest.shareSinceLast, first.sharedWatched, first.shareWatched, first.mvp, false);
	}

	/**
	 * The row the details open on: the reader's, else the pinned one here, else "Any unique" where
	 * there is one — the question a boss is farmed by — else the first.
	 */
	@Nullable
	static Drop selected(Monster monster, @Nullable String chosen, @Nullable String[] pin)
	{
		Drop any = anyUnique(monster);
		Drop drop = byName(monster, any, chosen);
		if (drop == null && pin != null && monster.npc.equalsIgnoreCase(pin[0]))
		{
			drop = byName(monster, any, pin[1]);
		}
		return drop != null ? drop : any != null ? any : monster.drops.isEmpty() ? null : monster.drops.get(0);
	}

	@Nullable
	private static Drop byName(Monster monster, @Nullable Drop any, @Nullable String item)
	{
		if (any != null && ANY.equalsIgnoreCase(item))
		{
			return any;
		}
		for (Drop drop : monster.drops)
		{
			if (drop.item.equalsIgnoreCase(item))
			{
				return drop;
			}
		}
		return null;
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
	static String context(Monster monster, boolean isNow, @Nullable Integer taskLeft, long now)
	{
		String kills = count(monster.kills) + (monster.solo ? " solo " : " recorded ") + monster.unit + (monster.kills == 1 ? "" : "s")
			+ (monster.shared > 0 ? ", " + count(monster.shared) + " shared" : "");
		if (isNow && taskLeft != null)
		{
			return "Slayer task · " + count(taskLeft) + " left · " + kills;
		}
		if (isNow)
		{
			return "Killing now · " + kills;
		}
		String last = "chest".equals(monster.unit) ? "Last opened " : "run".equals(monster.unit) ? "Last claimed " : "Last killed ";
		return monster.lastKillMs > 0 ? last + ago(now - monster.lastKillMs) + " · " + kills : kills;
	}

	/**
	 * A drop row's second line: its rate as the wiki prints it, knowing whether the next kill gets
	 * the better one. "Any unique" names the MVP's bonus the one time: "1 in 43, +10% as MVP".
	 */
	static String rowDetail(Monster monster, Drop drop, boolean boostedNow)
	{
		if (drop.oneIn == null)
		{
			return drop.obtained == 0 ? "Not had yet" : units(monster, drop.sinceLast) + " since the last";
		}
		String line = DropOdds.rateLine(drop.rate(), boostedNow, drop.mvp);
		return ANY.equals(drop.item) && drop.mvp > 1 ? line + ", +" + Math.round((drop.mvp - 1) * 100) + "% as MVP" : line;
	}

	/** The chip beside a drop's count, or NONE where there is no rate or nothing to say yet. */
	static DropOdds.Luck rowLuck(Monster monster, Drop drop)
	{
		/* Over the kills this drop was watched for, never every kill: see LootLog.Drop#watched. */
		return drop.oneIn == null ? DropOdds.Luck.NONE : DropOdds.luck(drop.obtained, drop.watchedKills(), drop.rate());
	}

	/** The details under the rows, for one drop. */
	@AllArgsConstructor
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
		/** "3 (usual ~5.4)": the count beside how many players usually have by now; null with no rate. */
		@Nullable
		final String had;
	}

	/**
	 * The details for one drop.
	 *
	 * <p>The forward line is the rest of the slayer task where the monster is on it — the number a
	 * reader decides "keep going or not" by — and the next hundred kills otherwise, which is a size
	 * of session rather than a promise. Each fact is said once: the median wait answered the forward
	 * line's question a second way, and went.
	 */
	static Detail detail(Monster monster, Drop drop, @Nullable Integer taskLeft)
	{
		return detail(monster, drop, taskLeft, false);
	}

	/**
	 * The same, knowing whether the drop's better rate applies to the next kill — on the task, with
	 * the diary — which is the rate the forward line is priced at.
	 */
	static Detail detail(Monster monster, Drop drop, @Nullable Integer taskLeft, boolean boostedNow)
	{
		String sinceLabel = drop.obtained > 0 ? "Since last drop" : "No drop recorded yet";
		String sinceValue = units(monster, drop.sinceLast);
		if (drop.oneIn == null)
		{
			return new Detail(drop.obtained > 0
				? count(drop.obtained) + Words.of("luck.recordedNoRate")
				: "No drop rate on file, so no odds.", sinceLabel, sinceValue, null, -1, null, null, null);
		}
		DropOdds.Rate rate = drop.rate();
		/* The rest of a task is task kills by definition; otherwise the rate that applies next. */
		boolean taskAhead = taskLeft != null && taskLeft > 0;
		boolean boostAhead = LootLog.SLAYER_TASK.equals(drop.boostWhen) ? taskAhead || boostedNow : boostedNow;
		double ahead = boostAhead ? rate.boosted() : rate.oneIn;
		double drierThan = DropOdds.within(rate, drop.sinceKills());
		String boost = ahead != rate.oneIn ? " " + rate.boostLabel : "";
		String aheadLabel = taskAhead ? "Rest of this Slayer task (" + count(taskLeft) + " kills)"
			: "Next " + units(monster, 100) + boost;
		String aheadValue = DropOdds.percent(DropOdds.within(ahead, taskAhead ? taskLeft : 100)) + "% chance";
		return new Detail(
			DropOdds.verdict(drop.obtained, drop.watchedKills(), drop.sinceKills(), rate),
			sinceLabel, sinceValue,
			DropOdds.waitSoFar(drop.sinceKills(), rate),
			drierThan, aheadLabel, aheadValue,
			count(drop.obtained) + " (usual " + DropOdds.usual(drop.watchedKills(), rate) + ")");
	}

	/**
	 * The list, filtered by a query on a monster's name or any of its drops, in the chosen order.
	 * The pin decides which drop a monster is dry on; see {@link #standing}.
	 */
	static List<Monster> list(List<Monster> monsters, String query, Sort sort, @Nullable String[] pin)
	{
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Monster> out = new ArrayList<>();
		for (Monster monster : monsters)
		{
			if (q.isEmpty() || monster.npc.toLowerCase(Locale.ROOT).contains(q) || anyDrop(monster, q))
			{
				out.add(monster);
			}
		}
		Comparator<Monster> order;
		switch (sort)
		{
			case KILLS:
				order = Comparator.comparingInt((Monster m) -> m.kills).reversed();
				break;
			case DRIEST:
				order = Comparator.comparingDouble((Monster m) -> driest(m, pin)).reversed();
				break;
			default:
				order = Comparator.comparingLong((Monster m) -> m.lastKillMs).reversed();
		}
		out.sort(order.thenComparing(m -> m.npc));
		return out;
	}

	private static boolean anyDrop(Monster monster, String q)
	{
		for (Drop drop : monster.drops)
		{
			if (drop.item.toLowerCase(Locale.ROOT).contains(q))
			{
				return true;
			}
		}
		return false;
	}

	/** The drop a monster's list row speaks for, and the share of players that wait is drier than. */
	@AllArgsConstructor
	static final class Standing
	{
		final Drop drop;
		final double drierThan;
	}

	/**
	 * How dry a monster is right now, and on which drop; null with no rated drop.
	 *
	 * <p>A pinned drop is judged on its own wait: the reader chose it before looking. Otherwise the
	 * row speaks for the driest drop, and the driest of several is not judged alone. With six drops
	 * watched, one of them being drier than 83% of players is what most players see, and a chip
	 * that says "Dry" for it says nothing. So the worst wait {@code q} is weighed as the share of
	 * players none of whose {@code m} drops would be that dry: {@code q^m}.
	 *
	 * <p>A drop counts towards {@code m} only where the kills it was watched for could have carried
	 * it that far. An abyssal dagger at 1 in 32,000 cannot be dry after a thousand kills, so it
	 * does not dilute the whip's wait; four crystals at one rate dilute each other.
	 */
	@Nullable
	static Standing standing(Monster monster, @Nullable String[] pin)
	{
		Drop worst = null;
		double q = -1;
		for (Drop drop : monster.drops)
		{
			if (drop.oneIn == null)
			{
				continue;
			}
			double wait = DropOdds.within(drop.rate(), drop.sinceKills());
			if (pin != null && monster.npc.equalsIgnoreCase(pin[0]) && drop.item.equalsIgnoreCase(pin[1]))
			{
				return new Standing(drop, wait);
			}
			if (wait > q)
			{
				worst = drop;
				q = wait;
			}
		}
		if (worst == null)
		{
			return null;
		}
		int could = 0;
		for (Drop drop : monster.drops)
		{
			if (drop.oneIn != null && DropOdds.within(drop.rate(), drop.watchedKills()) >= q)
			{
				could++;
			}
		}
		return new Standing(worst, Math.pow(q, Math.max(1, could)));
	}

	/** {@link #standing} as a number to sort by; 0 with no rated drop. */
	static double driest(Monster monster, @Nullable String[] pin)
	{
		Standing standing = standing(monster, pin);
		return standing == null ? 0 : standing.drierThan;
	}

	/** The list row's chip: "Dry" or "Very dry" when the monster's standing earns it. */
	static DropOdds.Luck dryNow(@Nullable Standing standing)
	{
		int shown = standing == null ? 0 : DropOdds.shown(standing.drierThan);
		return shown >= DropOdds.VERY ? DropOdds.Luck.VERY_DRY : shown >= DropOdds.NOTABLE ? DropOdds.Luck.DRY : DropOdds.Luck.NONE;
	}

	/** Which drop a row's chip is about, for its tooltip: "Primordial crystal: 2,600 kills since the last". */
	static String standingNote(Standing standing)
	{
		Drop drop = standing.drop;
		return drop.item + ": " + count(drop.sinceLast) + (drop.sinceLast == 1 ? " kill" : " kills")
			+ (drop.obtained > 0 ? " since the last" : " without one");
	}

	/** "1 kill", "12 chests", "3 runs": a count of what the monster's tally counts. */
	static String units(Monster monster, long n)
	{
		return count(n) + " " + monster.unit + (n == 1 ? "" : "s");
	}

	/** "1,024 kills · 2 h ago". */
	static String listMeta(Monster monster, long now)
	{
		String kills = units(monster, monster.kills);
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
	 *
	 * <p>The first one the log has seen says "recorded kills". Its wait runs from when watching
	 * began, and a player 2,000 kills dry before installing is not "luckier than 86% of players"
	 * for getting it 80 kills later; "recorded" says whose count the comparison is made on.
	 */
	@Nullable
	static String chatLine(LootLog.Notable drop)
	{
		if (drop.oneIn == null || drop.wait <= 0)
		{
			return null;
		}
		return drop.item + " after " + count(drop.wait) + (drop.first ? " recorded" : "") + (drop.wait == 1 ? " kill" : " kills")
			+ " — " + DropOdds.waitComparison(drop.waitKills(), drop.rate()) + ".";
	}
}
