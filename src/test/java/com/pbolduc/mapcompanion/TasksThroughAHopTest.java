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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.RuneScapeProfileChanged;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The Slayer and Port tasks cards through a world hop, with the game's boundaries raised through
 * the plugin's own handlers and the real readers, snapshot builder and cards.
 *
 * <p>For a moment after a hop every varbit reads zero, so an unsettled reader sends nothing; that
 * rule is the wire's, and holds here. What a hop must not do is empty the cards: the character
 * and its tasks are the same on the other world. A logout or another character still clears them.
 * What only a client can say, how long the game takes to settle, is in {@code docs/live-checks.md}.
 */
public class TasksThroughAHopTest
{
	private static final int GARGOYLES = 7;
	private static final int TERNS = 438;
	private static final int TERN_FEATHER = 32000;
	private static final String BOUNTY = "Port Sarim tern bounty";
	private static final String TASK = "Gargoyles · 143 left";
	private static final String EPOCH = "contract-relay-epoch";

	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Gson gson = new Gson();
	private final MapCompanionPlugin plugin = new MapCompanionPlugin();
	private final MemberIdentity identity = new MemberIdentity(store);
	private final QuestReader quests = new QuestReader(game.client());
	private final CargoHolds holds = new CargoHolds();
	private final PortTaskReader ports = new PortTaskReader(game.client(), holds);
	private final SlayerState slayer = new SlayerState();
	private final ClientThread thread = new ClientThread()
	{
		@Override
		public void invokeLater(Runnable action)
		{
			action.run();
		}
	};
	private final SnapshotBuilder builder;
	private SlayerPanel slayerCard;
	private PortTasksPanel portCard;
	private long now = 1_000_000L;

