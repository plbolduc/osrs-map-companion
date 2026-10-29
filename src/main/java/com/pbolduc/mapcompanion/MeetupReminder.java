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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * When to say a meetup is about to start: once for each, a chosen number of minutes before.
 *
 * <p>A countdown that only exists in the sidebar is a countdown you miss, because the sidebar is
 * shut and the game is on the other screen. So this is the one moment the plugin interrupts, through
 * RuneLite's own notifier, which the player's notification settings already govern, and only when
 * <i>Notify before a meetup</i> is on, which it is not until the player turns it on. It still does
 * not act: a notification is a sentence, and walking there remains the player's own business.
 *
 * <p>Every meetup of the week is watched, not only the next, and each is said once — by its id, its
 * time and its title, so a meetup moved to another hour is said again at the new one. Any member can
 * edit a meetup, so one being edited every few seconds is held to one notice a minute, and a meetup
 * that started more than a couple of minutes ago is not news.
 *
 * <p>Game tick only; the plugin owns the one instance and clears it with the group.
 */
final class MeetupReminder
{
	/** How long after the start a notice is still worth giving. */
	static final long GRACE_MS = 2 * 60_000L;
	/** Notices for meetups that keep changing are spaced by at least this. */
	static final long SPACING_MS = 60_000L;

	/** One meetup as this needs it: something to tell it apart by, a title, and when. */
	static final class Upcoming
	{
		final String key;
		final String title;
		final long startsAtMs;

		Upcoming(String key, String title, long startsAtMs)
		{
			this.key = key;
			this.title = title;
			this.startsAtMs = startsAtMs;
		}
	}

	private final Set<String> said = new HashSet<>();
	private long lastAtMs = Long.MIN_VALUE / 2;

	/**
	 * The sentences to notify now, at most one. Nothing when the setting is off, which is its default.
	 */
	List<String> due(List<Upcoming> meetups, long now, boolean on, int minutesBefore)
	{
		List<String> out = new ArrayList<>();
		if (!on || now - lastAtMs < SPACING_MS)
		{
			return out;
		}
		long lead = Math.max(1, minutesBefore) * 60_000L;
		for (Upcoming meetup : meetups)
		{
			long until = meetup.startsAtMs - now;
			String key = meetup.key + "|" + meetup.startsAtMs + "|" + meetup.title;
			if (until > lead || until < -GRACE_MS || said.contains(key))
			{
				continue;
			}
			said.add(key);
			lastAtMs = now;
			long minutes = Math.max(0L, Math.round(until / 60_000.0));
			out.add("Meetup: " + MapCompanionPlugin.safeTitle(meetup.title)
				+ (minutes <= 0 ? " is starting" : " starts in " + minutes + "m"));
			break;
		}
		return out;
	}

	/** A group left or a lifetime ended: the next group's meetups are new. */
	void clear()
	{
		said.clear();
		lastAtMs = Long.MIN_VALUE / 2;
	}

	/** Whether this meetup has been said, for the group-change test. */
	boolean said(String key, long startsAtMs, String title)
	{
		return said.contains(key + "|" + startsAtMs + "|" + title);
	}

	/** The week's meetups when the map sends them, else the one it always sent. */
	static List<Upcoming> of(List<RelayResponse.Meetup> events, RelayResponse.Event event)
	{
		List<Upcoming> out = new ArrayList<>();
		if (events != null)
		{
			for (RelayResponse.Meetup meetup : events)
			{
				out.add(new Upcoming(meetup.id, meetup.title, meetup.startsAtMs));
			}
		}
		else if (event != null)
		{
			out.add(new Upcoming("event", event.title, event.startsAtMs));
		}
		return out;
	}
}
