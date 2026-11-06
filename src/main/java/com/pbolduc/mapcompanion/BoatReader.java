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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;

/**
 * Where each of the player's boats is moored, and what teleport focus it carries.
 *
 * <p>The map starts a voyage only at a mooring one of these boats is at, and offers Teleport to
 * Boat only for a boat whose focus is a greater one. Both are claims about the account, so this
 * reader says nothing it has not read: a reading it cannot vouch for is null, which the map reads
 * as "not told" and keeps its whole docking network, never as "owns no boat".
 *
 * <p><b>The encodings are the game's own, from its scripts.</b> A slot's stored port is a
 * {@code sailing_dock:dock_id} column value, joined to its row by value as the game's dock lookup
 * (script 9094) does, and the row's {@code nice_name} is the port's name. Values 253, 254 and 255
 * are lost, capsized and bottled, which the script answers with no dock. Where the lookup finds no
 * row or several, the game's interface falls back to Port Sarim; this does not, and sends the value
 * with no name, which the map reads as a reading it cannot place. The focus is one varbit per boat
 * (script 9081): zero none, one the teleport focus (object 59715), two the greater one (59716),
 * which is what the boat-selection check asks before it lets a player teleport there. Any other
 * value is unknown. The pinned sources are in the map's
 * {@code docs/review-evidence/sailing-reader-sources-2026-10-30.md}.
 *
 * <p><b>Empty, unreadable and unsettled are three different things.</b> For a few seconds after a
 * login or a hop every varbit reads zero, and zero is Port Sarim's dock id, so {@link SessionSettle}
 * holds every reading back until the session has settled. After that, a slot with no sign of a boat
 * — its owned flag, its port and its three name parts all zero — is empty and left out. The owned
 * flag alone is not trusted to say so: the Hub's <i>Dude, Where's My Boat?</i> found it unreliable
 * and also counts a stored port or a name part, and so does this. A slot whose variables cannot be
 * read at all makes the whole reading null, because a list missing a boat that is really there
 * would refuse the routes that start at it. A reading that finds no boat in any slot is null too:
 * a settled zero proves the variables were sent, not that the account owns nothing, so "no boats"
 * is never claimed. The boats' names are read only as evidence of a boat and never sent.
 *
 * <p><b>Not while aboard.</b> Nothing here says what a boarded boat's stored port holds while it is
 * at sea. Until the live check in {@code docs/live-checks.md} has watched it, a player standing on a
 * deck sends no reading, so a boat sailing away from Port Sarim is never reported moored there.
 *
 * <p>Client thread only. Off unless the player shares it; {@link DestinationRoute} reads it locally
 * either way to follow a voyage, which sends nothing.
 */
@Singleton
public class BoatReader
{
	/** Five boats at most, the n of {@code SAILING_BOAT_n_*}. */
	static final int SLOTS = 5;
	/** Stored-port values the game's dock lookup answers with no dock. */
	static final int LOST = 253;
	static final int CAPSIZED = 254;
	static final int BOTTLED = 255;

	static final int[] PORT = {
		VarbitID.SAILING_BOAT_1_PORT, VarbitID.SAILING_BOAT_2_PORT, VarbitID.SAILING_BOAT_3_PORT,
		VarbitID.SAILING_BOAT_4_PORT, VarbitID.SAILING_BOAT_5_PORT,
	};
	static final int[] FOCUS = {
		VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_2_TELEPORT_FOCUS,
		VarbitID.SAILING_BOAT_3_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_4_TELEPORT_FOCUS,
		VarbitID.SAILING_BOAT_5_TELEPORT_FOCUS,
	};
	static final int[] OWNED = {
		VarbitID.SAILING_BOAT_1_OWNED, VarbitID.SAILING_BOAT_2_OWNED, VarbitID.SAILING_BOAT_3_OWNED,
		VarbitID.SAILING_BOAT_4_OWNED, VarbitID.SAILING_BOAT_5_OWNED,
	};
	static final int[][] NAME = {
		{VarbitID.SAILING_BOAT_1_NAME_1, VarbitID.SAILING_BOAT_1_NAME_2, VarbitID.SAILING_BOAT_1_NAME_3},
		{VarbitID.SAILING_BOAT_2_NAME_1, VarbitID.SAILING_BOAT_2_NAME_2, VarbitID.SAILING_BOAT_2_NAME_3},
		{VarbitID.SAILING_BOAT_3_NAME_1, VarbitID.SAILING_BOAT_3_NAME_2, VarbitID.SAILING_BOAT_3_NAME_3},
		{VarbitID.SAILING_BOAT_4_NAME_1, VarbitID.SAILING_BOAT_4_NAME_2, VarbitID.SAILING_BOAT_4_NAME_3},
		{VarbitID.SAILING_BOAT_5_NAME_1, VarbitID.SAILING_BOAT_5_NAME_2, VarbitID.SAILING_BOAT_5_NAME_3},
	};
	/** The map's cap on a port's name; a longer one is no port it knows. */
	private static final int MAX_PORT = 48;

