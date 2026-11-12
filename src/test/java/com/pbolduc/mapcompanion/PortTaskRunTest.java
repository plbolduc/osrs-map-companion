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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.runelite.client.callback.ClientThread;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * A port-task run, from the map's own fixture: two couriers and a bounty in four stops, each ticked
 * by this client's task slots and never by where the player stands.
 *
 * <p>Stop 1 is Port Khazard (deliver slot 1's fruit, collect slot 2's sand), 2 is Ardougne (deliver
 * the sand), 3 the osprey hunt (slot 3's parts owed reaching zero) and 4 Brimhaven's board (slot 3
 * letting go of the bounty).
 */
public class PortTaskRunTest
{
	private static final long START = 1_000_000L;
	private final Gson gson = new Gson();

	private RelayResponse.FarmRun run() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/port-task-run.json").readAllBytes(), StandardCharsets.UTF_8);
		RelayResponse.FarmRun run = gson.fromJson("{\"directive\":{\"run\":" + json + "}}", RelayResponse.class).directive.run;
		for (RelayResponse.FarmStop stop : run.stops)
		{
			stop.done = false;
		}
		return run;
	}

	private static PortTaskReader.Slot courier(int slot, int taskId, int taken, int delivered)
	{
		return new PortTaskReader.Slot(slot, PortTaskReader.COURIER, taskId, null, null, null, 3, taken, delivered, 0, null);
	}

	private static PortTaskReader.Slot bounty(int slot, int taskId, int remaining)
	{
		return new PortTaskReader.Slot(slot, PortTaskReader.BOUNTY, taskId, null, null, null, 15, 0, 0, remaining, "Osprey feather");
	}

	private static PortTaskReader.Slot empty(int slot)
	{
		return PortTaskReader.Slot.plain(slot, PortTaskReader.EMPTY);
	}

	/** The reading the run was planned from: fruit aboard, sand waiting, nine feathers owed. */
	private static List<PortTaskReader.Slot> planned()
	{
		return Arrays.asList(empty(0), courier(1, 84, 3, 0), courier(2, 121, 0, 0), bounty(3, 466, 9), empty(4));
	}

	private FarmRunState accepted() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.accept(run(), 1, START);
		state.observePorts(planned());
		return state;
	}

	@Test
	public void readsTheMapsPortTaskRunWithItsTicksAndVoyages() throws Exception
	{
		RelayResponse.FarmRun run = gson.fromJson("{\"directive\":{\"run\":" + new String(getClass()
			.getResourceAsStream("/contract/port-task-run.json").readAllBytes(), StandardCharsets.UTF_8) + "}}", RelayResponse.class).directive.run;
		assertNotNull(run);
		assertEquals(FarmRunWire.PORT_TASKS, run.kind);
		assertEquals(4, run.stops.size());
		RelayResponse.FarmStop first = run.stops.get(0);
		assertTrue("a stop ticked by counters may be sent done", first.done);
		assertEquals(Arrays.asList("delivered", "taken"), Arrays.asList(first.tasks.get(0).counter, first.tasks.get(1).counter));
		assertTrue(first.tasks.get(0).ends);
		assertFalse(first.tasks.get(1).ends);
		assertEquals("Port Sarim", first.voyage.from);
		assertEquals("Port Khazard", first.voyage.to);
		assertTrue(first.voyage.line.length > 2);
		assertNull("a hunt docks nowhere", run.stops.get(2).voyage.to);
		assertTrue(FarmRunState.tracked(run.stops.get(3)));
	}

	@Test
	public void dropsATickItCannotReadAndNeverCallsATickedStopVisited() throws Exception
	{
		RelayResponse.FarmRun raw = run();
		RelayResponse.FarmStop stop = raw.stops.get(1);
		RelayResponse.RunTask sailed = new RelayResponse.RunTask();
		sailed.slot = 2;
		sailed.taskId = 121;
		sailed.counter = "sailed";
		sailed.n = 3;
		sailed.text = "A counter this build has never heard of";
		RelayResponse.RunTask sixth = new RelayResponse.RunTask();
		sixth.slot = 5;
		sixth.taskId = 121;
		sixth.counter = "taken";
		sixth.n = 3;
		sixth.text = "A sixth slot";
		stop.tasks.add(sailed);
		stop.tasks.add(sixth);
		stop.visited = true;
		RelayResponse.FarmStop clean = FarmRunWire.normalize(raw).stops.get(1);
		assertEquals(1, clean.tasks.size());
		assertEquals("delivered", clean.tasks.get(0).counter);
		assertFalse(clean.visited);
	}

	@Test
	public void eachStopTicksWhenItsCountersReachTheirTargetsAndNotBefore() throws Exception
	{
		FarmRunState state = accepted();
		assertTrue(state.view().done.isEmpty());
		assertEquals(1, state.view().next().n);
		// The fruit is in, the sand not yet loaded: stop 1 waits on both.
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 3), courier(2, 121, 0, 0), bounty(3, 466, 9), empty(4)));
		assertTrue(state.view().done.isEmpty());
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 3), courier(2, 121, 3, 0), bounty(3, 466, 9), empty(4)));
		assertEquals(Collections.singleton(1), state.view().done);
		assertEquals(2, state.view().next().n);
		assertFalse(state.view().changed);
	}

	@Test
	public void theLastCrateEmptyingTheSlotStillTicksTheDropOff() throws Exception
	{
		FarmRunState state = accepted();
		// The game finishes the fruit task on its last crate and frees the slot at once.
		state.observePorts(Arrays.asList(empty(0), empty(1), courier(2, 121, 3, 0), bounty(3, 466, 9), empty(4)));
		assertEquals(Collections.singleton(1), state.view().done);
		assertFalse(state.view().changed);
	}

	@Test
	public void aCounterThatJumpsPastAStopTicksEveryStopItPassed() throws Exception
	{
		FarmRunState state = accepted();
		// One reading after a fast stretch: the fruit and the sand both in, and every feather collected.
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 3), courier(2, 121, 3, 3), bounty(3, 466, 0), empty(4)));
		assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), state.view().done);
		assertEquals(4, state.view().next().n);
		// The game frees the finished slots; that is no change, and handing the bounty in ends the run.
		state.observePorts(Arrays.asList(empty(0), empty(1), empty(2), empty(3), empty(4)));
		assertFalse(state.view().changed);
		assertNull(state.view().next());
		// The card says the run is over and what it came to, where it said "Route finished." alone.
		assertEquals("Run finished · 4 done", RunView.progressLine(state.view()));
	}

	@Test
	public void aTaskLetGoOfBeforeItsWorkWasDoneIsAChangeNotADoneStop() throws Exception
	{
		FarmRunState state = accepted();
		// The sand abandoned at the board before a crate of it was loaded.
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 0), empty(2), bounty(3, 466, 9), empty(4)));
		assertTrue(state.view().changed);
		assertFalse(state.view().done.contains(2));
		// And a bounty dropped with feathers still owed is no hand-in.
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 0), courier(2, 121, 0, 0), empty(3), empty(4)));
		assertTrue(state.view().changed);
		assertFalse(state.view().done.contains(4));
	}

	@Test
	public void aSlotReplacedMidRunSaysTheRunHasChanged() throws Exception
	{
		FarmRunState state = accepted();
		// The sand task abandoned before its crates were loaded, and another task taken in its slot.
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 0), courier(2, 2, 0, 0), bounty(3, 466, 9), empty(4)));
		assertTrue(state.view().changed);
		assertFalse("a change ticks nothing", state.view().done.contains(1));
		// And put back as it was, the run is the run again.
		state.observePorts(planned());
		assertFalse(state.view().changed);
	}

	@Test
	public void aTaskTakenIntoAFreeSlotSaysTheRunHasChanged() throws Exception
	{
		FarmRunState state = accepted();
		state.observePorts(Arrays.asList(courier(0, 2, 0, 0), courier(1, 84, 3, 0), courier(2, 121, 0, 0), bounty(3, 466, 9), empty(4)));
		assertTrue(state.view().changed);
	}

	@Test
	public void anUnreadableSlotTicksNothingAndChangesNothing() throws Exception
	{
		FarmRunState state = accepted();
		state.observePorts(Arrays.asList(empty(0), PortTaskReader.Slot.plain(1, PortTaskReader.UNKNOWN),
			courier(2, 121, 3, 0), bounty(3, 466, 9), empty(4)));
		assertTrue(state.view().done.isEmpty());
		assertFalse(state.view().changed);
		state.observePorts(null);
		assertTrue(state.view().done.isEmpty());
	}

	@Test
	public void standingOnTheLedgerTableTicksNothing() throws Exception
	{
		FarmRunState state = accepted();
		RelayResponse.FarmStop first = state.view().run.stops.get(0);
		state.observe(null, null, FarmRunState.point(first), START + 600);
		assertTrue(state.view().here.contains(1));
		assertTrue(state.view().done.isEmpty());
		assertTrue(state.view().visited.isEmpty());
	}

	@Test
	public void aRerouteKeepsADoneStopAtItsNumberAndDropsOneThatMoved() throws Exception
	{
		FarmRunState state = accepted();
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 3), courier(2, 121, 3, 0), bounty(3, 466, 9), empty(4)));
		assertEquals(Collections.singleton(1), state.view().done);
		RelayResponse.FarmRun rerouted = run();
		rerouted.stops.get(0).done = true;
		// The map planned the rest again: the hunt now comes at 2 and Ardougne at 3.
		RelayResponse.FarmStop ardougne = rerouted.stops.get(1);
		RelayResponse.FarmStop hunt = rerouted.stops.get(2);
		ardougne.n = 3;
		hunt.n = 2;
		state.accept(rerouted, 2, START + 5_000);
		assertEquals(Collections.singleton(1), state.view().done);
		assertEquals(2, state.view().next().n);
		assertEquals("Osprey", state.view().next().name);
	}

	@Test
	public void theLegCarriesItsVoyageToTheFollower() throws Exception
	{
		FarmRunState state = accepted();
		DestinationState destinations = new DestinationState(gson);
		DestinationRoute route = new DestinationRoute();
		FarmRunRoute follower = new FarmRunRoute(state, destinations, route, new ClientThread()
		{
			@Override
			public void invokeLater(Runnable task)
			{
				task.run();
			}
		});
		follower.sync();
		RelayResponse.Destination leg = destinations.getDestination();
		assertEquals("1. Port Khazard", leg.name);
		RelayResponse.RouteOption way = leg.route.options.get(0);
		/* The map's line for the leg, which now says how far before it says from where. */
		assertEquals("Sail 513 tiles · from Port Sarim", way.label);
		// The Sail step says the sailed distance; the leg's run-tile cost would contradict it.
		assertNull(way.tiles);
		assertEquals("Port Khazard", way.voyage.to);
		assertEquals(state.view().run.stops.get(0).voyage.line.length, way.voyage.line.length);
	}

	@Test
	public void aPortTaskRunCountsItsStopsRatherThanTrackedPatches() throws Exception
	{
		RelayResponse.FarmRun ports = run();
		/*
		 * This read "0 of 4 stops done". Each of the fixture's legs now says its sail in numbers, 513,
		 * 108, 90 and 128, so the line adds what is left of them, as a charting run's does.
		 */
		assertEquals("0 of 4 stops done · 839 tiles left", RunView.progressLine(new FarmRunState.View(FarmRunWire.normalize(ports),
			Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), true, 0, 1, null)));
	}
}
