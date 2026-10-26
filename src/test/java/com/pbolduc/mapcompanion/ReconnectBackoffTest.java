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
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The stream's reconnect delay.
 *
 * <p>Worth its own test because of what it replaced. A refusal used to schedule a reconnect from
 * inside a {@code try} and then return, which runs the {@code finally} — which scheduled a second
 * one. Two threads reopened, both were refused, and each scheduled two more: a connection count that
 * doubled every ten seconds against a server that was already saying no. The duplicate call is gone
 * and a flag now makes a second one impossible; this pins down the third defence, which is that the
 * delay grows, so a permanent refusal settles into costing almost nothing.
 */
public class ReconnectBackoffTest
{
	@Test
	public void startsAtTenSecondsAndDoubles()
	{
		assertEquals(10_000L, DirectiveStream.backoffMs(1));
		assertEquals(20_000L, DirectiveStream.backoffMs(2));
		assertEquals(40_000L, DirectiveStream.backoffMs(3));
		assertEquals(80_000L, DirectiveStream.backoffMs(4));
	}

	@Test
	public void capsAtFiveMinutesAndStaysThere()
	{
		long cap = 5 * 60_000L;
		assertEquals(cap, DirectiveStream.backoffMs(7));
		assertEquals(cap, DirectiveStream.backoffMs(50));
		/** The one that matters: a client left running for days must not shift its way negative. */
		assertEquals(cap, DirectiveStream.backoffMs(Integer.MAX_VALUE));
	}

	@Test
	public void neverReturnsSomethingThatWouldHammerTheServer()
	{
		for (int failures = 0; failures < 1000; failures++)
		{
			long delay = DirectiveStream.backoffMs(failures);
			assertTrue("delay for " + failures + " was " + delay, delay >= 10_000L);
			assertTrue("delay for " + failures + " was " + delay, delay <= 5 * 60_000L);
		}
	}
}
