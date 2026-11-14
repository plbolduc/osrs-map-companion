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

import java.util.*;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.config.Notification;
import static com.pbolduc.mapcompanion.DestinationRoute.standing;

/**
 * What the plugin does on a game tick, and when a session begins and ends, with nothing in it that
 * needs a running RuneLite to make: no sidebar, no overlay, no world map, no chat.
 *
 * <p>{@link MapCompanionPlugin} is the only thing RuneLite puts on its event bus, so it hears every
 * event and hands the ones a tick owns to this. What this cannot do itself, it asks of
 * {@link Surface}, which the plugin is: draw a marker, say a line, colour the status dot.
 *
 * <p><b>Why it is apart from the plugin.</b> The integration harness drives this plugin's real
 * classes against a real map with a made-up game, and it cannot make the plugin, which holds a
 * toolbar and six overlays. A tick it could not call it would have to write out again, without the
 * parts that draw, and a copy drifts: one reader not ended at its logout, one step not taken in its
 * tick, and a story passes on something no player's client does. So there is one tick, one logout
 * and one resume, here, and the harness calls them with a {@link Surface} that draws nothing.
 *
 * <p><b>Threads.</b> As the plugin runs them: the tick, the game's state and every ending on the
 * client thread, a character switch on whichever thread announces it, and what a frame of the
 * stream or a reply to a post is handed on OkHttp's.
 */
@Singleton
class CompanionLoop
{
	/**
	 * What the tick asks of the client's own surfaces, each at its moment in the tick or the
	 * ending that asks. The plugin draws; a harness has nothing to draw on and notes what it was
	 * asked.
	 */
	interface Surface
	{
		/** A tick has begun, before anything is read: the world map's menu and the bank's marks. */
		void begin();

		/** The route has been followed for this tick: its pins, the boats', the arrow, and what to say of an arrival. */
		void drawn(long now);

		/** The status card's colour and sentence, which move together. */
		void setStatus(Health health, String status);

		/** The group's dots from a frame of the stream; null is "not asked", which changes nothing. */
		void group(@Nullable List<RelayResponse.MemberDot> members);

		/** Nobody is drawn: the stream that brought the dots is not delivering. */
		void noGroup();

		/** A destination was taken in, so its marker follows. Any thread. */
		void destination();

		/** A meetup is about to start. */
		void remind(Notification notification, String line);

		/** Whether a reply said this member may rename the group; absent is "may not". OkHttp's thread. */
		void noteGroupOwner(String server, String code, @Nullable Boolean owner);

		/** A session ended: what is drawn of it goes, at its place in {@link CompanionLoop#end}. */
		void ended(SessionEnd end);
	}

	/**
	 * The floor between posts, and between the builds that decide whether there is one to make. The
	 * relay enforces its own; this keeps us under it regardless.
	 */
	private static final long MIN_POST_INTERVAL_MS = 2_000L;
	/**
	 * Sent even when nothing has changed, so a pairing does not look dead while somebody idles —
	 * and so a destination sent from the map is picked up reasonably soon.
	 *
	 * <p>That second reason is what sets the number, and it is a plain trade rather than a clever
	 * one. Directives ride home on the reply to a post, so the map cannot hand one over between
	 * posts however much it would like to: this server only ever speaks when spoken to. The
	 * heartbeat is therefore the whole of the latency for somebody standing still, and six seconds
	 * of it costs an idle client ten requests a minute instead of four — comfortably inside the
	 * relay's own per-member budget, and worth it so "send this to my client" does not sit for a
	 * quarter of a minute looking broken.
	 */
	private static final long HEARTBEAT_MS = 6_000L;

	/**
	 * The heartbeat once a stream is delivering.
	 *
	 * <p>Six seconds was never about liveness — the map does not call anybody stale until thirty —
	 * it was about being the only moment a destination could arrive. A stream takes that job, so
	 * the heartbeat goes back to the one it actually has and an idle client posts three times
	 * a minute instead of ten. Fewer requests and lower latency at once, which is unusual enough
	 * to be worth writing down. Twenty seconds rather than twenty-five: the map calls a member
	 * stale at thirty, and five seconds of slack is one tick of jitter plus a missed retry too
	 * few; twenty leaves ten.
	 */
	private static final long STREAMED_HEARTBEAT_MS = 20_000L;

	/**
	 * "You pick the guard's pocket." — the line that means the loot about to arrive is not a kill.
	 *
	 * <p>The server sends NPC loot for a successful pickpocket through the same event a kill uses,
	 * so a thieving session would otherwise count as thousands of kills and put a dryness figure
	 * under a monster nobody fought. RuneLite's own loot tracker solves it by noting the tick the
	 * chat line lands on and ignoring NPC loot on that tick, and this is that solution and that
	 * pattern, transcribed from {@code LootTrackerPlugin} at the client version this builds against.
	 *
	 * <p>Chat is <em>read</em> here and never sent; there is no field on the wire for it and this
	 * matches one sentence to decide whether to count a kill.
	 */
	static final Pattern PICKPOCKET_MESSAGE =
		Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	@Inject
	Client client;

	@Inject
	MapCompanionConfig config;

	@Inject
	ConfigStore settings;

	@Inject
	Surface surface;

	@Inject
	RelayClient relay;

	@Inject
	SnapshotBuilder snapshotBuilder;

	@Inject
	SnapshotFingerprint snapshotFingerprint;

