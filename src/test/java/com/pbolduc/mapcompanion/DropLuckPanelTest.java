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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

/**
 * The sidebar by its words: which monster it opens on, how it navigates, and that it says what
 * {@link DropLuckView} decided. Laid out without pictures — the panel takes a null item manager.
 */
public class DropLuckPanelTest
{
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private long now = System.currentTimeMillis();
	private final LootLog log = new LootLog(game.client(), store, new Gson());
	private final DropLuckPanel panel = new DropLuckPanel(log, null);

	public DropLuckPanelTest()
	{
		game.nameItem(4151, "Abyssal whip");
		game.nameItem(995, "Coins");
		log.clock(() -> now);
	}

	private void kill(String npc, int item)
	{
		log.record(new ServerNpcLoot(game.npc(npc), Arrays.asList(new ItemStack(item, 1))));
	}

	private String words()
	{
		return String.join(" | ", SidebarWords.words(panel));
	}

	@Test
	public void nothingKilledDrawsNothing()
	{
		panel.refresh();
		assertFalse(panel.hasContent());
		assertTrue(SidebarWords.words(panel).isEmpty());
	}

	@Test
	public void killingSomethingOpensItsCardWithTheVerdictFirst()
	{
		kill("Abyssal demon", 4151);
		kill("Abyssal demon", 995);
		panel.refresh();
		String words = words();
		assertTrue(words, words.contains("Abyssal demon"));
		assertTrue(words, words.contains("Killing now · 2 recorded kills"));
		assertTrue(words, words.contains("All monsters ›"));
		/*
		 * The card opens on "Any unique", the whip, head and dagger added up: it was the whip, the
		 * common one, until the card gained a row for the question a boss is farmed by. The detail
		 * leads with its verdict, has no Pin (the pin is a drop's name, and no item is called "Any
		 * unique"), says each fact once on its own line, and does not name the row again.
		 */
		assertTrue(words, words.contains("Any unique | 1 in 465 | 1 | Very lucky | Abyssal whip | 1 in 512"));
		assertTrue(words, words.contains("Lucky overall, fine right now. | Had | 1 (usual under 0.1) | Since last drop | 1 kill"));
		assertEquals(words, 1, words.split("Abyssal whip", -1).length - 1);
		assertTrue(words, words.contains("1 kill | >99% of players would still be waiting too"));
		assertTrue(words, words.endsWith("Next 100 kills | 19% chance"));
		assertFalse(words, words.contains("Pin"));
		/* The whip's own detail keeps its Pin. */
		assertTrue(words, SidebarWords.press(panel, "Abyssal whip"));
		assertTrue(words(), words().contains("Lucky overall, fine right now. | Pin | Had | 1 (usual under 0.1)"));
	}

	@Test
	public void theListOpensAPastMonsterAndTheNowBarComesBack()
	{
		now -= 3 * 60 * 60_000L;
		kill("Gargoyle", 995);
		now += 3 * 60 * 60_000L;
		kill("Abyssal demon", 995);
		panel.refresh();
		assertTrue(words(), SidebarWords.press(panel, "All monsters ›"));
		String list = words();
		assertTrue(list, list.contains("2 monsters tracked"));
		assertTrue(list, list.contains("1 kill · 3 h ago"));
		assertTrue(SidebarWords.press(panel, "Gargoyle"));
		String card = words();
		assertTrue(card, card.contains("Now killing Abyssal demon"));
		assertTrue(card, card.contains("‹ All monsters"));
		assertFalse("under the Now killing bar the card's head is not the section's first row", panel.namesItself());
		assertTrue(card, card.contains("Last killed 3 h ago · 1 recorded kill"));
		assertTrue(SidebarWords.press(panel, "Back ›"));
		assertTrue(words(), words().contains("Killing now"));
		/*
		 * Still said once, now by the card: its eyebrow is empty while it is the monster being killed,
		 * so "Drop luck" moved into that place beside All monsters and the sidebar drops its own
		 * heading over it (namesItself). Before, the sidebar said it and this card did not.
		 */
		assertTrue(words(), words().startsWith("Drop luck | All monsters ›"));
		assertTrue(panel.namesItself());
	}

