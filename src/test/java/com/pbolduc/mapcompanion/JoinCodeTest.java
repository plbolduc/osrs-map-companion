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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Everything a player might actually paste into the Join field.
 *
 * <p>This is the one piece of the joining story with no way to be tested by using it: the failure
 * is somebody pasting a Discord message and being told it is not a code, and neither the compiler
 * nor a running client has an opinion about that. So the accepted forms are pinned here as
 * literally as possible — the invite this plugin's own button writes, the link inside it, the
 * RuneLite code from the map's card, the two halves as the panel prints them — along with the
 * things that must <i>not</i> be read as a code.
 */
public class JoinCodeTest
{
	@Test
	public void takesABareCode()
	{
		JoinCode parsed = JoinCode.parse("ABC234");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertNull(parsed.suffix);
	}

	@Test
	public void takesACodeInAnyCaseAndWithWhitespaceAroundIt()
	{
		JoinCode parsed = JoinCode.parse("  abc234\n");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
	}

	@Test
	public void takesTheTwoHalvesTheMapPrints()
	{
		/** The panel draws a code as "ABC 234", so that is what somebody copying it off it has. */
		JoinCode parsed = JoinCode.parse("ABC 234");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertNull(parsed.suffix);
	}

	@Test
	public void takesTheTwoHalvesWithASuffixAfterThem()
	{
		/**
		 * What somebody has when they are reading a RuneLite code off one screen and typing it into
		 * another: the panel prints the group code in two halves, and the eye copies what it sees.
		 * The suffix is the half that stops the map guessing which dot is theirs, so answering this
		 * "that isn't a group code" — which is what happened before — cost them the whole point of
		 * pasting it, on the form they were most likely to type by hand.
		 */
		JoinCode parsed = JoinCode.parse("ABC 234-K7PQ2M3X");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertEquals("K7PQ2M3X", parsed.suffix);

		/** Lower case and stray whitespace are the typist's, as everywhere else here. */
		JoinCode typed = JoinCode.parse("  abc 234-k7pq2m3x\n");
		assertNotNull(typed);
		assertEquals("ABC234", typed.code);
		assertEquals("K7PQ2M3X", typed.suffix);
	}

	@Test
	public void doesNotFindAGroupedCodeInsideASentence()
	{
		/**
		 * What anchoring the two grouped patterns to the whole paste buys. Upper-cased, "the
		 * map-starters" is three legal characters, a space, three more, a dash and eight more —
		 * exactly the shape of a grouped RuneLite code — so an unanchored pattern would find one in
		 * any message that happened to contain those words.
		 *
		 * <p>What it does not buy, knowingly, is the phrase pasted on its own: that still reads as a
		 * code, as "THE MAP" always has. Both are survivable for the same reason, which is that this
		 * is a field labelled Join a group and Paste says what it found rather than joining.
		 */
		assertNull(JoinCode.parse("we should look at the map-starters before tonight"));
		assertNull(JoinCode.parse("meet me at ABC 234-K7PQ2M3X tonight"));
	}

	@Test
	public void takesARuneLiteCodeAndKeepsTheSuffix()
	{
		JoinCode parsed = JoinCode.parse("ABC234-K7PQ2M3X");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertEquals("K7PQ2M3X", parsed.suffix);
	}

	@Test
	public void takesALowerCaseRuneLiteCodeOutOfASentence()
	{
		JoinCode parsed = JoinCode.parse("here you go: abc234-k7pq2m3x (paste that in RuneLite)");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertEquals("K7PQ2M3X", parsed.suffix);
	}

	@Test
	public void takesAnInviteLink()
	{
		JoinCode parsed = JoinCode.parse("https://shootingstarsmap.com/map?join=ABC234&task=companion");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
	}

	@Test
	public void takesAnInviteLinkThatCarriesAnInviteToken()
	{
		JoinCode parsed = JoinCode.parse(
							  "https://shootingstarsmap.com/map?join=ABC234&i=7f2c9a1b4d&task=companion");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
	}

	@Test
	public void takesTheWholeInviteMessageThisPluginWrites()
	{
		/**
		 * Verbatim from {@code MapCompanionPanel.finishInvite}. The message names the code twice,
		 * in a link and in words, and both name the same group — so whichever the parser reaches
		 * first, the answer is the same one.
		 */
		String message = "Join my OSRS map: https://shootingstarsmap.com/map?join=ABC234&task=companion"
						 + " — then install the \"OSRS Map Companion\" RuneLite plugin and enter code ABC234";
		JoinCode parsed = JoinCode.parse(message);
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
	}

	@Test
	public void takesTheServerBuiltInviteMessage()
	{
		String message = "Join my OSRS map: https://shootingstarsmap.com/map?join=ABC234&i=tok&task=companion"
						 + " — code ABC234";
		JoinCode parsed = JoinCode.parse(message);
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
	}

