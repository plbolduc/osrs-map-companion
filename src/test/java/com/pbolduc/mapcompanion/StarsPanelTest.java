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
import java.awt.Component;
import java.awt.Container;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The top of the Stars tab is the picked world and the filters a player changes daily. Sync with
 * the website and the levels the list is measured by are set once, so they wait in More filters.
 */
public class StarsPanelTest
{
	@Test
	public void syncAndTheLevelsLineWaitInMoreFilters() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), new Pairing(store, new Gson()),
				new MemberIdentity(store), store);
			StarsPanel stars = new StarsPanel(service, null, null, null, null, () -> MapCompanionConfig.DEFAULT_MAP_URL);
			Component sync = named(stars, "stars.sync");
			assertNotNull(sync);
			assertFalse("hidden until More filters is opened", showing(sync, stars));
			((JToggleButton) named(stars, "stars.filters")).doClick();
			assertTrue(showing(sync, stars));
		});
	}

	/** Visible, and every ancestor up to the panel visible too. */
	private static boolean showing(Component component, Container root)
	{
		for (Component at = component; at != null && at != root; at = at.getParent())
		{
			if (!at.isVisible())
			{
				return false;
			}
		}
		return true;
	}

	private static Component named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
