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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Explicit Start run, Reroute and End run presses, with no automatic planning requests.
 *
 * <p>The game thread supplies credentials and the current tile. OkHttp reads bounded replies
 * outside the reset lock; a connection generation and a call identity reject old callbacks.
 * Planning shares the destination router's one-at-a-time rule and wait. End is allowed while
 * planning: it cancels that call and tells the server to discard a plan that has not landed yet.
 */
@Singleton
final class FarmRunClient
{
	static final String PATH = "/api/companion/run";
	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final FarmRunState state;
	private final DestinationState destinations;
	private final DestinationRoute gate;
	private Call call;
	private Ticket active;
	private volatile Said said;

	static final class Said
	{
		/** How long a confirmation is news; a refusal stays until the next press. */
		static final long CONFIRMATION_MS = 10_000L;

		final boolean planning;
		final String message;
		final long lifetime;
		final long runGeneration;
		/** Said about a port-task run, so the Port tasks card shows it and the farm run card does not. */
		final boolean port;
		/** A press that did what it asked, "Run ended.": said for a moment, then left out. */
		final boolean confirms;
		final long atMs;

		Said(boolean planning, @Nullable String message, long lifetime, long runGeneration, boolean port, boolean confirms)
		{
			this.port = port;
			this.runGeneration = runGeneration;
			this.planning = planning;
			this.message = message;
			this.lifetime = lifetime;
			this.confirms = confirms;
			this.atMs = System.currentTimeMillis();
		}

		/**
		 * Whether there are words to draw at {@code now}. A line on the card says what is true now: a
		 * confirmation stops being news after ten seconds and gives its row back, while a refusal keeps
		 * its line until the next press says something else.
		 */
		boolean shown(long now)
		{
			return message != null && (!confirms || now - atMs < CONFIRMATION_MS);
		}

		/** A press that did not do what it asked: everything said but a plan or an end on its way, and "Run ended.". */
		boolean refused()
		{
			return !planning && message != null && !confirms;
		}
	}

	@AllArgsConstructor
	private static final class Ticket
	{
		final long lifetime;
		final boolean end;
		final boolean holdsGate;
		final String member;
		final boolean port;
	}

	static final class Question
	{
		String start;
		Reroute reroute;
		RoutePress.Tile from;
	}

	static final class Reroute
	{
		String id;
		List<Integer> done;
		List<Integer> visited;
	}

