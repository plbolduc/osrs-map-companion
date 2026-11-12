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
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the sidebar owes the game while nobody is looking at it: closed, behind another plugin's
 * panel, or with the plugin turned off.
 *
 * <p>The real {@link MapCompanionPanel}, from {@link TestSidebar}. Its cards are redrawn by a timer
 * that runs only while the sidebar is open, so each test here closes it first.
 */
public class SidebarLifetimeTest
{
	private final TestSidebar sidebar = new TestSidebar();

	public SidebarLifetimeTest() throws Exception
	{
	}

	/**
	 * A route key is pressed in the game, where the sidebar is usually closed. A destination that
	 * arrives after it closed has no card drawn for it until the key's own press draws one.
	 */
	@Test
	public void aKeyFollowsADestinationThatArrivedWhileTheSidebarWasClosed() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			sidebar.panel.onActivate();
			sidebar.panel.onDeactivate();
		});
		TestSidebar.settle();

		DestinationState destinations = sidebar.injector.getInstance(DestinationState.class);
		DestinationRoute route = sidebar.injector.getInstance(DestinationRoute.class);
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")),
			StandardCharsets.UTF_8);
		destinations.accept(sidebar.gson.fromJson(reply, RelayResponse.class).directive);
		route.sync(destinations.getDestination(), null);
		assertNotNull("the destination came with a way", route.view());
		assertFalse("a route that merely arrived is not followed", route.view().following);

		SwingUtilities.invokeAndWait(() -> sidebar.panel.act(MapCompanionPanel.Act.FOLLOW));

		assertTrue("the Follow key follows it, as the card's button would have", route.view().following);
	}
}
