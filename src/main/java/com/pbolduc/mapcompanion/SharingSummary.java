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

import java.util.*;
import lombok.AllArgsConstructor;
import static com.pbolduc.mapcompanion.SharingFields.Field;

/**
 * What the status card's second line says is shared, in the three switches Settings has.
 *
 * <p>A count of fields named nothing. So the line names what a player might want to check:
 * <i>Sharing everything</i>, <i>Sharing all but loot and milestones</i> when no more is off than on,
 * the one or two that are on when only those are, and a count otherwise. A switch a player chose
 * part of is <i>part of your account</i>, or the one field of it that is off, so a player who
 * shared all the step offered before it ticked the death reads <i>Sharing all but where you died</i>
 * as they always have. The tooltip gives each switch a line, wrapped to fit the screen: on, off, or
 * its fields that are on or off, whichever are fewer; and the name the group sees.
 *
 * <p>It speaks of what goes out, so pet rewards and collection additions count only with Notable drops
 * and levels, which is what the snapshot does. It never claims that membership sends nothing: the
 * line is about character sharing, which is all a switch chooses.
 */
final class SharingSummary
{
	private SharingSummary()
	{
	}

	/** One switch as the status card weighs it: its words in a sentence, and its fields going out. */
	@AllArgsConstructor
	private static final class Switch
	{
		final String title;
		final List<Field> fields;
		final List<Field> sent;

		boolean whole()
		{
			return sent.size() == fields.size();
		}

		boolean none()
		{
			return sent.isEmpty();
		}

		String inLine()
		{
			String name = title.toLowerCase(Locale.ROOT);
			return whole() || none() ? name : "part of " + name;
		}

		/** Among what is not shared: the one field of it that is off by its own name, as "where you died" is. */
		String missing()
		{
			List<Field> off = new ArrayList<>(fields);
			off.removeAll(sent);
			return off.size() == 1 && !none() ? off.get(0).label.toLowerCase(Locale.ROOT) : inLine();
		}
	}

	/** The line under the group's name: never more than two names, and never a count of fields. */
	static String line(MapCompanionConfig config, ConfigStore store)
	{
		List<Switch> switches = switches(config, store);
		List<String> on = new ArrayList<>();
		List<String> notOn = new ArrayList<>();
		int whole = 0;
		int part = 0;
		for (Switch one : switches)
		{
			if (!one.none())
			{
				on.add(one.inLine());
			}
			if (!one.whole())
			{
				notOn.add(one.missing());
			}
			whole += one.whole() ? 1 : 0;
			part += !one.whole() && !one.none() ? 1 : 0;
		}
		if (on.isEmpty())
		{
			return "Character sharing off";
		}
		if (notOn.isEmpty())
		{
			return "Sharing everything";
		}
		if (notOn.size() <= 2 && notOn.size() <= on.size())
		{
			return "Sharing all but " + String.join(", ", notOn);
		}
		if (on.size() <= 2)
		{
			return "Sharing " + String.join(", ", on);
		}
		return "Sharing " + whole + " of " + switches.size() + " choices" + (part == 0 ? "" : ", " + part + " in part");
	}

	/** The line's tooltip: each switch on a line of its own, in Settings' words, wrapped to fit. */
	static String list(MapCompanionConfig config, ConfigStore store)
	{
		StringBuilder list = new StringBuilder("<html><body width='220'>");
		boolean first = true;
		for (Switch one : switches(config, store))
		{
			list.append(first ? "" : "<br>").append(MapCompanionPanel.escape(one.title)).append(": ")
				.append(one.whole() ? "on" : one.none() ? "off" : MapCompanionPanel.escape(SharingControls.partly(one.fields, one.sent, "all but ", "")));
			first = false;
		}
		String alias = SnapshotBuilder.alias(config);
		list.append("<br>Shown as: ").append(config.shareName() ? "your character name" : alias.isEmpty() ? "Member" : MapCompanionPanel.escape(alias));
		return list.append("</body></html>").toString();
	}

	private static List<Switch> switches(MapCompanionConfig config, ConfigStore store)
	{
		List<Switch> switches = new ArrayList<>();
		for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
		{
			List<Field> sent = new ArrayList<>();
			for (Field field : SharingGroups.shared(toggle.fields, config, store))
			{
				if (goesOut(field, store))
				{
					sent.add(field);
				}
			}
			switches.add(new Switch(toggle.title, toggle.fields, sent));
		}
		return switches;
	}

	/** Pets and collection additions ride on Notable drops and levels: with it off, neither is sent. */
	private static boolean goesOut(Field field, ConfigStore store)
	{
		boolean riding = MapCompanionConfig.KEY_SHARE_PETS.equals(field.key) || MapCompanionConfig.KEY_SHARE_COLLECTION.equals(field.key);
		return !riding || store.on(MapCompanionConfig.KEY_SHARE_MILESTONES);
	}
}
