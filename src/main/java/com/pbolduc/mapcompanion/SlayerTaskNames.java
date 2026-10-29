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

import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

/**
 * Turning a slayer id into the name a master would say.
 *
 * <p>The game ships these strings in its own database tables, which is why this plugin can send a
 * name at all rather than a number the map would have to keep a translation table for. The lookup
 * is isolated here because it is the part of the plugin most likely to need adjusting against a
 * client release, and because every caller already handles a null answer.
 *
 * <p><b>A varp holds a value, not a row.</b> {@code SLAYER_TARGET} is the task's {@code id} column,
 * and a database row id is a different number in a different space. So every read below first
 * finds the row by value with {@code getDBRowsByValue}, then reads a column of that row — the order
 * RuneLite's own {@code SlayerPlugin.updateTask} reads them in. Handing the varp straight to
 * {@code getDBTableField} as a row asks some unrelated row for a column it may not have, and the
 * catch below turns that into no task at all, so the mistake has no symptom but a quiet map.
 *
 * <p>The table and column ids are the client's {@link DBTableID} constants rather than copies, so a
 * client release that moves one moves this with it.
 *
 * <p><b>Null is a supported result, not an error.</b> {@link SlayerTaskReader} sends no task when
 * the name will not resolve, on the principle this whole feature runs on: an honest gap beats a
 * confident wrong answer. So a client version whose table this cannot read degrades to "the map
 * shows no slayer task", never to "the map sends you to the wrong monster".
 */
final class SlayerTaskNames
{
	/** An ordinary assignment: the task row whose id column holds the varp's value. */
	@Nullable
	static String forTaskId(Client client, int taskId)
	{
		try
		{
			Integer row = firstRow(client.getDBRowsByValue(
				DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId));
			return row == null ? null : taskName(client, row);
		}
		catch (RuntimeException | LinkageError e)
		{
			return null;
		}
	}

	/**
	 * A boss assignment, which takes two lookups.
	 *
	 * <p>The sublist table has no name of its own. Its row for this boss is found by the boss id the
	 * varbit holds, and its {@code task} column points at a row of the ordinary task table, which is
	 * where the name is — so a boss is named exactly as the game's own helper names it.
	 */
	@Nullable
	static String forBossId(Client client, int bossId)
	{
		try
		{
			Integer sublistRow = firstRow(client.getDBRowsByValue(
				DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, bossId));
			if (sublistRow == null)
			{
				return null;
			}
			Object taskRow = first(client.getDBTableField(sublistRow, DBTableID.SlayerTaskSublist.COL_TASK, 0));
			return taskRow instanceof Integer ? taskName(client, (Integer) taskRow) : null;
		}
		catch (RuntimeException | LinkageError e)
		{
			return null;
		}
	}

	/** The area a master restricted the task to, by the name the game's slayer helper shows. */
	@Nullable
	static String forAreaId(Client client, int areaId)
	{
		try
		{
			Integer row = firstRow(client.getDBRowsByValue(
				DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId));
			return row == null
				   ? null
				   : string(client.getDBTableField(row, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0));
		}
		catch (RuntimeException | LinkageError e)
		{
			return null;
		}
	}

	@Nullable
	private static String taskName(Client client, int row)
	{
		return string(client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0));
	}

	/**
	 * The first matching row, as the game's own plugin takes it.
	 *
	 * <p>Null rather than an empty list is handled too: a client that has not loaded its database,
	 * and the integration harness's fake, both answer that way.
	 */
	@Nullable
	private static Integer firstRow(@Nullable List<Integer> rows)
	{
		return rows == null || rows.isEmpty() ? null : rows.get(0);
	}

	@Nullable
	private static Object first(@Nullable Object[] values)
	{
		return values == null || values.length == 0 ? null : values[0];
	}

	@Nullable
	private static String string(@Nullable Object[] values)
	{
		Object value = first(values);
		return value instanceof String ? (String) value : null;
	}

	private SlayerTaskNames()
	{
	}
}