	@Inject
	XpTracker xpTracker;

	@Inject
	ClueReader clueReader;

	@Inject
	FarmingObserver farmingObserver;

	@Inject
	DiaryReader diaryReader;

	@Inject
	TravelUnlockReader travelUnlockReader;

	@Inject
	StashReader stashReader;

	@Inject
	BoatReader boatReader;

	@Inject
	PortTaskReader portTaskReader;

	@Inject
	DailyReader dailyReader;

	@Inject
	SeaChartReader seaChartReader;

	@Inject
	MortimerOfferReader mortimerOfferReader;

	@Inject
	BirdhouseReader birdhouseReader;

	@Inject
	QuestReader questReader;

	@Inject
	AssignmentReader assignmentReader;

	@Inject
	DeathWatcher deathWatcher;

	@Inject
	DestinationState destinationState;

	@Inject
	DestinationRoute destinationRoute;

	@Inject
	FarmRunState farmRunState;

	@Inject
	SlayerState slayerState;

	@Inject
	FarmRunClient farmRunClient;

	@Inject
	FarmRunRoute farmRunRoute;

	@Inject
	CapabilityEvaluator capabilityEvaluator;

	@Inject
	GroupState groupState;

	@Inject
	RoutePress routePress;

	@Inject
	DirectiveStream directiveStream;

	@Inject
	MemberIdentity identity;

	@Inject
	Pairing pairing;

	@Inject
	LootLog lootLog;

	@Inject
	MilestoneOutbox milestones;

	@Inject
	RewardObserver rewardObserver;

	@Inject
	CollectionItemCatalog collectionItems;

	/** Guarded by rewardObserver, including the handoff from settled observations to the outbox. */
	private RewardContext rewardContext;

	/** Package-private so a test can watch a quest being finished, and the plugin a setting; see {@link #announceCompletions}. */
	final CompletionWatcher completions = new CompletionWatcher();

	/**
	 * Whether the boats' pins should be read again: a boat's varbit moved, a session began, the
	 * setting came on, or the player stepped off a deck. Each read looks every port up in the game's
	 * database, so it is not done every tick for a reading that has not moved. Volatile because a
	 * character switch is announced off the client thread that reads it. The pins are the plugin's to
	 * draw; whether they are due is a session's, and so is ended with it.
	 */
	volatile boolean boatPinsStale = true;

	/** Set when anything worth reporting has happened since the last post. */
	volatile boolean dirty = true;
	private long lastPostAtMs = 0L;
	/** When a snapshot was last built, posted or not: what {@link #MIN_POST_INTERVAL_MS} is measured from. */
	private long lastBuildAtMs = 0L;
	private WorldPoint lastPosition = null;
	/**
	 * Gson of the last snapshot that was actually handed to OkHttp, clocks zeroed.
	 * Null until the first successful enqueue; a fingerprint match with no heartbeat due
	 * is what stops varbit churn posting every two seconds.
	 */
	String lastSentFingerprint = null;

	/** The meetups already announced, so one notification is one notification. Game tick only. */
	final MeetupReminder meetupReminder = new MeetupReminder();

	/**
	 * Set by the map's reply the first time it accepts this installation's RuneLite code.
	 *
	 * <p>A flag rather than a config write on the spot, because that reply arrives on an OkHttp
	 * thread and writing a setting there would raise a {@code ConfigChanged} — and so run this
	 * plugin's own handler — somewhere no other part of it ever runs. The next tick clears the
	 * suffix on the client thread, which is at most a couple of seconds away and is where every
	 * other decision here is made.
	 */
	volatile boolean suffixClaimed = false;

	/**
	 * A secret the map issued on its last reply, waiting for a tick to write it down.
	 *
	 * <p>Held rather than stored where it arrives, for exactly the reason {@link #suffixClaimed} is
	 * a flag: the reply lands on an OkHttp thread, and writing a setting there raises a
	 * {@code ConfigChanged} — and so runs this plugin's own handler — somewhere nothing else here
	 * ever runs.
	 *
	 * <p>It carries the code the post was made under rather than reading the current one when the
	 * tick gets to it. A reply can land after the player has joined somewhere else, and a secret
	 * filed under the wrong group would be a header offered to a group that never issued it, on
	 * every request, for as long as that group lasted.
	 */
	private volatile ForGroup issuedSecret = null;

	/**
	 * The group's name from the map's last reply, waiting for a tick to write it down — the same
	 * handoff as {@link #issuedSecret}, for the same two reasons: a setting is not written on an
	 * OkHttp thread, and the name belongs to the code the post was made under, not to whichever
	 * group the player is in by the time the tick reads it.
	 */
	private volatile ForGroup toldName = null;

	/**
	 * The tick a pickpocket succeeded on. NPC loot arriving on it is thieving, not a kill.
	 *
	 * <p>Starts below every real tick count so the first kill of a session is not mistaken for one.
	 */
	int pickpocketTick = -1;

	/**
	 * The last RuneScape profile this session actually played on, or null before the first login.
	 *
	 * <p>Kept because {@code RuneScapeProfileChanged} reports signing in and signing out as well as
	 * switching character, and only the third of those means the session's state belongs to
	 * somebody else. See {@code MapCompanionPlugin.onRuneScapeProfileChanged}.
	 */
	String lastProfile = null;