	@Test
	public void pinningKeepsTheDropOnTheCardBetweenGrinds()
	{
		now -= 60 * 60_000L;
		kill("Abyssal demon", 995);
		now += 30 * 60_000L;
		kill("Gargoyle", 995);
		now += 30 * 60_000L;
		panel.refresh();
		assertTrue("not killing anything: the last monster killed", words().contains("Last monster killed"));
		assertFalse("an eyebrow with words keeps the sidebar's own heading", panel.namesItself());
		assertTrue(words(), SidebarWords.press(panel, "All monsters ›"));
		assertTrue(SidebarWords.press(panel, "Abyssal demon"));
		assertTrue(SidebarWords.press(panel, "Abyssal whip"));
		assertTrue(SidebarWords.press(panel, "Pin"));
		assertTrue(SidebarWords.press(panel, "‹ All monsters"));
		assertTrue(SidebarWords.press(panel, "Close"));
		String card = words();
		assertTrue(card, card.contains("Your pinned drop"));
		assertTrue(card, card.contains("Pinned"));
	}

	@Test
	public void searchFindsAMonsterByOneOfItsDrops() throws Exception
	{
		kill("Abyssal demon", 995);
		kill("Gargoyle", 995);
		panel.refresh();
		assertTrue(words(), SidebarWords.press(panel, "All monsters ›"));
		/* RuneLite's search field asserts it is touched on the event thread, as the sidebar does. */
		javax.swing.SwingUtilities.invokeAndWait(() -> SidebarWords.search(panel, "whip"));
		List<String> found = SidebarWords.words(panel);
		assertTrue(found.toString(), found.contains("1 found"));
		assertTrue(found.toString(), found.contains("Abyssal demon"));
		assertFalse(found.toString(), found.contains("Gargoyle"));
	}

	@Test
	public void aRefreshWithNothingChangedDoesNotRebuild()
	{
		kill("Abyssal demon", 995);
		panel.refresh();
		java.awt.Component first = panel.getComponent(0);
		panel.refresh();
		assertEquals("the same component, so a click in progress is not lost", first, panel.getComponent(0));
	}

	/**
	 * Squeezed, a column takes each row down to its minimum, and a plain panel's minimum is 10px: the
	 * card's 3 to 6px gaps and the hairline over a drop's detail grew while the rows beside them were
	 * cut, as the Group and Stars tabs' gaps did. Every stack of the card, the list and a monster under
	 * the Now killing bar is squeezed in turn, and each spacer in it holds its height.
	 */
	@Test
	public void aSqueezedColumnNeverGrowsItsGaps() throws Exception
	{
		now -= 3 * 60 * 60_000L;
		kill("Gargoyle", 995);
		now += 3 * 60 * 60_000L;
		kill("Abyssal demon", 4151);
		kill("Abyssal demon", 995);
		panel.refresh();
		int checked = squeezed();
		assertTrue(words(), SidebarWords.press(panel, "All monsters ›"));
		checked += squeezed();
		assertTrue(words(), SidebarWords.press(panel, "Gargoyle"));
		checked += squeezed();
		assertTrue("the card's, the list's and the Now killing bar's spacers: " + checked, checked >= 10);
	}

	/**
	 * The strip's three ends, a count of drops never had and a monster's "No notable drops yet" were in
	 * RuneLite's MEDIUM_GRAY, about 2:1 on what they sit on. Each reads at 4.5:1 or better now.
	 */
	@Test
	public void theQuietWordsCanBeRead() throws Exception
	{
		now -= 3 * 60 * 60_000L;
		kill("Chicken", 995);
		now += 3 * 60 * 60_000L;
		kill("Abyssal demon", 4151);
		kill("Abyssal demon", 995);
		panel.refresh();
		List<JLabel> quiet = labels(panel, Arrays.asList("Lucky", "Typical", "Dry", "0"), new ArrayList<>());
		assertTrue(words(), quiet.size() >= 5);
		assertTrue(words(), SidebarWords.press(panel, "All monsters ›"));
		List<JLabel> empty = labels(panel, Arrays.asList("No notable drops yet"), new ArrayList<>());
		assertEquals(words(), 1, empty.size());
		quiet.addAll(empty);
		for (JLabel label : quiet)
		{
			double ratio = contrast(label.getForeground(), background(label));
			assertTrue("\"" + label.getText() + "\" reads at " + String.format("%.2f", ratio) + ":1", ratio >= 4.5);
		}
	}