	@Test
	public void prefersTheRuneLiteCodeWhenTheMessageCarriesBoth()
	{
		/**
		 * The suffix is the half that stops the map guessing which dot is the reader, and it only
		 * appears in one of these forms. Reading the bare code first would drop it silently, which
		 * is the failure this ordering exists to prevent.
		 */
		JoinCode parsed = JoinCode.parse(
							  "map?join=ABC234&task=companion — your RuneLite code is ABC234-K7PQ2M3X");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertEquals("K7PQ2M3X", parsed.suffix);
	}

	@Test
	public void keepsTheCodeWhenTheSuffixDidNotSurviveTheCopy()
	{
		/**
		 * A truncated RuneLite code still names a group, and refusing text that visibly contains a
		 * code helps nobody: the map answers a member it does not know by asking for the code
		 * again, which is a sentence somebody can act on.
		 */
		JoinCode parsed = JoinCode.parse("ABC234-K7PQ2M");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertNull(parsed.suffix);
	}

	@Test
	public void keepsTheCodeWhenTheSuffixIsTooLongRatherThanCuttingItDown()
	{
		/**
		 * The other end of the truncated case above, and the answer has to be the same one: a
		 * suffix that is not exactly eight characters is not this reader's suffix, and slicing one
		 * out of it would post a header the map never issued. The code is still visibly there, so
		 * it still joins — and the map answers a member it does not recognise by asking for the
		 * RuneLite code again, which is a sentence somebody can act on.
		 */
		JoinCode parsed = JoinCode.parse("ABC234-K7PQ2M3XY");
		assertNotNull(parsed);
		assertEquals("ABC234", parsed.code);
		assertNull(parsed.suffix);
	}

	@Test
	public void takesTheFirstCodeWhenAPasteNamesTwoGroups()
	{
		/**
		 * Somebody pasting a scroll of Discord can hand this two different groups, and there is no
		 * honest way to pick between them — so the rule is the one the ordering already implies:
		 * the most specific form wins, and among equals the first one. It is pinned because it is a
		 * decision rather than an accident, and because the panel shows which code it found before
		 * anybody presses Join, which is what makes a wrong guess visible rather than silent.
		 */
		JoinCode two = JoinCode.parse("ABC234 and also DEF567");
		assertNotNull(two);
		assertEquals("ABC234", two.code);

		/** A link and a bare code together: the link is the more specific form and wins. */
		JoinCode linked = JoinCode.parse("map?join=ABC234 — code DEF567");
		assertNotNull(linked);
		assertEquals("ABC234", linked.code);
	}

	@Test
	public void refusesEmptyAndAbsentText()
	{
		assertNull(JoinCode.parse(null));
		assertNull(JoinCode.parse(""));
		assertNull(JoinCode.parse("   "));
	}

	@Test
	public void refusesTextWithNoCodeInIt()
	{
		assertNull(JoinCode.parse("hey are you around tonight?"));
		assertNull(JoinCode.parse("https://shootingstarsmap.com/map?task=companion"));
	}

	@Test
	public void refusesTheWrongNumberOfCharacters()
	{
		assertNull(JoinCode.parse("ABC23"));
		assertNull(JoinCode.parse("ABCDEFGHJ"));
	}

	@Test
	public void refusesLettersTheAlphabetLeavesOut()
	{
		/** No O, I, L or U, so a code that looks like it has one has been misread. */
		assertNull(JoinCode.parse("ABCO23"));
		assertNull(JoinCode.parse("ABC1I4"));
		assertNull(JoinCode.parse("LOUIS7"));
	}

	@Test
	public void refusesSixLegalCharactersGluedToSomethingElse()
	{
		assertNull(JoinCode.parse("K7PQ2M3XABC234"));
		assertNull(JoinCode.parse("ABC2345"));
	}

	@Test
	public void knowsWhichStringsAreCodesAndSuffixes()
	{
		assertTrue(JoinCode.isWellFormedCode("ABC234"));
		assertFalse(JoinCode.isWellFormedCode("ABC23"));
		assertFalse(JoinCode.isWellFormedCode("abc234"));
		assertFalse(JoinCode.isWellFormedCode(null));

		assertTrue(JoinCode.isWellFormedSuffix("K7PQ2M3X"));
		assertFalse(JoinCode.isWellFormedSuffix("K7PQ2M3"));
		assertFalse(JoinCode.isWellFormedSuffix("K7PQ2M3O"));
		assertFalse(JoinCode.isWellFormedSuffix(""));
	}

	@Test
	public void printsACodeTheWayTheMapDoes()
	{
		assertEquals("ABC 234", JoinCode.forDisplay("ABC234"));
		/** Anything that is not a code is printed as it is rather than sliced. */
		assertEquals("ABC", JoinCode.forDisplay("ABC"));
	}
}
