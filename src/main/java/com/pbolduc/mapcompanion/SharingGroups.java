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
import javax.annotation.Nullable;
import static com.pbolduc.mapcompanion.SharingFields.Field;
import static com.pbolduc.mapcompanion.SharingFields.Section;

/**
 * Each sharing group's switch in RuneLite's list, kept in step with the fields it stands for.
 *
 * <p>The fields are what is sent: {@link SnapshotBuilder} and the readers read them and nothing else,
 * so a switch is never a permission of its own. It is stored only because RuneLite draws a box from
 * a stored value, and a box that disagreed with what is shared would be a list that lies. So the two
 * are kept in step both ways, from the plugin's {@code ConfigChanged}: a switch written by a press in
 * RuneLite's list writes its fields, and a field written by the sidebar, the step after Connect or a
 * switch's own fan-out moves its switch. A group's switch reads on only while every field in it is on.
 *
 * <p><b>Which way wins is the newer press.</b> While the plugin runs, that is whichever was written.
 * At a start it cannot see the order, so {@link #settle} goes by what it can see. The first start
 * of this version on a profile writes each switch from its fields and never a field: they are the
 * player's own choices from before switches existed, and a switch written from them only describes
 * what is already shared, a group partly ticked included. After that, a switch can disagree with its
 * fields at a start only if it was pressed while the plugin was not listening — RuneLite's list shows
 * a disabled plugin's settings too — so the switch wins. Letting the fields win every time would bring
 * back on, and share, a group switched off there while the plugin was disabled.
 *
 * <p><b>A field an update adds starts off, even in a group whose switch is on.</b> The switch stood
 * for the fields it listed when the player pressed it, and a field added since was never in that
 * press. So a profile remembers which release's list it was last settled with ({@link #KEY_SETTLED}
 * holds the {@link #GENERATION}), and a start that finds a field newer than that leaves the field
 * as it is, off unless chosen, and moves the switch to what its fields now share instead.
 *
 * <p>Nothing loops. A switch writes only the fields that differ from it; a field going on never turns
 * its switch off, which is what lets a switch's fan-out finish; and a switch moved because of a field
 * agrees with its fields, so its own event writes nothing.
 */
final class SharingGroups
{
	/**
	 * Stored once a profile's choices have been read as groups. Deliberately not one of RuneLite's
	 * settings: RuneLite writes every setting's default into a profile it loads, and resets them all on
	 * Reset, and either would make an untouched profile look read. Never renamed.
	 */
	static final String KEY_SETTLED = "sharingGroups";

	/**
	 * Which release's field list {@link #KEY_SETTLED} was written with: 1 for the groups as they first
	 * shipped, one more each release that adds a field to a group. Never lowered.
	 */
	static final int GENERATION = 2;

	/** Each field added to a group after the first generation, with the generation that added it. */
	static final Map<String, Integer> ADDED = Collections.unmodifiableMap(added());

	private static Map<String, Integer> added()
	{
		Map<String, Integer> added = new HashMap<>();
		added.put("shareDailies", 2);
		added.put("shareSeaCharts", 2);
		return added;
	}

	private SharingGroups()
	{
	}

	/** Every field in the group on: the only state its switch shows as on. */
	static boolean on(Section section, MapCompanionConfig config, ConfigStore store)
	{
		for (Field field : section.fields)
		{
			if (!field.on(config, store))
			{
				return false;
			}
		}
		return true;
	}

	/** The group's fields that are on, in its own order: what a partly shared group names. */
	static List<Field> shared(Section section, MapCompanionConfig config, ConfigStore store)
	{
		List<Field> on = new ArrayList<>();
		for (Field field : section.fields)
		{
			if (field.on(config, store))
			{
				on.add(field);
			}
		}
		return on;
	}

	/** Every field of the group to {@code on}, writing only those that differ, as a press of its switch does. */
	static void choose(Section section, boolean on, MapCompanionConfig config, ConfigStore store)
	{
		for (Field field : section.fields)
		{
			if (field.on(config, store) != on)
			{
				store.set(field.key, Boolean.toString(on));
			}
		}
	}

	/**
	 * At a start, and when RuneLite changes profile: make each switch agree with its fields, the first
	 * time by writing the switch, and after that by writing the fields.
	 */
	static void settle(MapCompanionConfig config, ConfigStore store)
	{
		settle(config, store, ADDED, GENERATION);
	}

	/** {@link #settle(MapCompanionConfig, ConfigStore)} with the generations named, so a test can add a field. */
	static void settle(MapCompanionConfig config, ConfigStore store, Map<String, Integer> added, int generation)
	{
		String marker = store.get(KEY_SETTLED);
		boolean read = marker != null;
		int settledWith = marker == null ? 0 : marker.matches("[0-9]{1,4}") ? Integer.parseInt(marker) : 1;
		for (Section section : SharingFields.GROUPS)
		{
			boolean fields = on(section, config, store);
			boolean shown = section.read.test(config);
			if (fields == shown)
			{
				continue;
			}
			if (read)
			{
				/* The switch wins over the fields it stood for, never over one added since. */
				for (Field field : section.fields)
				{
					boolean known = added.getOrDefault(field.key, 1) <= settledWith;
					if (known && field.on(config, store) != shown)
					{
						store.set(field.key, Boolean.toString(shown));
					}
				}
				boolean now = on(section, config, store);
				if (now != shown)
				{
					store.set(section.key, Boolean.toString(now));
				}
			}
			else
			{
				store.set(section.key, Boolean.toString(fields));
			}
		}
		if (!read || settledWith < generation)
		{
			store.set(KEY_SETTLED, Integer.toString(generation));
		}
	}

	/**
	 * A setting in the plugin's group was written, {@code value} being what it was written as.
	 *
	 * <p>A switch's own event is acted on only with a value, on a profile already read: unsetting one
	 * is how Reset starts, and a profile not yet read is one RuneLite may just have written defaults
	 * into, which the start that reads it decides about.
	 */
	static void changed(String key, @Nullable String value, MapCompanionConfig config, ConfigStore store)
	{
		for (Section section : SharingFields.GROUPS)
		{
			if (section.key.equals(key))
			{
				if (value != null && store.get(KEY_SETTLED) != null && Boolean.parseBoolean(value) != on(section, config, store))
				{
					choose(section, Boolean.parseBoolean(value), config, store);
				}
				return;
			}
			for (Field field : section.fields)
			{
				if (!field.key.equals(key))
				{
					continue;
				}
				boolean shown = section.read.test(config);
				if (!field.on(config, store) && shown)
				{
					store.set(section.key, "false");
				}
				else if (field.on(config, store) && !shown && on(section, config, store))
				{
					store.set(section.key, "true");
				}
				return;
			}
		}
	}
}