	/**
	 * The strip is the website's LuckStrip (osrs-stars-map's DropLuckSection.tsx), colours and stops:
	 * #4ade80 at 0, #71717a from 35 to 65%, #f59e0b at 85% and #f87171 at the end. It went from the
	 * grey straight to the red, so a wait in the Dry band sat on grey-pink beside an amber chip.
	 */
	@Test
	public void theStripRunsThroughTheDryChipsAmber()
	{
		/* The card's text width; the dot at a quarter, clear of every point read. */
		DropLuckPanel.Strip strip = new DropLuckPanel.Strip(0.25);
		strip.setSize(189, 16);
		BufferedImage drawn = new BufferedImage(strip.getWidth(), strip.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = drawn.createGraphics();
		strip.paint(g);
		g.dispose();
		/* The track runs from x 4 for the strip's width less 8, at y 5 to 10. */
		int w = strip.getWidth() - 8;
		assertNear("lucky", 0x4ade80, drawn.getRGB(4, 8));
		assertNear("typical", 0x71717a, drawn.getRGB(4 + (int) (w * 0.4), 8));
		assertNear("dry", 0xf59e0b, drawn.getRGB(4 + (int) (w * 0.85), 8));
		assertNear("very dry", 0xf87171, drawn.getRGB(4 + w - 1, 8));
	}

	/** Each stack holding a spacer, squeezed 4px under its height and laid out; how many spacers held. */
	private int squeezed() throws Exception
	{
		int[] checked = {0};
		SwingUtilities.invokeAndWait(() ->
		{
			for (Container column : columns(panel, new ArrayList<>()))
			{
				column.setSize(PluginPanel.PANEL_WIDTH - 20, column.getPreferredSize().height - 4);
				layout(column);
				for (Component child : column.getComponents())
				{
					if (spacer(child))
					{
						checked[0]++;
						assertTrue("a " + child.getPreferredSize().height + "px gap drawn " + child.getHeight() + "px tall",
							child.getHeight() <= child.getPreferredSize().height);
					}
				}
			}
		});
		return checked[0];
	}

	/** Every stack in the panel that holds a spacer of its own, the panel included. */
	private static List<Container> columns(Container parent, List<Container> found)
	{
		boolean holds = false;
		for (Component child : parent.getComponents())
		{
			if (spacer(child))
			{
				holds = true;
			}
			else if (child instanceof Container)
			{
				columns((Container) child, found);
			}
		}
		if (holds)
		{
			found.add(parent);
		}
		return found;
	}

	/** The spacers: empty panels one pixel wide, which is how a gap and the hairline are made. */
	private static boolean spacer(Component component)
	{
		return component instanceof JPanel && ((JPanel) component).getComponentCount() == 0
			&& component.getPreferredSize().width == 1;
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

	/** Every label shown whose words are one of {@code texts}. */
	private static List<JLabel> labels(Container parent, List<String> texts, List<JLabel> found)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel && texts.contains(((JLabel) child).getText()))
			{
				found.add((JLabel) child);
			}
			else if (child instanceof Container)
			{
				labels((Container) child, texts, found);
			}
		}
		return found;
	}

	/** What a label is drawn over: its own colour if it paints one, else the first panel under it that does. */
	private static Color background(Component component)
	{
		for (Component at = component; at != null; at = at.getParent())
		{
			if (at.isOpaque() && at.getBackground() != null)
			{
				return at.getBackground();
			}
		}
		throw new AssertionError("nothing is painted under " + component);
	}

	/** WCAG's contrast ratio. */
	private static double contrast(Color a, Color b)
	{
		double x = luminance(a);
		double y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static double luminance(Color c)
	{
		double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
		for (int i = 0; i < 3; i++)
		{
			rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
	}

	/** A pixel within a few levels of {@code expected}: one read beside a stop is a step into the next band. */
	private static void assertNear(String what, int expected, int rgb)
	{
		Color want = new Color(expected);
		Color seen = new Color(rgb);
		assertTrue(what + ": " + seen + " where " + want + " was expected", Math.abs(seen.getRed() - want.getRed()) <= 6
			&& Math.abs(seen.getGreen() - want.getGreen()) <= 6 && Math.abs(seen.getBlue() - want.getBlue()) <= 6);
	}
}
