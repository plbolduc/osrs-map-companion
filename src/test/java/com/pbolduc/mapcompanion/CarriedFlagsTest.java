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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * A run's bring list says which teleports the player carries, and working that out matches every
 * carried item's name against every teleport the map knows. It was done on every tick of a run.
 * It is done when the answer can have changed: a container changed, the run is another one, the
 * inventory had not arrived, and once a minute besides.
 *
 * <p>The plugin is {@link TestSidebar}'s, with an evaluator that counts how often it is asked and
 * answers what the test says the player carries.
 */
public class CarriedFlagsTest
{
	private final TestSidebar sidebar = new TestSidebar();
	private final FarmRunState runs = sidebar.injector.getInstance(FarmRunState.class);
	private int asked;
	private List<String> carrying = new ArrayList<>(Arrays.asList("ardougne_cloak"));

	public CarriedFlagsTest() throws Exception
	{
		Field evaluator = MapCompanionPlugin.class.getDeclaredField("capabilityEvaluator");
		evaluator.setAccessible(true);
		evaluator.set(sidebar.plugin, new CapabilityEvaluator(sidebar.game.client())
		{
			@Override
			public List<String> evaluate()
			{
				asked++;
				return carrying;
			}
		});
		/* A tick that is not the once-a-minute one, so each reading below is one of the others. */
		sidebar.game.tick();
		runs.bind(sidebar.identity.id());
		String json = new String(getClass().getResourceAsStream("/contract/farm-run.json").readAllBytes(),
			java.nio.charset.StandardCharsets.UTF_8);
		runs.acceptFor(sidebar.identity.id(), "contract-relay-epoch", sidebar.gson.fromJson(json, RelayResponse.FarmRun.class),
			1, System.currentTimeMillis());
	}

	@Test
	public void theFlagsAreWorkedOutWhenAContainerChangesAndNotEachTick()
	{
		sidebar.plugin.readCarried();
		sidebar.plugin.readCarried();
		sidebar.plugin.readCarried();
		assertEquals("three ticks with nothing changed ask once", 1, asked);
		assertEquals(Arrays.asList("ardougne_cloak"), runs.view().carried);

		carrying = new ArrayList<>(Arrays.asList("ardougne_cloak", "skills_necklace"));
		sidebar.plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.WORN, null));
		sidebar.plugin.readCarried();
		sidebar.plugin.readCarried();
		assertEquals("a necklace put on is a change, read once", 2, asked);
		assertEquals(Arrays.asList("ardougne_cloak", "skills_necklace"), runs.view().carried);
	}

	/** Before the inventory arrives there is no answer, and "not yet" is asked again rather than kept. */
	@Test
	public void anInventoryNotYetReceivedIsAskedForAgain()
	{
		List<String> held = carrying;
		carrying = null;
		sidebar.plugin.readCarried();
		sidebar.plugin.readCarried();
		assertEquals(2, asked);
		assertNull(runs.view().carried);

		carrying = held;
		sidebar.plugin.readCarried();
		sidebar.plugin.readCarried();
		assertEquals("answered, it is kept", 3, asked);
		assertEquals(held, runs.view().carried);
	}

	/** A run forgets what its player carried when it ends, so the next one is told afresh. */
	@Test
	public void anotherRunIsToldWhatIsCarried() throws Exception
	{
		sidebar.plugin.readCarried();
		assertEquals(1, asked);

		runs.clear();
		runs.bind(sidebar.identity.id());
		String json = new String(getClass().getResourceAsStream("/contract/farm-run.json").readAllBytes(),
			java.nio.charset.StandardCharsets.UTF_8);
		runs.acceptFor(sidebar.identity.id(), "contract-relay-epoch", sidebar.gson.fromJson(json, RelayResponse.FarmRun.class),
			1, System.currentTimeMillis());
		assertNull("the new run starts not knowing", runs.view().carried);

		sidebar.plugin.readCarried();
		assertEquals(2, asked);
		assertEquals(carrying, runs.view().carried);
	}

	/** Once a minute whatever else: the bound on an item the client could not name when it was asked. */
	@Test
	public void onceAMinuteItIsWorkedOutAnyway()
	{
		sidebar.plugin.readCarried();
		assertEquals(1, asked);
		for (int tick = 1; tick < 100; tick++)
		{
			sidebar.game.tick();
			sidebar.plugin.readCarried();
		}
		assertEquals("ninety-nine ticks on, the hundredth tick of the game's clock has come round once", 2, asked);
	}
}