	@Inject
	FarmRunClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread, Pairing pairing,
				  MemberIdentity identity, FarmRunState state, DestinationState destinations, DestinationRoute gate)
	{
		this.http = CompanionHttp.pinnedToServer(http).newBuilder()
					.readTimeout(60, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build();
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.pairing = pairing;
		this.identity = identity;
		this.state = state;
		this.destinations = destinations;
		this.gate = gate;
	}

	void start(String kind)
	{
		start(kind, null);
	}

	/** @param name the run as the picker names it, said while it is planned: "Planning Herb run…" */
	void start(String kind, @Nullable String name)
	{
		clientThread.invokeLater(() -> press(kind, name, false, false));
	}

	void reroute()
	{
		clientThread.invokeLater(() -> press(null, null, true, false));
	}

	void end()
	{
		clientThread.invokeLater(() -> press(null, null, false, true));
	}

	synchronized boolean ready()
	{
		return active == null && gate.pressReady(System.currentTimeMillis());
	}

	synchronized boolean endingRequest()
	{
		return active != null && active.end;
	}

	/**
	 * What a press said, as it is drawn at {@code now}: a wait it met counts down from the gate the run
	 * shares with every Route, and every other sentence is said as it is.
	 */
	String words(Said said, long now)
	{
		return MapWait.words(said.message, gate.pressWait(now));
	}

	@Nullable
	Said said()
	{
		Said current = said;
		return current != null && current.lifetime == destinations.generation()
			   && (current.runGeneration < 0 || state.current(current.runGeneration)) ? current : null;
	}

	/** Client thread, including the local stop; nothing reads a game tile in a network callback. */
	private void press(@Nullable String start, @Nullable String name, boolean reroute, boolean end)
	{
		synchronized (this)
		{
			// End may cancel a planner, but a repeated press must leave its deletion in flight.
			if (active != null && active.end)
			{
				return;
			}
		}
		String server = pairing.serverUrl();
		String code = pairing.code();
		String member = identity.id();
		if (member != null)
		{
			state.bind(member);
		}
		FarmRunState.View current = state.view();
		if (reroute && current == null)
		{
			return;
		}
		String id = current == null ? null : current.run.id;
		boolean port = FarmRunWire.PORT_TASKS.equals(start) || (start == null && current != null && FarmRunWire.PORT_TASKS.equals(current.run.kind));
		if (end)
		{
			state.end(id == null);
		}
		String secret = member == null ? null : pairing.secret(server, code, member);
		long lifetime = destinations.generation();
		boolean playing = client.getGameState() == GameState.LOGGED_IN;
		boolean connected = playing && server != null && !code.isEmpty()
							&& pairing.hasServerConsent(server, code) && secret != null && HttpUrl.parse(server + PATH) != null;
		Question question = new Question();
		question.start = start;
		if (reroute)
		{
			question.reroute = new Reroute();
			question.reroute.id = id;
			question.reroute.done = new ArrayList<>(current.done);
			question.reroute.visited = new ArrayList<>(current.visited);
		}
		WorldPoint where = connected ? DestinationRoute.standing(client) : null;
		question.from = where == null ? null : new RoutePress.Tile(where);

		Ticket ticket;
		synchronized (this)
		{
			boolean held = false;
			if (end && active != null)
			{
				held = active.holdsGate;
				active = null;
				if (call != null)
				{
					call.cancel();
				}
				call = null;
			}
			else if (active != null)
			{
				return;
			}
			if (!connected)
			{
				if (held)
				{
					gate.finishPress(System.currentTimeMillis() + RoutePress.MIN_GAP_MS);
				}
				said = new Said(false, unsent(playing, reroute, end), lifetime, -1, port, false);
				return;
			}
			if (!held)
			{
				String refused = gate.beginPress(System.currentTimeMillis());
				if (refused != null && !end)
				{
					said = new Said(false, refused, lifetime, -1, port, false);
					return;
				}
				held = refused == null;
			}
			ticket = new Ticket(lifetime, end, held, member, port);
			active = ticket;
			said = new Said(true, end ? "Ending run…" : name != null ? "Planning " + name + "…" : "Planning run…", lifetime, -1, port, false);
			call = http.newCall(request(server, code, member, secret, question, end, id));
			CompanionHttp.enqueue(call,
				(failed, error) -> refuse(ticket, RoutePress.UNREACHABLE, System.currentTimeMillis() + RoutePress.FAILURE_GAP_MS),
				(completed, response) -> handle(ticket, response));
		}
	}

	/**
	 * Why a press could not be sent, by its cause: no character logged in, or none the map takes as a
	 * member yet. End has already ended the run here, so it says what is left of it on the map.
	 */
	static String unsent(boolean playing, boolean reroute, boolean end)
	{
		if (end)
		{
			return playing ? Words.of("farm.endedNotConnected") : Words.of("farm.endedLogIn");
		}
		if (!playing)
		{
			return reroute ? "Log in to reroute the run." : "Log in to start a run.";
		}
		return RoutePress.NOT_CONNECTED;
	}

	Request request(String server, String code, String member, String secret, Question question, boolean end, @Nullable String id)
	{
		HttpUrl.Builder url = HttpUrl.parse(server + PATH).newBuilder();
		if (end && id != null)
		{
			url.addQueryParameter("run", id);
		}
		Request.Builder request = CompanionHttp.member(url.build(), code, member, secret);
		return (end ? request.delete() : request.post(CompanionHttp.json(gson, question))).build();
	}

	private void handle(Ticket ticket, Response response)
	{
		try
			(Response close = response)
		{
			long now = System.currentTimeMillis();
			long next = now + Math.max(RoutePress.MIN_GAP_MS, MapWait.named(close));
			if (close.isRedirect())
			{
				refuse(ticket, RoutePress.REDIRECTS, now + 300_000L);
				return;
			}
			if (!close.isSuccessful())
			{
				int code = close.code();
				String why = MapWait.why(code, Words.of("farm.notPrepared"));
				String words = CompanionHttp.said(gson, CompanionHttp.readBounded(close.body()), 240);
				boolean worded = words != null;
				why = worded ? words : why;
				/*
				 * A refusal the map words (409, 404, 400) is an answer, not a failure: the map is up, read
				 * the press and said why it will not plan it — nothing is due yet, no patch seen, no such
				 * run. Backing off spares a map that is struggling, and this one is not; the next press is
				 * the player's own, after they change something. So it keeps the five seconds between
				 * presses, or the wait the map named, as Route does after a 404. No answer, a 5xx or a
				 * reply that cannot be read keeps the half minute.
				 */
				boolean answered = worded && (code == 409 || code == 404 || code == 400);
				refuse(ticket, why, answered ? next : Math.max(next, now + RoutePress.FAILURE_GAP_MS));
				return;
			}
			if (ticket.end)
			{
				String epoch = close.header("X-Companion-Relay-Epoch");
				if (DirectiveEpoch.clean(epoch) == null)
				{
					refuse(ticket, RoutePress.UNREADABLE, next);
					return;
				}
				String removed = close.header("X-Companion-Run-Revision");
				long removalRevision = removed == null ? 0 : Long.parseLong(removed);
				if (removalRevision < 0 || removalRevision > 9_007_199_254_740_991L)
				{
					removalRevision = 0;
				}
				finish(ticket, null, removalRevision, epoch, null, next);
				return;
			}
			RelayResponse.Directive answer = gson.fromJson(
												 CompanionHttp.readBounded(close.body()), RelayResponse.Directive.class);
			if (answer == null || answer.run == null || answer.runRevision <= 0 || answer.relayEpoch == null)
			{
				refuse(ticket, "The map returned no readable run.", next);
				return;
			}
			finish(ticket, answer.run, answer.runRevision, answer.relayEpoch, null, next);
		}
		catch (IOException | RuntimeException unreadable)
		{
			refuse(ticket, RoutePress.UNREADABLE, System.currentTimeMillis() + RoutePress.FAILURE_GAP_MS);
		}
	}

	/** A press the map did not answer with a run: why, and when the next may go. */
	private void refuse(Ticket ticket, String why, long next)
	{
		finish(ticket, null, 0, null, why, next);
	}

	private synchronized void finish(Ticket ticket, @Nullable RelayResponse.FarmRun run, long revision,
									 @Nullable String epoch, @Nullable String why, long next)
	{
		if (active != ticket)
		{
			return;
		}
		active = null;
		call = null;
		if (ticket.holdsGate)
		{
			gate.finishPress(next);
		}
		if (ticket.lifetime != destinations.generation())
		{
			return;
		}
		if (run != null)
		{
			if (!state.acceptPlannedFor(ticket.member, epoch, run, revision, System.currentTimeMillis()))
			{
				said = null;
				return;
			}
		}
		else if (ticket.end && why == null)
		{
			if (!state.acceptFor(ticket.member, epoch, null, revision, System.currentTimeMillis()))
			{
				said = null;
				return;
			}
		}
		/*
		 * A run that lands says nothing: its stops appearing on the card are the answer, and a line
		 * saying so would sit under them for the run's whole life. An End that worked is said, for a
		 * moment, since what it leaves is an empty card.
		 */
		boolean ended = why == null && ticket.end;
		said = new Said(false, why != null ? why : ended ? "Run ended." : null, ticket.lifetime,
						why == null && run != null ? state.generation() : -1, ticket.port, ended);
	}

	/** A connection or profile ended. A cancelled reply must not release a newer press's gate. */
	synchronized void clear()
	{
		Ticket old = active;
		active = null;
		if (call != null)
		{
			call.cancel();
		}
		call = null;
		if (old != null && old.holdsGate)
		{
			gate.finishPress(System.currentTimeMillis() + RoutePress.MIN_GAP_MS);
		}
		said = null;
	}
}
