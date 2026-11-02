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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;

/**
 * The five Sailing port task slots, and how far each task has got.
 *
 * <p><b>Where the encoding comes from.</b> Not from a recorded session: from two plugins already on
 * the Plugin Hub and the game's own client script, which agree. A slot varbit holds the value of
 * the {@code PortTask} table's task-id column (LlemonDuck's {@code CourierTaskTracker} and the
 * game's {@code [proc,script9095]} both find the row by that value), so the row is found by value
 * with {@code getDBRowsByValue} and never read as a row id. A courier's crates collected and
 * delivered are two counters per slot ({@code CourierTaskUtil}'s named lists); a bounty's parts are
 * one counter per slot, varbits 14662, 14663, 14819, 15370 and 15397 for slots zero to four, which
 * count the parts still owed (nucleon's {@code PortTaskTrigger} and its handler). The pinned
 * commits are in the map's {@code docs/review-evidence/sailing-reader-sources-2026-10-30.md}.
 * {@code GameIdsTest} holds every id here to the client's gameval constants.
 *
 * <p><b>Unknown is not empty.</b> Each slot is one of five states, and only two of them are about
 * the absence of a task: {@code empty} (the slot reads zero, or the game's own "No Task" row) and
 * {@code locked} (the "Locked task slot" row, a slot not yet bought). A value whose row the client
 * cannot find, a row of a type this reader does not know, or a read that throws is
 * {@code unknown} — said as such, so the map does not draw a held task as a free slot. And for a
 * moment after a login or a hop every varbit reads zero, which would be five confident empty
 * slots: {@link SessionSettle} holds the whole reading back until the session has settled, and a
 * reader that cannot know yet sends nothing. Through a hop the sidebar alone keeps showing the
 * reading from before it ({@link #shown}).
 *
 * <p><b>What leaves the machine.</b> {@link #wire} sends a slot's state, the task id and the
 * game's own counters, and how many crates the hold of the boat last stood on carries
 * ({@link CargoHolds}) — nothing else. The names, ports and crate counts in {@link Slot} are read
 * from the client's tables for the sidebar and stay here; the map has the same table.
 */
@Singleton
public class PortTaskReader
{
	/** The slot varbits, slot 0 first. Each holds a task-id column value. */
	static final int[] SLOT_IDS = {
		VarbitID.PORT_TASK_SLOT_0_ID, VarbitID.PORT_TASK_SLOT_1_ID, VarbitID.PORT_TASK_SLOT_2_ID,
		VarbitID.PORT_TASK_SLOT_3_ID, VarbitID.PORT_TASK_SLOT_4_ID};
	/** Crates collected for a courier task, per slot. */
	static final int[] CARGO_TAKEN = {
		VarbitID.PORT_TASK_SLOT_0_CARGO_TAKEN, VarbitID.PORT_TASK_SLOT_1_CARGO_TAKEN, VarbitID.PORT_TASK_SLOT_2_CARGO_TAKEN,
		VarbitID.PORT_TASK_SLOT_3_CARGO_TAKEN, VarbitID.PORT_TASK_SLOT_4_CARGO_TAKEN};
	/** Crates delivered for a courier task, per slot. */
	static final int[] CARGO_DELIVERED = {
		VarbitID.PORT_TASK_SLOT_0_CARGO_DELIVERED, VarbitID.PORT_TASK_SLOT_1_CARGO_DELIVERED,
		VarbitID.PORT_TASK_SLOT_2_CARGO_DELIVERED, VarbitID.PORT_TASK_SLOT_3_CARGO_DELIVERED,
		VarbitID.PORT_TASK_SLOT_4_CARGO_DELIVERED};
	/**
	 * Parts still owed for a bounty task, per slot. The client numbers them one to five and the
	 * slots zero to four; nucleon's mapping pairs counter 1 with slot 0, and so on.
	 */
	static final int[] BOUNTY_REMAINING = {
		VarbitID.BOUNTY_TASK_ITEM_COUNTER_1, VarbitID.BOUNTY_TASK_ITEM_COUNTER_2, VarbitID.BOUNTY_TASK_ITEM_COUNTER_3,
		VarbitID.BOUNTY_TASK_ITEM_COUNTER_4, VarbitID.BOUNTY_TASK_ITEM_COUNTER_5};

