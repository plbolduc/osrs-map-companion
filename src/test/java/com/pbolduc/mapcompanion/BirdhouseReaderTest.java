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

import org.junit.Test;
import static org.junit.Assert.*;

/** The source stores seconds, including malformed or future timestamps after clock changes. */
public class BirdhouseReaderTest
{
	@Test
	public void decodesTheNineHouseTypesAndRejectsUnreadableHistory() throws Exception
	{
		assertEquals(9, Class.forName("net.runelite.client.plugins.timetracking.hunter.BirdHouse").getEnumConstants().length);
		assertEquals("empty", BirdhouseReader.decode("meadow-north", "0:1000", 1_001_000).state);
		assertEquals("built", BirdhouseReader.decode("meadow-north", "1:1000", 1_001_000).state);
		assertEquals("seeded", BirdhouseReader.decode("meadow-north", "27:1000", 1_001_000).state);
		assertEquals(1000L, BirdhouseReader.decode("meadow-north", "3:1000", 1_001_000).observedMsAgo);
		for (String value : new String[] {null, "", "28:1000", "3:2000", "3:-1", "3:9223372036854775807", "3:1000:extra", "a:1000"})
		{
			assertNull(value, BirdhouseReader.decode("meadow-north", value, 1_001_000));
		}
		assertNull(BirdhouseReader.decode("meadow-north", "3:1000", 23_000_000));
	}

	@Test
	public void anAgingTimerDoesNotTurnEveryTickIntoANetworkPost()
	{
		CompanionSnapshot snapshot = new CompanionSnapshot();
		snapshot.birdhouses = java.util.Arrays.asList(BirdhouseReader.decode("meadow-north", "3:1000", 1_001_000));
		SnapshotFingerprint fingerprint = new SnapshotFingerprint(new com.google.gson.Gson());
		String before = fingerprint.of(snapshot);
		snapshot.birdhouses.get(0).observedMsAgo += 60_000L;
		assertEquals(before, fingerprint.of(snapshot));
		snapshot.birdhouses.get(0).state = "empty";
		assertNotEquals(before, fingerprint.of(snapshot));
	}
}
