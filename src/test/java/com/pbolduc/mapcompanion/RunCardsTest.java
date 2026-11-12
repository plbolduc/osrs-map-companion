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
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The two cards that start a run, Farm run and Port tasks, read off their real components as a
 * player sees them, over the one run the map keeps a member.
 *
 * <p>Presses go through the cards' own buttons, {@link FarmRunClient} and {@link FarmRunState}. The
 * map's reply is made here and handed back inside the press, so nothing leaves the machine and a
 * press has landed by the time it returns.
 */
public class RunCardsTest
{
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final String EPOCH = "contract-relay-epoch";
	private static final String HONEY = "Port Sarim honey delivery";
	private static final String NO_BOAT = "The map doesn't know where your boat is. Turn on Boats and moorings, then log in.";

	private final Gson gson = new Gson();
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, gson);
	private final MemberIdentity identity = new MemberIdentity(store);
	private final FarmRunState state = new FarmRunState();
	private final DestinationState destinations = new DestinationState(gson);
	private final DestinationRoute route = new DestinationRoute();
	private final ClientThread thread = new ClientThread()
	{
		@Override
		public void invokeLater(Runnable action)
		{
			action.run();
		}
	};
	/** The map's answer to the next press. */
	private int status = 200;
	private String body = "{}";
	/** Every press that reached the map, and the body of the last. */
	private int asked;
	private String sent;
	private final FarmRunClient client = new FarmRunClient(answering(), gson, game.client(), thread, pairing, identity,
		state, destinations, route);
	private final FarmRunRoute follower = new FarmRunRoute(state, destinations, route, thread);
	private final ShortestPath shortest = new ShortestPath(message -> { });
	private final GameSprites sprites = new GameSprites(null, null, null);
	/** A courier and a bounty held, as the reader would have them once the session settled. */
	private final List<PortTaskReader.Slot> slots = Arrays.asList(
		new PortTaskReader.Slot(0, PortTaskReader.COURIER, 6, HONEY, "Catherby", "Port Sarim", 3, 0, 0, 0, null),
		new PortTaskReader.Slot(1, PortTaskReader.BOUNTY, 438, "Port Sarim tern bounty", null, "Port Sarim", 15, 0, 0, 9, "Tern feather"),
		PortTaskReader.Slot.plain(2, PortTaskReader.EMPTY), PortTaskReader.Slot.plain(3, PortTaskReader.EMPTY),
		PortTaskReader.Slot.plain(4, PortTaskReader.LOCKED));
	private final PortTaskReader tasks = new PortTaskReader(game.client(), new CargoHolds())
	{
		@Override
		List<Slot> latest()
		{
			return slots;
		}

		@Override
		Integer latestHold()
		{
			return 30;
		}
	};
	private PortTasksPanel ports;
	private FarmRunPanel farm;

	public RunCardsTest() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		config.set("sharePortTasks", true);
		config.set("shareBoats", true);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		state.bind(identity.id());
		SwingUtilities.invokeAndWait(() ->
		{
			ports = new PortTasksPanel(tasks, config, store, sprites, state, client, follower, route, shortest);
			farm = new FarmRunPanel(state, client, follower, route, shortest, sprites, config, store);
		});
	}

	/**
	 * Either card's Start replaces the run held, so a farm run being walked holds back Port tasks'
	 * Start run, and a line where it was says why. The tasks still show; Start comes back when the
	 * farm run ends.
	 */
	@Test
	public void aFarmRunBeingWalkedHoldsBackPortTasksStartRun() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		refresh();
		List<String> waiting = shown(ports);
		assertTrue(waiting.toString(), waiting.contains(HONEY));
		assertFalse(waiting.toString(), waiting.contains("Start run"));
		assertTrue(waiting.toString(), waiting.contains(PortTasksPanel.AFTER_FARM_RUN));

		status = 204;
		press(farm, "End run");
		refresh();
		List<String> free = shown(ports);
		assertTrue(free.toString(), free.contains("Start run"));
		assertFalse(free.toString(), free.contains(PortTasksPanel.AFTER_FARM_RUN));
	}

	/**
	 * And a port-task run being walked holds back the farm card's kind of run and Start run, with one
	 * line in their place. A finished run has nothing left to lose, so the picker comes back with it.
	 */
	@Test
	public void aPortRunBeingWalkedHoldsBackTheFarmPicker() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, portRun(), 1, System.currentTimeMillis());
		refresh();
		List<String> waiting = shown(farm);
		assertFalse(waiting.toString(), waiting.contains("Start run"));
		assertTrue(waiting.toString(), waiting.contains(FarmRunPanel.AFTER_PORT_RUN));

		/* The fixture's slots, every stop's counters reached, then every slot let go. */
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 0), courier(2, 121, 0, 0), bounty(3, 466, 9), empty(4)));
		state.observePorts(Arrays.asList(empty(0), courier(1, 84, 3, 3), courier(2, 121, 3, 3), bounty(3, 466, 0), empty(4)));
		state.observePorts(Arrays.asList(empty(0), empty(1), empty(2), empty(3), empty(4)));
		refresh();
		// "Route finished." until the card said what the run came to.
		assertTrue(shown(ports).toString(), shown(ports).contains("Run finished · 4 done"));
		List<String> free = shown(farm);
		assertTrue(free.toString(), free.contains("Start run"));
		assertFalse(free.toString(), free.contains(FarmRunPanel.AFTER_PORT_RUN));
	}

	/**
	 * End run on a port-task run leaves "Run ended." under the card, and that sentence is not a run.
	 * The card used to take any sentence for one, so the task lines and Start run stayed away for the
	 * rest of the session: the next run could not be started from the card that offers it.
	 */
	@Test
	public void anEndedPortRunGivesBackTheTasksAndStartRun() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, portRun(), 1, System.currentTimeMillis());
		refresh();
		List<String> walked = shown(ports);
		assertFalse("while a run is held its stops say the tasks: " + walked, walked.contains(HONEY));
		assertFalse("and it is the order: no second Start beside it", walked.contains("Start run"));

		status = 204;
		press(ports, "End run");
		refresh();
		List<String> ended = shown(ports);
		assertTrue(ended.toString(), ended.contains(HONEY));
		assertTrue(ended.toString(), ended.contains("Start run"));
		assertTrue("the run's last word stays, under the button that can start another: " + ended,
			ended.indexOf("Run ended.") > ended.indexOf("Start run"));
	}

	/**
	 * A refusal under a run's card was the grey of its stops' notes, so "The map doesn't know where
	 * your boat is." read as one more line of the card. It is in the refusal pink; "Run ended." keeps
	 * the line's grey, since that press did what it asked.
	 */
	@Test
	public void aRefusalIsSaidInPinkAndRunEndedInGrey() throws Exception
	{
		refresh();
		status = 409;
		body = "{\"error\":\"" + NO_BOAT + "\"}";
		press(ports, "Start run");
		refresh();
		assertEquals(MapCompanionPanel.REFUSED, label(ports, NO_BOAT).getForeground());

		state.acceptFor(identity.id(), EPOCH, portRun(), 1, System.currentTimeMillis());
		refresh();
		status = 204;
		press(ports, "End run");
		refresh();
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label(ports, "Run ended.").getForeground());
	}

	/**
	 * A busy run said "Try the run again shortly." while every Route and Start was held for half a
	 * minute. It says the wait the routes share, counted down as the card is drawn.
	 */
	@Test
	public void aBusyRunSaysTheSharedWaitAndCountsItDown() throws Exception
	{
		refresh();
		choose("Herb run");
		status = 429;
		body = "{\"reason\":\"busy\"}";
		press(farm, "Start run");
		long now = System.currentTimeMillis();
		long opens = now + route.pressWait(now);
		RunView card = runView(farm);
		SwingUtilities.invokeAndWait(() -> card.refresh(true, opens - 25_000L));
		assertTrue(shown(farm).toString(), shown(farm).contains("The map is busy. Try again in 25s."));
		SwingUtilities.invokeAndWait(() -> card.refresh(true, opens));
		assertTrue(shown(farm).toString(), shown(farm).contains("The map was busy."));
	}

	/**
	 * At the login screen a run's card offers nothing that asks the map as a member: the kind of run and
	 * Start run wait, and a run's Reroute, while End run, which ends the run here, stays.
	 */
	@Test
	public void atTheLoginScreenARunWaitsButEndRunStays() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> farm.refresh(true, false));
		assertTrue(button(farm, "Start run") != null && !enabled(farm, "Start run"));
		assertFalse(combo(farm).isEnabled());

		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		SwingUtilities.invokeAndWait(() -> farm.refresh(true, false));
		assertTrue(button(farm, "Reroute") != null && !enabled(farm, "Reroute"));
		assertTrue(enabled(farm, "End run"));
	}

	/** A refused Start is no run either: the tasks stay, and so does the button that can try again. */
	@Test
	public void aRefusedStartKeepsTheTasksAndStartRun() throws Exception
	{
		refresh();
		status = 409;
		body = "{\"error\":\"" + NO_BOAT + "\"}";
		press(ports, "Start run");
		refresh();
		List<String> refused = shown(ports);
		assertTrue(refused.toString(), refused.contains(NO_BOAT));
		assertTrue(refused.toString(), refused.contains(HONEY));
		assertTrue(refused.toString(), refused.contains("Start run"));
	}

	/**
	 * Ready now plans from the patches this client shares, and a contract from the contract. With that
	 * choice off the map could only refuse, so the card says which choice to turn on, Start run waits,
	 * and nothing is sent. A preset is planned from the map's own tables and needs neither.
	 */
	@Test
	public void aRunPlannedFromASharingChoiceThatIsOffIsNotSent() throws Exception
	{
		refresh();
		List<String> readyNow = shown(farm);
		assertTrue(readyNow.toString(), readyNow.contains(FarmRunPanel.NEEDS_PATCHES));
		assertFalse("Start run waits for the choice", enabled(farm, "Start run"));
		click(farm, "Start run");
		assertEquals("nothing reaches the map", 0, asked);

		choose("Herb run");
		assertFalse(shown(farm).contains(FarmRunPanel.NEEDS_PATCHES));
		assertTrue("a preset needs no reading", enabled(farm, "Start run"));

		choose("Farming contract");
		assertTrue(shown(farm).contains(FarmRunPanel.NEEDS_CONTRACT));
		assertFalse("a new pick is checked at once, not at the next refresh", enabled(farm, "Start run"));
		config.set("shareFarmingContracts", true);
		refresh();
		assertTrue(enabled(farm, "Start run"));

		config.set("shareFarming", true);
		choose("Ready now");
		assertTrue(enabled(farm, "Start run"));
		assertFalse(shown(farm).contains(FarmRunPanel.NEEDS_PATCHES));
		assertEquals(0, asked);
	}

	/**
	 * The dailies run is planned from the account the map already reads, so it needs no sharing
	 * choice: with Dailies collected and every farming choice off, Start run is pressable and asks
	 * the map for the dailies run by its wire name.
	 */
	@Test
	public void theDailiesRunNeedsNoSharingChoiceAndAsksForItself() throws Exception
	{
		refresh();
		choose("Dailies run");
		assertFalse(shown(farm).contains(FarmRunPanel.NEEDS_PATCHES));
		assertTrue("nothing needs turning on", enabled(farm, "Start run"));
		click(farm, "Start run");
		assertEquals(1, asked);
		assertEquals("dailies", gson.fromJson(sent, JsonObject.class).get("start").getAsString());
	}

	/**
	 * The sea charting run is planned from Sea charts charted, which says what is left and ticks each
	 * stop, so with it off the card says which group to turn on and Start waits; on, Start asks the
	 * map for the run by its wire name.
	 */
	@Test
	public void theSeaChartingRunWaitsForSeaChartsAndAsksForItself() throws Exception
	{
		refresh();
		choose("Sea charting run");
		assertTrue(shown(farm).contains(FarmRunPanel.NEEDS_SEA_CHARTS));
		assertFalse("Start run waits for the choice", enabled(farm, "Start run"));
		click(farm, "Start run");
		assertEquals("nothing reaches the map", 0, asked);

		config.set("shareSeaCharts", true);
		refresh();
		assertFalse(shown(farm).contains(FarmRunPanel.NEEDS_SEA_CHARTS));
		assertTrue(enabled(farm, "Start run"));
		click(farm, "Start run");
		assertEquals(1, asked);
		assertEquals(FarmRunWire.SEA_CHARTS, gson.fromJson(sent, JsonObject.class).get("start").getAsString());
	}

	/**
	 * Pausing changed one word, on the button, and a search pick or a friend's destination paused the
	 * run with no word on its card at all. The count says the run is paused now, and while the
	 * destination that paused it is shown, the card names it and the way back.
	 *
	 * <p>The count ends with how far is left now, as a charting run's does: the two stops ahead are
	 * 26 and 29 tiles, so the lines this pinned read "· 55 tiles left" after the words they had.
	 */
	@Test
	public void aPausedRunSaysSoAndNamesTheRouteThatPausedIt() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		follower.sync();
		refresh();
		assertTrue(shown(farm).toString(), shown(farm).contains("1 of 3 tracked done · 55 tiles left"));

		press(farm, "Pause");
		refresh();
		List<String> paused = shown(farm);
		assertTrue(paused.toString(), paused.contains("Paused · 1 of 3 tracked done · 55 tiles left"));
		assertFalse("the player paused it: nothing else to name", paused.contains(RunView.pausedLine("1. Falador farm")));

		press(farm, "Follow");
		follower.sync();
		destinations.selectLocal(bank());
		follower.sync();
		refresh();
		List<String> detour = shown(farm);
		assertTrue(detour.toString(), detour.contains("Paused · 1 of 3 tracked done · 55 tiles left"));
		assertTrue(detour.toString(), detour.contains(RunView.pausedLine("Lumbridge Castle bank")));

		destinations.dismiss();
		refresh();
		List<String> dismissed = shown(farm);
		assertTrue("still paused, as the README says", dismissed.contains("Paused · 1 of 3 tracked done · 55 tiles left"));
		assertFalse("but no longer for a destination gone from the screen",
			dismissed.contains(RunView.pausedLine("Lumbridge Castle bank")));
	}

	/**
	 * A finished run said only "Route finished." in grey, and kept Pause and Reroute greyed beside End
	 * run. It says what it came to, done and visited apart, and leaves End run its one press.
	 */
	@Test
	public void aFinishedRunSaysWhatItCameToAndOffersOnlyEndRun() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		replant("falador-herb");
		replant("catherby-herb");
		refresh();
		List<String> finished = shown(farm);
		assertTrue(finished.toString(), finished.contains("Run finished · 3 done, 1 visited"));
		assertTrue(finished.toString(), finished.contains("End run"));
		assertFalse(finished.toString(), finished.contains("Pause") || finished.contains("Follow") || finished.contains("Reroute"));
		assertTrue(enabled(farm, "End run"));
	}

	/**
	 * A run that lands is answered by its stops: "Run ready." stayed under them for the run's whole
	 * life. "Run ended." is news for ten seconds, then gives the card's row back.
	 */
	@Test
	public void aConfirmationIsSaidOnlyWhileItIsNews() throws Exception
	{
		refresh();
		choose("Herb run");
		RelayResponse.Directive reply = new RelayResponse.Directive();
		reply.relayEpoch = EPOCH;
		reply.run = run("/contract/farm-run.json");
		reply.runRevision = 1;
		body = gson.toJson(reply);
		press(farm, "Start run");
		refresh();
		List<String> ready = shown(farm);
		assertTrue(ready.toString(), ready.contains("1. Falador farm"));
		assertFalse(ready.toString(), ready.contains("Run ready."));

		status = 204;
		press(farm, "End run");
		refresh();
		assertTrue(shown(farm).toString(), shown(farm).contains("Run ended."));
		RunView card = runView(farm);
		SwingUtilities.invokeAndWait(() -> card.refresh(true, System.currentTimeMillis() + FarmRunClient.Said.CONFIRMATION_MS));
		assertFalse(shown(farm).toString(), shown(farm).contains("Run ended."));
	}

	/**
	 * Mid-detour the Reroute key rerouted the paused run and the Follow key took the run back, throwing
	 * away the route being walked. While another route is followed the run's card leaves the keys, and
	 * the step box's menu, to the destination card; a destination merely shown, not followed, leaves
	 * them with the run.
	 */
	@Test
	public void duringAFollowedDetourTheKeysLeaveThePausedRunAlone() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		follower.sync();
		refresh();
		assertEquals("Pause", act(MapCompanionPanel.Act.FOLLOW).getText());
		assertEquals("Reroute", act(MapCompanionPanel.Act.REROUTE).getText());
		assertEquals("the box's menu ends the run, as the card does", "End run", act(MapCompanionPanel.Act.END).getText());

		RelayResponse.Destination bank = DestinationRouteTest.destination("Lumbridge Castle bank",
			DestinationRouteTest.route("Lumbridge Home Teleport", DestinationRouteTest.alKharid()));
		destinations.selectLocal(bank);
		route.adoptPressed(bank);
		follower.sync();
		refresh();
		assertTrue("a Route press follows what it planned", route.view().following);
		assertNull(act(MapCompanionPanel.Act.FOLLOW));
		assertNull(act(MapCompanionPanel.Act.REROUTE));
		assertNull("ending the run is not the detour's to do", act(MapCompanionPanel.Act.END));

		route.follow(false);
		refresh();
		assertEquals("Follow", act(MapCompanionPanel.Act.FOLLOW).getText());
	}

	/**
	 * The farm card had seven pixels more of its own colour above and below than the task cards beside
	 * it, and bare words where theirs carry their skill's icon, so the three read as one block.
	 */
	@Test
	public void theFarmCardHasTheTaskCardsInsetAndItsSkillsIcon() throws Exception
	{
		List<String> skills = new ArrayList<>();
		GameSprites recording = new GameSprites(null, null, null)
		{
			@Override
			void skill(JLabel label, String name, int box)
			{
				skills.add(name + " " + box);
			}
		};
		Insets[] insets = new Insets[2];
		SwingUtilities.invokeAndWait(() ->
		{
			insets[0] = new FarmRunPanel(state, client, follower, route, shortest, recording, config, store).getInsets();
			insets[1] = new PortTasksPanel(tasks, config, store, recording, state, client, follower, route, shortest).getInsets();
		});
		assertEquals(insets[1], insets[0]);
		assertTrue(skills.toString(), skills.contains("Farming 16"));
	}

	/**
	 * The leg's steps came after the last stop, two stops below the one they lead to, and a stop behind
	 * the player kept every line. The leg sits under its own stop and moves on with it; a finished
	 * stop is one line that keeps its status word.
	 */
	@Test
	public void theLegSitsUnderItsStopAndAStopBehindIsOneLine() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		follower.sync();
		refresh();
		List<String> first = shown(farm);
		/* The heading carries its leg's length, so it is found by its whole words. */
		int leg = first.indexOf("This leg · 26 tiles");
		assertTrue(first.toString(), leg > first.indexOf("1. Falador farm") && leg < first.indexOf("2. Catherby"));
		assertTrue(first.toString(), first.contains("4. My bank · Visited"));
		assertFalse("a visited stop's way there is spent", first.contains("Draynor Manor Teleport"));

		replant("falador-herb");
		follower.sync();
		refresh();
		List<String> second = shown(farm);
		assertTrue(second.toString(), second.contains("1. Falador farm · Done"));
		assertFalse("a done stop's way there is spent", second.contains("Explorer's ring 2: Teleport"));
		int moved = second.indexOf("This leg · 29 tiles");
		assertTrue(second.toString(), moved > second.indexOf("2. Catherby") && moved < second.indexOf("4. My bank · Visited"));
	}

	/**
	 * Under This leg, the leg's way line said the way the stop's own line just above it says,
	 * "Explorer's ring 2: Teleport" twice in four lines. The stop says it once, and the leg's length
	 * is in the heading. A voyage's leg has no length there: its Sail step says how far it sails.
	 */
	@Test
	public void thisLegSaysItsLengthAndTheStopSaysItsWay() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		follower.sync();
		refresh();
		List<String> words = shown(farm);
		assertTrue(words.toString(), words.contains("This leg · 26 tiles"));
		assertEquals(words.toString(), 1, Collections.frequency(words, "Explorer's ring 2: Teleport"));
		assertFalse(words.toString(), words.contains("Explorer's ring 2: Teleport · 26 tiles"));
		assertEquals("This leg", RunView.legHeading(null));
	}

	/**
	 * A farm stop said how far its way was only while it was the leg being walked, in This leg's
	 * heading, so the stops ahead could not be compared. A stop still ahead that the map priced has
	 * its length at the right edge of its name's row, in the column a charting run's lengths make,
	 * with no word under it since its way is the line below; and the count under the bar says how far
	 * is left. A stop behind the player keeps its one struck line.
	 */
	@Test
	public void aFarmStopAheadSaysItsLengthAtTheRightAndTheCountWhatIsLeft() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		refresh();
		List<String> words = shown(farm);
		assertTrue(words.toString(), words.contains("1 of 3 tracked done · 55 tiles left"));
		int falador = words.indexOf("1. Falador farm");
		assertEquals("the length follows the name it is beside: " + words, "26t", words.get(falador + 1));
		assertEquals("and the stop's way is still the line under it", "Explorer's ring 2: Teleport", words.get(falador + 2));
		assertEquals("29t", words.get(words.indexOf("2. Catherby") + 1));
		assertFalse("a stop behind the player says no length: " + words, words.contains("140t") || words.contains("128t"));
		assertFalse("no word under a farm stop's length", raw(farm, "26t").contains("<br>"));
		SwingUtilities.invokeAndWait(() ->
		{
			JLabel length = label(farm, "26t");
			JLabel name = label(farm, "1. Falador farm");
			assertSame("on the name's row", name.getParent(), length.getParent());
			assertEquals("at its right edge", BorderLayout.EAST,
				((BorderLayout) length.getParent().getLayout()).getConstraints(length));
			assertEquals("on its first line", JLabel.TOP, length.getVerticalAlignment());
			assertTrue("the name gives up the length's width, so a long one wraps beside it and is not cut: " + name.getText(),
				name.getText().contains("width='" + (MapCompanionPanel.CARD_TEXT_WIDTH - length.getPreferredSize().width - 4) + "'"));
			assertTrue("a stop with no length keeps the whole width",
				label(farm, "4. My bank · Visited").getText().contains("width='" + MapCompanionPanel.CARD_TEXT_WIDTH + "'"));
			assertEquals("in the grey a charting run's length has", ColorScheme.LIGHT_GRAY_COLOR, length.getForeground());
		});

		replant("falador-herb");
		refresh();
		words = shown(farm);
		assertTrue(words.toString(), words.contains("2 of 3 tracked done · 29 tiles left"));
		assertFalse("a done stop's length is spent with its way: " + words, words.contains("26t"));
	}

	/**
	 * A stop the map priced nothing for has no length, and then there is no total to give: one stop
	 * in words alone would make "tiles left" a part of the way called the whole. A voyage's stop has
	 * none either, since its Sail step says the distance sailed, and no number is too long for the row.
	 */
	@Test
	public void aFarmStopTheMapDidNotPriceHasNoLengthAndTheCountNoTotal() throws Exception
	{
		RelayResponse.FarmRun herbs = run("/contract/farm-run.json");
		herbs.stops.get(1).costTiles = 0;
		state.acceptFor(identity.id(), EPOCH, herbs, 1, System.currentTimeMillis());
		refresh();
		List<String> words = shown(farm);
		assertTrue(words.toString(), words.contains("1 of 3 tracked done"));
		assertEquals("the priced stop still says its own", "26t", words.get(words.indexOf("1. Falador farm") + 1));
		assertEquals("the other is as it was", "Catherby tablet", words.get(words.indexOf("2. Catherby") + 1));

		RelayResponse.FarmStop stop = new RelayResponse.FarmStop();
		assertEquals(-1, RunView.tiles(stop));
		stop.costTiles = 29;
		assertEquals(29, RunView.tiles(stop));
		stop.costTiles = 100_001;
		assertEquals("bounded as a leg's length is", -1, RunView.tiles(stop));
		stop.costTiles = 29;
		stop.voyage = new RelayResponse.Voyage();
		assertEquals("a voyage's stop is priced by its Sail step", -1, RunView.tiles(stop));
		stop.leg = new RelayResponse.FarmLeg();
		stop.leg.tiles = 0;
		assertEquals("a leg in numbers is the length, nought included", 0, RunView.tiles(stop));
	}

	/**
	 * The next stop had a separate amber "Next" line, and a visited stop kept a white name while the
	 * world map faded its pin with the done ones. The next stop's number is amber, as its pin is, and
	 * a stop behind the player is struck and grey whether done or visited; its word tells them apart.
	 */
	@Test
	public void theNextStopIsItsAmberNumberAndAStopBehindIsStruck() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		refresh();
		assertFalse(shown(farm).toString(), shown(farm).contains("Next"));
		String next = raw(farm, "1. Falador farm");
		assertTrue(next, next.contains("<b><font color='#f59e0b'>1.</font></b> Falador farm"));
		assertFalse("only the next stop's number", raw(farm, "2. Catherby").contains("#f59e0b"));
		assertTrue(raw(farm, "4. My bank · Visited").contains("<strike>4. My bank</strike>"));
	}

	/**
	 * A stop's picture came from the item it names alone, so a spell such as Draynor Manor Teleport
	 * had none though This leg drew one for the same step, and a stop with no picture lost its place,
	 * moving its way twenty pixels left of the others'. The picture is the one This leg draws, and
	 * every stop of a run keeps a picture's place when any has one; a run with none keeps none.
	 */
	@Test
	public void eachStopShowsTheTeleportThisLegDrawsInOneColumn() throws Exception
	{
		/* The stops' pictures only: the bank checklist's lines ask for theirs too, and have their own test. */
		List<String> asked = new ArrayList<>();
		GameSprites recording = new GameSprites(null, null, null)
		{
			@Override
			void item(JLabel label, int itemId, int box)
			{
				if (!(label instanceof RunView.Picture))
				{
					asked.add("item " + itemId);
				}
			}

			@Override
			void sprite(JLabel label, int spriteId, int box)
			{
				if (!(label instanceof RunView.Picture))
				{
					asked.add("sprite " + spriteId);
				}
			}

			@Override
			void farmTravelItem(JLabel label, String name, int box)
			{
				if (!(label instanceof RunView.Picture))
				{
					asked.add("named " + name);
				}
			}
		};
		RelayResponse.FarmRun herbs = run("/contract/farm-run.json");
		for (RelayResponse.FarmStop stop : herbs.stops)
		{
			stop.done = false;
			stop.visited = false;
		}
		state.acceptFor(identity.id(), EPOCH, herbs, 1, System.currentTimeMillis());
		FarmRunPanel[] card = new FarmRunPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			card[0] = new FarmRunPanel(state, client, follower, route, shortest, recording, config, store);
			card[0].refresh(true);
		});
		assertEquals(Arrays.asList("item 13126", "item 24961", "sprite 1253"), asked);
		assertEquals("the birdhouse's walk keeps a picture's place too", 4, slots(card[0]));

		state.acceptFor(identity.id(), EPOCH, portRun(), 2, System.currentTimeMillis());
		refresh();
		assertEquals("a run with no picture keeps no place for one", 0, slots(ports));
	}

	/**
	 * The bar is drawn while a run is held and never on a card with none: a bar is visible until
	 * told otherwise, and a card with no run once drew an empty one under Start run.
	 *
	 * <p>This asserted that a farm run had no bar, when the bar was the charting run's alone. Every
	 * run says its count in words, so every run now has the bar over them, counting what they count:
	 * a charting run's tasks charted, and a farm run's stops that something can witness.
	 */
	@Test
	public void theBarIsDrawnForARunAndNeverOnACardWithNone() throws Exception
	{
		FarmRunPanel[] card = new FarmRunPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			card[0] = new FarmRunPanel(state, client, follower, route, shortest, sprites, config, store);
			card[0].refresh(true);
		});
		assertFalse("no run, no bar", drawn(bar(card[0])));

		state.acceptFor(identity.id(), EPOCH, run("/contract/sea-chart-run.json"), 1, System.currentTimeMillis());
		SwingUtilities.invokeAndWait(() -> card[0].refresh(true));
		assertTrue("a charting run's", drawn(bar(card[0])));
		javax.swing.JProgressBar charting = (javax.swing.JProgressBar) bar(card[0]);
		assertEquals("one of its four tasks charted", "1 of 4", charting.getValue() + " of " + charting.getMaximum());

		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 2, System.currentTimeMillis());
		SwingUtilities.invokeAndWait(() -> card[0].refresh(true));
		assertTrue("and the farm run's that replaced it", drawn(bar(card[0])));
		javax.swing.JProgressBar farm = (javax.swing.JProgressBar) bar(card[0]);
		assertEquals("counting the stops the line under it counts", state.view().trackedDone() + " of " + state.view().tracked(),
			farm.getValue() + " of " + farm.getMaximum());
		assertTrue(state.view().tracked() > 0);
	}

	private static Component bar(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			Component found = child instanceof javax.swing.JProgressBar ? child : child instanceof Container ? bar((Container) child) : null;
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	/** Whether a component and everything it sits in is visible. */
	private static boolean drawn(Component component)
	{
		for (Component at = component; at != null; at = at.getParent())
		{
			if (!at.isVisible())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A sea charting run was eight sentences at one weight, each with "Sail 71 tiles" in grey under
	 * it, and a charted task stayed three struck lines. A stop the map gives a tool and a leg in
	 * numbers is one row to scan: the tool's picture, the task, and the leg's length at the right
	 * edge. The count says how far is left, and a charted stop is one cut line with its word.
	 */
	@Test
	public void aChartingStopIsItsToolsPictureItsTaskAndItsLength() throws Exception
	{
		List<String> asked = new ArrayList<>();
		GameSprites recording = new GameSprites(null, null, null)
		{
			@Override
			void farmTravelItem(JLabel label, String name, int box)
			{
				asked.add(name);
			}
		};
		state.acceptFor(identity.id(), EPOCH, run("/contract/sea-chart-run.json"), 1, System.currentTimeMillis());
		FarmRunPanel[] card = new FarmRunPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			card[0] = new FarmRunPanel(state, client, follower, route, shortest, recording, config, store);
			card[0].refresh(true);
		});
		assertEquals("the stops still ahead, each by its task's tool", Arrays.asList("Spyglass", "Captain's log", "Current duck"), asked);
		String words = String.join("\n", SidebarWords.words(card[0]));
		assertTrue(words, words.contains("1 of 4 charted · 122 tiles left"));
		assertTrue("a leg's length over its way: " + words, words.contains("71tsail") && words.contains("20twalk"));
		assertFalse("and not the line it replaced", words.contains("Sail 71 tiles"));
		assertTrue("the charted stop is cut to one line with its word", words.contains("… · Charted"));
	}

	/**
	 * Before you leave was closed with no count, and open it was grey words alone, "not on you" at
	 * the end of one line in the same ink as the seeds. Closed, it now says how many teleports are not
	 * on you; open, each line has its picture, and a teleport not on you is amber beside its greyed
	 * picture. A line nothing here can check makes no claim either way.
	 */
	@Test
	public void beforeYouLeaveCountsWhatIsNotOnYouAndPicturesEachLine() throws Exception
	{
		List<String> asked = new ArrayList<>();
		GameSprites recording = new GameSprites(null, null, null)
		{
			@Override
			void item(JLabel label, int itemId, int box)
			{
				if (label instanceof RunView.Picture)
				{
					asked.add("item " + itemId);
				}
			}

			@Override
			void farmTravelItem(JLabel label, String name, int box)
			{
				if (label instanceof RunView.Picture)
				{
					asked.add("named " + name);
				}
			}

			@Override
			void itemNamed(JLabel label, String name, int box)
			{
				asked.add("supply " + name);
			}
		};
		RelayResponse.FarmRun herbs = run("/contract/farm-run.json");
		herbs.bring.add(teleport("Stony basalt"));
		herbs.bring.add(teleport("Skills necklace"));
		state.acceptFor(identity.id(), EPOCH, herbs, 1, System.currentTimeMillis());
		state.carried(Collections.singletonList("skillsNecklace"));
		FarmRunPanel[] card = new FarmRunPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			card[0] = new FarmRunPanel(state, client, follower, route, shortest, recording, config, store);
			card[0].refresh(true);
		});
		List<String> closed = shown(card[0]);
		assertTrue(closed.toString(), closed.contains("▸ Before you leave · 1 not on you"));
		assertEquals("amber while it counts", MapPin.AMBER, opener(card[0]).getForeground());

		SwingUtilities.invokeAndWait(() -> opener(card[0]).doClick());
		SwingUtilities.invokeAndWait(() -> card[0].refresh(true));
		List<String> open = shown(card[0]);
		assertTrue("open, the lines say it: " + open, open.contains("▾ Before you leave"));
		assertEquals(Arrays.asList("item 13126", "item 24961", "supply 2 herb seeds", "supply Rake",
			"named Stony basalt", "named Skills necklace"), asked);
		Object[] drawn = new Object[4];
		SwingUtilities.invokeAndWait(() ->
		{
			JLabel absent = label(card[0], "Stony basalt · not on you");
			JLabel carried = label(card[0], "Skills necklace");
			drawn[0] = absent.getForeground();
			drawn[1] = picture(absent).faded;
			drawn[2] = carried.getForeground();
			drawn[3] = picture(carried).faded;
		});
		assertEquals(MapPin.AMBER, drawn[0]);
		assertEquals(true, drawn[1]);
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, drawn[2]);
		assertEquals(false, drawn[3]);

		SwingUtilities.invokeAndWait(() -> opener(card[0]).doClick());
		state.carried(Arrays.asList("skillsNecklace", "stonyBasalt"));
		SwingUtilities.invokeAndWait(() -> card[0].refresh(true));
		assertTrue("nothing missing, nothing added", shown(card[0]).contains("▸ Before you leave"));
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, opener(card[0]).getForeground());
	}

	private static RelayResponse.FarmBring teleport(String name)
	{
		RelayResponse.FarmBring line = new RelayResponse.FarmBring();
		line.kind = "teleport";
		line.name = name;
		return line;
	}

	/** The checklist's opener, whatever its count says. */
	private static AbstractButton opener(Component component)
	{
		if (component instanceof AbstractButton && plain(((AbstractButton) component).getText()).contains("Before you leave"))
		{
			return (AbstractButton) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				AbstractButton found = opener(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The picture on a checklist line's own row. */
	private static RunView.Picture picture(JLabel line)
	{
		List<Component> row = new ArrayList<>();
		gather(line.getParent(), row);
		for (Component part : row)
		{
			if (part instanceof RunView.Picture)
			{
				return (RunView.Picture) part;
			}
		}
		return null;
	}

	private static void gather(Component component, List<Component> out)
	{
		out.add(component);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				gather(child, out);
			}
		}
	}

	/** A courier with every crate delivered is finished, and greyed as a finished stop is. */
	@Test
	public void aDeliveredCourierIsGreyed() throws Exception
	{
		PortTaskReader delivered = new PortTaskReader(game.client(), new CargoHolds())
		{
			@Override
			List<Slot> latest()
			{
				return Arrays.asList(
					new PortTaskReader.Slot(0, PortTaskReader.COURIER, 6, HONEY, "Catherby", "Port Sarim", 3, 3, 3, 0, null),
					new PortTaskReader.Slot(1, PortTaskReader.BOUNTY, 438, "Port Sarim tern bounty", null, "Port Sarim", 15, 0, 0, 9, "Tern feather"));
			}

			@Override
			Integer latestHold()
			{
				return 30;
			}
		};
		Color[] colours = new Color[2];
		SwingUtilities.invokeAndWait(() ->
		{
			PortTasksPanel card = new PortTasksPanel(delivered, config, store, sprites, state, client, follower, route, shortest);
			card.refresh(true);
			colours[0] = label(card, HONEY).getForeground();
			colours[1] = label(card, "Port Sarim tern bounty").getForeground();
		});
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, colours[0]);
		assertEquals("a task still to do stays white", Color.WHITE, colours[1]);
	}

	/**
	 * The game is read into a new list of slots on every tick, so a card that asked whether the list
	 * was the same object drew a held task again each second. It asks whether the reading differs.
	 */
	@Test
	public void theSameTasksReadAgainAreNotDrawnAgain() throws Exception
	{
		int[] collected = {0};
		PortTaskReader ticking = new PortTaskReader(game.client(), new CargoHolds())
		{
			@Override
			List<Slot> latest()
			{
				return Arrays.asList(
					new PortTaskReader.Slot(0, PortTaskReader.COURIER, 6, HONEY, "Catherby", "Port Sarim", 3, collected[0], 0, 0, null),
					PortTaskReader.Slot.plain(1, PortTaskReader.EMPTY));
			}

			@Override
			Integer latestHold()
			{
				return 30;
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			PortTasksPanel card = new PortTasksPanel(ticking, config, store, sprites, state, client, follower, route, shortest);
			card.refresh(true);
			JLabel drawn = label(card, HONEY);
			card.refresh(true);
			assertSame("a second reading of the same tasks leaves the rows as they are", drawn, label(card, HONEY));

			collected[0] = 1;
			card.refresh(true);
			assertNotSame("a crate collected is a different reading, and is drawn", drawn, label(card, HONEY));
			assertTrue(label(card, "Collect 2 crates at Catherby for Port Sarim") != null);
		});
	}

	/** The raw text of the shown label whose words are these, tags and all. */
	private static String raw(Component root, String words) throws Exception
	{
		String[] text = new String[1];
		SwingUtilities.invokeAndWait(() -> text[0] = label(root, words).getText());
		return text[0];
	}

	private static JLabel label(Component component, String words)
	{
		if (!component.isVisible())
		{
			return null;
		}
		if (component instanceof JLabel && words.equals(plain(((JLabel) component).getText())))
		{
			return (JLabel) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JLabel found = label(child, words);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Shown labels with no words that hold a picture's 16 pixels: the stops' picture places. */
	private static int slots(Component component) throws Exception
	{
		int[] count = new int[1];
		SwingUtilities.invokeAndWait(() -> count[0] = countSlots(component));
		return count[0];
	}

	private static int countSlots(Component component)
	{
		if (!component.isVisible())
		{
			return 0;
		}
		int count = 0;
		if (component instanceof JLabel && plain(((JLabel) component).getText()).isEmpty()
			&& component.getPreferredSize().width == 16 && component.getPreferredSize().height == 16)
		{
			count++;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				count += countSlots(child);
			}
		}
		return count;
	}

	private JButton act(MapCompanionPanel.Act act) throws Exception
	{
		JButton[] button = new JButton[1];
		SwingUtilities.invokeAndWait(() -> button[0] = farm.actButton(act));
		return button[0];
	}

	private static RunView runView(Component component)
	{
		if (component instanceof RunView)
		{
			return (RunView) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				RunView found = runView(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Lumbridge Castle bank, picked from the search: a destination of the player's own. */
	private static RelayResponse.Destination bank()
	{
		RelayResponse.Destination bank = new RelayResponse.Destination();
		bank.name = "Lumbridge Castle bank";
		bank.x = 3208;
		bank.y = 3220;
		bank.plane = 2;
		return bank;
	}

	/** A replant this client watched, as the farming reader reports one. */
	private void replant(String patch)
	{
		CompanionSnapshot.Patch replanted = new CompanionSnapshot.Patch();
		replanted.id = patch;
		replanted.state = "growing";
		replanted.completionId = "replanted-" + patch;
		replanted.completedMsAgo = 0L;
		state.observe(Collections.singletonList(replanted), null, null, System.currentTimeMillis());
	}

	/** Pick a kind of run in the farm card's picker, as the player does. */
	private void choose(String kind) throws Exception
	{
		choose(farm, kind);
	}

	private static void choose(Component card, String kind) throws Exception
	{
		SwingUtilities.invokeAndWait(() -> combo(card).setSelectedItem(kind));
	}

	/**
	 * A birdhouse said "Done · seeded sighting", and before that, in 10px, "Tracked by Time Tracking
	 * sightings"; every checklist teleport said "· 1 leg". Time Tracking is still named as the
	 * evidence, in the words a player uses, and a teleport says its legs only when it serves several.
	 */
	@Test
	public void aRunSaysItsEvidenceInThePlayersWords() throws Exception
	{
		RelayResponse.FarmRun herbs = run("/contract/farm-run.json");
		RelayResponse.FarmStop birdhouse = herbs.stops.get(2);
		assertEquals("Done · seen by Time Tracking", RunView.status(new FarmRunState.View(herbs, Collections.singleton(3),
			Collections.emptySet(), Collections.emptySet(), true, 0, 1, null), birdhouse));

		birdhouse.done = false;
		herbs.bring.get(0).uses = 2;
		state.acceptFor(identity.id(), EPOCH, herbs, 1, System.currentTimeMillis());
		refresh();
		SwingUtilities.invokeAndWait(() -> opener(farm).doClick());
		refresh();
		List<String> words = shown(farm);
		assertTrue(words.toString(), words.contains(RunView.BIRDHOUSE_PENDING));
		assertTrue(words.toString(), words.contains("Explorer's ring 2 · 2 legs"));
		assertTrue(words.toString(), words.contains("Catherby tablet"));
		float[] size = new float[1];
		SwingUtilities.invokeAndWait(() -> size[0] = label(farm, RunView.BIRDHOUSE_PENDING).getFont().getSize2D());
		assertEquals("as large as the lines around it", 11f, size[0], 0f);
	}

	/**
	 * While a run was planned the card was one full-width End run over "Planning run…", and the run
	 * chosen was gone. It names the run, and the button that calls the plan off says so.
	 */
	@Test
	public void aRunBeingPlannedIsNamedAndOffersCancel() throws Exception
	{
		FarmRunClient waiting = new FarmRunClient(neverAnswering(), gson, game.client(), thread, pairing, identity,
			state, destinations, route);
		FarmRunPanel[] card = new FarmRunPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			card[0] = new FarmRunPanel(state, waiting, follower, route, shortest, sprites, config, store);
			card[0].refresh(true);
		});
		choose(card[0], "Herb run");
		press(card[0], "Start run");
		SwingUtilities.invokeAndWait(() -> card[0].refresh(true));
		List<String> planning = shown(card[0]);
		assertTrue(planning.toString(), planning.contains("Planning Herb run…"));
		assertTrue(planning.toString(), planning.contains("Cancel"));
		assertFalse(planning.toString(), planning.contains("End run"));
		assertTrue(enabled(card[0], "Cancel"));
	}

	/** "Ready now ▾" alone read as a status. Closed, the picker names what it picks; its list stays plain. */
	@Test
	public void theClosedPickerNamesWhatItPicks() throws Exception
	{
		String[] drawn = new String[2];
		SwingUtilities.invokeAndWait(() ->
		{
			@SuppressWarnings("unchecked")
			ListCellRenderer<Object> rows = (ListCellRenderer<Object>) combo(farm).getRenderer();
			drawn[0] = ((JLabel) rows.getListCellRendererComponent(new JList<>(), "Ready now", -1, false, false)).getText();
			drawn[1] = ((JLabel) rows.getListCellRendererComponent(new JList<>(), "Ready now", 0, false, false)).getText();
		});
		assertEquals("Run: Ready now", drawn[0]);
		assertEquals("Ready now", drawn[1]);
	}

	/**
	 * After End run the kind of run was greyed with Start run for the wait between presses, though
	 * choosing asks the map nothing, and neither said why. The picker stays; Start run waits and says
	 * how long, on this card and on Port tasks, which the same wait holds.
	 */
	@Test
	public void theWaitBetweenPressesHoldsStartRunAndNotThePicker() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		refresh();
		status = 204;
		press(farm, "End run");
		refresh();
		assertTrue(route.pressWait(System.currentTimeMillis()) > 0L);
		assertTrue(combo(farm).isEnabled());
		choose("Herb run");
		assertFalse(enabled(farm, "Start run"));
		assertTrue(tip(farm), tip(farm).startsWith("The map asked to wait: "));
		assertFalse(enabled(ports, "Start run"));
		assertTrue(tip(ports), tip(ports).startsWith("The map asked to wait: "));
	}

	/** Start run's tooltip on a card, or "" with none. */
	private static String tip(Component card) throws Exception
	{
		String[] tip = new String[1];
		SwingUtilities.invokeAndWait(() -> tip[0] = button(card, "Start run").getToolTipText());
		return tip[0] == null ? "" : tip[0];
	}

	/** Calls queued on a dispatcher that never runs them: a press stays in flight for the whole test. */
	private static OkHttpClient neverAnswering()
	{
		AbstractExecutorService never = new AbstractExecutorService()
		{
			@Override
			public void execute(Runnable command)
			{
			}

			@Override
			public void shutdown()
			{
			}

			@Override
			public List<Runnable> shutdownNow()
			{
				return Collections.emptyList();
			}

			@Override
			public boolean isShutdown()
			{
				return false;
			}

			@Override
			public boolean isTerminated()
			{
				return false;
			}

			@Override
			public boolean awaitTermination(long timeout, TimeUnit unit)
			{
				return true;
			}
		};
		return new OkHttpClient.Builder().dispatcher(new Dispatcher(never)).build();
	}

	private static JComboBox<?> combo(Component component)
	{
		if (component instanceof JComboBox)
		{
			return (JComboBox<?>) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JComboBox<?> found = combo(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Whether a shown button with these words can be pressed. */
	private static boolean enabled(Component root, String words) throws Exception
	{
		boolean[] enabled = new boolean[1];
		SwingUtilities.invokeAndWait(() ->
		{
			AbstractButton button = button(root, words);
			enabled[0] = button != null && button.isEnabled();
		});
		return enabled[0];
	}

	/** A click that does what a click does, enabled or not: a disabled button takes no press. */
	private static void click(Component root, String words) throws Exception
	{
		SwingUtilities.invokeAndWait(() -> button(root, words).doClick());
	}

	private RelayResponse.FarmRun portRun() throws Exception
	{
		return run("/contract/port-task-run.json");
	}

	private RelayResponse.FarmRun run(String fixture) throws Exception
	{
		String json = new String(getClass().getResourceAsStream(fixture).readAllBytes(), StandardCharsets.UTF_8);
		return gson.fromJson(json, RelayResponse.FarmRun.class);
	}

	private static PortTaskReader.Slot courier(int slot, int taskId, int taken, int delivered)
	{
		return new PortTaskReader.Slot(slot, PortTaskReader.COURIER, taskId, null, null, null, 3, taken, delivered, 0, null);
	}

	private static PortTaskReader.Slot bounty(int slot, int taskId, int remaining)
	{
		return new PortTaskReader.Slot(slot, PortTaskReader.BOUNTY, taskId, null, null, null, 15, 0, 0, remaining, "Osprey feather");
	}

	private static PortTaskReader.Slot empty(int slot)
	{
		return PortTaskReader.Slot.plain(slot, PortTaskReader.EMPTY);
	}

	/** Both cards, as the sidebar's one-second refresh does them. */
	private void refresh() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			farm.refresh(true);
			ports.refresh(true);
		});
	}

	/** A click on a button the player can see and press, on the event thread as Swing makes it. */
	private static void press(Component root, String words) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AbstractButton button = button(root, words);
			assertTrue("no " + words + " to press: " + shown(root), button != null && button.isEnabled());
			button.doClick();
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

	/** A label's words as a player reads them: its markup dropped and its escapes read back. */
	private static String plain(String text)
	{
		return text == null ? "" : text.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
	}

	/**
	 * Calls run on the thread that makes them and are answered here, never sent: a press has landed
	 * when it returns, and no test waits on another thread for it.
	 */
	private OkHttpClient answering()
	{
		AbstractExecutorService inline = new AbstractExecutorService()
		{
			@Override
			public void execute(Runnable command)
			{
				command.run();
			}

			@Override
			public void shutdown()
			{
			}

			@Override
			public List<Runnable> shutdownNow()
			{
				return Collections.emptyList();
			}

			@Override
			public boolean isShutdown()
			{
				return false;
			}

			@Override
			public boolean isTerminated()
			{
				return false;
			}

			@Override
			public boolean awaitTermination(long timeout, TimeUnit unit)
			{
				return true;
			}
		};
		return new OkHttpClient.Builder().dispatcher(new Dispatcher(inline)).addInterceptor(chain ->
		{
			asked++;
			Buffer buffer = new Buffer();
			if (chain.request().body() != null)
			{
				chain.request().body().writeTo(buffer);
			}
			sent = buffer.readUtf8();
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("")
				.header("X-Companion-Relay-Epoch", EPOCH)
				.body(ResponseBody.create(MediaType.get("application/json"), body)).build();
		}).build();
	}
}