	/**
	 * The task-type column's two kinds. Not named by the client: LlemonDuck's {@code TaskType} is
	 * {@code DELIVERY, BOUNTY} by ordinal, and the map's export of the whole table agrees with the
	 * wiki on every one of its 602 tasks under the same reading.
	 */
	static final int TYPE_COURIER = 0;
	static final int TYPE_BOUNTY = 1;

	static final String COURIER = "courier";
	static final String BOUNTY = "bounty";
	static final String EMPTY = "empty";
	static final String LOCKED = "locked";
	static final String UNKNOWN = "unknown";

	/** One slot as the sidebar draws it. Immutable, so the panel can read it off the client thread. */
	static final class Slot
	{
		final int slot;
		final String state;
		@Nullable
		final Integer taskId;
		/** The game's own task name, "Port Sarim spice delivery". */
		@Nullable
		final String name;
		/** Courier: where the crates wait and where they go. Bounty: the board to hand in at, as {@link #to}. */
		@Nullable
		final String from;
		@Nullable
		final String to;
		/** Courier: crates in the task. Bounty: parts asked for. */
		final int needed;
		final int taken;
		final int delivered;
		final int remaining;
		/** Bounty: the part's name, where the client names it. */
		@Nullable
		final String part;

		Slot(int slot, String state, @Nullable Integer taskId, @Nullable String name, @Nullable String from,
			@Nullable String to, int needed, int taken, int delivered, int remaining, @Nullable String part)
		{
			this.slot = slot;
			this.state = state;
			this.taskId = taskId;
			this.name = name;
			this.from = from;
			this.to = to;
			this.needed = needed;
			this.taken = taken;
			this.delivered = delivered;
			this.remaining = remaining;
			this.part = part;
		}

		private static Slot of(int slot, String state, int taskId, Task task, int taken, int delivered, int remaining)
		{
			return new Slot(slot, state, taskId, task.name, task.from, task.to, task.needed, taken, delivered, remaining,
				task.part);
		}

		static Slot plain(int slot, String state)
		{
			return new Slot(slot, state, null, null, null, null, 0, 0, 0, 0, null);
		}

		boolean held()
		{
			return COURIER.equals(state) || BOUNTY.equals(state);
		}
	}

	/** What the client's tables say about one task id; read once a session and kept. */
	private static final class Task
	{
		final int row;
		final int type;
		@Nullable
		final String name;
		@Nullable
		final String from;
		@Nullable
		final String to;
		final int needed;
		@Nullable
		final String part;

		Task(int row, int type, @Nullable String name, @Nullable String from, @Nullable String to, int needed,
			@Nullable String part)
		{
			this.row = row;
			this.type = type;
			this.name = name;
			this.from = from;
			this.to = to;
			this.needed = needed;
			this.part = part;
		}
	}

	private final Client client;
	private final CargoHolds holds;
	private final SessionSettle settle = new SessionSettle(15_000L);
	/** Task id to its row, for this session's client. Client thread only. */
	private final Map<Integer, Task> tasks = new HashMap<>();
	/** The last settled reading, for the sidebar; null before one. */
	private volatile List<Slot> latest;
	/** The hold's crates as of that reading; null where no boat with a hold was stood on. */
	private volatile Integer latestHold;
	/*
	 * The reading before a hop, which the sidebar shows until the new session settles. Kept beside
	 * latest rather than in it: latest is what is sent and what ticks a run, and through a hop's
	 * window neither may use a reading the session no longer stands behind. The same character holds
	 * the same tasks across a hop, so the card need not vanish for those seconds.
	 */
	private volatile List<Slot> kept;
	private volatile Integer keptHold;

	@Inject
	PortTaskReader(Client client, CargoHolds holds)
	{
		this.client = client;
		this.holds = holds;
	}