	public TasksThroughAHopTest() throws Exception
	{
		config.set("shareSlayer", true);
		config.set("sharePortTasks", true);
		config.set("shareBoats", true);
		game.login("Example", 444, 3222, 3218, 0);
		game.setDbValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, GARGOYLES, 9007,
			DBTableID.SlayerTask.COL_NAME_UPPERCASE, "Gargoyles");
		game.setDbValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 5, 9100,
			DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, "Slayer Tower");
		/* The tern bounty as the game's table has it, its row found by the slot's value. */
		game.setDbValue(DBTableID.PortTask.ID, DBTableID.PortTask.COL_TASK_ID, TERNS, 9001, DBTableID.PortTask.COL_TASK_TYPE, PortTaskReader.TYPE_BOUNTY);
		game.setDbValue(DBTableID.PortTask.ID, DBTableID.PortTask.COL_TASK_ID, TERNS, 9001, DBTableID.PortTask.COL_NAME, BOUNTY);
		game.setDbValue(DBTableID.PortTask.ID, DBTableID.PortTask.COL_TASK_ID, TERNS, 9001, DBTableID.PortTask.COL_BOUNTY_OBJECT, TERN_FEATHER);
		game.setDbValue(DBTableID.PortTask.ID, DBTableID.PortTask.COL_TASK_ID, TERNS, 9001, DBTableID.PortTask.COL_BOUNTY_OBJECT_AMOUNT, 15);
		game.nameItem(TERN_FEATHER, "Tern feather");
		holdTasks();

		builder = Guice.createInjector(new AbstractModule()
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
				bind(CargoHolds.class).toInstance(holds);
				bind(PortTaskReader.class).toInstance(ports);
				bind(SlayerState.class).toInstance(slayer);
			}
		}).getInstance(SnapshotBuilder.class);

		/* The plugin's game-state and profile handlers, as CompletionAnnouncementTest assembles them. */
		RewardTestClient definitions = new RewardTestClient();
		CollectionItemCatalog catalog = definitions.catalog();
		plugin.milestones = new MilestoneOutbox();
		plugin.pairing = new Pairing(store, gson);
		plugin.destinationState = new DestinationState(gson);
		plugin.destinationMarker = new TestDestinationMarker();
		plugin.groupMarkers = new GroupMarkers(null, null);
		plugin.boatPins = new BoatPins(null, null);
		plugin.rewardObserver = new RewardObserver(definitions.client, catalog, catalog::contains, () -> 1000L);
		TestFarmRuns.attach(plugin);
		set("slayerState", slayer);
		set("client", game.client());
		set("config", config);
		set("settings", store);
		set("questReader", quests);
		set("diaryReader", construct(DiaryReader.class));
		set("xpTracker", new XpTracker());
		set("clueReader", new ClueReader(game.client()));
		set("farmingObserver", new FarmingObserver(game.client(), store, gson));
		set("travelUnlockReader", new TravelUnlockReader(game.client()));
		set("stashReader", new StashReader(game.client()));
		set("boatReader", new BoatReader(game.client()));
		set("cargoHolds", holds);
		set("portTaskReader", ports);
		set("birdhouseReader", new BirdhouseReader(key -> null));
		set("assignmentReader", new AssignmentReader(game.client(), store));
		set("deathWatcher", construct(DeathWatcher.class));
		set("lootLog", new LootLog(game.client(), store, gson));
		set("directiveStream", new DirectiveStream(new OkHttpClient(), gson, (action, delay) -> null));
		set("groupState", new GroupState(plugin.destinationState));

		DestinationState destinations = new DestinationState(gson);
		DestinationRoute route = new DestinationRoute();
		TestDestinationMarker marker = new TestDestinationMarker();
		Pairing pairing = new Pairing(store, gson);
		FarmRunState runs = new FarmRunState();
		FarmRunClient press = new FarmRunClient(new OkHttpClient(), gson, game.client(), thread, pairing, identity, runs,
			destinations, route);
		RoutePress routes = new RoutePress(new OkHttpClient(), gson, game.client(), thread, pairing, identity, route,
			destinations, marker);
		GameSprites sprites = new GameSprites(null, null, null);
		SwingUtilities.invokeAndWait(() ->
		{
			/* No choice is pressed here; the store is where the Slayer field is read. */
			slayerCard = new SlayerPanel(slayer, config, store, routes, destinations, marker, thread, sprites);
			portCard = new PortTasksPanel(ports, config, store, sprites, runs, press, new FarmRunRoute(runs, destinations, route, thread),
				route, new ShortestPath(message -> { }));
		});
	}

	/**
	 * Through a hop's unsettled window the cards keep the last settled reading, the Slayer card its
	 * places too, while nothing about either task is sent; the map's answer to that silence, a
	 * removal, does not empty the card either. What is kept is shown, not acted on: Route and Start
	 * run wait, greyed, for the next settled reading, which takes over.
	 */
	@Test
	public void aHopKeepsBothCardsWhileNothingIsSent() throws Exception
	{
		settledWithPlaces();

		hop();
		CompanionSnapshot unsettled = tick();
		assertNull("an unsettled reader sends no task", unsettled.slayer);
		assertNull(unsettled.portTasks);
		answer(2, null);
		List<String> slayerWords = words(slayerCard);
		assertTrue(slayerWords.toString(), slayerWords.contains(TASK));
		assertTrue(slayerWords.toString(), slayerWords.contains("1. Slayer Tower (basement)"));
		assertFalse(button(slayerCard, "Route").isEnabled());
		assertTrue(portCard.isVisible());
		assertTrue(words(portCard).toString(), words(portCard).contains(BOUNTY));
		assertFalse(button(portCard, "Start run").isEnabled());

		now += 15_000L;
		holdTasks();
		CompanionSnapshot settled = tick();
		assertEquals("Gargoyles", settled.slayer.taskName);
		assertEquals(PortTaskReader.BOUNTY, settled.portTasks.slots.get(0).state);
		assertTrue(words(slayerCard).contains(TASK));
		assertTrue(words(portCard).contains(BOUNTY));
		assertTrue(button(portCard, "Start run").isEnabled());
	}

	/** A logout ends the session: whatever a hop kept goes, and the next login waits for its own reading. */
	@Test
	public void aLogoutClearsWhatAHopKept() throws Exception
	{
		settledWithPlaces();
		hop();
		plugin.onGameStateChanged(event(GameState.LOGIN_SCREEN));
		game.login("Example", 444, 3222, 3218, 0);
		tick();
		assertFalse(words(slayerCard).toString(), words(slayerCard).contains(TASK));
		assertFalse(portCard.isVisible());
	}

	/**
	 * At the login screen the Slayer card keeps its task and places, and Route waits for a character,
	 * greyed and saying so, where it answered a press with "Connect to your map to plan a route.";
	 * Pin, which acts here, stays. A hop keeps the character, as it keeps the cards.
	 */
	@Test
	public void aLogoutGreysRouteAndAHopKeepsIt() throws Exception
	{
		settledWithPlaces();
		hop();
		assertTrue("a hop keeps the character", plugin.loggedIn());
		plugin.onGameStateChanged(event(GameState.LOGIN_SCREEN));
		assertFalse(plugin.loggedIn());
		SwingUtilities.invokeAndWait(() -> slayerCard.refresh(true, plugin.loggedIn()));
		assertTrue(words(slayerCard).toString(), words(slayerCard).contains(TASK));
		AbstractButton route = button(slayerCard, "Route");
		assertFalse(route.isEnabled());
		assertEquals(RoutePress.LOG_IN, route.getToolTipText());
		assertTrue(button(slayerCard, "Pin").isEnabled());
		assertTrue("logging in, the character is not there until the game says so",
			MapCompanionPlugin.loggedOutAfter(true, GameState.LOADING));
		assertFalse(MapCompanionPlugin.loggedOutAfter(true, GameState.LOGGED_IN));
	}

	/** Another character clears everything: the first one's tasks never reach the second. */
	@Test
	public void anotherCharacterClearsWhatAHopKept() throws Exception
	{
		settledWithPlaces();
		hop();
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged(null, "a"));
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("a", "b"));
		tick();
		assertFalse(words(slayerCard).toString(), words(slayerCard).contains(TASK));
		assertFalse(portCard.isVisible());
	}

	/** Logged in and settled, with the task, the bounty and the map's places for the task. */
	private void settledWithPlaces() throws Exception
	{
		quests.onSessionResumed(now);
		ports.onSessionResumed(now);
		now += 15_000L;
		CompanionSnapshot first = tick();
		assertEquals("Gargoyles", first.slayer.taskName);
		answer(1, places());
		assertTrue(words(slayerCard).contains(TASK));
		assertTrue(button(slayerCard, "Route").isEnabled());
		assertTrue(words(portCard).contains(BOUNTY));
	}

	/** A hop as the client raises it, then the new world's first ticks, where every varbit reads zero. */
	private void hop()
	{
		game.logout();
		plugin.onGameStateChanged(event(GameState.HOPPING));
		game.login("Example", 302, 3222, 3218, 0);
		game.setVarp(VarPlayerIds.SLAYER_TARGET, 0);
		game.setVarp(VarPlayerIds.SLAYER_COUNT, 0);
		game.setVarp(VarPlayerIds.SLAYER_AREA, 0);
		game.setVarbit(VarbitID.PORT_TASK_SLOT_0_ID, 0);
		game.setVarbit(VarbitID.BOUNTY_TASK_ITEM_COUNTER_1, 0);
		now += 1_000L;
	}

	/** The game's own values for the task and the bounty, as a settled session reads them. */
	private void holdTasks()
	{
		game.setVarp(VarPlayerIds.SLAYER_TARGET, GARGOYLES);
		game.setVarp(VarPlayerIds.SLAYER_COUNT, 143);
		game.setVarp(VarPlayerIds.SLAYER_AREA, 5);
		game.setVarbit(VarbitID.PORT_TASK_SLOT_0_ID, TERNS);
		game.setVarbit(VarbitID.BOUNTY_TASK_ITEM_COUNTER_1, 9);
	}

	/**
	 * What a tick does for these cards, in the plugin's order: the settling clocks told the client is
	 * up, the slots read, then a snapshot built for the post. The cards refresh as the sidebar does.
	 */
	private CompanionSnapshot tick() throws Exception
	{
		quests.onSessionResumed(now);
		ports.onSessionResumed(now);
		ports.read(now);
		CompanionSnapshot snapshot = builder.build(now);
		refresh();
		return snapshot;
	}

	/** The map's reply for the Slayer list: places, or a removal when there are none. */
	private void answer(long revision, RelayResponse.SlayerOptions options) throws Exception
	{
		RelayResponse.Directive directive = new RelayResponse.Directive();
		directive.relayEpoch = EPOCH;
		directive.slayerOptions = options;
		directive.slayerRevision = revision;
		slayer.accept(identity.id(), directive, System.currentTimeMillis());
		refresh();
	}

	private RelayResponse.SlayerOptions places() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/slayer-options.json").readAllBytes(), StandardCharsets.UTF_8);
		return SlayerWire.normalize(gson.fromJson(json, RelayResponse.SlayerOptions.class));
	}

	private void refresh() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			slayerCard.refresh(true);
			portCard.refresh(true);
		});
	}

	private GameStateChanged event(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private static AbstractButton button(Component component, String text)
	{
		if (!component.isVisible())
		{
			return null;
		}
		if (component instanceof AbstractButton && text.equals(((AbstractButton) component).getText()))
		{
			return (AbstractButton) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				AbstractButton found = button(child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The words on screen, in order: every label and button shown, inside parents that are shown. */
	private static List<String> words(Component root)
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

	private <T> T construct(Class<T> type) throws Exception
	{
		Constructor<T> make = type.getDeclaredConstructor(Client.class);
		make.setAccessible(true);
		return make.newInstance(game.client());
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = MapCompanionPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
