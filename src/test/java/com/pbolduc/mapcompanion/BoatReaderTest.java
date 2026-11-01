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
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The boats, read the way the game's own scripts read them.
 *
 * <p>The fake database keeps row ids apart from the {@code dock_id} values they hold, so a reader
 * that handed a stored port to {@code getDBTableField} as a row would name some other port and fail
 * here — the mistake {@link SlayerTaskReaderTest} keeps its fake for too. Port Sarim's dock id is
 * zero, the value every varbit reads before the session settles, which is why the settle and the
 * ownership evidence are tested beside the join.
 */
public class BoatReaderTest
{
	private static final int PORT_SARIM = 0;
	private static final int CATHERBY = 6;
	private static final int PORT_KHAZARD = 10;

	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Map<Integer, Object>> rows = new HashMap<>();
	private RuntimeException varbitFailure;
	private boolean aboard;

	private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
		(proxy, method, args) ->
		{
			if ("getLocalLocation".equals(method.getName()))
			{
				/* A boat's tiles belong to its own world view; the world's is view 0. */
				return new LocalPoint(64, 64, aboard ? 3 : 0);
			}
			throw new UnsupportedOperationException(method.getName());
		});

	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
		(proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getLocalPlayer":
					return player;
				case "getVarbitValue":
					if (varbitFailure != null)
					{
						throw varbitFailure;
					}
					return varbits.getOrDefault((Integer) args[0], 0);
				case "getDBRowsByValue":
					assertEquals(DBTableID.SailingDock.ID, args[0]);
					assertEquals(DBTableID.SailingDock.COL_DOCK_ID, args[1]);
					List<Integer> found = new ArrayList<>();
					for (Map.Entry<Integer, Map<Integer, Object>> row : rows.entrySet())
					{
						if (Objects.equals(row.getValue().get(DBTableID.SailingDock.COL_DOCK_ID), args[3]))
						{
							found.add(row.getKey());
						}
					}
					return found;
				case "getDBTableField":
					Map<Integer, Object> row = rows.get((Integer) args[0]);
					if (row == null || !row.containsKey((Integer) args[1]))
					{
						throw new IllegalArgumentException("no column " + args[1] + " on row " + args[0]);
					}
					return new Object[] {row.get((Integer) args[1])};
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});

	private final BoatReader reader = new BoatReader(client);

	public BoatReaderTest()
	{
		dock(8587, PORT_SARIM, "Port Sarim");
		dock(8593, CATHERBY, "Catherby");
		/* Row 10 is Catherby's value's neighbour: what a reader that took a value for a row would find. */
		dock(PORT_KHAZARD, 99, "The Pandemonium");
		dock(8597, PORT_KHAZARD, "Port Khazard");
		reader.onSessionResumed(0);
	}

	/**
	 * The ids the evidence names, against the client's own. The plan and the evidence file cite these
	 * numbers; if a client release moves one, this says so before a reading goes quietly wrong.
	 */
	@Test
	public void readsTheVariablesAndTableTheEvidenceNames()
	{
		assertEquals(19260, VarbitID.SAILING_BOAT_1_PORT);
		assertEquals(19298, VarbitID.SAILING_BOAT_2_PORT);
		assertEquals(19336, VarbitID.SAILING_BOAT_3_PORT);
		assertEquals(19374, VarbitID.SAILING_BOAT_4_PORT);
		assertEquals(19270, VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS);
		assertEquals(19308, VarbitID.SAILING_BOAT_2_TELEPORT_FOCUS);
		assertEquals(19346, VarbitID.SAILING_BOAT_3_TELEPORT_FOCUS);
		assertEquals(VarbitID.SAILING_BOAT_5_PORT, BoatReader.PORT[4]);
		assertEquals(VarbitID.SAILING_BOAT_5_TELEPORT_FOCUS, BoatReader.FOCUS[4]);
		assertEquals(VarbitID.SAILING_BOAT_3_OWNED, BoatReader.OWNED[2]);
		assertEquals(VarbitID.SAILING_BOAT_4_NAME_2, BoatReader.NAME[3][1]);
		/* The map's port-task dump reads the same table by this id (`PortTaskExport`, table 194). */
		assertEquals(194, DBTableID.SailingDock.ID);
	}

	@Test
	public void namesEachBoatsPortByTheRowThatHoldsItsValue()
	{
		boat(1, CATHERBY, 2);
		varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
		boat(3, PORT_KHAZARD, 1);
		varbits.put(VarbitID.SAILING_BOAT_3_NAME_2, 4);
		List<CompanionSnapshot.Boat> boats = reader.read(15_000);
		assertEquals(2, boats.size());
		assertBoat(boats.get(0), 1, CATHERBY, "Catherby", "greater");
		assertBoat(boats.get(1), 3, PORT_KHAZARD, "Port Khazard", "teleport");
	}

	@Test
	public void believesNothingUntilTheSessionHasSettled()
	{
		boat(1, CATHERBY, 2);
		varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
		assertNull("every varbit reads zero, Port Sarim's dock id, for a moment after a login", reader.read(14_999));
		assertEquals(1, reader.read(15_000).size());
		reader.onSessionEnded();
		assertNull("a hop or a logout starts the wait again", reader.read(16_000));
		reader.onSessionResumed(20_000);
		assertNull(reader.read(34_999));
		assertEquals(1, reader.read(35_000).size());
	}

	@Test
	public void aBoatAtPortSarimIsABoatWhenAnythingElseSaysSo()
	{
		/* Port Sarim's value is zero: the owned flag, or a name part, is what says a boat is there. */
		varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
		assertBoat(reader.read(15_000).get(0), 1, PORT_SARIM, "Port Sarim", "none");
		varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 0);
		varbits.put(VarbitID.SAILING_BOAT_1_NAME_1, 2);
		assertBoat(reader.read(15_000).get(0), 1, PORT_SARIM, "Port Sarim", "none");
	}

	@Test
	public void leavesAnEmptySlotOutAndNeverClaimsNoBoats()
	{
		assertNull("all zeros after the settle is not proof of owning nothing", reader.read(15_000));
		boat(2, CATHERBY, 0);
		List<CompanionSnapshot.Boat> boats = reader.read(15_000);
		assertEquals(1, boats.size());
		assertEquals(2, boats.get(0).slot);
	}

	@Test
	public void aLostCapsizedOrBottledBoatIsAtNoDock()
	{
		boat(1, BoatReader.LOST, 0);
		boat(2, BoatReader.CAPSIZED, 1);
		boat(3, BoatReader.BOTTLED, 2);
		List<CompanionSnapshot.Boat> boats = reader.read(15_000);
		assertEquals(3, boats.size());
		for (CompanionSnapshot.Boat boat : boats)
		{
			assertNull(boat.dock);
			assertNull(boat.port);
		}
		assertEquals("greater", boats.get(2).focus);
	}

	@Test
	public void aValueTheTableCannotNameIsSentWithoutAName()
	{
		/* The game's interface shows Port Sarim here; the map is told it cannot place the boat. */
		boat(1, 77, 0);
		assertBoat(reader.read(15_000).get(0), 1, 77, null, "none");
		dock(8600, CATHERBY, "Catherby again");
		boat(1, CATHERBY, 0);
		assertBoat(reader.read(15_000).get(0), 1, CATHERBY, null, "none");
	}

	@Test
	public void aFocusTheGamesCheckDoesNotNameIsUnknown()
	{
		boat(1, CATHERBY, 3);
		assertNull("only 2 passes the greater-focus check as the script writes it", reader.read(15_000).get(0).focus);
		assertEquals("none", BoatReader.focusFrom(0));
		assertEquals("teleport", BoatReader.focusFrom(1));
		assertEquals("greater", BoatReader.focusFrom(2));
		assertNull(BoatReader.focusFrom(-1));
	}

	@Test
	public void anUnreadableSlotIsNoReadingAtAll()
	{
		boat(1, CATHERBY, 2);
		varbitFailure = new IllegalStateException("not loaded");
		assertNull(reader.read(15_000));
	}

	@Test
	public void sendsNothingWhileStandingOnADeck()
	{
		boat(1, CATHERBY, 2);
		aboard = true;
		assertNull("a boat at sea may still read the port it left", reader.read(15_000));
		aboard = false;
		assertEquals(1, reader.read(15_000).size());
	}

	private void dock(int row, int dockId, String name)
	{
		Map<Integer, Object> columns = new HashMap<>();
		columns.put(DBTableID.SailingDock.COL_DOCK_ID, dockId);
		columns.put(DBTableID.SailingDock.COL_NICE_NAME, name);
		rows.put(row, columns);
	}

	private void boat(int slot, int port, int focus)
	{
		varbits.put(BoatReader.PORT[slot - 1], port);
		varbits.put(BoatReader.FOCUS[slot - 1], focus);
	}

	private static void assertBoat(CompanionSnapshot.Boat boat, int slot, Integer dock, String port, String focus)
	{
		assertEquals(slot, boat.slot);
		assertEquals(dock, boat.dock);
		assertEquals(port, boat.port);
		assertEquals(focus, boat.focus);
	}
}
