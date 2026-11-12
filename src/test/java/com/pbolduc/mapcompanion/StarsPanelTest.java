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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The top of the Stars tab is the pinned star and the filters a player changes daily, and a picked
 * star's buttons are in its own card. The levels the list is measured by are read once, so they
 * wait in More filters; the filters stay on this machine, with no switch that sends them anywhere.
 */
public class StarsPanelTest
{
	@Test
	public void theLevelsLineWaitsInMoreFiltersAndNothingSyncs() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), new Pairing(store, new Gson()),
				new MemberIdentity(store), store);
			StarsPanel stars = new StarsPanel(service, null, null, null, null, () -> MapCompanionConfig.DEFAULT_MAP_URL,
				new DestinationRoute(), null, null, null, rerouting -> null, () -> { });
			Component levels = named(stars, "stars.levels");
			assertNotNull(levels);
			assertFalse("hidden until More filters is opened", showing(levels, stars));
			((JToggleButton) named(stars, "stars.filters")).doClick();
			assertTrue(showing(levels, stars));
			assertNull("no Sync filters with website", named(stars, "stars.sync"));
		});
	}

	/** P2P and F2P carry the world switcher's own members and free stars, and keep their words. */
	@Test
	public void theWorldChipsAskForTheGamesOwnMembersAndFreeStars() throws Exception
	{
		java.util.Map<String, Integer> asked = new java.util.concurrent.ConcurrentHashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), new Pairing(store, new Gson()),
				new MemberIdentity(store), store);
			StarsPanel stars = new StarsPanel(service, null, null, null, null, () -> MapCompanionConfig.DEFAULT_MAP_URL,
				new DestinationRoute(), null, null, null, rerouting -> null, () -> { });
			stars.pictures(new GameSprites(null, null, null)
			{
				@Override
				void sprite(javax.swing.AbstractButton button, int spriteId, int box)
				{
					asked.put(button.getText(), spriteId);
				}
			});
		});
		assertEquals(Integer.valueOf(net.runelite.api.gameval.SpriteID.WorldswitcherStars.MEMBERS), asked.get("P2P"));
		assertEquals(Integer.valueOf(net.runelite.api.gameval.SpriteID.WorldswitcherStars.FREE), asked.get("F2P"));
	}

	/**
	 * Route and Pin sit in the card of the star they act on, so they are offered for a world the player
	 * picked, never one picked for them, and never one their own filters have just hidden from the
	 * list. This used to assert a Selected world block at the head of the tab: it repeated the row
	 * above the filters to carry the two buttons, a scroll away from the star that was pressed.
	 */
	@Test
	public void aPickedStarsButtonsAreInItsOwnCardWhileTheFiltersShowIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			Pairing pairing = new Pairing(store, new Gson());
			MemberIdentity identity = new MemberIdentity(store);
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);
			DestinationState destinations = new DestinationState(new Gson());
			RoutePress press = new RoutePress(new OkHttpClient(), new Gson(), null, new ClientThread(), pairing, identity,
				new DestinationRoute(), destinations, new TestDestinationMarker());
			StarsPanel stars = new StarsPanel(service, destinations, new ClientThread(), new TestDestinationMarker(), press,
				() -> MapCompanionConfig.DEFAULT_MAP_URL, new DestinationRoute(), null, null, null, rerouting -> null, () -> { });
			service.model.accept(feed());
			service.model.miningLevel = 72;
			stars.refresh();
			assertNull("a list arriving picks nothing", named(stars, "stars.pin"));

			((AbstractButton) named(stars, "stars.site.tile:2974,3241")).doClick();
			assertNotNull("a press picks", named(stars, "stars.pin"));
			assertTrue("in the star's own card", SwingUtilities.isDescendingFrom(named(stars, "stars.pin"),
				named(stars, "stars.site.tile:2974,3241").getParent().getParent().getParent()));
			assertEquals("Route first, since it pins as well", "stars.route", named(stars, "stars.pin").getParent().getComponent(0).getName());
			assertFalse("and nothing heads the tab for a pick", words(stars).contains("Selected world"));

			JComboBox<?> tier = (JComboBox<?>) named(stars, "stars.tier");
			tier.setSelectedIndex(8);
			assertNull("T9+ hides the T7 picked, so it is not offered", named(stars, "stars.pin"));
			assertEquals("but it is still the pick", "302|rimmington mine", service.model.selected);
			tier.setSelectedIndex(0);
			assertNotNull("and shows again when the filter lets it", named(stars, "stars.pin"));

			((AbstractButton) named(stars, "stars.site.tile:2974,3241")).doClick();
			assertNull("a second press lets it go", service.model.selected);
			assertNull(named(stars, "stars.pin"));
		});
	}

	/**
	 * A folded card names its first world alone, so folding it lets go of a pick of another: Route and
	 * Pin never act on a world the card is not showing. A pick of the first world stays.
	 */
	@Test
	public void foldingASiteLetsGoOfAWorldItNoLongerShows() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			AbstractButton expand = (AbstractButton) named(tab.stars, "stars.expand.tile:2974,3241");
			expand.doClick();
			((AbstractButton) named(tab.stars, "stars.world.330|rimmington mine")).doClick();
			assertNotNull(named(tab.stars, "stars.pin"));
			((AbstractButton) named(tab.stars, "stars.expand.tile:2974,3241")).doClick();
			assertNull(tab.service.model.selected);
			assertNull(named(tab.stars, "stars.pin"));

			tab.pick();
			((AbstractButton) named(tab.stars, "stars.expand.tile:2974,3241")).doClick();
			((AbstractButton) named(tab.stars, "stars.expand.tile:2974,3241")).doClick();
			assertEquals("the first world is still on the card", "302|rimmington mine", tab.service.model.selected);
		});
	}

	/**
	 * The pinned star is the one being walked to, so it heads the tab whatever is picked since and
	 * whatever the filters hide, with Unpin always in reach.
	 */
	@Test
	public void thePinnedStarHeadsTheTabWhateverIsPickedOrFiltered() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			((AbstractButton) named(tab.stars, "stars.pin")).doClick();
			((AbstractButton) named(tab.stars, "stars.site.tile:3296,3298")).doClick();
			assertEquals("another star picked", "306|al kharid mine", tab.service.model.selected);
			assertTrue(words(tab.stars).contains("Pinned destination"));
			assertTrue(words(tab.stars).toString(), words(tab.stars).stream().anyMatch(line -> line.startsWith("W302")));
			assertNotNull("with its own Route and Pin", named(tab.stars, "stars.pin"));

			((JComboBox<?>) named(tab.stars, "stars.tier")).setSelectedIndex(8);
			assertTrue("T9+ hides the T7 from the list, not from the head", words(tab.stars).contains("Pinned destination"));
			assertNotNull(named(tab.stars, "stars.unpin"));
		});
	}

	/**
	 * A pick ends with its pin. Reached, the plugin dismisses the destination; the star then goes back
	 * to a plain row rather than opening on Route and Pin for the place the player is standing at.
	 */
	@Test
	public void aPickEndsWithItsPin() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			tab.routeTo(302);
			tab.stars.refresh();
			assertTrue(words(tab.stars).contains("Pinned destination"));

			tab.destinations.dismiss();
			tab.stars.refresh();
			assertFalse(words(tab.stars).contains("Pinned destination"));
			assertNull(tab.service.model.selected);
			assertNull(named(tab.stars, "stars.pin"));
		});
	}

	/**
	 * The buttons are in the star's card, which can be far down the list, and the pinned star's way
	 * is at the head of the tab: pinning one brings the head into view, where the way would otherwise
	 * arrive off the top of the sidebar.
	 */
	@Test
	public void pinningAStarFromDownTheListBringsTheHeadOfTheTabIntoView() throws Exception
	{
		Tab[] made = new Tab[1];
		javax.swing.JScrollPane[] pane = new javax.swing.JScrollPane[1];
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			made[0] = tab;
			tab.stars.selected(true);
			pane[0] = new javax.swing.JScrollPane(tab.stars);
			pane[0].setSize(225, 120);
			tab.pick();
			layout(pane[0]);
			pane[0].getViewport().setViewPosition(new java.awt.Point(0, 60));
			assertEquals(60, pane[0].getViewport().getViewPosition().y);
			((AbstractButton) named(tab.stars, "stars.pin")).doClick();
			layout(pane[0]);
		});
		SwingUtilities.invokeAndWait(() ->
			assertEquals("scrolled to the pinned star", 0, pane[0].getViewport().getViewPosition().y));
	}

	/** Lay a detached tree out as a window would, so a view has a size to scroll within. */
	private static void layout(Container root)
	{
		root.doLayout();
		for (Component child : root.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}

	/** What a press of Route said is drawn under the buttons of the star it was pressed for, in its card. */
	@Test
	public void whatAPressSaidIsUnderTheStarItWasPressedFor() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			((AbstractButton) named(tab.stars, "stars.route")).doClick();
			tab.press.refuse(StarsPanel.SOURCE, "The map could not be reached.");
			tab.stars.refresh();
			Component card = named(tab.stars, "stars.site.tile:2974,3241").getParent().getParent().getParent();
			assertTrue(words((Container) card).toString(), words((Container) card).contains("The map could not be reached."));

			((AbstractButton) named(tab.stars, "stars.site.tile:3296,3298")).doClick();
			assertFalse("another star's card does not say it", words(tab.stars).contains("The map could not be reached."));
		});
	}

	/**
	 * One loud button a page: Pin destination is as plain as Route beside it, not the brown Group gives
	 * an answer already chosen. A filter chip that is on says so with an orange outline, not by a fill a
	 * few shades lighter alone, and it is the same size on as off.
	 */
	@Test
	public void pinIsAsPlainAsRouteAndAChipThatIsOnIsOutlined() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			AbstractButton pin = (AbstractButton) named(tab.stars, "stars.pin");
			AbstractButton route = (AbstractButton) named(tab.stars, "stars.route");
			assertEquals(route.getFont(), pin.getFont());
			assertEquals(route.getBackground(), pin.getBackground());
			assertEquals(route.getForeground(), pin.getForeground());
			assertEquals(route.getMargin(), pin.getMargin());

			JToggleButton members = (JToggleButton) named(tab.stars, "stars.members");
			JToggleButton free = (JToggleButton) named(tab.stars, "stars.free");
			assertTrue("P2P starts on", members.isSelected());
			assertTrue("on: outlined in orange", outlined(members));
			assertFalse("off: the look and feel's own border", outlined(free));
			java.awt.Dimension off = free.getPreferredSize();
			free.doClick();
			assertTrue(outlined(free));
			assertEquals("the same size on as off", off, free.getPreferredSize());
			members.doClick();
			assertFalse(outlined(members));
		});
	}

	private static boolean outlined(JToggleButton chip)
	{
		return chip.getBorder() instanceof javax.swing.border.CompoundBorder
			&& ((javax.swing.border.CompoundBorder) chip.getBorder()).getOutsideBorder() instanceof javax.swing.border.LineBorder
			&& ColorScheme.BRAND_ORANGE.equals(((javax.swing.border.LineBorder) ((javax.swing.border.CompoundBorder) chip.getBorder())
				.getOutsideBorder()).getLineColor());
	}

	/**
	 * A star hunter stays on Stars while they hop, so the way to the picked star is drawn under it
	 * once the map answers: the steps Home's card draws, and Home's own Follow and Reroute as Home
	 * offers them, pressed through rather than copied, so Stars never offers a press Home would not.
	 */
	@Test
	public void theWayToThePickedStarShowsUnderItWithHomesOwnButtons() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			tab.routeTo(302);
			tab.offer(tab.follow, tab.reroute);
			tab.reroute.setEnabled(false);
			tab.stars.refresh();

			assertTrue(words(tab.stars).toString(), words(tab.stars).contains("Pinned destination"));
			RouteStepsView steps = steps(tab.stars);
			assertNotNull("a view of the steps under the star", steps);
			assertTrue("showing", showing(steps, tab.stars));
			assertTrue(words(steps).toString(), words(steps).stream().anyMatch(line -> line.contains("Walk 12 tiles to the bank")));
			AbstractButton follow = (AbstractButton) named(tab.stars, "stars.follow");
			assertEquals("Home's own words", "Pause", follow.getText());
			follow.doClick();
			assertEquals("a press of Home's very button", 1, tab.followed.get());
			assertFalse("pressable only while Home's is", named(tab.stars, "stars.reroute").isEnabled());
			assertNotNull(named(tab.stars, "stars.unpin"));
			assertNull("the way is planned, so Route gives way to Reroute", named(tab.stars, "stars.route"));

			/* Home stops offering Follow, as it does on arrival: so does Stars. */
			tab.offer(tab.reroute);
			tab.stars.refresh();
			assertNull(named(tab.stars, "stars.follow"));
			assertNotNull(named(tab.stars, "stars.reroute"));
		});
	}

	/**
	 * The same mine on another world is another star. The one pinned heads the tab with its way, and
	 * the pick keeps Route and Pin in its own card. This used to assert the pick heading the tab with
	 * no steps: the head was the pick's then, and is the pinned star's now.
	 */
	@Test
	public void aStarPinnedOnAnotherWorldLeavesThePickItsOwnButtons() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			tab.routeTo(330);
			tab.offer(tab.follow, tab.reroute);
			tab.stars.refresh();

			assertEquals("302|rimmington mine", tab.service.model.selected);
			assertTrue(words(tab.stars).toString(), words(tab.stars).stream().anyMatch(line -> line.startsWith("W330")));
			assertTrue("W330's steps", stepsShowing(tab.stars));
			assertNotNull(named(tab.stars, "stars.unpin"));
			assertNotNull("W302's own", named(tab.stars, "stars.pin"));
			assertNotNull(named(tab.stars, "stars.route"));
		});
	}

	@Test
	public void unpinIsAButtonThatDismissesTheDestination() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			tab.routeTo(302);
			tab.offer(tab.follow, tab.reroute);
			tab.stars.refresh();

			Component unpin = named(tab.stars, "stars.unpin");
			assertTrue("a button, as Dismiss is on Home", unpin instanceof JButton && ((JButton) unpin).isContentAreaFilled());
			((AbstractButton) unpin).doClick();
			assertNull(tab.destinations.getDestination());
			assertFalse(words(tab.stars).contains("Pinned destination"));
			assertFalse("no steps", stepsShowing(tab.stars));
			/* It used to fall back to Selected world with Pin destination; the pick now ends with its pin. */
			assertNull("the star is a plain row again", named(tab.stars, "stars.pin"));
		});
	}

	/** Pin, then the way there, at the head of the tab: a star pinned with no way yet keeps its Route. */
	@Test
	public void aStarPinnedWithNoWayYetOffersRoute() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Tab tab = new Tab();
			tab.pick();
			((AbstractButton) named(tab.stars, "stars.pin")).doClick();

			assertTrue(words(tab.stars).contains("Pinned destination"));
			AbstractButton route = (AbstractButton) named(tab.stars, "stars.route");
			assertNotNull("Route stays beside Unpin", route);
			assertTrue(route.isEnabled());
			assertNotNull(named(tab.stars, "stars.unpin"));
			assertNull(named(tab.stars, "stars.pin"));
			assertFalse("no steps", stepsShowing(tab.stars));
		});
	}

	/**
	 * A tier is bold letters in the map's own colour for it, and a time left warns before the star ends: amber
	 * under twenty minutes and red under five, as the website tones them, grey once it is depleted, and
	 * the line's own ink while there is time. The same on a site card, a world row and the pinned line.
	 *
	 * <p>This read the facts out of one string of markup. Each is a label of its own now, the tier and
	 * the world in bold, so a list is scanned by colour down its left edge and by time down its right;
	 * and a world row says "~34m left" as every line does. The tier is its letters' colour, on no
	 * ground of its own.
	 */
	@Test
	public void aTierIsTheMapsColourAndATimeLeftWarnsBeforeItEnds() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			Pairing pairing = new Pairing(store, new Gson());
			MemberIdentity identity = new MemberIdentity(store);
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);
			DestinationState destinations = new DestinationState(new Gson());
			RoutePress press = new RoutePress(new OkHttpClient(), new Gson(), null, new ClientThread(), pairing, identity,
				new DestinationRoute(), destinations, new TestDestinationMarker());
			StarsPanel stars = new StarsPanel(service, destinations, new ClientThread(), new TestDestinationMarker(), press,
				() -> MapCompanionConfig.DEFAULT_MAP_URL, new DestinationRoute(), null, null, null, rerouting -> null, () -> { });
			StarsModel.Feed feed = feed();
			for (StarsModel.Star star : feed.rows)
			{
				if (star.world == 306)
				{
					star.remainingMs = 3 * 60_000L;
				}
				if (star.world == 444)
				{
					star.remainingMs = -30_000L;
				}
			}
			service.model.accept(feed);
			service.model.miningLevel = 72;
			service.model.preferences.filters.hideDepleted = false;
			stars.refresh();

			Container rimmington = (Container) named(stars, "stars.site.tile:2974,3241");
			assertEquals("T7", fact(rimmington, "tier").getText());
			assertEquals(StarsPanel.TIERS[6], fact(rimmington, "tier").getForeground());
			assertTrue("in bold", fact(rimmington, "tier").getFont().isBold());
			assertFalse("letters alone, on no ground of their own", fact(rimmington, "tier").isOpaque());
			assertEquals("W302", fact(rimmington, "world").getText());
			assertTrue("the world in bold", fact(rimmington, "world").getFont().isBold());
			assertEquals("~46m left", fact(rimmington, "left").getText());
			assertEquals("time to spare keeps the card's ink", new Color(184, 184, 184), fact(rimmington, "left").getForeground());
			Container alKharid = (Container) named(stars, "stars.site.tile:3296,3298");
			assertEquals(StarsPanel.TIERS[2], fact(alKharid, "tier").getForeground());
			assertEquals("~3m left", fact(alKharid, "left").getText());
			assertEquals(StarsPanel.DYING_TONE, fact(alKharid, "left").getForeground());

			assertEquals("the worlds besides the one printed", "+2 ▾", ((AbstractButton) named(stars, "stars.expand.tile:2974,3241")).getText());
			((AbstractButton) named(stars, "stars.expand.tile:2974,3241")).doClick();
			/* It listed W302 again under its own card, as the first of three rows; the fold opens the other two. */
			assertNull("the card is its first world, so the list does not repeat it", named(stars, "stars.world.302|rimmington mine"));
			assertEquals(StarsPanel.TIERS[4], fact(row(stars, "330|rimmington mine"), "tier").getForeground());
			assertEquals("~34m left", fact(row(stars, "330|rimmington mine"), "left").getText());
			assertEquals("Depleted", fact(row(stars, "444|rimmington mine"), "left").getText());
			assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, fact(row(stars, "444|rimmington mine"), "left").getForeground());

			service.model.feed.rows.stream().filter(star -> star.world == 330).findFirst().get().remainingMs = 15 * 60_000L;
			((AbstractButton) named(stars, "stars.world.330|rimmington mine")).doClick();
			assertEquals("under twenty minutes", StarsPanel.FADING_TONE, fact(row(stars, "330|rimmington mine"), "left").getForeground());
			((AbstractButton) named(stars, "stars.pin")).doClick();
			Container head = (Container) stars.getComponent(0);
			assertTrue(words(head).toString(), words(head).containsAll(Arrays.asList("Pinned destination", "T5", "W330", "~15m left")));
		});
	}

	/**
	 * A world is its number behind the world switcher's own star for its kind, as the game lists a
	 * world, where it was "W302": the star says members or free, and PvP or Deadman before either,
	 * which the letter never could. The rule is the website's. A world the map's list did not have
	 * keeps the W rather than being called free to play, and so does every world while there are no
	 * pictures to draw.
	 */
	@Test
	public void aWorldIsItsNumberBehindTheWorldSwitchersStarForItsKind() throws Exception
	{
		/* The star asked for, by the label it is to be drawn on. */
		java.util.Map<Component, Integer> asked = new java.util.IdentityHashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			Pairing pairing = new Pairing(store, new Gson());
			MemberIdentity identity = new MemberIdentity(store);
			StarsClient service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);
			DestinationState destinations = new DestinationState(new Gson());
			RoutePress press = new RoutePress(new OkHttpClient(), new Gson(), null, new ClientThread(), pairing, identity,
				new DestinationRoute(), destinations, new TestDestinationMarker());
			GameSprites pictures = new GameSprites(null, null, null)
			{
				@Override
				void sprite(JLabel label, int spriteId, int box)
				{
					asked.put(label, spriteId);
				}
			};
			StarsPanel stars = new StarsPanel(service, destinations, new ClientThread(), new TestDestinationMarker(), press,
				() -> MapCompanionConfig.DEFAULT_MAP_URL, new DestinationRoute(), null, null, pictures, rerouting -> null, () -> { });
			StarsModel.Feed feed = feed();
			for (StarsModel.Star star : feed.rows)
			{
				star.pvp = star.world == 330;
				star.deadman = star.world == 444;
				if (star.world == 306)
				{
					star.members = null;
				}
			}
			service.model.accept(feed);
			service.model.miningLevel = 72;
			stars.refresh();
			((AbstractButton) named(stars, "stars.expand.tile:2974,3241")).doClick();

			assertEquals("the number alone", "302", fact((Container) named(stars, "stars.site.tile:2974,3241"), "world").getText());
			assertEquals("the star before the number", "star.kind", fact((Container) named(stars, "stars.site.tile:2974,3241"), "world").getParent().getComponent(0).getName());
			assertEquals(Integer.valueOf(net.runelite.api.gameval.SpriteID.WorldswitcherStars.MEMBERS),
				asked.get(named((Container) named(stars, "stars.site.tile:2974,3241"), "star.kind")));
			assertEquals("PvP before members", Integer.valueOf(net.runelite.api.gameval.SpriteID.WorldswitcherStars.PVP),
				asked.get(named(row(stars, "330|rimmington mine"), "star.kind")));
			assertEquals("Deadman before both", Integer.valueOf(net.runelite.api.gameval.SpriteID.WorldswitcherStars.DEADMAN),
				asked.get(named(row(stars, "444|rimmington mine"), "star.kind")));
			assertEquals("a world of no known kind keeps its letter", "W306", fact((Container) named(stars, "stars.site.tile:3296,3298"), "world").getText());
			assertNull("and gets no star", named((Container) named(stars, "stars.site.tile:3296,3298"), "star.kind"));
		});
		StarsModel.Star free = new StarsModel.Star();
		free.members = false;
		assertEquals(Integer.valueOf(net.runelite.api.gameval.SpriteID.WorldswitcherStars.FREE), StarsPanel.worldStar(free));
	}

	/**
	 * Each colour reads on a site card's grey and on the picked world's row, which is darkened for
	 * them: every tier's letters, both warnings, and the world the player is on.
	 */
	@Test
	public void everyTierAndEachColouredWordReadsOnACardAndOnThePickedRow()
	{
		for (Color colour : StarsPanel.TIERS)
		{
			assertTrue(colour + " on a card", contrast(colour, ColorScheme.DARKER_GRAY_COLOR) >= 5.5);
			assertTrue(colour + " on the picked row", contrast(colour, StarsPanel.PICKED) >= 4.5);
		}
		for (Color colour : new Color[]{StarsPanel.FADING_TONE, StarsPanel.DYING_TONE, StarsPanel.HERE})
		{
			assertTrue(colour + " on a card", contrast(colour, ColorScheme.DARKER_GRAY_COLOR) >= 4.5);
			assertTrue(colour + " on the picked row", contrast(colour, StarsPanel.PICKED) >= 4.5);
		}
	}

	/** WCAG's contrast ratio between two colours. */
	private static double contrast(Color a, Color b)
	{
		double x = luminance(a), y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static double luminance(Color colour)
	{
		return 0.2126 * linear(colour.getRed()) + 0.7152 * linear(colour.getGreen()) + 0.0722 * linear(colour.getBlue());
	}

	private static double linear(int channel)
	{
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	/** One world's row under its site. */
	private static Container row(Container stars, String star)
	{
		return (Container) named(stars, "stars.world." + star);
	}

	/** One of a star's facts in a card or a row: its tier, world or time left. */
	private static JLabel fact(Container in, String which)
	{
		return (JLabel) named(in, "star." + which);
	}

	/**
	 * The tab over the website's own feed with a real destination and route, and two buttons standing
	 * in for the destination card's Follow and Reroute, which Home offers as {@link #offer} says.
	 */
	private static final class Tab
	{
		final DestinationState destinations = new DestinationState(new Gson());
		final DestinationRoute route = new DestinationRoute();
		final JButton follow = new JButton("Pause");
		final JButton reroute = new JButton("Reroute");
		final AtomicInteger followed = new AtomicInteger();
		final List<JButton> offered = new ArrayList<>();
		final StarsClient service;
		final StarsPanel stars;
		final RoutePress press;

		Tab()
		{
			HarnessStore store = new HarnessStore();
			Pairing pairing = new Pairing(store, new Gson());
			MemberIdentity identity = new MemberIdentity(store);
			service = new StarsClient(new OkHttpClient(), new Gson(), null, new ClientThread(),
				new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), pairing, identity, store);
			press = new RoutePress(new OkHttpClient(), new Gson(), null, new ClientThread(), pairing, identity,
				route, destinations, new TestDestinationMarker());
			follow.addActionListener(e -> followed.incrementAndGet());
			stars = new StarsPanel(service, destinations, new ClientThread(), new TestDestinationMarker(), press,
				() -> MapCompanionConfig.DEFAULT_MAP_URL, route, null, null, null, rerouting ->
				{
					JButton own = rerouting ? reroute : follow;
					return offered.contains(own) ? own : null;
				}, () -> { });
			service.model.accept(feed());
			service.model.miningLevel = 72;
			stars.refresh();
		}

		/** Rimmington mine's first world, W302, picked by a press as a player picks it. */
		void pick()
		{
			((AbstractButton) named(stars, "stars.site.tile:2974,3241")).doClick();
		}

		/** A Route's answer as RoutePress lands it: Rimmington mine on that world, with the reply fixture's way. */
		void routeTo(int world)
		{
			RelayResponse.Destination there = new RelayResponse.Destination();
			there.x = 2974;
			there.y = 3241;
			there.plane = 0;
			there.name = "Rimmington mine";
			there.world = world;
			there.route = new Gson().fromJson(new InputStreamReader(StarsPanelTest.class.getResourceAsStream("/contract/reply.json"),
				StandardCharsets.UTF_8), RelayResponse.class).directive.destination.route;
			destinations.selectLocal(there);
			route.adoptPressed(there);
		}

		void offer(JButton... home)
		{
			offered.clear();
			offered.addAll(Arrays.asList(home));
		}
	}

	/** Whether a view of the steps is in the tab and showing. */
	private static boolean stepsShowing(Container root)
	{
		RouteStepsView steps = steps(root);
		return steps != null && showing(steps, root);
	}

	private static RouteStepsView steps(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof RouteStepsView)
			{
				return (RouteStepsView) child;
			}
			if (child instanceof Container)
			{
				RouteStepsView found = steps((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The words showing, as a player reads them: label and button text without its markup. */
	private static List<String> words(Container root)
	{
		List<String> found = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (!child.isVisible())
			{
				continue;
			}
			String text = child instanceof JLabel ? ((JLabel) child).getText()
				: child instanceof AbstractButton ? ((AbstractButton) child).getText() : null;
			if (text != null && !text.isEmpty())
			{
				found.add(text.replaceAll("<[^>]+>", "").trim());
			}
			if (child instanceof Container)
			{
				found.addAll(words((Container) child));
			}
		}
		return found;
	}

	private static StarsModel.Feed feed()
	{
		return new Gson().fromJson(new InputStreamReader(StarsPanelTest.class.getResourceAsStream("/companion-stars.sample.json"),
			StandardCharsets.UTF_8), StarsModel.Feed.class);
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
