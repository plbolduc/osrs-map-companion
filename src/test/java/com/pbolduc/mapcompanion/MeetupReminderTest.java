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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The meetup reminder is off until asked for, and then says each meetup once, when it was asked to. */
public class MeetupReminderTest
{
	private static final long NOW = 1_800_000_000_000L;

	@Test
	public void isOffByDefault()
	{
		MapCompanionConfig defaults = new MapCompanionConfig()
		{
		};
		assertFalse("the reminder is opt-in", defaults.notifyMeetups());
		assertEquals(5, defaults.meetupReminderMinutes());

		MeetupReminder reminder = new MeetupReminder();
		List<MeetupReminder.Upcoming> soon = Collections.singletonList(new MeetupReminder.Upcoming("hunt", "Star hunt", NOW + 60_000L));
		assertTrue("nothing is said while it is off", reminder.due(soon, NOW, defaults.notifyMeetups(), 5).isEmpty());
	}

	@Test
	public void saysEachMeetupOnceAtTheChosenLead()
	{
		MeetupReminder reminder = new MeetupReminder();
		List<MeetupReminder.Upcoming> week = Arrays.asList(
			new MeetupReminder.Upcoming("hunt", "Star hunt", NOW + 20 * 60_000L),
			new MeetupReminder.Upcoming("raids", "Raids", NOW + 26 * 3_600_000L));
		assertTrue("twenty minutes out is not yet fifteen", reminder.due(week, NOW, true, 15).isEmpty());
		assertEquals(Collections.singletonList("Meetup: Star hunt starts in 15m"),
			reminder.due(week, NOW + 5 * 60_000L, true, 15));
		assertTrue("once", reminder.due(week, NOW + 10 * 60_000L, true, 15).isEmpty());

		/* Moved to another hour, it is a new time to be told about. */
		List<MeetupReminder.Upcoming> moved = Collections.singletonList(
			new MeetupReminder.Upcoming("hunt", "Star hunt", NOW + 75 * 60_000L));
		assertEquals(1, reminder.due(moved, NOW + 65 * 60_000L, true, 15).size());
	}

	@Test
	public void aMeetupLongStartedIsNotNews()
	{
		MeetupReminder reminder = new MeetupReminder();
		List<MeetupReminder.Upcoming> past = Collections.singletonList(new MeetupReminder.Upcoming("hunt", "Star hunt", NOW - 10 * 60_000L));
		assertTrue(reminder.due(past, NOW, true, 5).isEmpty());
	}

	@Test
	public void theWeeksMeetupsWhenSentElseTheOneAlwaysSent()
	{
		RelayResponse.Event event = new RelayResponse.Event();
		event.title = "Star hunt";
		event.startsAtMs = NOW;
		assertEquals(1, MeetupReminder.of(null, event).size());
		assertTrue(MeetupReminder.of(Collections.<RelayResponse.Meetup>emptyList(), event).isEmpty());
		assertTrue(MeetupReminder.of(null, null).isEmpty());
	}
}
