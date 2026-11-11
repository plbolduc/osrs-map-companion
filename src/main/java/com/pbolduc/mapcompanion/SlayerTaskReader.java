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
import javax.annotation.Nullable;
import net.runelite.api.Client;

/**
 * The current slayer assignment, read the way RuneLite's own slayer plugin reads it.
 *
 * <p>Server-controlled variables, not chat messages. The game exposes the task, the amount
 * remaining and the assigned area directly, and the client resolves the task's <i>name</i> from the
 * game's own tables — so this never has to parse "You are assigned to kill…" out of the chat box,
 * and it stays correct for a player who logs in mid-task.
 *
 * <p><b>Why it sends the name rather than the id.</b> The map joins assignments to monsters by
 * normalized name. An id is stable until Jagex renumbers or inserts, and when that happens a stale
 * table maps one task confidently onto another monster. A name that stops matching produces no row
 * at all, which the map renders as "can't map this task yet". Between a wrong answer and an honest
 * gap, this feature always takes the gap.
 *
 * <p>The varbit and varplayer ids are literals in {@link VarbitIds} and {@link VarPlayerIds}, each
 * asserted against {@code runelite-api}'s generated constant by {@code VarbitIdsTest}; the database
 * tables and columns {@link SlayerTaskNames} reads are the client's constants themselves.
 */
@Singleton
public class SlayerTaskReader
{
	/**
	 * The sentinel the game uses for a boss assignment: the ordinary task id is this value, and the
	 * actual boss is named by a second varbit. Mirrors {@code SlayerPlugin.updateTask}.
	 */
	private static final int BOSS_TASK_SENTINEL = 98;

	private final Client client;

	@Inject
	SlayerTaskReader(Client client)
	{
		this.client = client;
	}

	/**
	 * The assignment, or null when there is none.
	 *
	 * <p>Must be called on the client thread — every read below is game state.
	 *
	 * <p>Zero remaining is returned rather than folded into null: "the task is finished and not yet
	 * replaced" is a real state, and it reads differently on the map from having no task at all.
	 */
	@Nullable
	public CompanionSnapshot.Slayer read()
	{
		int remaining = varPlayer(VarPlayerIds.SLAYER_COUNT);
		if (remaining < 0)
		{
			return null;
		}

		int taskId = varPlayer(VarPlayerIds.SLAYER_TARGET);
		if (taskId <= 0)
		{
			return null;
		}

		/* The names come from the game's own tables, through SlayerTaskNames; null when it cannot say. */
		String taskName = taskId == BOSS_TASK_SENTINEL
						  ? SlayerTaskNames.forBossId(client, client.getVarbitValue(VarbitIds.SLAYER_TARGET_BOSS))
						  : SlayerTaskNames.forTaskId(client, taskId);

		if (taskName == null || taskName.isEmpty())
		{
			/**
			 * The client could not resolve the name. Reporting the raw id instead would hand the map
			 * something it cannot join and must not guess at, so nothing is sent — the map then shows
			 * the player as having no task, which is wrong but harmless, rather than sending them to
			 * whatever monster an id collision points at.
			 */
			return null;
		}

		CompanionSnapshot.Slayer slayer = new CompanionSnapshot.Slayer();
		slayer.taskName = taskName;
		slayer.remaining = remaining;
		int area = varPlayer(VarPlayerIds.SLAYER_AREA);
		slayer.areaName = area <= 0 ? null : SlayerTaskNames.forAreaId(client, area);
		return slayer;
	}

	/**
	 * Whether the game says there is no assignment at all. {@link #read} returns null for that and
	 * for a task this client cannot name alike, and only the first may be shown as "no task": the
	 * other is the honest gap above, not an empty one. Client thread.
	 */
	boolean none()
	{
		return varPlayer(VarPlayerIds.SLAYER_TARGET) == 0;
	}

	private int varPlayer(int id)
	{
		try
		{
			return client.getVarpValue(id);
		}
		catch (RuntimeException e)
		{
			return -1;
		}
	}
}
