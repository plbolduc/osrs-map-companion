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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.WorldView;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * The teleport the step being followed uses, outlined where it is clicked: its item in the inventory
 * or among worn items, or its spell in the spellbook, by the id the map sent with the step for its
 * picture. The box over the game says which teleport; this puts the answer under the pointer.
 *
 * <p>Only while the route is followed and outside an instance, as the box is, and only drawn: nothing
 * is clicked, moved or sent. An item is matched as {@link CapabilityEvaluator#stepItem} says, so a
 * glory with charges left is outlined for a step that names a full one and an emptied one never is,
 * and a bank note is never the item. A spell is matched by the icon the spellbook shows, which is the
 * map's sprite only while the spell can be cast, so a spell greyed for want of runes or level, or in
 * another spellbook, is left dark. Client thread throughout.
 */
class TeleportOutlineOverlay extends WidgetItemOverlay
{
	private static final Stroke EDGE = new BasicStroke(2);

	private final Client client;
	private final MapCompanionConfig config;
	private final DestinationRoute route;
	private final ItemManager items;
	/** The step this frame draws for, set before any item is asked about. */
	@Nullable
	private RelayResponse.RouteStep step;
	/** Each item's answer for the step's item, {@link #markedFor}, because an inventory draws every frame. */
	private final Map<Integer, Boolean> marked = new HashMap<>();
	private Integer markedFor;
	private CapabilityEvaluator.ItemTest test;
	/** The spell's icon in the open spellbook, for the sprite and on the game tick it was looked for; see {@link #drawSpell}. */
	@Nullable
	private Widget spell;
	private int spellSprite, spellTick;

	@Inject
	TeleportOutlineOverlay(Client client, MapCompanionConfig config, DestinationRoute route, ItemManager items)
	{
		this.client = client;
		this.config = config;
		this.route = route;
		this.items = items;
		showOnInventory();
		showOnEquipment();
		showOnInterfaces(InterfaceID.MAGIC_SPELLBOOK);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		step = shown();
		super.render(graphics);
		if (step != null && step.spriteId != null)
		{
			drawSpell(graphics, step.spriteId);
		}
		return null;
	}

	/** The step being followed, as the box decides it, while the setting is on; null otherwise. */
	@Nullable
	RelayResponse.RouteStep shown()
	{
		WorldView view = client.getTopLevelWorldView();
		return !config.outlineStepTeleport() || view != null && view.isInstance() ? null : RouteStepOverlay.followed(route.view());
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item)
	{
		RelayResponse.RouteStep shown = step;
		if (shown == null || shown.itemId == null)
		{
			return;
		}
		if (!shown.itemId.equals(markedFor))
		{
			markedFor = shown.itemId;
			test = CapabilityEvaluator.stepItem(shown.itemId, items.getItemComposition(shown.itemId).getName());
			marked.clear();
		}
		Boolean hit = marked.get(itemId);
		if (hit == null)
		{
			ItemComposition held = items.getItemComposition(itemId);
			hit = held.getNote() == -1 && test.grants(itemId, held.getName());
			marked.put(itemId, hit);
		}
		if (hit)
		{
			Rectangle bounds = item.getCanvasBounds();
			graphics.drawImage(items.getItemOutline(itemId, item.getQuantity(), MapPin.AMBER), bounds.x, bounds.y, null);
		}
	}

	/**
	 * The spell's icon edged in the step's amber while the spellbook shows it. Edged and never tinted:
	 * the game greys a spell it cannot cast, so a darkened icon would say the opposite.
	 */
	void drawSpell(Graphics2D graphics, int spriteId)
	{
		/*
		 * This draws every frame and the book is a few hundred widgets, so an icon found is looked
		 * for again only on the next game tick, which is as often as the game changes what the book
		 * shows. Between ticks the widget found is asked where it is and whether it still shows,
		 * which a closed book answers too. With none found the book is asked each frame, so the
		 * outline is there the frame the book opens; a closed book answers that at once.
		 */
		int tick = client.getTickCount();
		if (spell == null || tick != spellTick || spriteId != spellSprite)
		{
			spellTick = tick;
			spellSprite = spriteId;
			spell = showing(client.getWidget(InterfaceID.MagicSpellbook.UNIVERSE), spriteId);
		}
		if (spell != null && !spell.isHidden())
		{
			Stroke before = graphics.getStroke();
			graphics.setColor(MapPin.AMBER);
			graphics.setStroke(EDGE);
			graphics.draw(spell.getBounds());
			graphics.setStroke(before);
		}
	}

	/** The widget under {@code root}, itself included, that shows {@code spriteId} and is not hidden. */
	@Nullable
	static Widget showing(@Nullable Widget root, int spriteId)
	{
		if (root == null || root.isHidden())
		{
			return null;
		}
		if (root.getSpriteId() == spriteId)
		{
			return root;
		}
		for (Widget[] children : new Widget[][]{root.getStaticChildren(), root.getDynamicChildren(), root.getNestedChildren()})
		{
			for (Widget child : children == null ? new Widget[0] : children)
			{
				Widget found = showing(child, spriteId);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
