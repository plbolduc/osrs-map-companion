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

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

/** Exercise the real directive adapter, including values which must not lose a pairing reply. */
public class FarmRunWireTest
{
	private final Gson gson = new Gson();

	private RelayResponse.FarmRun example() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/farm-run.json").readAllBytes(), StandardCharsets.UTF_8);
		return gson.fromJson("{\"directive\":{\"run\":" + json + "}}", RelayResponse.class).directive.run;
	}

	@Test
	public void readsTheMapsActualContractWithoutLosingStopNumbersOrEvidence() throws Exception
	{
		RelayResponse.FarmRun run = example();
		assertNotNull(run);
		assertEquals(4, run.stops.size());
		assertEquals(1, run.stops.get(0).n);
		assertEquals("falador-herb", run.stops.get(0).patchIds.get(0));
		assertEquals("meadow-north", run.stops.get(2).birdhouseId);
		assertTrue(run.stops.get(2).done);
		assertTrue(run.stops.get(3).visited);
		assertFalse(run.stops.get(3).done);
		assertFalse(run.bring.isEmpty());
		assertNotNull(run.stops.get(0).steps.get(0).title);
	}

	@Test
	public void anUnreadableRunDoesNotLoseThePairingSecretOrAcknowledgement()
	{
		for (String bad : new String[] {"4", "[]", "{\"stops\":4}", "{\"startedAtMs\":{}}"})
		{
			RelayResponse response = gson.fromJson("{\"memberSecret\":\"keep-this\",\"acknowledgedMilestones\":[\"reward-1\"],\"directive\":{\"run\":" + bad + "}}", RelayResponse.class);
			assertNull(response.directive.run);
			assertEquals("keep-this", response.memberSecret);
			assertEquals("reward-1", response.acknowledgedMilestones.get(0));
		}
	}

	@Test
	public void keepsTwelveGrapeReceiptsAndBoundsAndSortsTheStops() throws Exception
	{
		RelayResponse.FarmRun raw = example();
		RelayResponse.FarmStop grapes = raw.stops.get(0);
		grapes.patchIds = new ArrayList<>();
		for (String side : new String[] {"east", "west"})
			for (int n = 1; n <= 6; n++)
			{
				grapes.patchIds.add("hosidius-" + side + "-" + n + "-grape");
			}
		raw.stops = new ArrayList<>(List.of(raw.stops.get(2), grapes, grapes));
		RelayResponse.FarmRun clean = FarmRunWire.normalize(raw);
		assertEquals(2, clean.stops.size());
		assertEquals(1, clean.stops.get(0).n);
		assertEquals(12, clean.stops.get(0).patchIds.size());
		grapes.tile.plane = 4;
		assertEquals(1, FarmRunWire.normalize(raw).stops.size());
	}

	@Test
	public void anUnreadableEpochDoesNotLoseThePairingSecretOrAcknowledgement()
	{
		for (String bad : new String[] {"4", "[]", "{}", "null", "\"short\""})
		{
			RelayResponse response = gson.fromJson("{\"memberSecret\":\"keep-this\",\"acknowledgedMilestones\":[\"reward-1\"],\"directive\":{\"relayEpoch\":" + bad + "}}", RelayResponse.class);
			assertNull(response.directive.relayEpoch);
			assertEquals("keep-this", response.memberSecret);
			assertEquals("reward-1", response.acknowledgedMilestones.get(0));
		}
	}

	@Test
	public void rejectsUnknownKindsAndRunsWithNoUsableStop() throws Exception
	{
		RelayResponse.FarmRun raw = example();
		raw.kind = "future-run";
		assertNull(FarmRunWire.normalize(raw));
		raw.kind = "sent";
		for (RelayResponse.FarmStop stop : raw.stops)
		{
			stop.tile.x = -1;
		}
		assertNull(FarmRunWire.normalize(raw));
	}
}
