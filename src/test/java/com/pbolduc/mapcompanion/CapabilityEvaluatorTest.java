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
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Carrying an item name is not enough: zero charges and bank notes cannot fund a teleport. */
public class CapabilityEvaluatorTest
{
	@Test
	public void theBankChecklistOnlyClaimsMissingItemsFromItsKnownTravelVocabulary()
	{
		assertEquals(Boolean.TRUE, CapabilityEvaluator.carrying("Ring of dueling", Arrays.asList("ringOfDueling")));
		assertEquals(Boolean.FALSE, CapabilityEvaluator.carrying("Ring of dueling", Arrays.asList("amuletOfGlory")));
		assertEquals(null, CapabilityEvaluator.carrying("Ring of dueling", null));
		assertEquals(null, CapabilityEvaluator.carrying("Catherby Teleport", Arrays.asList("ringOfDueling")));
		assertEquals(null, CapabilityEvaluator.carrying("My supplies", Arrays.asList("ringOfDueling")));
	}

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

	/**
	 * The game shortens the cape's name to "Construct. cape", trimmed or not, and either one carried or
	 * worn is the flag the map routes by and the bank checklist reads.
	 */
	@Test
	public void theConstructionCapeIsKnownByTheNameTheGameGivesIt()
	{
		game.define(ItemID.SKILLCAPE_CONSTRUCTION, "Construct. cape", false);
		game.define(ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED, "Construct. cape(t)", false);
		game.carry(ItemID.SKILLCAPE_CONSTRUCTION);
		assertEquals(Arrays.asList("constructionCape"), evaluator.evaluate());
		game.carry();
		game.equip(ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED);
		assertEquals(Arrays.asList("constructionCape"), evaluator.evaluate());
		assertTrue("and the bank outlines it for a run that needs one",
			CapabilityEvaluator.itemTest("Construction cape").grants(ItemID.SKILLCAPE_CONSTRUCTION, "Construct. cape"));
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
		game.define(2, "Construct. cape", true);
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

	/**
	 * The item a route step names stands for each of its family that can make the trip: another charge,
	 * a trim, the eternal glory, a higher diary tier, a trimmed cape. Never a spent one, a lower tier,
	 * or the ring of the elements' empty twin, which only its id tells apart.
	 */
	@Test
	public void aStepsItemIsAnyOfItsFamilyThatCanMakeTheTrip()
	{
		CapabilityEvaluator.ItemTest glory = CapabilityEvaluator.stepItem(ItemID.AMULET_OF_GLORY_6, "Amulet of glory(6)");
		assertTrue(glory.grants(ItemID.AMULET_OF_GLORY_6, "Amulet of glory(6)"));
		assertTrue(glory.grants(ItemID.AMULET_OF_GLORY_4, "Amulet of glory(4)"));
		assertTrue(glory.grants(ItemID.TRAIL_AMULET_OF_GLORY_3, "Amulet of glory(t3)"));
		assertTrue("RuneLite's variations leave it out, the vocabulary does not",
			glory.grants(ItemID.AMULET_OF_GLORY_INF, "Amulet of eternal glory"));
		assertFalse("an emptied glory carries nothing", glory.grants(ItemID.AMULET_OF_GLORY, "Amulet of glory"));

		CapabilityEvaluator.ItemTest cloak = CapabilityEvaluator.stepItem(ItemID.ARDY_CAPE_MEDIUM, "Ardougne cloak 2");
		assertTrue("a higher tier makes the trip", cloak.grants(ItemID.ARDY_CAPE_ELITE, "Ardougne cloak 4"));
		assertFalse("a lower one does not", cloak.grants(ItemID.ARDY_CAPE_EASY, "Ardougne cloak 1"));

		CapabilityEvaluator.ItemTest ring = CapabilityEvaluator.stepItem(ItemID.LUMBRIDGE_RING_MEDIUM, "Explorer's ring 2");
		assertTrue("outside the vocabulary, RuneLite's family", ring.grants(ItemID.LUMBRIDGE_RING_ELITE, "Explorer's ring 4"));
		assertFalse(ring.grants(ItemID.LUMBRIDGE_RING_EASY, "Explorer's ring 1"));
		assertTrue(CapabilityEvaluator.stepItem(ItemID.SKILLCAPE_CRAFTING, "Crafting cape")
			.grants(ItemID.SKILLCAPE_CRAFTING_TRIMMED, "Crafting cape(t)"));

		CapabilityEvaluator.ItemTest elements = CapabilityEvaluator.stepItem(ItemID.RING_OF_ELEMENTS_CHARGED, "Ring of the elements");
		assertTrue(elements.grants(ItemID.RING_OF_ELEMENTS_CHARGED, "Ring of the elements"));
		assertFalse("the empty ring has the same name", elements.grants(ItemID.RING_OF_ELEMENTS, "Ring of the elements"));
		/* Whichever twin a step pictures, the charged ring is the one that teleports. */
		CapabilityEvaluator.ItemTest pictured = CapabilityEvaluator.stepItem(ItemID.RING_OF_ELEMENTS, "Ring of the elements");
		assertTrue(pictured.grants(ItemID.RING_OF_ELEMENTS_CHARGED, "Ring of the elements"));
		assertFalse(pictured.grants(ItemID.RING_OF_ELEMENTS, "Ring of the elements"));
		assertFalse(CapabilityEvaluator.stepItem(ItemID.XERIC_TALISMAN, "Xeric's talisman")
			.grants(ItemID.XERIC_TALISMAN_EMPTY, "Xeric's talisman (inert)"));
		CapabilityEvaluator.ItemTest spentPictured = CapabilityEvaluator.stepItem(ItemID.XERIC_TALISMAN_EMPTY, "Xeric's talisman (inert)");
		assertTrue("a spent twin pictured still means the live one", spentPictured.grants(ItemID.XERIC_TALISMAN, "Xeric's talisman"));
		assertFalse(spentPictured.grants(ItemID.XERIC_TALISMAN_EMPTY, "Xeric's talisman (inert)"));

		CapabilityEvaluator.ItemTest tablet = CapabilityEvaluator.stepItem(ItemID.POH_TABLET_LUMBRIDGETELEPORT, "Lumbridge teleport");
		assertTrue(tablet.grants(ItemID.POH_TABLET_LUMBRIDGETELEPORT, "Lumbridge teleport"));
		assertFalse("a tablet is itself alone", tablet.grants(ItemID.POH_TABLET_VARROCKTELEPORT, "Varrock teleport"));
	}
}
