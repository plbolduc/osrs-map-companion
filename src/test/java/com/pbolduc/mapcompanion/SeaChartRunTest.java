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
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * A sea charting run, from the map's own fixture: the first four tasks of the Kharidian Sea from a
 * boat at Port Sarim, the first charted already. The first two are done on foot, Chartin' Charles
 * inside the Pandemonium Cave and the spyglass spot at its entrance, so the run docks, walks, and
 * walks back to the boat before it sails on, in the same steps a port-task leg uses. Each stop is
 * ticked by the map's {@code visited}, sent when this character's charts say its task is done, and
 * never by the player or the boat being near it.
 */
public class SeaChartRunTest
{
	private static final long START = 1_000_000L;
	private final Gson gson = new Gson();

	private RelayResponse.FarmRun run(boolean firstCharted) throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/sea-chart-run.json").readAllBytes(), StandardCharsets.UTF_8);
		RelayResponse.FarmRun run = gson.fromJson("{\"directive\":{\"run\":" + json + "}}", RelayResponse.class).directive.run;
		run.stops.get(0).visited = firstCharted;
		return run;
	}

	private static WorldPoint at(RelayResponse.FarmStop stop)
	{
		return new WorldPoint(stop.tile.x, stop.tile.y, 0);
	}

	@Test
	public void readsTheMapsChartingRunAsAKindItFollows() throws Exception
	{
		RelayResponse.FarmRun run = FarmRunWire.normalize(run(true));
		assertNotNull("a kind this build knows is kept", run);
		assertEquals(FarmRunWire.SEA_CHARTS, run.kind);
		assertEquals(4, run.stops.size());
		assertTrue("the map's tick survives the normalizer", run.stops.get(0).visited);
		assertEquals("Port Sarim", run.stops.get(0).voyage.from);
		assertEquals("a task done on foot docks", "The Pandemonium", run.stops.get(0).voyage.to);
		assertNull("the next task from the same dock is walked to", run.stops.get(1).voyage);
		assertEquals("a sail after a task ashore leaves from the dock", "The Pandemonium", run.stops.get(2).voyage.from);
		assertNull("a sail between tasks at sea docks nowhere", run.stops.get(3).voyage.to);
		assertEquals("a sail at sea leaves from the task before", run.stops.get(2).name, run.stops.get(3).voyage.from);
		assertFalse("a charting stop has no evidence of its own to wait for", FarmRunState.tracked(run.stops.get(1)));
	}

	/**
	 * A task done on foot is followed by the steps a port-task leg already uses: the walk to the
	 * boat, aboard, the sail, ashore at the dock, then the walk into the cave, each row passed by
	 * where the player is and whether they are aboard. Nothing in it is new to this build.
	 */
	@Test
	public void followsATaskDoneOnFootThroughItsDockAndWalk() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.accept(run(false), 1, START);
		RelayResponse.FarmStop cave = state.view().run.stops.get(0);
		RelayResponse.RouteOption way = FarmRunRoute.destination(state.view(), cave).route.options.get(0);
		List<RelayResponse.RouteStep> steps = way.steps;
		DestinationRoute.Sailing ashore = new DestinationRoute.Sailing(false, null);
		DestinationRoute.Sailing aboard = new DestinationRoute.Sailing(true, null);

		assertEquals(0, DestinationRoute.next(steps, 0, new WorldPoint(3040, 3200, 0), ashore, null, way.voyage));
		assertEquals("at the boat", 1, DestinationRoute.next(steps, 0, new WorldPoint(3051, 3192, 0), ashore, null, way.voyage));
		assertEquals("aboard", 2, DestinationRoute.next(steps, 1, new WorldPoint(3053, 3196, 0), aboard, null, way.voyage));
		assertEquals("sailing", 2, DestinationRoute.next(steps, 2, new WorldPoint(3060, 3100, 0), aboard, null, way.voyage));
		assertEquals("off the Pandemonium", 3, DestinationRoute.next(steps, 2, new WorldPoint(3072, 2990, 0), aboard, null, way.voyage));
		assertEquals("docked and ashore", 4, DestinationRoute.next(steps, 3, new WorldPoint(3070, 2986, 0), ashore, null, way.voyage));
		assertEquals("at the cave's entrance", 5, DestinationRoute.next(steps, 4, new WorldPoint(3046, 2996, 0), ashore, null, way.voyage));
		assertEquals("by Chartin' Charles", steps.size(), DestinationRoute.next(steps, 5, at(cave), ashore, null, way.voyage));

		// Arriving is not charting: the stop waits for the map's tick.
		state.observe(null, null, at(cave), START + 1);
		assertTrue(state.view().visited.isEmpty());
		assertEquals(1, state.view().next().n);

		// The sail on starts by walking back to the boat, ashore, and only then boards.
		RelayResponse.FarmStop onward = state.view().run.stops.get(2);
		RelayResponse.RouteOption back = FarmRunRoute.destination(state.view(), onward).route.options.get(0);
		WorldPoint entrance = at(state.view().run.stops.get(1));
		assertEquals(0, DestinationRoute.next(back.steps, 0, entrance, ashore, null, back.voyage));
		assertEquals(1, DestinationRoute.next(back.steps, 0, new WorldPoint(3070, 2986, 0), ashore, null, back.voyage));
		assertEquals(2, DestinationRoute.next(back.steps, 1, new WorldPoint(3070, 2988, 0), aboard, null, back.voyage));
	}

	@Test
	public void standingOnAChartingStopNeverTicksIt() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.accept(run(false), 1, START);
		for (RelayResponse.FarmStop stop : state.view().run.stops)
		{
			state.observe(null, null, at(stop), START + 1);
		}
		assertTrue(state.view().visited.isEmpty());
		assertEquals(1, state.view().next().n);
		assertEquals("0 of 4 charted", RunView.progressLine(state.view()));

		// The same stops in a run the plugin ticks by position: sailing past them would visit each one.
		RelayResponse.FarmRun preset = run(false);
		preset.kind = "preset";
		FarmRunState farm = new FarmRunState();
		farm.accept(preset, 1, START);
		for (RelayResponse.FarmStop stop : farm.view().run.stops)
		{
			farm.observe(null, null, at(stop), START + 1);
		}
		assertEquals(4, farm.view().visited.size());
	}

	@Test
	public void theMapsTickMovesTheRunOnAndSaysCharted() throws Exception
	{
		FarmRunState state = new FarmRunState();
		state.accept(run(false), 1, START);
		state.accept(run(true), 2, START + 10);
		FarmRunState.View view = state.view();
		assertTrue(view.visited.contains(1));
		assertEquals(2, view.next().n);
		assertEquals("Charted", RunView.status(view, view.run.stops.get(0)));
		assertEquals("1 of 4 charted", RunView.progressLine(view));

		// A tick the map has sent stays when a later frame of the same run repeats the stop.
		state.observe(null, null, at(view.run.stops.get(0)), START + 20);
		assertTrue(state.view().visited.contains(1));
	}
}
