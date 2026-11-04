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
 * What Paste does with a friend's message: before a group it joins at once, in a group it waits for
 * Join.
 *
 * <p>An invited friend pressed Paste, read "Found an invite to KQX 482. Press Join.", then pressed
 * Join or the orange Connect, which joined the same invite — three presses that all meant join, the
 * line naming a code rather than a group. Paste waited so that a stray press could not move anybody
 * out of the group they were in, and before a group there is none.
 */
public class PasteJoinsTest
{
	/** The whole message Copy invite puts on a clipboard, as a friend pastes it. */
	private static final String INVITE = ConnectCard.inviteMessage(
		"https://osrs-map.com/map?join=KQX482&task=companion&i=K7PQ2M3XABCDEFGH");

	@Test
	public void beforeAGroupAPastedInviteJoinsAtOnce()
	{
		assertEquals(ConnectCard.PasteAnswer.JOIN, ConnectCard.pasteAnswer("", INVITE));
		/** The reader's own RuneLite code from the map's panel is as plainly meant. */
		assertEquals(ConnectCard.PasteAnswer.JOIN, ConnectCard.pasteAnswer("", "ABC234-K7PQ2M3X"));
	}

	@Test
	public void inAGroupAPastedInviteWaitsForJoin()
	{
		assertEquals(ConnectCard.PasteAnswer.FILL, ConnectCard.pasteAnswer("ABC234", INVITE));
		assertEquals(ConnectCard.PasteAnswer.FILL, ConnectCard.pasteAnswer("ABC234", "KQX482-K7PQ2M3X"));
	}

	/** Six legal characters can spell a word, so a bare code waits for Join whether or not there is a group. */
	@Test
	public void aCodeAloneWaitsForJoin()
	{
		assertEquals(ConnectCard.PasteAnswer.FILL, ConnectCard.pasteAnswer("", "KQX482"));
	}

	@Test
	public void whatIsNoInviteIsSaidAndNothingIsJoined()
	{
		assertEquals(ConnectCard.PasteAnswer.NOTHING, ConnectCard.pasteAnswer("", null));
		assertEquals(ConnectCard.PasteAnswer.NOTHING, ConnectCard.pasteAnswer("", "   "));
		assertEquals(ConnectCard.PasteAnswer.NO_INVITE, ConnectCard.pasteAnswer("", "see you at the GE at 8"));
	}
}
