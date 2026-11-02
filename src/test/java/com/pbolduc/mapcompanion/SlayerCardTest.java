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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the Slayer card says, read off the real {@link SlayerPanel} as a player sees it, above all
 * while something it stands on is not ready yet: the map ranking the places again, or the game
 * still to say whether there is a task at all.
 *
 * <p>The places are the map's own Gargoyles lists ({@code contract/slayer-options.json}, and
 * {@code slayer-blocked.json} for a profile below every place's requirement). No press reaches the
 * map: nothing here is routed.
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

	/**
	 * "No Slayer task" is said only once the game has said so, with the step that changes it. Before
	 * the session settles every varbit reads zero, and a task this client cannot name reads as none
	 * too: both are sent as no task, as they must be, and neither may tell a player with a task to go
	 * and get one. The card then claims nothing but when the task will appear.
	 */
	@Test
	public void noTaskIsSaidOnlyOnceTheGameHasSaidSo() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		QuestReader quests = new QuestReader(game.client());
		SnapshotBuilder builder = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(game.client());
				bind(Gson.class).toInstance(gson);
				bind(MapCompanionConfig.class).toInstance(config);
				bind(MemberIdentity.class).toInstance(identity);
				bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
				bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
				bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
				bind(QuestReader.class).toInstance(quests);
				bind(SlayerState.class).toInstance(state);
			}
		}).getInstance(SnapshotBuilder.class);
		quests.onSessionResumed(0);

		assertNull(builder.build(14_999).slayer);
		refresh();
		List<String> unsettled = shown(panel);
		assertFalse(unsettled.toString(), unsettled.contains(SlayerPanel.NO_TASK));
		assertTrue(unsettled.toString(), unsettled.contains("The current assignment appears here once the game has settled."));

		game.setVarp(VarPlayerIds.SLAYER_TARGET, 99);
		game.setVarp(VarPlayerIds.SLAYER_COUNT, 40);
		assertNull("a task this client cannot name is sent as none", builder.build(15_000).slayer);
		refresh();
		assertFalse(shown(panel).toString(), shown(panel).contains(SlayerPanel.NO_TASK));

		game.setVarp(VarPlayerIds.SLAYER_TARGET, 0);
		game.setVarp(VarPlayerIds.SLAYER_COUNT, 0);
		assertNull(builder.build(16_000).slayer);
		refresh();
		List<String> none = shown(panel);
		assertTrue(none.toString(), none.contains(SlayerPanel.NO_TASK));
		assertTrue(none.toString(), none.contains(SlayerPanel.GET_ONE));
	}

	/**
	 * Where every place asks for something not met yet, nothing wears Best for you and the card shows
	 * the first place alone; one line above it says why, instead of leaving "Needs 75 Slayer" to.
	 */
	@Test
	public void aListWithNoPlaceOpenYetSaysSo() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("/contract/slayer-blocked.json", "best"), 1);
		refresh();
		List<String> blocked = shown(panel);
		assertTrue(blocked.toString(), blocked.indexOf(SlayerPanel.NONE_OPEN) >= 0
			&& blocked.indexOf(SlayerPanel.NONE_OPEN) < blocked.indexOf("1. Slayer Tower (basement)"));

		received(places("best"), 2);
		refresh();
		assertFalse(shown(panel).contains(SlayerPanel.NONE_OPEN));
	}

	/** The closed order box says what it chooses, "Order: Best spot"; the open list keeps the choices' own names. */
	@Test
	public void theOrderBoxSaysWhatItChooses() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		press("▸ All 3 places & options");
		SwingUtilities.invokeAndWait(() ->
		{
			@SuppressWarnings("unchecked")
			JComboBox<String> order = (JComboBox<String>) find(panel, "slayer.order");
			JList<String> list = new JList<>();
			assertEquals("Order: Best spot", ((JLabel) order.getRenderer().getListCellRendererComponent(list, "Best spot", -1, false, false)).getText());
			assertEquals("One target at a time", ((JLabel) order.getRenderer().getListCellRendererComponent(list, "One target at a time", 1, false, false)).getText());
		});
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
		return places("/contract/slayer-options.json", order);
	}

	private RelayResponse.SlayerOptions places(String fixture, String order) throws Exception
	{
		String json = new String(getClass().getResourceAsStream(fixture).readAllBytes(), StandardCharsets.UTF_8);
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
