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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Which monsters count towards the slayer task, learned from the task's own counter.
 *
 * <p>The card offers "143 kills left: 24% chance of another", and that sentence is only true of a
 * monster that is on the task. Deciding it by name does not work: tasks are categories — "Demons",
 * "Kalphite", "Dragons" — and joining a category to the monster that died needs a table this plugin
 * does not carry and the map keeps for other reasons. The game already knows, though: a task kill
 * makes the counter fall. So a monster is on the task when its kill and a fall of the counter land
 * within {@link #WINDOW_TICKS} of each other, in either order — the counter can move on the death
 * and the loot arrive a tick later.
 *
 * <p>What it can get wrong, and why that is acceptable: in a crowd, an off-task kill landing in the
 * same three ticks as a task kill is marked too. The cost is one line offering task odds for a
 * monster that does not need them; the alternative, guessing from names, is wrong for most tasks
 * every time. Learned per task and forgotten when the task changes, in memory only — the first kill
 * after a relog teaches it again.
 */
final class TaskKills
{
	static final int WINDOW_TICKS = 3;

	private int task = -1;
	private int remaining = -1;
	private int lastFallTick = Integer.MIN_VALUE / 2;
	@Nullable
	private String lastKill;
	private int lastKillTick = Integer.MIN_VALUE / 2;
	private final Set<String> onTask = new HashSet<>();

	/** A loot-bearing kill of {@code npc}, on game tick {@code tick}. */
	synchronized void onKill(String npc, int tick)
	{
		lastKill = npc;
		lastKillTick = tick;
		if (task > 0 && tick - lastFallTick <= WINDOW_TICKS)
		{
			onTask.add(key(npc));
		}
	}

	/**
	 * The task's two varps, read every tick.
	 *
	 * @param task the task id; zero or less is no task
	 * @param remaining the kills left, or a negative number when unreadable
	 */
	synchronized void onTick(int tick, int task, int remaining)
	{
		if (task != this.task)
		{
			this.task = task;
			this.remaining = remaining;
			onTask.clear();
			return;
		}
		if (task > 0 && this.remaining >= 0 && remaining >= 0 && remaining < this.remaining)
		{
			lastFallTick = tick;
			if (lastKill != null && tick - lastKillTick <= WINDOW_TICKS)
			{
				onTask.add(key(lastKill));
			}
		}
		this.remaining = remaining;
	}

	/**
	 * On the task now: it has kills left, and this monster was seen to count towards it.
	 *
	 * <p>Kills left, because the game leaves the task's target set when the count reaches zero —
	 * RuneLite's own slayer plugin reads "has a task" from the count for the same reason — and a
	 * drake killed after its task was done would otherwise be priced at the task's rate.
	 */
	synchronized boolean onTask(String npc)
	{
		return task > 0 && remaining > 0 && onTask.contains(key(npc));
	}

	/**
	 * Whether a kill on {@code tick} was a task kill: on the task now, or the kill that finished it.
	 *
	 * <p>The last kill of a task can arrive a tick after the count reached zero, and it was still a
	 * task kill; within {@link #WINDOW_TICKS} of the fall that took the count to zero it counts.
	 */
	synchronized boolean onTaskAt(String npc, int tick)
	{
		return onTask(npc) || (task > 0 && remaining == 0 && tick - lastFallTick <= WINDOW_TICKS && onTask.contains(key(npc)));
	}

	/** Kills left on the task, for a monster known to be on it; otherwise null. */
	@Nullable
	synchronized Integer remaining(String npc)
	{
		return onTask(npc) && remaining >= 0 ? remaining : null;
	}

	private static String key(String npc)
	{
		return npc.toLowerCase(Locale.ROOT).trim();
	}
}
