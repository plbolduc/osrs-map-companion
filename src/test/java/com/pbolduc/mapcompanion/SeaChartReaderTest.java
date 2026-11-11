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

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.VarPlayerID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** One bit a task, task n being bit n % 32 of the n / 32nd completion varp. */
public class SeaChartReaderTest
{
	private final HarnessGame game = new HarnessGame();
	private final SeaChartReader reader = new SeaChartReader(game.client());

	@Test
	public void readsEachSetBitAsTheTaskWithThatId()
	{
		/* Task 0 (Port Sarim's task board) and 3 (the Bay of Sarim's currents) in the first varp; 167 is bit 7 of the sixth. */
		game.setVarp(VarPlayerID.SAILING_CHARTING_COMPLETION_0, 0b1001);
		game.setVarp(VarPlayerID.SAILING_CHARTING_COMPLETION_5, 1 << 7);
		game.setVarp(VarPlayerID.SAILING_CHARTING_COMPLETION_11, Integer.MIN_VALUE);
		reader.onSessionResumed(0);
		assertNull("every varp reads zero for a moment after a login, which would be nothing charted", reader.read(1_000));
		assertEquals(Arrays.asList(0, 3, 167, 11 * 32 + 31), reader.read(15_000));
	}

	@Test
	public void nothingChartedIsAnEmptyListOnceSettled()
	{
		reader.onSessionResumed(0);
		assertEquals(Collections.emptyList(), reader.read(15_000));
		reader.onSessionEnded();
		assertNull(reader.read(16_000));
	}
}
