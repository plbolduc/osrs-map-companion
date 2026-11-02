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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The card that starts a port-task run, read off its real components as a player sees them.
 *
 * <p>Presses go through the card's own buttons, {@link FarmRunClient} and {@link FarmRunState}. The
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

	public RunCardsTest() throws Exception
	{
		game.login("Example", 444, 3222, 3218, 0);
		config.set("sharePortTasks", true);
		config.set("shareBoats", true);
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
		state.bind(identity.id());
		SwingUtilities.invokeAndWait(() -> ports = new PortTasksPanel(tasks, config, sprites, state, client, follower, route, shortest));
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

	private RelayResponse.FarmRun portRun() throws Exception
	{
		String json = new String(getClass().getResourceAsStream("/contract/port-task-run.json").readAllBytes(), StandardCharsets.UTF_8);
		return gson.fromJson(json, RelayResponse.FarmRun.class);
	}

	private void refresh() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> ports.refresh(true));
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
			new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("")
				.header("X-Companion-Relay-Epoch", EPOCH)
				.body(ResponseBody.create(MediaType.get("application/json"), body)).build()).build();
	}
}
