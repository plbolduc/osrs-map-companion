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
 * A reader of the game's stored state that believes nothing until the session has settled.
 *
 * <p>The plugin tells every such reader when the client comes up and when a login or a hop ends
 * the session; {@link SessionSettle} holds the rule for when a reading is worth believing after
 * that. Most readers did nothing else at those two moments but pass them on, each with its own copy
 * of the two methods, so they are said here once. A reader that forgets something of its own when
 * a session ends overrides {@link #onSessionEnded()} and passes it on first.
 */
abstract class SettlingReader
{
	/** Fifteen seconds after a login or a hop, the wait every reader here keeps. Client thread. */
	final SessionSettle settle = new SessionSettle(15_000L);

	/** The client is up. Starts the settling clock, but only if it was not already running. */
	public void onSessionResumed(long nowMs)
	{
		settle.resumed(nowMs);
	}

	/** Logged out or hopping: everything is about to read zero, so believe none of it. */
	public void onSessionEnded()
	{
		settle.ended();
	}

	/**
	 * The session ended, and this is how. Every way one ends takes the readings with it, so that is
	 * the answer here; a reader that keeps something through one ending, or forgets more at another,
	 * overrides this and says which. The plugin tells every reader through this, in one list.
	 */
	void onSessionEnded(SessionEnd end)
	{
		onSessionEnded();
	}
}