	/**
	 * What a session's end forgets, written once for every way one ends; {@link SessionEnd} names
	 * them. One list, because a list for each ending is a list that misses something in one of them.
	 * Here a line says which endings take a thing, so a new piece of state is asked that question
	 * once, in sight of the answers already given, and each reader is told the ending and answers
	 * for itself.
	 *
	 * <p>The connection is the lifetime's: only the plugin stopping resets the relay and takes the
	 * destination down. A logout closes the stream too, which {@link #gameState} does last, beside
	 * the words it leaves on the status card.
	 *
	 * <p>The client thread, and whichever thread announces a character switch.
	 */
	void end(SessionEnd end)
	{
		boolean off = end == SessionEnd.OFF;
		/* Somebody else may be playing next, so nothing held for this character may be delivered to them. */
		boolean another = off || end == SessionEnd.SWITCH;
		if (another)
		{
			/* Old member replies and frames must not deliver a previous character's run. */
			farmRunClient.clear();
			if (off)
			{
				farmRunState.clear();
			}
			else
			{
				farmRunState.bind(null);
			}
			slayerState.clear();
		}
		else
		{
			/* On no world until the next tick names one, so no meetup's world reads as this one meanwhile. */
			groupState.onWorld(0);
			if (end == SessionEnd.HOP)
			{
				/*
				 * The same character, back in seconds: the task cards keep the last settled reading
				 * until the next one settles. Only what the sidebar shows; nothing unsettled is sent.
				 */
				slayerState.hop();
			}
			else
			{
				slayerState.loggedOut();
			}
		}
		if (off)
		{
			lootLog.flush();
			relay.reset();
			stopStream();
			/* Cleared before the pins go, so a stream frame's queued sync cannot redraw one. */
			destinationState.clear();
			destinationRoute.clear();
		}
		/* What is drawn of the session goes here: the boats' pins, and with the lifetime the route's marks. */
		surface.ended(end);
		if (off)
		{
			resetRewardDelivery();
		}
		else if (another)
		{
			milestones.reset();
			completions.reset();
		}
		else
		{
			rewardObserver.reset();
		}
		xpTracker.reset();
		clueReader.reset();
		/** Stored values are about to read zero, or to be another character's; {@link SessionSettle} has the window. */
		for (SettlingReader reader : readers())
		{
			/*
			 * A switch is not told to the patches or the diaries. The logout or hop it came with ended
			 * both, and the patches' history is filed by profile, which that reader sees change itself.
			 */
			if (end != SessionEnd.SWITCH || reader != farmingObserver && reader != diaryReader)
			{
				reader.onSessionEnded(end);
			}
		}
		if (!off)
		{
			boatPinsStale = true;
		}
		if (end != SessionEnd.SWITCH)
		{
			lastPosition = null;
			lastSentFingerprint = null;
		}
		if (another)
		{
			pickpocketTick = -1;
		}
		if (off)
		{
			meetupReminder.clear();
			/*
			 * Forgotten with the lifetime, so the next one's first login is never seen as a switch:
			 * which is why what a switch would clear, a death among it, goes at this ending as well.
			 */
			lastProfile = null;
			suffixClaimed = false;
			issuedSecret = null;
			toldName = null;
		}
	}

	/**
	 * Every reader that believes nothing until a session has settled. Each is told of a session's
	 * start and of every way one ends through this one list, so a reader left out of it is told of
	 * neither. Package-private so a test can hold the list to the readers there are.
	 */
	SettlingReader[] readers()
	{
		return new SettlingReader[]{farmingObserver, diaryReader, travelUnlockReader, stashReader, boatReader, birdhouseReader,
			dailyReader, seaChartReader, mortimerOfferReader, portTaskReader, questReader, assignmentReader, deathWatcher};
	}

	/**
	 * The game's own state changed: a logout or a hop ends the session, a lost connection and a
	 * loading screen break what was being watched, and logging in is something to say. Client thread.
	 */
	void gameState(GameState state)
	{
		boolean leaving = state == GameState.LOGIN_SCREEN || state == GameState.HOPPING;
		if (leaving || state == GameState.CONNECTION_LOST)
		{
			/*
			 * The readings either side of any of these are not one watched stretch of play, so the
			 * next is a new baseline. A lost connection has no other reset of its own: the game comes
			 * back to the same session, and whatever its quest log says then was not seen happening.
			 */
			completions.reset();
		}
		if (leaving)
		{
			/**
			 * Logging out stops the wire. Not "sends a final snapshot saying goodbye" — simply stops,
			 * and the map's own staleness handling takes it from there, which is the same thing it
			 * does when a client crashes or a laptop lid closes.
			 */
			end(state == GameState.HOPPING ? SessionEnd.HOP : SessionEnd.LOGOUT);
			if (state == GameState.LOGIN_SCREEN)
			{
				/**
				 * A hop keeps the stream — it is the same pairing and the same member, and the gap is
				 * seconds. A logout does not: "logging out stops the wire" has to include the
				 * connection that was held open to listen, or the sentence is only half true.
				 */
				stopStream();
			}
			surface.setStatus(Health.WAITING, "Logged out");
		}
		else if (state == GameState.LOGGED_IN)
		{
			dirty = true;
		}
		else
		{
			// Loading gaps invalidate evidence without restarting the login delay.
			farmingObserver.resetSession();
		}
	}

