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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Drop luck's arithmetic and its words, in one place.
 *
 * <p>The map has the same functions in {@code lib/drop-luck.ts}, and the two must say the same
 * thing about the same history — a sidebar that calls you "dry" while the map beside it says
 * "about normal" is worse than either alone. So the thresholds are constants here and there, and
 * both test suites pin the same cases: four whips in 1,024 kills is "Lucky", 700 kills since the
 * last one is "drier than 75%", 143 task kills left is "24%", half of players have one by 355.
 *
 * <p><b>Two questions, never one number.</b> "How lucky have I been" is the count against the
 * kills: a binomial tail. "How dry am I right now" is the wait since the last one: a geometric
 * tail. They disagree all the time — four whips where two is usual, and 700 kills since the last
 * — and both are true, so the card says both rather than averaging them into a feeling.
 *
 * <p><b>Two rates, where the game has two.</b> A drake's tooth is 1/2,560 off a slayer task and
 * 1/512 on one. A wait that mixes the two kinds of kill is priced by both — (1 − 1/2560)^off ×
 * (1 − 1/512)^on — and never by whichever rate applies now: a player who did 300 kills off task
 * and 100 on it is neither as dry as the first rate says nor as the second. {@link Rate} carries
 * the pair and {@link Kills} the split; a drop with one rate is the case where the boosted count
 * is zero, so every single-rate form below is that special case and says exactly what it did.
 *
 * <p><b>Percentiles, in the players' own words.</b> "Drier than 75% of players" is the same fact
 * as "25% would still be waiting", said the way people already say it. A sentence never claims a
 * band the printed percentage does not show: every threshold reads the rounded number.
 */
final class DropOdds
{
	/** At or past this percentile a wait or a count earns a word; below it, it is ordinary. */
	static final int NOTABLE = 75;
	/** And past this one, the word gets "very". */
	static final int VERY = 90;
	/** At or below this, the current wait is early enough to say so. */
	static final int EARLY = 35;
	/** Below half a drop expected and none had, there is no luck either way yet. */
	static final double TOO_EARLY_MEAN = 0.5;

	private DropOdds()
	{
	}

	/** A drop's rate, and the better one that applies to some kills — on a task, with a diary. */
	static final class Rate
	{
		final double oneIn;
		/** Null where every kill is priced alike. */
		@Nullable
		final Double boostOneIn;
		/** "on task", "with the diary": how a reader is told when the second rate applies. */
		@Nullable
		final String boostLabel;

		Rate(double oneIn, @Nullable Double boostOneIn, @Nullable String boostLabel)
		{
			this.oneIn = oneIn;
			this.boostOneIn = boostOneIn;
			this.boostLabel = boostLabel;
		}

		static Rate of(double oneIn)
		{
			return new Rate(oneIn, null, null);
		}

		double boosted()
		{
			return boostOneIn == null ? oneIn : boostOneIn;
		}
	}

	/** A count of kills, split by which rate priced them. */
	static final class Kills
	{
		final long ordinary;
		final long boosted;

		Kills(long ordinary, long boosted)
		{
			this.ordinary = Math.max(0, ordinary);
			this.boosted = Math.max(0, boosted);
		}

		static Kills of(long kills)
		{
			return new Kills(kills, 0);
		}

		long total()
		{
			return ordinary + boosted;
		}
	}

	/** The share of players still without one after {@code kills} kills: (1 - 1/r)^n. */
	static double stillWaiting(double oneIn, long kills)
	{
		return stillWaiting(Rate.of(oneIn), Kills.of(kills));
	}

	/** The same across a mixed wait: the product of each kind of kill's share. */
	static double stillWaiting(Rate rate, Kills kills)
	{
		if (kills.total() <= 0)
		{
			return 1;
		}
		double share = Math.exp(kills.ordinary * Math.log1p(-1 / rate.oneIn) + kills.boosted * Math.log1p(-1 / rate.boosted()));
		/* An underflow is not a certainty: with any rate short of 1 in 1, somebody is still waiting. */
		return share == 0 && Math.min(rate.oneIn, rate.boosted()) > 1 ? Double.MIN_VALUE : share;
	}

