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

/**
 * When a reading of the game's stored state is worth believing.
 *
 * <p>Varps and varbits hold whatever the server last told this client, which is what lets the
 * plugin report a farming patch it saw six hours ago. It is also what makes the moments after a
 * login a trap: the client has not been told anything yet, every one of those reads zero, and zero
 * is a perfectly valid value. So a reading taken there is not "missing" — it is a confident,
 * freshly stamped claim that nothing is planted anywhere and no diary has ever been finished, told
 * in this plugin's own voice.
 *
 * <p>Hence: believe nothing until the session has been up for a few seconds. The window is measured
 * from coming back from a logged-out state rather than from every {@code LOGGED_IN} event, because
 * crossing a loading zone raises one of those too and a run past four regions would otherwise
 * suppress the readings it was meant to collect.
 *
 * <p>This lives on its own because two readers need the same rule for the same reason, and a rule
 * this subtle kept in two places is a rule that will be fixed in one of them.
 */
final class SessionSettle
{
	private final long settleMs;

	/**
	 * When readings start being trustworthy again, or {@code MAX_VALUE} while logged out.
	 *
	 * <p>Starting at {@code MAX_VALUE} is what makes a plugin enabled mid-session behave: nothing is
	 * believed until {@link #resumed} has been told the client is up, which the game tick does on
	 * every pass, so this heals itself whether the session began with a login or with a toggle.
	 */
	private long readableFromMs = Long.MAX_VALUE;

	SessionSettle(long settleMs)
	{
		this.settleMs = settleMs;
	}

	/** The client is up. Starts the clock, but only if it was not already running. */
	void resumed(long nowMs)
	{
		if (readableFromMs == Long.MAX_VALUE)
		{
			readableFromMs = nowMs + settleMs;
		}
	}

	/** Logged out or hopping: everything is about to read zero, so believe none of it. */
	void ended()
	{
		readableFromMs = Long.MAX_VALUE;
	}

	boolean settled(long nowMs)
	{
		return nowMs >= readableFromMs;
	}
}
