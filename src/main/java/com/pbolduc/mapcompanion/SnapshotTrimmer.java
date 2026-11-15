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

import com.google.gson.Gson;
import com.google.inject.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Making a snapshot fit, and saying so when something had to go.
 *
 * <h2>Why this is not a nicety</h2>
 *
 * <p>The relay refuses a body over sixteen kilobytes with a 413, and a 413 is one of the four
 * refusals {@link RelayClient} treats as permanent — the plugin stops posting and the sidebar says
 * "This plugin sent more than the map accepts. Please report this." Nothing here bounded the
 * snapshot, and two of its sections have no natural ceiling: the kill log grows with every new
 * monster somebody fights, and the patch observations grew from fifteen rows to twenty-four. A
 * long-lived account fighting forty tracked monsters produces a snapshot comfortably past the cap,
 * so the failure was not hypothetical — it was waiting for somebody's second year of play, and its
 * symptom would have been a dot that stopped and a sentence blaming the map.
 *
 * <h2>What goes, in what order, and what the reader is told</h2>
 *
 * <p>First the map's own caps are applied — forty monsters, thirty-two drops each, and no patch
 * reading older than the week the map reads. Any clipping of the kill log is disclosed, including
 * drops the receiver would have clipped itself: an omitted obtained item must never look like a
 * known never-obtained item. A monster's collection rows go before its rated ones, and only a rated
 * row cut clears its completeness flag, because a rated row is the only kind whose absence the map
 * reads, as the default. A week-old reading is not disclosed: the map drops it on arrival, so
 * leaving it here costs nobody anything, while sending it cost monsters off the kill log.
 *
 * <p>If that is not enough, the least useful thing goes first: the kill log's collection rows, from
 * the monsters at the foot of the list, then whole monsters from the foot, then the optional full
 * quest log, then the patch observations, oldest first. A drought counter is a curiosity and a
 * six-hour-old patch reading is already a "go and look", and everything else here is bounded by a
 * table rather than by how long somebody has played. The quest log is dropped whole: a subset must
 * not masquerade as a complete log. STASH construction goes next if the remaining fixed fields
 * need space, then the sea charts charted. Both are dropped whole too, and the map reads an absent
 * one as not told, never as none built or charted.
 * The worst-case test includes every category so a new one cannot silently outgrow the budget.
 * Any clipping sets detailsTruncated, which the map discloses alongside the shared facts.
 *
 * <h2>Put back in order, not cut in order</h2>
 *
 * <p>Everything that may go comes off at once, and each part goes back while it fits, the one most
 * worth keeping first. Cutting in order instead is greedy: a whole part dropped late frees room
 * that a list cut early never gets back, so the kill log went to nothing and then the quest log
 * went anyway, and a player sharing what the Connect press recommends showed no kill log at all.
 *
 * <p>The measurement is a serialisation, so a list is not put back a row at a time, which would
 * be forty passes over a twenty-kilobyte string on the game thread every couple of seconds. It is
 * tried whole, since it usually fits, and otherwise bisected: seven passes for a hundred rows, and
 * it keeps exactly as many as fit rather than the half that halving would.
 */
@Singleton
public class SnapshotTrimmer
{
	/**
	 * The budget, under the relay's {@code MAX_SNAPSHOT_BYTES} of 16,384.
	 *
	 * <p>The measurement here is exact — {@link RelayClient} posts this same gson string as the
	 * body — so the margin is not for measurement error. It is so that a future header, wrapper or
	 * one added field cannot turn a snapshot that just fitted into a permanent refusal.
	 */
	static final int BUDGET_BYTES = 15_872;

