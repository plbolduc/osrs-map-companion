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
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/**
 * Meet here from the world map before the map has said yes to this group: after Map address was
 * changed in RuneLite's settings, say, when the status card asks for Connect. The tabs and the Group
 * view are hidden then, so selecting the Group view hid Home with them and left the column empty.
 */
public class MeetHereBeforeConnectTest
{
	private final TestSidebar sidebar = new TestSidebar();

	public MeetHereBeforeConnectTest() throws Exception
	{
	}

	@Test
	public void leavesHomeWhereItWas() throws Exception
	{
		sidebar.store.set(MapCompanionConfig.KEY_RELAY_URL, "https://another-map.example");
		assertFalse(sidebar.pairing.hasServerConsent());
		SwingUtilities.invokeAndWait(() -> sidebar.panel.meetAt(new WorldPoint(3565, 3288, 0)));
		Field views = MapCompanionPanel.class.getDeclaredField("views");
		views.setAccessible(true);
		assertEquals(CompanionViews.View.HOME, ((CompanionViews) views.get(sidebar.panel)).selected());
	}
}
