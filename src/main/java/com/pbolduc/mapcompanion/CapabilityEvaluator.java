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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;

/**
 * Which travel unlocks the player is carrying, as yes/no answers.
 *
 * <p><b>The inventory never leaves the client.</b> That is the entire point of this class. The map
 * needs to know whether a glory is on you so it can rank the teleport it unlocks; it does not need
 * to know that you are also carrying 400 sharks. Item names are matched here and what travels is a
 * short list of flag names from a fixed vocabulary the map already understands.
 *
 * <p>Item names cover charge and trim variants without duplicating RuneLite's item-id table.
 * Jewellery that keeps an uncharged item must have a positive charge suffix; an eternal glory
 * is usable without one. Noted items are excluded before matching because a name alone does not
 * mean its teleport can be used.
 *
 * <p>The vocabulary below mirrors {@code lib/transport-capabilities.ts} in the map's repository,
 * where each flag is checked by test against the real transport table — a flag that matches no
 * transport is a bug there, and a flag here that the map does not know is a flag the map's
 * normalizer will drop.
 */
@Singleton
public class CapabilityEvaluator
{
	/** One unlock: the flag the map knows it by, and the item names, or the one item, that grant it. */
	private static final class Capability
	{
		final String flag;
		final Pattern name;
		/** Nonzero where the name cannot tell a spent item from a charged one, so the id decides. */
		final int itemId;

		Capability(String flag, String regex)
		{
			this(flag, regex, 0);
		}

		Capability(String flag, String regex, int itemId)
		{
			this.flag = flag;
			this.name = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			this.itemId = itemId;
		}
	}

	/**
	 * Prefixes cover variants of items that disappear when spent. Jewellery that remains when
	 * empty instead requires a positive charge suffix. Every match starts at the item's own name.
	 */
	private static final List<Capability> CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
			new Capability("achievementDiaryCape", "^achievement diary cape"),
			new Capability("maxCape", "^max cape"),
			new Capability("masterScrollBook", "^master scroll book"),
			new Capability("slayerRing", "^slayer ring"),
			new Capability("amuletOfGlory", "^(?:amulet of glory\\s*\\(t?[1-9][0-9]*\\)|amulet of eternal glory)$"),
			new Capability("constructionCape", "^construction cape"),
			new Capability("skillsNecklace", "^skills necklace\\s*\\([1-9][0-9]*\\)$"),
			new Capability("pendantOfAtes", "^pendant of ates"),
			new Capability("radasBlessing", "^rada'?s blessing"),
			new Capability("gamesNecklace", "^games necklace"),
			new Capability("kharedstMemoirs", "^kharedst'?s memoirs"),
			new Capability("bookOfTheDead", "^book of the dead"),
			new Capability("xericsTalisman", "^xeric'?s talisman"),
			new Capability("ringOfShadows", "^ring of shadows"),
			new Capability("ardougneCloak", "^ardougne cloak"),
			new Capability("morytaniaLegs", "^morytania legs"),
			new Capability("fremennikSeaBoots", "^fremennik sea boots"),
			new Capability("ringOfDueling", "^ring of dueling"),
			new Capability("combatBracelet", "^combat bracelet\\s*\\([1-9][0-9]*\\)$"),
			new Capability("ringOfWealth", "^ring of wealth\\s*\\(i?[1-9][0-9]*\\)$"),
			new Capability("enchantedLyre", "^enchanted lyre"),
			new Capability("pharaohsSceptre", "^pharaoh'?s sceptre"),
			/* The empty ring is named exactly as the charged one. */
			new Capability("ringOfTheElements", "^ring of the elements", ItemID.RING_OF_ELEMENTS_CHARGED),
			new Capability("digsitePendant", "^digsite pendant"),
			new Capability("necklaceOfPassage", "^necklace of passage"),
			new Capability("burningAmulet", "^burning amulet"),
			new Capability("hunterCape", "^hunter cape"),
			new Capability("fishingCape", "^fishing cape"),
			new Capability("drakansMedallion", "^drakan'?s medallion"),
			new Capability("giantsoulAmulet", "^giantsoul amulet"),
			new Capability("sailorsAmulet", "^sailors'? amulet"),
			new Capability("desertAmulet", "^desert amulet"),
			new Capability("teleportCrystal", "^(eternal )?teleport crystal"),
			new Capability("camulet", "^camulet"),
			new Capability("stonyBasalt", "^stony basalt"),
			new Capability("karamjaGloves", "^karamja gloves"),
			new Capability("quetzalWhistle", "^(perfected |basic |enhanced )?quetzal whistle"),
			new Capability("royalSeedPod", "^royal seed pod"),
			new Capability("dramenStaff", "^(dramen|lunar) staff")
		));

	private final Client client;

	@Inject
	CapabilityEvaluator(Client client)
	{
		this.client = client;
	}

	/**
	 * Evaluate the flags. Must run on the client thread.
	 *
	 * <p>Returns an empty list rather than null when nothing is carried: an empty list is the claim
	 * "asked and found none", which the map can act on, while null makes it abstain — which is what
	 * an inventory the client has not been sent yet calls for. Keeping those distinct is the same
	 * three-way discipline the quest list follows.
	 */
	@Nullable
	public List<String> evaluate()
	{
		if (client.getItemContainer(InventoryID.INV) == null)
		{
			/*
			 * Not received yet, right after a login or a hop. "Carrying nothing" here would be a
			 * confident claim that drops every jewellery route until the next post, so say nothing.
			 */
			return null;
		}
		Set<String> names = new LinkedHashSet<>();
		Set<Integer> ids = new LinkedHashSet<>();
		collectNames(names, ids, InventoryID.INV);
		collectNames(names, ids, InventoryID.WORN);

		List<String> flags = new ArrayList<>();
		for (Capability capability : CAPABILITIES)
		{
			if (capability.itemId != 0)
			{
				if (ids.contains(capability.itemId))
				{
					flags.add(capability.flag);
				}
				continue;
			}
			for (String name : names)
			{
				if (capability.name.matcher(name).find())
				{
					flags.add(capability.flag);
					break;
				}
			}
		}
		return flags;
	}

	private void collectNames(Set<String> into, Set<Integer> ids, int which)
	{
		ItemContainer container = client.getItemContainer(which);
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			try
			{
				ItemComposition composition = client.getItemDefinition(item.getId());
				if (composition == null || composition.getNote() != -1)
				{
					continue;
				}
				String name = composition.getName();
				if (name == null || name.isEmpty() || "null".equals(name))
				{
					continue;
				}
				String lower = name.toLowerCase();
				if (lower.contains("(inert)") || lower.contains("(uncharged)"))
				{
					/* Xeric's talisman, Pharaoh's sceptre, the ring of shadows, the giantsoul amulet: spent, no teleport. */
					continue;
				}
				into.add(name.trim());
				ids.add(item.getId());
			}
			catch (RuntimeException e)
			{
				/** An item the client cannot name is simply not counted; absence is not a claim. */
			}
		}
	}
}