	/**
	 * The map's own ceilings. Omitted history is disclosed rather than mistaken for no drops.
	 *
	 * <p>Thirty-two is Barrows' twenty-four rated pieces with room for collection rows. A cap under
	 * a table's size cuts an honest log, and the cut makes every rated row left off as the default
	 * an unknown on the map: an ordinary Barrows log has nine pieces by its 160th chest. Never above
	 * {@code MAX_DROPS_PER_MONSTER} in {@code lib/companion-snapshot.ts}, which the map's contract
	 * test holds; the byte budget below bounds the whole snapshot whatever this is.
	 */
	static final int MAX_LOOT_NPCS = 40;
	static final int MAX_DROPS_PER_NPC = 32;

	/**
	 * The oldest patch reading the map reads, its {@code MAX_OBSERVED_AGE_MS}, which the map's
	 * contract test holds this to. A reading is restamped only while its region is loaded, so a site
	 * visited once keeps its reading for good, and the sidebar still uses it.
	 */
	static final long MAX_PATCH_AGE_MS = 7 * 24 * 60 * 60_000L;

	/**
	 * Freshest first, and the id decides between two of the same age.
	 *
	 * <p>The tie-break is the whole point. Age alone leaves the order of equal readings to whatever
	 * order they arrived in, and equal readings are the <i>ordinary</i> case: the game reports a
	 * region's patches together, so every patch of a farm run stop is stamped on the same tick. So
	 * two clients holding the same knowledge dropped different halves of it, and one client dropped
	 * a different half between two posts as its own map reordered itself while filling up.
	 */
	private static final Comparator<CompanionSnapshot.Patch> BY_AGE_THEN_ID = (a, b) ->
	{
		int byAge = Long.compare(a.observedMsAgo, b.observedMsAgo);
		return byAge != 0 ? byAge : String.valueOf(a.id).compareTo(String.valueOf(b.id));
	};

	private final Gson gson;

	@Inject
	SnapshotTrimmer(Gson gson)
	{
		this.gson = gson;
	}

	/** Trim in place until it fits, flagging the snapshot if a reader lost anything by it. */
	void fit(CompanionSnapshot s)
	{
		applyReceiverCaps(s);
		if (fits(s))
		{
			return;
		}
		Map<String, String> questLog = s.questLog;
		Map<Integer, Boolean> stash = s.stashUnits;
		List<CompanionSnapshot.Patch> patches = s.farming == null ? new ArrayList<>() : s.farming.patches;
		List<CompanionSnapshot.Npc> npcs = s.loot == null ? new ArrayList<>() : s.loot.npcs;
		List<CompanionSnapshot.Patch> byAge = new ArrayList<>(patches);
		List<CompanionSnapshot.Npc> log = new ArrayList<>(npcs);
		byAge.sort(BY_AGE_THEN_ID);
		patches.clear();
		npcs.clear();
		s.questLog = null;
		s.stashUnits = null;

		/**
		 * Kept longest: the sea charts, the largest catalogue at up to 384 ids, then STASH, then the
		 * freshest observations, then the quest log, then the kill log. Dailies and Mortimer's offer are
		 * a few hundred bytes together and are never dropped: the worst case fits with both kept.
		 */
		boolean lost = !fits(s) && s.seaCharts != null;
		if (lost)
		{
			s.seaCharts = null;
		}
		s.stashUnits = stash;
		if (!fits(s) && stash != null)
		{
			s.stashUnits = null;
			lost = true;
		}
		lost |= most(s, patches, byAge) < byAge.size();
		s.questLog = questLog;
		if (!fits(s) && questLog != null)
		{
			s.questLog = null;
			lost = true;
		}

		/**
		 * Whole monsters from the top of the log with their rated rows, then their collection rows, again
		 * from the top. The log is ordered pinned, then most recently killed, so the foot is what goes;
		 * and a collection row has no rate, so the map never reads its absence, which makes the 40th
		 * monster's rated rows worth more than the 1st's collection rows.
		 */
		List<List<CompanionSnapshot.Drop>> whole = new ArrayList<>();
		List<List<CompanionSnapshot.Drop>> rated = new ArrayList<>();
		for (CompanionSnapshot.Npc npc : log)
		{
			whole.add(npc.drops);
			rated.add(npc.drops = npc.drops == null ? null
				: npc.drops.stream().filter(d -> !d.collection).collect(Collectors.toList()));
		}
		int kept = most(s, npcs, log);
		int full = most(s, kept, n ->
		{
			for (int i = 0; i < kept; i++)
			{
				log.get(i).drops = (i < n ? whole : rated).get(i);
			}
		});
		lost |= kept < log.size() || !whole.subList(full, kept).equals(rated.subList(full, kept));
		if (patches.isEmpty())
		{
			s.farming = null;
		}
		if (npcs.isEmpty())
		{
			s.loot = null;
		}
		if (lost)
		{
			s.detailsTruncated = true;
		}
	}