	void onScriptPreFired(ScriptPreFired event)
	{
		if (!RewardObserver.watchesScript(event.getScriptId()))
		{
			return;
		}
		synchronized (rewardObserver)
		{
			RewardContext context = activeRewardContext();
			if (context != null && (context.pets || context.collections))
			{
				rewardObserver.onScriptPreFired(event, context.pets, context.collections);
			}
		}
	}

	/**
	 * Read local game messages for pickpockets and explicitly enabled reward notices.
	 *
	 * <p>No chat line travels on the wire. RewardObserver extracts only a local item/pet observation;
	 * the pickpocket sentence keeps thieving out of the kill log.
	 */
	void onChatMessage(ChatMessage event)
	{
		assignmentReader.onChatMessage(event, System.currentTimeMillis());
		synchronized (rewardObserver)
		{
			RewardContext context = activeRewardContext();
			if (context != null)
			{
				rewardObserver.onChatMessage(event, context.pets, context.collections);
			}
		}
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
		{
			return;
		}
		String message = event.getMessage();
		if (message != null && PICKPOCKET_MESSAGE.matcher(message).matches())
		{
			pickpocketTick = client.getTickCount();
		}
	}

	/**
	 * What a run's player carries and wears, for its bring list, worked out when it can have changed
	 * and not on every tick of the run: each time it matches every carried item's name against every
	 * teleport the map knows. It can have changed when a container did, when the run is another one,
	 * whose flags start unknown, and while the inventory has not arrived, which reads as null. Once
	 * a minute besides, so an item the client could not name at the time is not left out for long.
	 * Client thread; package-private for its test.
	 */
	void readCarried()
	{
		long run = farmRunState.generation();
		if (carriedStale || run != carriedRun || client.getTickCount() % 100 == 0)
		{
			List<String> flags = capabilityEvaluator.evaluate();
			carriedStale = flags == null;
			carriedRun = run;
			farmRunState.carried(flags);
		}
	}

	boolean carriedStale = true;
	private long carriedRun = -1;

	/**
	 * One game tick, whole and in its order. The plugin calls this for the client's, and the
	 * integration harness for one it makes up, so neither has an order of its own to keep. Client thread.
	 */
	void tick()
	{
		long now = System.currentTimeMillis();
		flushRewards(now);
		/* Kills inside the save window; a logout or character change writes them through reload. */
		lootLog.saveIfDue();
		surface.begin();

		trackMovement(now);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			String farmMember = identity.id();
			if (farmMember != null)
			{
				farmRunState.bind(farmMember);
			}
		}
		observeGameState(now);
		announceCompletions(now);
		if (farmRunState.active())
		{
			readCarried();
			farmRunState.observe(farmingObserver.observations(now), birdhouseReader.read(now), standing(client), now);
			/* A port-task run's stops tick by the slots read this tick, and by nothing else. */
			farmRunState.observePorts(settings.on("sharePortTasks") ? portTaskReader.latest() : null);
		}
		farmRunRoute.sync();
		/**
		 * Before the pairing checks: a destination pinned from the Stars tab needs no group, and a
		 * route already received is followed whether or not the next post can be made.
		 */
		RelayResponse.Destination shown = DestinationSceneOverlay.shown(config, destinationState);
		/* This client's member, asked only of a destination that names one: see DestinationRoute.sync. */
		destinationRoute.sync(shown, shown != null && shown.targetMemberId != null ? identity.id() : null);
		if (destinationRoute.hasRoute())
		{
			/* A voyage's rows are ticked by the deck and the boats' ports, read here and never sent. */
			destinationRoute.advance(standing(client),
				DestinationRoute.Sailing.of(WorldTiles.aboard(client), boatReader.read(now)));
		}
		/* Reached, the place has no more to say; a run's stop is the run's to move on from. */
		if (shown != null && !farmRunRoute.owns(shown) && destinationRoute.reaches(standing(client),
			shown.world == null || shown.world == client.getWorld()))
		{
			destinationState.dismiss();
			destinationRoute.sync(null, null);
		}
		surface.drawn(now);
		/*
		 * The task counter, so a kill can be matched to the counter it moved — see TaskKills — and the
		 * collection vocabulary's retry, which drop luck needs whether or not rewards are shared.
		 */
		int tickCount = client.getTickCount();
		lootLog.onTick(tickCount, client.getVarpValue(VarPlayerIds.SLAYER_TARGET), client.getVarpValue(VarPlayerIds.SLAYER_COUNT));
		collectionItems.onGameTick(tickCount);

		if (suffixClaimed)
		{
			/** The map has the binding; the suffix has done its one job. See {@link #suffixClaimed}. */
			suffixClaimed = false;
			pairing.clearSuffix();
		}
		storeIssuedSecret();
		storeToldName();

		String code = Pairing.codeIn(config);
		if (code.isEmpty())
		{
			/*
			 * Two words, because the field this used to point at is directly under it and says what
			 * goes in it (the sidebar's join field, "Invite link or code").
			 */
			surface.setStatus(Health.IDLE, "Not paired");
			return;
		}

		String server = pairing.serverUrl();
		if (!pairing.hasServerConsent(server, code))
		{
			stopStream();
			surface.setStatus(Health.WAITING, "Press Connect to choose " + pairing.serverLabel());
			return;
		}
		String fatal = relay.getFatalError();
		if (fatal != null)
		{
			/**
			 * Stop the stream too. Returning here used to leave it reconnecting against a code
			 * the relay has already refused, which is how a 400 became a connection every ten
			 * seconds forever.
			 */
			stopStream();
			surface.setStatus(Health.ERROR, fatal);
			return;
		}