	private final Client client;
	private final SessionSettle settle = new SessionSettle(15_000L);

	@Inject
	BoatReader(Client client)
	{
		this.client = client;
	}

	/**
	 * Whether this varbit is one a boat's mooring or focus is read from — what a reader of the
	 * moorings asks before reading again, because each read looks every port up in the game's
	 * database.
	 */
	static boolean watches(int varbitId)
	{
		for (int i = 0; i < SLOTS; i++)
		{
			if (varbitId == PORT[i] || varbitId == FOCUS[i] || varbitId == OWNED[i])
			{
				return true;
			}
		}
		return false;
	}

	public void onSessionResumed(long nowMs)
	{
		settle.resumed(nowMs);
	}

	public void onSessionEnded()
	{
		settle.ended();
	}

	/**
	 * Each boat in slot order, or null when nothing can be said: before the session settles, while
	 * the player is aboard, when a slot cannot be read, and when no slot shows a boat.
	 */
	@Nullable
	public List<CompanionSnapshot.Boat> read(long nowMs)
	{
		if (!settle.settled(nowMs) || WorldTiles.aboard(client))
		{
			return null;
		}
		List<CompanionSnapshot.Boat> boats = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++)
		{
			int port = bit(PORT[i]);
			int focus = bit(FOCUS[i]);
			int owned = bit(OWNED[i]);
			int[] name = {bit(NAME[i][0]), bit(NAME[i][1]), bit(NAME[i][2])};
			if (port < 0 || focus < 0 || owned < 0 || name[0] < 0 || name[1] < 0 || name[2] < 0)
			{
				return null;
			}
			if (owned == 0 && port == 0 && name[0] == 0 && name[1] == 0 && name[2] == 0)
			{
				continue;
			}
			CompanionSnapshot.Boat boat = new CompanionSnapshot.Boat();
			boat.slot = i + 1;
			if (port != LOST && port != CAPSIZED && port != BOTTLED)
			{
				boat.dock = port;
				boat.port = dockName(port);
			}
			boat.focus = focusFrom(focus);
			boats.add(boat);
		}
		return boats.isEmpty() ? null : boats;
	}

	/** The focus varbit in the map's words, or null for a value the game's check does not name. */
	@Nullable
	static String focusFrom(int value)
	{
		switch (value)
		{
			case 0:
				return "none";
			case 1:
				return "teleport";
			case 2:
				return "greater";
			default:
				return null;
		}
	}

	/**
	 * The port's name, from the one {@code SailingDock} row whose {@code dock_id} holds this value, or
	 * null where there is not exactly one — the case the game's own interface papers over with Port
	 * Sarim, which would put a boat somewhere it is not.
	 */
	@Nullable
	private String dockName(int dockId)
	{
		try
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SailingDock.ID, DBTableID.SailingDock.COL_DOCK_ID, 0, dockId);
			if (rows == null || rows.size() != 1)
			{
				return null;
			}
			Object[] values = client.getDBTableField(rows.get(0), DBTableID.SailingDock.COL_NICE_NAME, 0);
			Object value = values == null || values.length == 0 ? null : values[0];
			String name = value instanceof String ? ((String) value).trim() : null;
			return name == null || name.isEmpty() || name.length() > MAX_PORT ? null : name;
		}
		catch (RuntimeException | LinkageError e)
		{
			return null;
		}
	}

	private int bit(int id)
	{
		try
		{
			return client.getVarbitValue(id);
		}
		catch (RuntimeException e)
		{
			return -1;
		}
	}
}
