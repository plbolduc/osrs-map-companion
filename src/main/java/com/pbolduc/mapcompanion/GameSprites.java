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
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

/**
 * The game's own pictures for the sidebar: an item's sprite from {@link ItemManager}, and an
 * interface sprite — a world-map icon, a spell — from {@link SpriteManager}.
 *
 * <p>Only from the client's own cache, never from the map. A picture per search row or route step
 * fetched from a server would be a request to a third party on every search, which the README
 * would have to name, for a sprite the game already holds on this machine.
 *
 * <p>Both managers answer later: an item sprite loads on the client thread, and an interface sprite
 * is read there once the cache is up. Each picture is set on its label on the event thread when it
 * arrives, so a row is laid out at once with an empty slot of the right size and fills in.
 */
@Singleton
class GameSprites
{
	@Nullable
	private final ItemManager items;
	@Nullable
	private final SpriteManager sprites;
	@Nullable
	private final SkillIconManager skills;

	@Inject
	GameSprites(@Nullable ItemManager items, @Nullable SpriteManager sprites, @Nullable SkillIconManager skills)
	{
		this.items = items;
		this.sprites = sprites;
		this.skills = skills;
	}

	/**
	 * A skill's small icon, the one the game draws beside a level, by the skill's name as the map
	 * words it ("Woodcutting"). A name that is no skill gets no picture.
	 */
	void skill(JLabel label, String name, int box)
	{
		if (skills == null || name == null)
		{
			return;
		}
		Skill skill;
		try
		{
			skill = Skill.valueOf(name.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException notASkill)
		{
			return;
		}
		show(label, skills.getSkillImage(skill, true), box);
	}

	/** An item's sprite, shrunk to fit {@code box} square when it is larger, on {@code label}. */
	void item(JLabel label, int itemId, int box)
	{
		if (items == null || itemId <= 0)
		{
			return;
		}
		AsyncBufferedImage image = items.getImage(itemId);
		image.onLoaded(() -> show(label, image, box));
		show(label, image, box);
	}

	/**
	 * An item's sprite by its exact name, for an item the map names no id for — a drop no shop
	 * stocks, such as an abyssal whip. Asked of the client's own table of tradeable items, and only a
	 * name that is exactly one item there gets a picture; anything else gets none rather than the
	 * nearest spelling's.
	 */
	void itemNamed(JLabel label, String name, int box)
	{
		if (items == null)
		{
			return;
		}
		Integer found = null;
		for (ItemPrice price : items.search(name))
		{
			if (price.getName().equalsIgnoreCase(name))
			{
				if (found != null && found != price.getId())
				{
					return;
				}
				found = price.getId();
			}
		}
		if (found != null)
		{
			item(label, found, box);
		}
	}

	/** An interface sprite — its archive in {@code net.runelite.api.gameval.SpriteID}, frame 0. */
	void sprite(JLabel label, int spriteId, int box)
	{
		if (sprites == null || spriteId <= 0)
		{
			return;
		}
		sprites.getSpriteAsync(spriteId, 0, (Consumer<BufferedImage>) image -> show(label, image, box));
	}

	/** The same, on a button: a filter chip that carries the game's own mark beside its word. */
	void sprite(AbstractButton button, int spriteId, int box)
	{
		if (sprites == null || spriteId <= 0)
		{
			return;
		}
		sprites.getSpriteAsync(spriteId, 0, (Consumer<BufferedImage>) image -> show(button, image, box));
	}

	/** Set on the event thread, at the sprite's own size when it fits, so pixel art stays crisp. */
	static void show(JLabel label, @Nullable BufferedImage image, int box)
	{
		if (image == null)
		{
			return;
		}
		BufferedImage fitted = fit(image, box);
		SwingUtilities.invokeLater(() -> label.setIcon(new ImageIcon(fitted)));
	}

	static void show(AbstractButton button, @Nullable BufferedImage image, int box)
	{
		if (image == null)
		{
			return;
		}
		BufferedImage fitted = fit(image, box);
		SwingUtilities.invokeLater(() -> button.setIcon(new ImageIcon(fitted)));
	}

	static BufferedImage fit(BufferedImage image, int box)
	{
		if (image.getWidth() <= box && image.getHeight() <= box)
		{
			return image;
		}
		return ImageUtil.resizeImage(image, box, box, true);
	}
}
