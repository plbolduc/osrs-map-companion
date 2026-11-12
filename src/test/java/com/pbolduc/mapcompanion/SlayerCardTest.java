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
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
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
	private final DestinationState destinations = new DestinationState(gson);
	/** What the game's world map is handed, with the game's thread run at once. */
	private final List<WorldMapPoint> drawn = new ArrayList<>();
	private SlayerPanel panel;

	public SlayerCardTest() throws Exception
	{
		config.set("shareSlayer", true);
		DestinationMarker marker = new DestinationMarker(new WorldMapPointManager()
		{
			@Override
			public void add(WorldMapPoint point)
			{
				drawn.add(point);
			}

			@Override
			public void remove(WorldMapPoint point)
			{
				drawn.remove(point);
			}
		}, destinations, config, new ClientThread()
		{
			@Override
			public void invoke(Runnable action)
			{
				action.run();
			}
		}, new DestinationRoute(), null);
		RoutePress routes = new RoutePress(new OkHttpClient(), gson, game.client(), thread, new Pairing(store, gson),
			identity, new DestinationRoute(), destinations, marker);
		/* No choice is pressed here, so the settings it would write have nowhere to go. */
		SwingUtilities.invokeAndWait(() -> panel = new SlayerPanel(state, config, store, routes, destinations, marker, thread,
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
		/* A place that is not the open one is its name as a link, with the fold's arrow: see onePlaceIsOpenAtATime. */
		assertTrue(waiting.toString(), waiting.contains("▸ 3. Slayer Tower (roof)"));
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
				bind(ConfigStore.class).toInstance(store);
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

	/**
	 * The button beside a place's Route makes it this client's destination, as Stars' Pin destination
	 * does: Home's card, the arrow and a pin on the game's world map. So it says Pin, the act, and not
	 * World map, which on a friend's card draws only that friend's dot.
	 */
	@Test
	public void theButtonBesideRoutePinsThePlaceAsYourDestination() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		List<String> card = shown(panel);
		assertFalse(card.toString(), card.contains("World map"));
		AbstractButton pin = button(panel, "Pin");
		assertNotNull(card.toString(), pin);
		assertEquals("Pin this spot as your destination: world map, arrow and Home", pin.getToolTipText());

		press("Pin");
		assertTrue(destinations.hasLocalDestination());
		assertEquals("Home's card is the place", "Slayer Tower (basement)", destinations.getDestination().name);
		assertEquals("and the world map has its pin", 1, drawn.size());
	}

	/**
	 * A task with nothing left to kill folds to its row and the step that changes it, until the next
	 * task is read: a place and its Route are no use to it. A new count opens the card again.
	 */
	@Test
	public void aFinishedTaskFoldsToItsRowWithTheNextStep() throws Exception
	{
		CompanionSnapshot.Slayer finished = gargoyles();
		finished.remaining = 0;
		state.local(identity.id(), finished, "best", true);
		received(places("best"), 1);
		refresh();
		/* The heading's own arrow, which folds any task card, stands beside it; nothing else was added. */
		assertEquals(Arrays.asList("Slayer", "▾", "Gargoyles · Complete", SlayerPanel.NEXT_TASK), shown(panel));

		state.local(identity.id(), gargoyles(), "best", true);
		refresh();
		List<String> next = shown(panel);
		assertTrue(next.toString(), next.contains("Gargoyles · 143 left"));
		assertTrue(next.toString(), next.contains("1. Slayer Tower (basement)"));
	}

	/**
	 * Six places each with its facts and a Route and a Pin read as twelve buttons alike. One place is
	 * open at a time, Best for you to begin with: its kinds, what it asks and its two buttons. Every
	 * other is its name and labels, and pressing the name opens it in place of the one that was open.
	 */
	@Test
	public void onePlaceIsOpenAtATime() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		press("▸ All 3 places & options");
		List<String> list = shown(panel);
		assertEquals(list.toString(), 1, java.util.Collections.frequency(list, "Route"));
		assertEquals(list.toString(), 1, java.util.Collections.frequency(list, "✓ 75 Slayer"));
		assertTrue(list.toString(), list.indexOf("Route") < list.indexOf("▸ 2. Slayer Tower (top floor)"));
		assertTrue("a closed place keeps its labels: " + list, list.indexOf("Safespot · Aggressive") < list.lastIndexOf("Safespot · Aggressive"));

		press("▸ 3. Slayer Tower (roof)");
		List<String> roof = shown(panel);
		assertTrue(roof.toString(), roof.contains("3. Slayer Tower (roof)"));
		assertTrue("the place that was open closes: " + roof, roof.contains("▸ 1. Slayer Tower (basement)"));
		assertEquals(roof.toString(), 1, java.util.Collections.frequency(roof, "Route"));
		assertTrue(roof.toString(), roof.indexOf("Route") > roof.indexOf("3. Slayer Tower (roof)"));
		press("Pin");
		assertEquals("the buttons are the open place's", "Slayer Tower (roof)", destinations.getDestination().name);

		/* Folded back to one place, the list shows Best for you again, open: a closed row alone would offer no Route. */
		press("▾ Show less");
		List<String> closed = shown(panel);
		assertTrue(closed.toString(), closed.contains("1. Slayer Tower (basement)"));
		assertEquals(closed.toString(), 1, java.util.Collections.frequency(closed, "Route"));
	}

	/**
	 * A name too long for the card's one line breaks before its last two words, which are what tell
	 * one room of a cave from the next; left to wrap, one row ended "room" and its neighbour
	 * "eastern room". A name that fits is left whole.
	 */
	@Test
	public void aLongNameBreaksBeforeItsLastTwoWords()
	{
		JLabel label = new JLabel();
		java.awt.FontMetrics metrics = label.getFontMetrics(label.getFont().deriveFont(11f));
		String wrapped = SlayerPanel.named("▸ 2. Morytania Spider Cave eastern room and a great deal more of it", metrics);
		assertTrue(wrapped, wrapped.contains("more<br>of it"));
		assertFalse(SlayerPanel.named("1. Slayer Tower (roof)", metrics).contains("<br>"));
		assertTrue("still escaped", SlayerPanel.named("1. <b>x</b> y", metrics).contains("&lt;b&gt;"));
	}

	/**
	 * A place that still asks for something says so while it is closed, since that is why its name is
	 * grey; what a place asks and the player has waits for the open one.
	 */
	@Test
	public void aClosedPlaceStillSaysWhatItNeeds() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("/contract/slayer-blocked.json", "best"), 1);
		refresh();
		press("▸ All 3 places & options");
		List<String> list = shown(panel);
		assertTrue(list.toString(), list.contains("▸ 2. Slayer Tower (top floor)"));
		assertEquals("on the open place and both closed ones: " + list, 3, java.util.Collections.frequency(list, "Needs 75 Slayer"));
	}

	/**
	 * The heading's arrow folds the card to its heading and the task with its count, which is what a
	 * glance at a folded Slayer card is for, and the choice is kept for the next time the sidebar
	 * opens. Pressed again, the places are back.
	 */
	@Test
	public void theHeadingFoldsTheCardToItsTaskAndRemembersIt() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		press("▾");
		refresh();
		assertEquals(Arrays.asList("Slayer", "▸", "Gargoyles · 143 left"), shown(panel));
		assertTrue(store.on("slayerFolded"));

		press("▸");
		refresh();
		assertTrue(shown(panel).toString(), shown(panel).contains("Route"));
		assertFalse(store.on("slayerFolded"));
	}

	/**
	 * The closed card names its source and ends there. The licence is with the open list, after its
	 * options, where the website's Slayer block has it.
	 */
	@Test
	public void theClosedCardEndsOnItsSourceAndTheLicenceIsInTheFold() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		List<String> closed = shown(panel);
		assertEquals(closed.toString(), "OSRS Wiki ↗", closed.get(closed.size() - 1));
		assertFalse(closed.toString(), closed.contains("CC BY-NC-SA 3.0 ↗"));

		press("▸ All 3 places & options");
		List<String> open = shown(panel);
		assertTrue(open.toString(), open.indexOf("CC BY-NC-SA 3.0 ↗") > open.indexOf("Hide Wilderness"));
	}

	/**
	 * "The map could not be reached." ran on into "Only Slayer Tower counts for this task." as one
	 * grey paragraph. A Route that did not happen is said in the refusal pink, apart from the notes.
	 */
	@Test
	public void aRefusedRouteIsPinkApartFromTheCardsNotes() throws Exception
	{
		state.local(identity.id(), gargoyles(), "best", true);
		received(places("best"), 1);
		refresh();
		AbstractButton route = button(panel, "Route");
		String source = route.getName();
		SwingUtilities.invokeAndWait(route::doClick);
		refresh();
		JLabel said = line(panel, source);
		assertTrue(said.isVisible());
		assertEquals(MapCompanionPanel.REFUSED, said.getForeground());
	}

	/** The label a Route's answer is said in, named for the place as its button is. */
	private static JLabel line(Component component, String name)
	{
		if (component instanceof JLabel && name.equals(component.getName()))
		{
			return (JLabel) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JLabel found = line(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
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
		/* A place's name wraps, so its button's words are markup: read as a player reads them. */
		if (component instanceof AbstractButton && words.equals(plain(((AbstractButton) component).getText())))
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

	private static String plain(String text)
	{
		return text == null ? "" : text.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
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
			out.add(plain(text));
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
