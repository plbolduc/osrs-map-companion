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

import javax.annotation.Nullable;
import net.runelite.api.Client;

/**
 * Turning a slayer id into the name a master would say.
 *
 * <p>The game ships these strings in its own database tables, which is why this plugin can send a
 * name at all rather than a number the map would have to keep a translation table for. The lookup
 * is isolated here because it is the part of the plugin most likely to need adjusting against a
 * client release, and because every caller already handles a null answer.
 *
 * <p><b>Null is a supported result, not an error.</b> {@link SlayerTaskReader} sends no task when
 * the name will not resolve, on the principle this whole feature runs on: an honest gap beats a
 * confident wrong answer. So a client version whose table this cannot read degrades to "the map
 * shows no slayer task", never to "the map sends you to the wrong monster".
 */
final class SlayerTaskNames
{
	/**
	 * The game's slayer task table. Reading it through the client's database API keeps the names in
	 * step with the game rather than with this repository — the same reason the map joins on names.
	 */
	@Nullable
	static String forTaskId(Client client, int taskId)
	{
		return lookup(client, DbTables.SLAYER_TASK, taskId);
	}

	@Nullable
	static String forBossId(Client client, int bossId)
	{
		return lookup(client, DbTables.SLAYER_TASK_SUBLIST, bossId);
	}

	@Nullable
	static String forAreaId(Client client, int areaId)
	{
		return lookup(client, DbTables.SLAYER_AREA, areaId);
	}

	/**
	 * One guarded read.
	 *
	 * <p>Wrapped in a catch because the database API has changed shape across client versions and a
	 * plugin that throws inside a game-thread callback takes more than itself down. A failure here
	 * means one snapshot without a task name, which the map already knows how to render.
	 */
	@Nullable
	private static String lookup(Client client, int table, int row)
	{
		if (row <= 0)
		{
			return null;
		}
		try
		{
			Object[] values = client.getDBTableField(row, table, 0);
			if (values == null || values.length == 0)
			{
				return null;
			}
			Object first = values[0];
			return first instanceof String ? (String) first : null;
		}
		catch (RuntimeException | LinkageError e)
		{
			return null;
		}
	}

	private SlayerTaskNames()
	{
	}
}
