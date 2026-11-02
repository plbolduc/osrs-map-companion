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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
		assertTrue("the common drop is the one opened, not the 1 in 32,000", words.contains("Abyssal whip | Pin"));
		assertTrue(words, words.contains("Since last drop"));
		assertTrue(words, words.contains("1 kill | >99% of players would still be waiting too"));
		assertTrue(words, words.contains("Pin"));
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
}
