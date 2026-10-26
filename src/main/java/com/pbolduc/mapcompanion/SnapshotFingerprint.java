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
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Whether two snapshots say the same thing about the player, ignoring clocks.
 *
 * <p>{@code onVarbitChanged} used to set {@code dirty} unconditionally, and varps churn every
 * few ticks while logged in, so the plugin posted at the two-second floor almost continuously
 * — about thirty requests a minute from a client standing still — while the README claimed
 * "only when something has changed". The fingerprint is the authority: Gson of the snapshot
 * with the time-only fields zeroed, because those change every millisecond and say nothing
 * new.
 *
 * <p>Normalised, and why:
 * <ul>
 *   <li>{@code sentAtMs} — the local clock at send time; always different, never content.
 *   <li>{@code idleMs} — counts up every tick the player stands still.
 *   <li>{@code activeSkill.msSinceLastDrop} — same, for the training chip.
 *   <li>{@code farming.patches[].observedMsAgo} — ages in place; the observation itself is
 *       the content, and a later sweep restamps it.
 *   <li>{@code milestones[].msAgo} — an unacknowledged event is resent unchanged; its id is
 *       what makes it the same event, and its age is only a clock.
 *   <li>{@code death.msAgo} — a death is a tile; how long ago is the heartbeat's job.
 * </ul>
 *
 * Gson is the same serialiser the wire uses. A second one would drift.
 */
@Singleton
public class SnapshotFingerprint
{
	private final Gson gson;

	@Inject
	SnapshotFingerprint(Gson gson)
	{
		this.gson = gson;
	}

	String of(CompanionSnapshot snapshot)
	{
		CompanionSnapshot copy = gson.fromJson(gson.toJson(snapshot), CompanionSnapshot.class);
		copy.sentAtMs = 0L;
		copy.idleMs = null;
		if (copy.activeSkill != null)
		{
			copy.activeSkill.msSinceLastDrop = 0L;
		}
		if (copy.farming != null && copy.farming.patches != null)
		{
			for (CompanionSnapshot.Patch patch : copy.farming.patches)
			{
				patch.observedMsAgo = 0L;
				/**
				 * The receipt's <em>id</em> is content — a new one is news, and changing this
				 * fingerprint is what gets a finished run stop posted promptly. Its age is the
				 * same ticking number as every other duration here, and leaving that in would put
				 * the plugin back on the two-second floor for hours after every replant.
				 */
				if (patch.completedMsAgo != null)
				{
					patch.completedMsAgo = 0L;
				}
			}
		}
		if (copy.birdhouses != null)
		{
			for (CompanionSnapshot.Birdhouse house : copy.birdhouses) house.observedMsAgo = 0L;
		}
		for (CompanionSnapshot.Assignment assignment : new CompanionSnapshot.Assignment[] {copy.hunterRumour, copy.mahoganyHome, copy.farmingContract})
		{
			if (assignment != null)
			{
				assignment.observedMsAgo = 0L;
			}
		}
		if (copy.milestones != null)
		{
			for (CompanionSnapshot.Milestone milestone : copy.milestones)
			{
				/**
				 * The id is the content: a new event changes this fingerprint and gets posted at
				 * once, which is the point of an outbox. Its age only counts up, and leaving that
				 * in would post the same unacknowledged drop every two seconds for five minutes.
				 */
				milestone.msAgo = 0L;
			}
		}
		if (copy.death != null)
		{
			copy.death.msAgo = 0L;
		}
		return gson.toJson(copy);
	}
}