		/*
		 * A member is one character, and RuneLite names the character's profile a tick or two after
		 * the game says logged in. Until it does there is nobody to post as.
		 */
		String memberId = client.getGameState() == GameState.LOGGED_IN ? identity.id() : null;
		if (memberId == null)
		{
			surface.setStatus(Health.WAITING, "Waiting for login");
			return;
		}

		/**
		 * Keep the stream open alongside the posting. It is idempotent for an unchanged pairing,
		 * so calling it from the tick costs a string comparison and means a dropped connection is
		 * re-established without any other machinery.
		 */
		boolean showGroup = config.showGroupOnWorldMap();
		/*
		 * A friend the player chose to show, or is routing to, needs their dot, and dots come only on
		 * a stream that asks for them; that is the whole of what this adds to the stream.
		 */
		boolean friends = groupState.wantsPositions(now);
		boolean positions = showGroup || friends;
		if (config.showDestination() || positions)
		{
			directiveStream.start(
				server,
				code,
				memberId,
				pairing.secret(server, code, memberId),
				positions,
				directive ->
			{
				/** Each half only while its own setting is on; the stream may be open for the other. */
				acceptDirective(memberId, directive);
				long at = System.currentTimeMillis();
				groupState.dots(directive.members, at);
				if (config.showGroupOnWorldMap())
				{
					surface.group(directive.members);
				}
				else if (directive.members != null)
				{
					/* Only the friends asked for, while the group layer is off. */
					surface.group(groupState.shownDots(directive.members));
				}
				routeToArrivedFriend(directive.members, at);
			});
		}
		else
		{
			/** Turned off mid-session: close the connection rather than leaving it listening. */
			stopStream();
		}
		if (!positions || !directiveStream.isConnected())
		{
			/**
			 * Nobody is drawn from a connection that is not delivering. The next frame of a stream
			 * that reconnects puts them back; until then a dot would be a claim nobody is making.
			 */
			surface.noGroup();
		}
		groupState.stream(positions && directiveStream.isConnected(), directiveStream.lastReadAtMs());

		maybeNotifyMeetup(now);

