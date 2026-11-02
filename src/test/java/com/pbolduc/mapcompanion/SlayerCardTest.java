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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the Slayer card shows while something it stands on is not ready yet, read off the real
 * {@link SlayerPanel} as a player sees it: the map ranking the places again.
 *
 * <p>The places are the map's own Gargoyles list ({@code contract/slayer-options.json}). No press
 * reaches the map: nothing here is routed.
 */
public class SlayerCardTest
{
	private static final String EPOCH = "contract-relay-epoch";

	private final Gson gson = new Gson();
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final MemberIdentity identity = new MemberIdentity(store);
	private final SlayerState state = new SlayerState();
	private final ClientThread thread = new ClientThread()
	{
		@Override
		public void invokeLater(Runnable action)
		{
			action.run();
		}
	};
	private SlayerPanel panel;

	public SlayerCardTest() throws Exception
	{
		config.set("shareSlayer", true);
		DestinationState destinations = new DestinationState(gson);
		TestDestinationMarker marker = new TestDestinationMarker();
		RoutePress routes = new RoutePress(new OkHttpClient(), gson, game.client(), thread, new Pairing(store, gson),
			identity, new DestinationRoute(), destinations, marker);
		/* No choice is pressed here, so the settings it would write have nowhere to go. */
		SwingUtilities.invokeAndWait(() -> panel = new SlayerPanel(state, config, null, routes, destinations, marker, thread,
			new GameSprites(null, null, null)));
	}

	/**
	 * A new ranking choice keeps the places on the card, greyed, while the map ranks them again. The
	 * picker just used stays under the pointer showing the choice, the Wilderness box beside it, and
	 * nothing can be routed from a list ranked for the other order. The new places replace them.
	 */
	@Test
	public void aNewChoiceKeepsThePlacesAndThePickerWhileTheMapRanksAgain() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		press("▸ All 3 places & options");

		/* The picker's own first act, before it writes the setting. */
		state.choices("single", true);
		refresh();
		List<String> waiting = shown(panel);
		assertTrue(waiting.toString(), waiting.contains("Ranking again for one target at a time…"));
		assertTrue(waiting.toString(), waiting.contains("1. Slayer Tower (basement)"));
		assertTrue(waiting.toString(), waiting.contains("3. Slayer Tower (roof)"));
		assertTrue(waiting.toString(), waiting.contains("Hide Wilderness"));
		JComboBox<?> order = (JComboBox<?>) find(panel, "slayer.order");
		assertNotNull("the picker stays on the card: " + waiting, order);
		assertEquals("and shows the choice just made", 1, order.getSelectedIndex());
		assertFalse("no Route from places ranked for the other order", button(panel, "Route").isEnabled());

		received(places("single"), 2);
		refresh();
		List<String> ranked = shown(panel);
		assertTrue(ranked.toString(), ranked.contains("Slayer Tower"));
		assertFalse(ranked.toString(), ranked.contains("Ranking again for one target at a time…"));
		assertTrue(button(panel, "Route").isEnabled());
	}

	private CompanionSnapshot.Slayer gargoyles()
	{
		CompanionSnapshot.Slayer task = new CompanionSnapshot.Slayer();
		task.taskName = "Gargoyles";
		task.areaName = "Slayer Tower";
		task.remaining = 143;
		return task;
	}

	private RelayResponse.SlayerOptions places(String order) throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/slayer-options.json").readAllBytes(), StandardCharsets.UTF_8);
		RelayResponse.SlayerOptions options = SlayerWire.normalize(gson.fromJson(json, RelayResponse.SlayerOptions.class));
		options.order = order;
		return options;
	}

	private void received(RelayResponse.SlayerOptions options, long revision)
	{
		RelayResponse.Directive directive = new RelayResponse.Directive();
		directive.relayEpoch = EPOCH;
		directive.slayerOptions = options;
		directive.slayerRevision = revision;
		state.accept(identity.id(), directive, System.currentTimeMillis());
	}

	private void refresh() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> panel.refresh(true));
	}

	private void press(String words) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AbstractButton link = button(panel, words);
			assertNotNull("no " + words + " to press: " + shown(panel), link);
			link.doClick();
		});
	}

	private static AbstractButton button(Component component, String words)
	{
		if (!component.isVisible())
		{
			return null;
		}
		if (component instanceof AbstractButton && words.equals(((AbstractButton) component).getText()))
		{
			return (AbstractButton) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				AbstractButton found = button(child, words);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static Component find(Component component, String name)
	{
		if (!component.isVisible())
		{
			return null;
		}
		if (name.equals(component.getName()))
		{
			return component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				Component found = find(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The words on screen, in order: every label and button shown, inside parents that are shown. */
	private static List<String> shown(Component root)
	{
		List<String> out = new ArrayList<>();
		collect(root, out);
		return out;
	}

	private static void collect(Component component, List<String> out)
	{
		if (!component.isVisible())
		{
			return;
		}
		String text = component instanceof JLabel ? ((JLabel) component).getText()
			: component instanceof AbstractButton ? ((AbstractButton) component).getText() : null;
		if (text != null && !text.isEmpty())
		{
			out.add(text.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim());
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, out);
			}
		}
	}
}