	public void onSessionResumed(long nowMs)
	{
		settle.resumed(nowMs);
	}

	/** Logged out, another character, or the plugin stopping: forget the reading, the sidebar's copy too. */
	public void onSessionEnded()
	{
		end();
		kept = null;
		keptHold = null;
	}

	/**
	 * A world hop: the reading is withheld as at a logout, since every varbit is about to read zero,
	 * and the sidebar keeps the last settled one until the new session settles. Client thread.
	 */
	public void onHop()
	{
		List<Slot> held = latest;
		if (held != null)
		{
			kept = held;
			keptHold = latestHold;
		}
		end();
	}

	private void end()
	{
		settle.ended();
		tasks.clear();
		holds.reset();
		latest = null;
		latestHold = null;
	}

	/** Sharing withdrawn: the sidebar stops showing the last reading, and the session's clock runs on. */
	public void forget()
	{
		latest = null;
		latestHold = null;
		kept = null;
		keptHold = null;
	}

	/**
	 * The five slots, or null while the session is too young to believe. Client thread only.
	 * Also kept for {@link #latest}, which the sidebar reads.
	 */
	@Nullable
	public List<Slot> read(long nowMs)
	{
		if (!settle.settled(nowMs))
		{
			latest = null;
			latestHold = null;
			return null;
		}
		standing();
		List<Slot> slots = new ArrayList<>(SLOT_IDS.length);
		for (int i = 0; i < SLOT_IDS.length; i++)
		{
			slots.add(slot(i));
		}
		List<Slot> reading = Collections.unmodifiableList(slots);
		latestHold = hold();
		latest = reading;
		kept = null;
		keptHold = null;
		return reading;
	}

	/** Crates the remembered boat's hold carries; read after {@link #read} on the same thread. */
	@Nullable
	Integer latestHold()
	{
		return latestHold;
	}

	/** The last settled reading, from any thread; null before one or after the session ended. */
	@Nullable
	List<Slot> latest()
	{
		return latest;
	}

	/**
	 * What the sidebar shows, from any thread: the last settled reading, or through a hop's unsettled
	 * window the one before the hop. Never sent, and never what ticks a run: {@link #latest} is.
	 */
	@Nullable
	List<Slot> shown()
	{
		List<Slot> now = latest();
		return now != null ? now : kept;
	}

	/** The hold as of the reading {@link #shown} gives. */
	@Nullable
	Integer shownHold()
	{
		return latest() != null ? latestHold() : keptHold;
	}

	/** What the snapshot carries: each slot's state, task id and counters. Client thread only. */
	@Nullable
	public CompanionSnapshot.PortTasks wire(long nowMs)
	{
		List<Slot> slots = read(nowMs);
		if (slots == null)
		{
			return null;
		}
		CompanionSnapshot.PortTasks out = new CompanionSnapshot.PortTasks();
		out.slots = new ArrayList<>(slots.size());
		for (Slot slot : slots)
		{
			CompanionSnapshot.PortTaskSlot w = new CompanionSnapshot.PortTaskSlot();
			w.slot = slot.slot;
			w.state = slot.state;
			if (COURIER.equals(slot.state))
			{
				w.taskId = slot.taskId;
				w.taken = slot.taken;
				w.delivered = slot.delivered;
			}
			else if (BOUNTY.equals(slot.state))
			{
				w.taskId = slot.taskId;
				w.remaining = slot.remaining;
			}
			out.slots.add(w);
		}
		out.hold = latestHold;
		return out;
	}

	/** Which boat the player stands on, if any, for {@link CargoHolds}. */
	private void standing()
	{
		Player local = client.getLocalPlayer();
		WorldView view = local == null ? null : local.getWorldView();
		if (view != null)
		{
			holds.standingIn(view.getId(), view.isTopLevel());
		}
	}

