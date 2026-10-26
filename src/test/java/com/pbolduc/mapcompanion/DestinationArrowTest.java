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
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The game's hint arrow is one per client and shared with the game and every other plugin. These
 * are the ways this plugin could take one that is not its own.
 */
public class DestinationArrowTest
{
	/** The arrow as the client holds it, and a scene loaded around Lumbridge. */
	private WorldPoint arrow;
	private int sceneBaseX = 3200;

	private final Client client = (Client) Proxy.newProxyInstance(
		Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "hasHintArrow":
					return arrow != null;
				case "getHintArrowPoint":
					return arrow;
				case "setHintArrow":
					arrow = (WorldPoint) args[0];
					return null;
				case "clearHintArrow":
					arrow = null;
					return null;
				case "getTopLevelWorldView":
					return view();
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});

	private WorldView view()
	{
		return (WorldView) Proxy.newProxyInstance(
			WorldView.class.getClassLoader(), new Class<?>[]{WorldView.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getPlane":
						return 0;
					case "getBaseX":
						return sceneBaseX;
					case "getBaseY":
						return 3200;
					case "getSizeX":
					case "getSizeY":
						return 104;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
	}

	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, "http://localhost");
	private final DestinationState state = new DestinationState(new Gson());
	private final DestinationArrow arrows = new DestinationArrow(client, config, state);

	private void destination(int x, int y)
	{
		RelayResponse.Destination d = new RelayResponse.Destination();
		d.x = x;
		d.y = y;
		d.name = "Star";
		state.selectLocal(d);
	}

	@Test
	public void pointsAtADestinationInTheLoadedSceneAndTakesItDownAfter()
	{
		destination(3222, 3218);
		arrows.sync();
		assertEquals(new WorldPoint(3222, 3218, 0), arrow);
		state.dismiss();
		arrows.sync();
		assertNull(arrow);
	}

	@Test
	public void pointsAtNothingTheGameHasNotLoaded()
	{
		destination(2440, 3090);
		arrows.sync();
		assertNull(arrow);
	}

	@Test
	public void neverReplacesAnArrowItDidNotSet()
	{
		WorldPoint quest = new WorldPoint(3210, 3210, 0);
		arrow = quest;
		destination(3222, 3218);
		arrows.sync();
		assertEquals(quest, arrow);
		arrows.clear();
		assertEquals("clearing takes down only its own", quest, arrow);
	}

	@Test
	public void letsGoOfAnArrowSomebodyElseReplaced()
	{
		destination(3222, 3218);
		arrows.sync();
		WorldPoint quest = new WorldPoint(3210, 3210, 0);
		arrow = quest;
		state.dismiss();
		arrows.sync();
		assertEquals(quest, arrow);
	}

	@Test
	public void staysOffWhenTurnedOff()
	{
		config.set("destinationHintArrow", false);
		destination(3222, 3218);
		arrows.sync();
		assertNull(arrow);
	}
}
