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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The slayer task's name, read through a database shaped like the game's.
 *
 * <p>The fake below keeps rows the way the client does: each row has an id of its own, belongs to
 * one table and has the columns that table has, and the only way from a game value to a row is a
 * search by value. Row ids are deliberately in a different range from the values the varps hold,
 * and a few rows are placed where a reader that treats a varp as a row id would land on them — so
 * that mistake reads the wrong monster here instead of quietly reading nothing, as it did on a real
 * client.
 *
 * <p>The table and column ids are the client's {@link DBTableID} constants. The rows, ids and names
 * are made up for the test; only their shape is the game's, taken from RuneLite's own
 * {@code SlayerPlugin.updateTask}.
 */
public class SlayerTaskReaderTest
{
	private static final int ABYSSAL_DEMONS = 42;
	private static final int VORKATH = 3;
	private static final int KALPHITE_LAIR = 5;
	private static final int BOSS_SENTINEL = 98;

	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Row> rows = new HashMap<>();
	private boolean databaseLoaded = true;
	private RuntimeException failure;

	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
		(proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getVarpValue":
					return varps.getOrDefault((Integer) args[0], 0);
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) args[0], 0);
				case "getDBRowsByValue":
					if (failure != null)
					{
						throw failure;
					}
					return databaseLoaded ? rowsByValue((Integer) args[0], (Integer) args[1], (Integer) args[2], args[3]) : null;
				case "getDBTableField":
					return field((Integer) args[0], (Integer) args[1], (Integer) args[2]);
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});
	private final SlayerTaskReader reader = new SlayerTaskReader(client);

	public SlayerTaskReaderTest()
	{
		task(7001, ABYSSAL_DEMONS, "Abyssal demons");
		/** Row 42 is another task: what a reader that took the task id for a row id would name. */
		task(ABYSSAL_DEMONS, 11, "Cave crawlers");
		/** A task whose id equals the boss id: what looking the boss up in the task table finds. */
		task(7004, VORKATH, "Bats");
		task(7002, 1002, "Vorkath");
		task(7003, 1003, "Zulrah");
		sublist(7101, VORKATH, 7002);
		sublist(7102, 4, 7003);
		/** Row 3 of the sublist is another boss, for the same reason as row 42 above. */
		sublist(VORKATH, 4, 7003);
		area(7201, KALPHITE_LAIR, "Kalphite Lair", "in the Kalphite Lair");
		/** Row 5 is another area. */
		area(KALPHITE_LAIR, 6, "Stronghold Slayer Cave", "in the Stronghold Slayer Cave");
	}

	@Test
	public void anOrdinaryTaskIsNamedFromTheRowWhoseIdColumnHoldsTheVarp()
	{
		assign(142, ABYSSAL_DEMONS, 0);
		CompanionSnapshot.Slayer slayer = reader.read();
		assertNotNull("a task the tables can name must be sent", slayer);
		assertEquals("Abyssal demons", slayer.taskName);
		assertEquals(142, slayer.remaining);
		assertNull(slayer.areaName);
	}

	@Test
	public void aBossTaskFollowsTheSublistToTheTaskRowThatNamesIt()
	{
		assign(3, BOSS_SENTINEL, 0);
		varbits.put(VarbitID.SLAYER_TARGET_BOSSID, VORKATH);
		CompanionSnapshot.Slayer slayer = reader.read();
		assertNotNull("a boss the tables can name must be sent", slayer);
		assertEquals("Vorkath", slayer.taskName);
	}

	@Test
	public void anAreaIsNamedAsTheGamesSlayerHelperNamesIt()
	{
		assign(120, ABYSSAL_DEMONS, KALPHITE_LAIR);
		CompanionSnapshot.Slayer slayer = reader.read();
		assertNotNull(slayer);
		assertEquals("Kalphite Lair", slayer.areaName);
	}

	@Test
	public void aTaskTheTablesCannotNameSendsNothing()
	{
		assign(50, 77, 0);
		assertNull(reader.read());
		assign(50, BOSS_SENTINEL, 0);
		varbits.put(VarbitID.SLAYER_TARGET_BOSSID, 77);
		assertNull(reader.read());
	}

	@Test
	public void aClientThatCannotAnswerSendsNothingRatherThanThrowing()
	{
		assign(142, ABYSSAL_DEMONS, KALPHITE_LAIR);
		databaseLoaded = false;
		assertNull("an unloaded database answers null, as the integration harness's fake does", reader.read());
		databaseLoaded = true;
		failure = new IllegalStateException("the database API moved");
		assertNull("a throw inside a game-thread read must stay here", reader.read());
	}

	private void assign(int remaining, int task, int area)
	{
		varps.put(VarPlayerID.SLAYER_COUNT, remaining);
		varps.put(VarPlayerID.SLAYER_TARGET, task);
		varps.put(VarPlayerID.SLAYER_AREA, area);
	}

	private void task(int row, int id, String name)
	{
		rows.put(row, new Row(DBTableID.SlayerTask.ID)
			.with(DBTableID.SlayerTask.COL_ID, id)
			.with(DBTableID.SlayerTask.COL_NAME_LOWERCASE, name.toLowerCase())
			.with(DBTableID.SlayerTask.COL_NAME_UPPERCASE, name));
	}

	private void sublist(int row, int bossId, int taskRow)
	{
		rows.put(row, new Row(DBTableID.SlayerTaskSublist.ID)
			.with(DBTableID.SlayerTaskSublist.COL_SUBTABLE_ID, BOSS_SENTINEL)
			.with(DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, bossId)
			.with(DBTableID.SlayerTaskSublist.COL_TASK, taskRow));
	}

	private void area(int row, int id, String helperName, String text)
	{
		rows.put(row, new Row(DBTableID.SlayerArea.ID)
			.with(DBTableID.SlayerArea.COL_AREA_ID, id)
			.with(DBTableID.SlayerArea.COL_AREA_TEXT, text)
			.with(DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, helperName));
	}

	private List<Integer> rowsByValue(int table, int column, int tuple, Object value)
	{
		assertEquals("every column read here holds a single value", 0, tuple);
		List<Integer> found = new ArrayList<>();
		for (Map.Entry<Integer, Row> entry : rows.entrySet())
		{
			Row row = entry.getValue();
			if (row.table == table && Objects.equals(row.columns.get(column), value))
			{
				found.add(entry.getKey());
			}
		}
		return found;
	}

	/** A row the client does not have, or a column its table does not have, is an error. */
	private Object[] field(int rowId, int column, int tuple)
	{
		assertEquals("every column read here holds a single value", 0, tuple);
		Row row = rows.get(rowId);
		if (row == null || !row.columns.containsKey(column))
		{
			throw new IllegalArgumentException("no column " + column + " on row " + rowId);
		}
		return new Object[] {row.columns.get(column)};
	}

	private static final class Row
	{
		final int table;
		final Map<Integer, Object> columns = new HashMap<>();

		Row(int table)
		{
			this.table = table;
		}

		Row with(int column, Object value)
		{
			columns.put(column, value);
			return this;
		}
	}
}
