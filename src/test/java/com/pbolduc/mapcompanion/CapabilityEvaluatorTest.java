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

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Carrying an item name is not enough: zero charges and bank notes cannot fund a teleport. */
public class CapabilityEvaluatorTest
{
	private final ItemReadingsTestClient game = new ItemReadingsTestClient();
	private final CapabilityEvaluator evaluator = new CapabilityEvaluator(game.client);

	@Test
	public void unchargedJewelleryDoesNotAdvertiseTeleports()
	{
		String[] names = {"Amulet of glory", "Skills necklace", "Combat bracelet", "Ring of wealth",
						  "Amulet of glory (t)", "Skills necklace(0)"
						 };
		for (int i = 0; i < names.length; i++)
		{
			game.define(i + 1, names[i], false);
		}
		game.carry(1, 2, 3, 4, 5, 6);
		assertTrue(evaluator.evaluate().isEmpty());
	}

	@Test
	public void positiveChargesAreRecognisedInInventoryAndEquipment()
	{
		game.define(1, "Amulet of glory(1)", false);
		game.define(2, "Skills necklace(6)", false);
		game.define(3, "Combat bracelet(4)", false);
		game.define(4, "Ring of wealth (5)", false);
		game.carry(2, 3, 4);
		game.equip(1);
		assertEquals(Arrays.asList("amuletOfGlory", "skillsNecklace", "combatBracelet", "ringOfWealth"),
					 evaluator.evaluate());
	}

	@Test
	public void trimmedImbuedAndEternalVariantsRemainUsable()
	{
		game.define(1, "Amulet of glory (t4)", false);
		game.define(2, "Ring of wealth (i3)", false);
		game.carry(1, 2);
		assertEquals(Arrays.asList("amuletOfGlory", "ringOfWealth"), evaluator.evaluate());
		game.define(3, "Amulet of eternal glory", false);
		game.carry(3);
		assertEquals(Arrays.asList("amuletOfGlory"), evaluator.evaluate());
	}

	@Test
	public void notedItemsCannotGrantAnyCapability()
	{
		game.define(1, "Amulet of glory(6)", true);
		game.define(2, "Construction cape", true);
		game.carry(1, 2);
		assertTrue(evaluator.evaluate().isEmpty());
	}

	/** The spent forms of four teleports share the charged one's name up to a suffix. */
	@Test
	public void spentTeleportItemsAdvertiseNothing()
	{
		game.define(1, "Xeric's talisman (inert)", false);
		game.define(2, "Pharaoh's sceptre (uncharged)", false);
		game.define(3, "Ring of shadows (uncharged)", false);
		game.define(4, "Giantsoul amulet (uncharged)", false);
		game.carry(1, 2, 3, 4);
		assertTrue(evaluator.evaluate().isEmpty());
		game.define(5, "Xeric's talisman", false);
		game.carry(5);
		assertEquals(Arrays.asList("xericsTalisman"), evaluator.evaluate());
	}

	/** The empty ring of the elements is named exactly as the charged one, so its id decides. */
	@Test
	public void onlyTheChargedRingOfTheElementsTeleports()
	{
		game.define(net.runelite.api.gameval.ItemID.RING_OF_ELEMENTS, "Ring of the elements", false);
		game.define(net.runelite.api.gameval.ItemID.RING_OF_ELEMENTS_CHARGED, "Ring of the elements", false);
		game.carry(net.runelite.api.gameval.ItemID.RING_OF_ELEMENTS);
		assertTrue(evaluator.evaluate().isEmpty());
		game.carry(net.runelite.api.gameval.ItemID.RING_OF_ELEMENTS_CHARGED);
		assertEquals(Arrays.asList("ringOfTheElements"), evaluator.evaluate());
	}

	/** An inventory the client has not been sent yet is unknown, not empty. */
	@Test
	public void anUnreadInventoryIsNotCarryingNothing()
	{
		game.unreadInventory();
		org.junit.Assert.assertNull(evaluator.evaluate());
	}
}
