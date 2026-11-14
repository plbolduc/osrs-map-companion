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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

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
 * <p>If that is not enough, the kill tallies go, least-killed first, then the patch observations,
 * oldest first. Those are the two least actionable things on the wire — a drought counter is a
 * curiosity and a six-hour-old patch reading is already a "go and look" — and everything else here
 * is bounded by a table rather than by how long somebody has played.
 *
 * <p>The optional full quest log goes before recent farming observations if the snapshot still
 * cannot fit. It is dropped whole: a subset must not masquerade as a complete log. STASH
 * construction is the next optional catalogue if the remaining fixed fields need space, then the
 * sea charts charted. Both are dropped whole too, and the map reads an absent one as not told,
 * never as none built or charted.
 * The worst-case test includes every category so a new one cannot silently outgrow the budget.
 * Any clipping sets detailsTruncated, which the map discloses alongside the shared facts.
 *
 * <h2>Halving rather than one at a time</h2>
 *
 * <p>The measurement is a serialisation, so dropping one row and re-measuring forty times would put
 * forty passes over a twenty-kilobyte string on the game thread every couple of seconds. Halving
 * gets there in at most six, overshoots by at most one row's worth, and errs towards a smaller
 * snapshot — which is the right direction for the thing being avoided.
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
	void fit(CompanionSnapshot snapshot)
	{
		applyReceiverCaps(snapshot);
		if (fits(snapshot))
		{
			return;
		}

		boolean lost = false;
		/** The tallies first: least-killed off the end, since the log is ordered most-killed first. */
		while (lootRows(snapshot) > 0 && !fits(snapshot))
		{
			trimTo(snapshot.loot.npcs, lootRows(snapshot) / 2);
			lost = true;
		}
		if (snapshot.loot != null && snapshot.loot.npcs.isEmpty())
		{
			snapshot.loot = null;
		}

		if (!fits(snapshot) && snapshot.questLog != null)
		{
			snapshot.questLog = null;
			lost = true;
		}

		/** Then the observations, oldest first — the ones already too stale to act on. */
		while (patchRows(snapshot) > 0 && !fits(snapshot))
		{
			List<CompanionSnapshot.Patch> patches = snapshot.farming.patches;
			patches.sort(BY_AGE_THEN_ID);
			trimTo(patches, patches.size() / 2);
			lost = true;
		}
		if (snapshot.farming != null && snapshot.farming.patches.isEmpty())
		{
			snapshot.farming = null;
		}

		if (!fits(snapshot) && snapshot.stashUnits != null)
		{
			snapshot.stashUnits = null;
			lost = true;
		}

		/**
		 * Then the sea charts, the largest catalogue at up to 384 ids. Dailies and Mortimer's offer are
		 * a few hundred bytes together and are never dropped: the worst case fits with both kept.
		 */
		if (!fits(snapshot) && snapshot.seaCharts != null)
		{
			snapshot.seaCharts = null;
			lost = true;
		}

		if (lost)
		{
			snapshot.detailsTruncated = true;
		}
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

	private int lootRows(CompanionSnapshot snapshot)
	{
		return snapshot.loot == null || snapshot.loot.npcs == null ? 0 : snapshot.loot.npcs.size();
	}

	private int patchRows(CompanionSnapshot snapshot)
	{
		return snapshot.farming == null || snapshot.farming.patches == null
			   ? 0 : snapshot.farming.patches.size();
	}

	/** Keep the first {@code keep} rows. Package-private so the halving can be pinned by a test. */
	static void trimTo(List<?> rows, int keep)
	{
		while (rows.size() > Math.max(0, keep))
		{
			rows.remove(rows.size() - 1);
		}
	}
}
