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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * The two or three tasks Mortimer offers while the player chooses, each with its mortifier.
 *
 * <p>Read as the game's own choice interface reads them (script 9739 in the cache): each offer is a
 * task varbit holding the {@code SlayerTask} table's id column, found by value, and a modifier id
 * that script finds by value in {@code SlayerModifiers}, whose rows 1 to 5 are points, quantity,
 * clue, superior and xp. The value varbit and its negative flag are the mortifier's amount. The ids
 * are in game-reads.txt; Efficient Mortimer on the Plugin Hub reads the same ones.
 *
 * <p>Only an offer seen on that interface counts, and only while every task it names is a word on
 * it: the varbits may well keep an old offer after it was taken. Once seen, it is held while the
 * varbits stay as they were and the player has no task, so closing the interface to look at the map
 * keeps it; a task taken (its kill count above zero), a new roll or a new session drops it.
 */
@Singleton
public class MortimerOfferReader extends SettlingReader
{
	private static final List<String[]> SLOTS = GameReads.rows("mortimer");
	private static final List<String[]> MODIFIERS = GameReads.rows("mortifier");

	private final Client client;
	private int[] heldState;
	private List<CompanionSnapshot.MortimerTask> held;

	@Inject
	MortimerOfferReader(Client client)
	{
		this.client = client;
	}

	@Override
	public void onSessionEnded()
	{
		super.onSessionEnded();
		held = null;
	}

	/** Each tick while the Slayer task is shared: look at the interface, or keep what it showed. */
	public void observe(long nowMs)
	{
		if (!settle.settled(nowMs) || client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0)
		{
			held = null;
			return;
		}
		try
		{
			int[] state = new int[SLOTS.size() * 5];
			for (int slot = 0; slot < SLOTS.size(); slot++)
			{
				int[] ids = GameReads.ids(SLOTS.get(slot)[1]);
				for (int i = 0; i < 5; i++)
				{
					state[slot * 5 + i] = client.getVarbitValue(ids[i]);
				}
			}
			Widget content = client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT);
			if (content != null && !content.isHidden())
			{
				held = decode(state, words(content));
				heldState = state;
			}
			else if (!Arrays.equals(state, heldState))
			{
				held = null;
			}
		}
		catch (RuntimeException e)
		{
			held = null;
		}
	}

	@Nullable
	public List<CompanionSnapshot.MortimerTask> offer()
	{
		return held;
	}

	/** The offer, or null unless both first slots hold a task named on the interface and nothing is a boss. */
	@Nullable
	private List<CompanionSnapshot.MortimerTask> decode(int[] state, String shown)
	{
		List<CompanionSnapshot.MortimerTask> offer = new ArrayList<>();
		for (int slot = 0; slot < SLOTS.size(); slot++)
		{
			int task = state[slot * 5];
			if (task == 0 && slot == 2)
			{
				break;
			}
			String name = SlayerTaskNames.forTaskId(client, task);
			String modifier = null;
			for (String[] row : MODIFIERS)
			{
				modifier = row[0].equals(Integer.toString(state[slot * 5 + 1])) ? row[1] : modifier;
			}
			if (task == 0 || state[slot * 5 + 4] != 0 || name == null || modifier == null
				|| !shown.contains(name.toLowerCase(Locale.ROOT)))
			{
				return null;
			}
			CompanionSnapshot.MortimerTask offered = new CompanionSnapshot.MortimerTask();
			offered.taskId = task;
			offered.taskName = name;
			offered.mortifier = modifier;
			offered.value = state[slot * 5 + 3] == 1 ? -state[slot * 5 + 2] : state[slot * 5 + 2];
			offer.add(offered);
		}
		return offer;
	}

	/** Every word the interface shows, lower case, without tags. */
	private static String words(Widget content)
	{
		StringBuilder words = new StringBuilder();
		Widget[] children = content.getDynamicChildren();
		for (Widget child : children == null ? new Widget[0] : children)
		{
			if (child != null && child.getText() != null)
			{
				words.append(Text.removeTags(child.getText()).replace('\u00a0', ' ')).append('\n');
			}
		}
		return words.toString().toLowerCase(Locale.ROOT);
	}
}