	/** The chance of at least one in the next {@code kills} kills. */
	static double within(double oneIn, long kills)
	{
		return within(Rate.of(oneIn), Kills.of(kills));
	}

	static double within(Rate rate, Kills kills)
	{
		double share = 1 - stillWaiting(rate, kills);
		/* The same guard from the other side: 1 - a tiny number rounds to exactly 1 in a double. */
		return share >= 1 && Math.min(rate.oneIn, rate.boosted()) > 1 && kills.total() > 0 ? Math.nextDown(1.0) : share;
	}

	/** Kills by which a share {@code q} of players have had one: 0.5 is the median wait. */
	static long killsFor(double q, double oneIn)
	{
		return (long) Math.ceil(Math.log1p(-q) / Math.log1p(-1 / oneIn));
	}

	/**
	 * P(X ≥ k) for X ~ Binomial(n, p), summed exactly.
	 *
	 * <p>Iterated on the probability mass rather than through factorials, which overflow long
	 * before a kill count does. {@code k} is a drop count, so the loop is short.
	 */
	static double atLeast(int k, long n, double p)
	{
		return atLeast(k, new Kills(n, 0), p, p);
	}

	/**
	 * P(X ≥ k) where X is the sum of two binomials, one per kind of kill.
	 *
	 * <p>Convolved below {@code k} rather than approximated: the drops that matter here are a
	 * handful, so the two short mass arrays are all there is to multiply.
	 */
	static double atLeast(int k, Kills kills, double pOrdinary, double pBoosted)
	{
		if (k <= 0)
		{
			return 1;
		}
		if (k > kills.total())
		{
			return 0;
		}
		double mean = kills.ordinary * pOrdinary + kills.boosted * pBoosted;
		if (k > EXACT_LIMIT || mean > EXACT_LIMIT)
		{
			return normalAtLeast(k, mean, kills.ordinary * pOrdinary * (1 - pOrdinary) + kills.boosted * pBoosted * (1 - pBoosted));
		}
		double[] a = masses(k, kills.ordinary, pOrdinary);
		double[] b = masses(k, kills.boosted, pBoosted);
		double below = 0;
		for (int i = 0; i < k; i++)
		{
			for (int j = 0; i + j < k; j++)
			{
				below += a[i] * b[j];
			}
		}
		return Math.max(0, 1 - below);
	}

	/**
	 * Past this many drops, or this many expected, the exact sum gives way to the normal curve.
	 *
	 * <p>The exact sum starts from (1 − p)^n, which is zero in a double once some 745 drops are
	 * expected — and then called every count "very dry" — and its cost grows with the square of
	 * the count, which a malformed report can make anything. Two hundred is far inside where the
	 * normal curve is good to a percentage point, which is all a printed percentage resolves.
	 */
	static final int EXACT_LIMIT = 200;

	/** P(X ≥ k) by the normal curve with a continuity correction. */
	static double normalAtLeast(int k, double mean, double variance)
	{
		if (variance <= 0)
		{
			return k <= mean ? 1 : 0;
		}
		return 1 - phi((k - 0.5 - mean) / Math.sqrt(variance));
	}

	/**
	 * The standard normal CDF, through Abramowitz and Stegun's 7.1.26 for erf — good to 1.5e-7,
	 * and written so {@code lib/drop-luck.ts} can do exactly the same arithmetic.
	 */
	static double phi(double z)
	{
		double x = Math.abs(z) / Math.sqrt(2);
		double t = 1 / (1 + 0.3275911 * x);
		double erf = 1 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
		return 0.5 * (1 + (z < 0 ? -erf : erf));
	}

