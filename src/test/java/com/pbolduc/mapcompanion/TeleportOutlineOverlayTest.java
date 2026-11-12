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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The teleport the step being followed uses is outlined where it is clicked, and nowhere else. */
public class TeleportOutlineOverlayTest
{
	private final DestinationRoute route = new DestinationRoute();
	private final HarnessConfig config = new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL);
	private boolean instance;
	private Widget spellbook;
	/** The game tick the fake client is on, and how many times a widget was asked for its children. */
	private int tick;
	private static int walked;

	private final Client client = (Client) Proxy.newProxyInstance(
		Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getTopLevelWorldView":
					return Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[]{WorldView.class},
						(view, asked, none) -> asked.getName().equals("isInstance") ? instance : null);
				case "getWidget":
					return InterfaceID.MagicSpellbook.UNIVERSE == (Integer) args[0] ? spellbook : null;
				case "getTickCount":
					return tick;
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});

	private final TeleportOutlineOverlay overlay = new TeleportOutlineOverlay(client, config, route, null);

	/** The fixture's route, whose first step casts Lumbridge Home Teleport and whose second walks. */
	private void received() throws IOException
	{
		String reply = new String(Files.readAllBytes(Paths.get("src", "test", "resources", "contract", "reply.json")), StandardCharsets.UTF_8);
		route.sync(new Gson().fromJson(reply, RelayResponse.class).directive.destination, null);
	}

	/**
	 * The step the box says, and only while it says it: a route followed, outside an instance, with the
	 * setting on. A walk names no teleport, so it outlines nothing.
	 */
	@Test
	public void outlinesForTheStepTheBoxSays() throws IOException
	{
		received();
		assertNull("received, not yet followed", overlay.shown());
		route.follow(true);
		assertEquals(Integer.valueOf(356), overlay.shown().spriteId);
		instance = true;
		assertNull("nothing inside an instance, as the box", overlay.shown());
		instance = false;
		config.set("outlineStepTeleport", false);
		assertNull(overlay.shown());
		config.set("outlineStepTeleport", true);
		route.advance(new WorldPoint(3222, 3218, 0), DestinationRoute.Sailing.ASHORE_UNKNOWN);
		assertNull("a walk is the second step", overlay.shown().spriteId);
	}

	/**
	 * The spell is the widget showing the step's icon, wherever the spellbook nests it, while it is
	 * shown: a spell filtered out of the book, or a book with no such icon, outlines nothing.
	 */
	@Test
	public void theSpellIsTheIconTheSpellbookShows()
	{
		Widget spell = widget(356, false, new Rectangle(40, 30, 24, 24));
		Widget hidden = widget(357, true, new Rectangle(70, 30, 24, 24));
		spellbook = widget(-1, false, new Rectangle(0, 0, 200, 260), widget(-1, false, new Rectangle(0, 0, 200, 260), hidden, spell));
		assertSame(spell, TeleportOutlineOverlay.showing(spellbook, 356));
		assertNull("filtered out of the book", TeleportOutlineOverlay.showing(spellbook, 357));
		assertNull(TeleportOutlineOverlay.showing(spellbook, 358));
		assertNull("no spellbook loaded", TeleportOutlineOverlay.showing(null, 356));

		BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		overlay.drawSpell(graphics, 356);
		graphics.dispose();
		assertEquals("its edge is the step's amber", MapPin.AMBER.getRGB(), image.getRGB(40, 42));
		assertEquals(MapPin.AMBER.getRGB(), image.getRGB(64, 42));
		assertEquals("the icon itself is not tinted, which would read as greyed", 0, image.getRGB(52, 42));
		assertEquals("nothing past it", 0, image.getRGB(80, 42));
		assertEquals(0, image.getRGB(36, 42));
	}

	/**
	 * The overlay draws every frame, and the spellbook is hundreds of widgets. The icon is looked for
	 * once a game tick; between ticks the widget found says where it is and whether it still shows.
	 */
	@Test
	public void theSpellbookIsWalkedOnceAGameTick()
	{
		boolean[] filtered = {false};
		Widget spell = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getSpriteId":
					return 356;
				case "isHidden":
					return filtered[0];
				case "getBounds":
					return new Rectangle(40, 30, 24, 24);
				default:
					return new Widget[0];
			}
		});
		spellbook = widget(-1, false, new Rectangle(0, 0, 200, 260), widget(-1, false, new Rectangle(0, 0, 200, 260), spell));
		walked = 0;
		assertTrue("the first frame finds the spell", outlined());
		int once = walked;
		assertTrue(once > 0);
		assertTrue(outlined());
		assertTrue(outlined());
		assertEquals("later frames of the same tick ask the book nothing", once, walked);

		/* A widget says it is hidden when anything above it is, so this is a closed book as much as a filtered spell. */
		filtered[0] = true;
		assertFalse("hidden inside the tick, the spell loses its outline that frame", outlined());
		filtered[0] = false;

		tick++;
		assertTrue(outlined());
		assertEquals("the next tick looks again", 2 * once, walked);

		Widget open = spellbook;
		spellbook = null;
		tick++;
		assertFalse("no book loaded, as on a tick with the tab closed", outlined());
		spellbook = open;
		assertTrue("and one opened inside the same tick is read the frame it opens", outlined());
	}

	/** Whether a frame drawn now edges the spell at (40, 30). */
	private boolean outlined()
	{
		BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		overlay.drawSpell(graphics, 356);
		graphics.dispose();
		return image.getRGB(40, 42) == MapPin.AMBER.getRGB();
	}

	private static Widget widget(int sprite, boolean hidden, Rectangle bounds, Widget... children)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getSpriteId":
					return sprite;
				case "isHidden":
					return hidden;
				case "getBounds":
					return bounds;
				case "getStaticChildren":
					walked++;
					return children;
				case "getDynamicChildren":
				case "getNestedChildren":
					return new Widget[0];
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});
	}
}
