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
 * The sidebar dot, derived from the last post's outcome rather than from the fact that we
 * are about to try.
 *
 * <p>Writing LIVE before {@code post} is what made the dot stay green through a dead
 * network: the attempt was taken as the result. These four outcomes are the whole state
 * machine; anything else is a colour recovered from a sentence, which is the bug this
 * enum exists not to have.
 */
public class HealthTest
{
	@Test
	public void waitsUntilAPostHasLanded()
	{
		assertEquals(Health.WAITING, MapCompanionPlugin.healthFor(false, 0, null));
		assertEquals(Health.WAITING, MapCompanionPlugin.healthFor(false, 3, null));
	}

	@Test
	public void isLiveOnlyAfterASuccessWithNothingOutstanding()
	{
		assertEquals(Health.LIVE, MapCompanionPlugin.healthFor(true, 0, null));
	}

	@Test
	public void degradesWhenALiveConnectionMisses()
	{
		assertEquals(Health.DEGRADED, MapCompanionPlugin.healthFor(true, 1, null));
		assertEquals(Health.DEGRADED, MapCompanionPlugin.healthFor(true, 8, null));
	}

	@Test
	public void errorsOnAFatalRefusal()
	{
		assertEquals(Health.ERROR, MapCompanionPlugin.healthFor(true, 0, "That group is full."));
		assertEquals(Health.ERROR, MapCompanionPlugin.healthFor(false, 0, "That pairing code isn't the right shape — check it on the map's Companion panel."));
	}
}