	/** P(Y = i) for i below {@code k}, Y ~ Binomial(n, p). */
	private static double[] masses(int k, long n, double p)
	{
		double[] out = new double[k];
		double mass = Math.exp(n * Math.log1p(-p));
		for (int i = 0; i < k && i <= n; i++)
		{
			out[i] = mass;
			mass *= (double) (n - i) / (i + 1) * p / (1 - p);
		}
		return out;
	}

	/** Rounded for printing, without ever rounding a possible thing to 0 or a doubt to 100. */
	static String percent(double share)
	{
		long rounded = Math.round(share * 100);
		if (rounded == 0 && share > 0)
		{
			return "<1";
		}
		if (rounded >= 100 && share < 1)
		{
			return ">99";
		}
		return Long.toString(rounded);
	}

	/** The percentile a word is decided on: the number the reader is shown, not the one behind it. */
	static int shown(double share)
	{
		return (int) Math.round(share * 100);
	}

	/** How the count compares with the kills. {@link #NONE} is "too early to say", not "normal". */
	enum Luck
	{
		NONE(null),
		VERY_LUCKY("Very lucky"),
		LUCKY("Lucky"),
		NORMAL("Normal"),
		DRY("Dry"),
		VERY_DRY("Very dry");

		final String word;

		Luck(String word)
		{
			this.word = word;
		}

		boolean lucky()
		{
			return this == LUCKY || this == VERY_LUCKY;
		}

		boolean dry()
		{
			return this == DRY || this == VERY_DRY;
		}
	}

	static Luck luck(int obtained, long kills, double oneIn)
	{
		return luck(obtained, Kills.of(kills), Rate.of(oneIn));
	}

	/**
	 * Overall luck: how unusual it is to have had this many by now.
	 *
	 * <p>At or above the expected count it is the share who would have had fewer; below it, the
	 * share who would have had more. A tie goes to the lucky side's arithmetic, which at the mean
	 * comes out ordinary either way.
	 */
	static Luck luck(int obtained, Kills kills, Rate rate)
	{
		double pOrdinary = 1 / rate.oneIn;
		double pBoosted = 1 / rate.boosted();
		double mean = kills.ordinary * pOrdinary + kills.boosted * pBoosted;
		if (kills.total() <= 0 || (obtained == 0 && mean < TOO_EARLY_MEAN))
		{
			return Luck.NONE;
		}
		if (obtained >= mean)
		{
			int beat = shown(1 - atLeast(obtained, kills, pOrdinary, pBoosted));
			return beat >= VERY ? Luck.VERY_LUCKY : beat >= NOTABLE ? Luck.LUCKY : Luck.NORMAL;
		}
		int beat = shown(atLeast(obtained + 1, kills, pOrdinary, pBoosted));
		return beat >= VERY ? Luck.VERY_DRY : beat >= NOTABLE ? Luck.DRY : Luck.NORMAL;
	}

	/** "dry right now" and its neighbours, from the share who would have had one by now. */
	static String now(double drierThan)
	{
		int shown = shown(drierThan);
		return shown >= VERY ? "very dry right now"
			: shown >= NOTABLE ? "dry right now"
			: shown <= EARLY ? "fine right now"
			: "about normal right now";
	}

	static String verdict(int obtained, long kills, long sinceLast, double oneIn)
	{
		return verdict(obtained, Kills.of(kills), Kills.of(sinceLast), Rate.of(oneIn));
	}

	/**
	 * The card's first line: "Lucky overall, dry right now."
	 *
	 * <p>With nothing had yet, overall and now are one fact — the same kills, the same rate — so the
	 * sentence says it once rather than twice in different words.
	 */
	static String verdict(int obtained, Kills watched, Kills sinceLast, Rate rate)
	{
		String now = now(within(rate, sinceLast));
		if (obtained == 0)
		{
			return capitalise(now) + ".";
		}
		Luck luck = luck(obtained, watched, rate);
		String overall = luck.lucky() ? "Lucky overall" : luck.dry() ? "Dry overall" : "About normal overall";
		return overall + ", " + now + ".";
	}

