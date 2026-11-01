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
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

/** A visit is not a replant; revisions and lifetime boundaries must preserve that distinction. */
public class FarmRunStateTest
{
	private static final long START = 1_000_000L;

	private RelayResponse.FarmRun run() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/farm-run.json").readAllBytes(), StandardCharsets.UTF_8);
		RelayResponse.FarmRun run = new Gson().fromJson(json, RelayResponse.FarmRun.class);
		for (RelayResponse.FarmStop stop : run.stops)
		{
			stop.done = false;
		}
		return run;
	}

	private CompanionSnapshot.Patch replant(String id, String receipt, long age)
	{
		CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
		patch.id = id;
		patch.state = "growing";
		patch.observedMsAgo = 0;
		patch.completionId = receipt;
		patch.completedMsAgo = age;
		return patch;
	}

	@Test
	public void aReplantAfterTheRunArrivesCountsOnItsFirstTick() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.accept(run(), 1, START);
		state.observe(List.of(replant("falador-herb", "first-tick", 0)), null, null, START + 600);
		assertTrue(state.view().done.contains(1));
	}

	@Test
	public void arrivalAndAnExistingReceiptCannotCompleteTheStopButANewReplantCan() throws Exception
	{
		FarmRunState state = new FarmRunState();
		RelayResponse.FarmRun run = run();
		state.accept(run, 1, START);
		state.observe(List.of(replant("falador-herb", "old", 1_000)), null, FarmRunState.point(run.stops.get(0)), START + 100);
		assertTrue(state.view().here.contains(1));
		assertTrue(state.view().done.isEmpty());
		assertEquals(1, state.view().next().n);
		state.observe(List.of(replant("falador-herb", "old", 1_600)), null, null, START + 700);
		assertTrue(state.view().done.isEmpty());
		state.observe(List.of(replant("falador-herb", "new", 100)), null, null, START + 1_000);
		assertEquals(Collections.singleton(1), state.view().done);
		assertEquals(2, state.view().next().n);
		assertEquals(1, state.view().trackedDone());
	}

	@Test
	public void allTwelveGrapePatchesAreRequiredAndAnotherCropDoesNotCount() throws Exception
	{
		FarmRunState state = new FarmRunState();
		RelayResponse.FarmRun run = run();
		RelayResponse.FarmStop stop = run.stops.get(0);
		stop.patchIds = new ArrayList<>();
		for (String side : new String[] {"east", "west"})
			for (int i = 1; i <= 6; i++)
			{
				stop.patchIds.add("hosidius-" + side + "-" + i + "-grape");
			}
		run.stops = List.of(stop);
		state.accept(run, 1, START);
		state.observe(null, null, null, START);
		state.observe(List.of(replant("hosidius-herb", "wrong-crop", 0)), null, null, START + 100);
		assertTrue(state.view().done.isEmpty());
		for (int i = 0; i < 12; i++)
		{
			state.observe(List.of(replant(stop.patchIds.get(i), "grape-" + i, 0)), null, null, START + 200 + i);
			assertEquals(i == 11, state.view().done.contains(1));
		}
	}

	@Test
	public void staleFutureOrPreRunReceiptsAreNotEvidence() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.accept(run(), 1, START);
		state.observe(null, null, null, START);
		for (long age : new long[] {-1, FarmRunState.FRESH_MS + 1, 10_000})
		{
			state.observe(List.of(replant("falador-herb", "bad-" + age, age)), null, null, START + 1_000);
		}
		assertTrue(state.view().done.isEmpty());
		CompanionSnapshot.Patch stale = replant("falador-herb", "stale-observation", 0);
		stale.observedMsAgo = FarmRunState.FRESH_MS + 1;
		state.observe(List.of(stale), null, null, START + 1_000);
		assertTrue(state.view().done.isEmpty());
	}

	@Test
	public void birdhouseNeedsANewSeededSightingAndCustomStopsAreOnlyVisited() throws Exception
	{
		FarmRunState state = new FarmRunState();
		RelayResponse.FarmRun run = run();
		run.stops.get(1).patchIds = Collections.emptyList();
		state.accept(run, 1, START);
		CompanionSnapshot.Birdhouse house = new CompanionSnapshot.Birdhouse();
		house.id = "meadow-north";
		house.state = "seeded";
		house.observedMsAgo = 1_000;
		state.observe(null, List.of(house), null, START);
		state.observe(null, List.of(house), FarmRunState.point(run.stops.get(1)), START + 100);
		assertTrue(state.view().visited.contains(2));
		assertFalse(state.view().done.contains(2));
		assertFalse(state.view().done.contains(3));
		house.state = "built";
		house.observedMsAgo = 0;
		state.observe(null, List.of(house), null, START + 200);
		assertFalse(state.view().done.contains(3));
		house.state = "seeded";
		state.observe(null, List.of(house), null, START + 300);
		assertTrue(state.view().done.contains(3));
	}

	@Test
	public void duplicateAndOutOfOrderChannelsNeverRewindProgressOrReviveAnEndedRun() throws Exception
	{
		FarmRunState state = new FarmRunState();
		RelayResponse.FarmRun run = run();
		state.accept(run, 1, START);
		state.observe(null, null, null, START);
		state.observe(List.of(replant("falador-herb", "new", 0)), null, null, START + 100);
		state.accept(run(), 1, START + 200);
		assertTrue(state.view().done.contains(1));
		state.accept(run(), 2, START + 300); // A reroute preserves local work even before its echo.
		assertTrue(state.view().done.contains(1));
		state.accept(null, 3, START + 400);
		assertNull(state.view());
		state.accept(run(), 2, START + 500);
		assertNull(state.view());
	}

	@Test
	public void localEndAndConnectionResetInvalidateOldPressesAndKeepCharactersSeparate() throws Exception
	{
		FarmRunState state = new FarmRunState();
		RelayResponse.FarmRun run = run();
		state.accept(run, 1, START);
		long ticket = state.generation();
		state.end();
		assertFalse(state.current(ticket));
		state.accept(run, 2, START + 100); // A reroute already sent before End.
		assertNull(state.view());
		state.clear(); // A different pairing or RuneScape profile.
		run.id = "another-character";
		state.accept(run, 1, START + 200);
		assertTrue(state.view().done.isEmpty());
		state.observe(null, null, null, START + 200);
		state.observe(List.of(replant("falador-herb", "old-character", 500)), null, null, START + 300);
		assertTrue(state.view().done.isEmpty());
		state.observe(null, null, null, START + 200 + FarmRunState.RETENTION_MS + 1);
		assertNull(state.view());
	}

	@Test
	public void anOldCharactersStreamCannotReplaceANewCharactersRunEvenWithAHigherRevision() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.bind("old-character");
		state.acceptFor("old-character", run(), 20, START);
		state.bind("new-character");
		RelayResponse.FarmRun fresh = run();
		fresh.id = "new-characters-run";
		state.acceptFor("new-character", fresh, 1, START + 100);
		state.acceptFor("old-character", run(), 21, START + 200);
		assertEquals("new-characters-run", state.view().run.id);
		assertEquals(1, state.view().revision);
		assertTrue(state.view().done.isEmpty());
	}

	@Test
	public void endBeforeTheInitialPlanArrivesAlsoBlocksItsLateStreamFrame() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.end();
		state.accept(run(), 7, START);
		assertNull("the initial run id was not known when End was pressed", state.view());
		state.accept(null, 8, START + 100);
		RelayResponse.FarmRun next = run();
		next.id = "started-after-end";
		state.accept(next, 9, START + 200);
		assertEquals("started-after-end", state.view().run.id);
	}

	@Test
	public void anExplicitNewStartCanRecoverAfterEndCouldNotReachTheMap() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.bind("reader");
		state.end();
		state.acceptFor("reader", run(), 7, START);
		assertNull(state.view());
		RelayResponse.FarmRun next = run();
		next.id = "new-start";
		state.acceptPlannedFor("reader", next, 8, START + 100);
		assertEquals(next.id, state.view().run.id);
		assertFalse(state.ending());
	}
}
