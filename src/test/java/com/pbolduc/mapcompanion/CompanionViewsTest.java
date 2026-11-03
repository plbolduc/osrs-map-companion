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

	/**
	 * A friend's or a meetup's Route is answered on Home's destination card, so when the answer lands
	 * while Group still shows, Home comes forward, once. A refusal stays under its button, a tab the
	 * player has moved to is left alone, and a star's way is drawn on Stars, which stays where it is.
	 */
	@Test
	public void aWayFromGroupBringsHomeForwardOnceItLands() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel home = sized(30);
			CompanionViews views = new CompanionViews(home, sized(400), sized(600), shown -> { });
			views.select(CompanionViews.View.GROUP);

			assertFalse("still planning", views.showAnswer(said("friend:cara", true, null, null)));
			assertFalse("refused: the line under the button says why", views.showAnswer(said("friend:cara", false, "The map is busy.", null)));
			assertEquals(CompanionViews.View.GROUP, views.selected());

			RoutePress.Said cara = said("friend:cara", false, null, "3222,3218,0|302|Cara|null");
			assertTrue(views.showAnswer(cara));
			assertEquals(CompanionViews.View.HOME, views.selected());
			assertTrue(home.isVisible());

			/* Back on Group with the same answer still the last one said: it has been shown. */
			views.select(CompanionViews.View.GROUP);
			assertFalse(views.showAnswer(cara));
			assertEquals(CompanionViews.View.GROUP, views.selected());

			assertTrue("a meetup's way too", views.showAnswer(said("meetup:star-hunt", false, null, "3222,3218,0|302|Star hunt|null")));
			assertEquals(CompanionViews.View.HOME, views.selected());

			views.select(CompanionViews.View.STARS);
			assertFalse("a star's way stays on Stars", views.showAnswer(said("star", false, null, "2974,3241,0|302|Rimmington mine|null")));
			assertFalse("a friend's way that lands after the player left Group", views.showAnswer(said("friend:ash", false, null,
				"3165,3487,0|330|Ash|null")));
			assertEquals(CompanionViews.View.STARS, views.selected());
		});
	}

	private static RoutePress.Said said(String source, boolean planning, String message, String key)
	{
		return new RoutePress.Said(source, planning, message, key, 0);
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
			/* A switch and an open fold, which were a field's box and its group's Customise before sharing went by group. */
			((JCheckBox) named(sharing, MapCompanionConfig.KEY_SHARE_MAP_PRESENCE)).doClick();
			assertTrue(SidebarWords.press(sharing, "▸ What each one sends"));

			((JButton) named(pages, "settings.back")).doClick();
			assertTrue(daily.isVisible());
			assertEquals(quiet, pages.getPreferredSize().height);
			sharing.refresh();
			assertFalse("refresh cannot open the page", pages.settingsOpen());

			pages.open(true);
			assertTrue(config.sharePosition());
			assertTrue(store.on("shareActivity"));
			assertFalse(config.shareName());
			assertTrue(named(sharing, "sharing.details").isVisible());
			assertTrue(((JCheckBox) named(sharing, MapCompanionConfig.KEY_SHARE_MAP_PRESENCE)).isSelected());
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