	static String waitComparison(long wait, double oneIn)
	{
		return waitComparison(Kills.of(wait), Rate.of(oneIn));
	}

	/**
	 * How a finished wait compares: "luckier than 86%" or "drier than 78%".
	 *
	 * <p>The wait counts the kill that dropped it. Under the median a wait is luck, over it it is a
	 * drought, and the number is always the share of players it beat.
	 */
	static String waitComparison(Kills wait, Rate rate)
	{
		double had = within(rate, wait);
		return had < 0.5
			? "luckier than " + percent(1 - had) + "% of players"
			: "drier than " + percent(had) + "% of players";
	}

	static String waitSoFar(long kills, double oneIn)
	{
		return waitSoFar(Kills.of(kills), Rate.of(oneIn));
	}

	/**
	 * How a wait still going compares: "drier than 75% of players" once it is past the median, and
	 * "99% of players would still be waiting too" before it.
	 *
	 * <p>"Drier than 2% of players" is true and says nothing anyone would say; early in a wait the
	 * fact worth hearing is how many are in the same place.
	 */
	static String waitSoFar(Kills kills, Rate rate)
	{
		double had = within(rate, kills);
		return shown(had) >= 50
			? "drier than " + percent(had) + "% of players"
			: percent(1 - had) + "% of players would still be waiting too";
	}

	static String expectation(long kills, double oneIn)
	{
		return expectation(Kills.of(kills), Rate.of(oneIn), false);
	}

	/**
	 * "~2 on average", or "1 in 32,000" where the average would be a fraction of a drop — the rate
	 * that applies to the next kill, named where it is the better one: "1 in 512 on task".
	 *
	 * <p>One rate, not both: "1 in 2,560 · 1 in 512 on task" took three lines of a sidebar row. The
	 * details say how the wait was priced; the row says what the next kill is worth.
	 */
	static String expectation(Kills kills, Rate rate, boolean boostedNow)
	{
		double mean = kills.ordinary / rate.oneIn + kills.boosted / rate.boosted();
		if (mean >= TOO_EARLY_MEAN)
		{
			return "~" + (mean < 10 ? trim(oneDecimal(mean)) : String.format(Locale.ROOT, "%,d", Math.round(mean)))
				+ " on average";
		}
		return boostedNow && rate.boostOneIn != null
			? "1 in " + grouped(rate.boostOneIn) + " " + rate.boostLabel
			: "1 in " + grouped(rate.oneIn);
	}

	/** 5000 → "5,000", 5012.5 → "5,012.5": the digits grouped, the decimals the table kept. */
	static String grouped(double oneIn)
	{
		long whole = (long) Math.floor(oneIn);
		String text = String.format(Locale.ROOT, "%,d", whole);
		if (oneIn == Math.rint(oneIn))
		{
			return text;
		}
		String decimals = Double.toString(oneIn);
		String fraction = decimals.substring(decimals.indexOf('.'));
		if (fraction.length() <= 3)
		{
			return text + fraction;
		}
		/* A per-kill rate folded from two rolls is not a fraction anybody wrote: one decimal. */
		String rounded = oneDecimal(oneIn);
		int dot = rounded.indexOf('.');
		return String.format(Locale.ROOT, "%,d", Long.parseLong(rounded.substring(0, dot))) + rounded.substring(dot);
	}

	/**
	 * One decimal, rounded half up on the double's exact value — what JavaScript's
	 * {@code toFixed(1)} does. {@code %.1f} rounds the shortest decimal instead, and the two
	 * disagreed at 0.95: "1.0" in the sidebar beside "0.9" on the map.
	 */
	static String oneDecimal(double x)
	{
		return new BigDecimal(x).setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	static String count(long n)
	{
		return String.format(Locale.ROOT, "%,d", n);
	}

	private static String trim(String decimal)
	{
		return decimal.endsWith(".0") ? decimal.substring(0, decimal.length() - 2) : decimal;
	}

	private static String capitalise(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
