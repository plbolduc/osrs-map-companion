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

import net.runelite.api.events.ActorDeath;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * What turning the plugin off forgets, so that turning it on again starts from nothing.
 *
 * <p>The plugin is {@link TestSidebar}'s: the real one, holding the real readers and clients, with
 * a client thread that runs what it is handed at once. So {@code shutDown} here runs the whole
 * teardown a game tick's state is ended by, not a copy of it.
 */
public class TurnedOffTest
{
	private final TestSidebar sidebar = new TestSidebar();

	public TurnedOffTest() throws Exception
	{
	}

	/**
	 * Off, another character, on again: the death was the first character's, and for an hour it
	 * would have been posted, and drawn on the map, as the second's.
	 */
	@Test
	public void aDeathIsForgottenWithThePlugin() throws Exception
	{
		DeathWatcher deaths = sidebar.injector.getInstance(DeathWatcher.class);
		deaths.record(new ActorDeath(sidebar.game.client().getLocalPlayer()));
		assertNotNull("the death is remembered while the plugin runs", deaths.snapshot(System.currentTimeMillis()));

		sidebar.plugin.shutDown();

		assertNull("and not by the next lifetime", deaths.snapshot(System.currentTimeMillis()));
	}
}
