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
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import static org.junit.Assert.*;
import org.junit.Test;

/** A run shares the ordinary route follower and gives up control when the player chooses another. */
public class FarmRunRouteTest
{
	private final FarmRunState state = new FarmRunState();
	private final DestinationState destinations = new DestinationState(new Gson());
	private final DestinationRoute route = new DestinationRoute();
	private final FarmRunRoute follower = new FarmRunRoute(state, destinations, route, new ClientThread()
	{
		@Override
		public void invokeLater(Runnable task)
		{
			task.run();
		}
	});

	private RelayResponse.FarmRun run() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/farm-run.json").readAllBytes(), StandardCharsets.UTF_8);
		RelayResponse.FarmRun run = new Gson().fromJson(json, RelayResponse.FarmRun.class);
		for (RelayResponse.FarmStop stop : run.stops)
		{
			stop.done = false;
			stop.visited = false;
		}
		return run;
	}

	private void replant()
	{
		CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
		patch.id = "falador-herb";
		patch.state = "growing";
		patch.observedMsAgo = 0;
		patch.completionId = "planted-now";
		patch.completedMsAgo = 0L;
		state.observe(Collections.singletonList(patch), null, null, 1_600);
	}

	@Test
	public void aReplantMovesTheExistingFollowerToTheNextNumberAndEndRemovesOnlyItsOwnTarget() throws Exception
	{
		RelayResponse.FarmRun run = run();
		state.accept(run, 1, 1_000);
		follower.sync();
		assertEquals("1. Falador farm", destinations.getDestination().name);
		assertTrue(route.view().following);
		assertTrue(follower.owns(destinations.getDestination()));
		route.advance(FarmRunState.point(run.stops.get(0)));
		follower.sync();
		assertEquals("arriving at a patch is not completing it", 1, state.view().next().n);
		replant();
		follower.sync();
		assertEquals("2. Catherby", destinations.getDestination().name);
		assertTrue(route.view().following);
		state.end();
		follower.sync();
		assertNull(destinations.getDestination());
		assertNull(route.view());
	}

	@Test
	public void anotherDestinationPausesTheRunUntilFollowIsPressed() throws Exception
	{
		state.accept(run(), 1, 1_000);
		follower.sync();
		RelayResponse.Destination other = new RelayResponse.Destination();
		other.name = "My own route";
		other.x = 3222;
		other.y = 3218;
		destinations.selectLocal(other);
		follower.sync();
		assertFalse(state.view().following);
		assertSame(other, destinations.getDestination());
		assertEquals("the card can say what the run waits for", "My own route", follower.pausedFor());
		replant();
		follower.sync();
		assertSame(other, destinations.getDestination());
		follower.follow(true);
		assertTrue(state.view().following);
		assertEquals("2. Catherby", destinations.getDestination().name);
		assertNull(follower.pausedFor());
	}

	@Test
	public void aDetourOffTheScreenIsNoLongerWhatThePausedRunWaitsFor() throws Exception
	{
		state.accept(run(), 1, 1_000);
		follower.sync();
		RelayResponse.Destination other = new RelayResponse.Destination();
		other.name = "My own route";
		other.x = 3222;
		other.y = 3218;
		destinations.selectLocal(other);
		follower.sync();
		destinations.dismiss();
		follower.sync();
		assertFalse("dismissing the detour leaves the run paused", state.view().following);
		assertNull(follower.pausedFor());
		follower.follow(true);
		follower.follow(false);
		assertNull("paused by the player, for nothing else", follower.pausedFor());
	}

	@Test
	public void shortestPathTakenOverPausesTheWholeRunSoTheNextReplantDoesNotTakeItBack() throws Exception
	{
		state.accept(run(), 1, 1_000);
		follower.sync();
		follower.takenOver();
		assertFalse(state.view().following);
		assertFalse(route.view().following);
		assertEquals(ShortestPath.TAKEN_OVER, route.view().message);
		replant();
		follower.sync();
		assertEquals("1. Falador farm", destinations.getDestination().name);
		assertFalse(route.view().following);
		follower.follow(true);
		assertEquals("2. Catherby", destinations.getDestination().name);
	}

	@Test
	public void aReplacementConnectionWithAnIdenticalTargetDoesNotBelongToTheOldRun() throws Exception
	{
		state.accept(run(), 1, 1_000);
		follower.sync();
		RelayResponse.Destination sameWords = destinations.getDestination();
		destinations.clear();
		destinations.selectLocal(sameWords);
		assertFalse(follower.owns(sameWords));
		state.clear();
		follower.sync();
		assertSame("ending the old run cannot clear the new connection's destination", sameWords, destinations.getDestination());
	}
}
