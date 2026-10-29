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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * The walking steps of a followed route, handed to the Shortest Path plugin to draw.
 *
 * <p>The map chooses the way and words its steps; Shortest Path draws a walk better than straight
 * lines could. So it is given one walking step at a time — the step's start, which is where the
 * player stands, and its end — and nothing at all while the current step is a teleport or a ride:
 * aimed at a landing, it would plan its own way there and contradict the step above it (the map's
 * plan, § 4, the owner's rule). The messages are Shortest Path's own, read from its
 * {@code PluginMessageHandler}: namespace {@code shortestpath}, {@code path} with {@code start} and
 * {@code target}, {@code clear}, and {@code getTarget}, answered with {@code currentTarget}.
 *
 * <p><b>Whose path it is.</b> The player's Shortest Path target is theirs. This draws only on a
 * route the player chose to follow — Follow or Reroute pressed here — so a destination that merely
 * arrived, a friend's included, never touches it. It clears only a path it set, and asks first:
 * {@code clear} goes only when Shortest Path's answer to {@code getTarget} still names this
 * plugin's target alone. When the answer names another target, the player or another plugin has
 * taken Shortest Path over, and the route stops being drawn there until Follow is pressed again.
 * It never sends Shortest Path's {@code config}, which would override the player's settings.
 *
 * <p><b>Whether it is there.</b> Shortest Path is another Hub plugin and may not be installed or
 * running. While a route is shown this asks {@code getTarget} every few seconds; no answer within
 * two ticks means it is not there, and nothing is drawn or offered. Asking starts no path and
 * changes nothing in it.
 *
 * <p>An answer is matched to the question it answers by the id this sends with it, because the
 * answer is posted on a later client frame: an answer to a question asked before this plugin's own
 * last {@code path} describes the path before it, and is not read as somebody else's.
 *
 * <p>Client thread only: the game tick drives it, and Shortest Path answers on the client thread.
 * The two fields the sidebar reads are volatile.
 */
@Singleton
class ShortestPath
{
	static final String NAMESPACE = "shortestpath";
	/** The start of every id this plugin asks with, so its answers are told from another plugin's. */
	static final String ID = "osrs-map-companion:";
	/** While a route is shown, how often to ask whether Shortest Path is there and what it draws. */
	static final int ASK_EVERY_TICKS = 10;
	/** Shortest Path answers on the next client frame; this long without an answer is no plugin. */
	static final int ANSWER_TICKS = 2;

	static final String TAKEN_OVER = "Shortest Path was given another path, so this route stopped drawing there. "
		+ "Follow draws it again.";

	private final Consumer<PluginMessage> post;
	@Nullable
	private final EventBus eventBus;
	@Nullable
	private final ClientThread clientThread;
	@Nullable
	private EventBus.Subscriber shutdownListener;
	@Nullable
	private WorldPoint shutdownTarget;
	private int shutdownAsked;
	private boolean stopped;

	/** Whether Shortest Path answered the last question. */
	private volatile boolean installed;
	/** The step number whose walk this plugin's path draws, or zero. */
	private volatile int drawing;

	private int asked;
	private int askedAt = Integer.MIN_VALUE / 2;
	private boolean awaiting;
	/** Questions numbered below this were asked before this plugin's own latest path. */
	private int pathAsked;
	/** The question whose answer decides a clear, or zero. */
	private int clearAsked;
	/** The target this plugin set and believes Shortest Path still shows, or null. */
	@Nullable
	private WorldPoint ours;
	/** The step that target was set for, so the same step is not sent again after Shortest Path ends it. */
	@Nullable
	private String drawnKey;
	@Nullable
	private Runnable onTakenOver;

	@Inject
	ShortestPath(EventBus eventBus, ClientThread clientThread)
	{
		this.post = eventBus::post;
		this.eventBus = eventBus;
		this.clientThread = clientThread;
	}

	ShortestPath(Consumer<PluginMessage> post)
	{
		this.post = post;
		this.eventBus = null;
		this.clientThread = null;
	}

	boolean installed()
	{
		return installed;
	}

	/** The number of the step whose walk Shortest Path is drawing for this plugin, or zero. */
	int drawing()
	{
		return drawing;
	}

	/**
	 * Bring Shortest Path in line with the route. Called on the game tick.
	 *
	 * @param enabled the player's setting, Draw paths with Shortest Path
	 * @param from where the player stands, or null where that cannot be trusted
	 * @param takenOver run when somebody else's target replaces this plugin's
	 */
	void tick(int tick, @Nullable DestinationRoute.View view, boolean enabled, @Nullable WorldPoint from, Runnable takenOver)
	{
		cancelShutdown();
		stopped = false;
		onTakenOver = takenOver;
		if (awaiting && tick - askedAt > ANSWER_TICKS)
		{
			/* No answer: not installed, or turned off. Nothing of ours is drawn by a plugin that is not there. */
			awaiting = false;
			installed = false;
			clearAsked = 0;
			forget();
		}

		boolean shown = view != null && view.hasRoute();
		if ((enabled && shown || ours != null) && !awaiting && tick - askedAt >= ASK_EVERY_TICKS)
		{
			ask(tick);
		}

		RelayResponse.RouteStep step = enabled && installed && view != null && view.following ? view.current() : null;
		if (step != null && step.walk && step.at != null && from != null)
		{
			String key = view.key + "|" + view.revision + "|" + view.chosen + "|" + view.step;
			if (!key.equals(drawnKey))
			{
				WorldPoint target = DestinationRoute.point(step.at);
				Map<String, Object> data = new HashMap<>();
				data.put("start", from);
				data.put("target", target);
				post.accept(new PluginMessage(NAMESPACE, "path", data));
				ours = target;
				drawnKey = key;
				drawing = view.step + 1;
				pathAsked = asked + 1;
				clearAsked = 0;
			}
			return;
		}
		/* A teleport or a ride, a route not followed, or none: nothing is drawn, and ours goes. */
		drawnKey = null;
		drawing = 0;
		if (ours != null && clearAsked == 0)
		{
			clearAsked = ask(tick);
		}
	}

	/** Read Shortest Path's answer. Called from the plugin's own {@code PluginMessage} subscription. */
	void onPluginMessage(PluginMessage message)
	{
		if (!NAMESPACE.equals(message.getNamespace()) || !"currentTarget".equals(message.getName()))
		{
			return;
		}
		Map<String, Object> data = message.getData();
		Object id = data == null ? null : data.get("id");
		if (!(id instanceof String) || !((String) id).startsWith(ID))
		{
			return;
		}
		int question;
		try
		{
			question = Integer.parseInt(((String) id).substring(ID.length()));
		}
		catch (NumberFormatException other)
		{
			return;
		}
		if (question == shutdownAsked && shutdownTarget != null)
		{
			boolean clear = owns(data, shutdownTarget);
			cancelShutdown();
			if (clear)
			{
				post.accept(new PluginMessage(NAMESPACE, "clear"));
			}
			return;
		}
		if (stopped)
		{
			return;
		}
		installed = true;
		if (question == asked)
		{
			awaiting = false;
		}
		if (ours == null || question < pathAsked)
		{
			/* Nothing of ours to judge, or an answer about the path before ours. */
			return;
		}
		boolean set = Boolean.TRUE.equals(data.get("set"));
		boolean stillOurs = owns(data, ours);
		if (stillOurs)
		{
			if (question == clearAsked)
			{
				post.accept(new PluginMessage(NAMESPACE, "clear"));
				forget();
				clearAsked = 0;
			}
			return;
		}
		if (set)
		{
			/* Somebody else's target: theirs now, and the route stops drawing there. */
			Runnable stop = onTakenOver;
			if (stop != null)
			{
				stop.run();
			}
		}
		/* Shortest Path ended our path itself, on arrival, or somebody replaced it: nothing to clear. */
		ours = null;
		clearAsked = 0;
	}

	/**
	 * The plugin has left the event bus before its teardown runs. A temporary listener asks who
	 * owns the path now, and clears only that answer's matching target. It expires after two
	 * seconds without blocking the client, and a new lifetime cancels it on its first tick.
	 */
	void shutDown()
	{
		WorldPoint target = ours;
		cancelShutdown();
		stopped = true;
		forget();
		installed = false;
		awaiting = false;
		clearAsked = 0;
		onTakenOver = null;
		if (target == null)
		{
			return;
		}
		shutdownTarget = target;
		int question = shutdownAsked = ++asked;
		if (eventBus != null)
		{
			shutdownListener = eventBus.register(PluginMessage.class, this::onPluginMessage, 0);
		}
		if (clientThread != null)
		{
			long deadline = System.nanoTime() + 2_000_000_000L;
			clientThread.invokeLater(() ->
			{
				if (shutdownAsked != question)
				{
					return true;
				}
				if (System.nanoTime() < deadline)
				{
					return false;
				}
				cancelShutdown();
				return true;
			});
		}
		Map<String, Object> data = new HashMap<>();
		data.put("id", ID + question);
		post.accept(new PluginMessage(NAMESPACE, "getTarget", data));
	}

	private static boolean owns(Map<String, Object> data, WorldPoint expected)
	{
		Object targets = data.get("target");
		return Boolean.TRUE.equals(data.get("set")) && targets instanceof Collection && ((Collection<?>) targets).size() == 1
			&& expected.equals(((Collection<?>) targets).iterator().next());
	}

	private void cancelShutdown()
	{
		if (shutdownListener != null && eventBus != null)
		{
			eventBus.unregister(shutdownListener);
		}
		shutdownListener = null;
		shutdownTarget = null;
		shutdownAsked = 0;
	}

	private int ask(int tick)
	{
		asked++;
		Map<String, Object> data = new HashMap<>();
		data.put("id", ID + asked);
		post.accept(new PluginMessage(NAMESPACE, "getTarget", data));
		askedAt = tick;
		awaiting = true;
		return asked;
	}

	private void forget()
	{
		ours = null;
		drawnKey = null;
		drawing = 0;
	}
}