	/** As many of {@code rows}, from the top, as fit in {@code target}: how many that was. */
	private <T> int most(CompanionSnapshot s, List<T> target, List<T> rows)
	{
		return most(s, rows.size(), n ->
		{
			target.clear();
			target.addAll(rows.subList(0, n));
		});
	}

	/** The most of {@code count} rows that fit, each try measured whole: what is sent is what was measured. */
	private int most(CompanionSnapshot s, int count, IntConsumer keep)
	{
		keep.accept(count);
		if (fits(s))
		{
			return count;
		}
		int lo = 0;
		int hi = count - 1;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) / 2;
			keep.accept(mid);
			if (fits(s))
			{
				lo = mid;
			}
			else
			{
				hi = mid - 1;
			}
		}
		keep.accept(lo);
		return lo;
	}

	/**
	 * The ceilings the map applies on arrival anyway.
	 *
	 * <p>Clipping on either side loses history. In particular, an absent rated drop must not become
	 * "never obtained" in a web reader; the per-monster completeness bit keeps that unknown. The
	 * collection rows go first, from the end, which is the least recently had: the map shows one
	 * only when it arrives, so cutting it costs that row and keeps every rated row's default.
	 */
	private void applyReceiverCaps(CompanionSnapshot snapshot)
	{
		if (snapshot.farming != null && snapshot.farming.patches != null)
		{
			snapshot.farming.patches.removeIf(patch -> patch.observedMsAgo > MAX_PATCH_AGE_MS);
			/* As the builder sends none: an empty list would say a reading was taken. */
			if (snapshot.farming.patches.isEmpty())
			{
				snapshot.farming = null;
			}
		}
		if (snapshot.loot == null || snapshot.loot.npcs == null)
		{
			return;
		}
		if (snapshot.loot.npcs.size() > MAX_LOOT_NPCS)
		{
			snapshot.detailsTruncated = true;
		}
		trimTo(snapshot.loot.npcs, MAX_LOOT_NPCS);
		for (CompanionSnapshot.Npc npc : snapshot.loot.npcs)
		{
			if (npc.drops == null || npc.drops.size() <= MAX_DROPS_PER_NPC)
			{
				continue;
			}
			snapshot.detailsTruncated = true;
			for (int i = npc.drops.size() - 1; i >= 0 && npc.drops.size() > MAX_DROPS_PER_NPC; i--)
			{
				if (npc.drops.get(i).collection)
				{
					npc.drops.remove(i);
				}
			}
			if (npc.drops.size() > MAX_DROPS_PER_NPC)
			{
				npc.dropsComplete = false;
				trimTo(npc.drops, MAX_DROPS_PER_NPC);
			}
		}
	}

	private boolean fits(CompanionSnapshot snapshot)
	{
		return sizeOf(snapshot) <= BUDGET_BYTES;
	}

	/** The exact number of bytes {@link RelayClient} would put on the wire. */
	int sizeOf(CompanionSnapshot snapshot)
	{
		return gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8).length;
	}

	/** Keep the first {@code keep} rows. */
	static void trimTo(List<?> rows, int keep)
	{
		while (rows.size() > Math.max(0, keep))
		{
			rows.remove(rows.size() - 1);
		}
	}
}
