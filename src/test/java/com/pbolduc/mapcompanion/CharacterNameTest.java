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
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The one field this plugin sends that names the account rather than describing it.
 *
 * <p>It travels only while its toggle is on, and only in a shape the map accepts —
 * {@code ^[A-Za-z0-9 _-]{1,12}$}. Two things are being pinned here. The first is that a real name
 * survives: the client writes the separator in a two-word name as a non-breaking space, so without
 * one repair every "Iron Mike" in the game would quietly fail the pattern and never be sent. The
 * second is that anything else is <b>dropped rather than trimmed into fitting</b>, because the only
 * way to get here with something unmatchable is for the client to have handed over something that
 * is not a name, and half of that is a wrong name shown confidently beside somebody's dot.
 */
public class CharacterNameTest
{
	@Test
	public void keepsAnOrdinaryName()
	{
		assertEquals("Zezima", SnapshotBuilder.sanitizeCharacterName("Zezima"));
	}

	@Test
	public void keepsTheCharactersRuneScapeNamesAreMadeOf()
	{
		assertEquals("Iron_Mike-7", SnapshotBuilder.sanitizeCharacterName("Iron_Mike-7"));
		assertEquals("A", SnapshotBuilder.sanitizeCharacterName("A"));
		/** Twelve is the longest name the game issues, and the pattern's ceiling. */
		assertEquals("Abcdefghijkl", SnapshotBuilder.sanitizeCharacterName("Abcdefghijkl"));
	}

	@Test
	public void turnsTheClientsNonBreakingSpaceIntoAnOrdinaryOne()
	{
		assertEquals("Iron Mike", SnapshotBuilder.sanitizeCharacterName("Iron\u00A0Mike"));
	}

	@Test
	public void trimsWhitespaceAroundIt()
	{
		assertEquals("Zezima", SnapshotBuilder.sanitizeCharacterName("  Zezima "));
	}

	@Test
	public void refusesNothingAtAll()
	{
		assertNull(SnapshotBuilder.sanitizeCharacterName(null));
		assertNull(SnapshotBuilder.sanitizeCharacterName(""));
		assertNull(SnapshotBuilder.sanitizeCharacterName("   "));
	}

	@Test
	public void refusesAnythingLongerThanAName()
	{
		assertNull(SnapshotBuilder.sanitizeCharacterName("ThirteenChars"));
	}

	@Test
	public void refusesCharactersANameCannotContain()
	{
		assertNull(SnapshotBuilder.sanitizeCharacterName("Zez<b>ima"));
		assertNull(SnapshotBuilder.sanitizeCharacterName("Zez\nima"));
		assertNull(SnapshotBuilder.sanitizeCharacterName("Zezim@"));
	}
}
