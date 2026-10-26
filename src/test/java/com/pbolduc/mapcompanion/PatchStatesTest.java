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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;
import org.junit.Test;

public class PatchStatesTest
{
	@Test
	public void bundledValuesMatchEverySupportedRuneLiteDecode() throws Exception
	{
		String resource = new String(getClass().getResourceAsStream("/farming-crops.txt").readAllBytes(), StandardCharsets.UTF_8);
		List<String> rows = Arrays.stream(resource.split("\\R")).filter(row -> !row.startsWith("#") && !row.isEmpty()).collect(Collectors.toList());
		assertEquals("Review farming decode changes against the installed RuneLite version", FarmingDecodeReference.rows(), rows);
	}

	@Test
	public void newlyPlantedRequiresARealCropAtItsFirstStage()
	{
		assertTrue(PatchStates.decode("HERB", 4).newlyPlanted);
		assertTrue(PatchStates.decode("TREE", 8).newlyPlanted);
		assertTrue(PatchStates.decode("FRUIT_TREE", 8).newlyPlanted);
		assertFalse(PatchStates.decode("HERB", 3).newlyPlanted);
		assertFalse(PatchStates.decode("HERB", 5).newlyPlanted);
		assertFalse(PatchStates.decode("FRUIT_TREE", 14).newlyPlanted);
		assertFalse(PatchStates.decode("TREE", 13).newlyPlanted);
	}

	@Test
	public void readyIncludesTreesAwaitingTheirHealthCheckButNotGrowth()
	{
		assertEquals("growing", PatchStates.decode("TREE", 11).state);
		assertEquals("ready", PatchStates.decode("TREE", 12).state);
		assertEquals("ready", PatchStates.decode("TREE", 13).state);
		assertEquals("ready", PatchStates.decode("FRUIT_TREE", 34).state);
		assertEquals("ready", PatchStates.decode("HERB", 8).state);
		assertEquals(Integer.valueOf(4), PatchStates.decode("HERB", 4).stagesLeft);
		assertEquals("Guam", PatchStates.decode("HERB", 4).produce);
	}

	@Test
	public void unreadableValuesNeverBecomeEmptyOrCompleted()
	{
		for (int value : new int[] {-1, 256, Integer.MAX_VALUE})
		{
			assertEquals("unknown", PatchStates.decode("HERB", value).state);
			assertFalse(PatchStates.decode("HERB", value).newlyPlanted);
		}
		assertEquals("unknown", PatchStates.decode("future-crop", 3).state);
	}

	@Test
	public void growthTimingComesFromTheSameCropAsItsStages()
	{
		assertEquals(Integer.valueOf(20), PatchStates.decode("HERB", 4).growthMinutes);
		assertEquals(Integer.valueOf(40), PatchStates.decode("TREE", 8).growthMinutes);
		assertEquals(Integer.valueOf(160), PatchStates.decode("FRUIT_TREE", 8).growthMinutes);
		assertNull(PatchStates.decode("HERB", 8).growthMinutes);
		assertNull(PatchStates.decode("HERB", 255).growthMinutes);
	}
}
