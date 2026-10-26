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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Switching views must keep choices alive without reserving a hidden settings page's height. */
public class CompanionViewsTest
{
	@Test
	public void overviewStaysShortAndSwitchingPreservesPartialSharing() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			store.onChanged(key ->
			{
				String value = store.get(key);
				if ("true".equals(value) || "false".equals(value)) config.set(key, Boolean.parseBoolean(value));
			});
			JPanel overview = new JPanel();
			overview.setPreferredSize(new Dimension(205, 30));
			SharingControls sharing = new SharingControls(config, store);
			CompanionViews views = new CompanionViews(overview, sharing);
			int quietHeight = views.getPreferredSize().height;
			assertTrue(overview.isVisible());
			assertFalse(sharing.isVisible());
			assertTrue(quietHeight < 100);
			((JToggleButton) named(views, "companionView.sharing")).doClick();
			assertFalse(overview.isVisible());
			assertTrue(sharing.isVisible());
			assertTrue(views.getPreferredSize().height > quietHeight);
			((JCheckBox) named(sharing, "sharePosition")).doClick();
			((javax.swing.JButton) named(sharing, "customise.presence")).doClick();
			((JToggleButton) named(views, "companionView.overview")).doClick();
			assertEquals(quietHeight, views.getPreferredSize().height);
			sharing.refresh();
			assertFalse("refresh cannot select another view", sharing.isVisible());
			((JToggleButton) named(views, "companionView.sharing")).doClick();
			assertTrue(config.sharePosition());
			assertFalse(config.shareActivity());
			assertFalse(config.shareName());
			assertTrue(named(sharing, "sharingOptions.presence").isVisible());
			assertEquals("indeterminate", ((JCheckBox) named(sharing, "shareGroup.presence")).getClientProperty("JButton.selectedState"));
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	private static Component named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName())) return child;
			if (child instanceof Container)
			{
				Component result = named((Container) child, name);
				if (result != null) return result;
			}
		}
		return null;
	}
}
