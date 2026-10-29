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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The tabs and the Settings page keep what is open without reserving a hidden page's height.
 *
 * <p>Rewritten when Sharing stopped being a tab and became the Settings page behind the gear: the
 * partial sharing choice and the open Customise this test always kept now survive leaving that page
 * and coming back, and the tabs are Home, Group and Stars.
 */
public class CompanionViewsTest
{
	@Test
	public void homeStaysShortAndTheGroupTabCountsItsFriends() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel home = sized(30);
			JPanel group = sized(400);
			JPanel stars = sized(600);
			List<Boolean> starsShown = new ArrayList<>();
			CompanionViews views = new CompanionViews(home, group, stars, starsShown::add);
			int quiet = views.getPreferredSize().height;
			assertTrue("a hidden tab reserves no height", quiet < 100);
			assertTrue(home.isVisible());
			assertFalse(group.isVisible());

			views.groupCount(3);
			JToggleButton groupTab = (JToggleButton) named(views, "companionView.group");
			assertEquals("Group · 3", groupTab.getText());
			groupTab.doClick();
			assertTrue(group.isVisible());
			assertFalse(home.isVisible());
			assertTrue(views.getPreferredSize().height > quiet);

			((JToggleButton) named(views, "companionView.stars")).doClick();
			assertEquals(Boolean.TRUE, starsShown.get(starsShown.size() - 1));
			views.groupCount(0);
			assertEquals("Group", groupTab.getText());

			/* Before a group is joined there are no tabs, and Home is the page whichever was open. */
			views.tabsShown(false);
			assertFalse(named(views, "companionView.home").getParent().isVisible());
			assertTrue(home.isVisible());
			assertFalse(stars.isVisible());
			assertEquals(Boolean.FALSE, starsShown.get(starsShown.size() - 1));
			assertEquals(CompanionViews.View.HOME, views.selected());
		});
	}

	@Test
	public void settingsTakesThePageAndGivesItBackWithChoicesKept() throws Exception
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
			JPanel daily = sized(30);
			SharingControls sharing = new SharingControls(config, store);
			SidebarPages pages = new SidebarPages(daily, sharing);
			int quiet = pages.getPreferredSize().height;
			assertTrue(daily.isVisible());
			assertFalse(pages.settingsOpen());
			assertTrue("the closed Settings page reserves no height", quiet < 100);

			pages.open(true);
			assertFalse(daily.isVisible());
			assertTrue(pages.settingsOpen());
			assertTrue(pages.getPreferredSize().height > quiet);
			((JCheckBox) named(sharing, "sharePosition")).doClick();
			((JButton) named(sharing, "customise.presence")).doClick();

			((JButton) named(pages, "settings.back")).doClick();
			assertTrue(daily.isVisible());
			assertEquals(quiet, pages.getPreferredSize().height);
			sharing.refresh();
			assertFalse("refresh cannot open the page", pages.settingsOpen());

			pages.open(true);
			assertTrue(config.sharePosition());
			assertFalse(config.shareActivity());
			assertFalse(config.shareName());
			assertTrue(named(sharing, "sharingOptions.presence").isVisible());
			assertEquals("indeterminate", ((JCheckBox) named(sharing, "shareGroup.presence")).getClientProperty("JButton.selectedState"));
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	private static JPanel sized(int height)
	{
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(205, height));
		return panel;
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
