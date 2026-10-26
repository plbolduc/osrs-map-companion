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

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * The sentence beside "Invite copied.", which is about somebody else's number.
 *
 * <p>The map states when an invite link stops working; this only prints it. So the rule being
 * pinned here is mostly about silence: anything that is not plainly a future instant in
 * milliseconds produces no sentence at all, because a link that says "expires in 20,000 days"
 * because the units were seconds is worse than a link that says nothing.
 */
public class InviteExpiryTest
{
	private static final long NOW = 1_800_000_000_000L;

	@Test
	public void countsWholeDays()
	{
		assertEquals(" It expires in 7 days.", MapCompanionPanel.describeExpiry(
						 Long.toString(NOW + 7 * 86_400_000L), NOW));
		assertEquals(" It expires in 1 day.", MapCompanionPanel.describeExpiry(
						 Long.toString(NOW + 86_400_000L + 60_000L), NOW));
	}

	@Test
	public void fallsBackToHoursAndThenToSoon()
	{
		assertEquals(" It expires in 5 hours.", MapCompanionPanel.describeExpiry(
						 Long.toString(NOW + 5 * 3_600_000L), NOW));
		assertEquals(" It expires in 1 hour.", MapCompanionPanel.describeExpiry(
						 Long.toString(NOW + 3_600_000L), NOW));
		assertEquals(" It expires within the hour.", MapCompanionPanel.describeExpiry(
						 Long.toString(NOW + 60_000L), NOW));
	}

	@Test
	public void saysNothingWhenTheMapSaidNothingUsable()
	{
		assertEquals("", MapCompanionPanel.describeExpiry(null, NOW));
		assertEquals("", MapCompanionPanel.describeExpiry("", NOW));
		/** An instant as text: readable by a person, not by this, and not worth guessing at. */
		assertEquals("", MapCompanionPanel.describeExpiry("2026-09-14T00:00:00Z", NOW));
		/** Seconds rather than milliseconds reads as long past, which is silence rather than a lie. */
		assertEquals("", MapCompanionPanel.describeExpiry(Long.toString(NOW / 1000L + 604_800L), NOW));
		/** And an invite that has already expired is not news to put beside a fresh one. */
		assertEquals("", MapCompanionPanel.describeExpiry(Long.toString(NOW - 1L), NOW));
	}
}
