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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

	/**
	 * A search waits before it is asked: a moment after the last key, or as long as the map said to,
	 * which can be minutes. Turned off inside that wait, the plugin still sent it when the wait ended.
	 */
	@Test
	public void aSearchStillWaitingIsNeverAskedOnceThePluginStops() throws Exception
	{
		SearchClient search = sidebar.injector.getInstance(SearchClient.class);
		SwingUtilities.invokeAndWait(() -> search.typed("lumbridge"));
		assertEquals("the search waits for the typing to stop", 1, sidebar.waiting.size());
		Runnable timer = sidebar.waiting.get(0);

		SwingUtilities.invokeAndWait(sidebar.panel::stopRefreshing);

		assertTrue("its timer is cancelled with the plugin", sidebar.waiting.isEmpty());
		/* And one that had fired already, queued behind the shutdown, finds nothing left to ask for. */
		SwingUtilities.invokeAndWait(timer);
		assertEquals("no question is left to ask", "", search.result().query);
		assertTrue("nothing was sent: " + sidebar.asked, sidebar.asked.isEmpty());
	}

	/**
	 * Closing the sidebar leaves a star list that was asked for to land, so the tab has it when the
	 * player comes back. Turning the plugin off closes the sidebar too, and there the call is
	 * cancelled: nothing of a reply is read by a plugin that is off.
	 */
	@Test
	public void aStarListStillOnItsWayIsDroppedWhenThePluginStops() throws Exception
	{
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch answer = new CountDownLatch(1);
		CountDownLatch answered = new CountDownLatch(1);
		String feed = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "companion-stars.sample.json")),
			StandardCharsets.UTF_8);
		sidebar.map = chain ->
		{
			asked.countDown();
			try
			{
				answer.await();
			}
			catch (InterruptedException stopped)
			{
				throw new IOException(stopped);
			}
			answered.countDown();
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
				.body(ResponseBody.create(MediaType.get("application/json"), feed)).build();
		};
		StarsClient stars = sidebar.injector.getInstance(StarsClient.class);
		Field views = MapCompanionPanel.class.getDeclaredField("views");
		views.setAccessible(true);
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				((CompanionViews) views.get(sidebar.panel)).select(CompanionViews.View.STARS);
			}
			catch (IllegalAccessException e)
			{
				throw new AssertionError(e);
			}
			sidebar.panel.onActivate();
		});
		assertTrue("the Stars tab asked for its list", asked.await(3, TimeUnit.SECONDS));

		SwingUtilities.invokeAndWait(sidebar.panel::stopRefreshing);
		answer.countDown();
		assertTrue(answered.await(3, TimeUnit.SECONDS));
		for (int i = 0; i < 40; i++)
		{
			SwingUtilities.invokeAndWait(() -> assertNull("a stopped plugin keeps no list", stars.model.feed));
			Thread.sleep(5);
		}
	}
}