	/** The remembered hold's crates, at an Ultimate Ironman's limit where the account is one. */
	@Nullable
	private Integer hold()
	{
		try
		{
			return holds.capacity("uim".equals(AccountTypes.name(client.getVarbitValue(AccountTypes.VARBIT))));
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private Slot slot(int index)
	{
		try
		{
			int value = client.getVarbitValue(SLOT_IDS[index]);
			if (value == 0)
			{
				return Slot.plain(index, EMPTY);
			}
			Task task = task(value);
			if (task == null)
			{
				return Slot.plain(index, UNKNOWN);
			}
			if (task.row == DBTableID.PortTask.Row.PORT_TASK_BLANK)
			{
				return Slot.plain(index, EMPTY);
			}
			if (task.row == DBTableID.PortTask.Row.PORT_TASK_LOCKED)
			{
				return Slot.plain(index, LOCKED);
			}
			if (task.type == TYPE_COURIER)
			{
				return Slot.of(index, COURIER, value, task,
					client.getVarbitValue(CARGO_TAKEN[index]), client.getVarbitValue(CARGO_DELIVERED[index]), 0);
			}
			if (task.type == TYPE_BOUNTY)
			{
				return Slot.of(index, BOUNTY, value, task, 0, 0, client.getVarbitValue(BOUNTY_REMAINING[index]));
			}
			return Slot.plain(index, UNKNOWN);
		}
		catch (RuntimeException | LinkageError e)
		{
			/* A table this client cannot read gives no answer, and no answer is not "empty". */
			return Slot.plain(index, UNKNOWN);
		}
	}

	/** The row whose task-id column holds {@code value}, and what it says; null where none does. */
	@Nullable
	private Task task(int value)
	{
		Task held = tasks.get(value);
		if (held != null)
		{
			return held;
		}
		List<Integer> rows = client.getDBRowsByValue(DBTableID.PortTask.ID, DBTableID.PortTask.COL_TASK_ID, 0, value);
		if (rows == null || rows.isEmpty())
		{
			return null;
		}
		int row = rows.get(0);
		Object type = first(client.getDBTableField(row, DBTableID.PortTask.COL_TASK_TYPE, 0));
		if (!(type instanceof Integer))
		{
			return null;
		}
		Task task;
		if ((Integer) type == TYPE_COURIER)
		{
			task = new Task(row, TYPE_COURIER, string(client.getDBTableField(row, DBTableID.PortTask.COL_NAME, 0)),
				dock(client.getDBTableField(row, DBTableID.PortTask.COL_CARGO_PORT, 0)),
				dock(client.getDBTableField(row, DBTableID.PortTask.COL_ENDING_PORT, 0)),
				integer(client.getDBTableField(row, DBTableID.PortTask.COL_CARGO, 1)), null);
		}
		else if ((Integer) type == TYPE_BOUNTY)
		{
			task = new Task(row, TYPE_BOUNTY, string(client.getDBTableField(row, DBTableID.PortTask.COL_NAME, 0)),
				null, dock(client.getDBTableField(row, DBTableID.PortTask.COL_STARTING_PORT, 0)),
				integer(client.getDBTableField(row, DBTableID.PortTask.COL_BOUNTY_OBJECT_AMOUNT, 0)),
				itemName(first(client.getDBTableField(row, DBTableID.PortTask.COL_BOUNTY_OBJECT, 0))));
		}
		else
		{
			/* A kind this reader does not know: the slot says unknown rather than guess at it. */
			task = new Task(row, (Integer) type, null, null, null, 0, null);
		}
		tasks.put(value, task);
		return task;
	}

	/** A port column holds a {@code SailingDock} row; its name is that row's own column. */
	@Nullable
	private String dock(@Nullable Object[] values)
	{
		Object row = first(values);
		return row instanceof Integer
			   ? string(client.getDBTableField((Integer) row, DBTableID.SailingDock.COL_NICE_NAME, 0))
			   : null;
	}

	@Nullable
	private String itemName(@Nullable Object id)
	{
		if (!(id instanceof Integer))
		{
			return null;
		}
		ItemComposition item = client.getItemDefinition((Integer) id);
		return item == null ? null : item.getName();
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

	private static int integer(@Nullable Object[] values)
	{
		Object value = first(values);
		return value instanceof Integer ? (Integer) value : 0;
	}
}
