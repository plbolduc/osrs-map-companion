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
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
	/** Every press that reached the map. */
	private int asked;
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
			ports = new PortTasksPanel(tasks, config, sprites, state, client, follower, route, shortest);
			farm = new FarmRunPanel(state, client, follower, route, shortest, sprites, config);
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
	 * Pausing changed one word, on the button, and a search pick or a friend's destination paused the
	 * run with no word on its card at all. The count says the run is paused now, and while the
	 * destination that paused it is shown, the card names it and the way back.
	 */
	@Test
	public void aPausedRunSaysSoAndNamesTheRouteThatPausedIt() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		follower.sync();
		refresh();
		assertTrue(shown(farm).toString(), shown(farm).contains("1 of 3 tracked done"));

		press(farm, "Pause");
		refresh();
		List<String> paused = shown(farm);
		assertTrue(paused.toString(), paused.contains("Paused · 1 of 3 tracked done"));
		assertFalse("the player paused it: nothing else to name", paused.contains(RunView.pausedLine("1. Falador farm")));

		press(farm, "Follow");
		follower.sync();
		destinations.selectLocal(bank());
		follower.sync();
		refresh();
		List<String> detour = shown(farm);
		assertTrue(detour.toString(), detour.contains("Paused · 1 of 3 tracked done"));
		assertTrue(detour.toString(), detour.contains(RunView.pausedLine("Lumbridge Castle bank")));

		destinations.dismiss();
		refresh();
		List<String> dismissed = shown(farm);
		assertTrue("still paused, as the README says", dismissed.contains("Paused · 1 of 3 tracked done"));
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
	 * away the route being walked. While another route is followed the run's card leaves the keys to
	 * the destination card; a destination merely shown, not followed, leaves them with the run.
	 */
	@Test
	public void duringAFollowedDetourTheKeysLeaveThePausedRunAlone() throws Exception
	{
		state.acceptFor(identity.id(), EPOCH, run("/contract/farm-run.json"), 1, System.currentTimeMillis());
		follower.sync();
		refresh();
		assertEquals("Pause", hotkey(false).getText());
		assertEquals("Reroute", hotkey(true).getText());

		RelayResponse.Destination bank = DestinationRouteTest.destination("Lumbridge Castle bank",
			DestinationRouteTest.route("Lumbridge Home Teleport", DestinationRouteTest.alKharid()));
		destinations.selectLocal(bank);
		route.adoptPressed(bank);
		follower.sync();
		refresh();
		assertTrue("a Route press follows what it planned", route.view().following);
		assertNull(hotkey(false));
		assertNull(hotkey(true));

		route.follow(false);
		refresh();
		assertEquals("Follow", hotkey(false).getText());
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
			insets[0] = new FarmRunPanel(state, client, follower, route, shortest, recording, config).getInsets();
			insets[1] = new PortTasksPanel(tasks, config, recording, state, client, follower, route, shortest).getInsets();
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
		int leg = first.indexOf("This leg");
		assertTrue(first.toString(), leg > first.indexOf("1. Falador farm") && leg < first.indexOf("2. Catherby"));
		assertTrue(first.toString(), first.contains("4. My bank · Visited"));
		assertFalse("a visited stop's way there is spent", first.contains("Draynor Manor Teleport"));

		replant("falador-herb");
		follower.sync();
		refresh();
		List<String> second = shown(farm);
		assertTrue(second.toString(), second.contains("1. Falador farm · Done"));
		assertFalse("a done stop's way there is spent", second.contains("Explorer's ring 2: Teleport"));
		int moved = second.indexOf("This leg");
		assertTrue(second.toString(), moved > second.indexOf("2. Catherby") && moved < second.indexOf("4. My bank · Visited"));
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
		List<String> asked = new ArrayList<>();
		GameSprites recording = new GameSprites(null, null, null)
		{
			@Override
			void item(JLabel label, int itemId, int box)
			{
				asked.add("item " + itemId);
			}

			@Override
			void sprite(JLabel label, int spriteId, int box)
			{
				asked.add("sprite " + spriteId);
			}

			@Override
			void farmTravelItem(JLabel label, String name, int box)
			{
				asked.add("named " + name);
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
			card[0] = new FarmRunPanel(state, client, follower, route, shortest, recording, config);
			card[0].refresh(true);
		});
		assertEquals(Arrays.asList("item 13126", "item 24961", "sprite 1253"), asked);
		assertEquals("the birdhouse's walk keeps a picture's place too", 4, slots(card[0]));

		state.acceptFor(identity.id(), EPOCH, portRun(), 2, System.currentTimeMillis());
		refresh();
		assertEquals("a run with no picture keeps no place for one", 0, slots(ports));
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
			PortTasksPanel card = new PortTasksPanel(delivered, config, sprites, state, client, follower, route, shortest);
			card.refresh(true);
			colours[0] = label(card, HONEY).getForeground();
			colours[1] = label(card, "Port Sarim tern bounty").getForeground();
		});
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, colours[0]);
		assertEquals("a task still to do stays white", Color.WHITE, colours[1]);
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

	private JButton hotkey(boolean rerouting) throws Exception
	{
		JButton[] button = new JButton[1];
		SwingUtilities.invokeAndWait(() -> button[0] = farm.hotkey(rerouting));
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
		SwingUtilities.invokeAndWait(() -> combo(farm).setSelectedItem(kind));
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
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("")
				.header("X-Companion-Relay-Epoch", EPOCH)
				.body(ResponseBody.create(MediaType.get("application/json"), body)).build();
		}).build();
	}
}
