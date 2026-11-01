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
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The five port task slots, read through a database shaped like the game's.
 *
 * <p>As in {@code SlayerTaskReaderTest}, a row has an id of its own and the only way from a slot's
 * value to a row is a search by value. A decoy row sits at the row id equal to a task's value, so a
 * reader that took the value for a row would read another task here instead of nothing. The table
 * and column ids are the client's {@link DBTableID} constants; the two placeholder rows are the
 * client's own row ids. The task ids, names and counts are the map's {@code data/port-tasks.json}
 * rows for Port Sarim's spice delivery and tern bounty.
 */
public class PortTaskReaderTest
{
	private static final int SPICES = 2;
	private static final int TERNS = 438;
	private static final int NO_TASK = 601;
	private static final int LOCKED = 602;
	private static final int SARIM_DOCK_ROW = 9100;
	private static final int PANDEMONIUM_DOCK_ROW = 9101;
	private static final int TERN_FEATHER = 32000;

	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Map<Integer, Object[]>> rows = new HashMap<>();
	private final Map<Integer, Integer> tableOf = new HashMap<>();
	private RuntimeException failure;

	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
		(proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) args[0], 0);
				case "getDBRowsByValue":
					if (failure != null)
					{
						throw failure;
					}
					return rowsByValue((Integer) args[0], (Integer) args[1], args[3]);
				case "getDBTableField":
					return field((Integer) args[0], (Integer) args[1], (Integer) args[2]);
				case "getItemDefinition":
					return named(ItemComposition.class, (Integer) args[0] == TERN_FEATHER ? "Tern feather" : null);
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});
	private final PortTaskReader reader = new PortTaskReader(client);

	public PortTaskReaderTest()
	{
		dock(SARIM_DOCK_ROW, 0, "Port Sarim");
		dock(PANDEMONIUM_DOCK_ROW, 1, "The Pandemonium");
		task(8665, SPICES, "Port Sarim spice delivery", PortTaskReader.TYPE_COURIER);
		cell(8665, DBTableID.PortTask.COL_STARTING_PORT, SARIM_DOCK_ROW);
		cell(8665, DBTableID.PortTask.COL_CARGO_PORT, PANDEMONIUM_DOCK_ROW);
		cell(8665, DBTableID.PortTask.COL_ENDING_PORT, SARIM_DOCK_ROW);
		cell(8665, DBTableID.PortTask.COL_CARGO, 31000, 1);
		task(9001, TERNS, "Port Sarim tern bounty", PortTaskReader.TYPE_BOUNTY);
		cell(9001, DBTableID.PortTask.COL_STARTING_PORT, SARIM_DOCK_ROW);
		cell(9001, DBTableID.PortTask.COL_BOUNTY_OBJECT, TERN_FEATHER);
		cell(9001, DBTableID.PortTask.COL_BOUNTY_OBJECT_AMOUNT, 15);
		/** Row 2 and row 438 are other tasks: what a reader taking the value for a row would name. */
		task(SPICES, 77, "Catherby bait delivery", PortTaskReader.TYPE_COURIER);
		task(TERNS, 78, "Mogre bounty", PortTaskReader.TYPE_BOUNTY);
		task(DBTableID.PortTask.Row.PORT_TASK_BLANK, NO_TASK, "No Task", PortTaskReader.TYPE_COURIER);
		task(DBTableID.PortTask.Row.PORT_TASK_LOCKED, LOCKED, "Locked task slot", PortTaskReader.TYPE_COURIER);
		task(9500, 700, "A kind this reader has never seen", 7);
	}

	@Test
	public void theCountersAreTheOnesTheSourcesNamePerSlot()
	{
		/* nucleon's PortTaskTrigger, slot 0 to 4; the client names them BOUNTY_TASK_ITEM_COUNTER_1 to _5. */
		assertArrayEquals(new int[] {14662, 14663, 14819, 15370, 15397}, PortTaskReader.BOUNTY_REMAINING);
		assertArrayEquals(new int[] {
			VarbitID.PORT_TASK_SLOT_0_ID, VarbitID.PORT_TASK_SLOT_1_ID, VarbitID.PORT_TASK_SLOT_2_ID,
			VarbitID.PORT_TASK_SLOT_3_ID, VarbitID.PORT_TASK_SLOT_4_ID}, PortTaskReader.SLOT_IDS);
		assertEquals(19574, PortTaskReader.SLOT_IDS[0]);
		assertEquals(VarbitID.PORT_TASK_SLOT_3_CARGO_TAKEN, PortTaskReader.CARGO_TAKEN[3]);
		assertEquals(VarbitID.PORT_TASK_SLOT_3_CARGO_DELIVERED, PortTaskReader.CARGO_DELIVERED[3]);
		assertEquals(197, DBTableID.PortTask.ID);
		assertEquals(0, DBTableID.PortTask.COL_TASK_ID);
	}

	@Test
	public void nothingIsReadBeforeTheSessionSettles()
	{
		varbits.put(VarbitID.PORT_TASK_SLOT_0_ID, SPICES);
		reader.onSessionResumed(0);
		assertNull("every varbit reads zero after a login, which is not five empty slots", reader.wire(14_999));
		assertNull(reader.latest());
		assertEquals(PortTaskReader.COURIER, reader.wire(15_000).slots.get(0).state);
	}

	@Test
	public void aCourierIsFoundByValueAndCarriesItsOwnSlotsCounters()
	{
		settle();
		varbits.put(VarbitID.PORT_TASK_SLOT_1_ID, SPICES);
		varbits.put(VarbitID.PORT_TASK_SLOT_1_CARGO_TAKEN, 1);
		varbits.put(VarbitID.PORT_TASK_SLOT_0_CARGO_DELIVERED, 5);
		List<PortTaskReader.Slot> slots = reader.read(20_000);
		PortTaskReader.Slot spices = slots.get(1);
		assertEquals(PortTaskReader.COURIER, spices.state);
		assertEquals(Integer.valueOf(SPICES), spices.taskId);
		assertEquals("Port Sarim spice delivery", spices.name);
		assertEquals("The Pandemonium", spices.from);
		assertEquals("Port Sarim", spices.to);
		assertEquals(1, spices.needed);
		assertEquals(1, spices.taken);
		assertEquals("slot 0's counter is not slot 1's", 0, spices.delivered);
	}

	@Test
	public void aBountyReadsThePartsStillOwedFromItsSlotsCounter()
	{
		settle();
		varbits.put(VarbitID.PORT_TASK_SLOT_2_ID, TERNS);
		varbits.put(14819, 9);
		PortTaskReader.Slot terns = reader.read(20_000).get(2);
		assertEquals(PortTaskReader.BOUNTY, terns.state);
		assertEquals(9, terns.remaining);
		assertEquals(15, terns.needed);
		assertEquals("Tern feather", terns.part);
		assertEquals("the board the parts are handed in at", "Port Sarim", terns.to);
	}

	@Test
	public void emptyLockedAndUnreadableAreThreeDifferentAnswers()
	{
		settle();
		varbits.put(VarbitID.PORT_TASK_SLOT_1_ID, NO_TASK);
		varbits.put(VarbitID.PORT_TASK_SLOT_2_ID, LOCKED);
		varbits.put(VarbitID.PORT_TASK_SLOT_3_ID, 12345);
		varbits.put(VarbitID.PORT_TASK_SLOT_4_ID, 700);
		List<PortTaskReader.Slot> slots = reader.read(20_000);
		assertEquals("zero is an empty slot", PortTaskReader.EMPTY, slots.get(0).state);
		assertEquals("the game's No Task row is an empty slot", PortTaskReader.EMPTY, slots.get(1).state);
		assertEquals(PortTaskReader.LOCKED, slots.get(2).state);
		assertEquals("a value no row holds is not a free slot", PortTaskReader.UNKNOWN, slots.get(3).state);
		assertEquals("a task type this reader does not know is not a free slot", PortTaskReader.UNKNOWN, slots.get(4).state);
	}

	@Test
	public void aTableThatCannotBeReadIsUnknownRatherThanEmpty()
	{
		settle();
		varbits.put(VarbitID.PORT_TASK_SLOT_0_ID, SPICES);
		failure = new IllegalStateException("database not loaded");
		assertEquals(PortTaskReader.UNKNOWN, reader.read(20_000).get(0).state);
	}

	@Test
	public void theWireCarriesStatesIdsAndCountersAndNothingTheTablesSay()
	{
		settle();
		varbits.put(VarbitID.PORT_TASK_SLOT_0_ID, SPICES);
		varbits.put(VarbitID.PORT_TASK_SLOT_0_CARGO_TAKEN, 1);
		varbits.put(VarbitID.PORT_TASK_SLOT_1_ID, TERNS);
		varbits.put(14663, 9);
		CompanionSnapshot.PortTasks wire = reader.wire(20_000);
		assertEquals(5, wire.slots.size());
		for (int i = 0; i < 5; i++)
		{
			assertEquals(i, wire.slots.get(i).slot);
		}
		assertEquals(Integer.valueOf(1), wire.slots.get(0).taken);
		assertEquals(Integer.valueOf(0), wire.slots.get(0).delivered);
		assertNull(wire.slots.get(0).remaining);
		assertEquals(Integer.valueOf(9), wire.slots.get(1).remaining);
		assertNull(wire.slots.get(2).taskId);
		String json = new Gson().toJson(wire);
		assertFalse(json, json.contains("Port Sarim"));
		assertFalse(json, json.contains("Tern"));
	}

	@Test
	public void aHopForgetsTheReadingUntilTheNewSessionSettles()
	{
		settle();
		varbits.put(VarbitID.PORT_TASK_SLOT_0_ID, SPICES);
		reader.read(20_000);
		assertTrue(reader.latest().get(0).held());
		reader.onSessionEnded();
		assertNull("the sidebar stops showing a reading the session no longer stands behind", reader.latest());
		reader.onSessionResumed(30_000);
		assertNull(reader.wire(40_000));
		assertEquals(PortTaskReader.COURIER, reader.wire(45_000).slots.get(0).state);
	}

	@Test
	public void withdrawingSharingHidesTheReading()
	{
		settle();
		reader.read(20_000);
		reader.forget();
		assertNull(reader.latest());
	}

	private void settle()
	{
		reader.onSessionResumed(0);
	}

	private void dock(int row, int id, String name)
	{
		tableOf.put(row, DBTableID.SailingDock.ID);
		cell(row, DBTableID.SailingDock.COL_DOCK_ID, id);
		cell(row, DBTableID.SailingDock.COL_NICE_NAME, name);
	}

	private void task(int row, int id, String name, int type)
	{
		tableOf.put(row, DBTableID.PortTask.ID);
		cell(row, DBTableID.PortTask.COL_TASK_ID, id);
		cell(row, DBTableID.PortTask.COL_NAME, name);
		cell(row, DBTableID.PortTask.COL_TASK_TYPE, type);
	}

	/** One column of a row; a tuple column keeps one value per tuple index, as the client returns it. */
	private void cell(int row, int column, Object... values)
	{
		rows.computeIfAbsent(row, r -> new HashMap<>()).put(column, values);
	}

	private List<Integer> rowsByValue(int table, int column, Object value)
	{
		List<Integer> found = new ArrayList<>();
		for (Map.Entry<Integer, Map<Integer, Object[]>> row : rows.entrySet())
		{
			Object[] cell = row.getValue().get(column);
			if (Objects.equals(tableOf.get(row.getKey()), table) && cell != null && Objects.equals(cell[0], value))
			{
				found.add(row.getKey());
			}
		}
		return found;
	}

	private Object[] field(int row, int column, int tupleIndex)
	{
		Map<Integer, Object[]> columns = rows.get(row);
		Object[] cell = columns == null ? null : columns.get(column);
		if (cell == null || tupleIndex >= cell.length)
		{
			return new Object[0];
		}
		return new Object[] {cell[tupleIndex]};
	}

	private static <T> T named(Class<T> type, String name)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
		{
			if ("getName".equals(method.getName()))
			{
				return name;
			}
			throw new UnsupportedOperationException(method.getName());
		}));
	}
}
