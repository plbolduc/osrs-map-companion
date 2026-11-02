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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A picture from the client's cache lands after the row it belongs to was built and laid out, and
 * the sidebar lays the tab out again when it does. A row measured before its picture, and a column
 * that grows its gaps when squeezed, cut friends' cards and pushed the group's own buttons and Data
 * from Star Miners off the page.
 *
 * <p>Each scene is laid out, given its pictures, invalidated as setting an icon invalidates a live
 * sidebar, and laid out again at its new preferred height, which is the height the sidebar gives it.
 */
public class LatePicturesTest
{
	/** What GameSprites hands over later: the labels waiting for a picture, and the chips. */
	private final List<JLabel> waiting = new ArrayList<>();
	private final List<AbstractButton> chips = new ArrayList<>();
	private final GameSprites later = new GameSprites(null, null, null)
	{
		@Override
		void item(JLabel label, int itemId, int box)
		{
			waiting.add(label);
		}

		@Override
		void skill(JLabel label, String name, int box)
		{
			waiting.add(label);
		}

		@Override
		void sprite(AbstractButton button, int spriteId, int box)
		{
			chips.add(button);
		}
	};

	/** A friend's news line is as tall before its picture as after, so its card never grows. */
	@Test
	public void aFriendsNewsKeepsItsPicturesPlaceUntilThePictureLands() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			group();
			assertEquals("Cara's skill and Dax's drop", 2, waiting.size());
			for (JLabel news : waiting)
			{
				int before = news.getPreferredSize().height;
				news.setIcon(picture(16, 16));
				assertEquals(news.getName() + " does not grow when its picture lands", before, news.getPreferredSize().height);
			}
		});
	}

	@Test
	public void aPictureLandingLaterLeavesEveryFriendWholeAndTheGroupsButtonsOnThePage() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			GroupPanel group = group();
			/* The Group tab as the sidebar stacks it: the friends, then Copy invite and Switch. */
			JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
			MapCompanionPanel.syncRow(buttons, Arrays.asList(new JButton("Copy invite"), new JButton("Switch ▾")),
				new ArrayList<>());
			JPanel tab = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
			tab.add(group);
			tab.add(MapCompanionPanel.left(buttons));
			JPanel root = sidebar(tab);

			for (JLabel news : waiting)
			{
				news.setIcon(picture(16, 16));
			}
			relayout(root);

			JComponent friends = (JComponent) group.getComponent(0);
			for (Component slot : friends.getComponents())
			{
				assertEquals("a card at its own height, neither cut nor stretched", slot.getPreferredSize().height, slot.getHeight());
				assertTrue("and inside the list", slot.getY() + slot.getHeight() <= friends.getHeight());
			}
			assertOnPage("Copy invite and Switch", buttons, root);
		});
	}

	/**
	 * A card's slot is as tall as the card is when laid out, whatever grew in it since it was built:
	 * the placeholder above covers the pictures this panel knows of, and this covers the rest.
	 */
	@Test
	public void aFriendsSlotFollowsItsCardWhenTheCardGrows() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Container slot = (Container) ((Container) group().getComponent(0)).getComponent(0);
			((Container) slot.getComponent(0)).add(new JLabel("A line that came later"));
			assertEquals("no maximum under the card's own height", slot.getPreferredSize().height, slot.getMaximumSize().height);
		});
	}

	/**
	 * The P2P and F2P chips get the world switcher's stars after the tab is built. The test's star is
	 * drawn taller than the chip's words in any look and feel, so the chip surely grows.
	 */
	@Test
	public void dataFromStarMinersStaysOnThePageOnceTheChipsGetTheirStars() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			StarsPanel stars = stars();
			JPanel root = sidebar(stars);

			assertEquals("P2P and F2P", 2, chips.size());
			for (AbstractButton chip : chips)
			{
				chip.setIcon(picture(14, 30));
			}
			relayout(root);

			for (Component row : stars.getComponents())
			{
				if (row.isVisible())
				{
					assertEquals("every row of the tab at its own height", row.getPreferredSize().height, row.getHeight());
				}
			}
			JLabel source = credit(stars);
			assertNotNull(source);
			assertOnPage("Data from Star Miners", source, root);
		});
	}

	/**
	 * Squeezed, a column takes each row down to its minimum, and a plain panel's minimum is 10px: the
	 * 2 to 8px gaps between rows grew while the rows beside them were cut. A gap holds its height.
	 */
	@Test
	public void aSqueezedColumnNeverGrowsItsGaps() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			List<JComponent> columns = new ArrayList<>();
			columns.add(stars());
			for (Component slot : ((Container) group().getComponent(0)).getComponents())
			{
				columns.add((JComponent) slot);
			}
			int checked = 0;
			for (JComponent column : columns)
			{
				column.setSize(PluginPanel.PANEL_WIDTH - 20, column.getPreferredSize().height - 4);
				layout(column);
				for (Component gap : gaps(column, new ArrayList<>()))
				{
					checked++;
					assertTrue("a " + gap.getPreferredSize().height + "px gap drawn " + gap.getHeight() + "px tall",
						gap.getHeight() <= gap.getPreferredSize().height);
				}
			}
			assertTrue("the Stars tab's and the friends' cards' gaps", checked >= 5);
		});
	}

	/** The Stars tab over the website's own feed, with this test's late pictures for its chips. */
	private StarsPanel stars()
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
		stars.pictures(later);
		service.model.accept(new Gson().fromJson(new InputStreamReader(
			LatePicturesTest.class.getResourceAsStream("/companion-stars.sample.json"), StandardCharsets.UTF_8), StarsModel.Feed.class));
		service.model.miningLevel = 72;
		stars.refresh();
		return stars;
	}

	/** The spacers: empty panels one pixel wide, which is how every gap() here is made. */
	private static List<Component> gaps(Container parent, List<Component> found)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JPanel && ((JPanel) child).getComponentCount() == 0 && child.getPreferredSize().width == 1)
			{
				found.add(child);
			}
			else if (child instanceof Container)
			{
				gaps((Container) child, found);
			}
		}
		return found;
	}

	/** The Group tab's panel over the contract's group of three, with this test's late pictures. */
	private GroupPanel group()
	{
		HarnessStore store = new HarnessStore();
		HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
		Pairing pairing = new Pairing(store, new Gson());
		MemberIdentity identity = new MemberIdentity(store);
		DestinationState destinations = new DestinationState(new Gson());
		GroupState state = new GroupState(destinations);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		Client game = MeetupClientTest.game(false);
		ClientThread now = new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		};
		RoutePress press = new RoutePress(new OkHttpClient(), new Gson(), game, now, pairing, identity, new DestinationRoute(),
			destinations, new TestDestinationMarker());
		MeetupClient meetups = new MeetupClient(new OkHttpClient(), new Gson(), game, now, pairing, identity, destinations, state,
			link -> { });
		GroupPanel group = new GroupPanel(destinations, state, press, meetups, config, later,
			MapCompanionPanel.smallButton("Copy invite"));
		try
		{
			String stream = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "stream.json")),
				StandardCharsets.UTF_8);
			RelayResponse.StreamDirective directive = new Gson().fromJson(stream, RelayResponse.StreamDirective.class);
			directive.events.get(0).startsAtMs = System.currentTimeMillis() + 3_600_000L;
			destinations.acceptSocial(directive, true);
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
		group.refresh(true);
		return group;
	}

	/** The tab in a column of the sidebar's width, given its preferred height and laid out once. */
	private static JPanel sidebar(JComponent tab)
	{
		JPanel content = MapCompanionPanel.column(ColorScheme.DARK_GRAY_COLOR);
		content.add(tab);
		JPanel root = new JPanel(new BorderLayout());
		root.add(content, BorderLayout.NORTH);
		root.setSize(PluginPanel.PANEL_WIDTH - 20, root.getPreferredSize().height);
		layout(root);
		return root;
	}

	/** What a live sidebar does when an icon is set: everything above it invalid, then laid out again. */
	private static void relayout(JPanel root)
	{
		invalidate(root);
		root.setSize(root.getWidth(), root.getPreferredSize().height);
		layout(root);
	}

	private static void invalidate(Component component)
	{
		component.invalidate();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				invalidate(child);
			}
		}
	}

	private static void layout(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}

	/** Drawn whole: at its own height and inside every panel above it, since a panel clips what overhangs it. */
	private static void assertOnPage(String what, Component component, JPanel root)
	{
		assertTrue(what + " at its own height", component.getHeight() >= component.getPreferredSize().height);
		for (Container above = component.getParent(); above != null; above = above.getParent())
		{
			Point at = SwingUtilities.convertPoint(component.getParent(), component.getX(), component.getY(), above);
			assertTrue(what + " ends at " + (at.y + component.getHeight()) + " inside a panel " + above.getHeight() + "px tall",
				at.y >= 0 && at.y + component.getHeight() <= above.getHeight());
			if (above == root)
			{
				break;
			}
		}
	}

	@Nullable
	private static JLabel credit(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel && "Data from Star Miners".equals(((JLabel) child).getText()))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel found = credit((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static ImageIcon picture(int width, int height)
	{
		return new ImageIcon(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
	}
}