		/**
		 * Recompute from the last post's outcome even when we are not posting this tick. The
		 * post completes asynchronously, so a failure that lands between posts would otherwise
		 * leave the dot green until the next heartbeat. Three volatile reads; cheap enough
		 * every tick.
		 */
		publishHealth();
		postIfDue(now, server, code);
	}

	/**
	 * The tick's last step: build a snapshot when one is due, and post it when it says something
	 * the map has not heard or the heartbeat is due. Client thread; package-private for its test.
	 */
	void postIfDue(long now, String server, String code)
	{
		long heartbeat = directiveStream.isConnected() ? STREAMED_HEARTBEAT_MS : HEARTBEAT_MS;
		boolean heartbeatDue = now - lastPostAtMs >= heartbeat;
		if (!dirty && !heartbeatDue)
		{
			return;
		}
		/*
		 * The floor is from the last build, which is also the last post when it was sent. A build
		 * whose snapshot said nothing new posts nothing, and in combat every tick marks the plugin
		 * dirty with a varbit or a hitpoint; from the last post alone, each of those ticks built and
		 * serialised a whole snapshot to find that out again.
		 */
		if (now - lastBuildAtMs < MIN_POST_INTERVAL_MS || !relay.isReady(now))
		{
			return;
		}

		/**
		 * Cleared <em>before</em> building, not after. An event that fires during {@code build}
		 * re-arms dirty and the next tick catches it. Clearing after used to drop that event
		 * until the heartbeat.
		 */
		dirty = false;
		lastBuildAtMs = now;
		CompanionSnapshot snapshot = snapshotBuilder.build(now);
		if (snapshot == null)
		{
			return;
		}

		String fingerprint = snapshotFingerprint.of(snapshot);
		if (fingerprint.equals(lastSentFingerprint) && !heartbeatDue)
		{
			return;
		}
		if (!pairing.hasServerConsent(server, code))
		{
			dirty = true;
			return;
		}

		String postedAs = snapshot.member == null ? null : snapshot.member.id;
		if (!relay.post(server, code, pairing.suffix(server, code), pairing.secret(server, code, postedAs),
			identity.installationKey(), snapshot, response ->
	{
		/*
		 * Before the directive, and outside the toggle that guards it. A reader who has turned
		 * destinations off still needs the events they sent to stop being resent, and an
		 * acknowledgement is the only thing that ends a retry.
		 */
		milestones.acknowledge(response.acknowledgedMilestones);
			if (Boolean.TRUE.equals(response.claimed))
			{
				suffixClaimed = true;
			}
			noteIssuedSecret(server, code, postedAs, response.memberSecret);
			noteGroupName(server, code, response.groupName);
			surface.noteGroupOwner(server, code, response.groupOwner);
			acceptDirective(postedAs, response.directive);
		}))
		{
			/** Did not enqueue: another post is in flight, or a fatal is latched. Retry. */
			dirty = true;
			return;
		}

		lastSentFingerprint = fingerprint;
		lastPostAtMs = now;
	}

	/**
	 * Close the held connection and take down everything drawn from it. The group's dots are only
	 * true while the stream that brought them is open, so the two always end together.
	 */
	void stopStream()
	{
		directiveStream.stop();
		groupState.stream(false, 0L);
		surface.noGroup();
	}

	void resetRewardDelivery()
	{
		synchronized (rewardObserver)
		{
			rewardObserver.reset();
			milestones.reset();
			completions.reset();
			rewardContext = null;
		}
	}

	/** Runs {@code action} under the observer monitor, and only while rewards are being shared. */
	void whileRewarding(Runnable action)
	{
		synchronized (rewardObserver)
		{
			if (activeRewardContext() != null)
			{
				action.run();
			}
		}
	}

	/** Caller holds the observer monitor. Config writes become visible before ConfigChanged runs. */
	private RewardContext activeRewardContext()
	{
		RewardContext current = new RewardContext(config, settings);
		if (rewardContext != null && !rewardContext.sameAs(current))
		{
			rewardObserver.reset();
			milestones.reset();
			completions.reset();
		}
		rewardContext = current;
		if (!current.enabled || current.code.isEmpty() || client.getGameState() != GameState.LOGGED_IN)
		{
			rewardObserver.reset();
			return null;
		}
		return current;
	}

	private static final class RewardContext
	{
		final String code;
		final String server;
		final boolean enabled;
		final boolean pets;
		final boolean collections;

		RewardContext(MapCompanionConfig config, ConfigStore settings)
		{
			server = Pairing.normalizeServer(config.relayUrl());
			code = Pairing.codeIn(config);
			enabled = settings.on(MapCompanionConfig.KEY_SHARE_MILESTONES);
			pets = settings.on(MapCompanionConfig.KEY_SHARE_PETS);
			collections = settings.on(MapCompanionConfig.KEY_SHARE_COLLECTION);
		}

		boolean sameAs(RewardContext other)
		{
			return java.util.Objects.equals(server, other.server) && code.equals(other.code)
				   && enabled == other.enabled && pets == other.pets
				   && collections == other.collections;
		}
	}

	/** Settle first, then assign retry identity. No extra posts or independent timers are added. */
	void flushRewards(long now)
	{
		synchronized (rewardObserver)
		{
			RewardContext context = activeRewardContext();
			if (context == null)
			{
				return;
			}
			List<RewardObservation> settled = rewardObserver.onGameTick(now, client.getTickCount());
			if (!context.sameAs(new RewardContext(config, settings)))
			{
				activeRewardContext();
				return;
			}
			for (RewardObservation reward : settled)
			{
				boolean pet = "pet".equals(reward.rewardType)
							  || (reward.itemId != null && collectionItems.isPet(reward.itemId));
				boolean collection = context.collections && Boolean.TRUE.equals(reward.collectionNew);
				if (pet && !context.pets)
				{
					if (!collection)
					{
						continue;
					}
					reward = new RewardObservation("collection_log", reward.item, reward.itemId, null,
												   null, null, true, reward.at);
				}
				else if (!context.collections && reward.collectionNew != null)
				{
					reward = new RewardObservation(reward.rewardType, reward.item, reward.itemId, reward.quantity,
												   reward.sourceKind, reward.sourceName, null, reward.at);
				}
				milestones.reward(reward, now);
				dirty = true;
			}
			// A settings write can happen during enqueue while its event waits for this monitor.
			// Recheck before releasing it, so that write cannot leave an old-context batch queued.
			if (!context.sameAs(new RewardContext(config, settings)))
			{
				activeRewardContext();
			}
		}
	}

	/**
	 * Queue the quests and diary tiers finished since the last tick, while sharing allows it.
	 *
	 * <p>Read from what the snapshot already reads, so watching costs nothing new: the full quest log
	 * with <i>Full quest progress</i>, the travel quests with <i>Quest completion</i> alone, both
	 * cached for thirty seconds, and the diary flags with <i>Achievement diaries</i>. Each needs its
	 * own choice and <i>Drops and levels</i>, which is the choice to announce at all and to have the
	 * group's feed keep it. With either off, nothing is watched, so turning it on starts from a new
	 * baseline instead of announcing what was done meanwhile — see {@link CompletionWatcher}.
	 *
	 * <p>Under the reward lock, like every other way into the outbox, so a withdrawal written on
	 * another thread cannot land between the context check and the queueing.
	 */
	void announceCompletions(long now)
	{
		/* Loading a region is not a boundary; onGameStateChanged resets on the states that are. */
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		synchronized (rewardObserver)
		{
			if (activeRewardContext() == null)
			{
				completions.reset();
				return;
			}
			if (settings.on("shareQuestLog") || settings.on("shareQuests"))
			{
				Map<String, String> quests = settings.on("shareQuestLog") ? questReader.readLog(now)
					: questReader.settled(now) ? questReader.readStates(now) : null;
				for (String quest : completions.quests(quests))
				{
					milestones.quest(quest, now);
					dirty = true;
				}
			}
			else
			{
				completions.forgetQuests();
			}
			if (settings.on("shareDiaries"))
			{
				for (String[] tier : completions.diaries(diaryReader.readTiers(now)))
				{
					milestones.diary(tier[0], tier[1], now);
					dirty = true;
				}
			}
			else
			{
				completions.forgetDiaries();
			}
		}
	}

	/**
	 * Hold a secret the map's reply carried, so the tick can store it.
	 *
	 * <p>Called on an OkHttp thread, which is the whole reason it only assigns a field — see
	 * {@link #issuedSecret}. A reply that says nothing about a secret leaves the held one alone
	 * <em>and</em> leaves the stored one alone, because absent means "keep what you have": the map
	 * stops repeating a secret once it has seen the plugin present one, so reading silence as a
	 * revocation would delete the credential on the first reply after it started working.
	 *
	 * <p>The member is the one the post was made as, not whoever is logged in when the tick files
	 * it: a reply can land after a switch to another account, and filing the main's secret under
	 * the alt would present it on the alt's doors, where the map refuses it.
	 *
	 * <p>Package-private so both halves of the handoff can be driven without a client or a socket.
	 */
	void noteIssuedSecret(String server, String code, @Nullable String memberId, @Nullable String secret)
	{
		if (secret != null && !secret.isEmpty() && memberId != null)
		{
			issuedSecret = new ForGroup(server, code, memberId, secret);
		}
	}

	/**
	 * Write down whatever {@link #noteIssuedSecret} was handed, on the client thread.
	 *
	 * <p>Cleared before the write rather than after, so a reply landing during it is not dropped:
	 * the next tick finds the new one still there. {@link Pairing} does the rest of the deciding —
	 * whether it is the shape of a secret at all, and whether it is worth a config write.
	 */
	void storeIssuedSecret()
	{
		ForGroup issued = issuedSecret;
		if (issued == null)
		{
			return;
		}
		issuedSecret = null;
		pairing.rememberSecret(issued.server, issued.code, issued.member, issued.value);
	}

	/**
	 * Hold the group's name a reply carried, so the tick can store it.
	 *
	 * <p>On an OkHttp thread, so it only assigns a field. Absent leaves both the held name and the
	 * stored one alone: a legacy group and an older map send none, and that means "keep what you
	 * have" here as it does for the secret.
	 */
	void noteGroupName(String server, String code, @Nullable String name)
	{
		if (name != null && !name.isEmpty())
		{
			toldName = new ForGroup(server, code, name);
		}
	}

	/**
	 * Write down whatever {@link #noteGroupName} was handed, on the client thread. {@link Pairing}
	 * decides whether it is a name and whether it differs from the one held, which is what keeps a
	 * name repeated on every reply from being a config write on every tick.
	 */
	void storeToldName()
	{
		ForGroup told = toldName;
		if (told == null)
		{
			return;
		}
		toldName = null;
		pairing.rememberName(told.server, told.code, told.value);
	}

	/**
	 * Something the map sent and the group it was sent for, together, because either alone is a bug
	 * — a secret, or a group's name.
	 *
	 * <p>Two volatile fields would be readable in a state where they disagree — the code of one
	 * group beside the secret of another — and this is exactly the handoff where that happens, since
	 * a reply crosses a group change often enough to be worth designing for rather than hoping
	 * about. One immutable object published through one volatile field cannot be half-read. The
	 * plugin holds one too, for whether this member may rename the group.
	 */
	@AllArgsConstructor
	static final class ForGroup
	{
		final String server;
		final String code;
		/** The member a secret was issued to; null for a group's name, which is everyone's. */
		@Nullable
		private final String member;
		private final String value;

		ForGroup(String server, String code, String value)
		{
			this(server, code, null, value);
		}
	}

	/**
	 * Colour and sentence from the last post's outcome, not from the fact that we are about to
	 * try. Writing LIVE before {@code post} is what made the dot stay green through a dead
	 * network: the attempt was taken as the result.
	 *
	 * <p>Package-private so a unit test can pin the four outcomes without a client.
	 */
	static Health healthFor(boolean everSucceeded, int consecutiveFailures, String fatal)
	{
		if (fatal != null && !fatal.isEmpty())
		{
			return Health.ERROR;
		}
		if (!everSucceeded)
		{
			return Health.WAITING;
		}
		return consecutiveFailures > 0 ? Health.DEGRADED : Health.LIVE;
	}

	private void publishHealth()
	{
		int failures = relay.getConsecutiveFailures();
		boolean live = relay.isEverSucceeded();
		surface.setStatus(healthFor(live, failures, null),
			healthSentence(live, failures, relay.getLastTransientError(), "Connected to " + pairing.serverLabel()));
	}

	/**
	 * The sentence beside {@link #healthFor}'s colour, from the same outcome.
	 *
	 * <p>Once a post has failed it says why, whether or not one ever landed: a map that is down, or
	 * that this network cannot reach, failed the first post as plainly as it fails a later one, and
	 * {@code Connecting…} there would never change while it looked exactly like a connection about
	 * to work. The colour still waits until a post has landed. Package-private for its test.
	 */
	static String healthSentence(boolean everSucceeded, int consecutiveFailures, @Nullable String transientError, String connected)
	{
		if (consecutiveFailures > 0 && transientError != null && !transientError.isEmpty())
		{
			return transientError;
		}
		if (consecutiveFailures > 0)
		{
			return everSucceeded ? "Reconnecting…" : "Connecting…";
		}
		return everSucceeded ? connected : "Connecting…";
	}

	/**
	 * Say once when a meetup is about to start, through RuneLite's own notifier with the setting's own
	 * notification settings, and only with <i>Meetup reminder</i> on. {@link MeetupReminder}
	 * holds the rule, and {@link NotificationChoice} how it reaches a player who is playing.
	 */
	private void maybeNotifyMeetup(long now)
	{
		Notification notification = config.notifyMeetups();
		List<String> due = meetupReminder.due(
			MeetupReminder.of(destinationState.getEvents()), now,
			notification.isEnabled(), config.meetupReminderMinutes());
		for (String line : due)
		{
			surface.remind(notification, line);
		}
	}

	/**
	 * A directive from the map, by the stream or in a post's reply, taken in by each part whose
	 * setting is on: the group's social half, a Slayer partner's task, and the destination with the
	 * farm run that rides along with it. {@code member} is who the directive was written for.
	 */
	private void acceptDirective(@Nullable String member, @Nullable RelayResponse.Directive directive)
	{
		destinationState.acceptSocial(directive, config.showDestination());
		if (settings.on("shareSlayer"))
		{
			slayerState.accept(member, directive, System.currentTimeMillis());
		}
		if (config.showDestination())
		{
			destinationState.accept(directive);
			if (directive != null)
			{
				farmRunState.acceptFor(member, directive.relayEpoch, directive.run, directive.runRevision, System.currentTimeMillis());
			}
			surface.destination();
		}
	}

	/**
	 * A Route to a friend that was waiting for their position, now that a frame brought positions.
	 * Once, to where they stand in it; never into an instance. OkHttp's thread; the press hands
	 * itself to the client thread.
	 */
	private void routeToArrivedFriend(@Nullable List<RelayResponse.MemberDot> members, long now)
	{
		RelayResponse.MemberDot dot = groupState.arrived(members, now);
		if (dot != null && !dot.instance)
		{
			routePress.press(GroupState.routeTarget(dot, destinationState.getRoster()));
		}
	}

	/**
	 * Movement is activity, and it is also the cheapest reason to send a new snapshot.
	 *
	 * <p>Read through {@link WorldTiles}, the tile the snapshot will carry, rather than the world
	 * location. On a boat the world location is a deck tile that stays put while the boat sails, so
	 * a player at sea read as standing still: the voyage marked nothing dirty, the dot moved only
	 * when something else sent a snapshot, and a crossing with no experience in it read as idle.
	 */
	private void trackMovement(long now)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}
		WorldPoint where = WorldTiles.of(client, client.getLocalPlayer());
		if (where == null)
		{
			return;
		}
		if (lastPosition == null || !where.equals(lastPosition))
		{
			lastPosition = where;
			xpTracker.onActivity(now);
			dirty = true;
		}
	}

	/**
	 * Keep the readings of stored game state up to date, once a tick.
	 *
	 * <p>Two things happen here. The settling clocks are told the client is up, which is what makes
	 * a reading believable at all — {@link SessionSettle} says why an eager one would be a lie. And
	 * the farming patches the player is standing among are read.
	 *
	 * <p>The patch read used to run every thirty seconds and take every watched varbit wherever the
	 * player was, which {@link FarmingPatchCatalog} explains cannot be right: a patch's state only
	 * means that patch while you are in its region. Once the read is gated on the region, a tick is
	 * the right cadence and thirty seconds is the wrong one — a player crosses the Falador farm in
	 * less than that, and a replant is a transition between two consecutive readings, which a sweep
	 * sampling twice a minute would show as one number having become another with nothing in
	 * between.
	 *
	 * <p>It costs a region lookup and, outside a farming region, two dozen integer comparisons.
	 */
	private void observeGameState(long now)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		/* For the Group tab, which shows a meetup's world green while this client is on it. */
		groupState.onWorld(client.getWorld());
		/**
		 * Idempotent, and called from here rather than from the login event so that enabling the
		 * plugin while already logged in starts the settling clock too — that path raises no game
		 * state change at all, and without this the readers would wait for a logout to begin.
		 * Ahead of the sharing toggle, because a clock that only runs while a setting is on would
		 * make turning the setting on mid-session read a fresh set of zeroes.
		 */
		for (SettlingReader reader : readers())
		{
			reader.onSessionResumed(now);
		}
		assignmentReader.observe(now);
		/*
		 * Read every tick while the choice is on, for the sidebar as much as the snapshot: the slots
		 * are five varbits and a cached row each. Off, nothing is read or shown.
		 */
		if (settings.on("shareSlayer"))
		{
			mortimerOfferReader.observe(now);
		}
		if (settings.on("sharePortTasks"))
		{
			portTaskReader.read(now);
		}
		else
		{
			portTaskReader.forget();
		}
		/* The sidebar's clue card reads the last reading; a scroll dropped or finished goes with it. */
		clueReader.read();
		/* The death card's gravestone line, from the game's own timer; local, and never sent. */
		deathWatcher.readGravestone(now);
		if (settings.on("shareFarming") || farmRunState.active())
		{
			farmingObserver.observe(now, currentRegionId());
		}
		else
		{
			farmingObserver.resetSession();
		}
	}

	/**
	 * Which map square the player is standing on, or {@code -1} when that cannot be answered.
	 *
	 * <p>The plain world location, not an instance-resolved one: farming varbits are transmitted
	 * for the region the client is actually loaded in, and no watched patch is inside an instance,
	 * so a template tile here would be answering a question nobody asked.
	 */
	private int currentRegionId()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return -1;
		}
		WorldPoint where = local.getWorldLocation();
		return where == null ? -1 : where.getRegionID();
	}
}
