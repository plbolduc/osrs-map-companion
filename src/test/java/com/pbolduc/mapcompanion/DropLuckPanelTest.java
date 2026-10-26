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
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
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
		return String.join(" | ", panel.words());
	}

	@Test
	public void nothingKilledDrawsNothing()
	{
		panel.refresh();
		assertFalse(panel.hasContent());
		assertTrue(panel.words().isEmpty());
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
		assertTrue(words(), panel.press("All monsters ›"));
		String list = words();
		assertTrue(list, list.contains("2 monsters tracked"));
		assertTrue(list, list.contains("1 kill · 3 h ago"));
		assertTrue(panel.press("Gargoyle"));
		String card = words();
		assertTrue(card, card.contains("Now killing Abyssal demon"));
		assertTrue(card, card.contains("‹ All monsters"));
		assertTrue(card, card.contains("Last killed 3 h ago · 1 recorded kill"));
		assertTrue(panel.press("Back ›"));
		assertTrue(words(), words().contains("Killing now"));
		/* The sidebar heads this section "Drop luck"; the card under it does not say it again. */
		assertFalse(words(), words().contains("Drop luck"));
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
		assertTrue(words(), panel.press("All monsters ›"));
		assertTrue(panel.press("Abyssal demon"));
		assertTrue(panel.press("Abyssal whip"));
		assertTrue(panel.press("Pin"));
		assertTrue(panel.press("‹ All monsters"));
		assertTrue(panel.press("Close"));
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
		assertTrue(words(), panel.press("All monsters ›"));
		/* RuneLite's search field asserts it is touched on the event thread, as the sidebar does. */
		javax.swing.SwingUtilities.invokeAndWait(() -> panel.search("whip"));
		List<String> found = panel.words();
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
}
