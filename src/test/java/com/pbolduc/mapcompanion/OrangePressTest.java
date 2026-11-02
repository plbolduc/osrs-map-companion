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

import com.pbolduc.mapcompanion.ConnectCard.OrangePress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * What the orange button presses: a friend's invite in the field, a new group, or the map.
 *
 * <p>Pasting an invite and pressing the loudest button on the page made a new group, emptied the
 * field and left the friend alone in it while the inviter waited. Connect joins what the field holds
 * when that is an invite or a RuneLite code, and only then: a code alone is six letters an ordinary
 * word can spell.
 */
public class OrangePressTest
{
	/** The whole message Copy invite puts on a clipboard, as a friend pastes it. */
	private static final String INVITE = ConnectCard.inviteMessage(
		"https://osrs-map.com/map?join=KQX482&task=companion&i=K7PQ2M3XABCDEFGH");

	@Test
	public void connectJoinsAPastedInviteRatherThanMakingAGroup()
	{
		assertEquals(OrangePress.JOIN, ConnectCard.orangePress("", false, INVITE));
		assertEquals(OrangePress.JOIN, ConnectCard.orangePress("", false, "https://osrs-map.com/map?join=KQX482&i=K7PQ2M3XABCDEFGH"));
		/** The reader's own RuneLite code from the map's panel is as plainly meant. */
		assertEquals(OrangePress.JOIN, ConnectCard.orangePress("", false, "ABC234-K7PQ2M3X"));
	}

	@Test
	public void connectStillMakesAGroupFromAnEmptyFieldOrAWord()
	{
		assertEquals(OrangePress.START, ConnectCard.orangePress("", false, ""));
		assertEquals(OrangePress.START, ConnectCard.orangePress("", false, null));
		assertEquals(OrangePress.START, ConnectCard.orangePress("", false, "see you at the GE at 8"));
		/** Six legal characters each: Join is the press that says a code is meant. */
		assertEquals(OrangePress.START, ConnectCard.orangePress("", false, "THANKS"));
		assertEquals(OrangePress.START, ConnectCard.orangePress("", false, "KQX482"));
		/** The old plain message names a code and no invite, so it is a code alone too. */
		assertEquals(OrangePress.START, ConnectCard.orangePress("", false,
			"Join my OSRS map: https://osrs-map.com/map?join=KQX482&task=companion"));
	}

	@Test
	public void inAGroupTheButtonOpensTheMapWhateverTheFieldHolds()
	{
		assertEquals(OrangePress.OPEN_MAP, ConnectCard.orangePress("KQX482", true, INVITE));
		/** A code the selected map has not approved connects again, through the field when it holds one. */
		assertEquals(OrangePress.JOIN, ConnectCard.orangePress("KQX482", false, ""));
	}

	/** What is joined is what Join would join: the invite's code and its token. */
	@Test
	public void theInviteJoinedIsTheOneInTheField()
	{
		JoinCode joined = ConnectCard.joinsInstead(INVITE);
		assertNotNull(joined);
		assertEquals("KQX482", joined.code);
		assertEquals("K7PQ2M3XABCDEFGH", joined.invite);
		assertNull(ConnectCard.joinsInstead("THANKS"));
	}
}
