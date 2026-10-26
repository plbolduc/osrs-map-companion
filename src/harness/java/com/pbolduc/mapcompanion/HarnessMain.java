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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This plugin, in a terminal: the real classes, a fake client, and a story read off stdin.
 *
 * <pre>
 *   ./gradlew runHarness --args="--map http://localhost:3000"
 *   {"cmd":"login","name":"Bob","world":444,"x":3222,"y":3218}
 *   {"cmd":"join","text":"Join my OSRS map: http://localhost:3000/map?join=ABC234&amp;i=… — code ABC234"}
 *   {"cmd":"tick","n":6}
 * </pre>
 *
 * <h2>Why this exists</h2>
 *
 * <p>Everything between "the plugin builds a snapshot" and "a reader sees a dot move" has only ever
 * been checked in two halves. The map's own walk ({@code scripts/companion-e2e.mjs}) impersonates a
 * plugin in JavaScript; this repository's unit suite drives the pieces that do not need a client.
 * Neither runs a line of this plugin against a line of that server, so the seam between them — the
 * headers, the claim, the member secret, the refusals, the stream — was covered by two descriptions
 * of a contract and by nothing that had ever seen both sides at once. This is the thing that sees
 * both: the map's own dev server, a real browser driven beside it, and <em>this</em> plugin's real
 * {@link RelayClient}, {@link SnapshotBuilder}, {@link DirectiveStream}, {@link Pairing},
 * {@link JoinCode}, {@link MilestoneOutbox}, {@link LootLog} and readers, wired to
 * {@link HarnessGame} instead of to a game.
 *
 * <h2>What is real and what is not</h2>
 *
 * <p><b>Real:</b> every class named above, its Guice wiring, the whole of what goes on the wire, and
 * every decision made about a reply. <b>Not real:</b> the client (see {@link HarnessGame}), the
 * sidebar, the world-map overlay, and the game's own event bus — commands here stand in for the
 * events RuneLite would raise, and {@link HarnessStore} raises the config change that a real
 * settings write raises.
 *
 * <p><b>And the tick loop is a transcription rather than the original.</b> {@link #tick} is
 * {@code MapCompanionPlugin.onGameTick} written out again, minus the parts that need Swing or the
 * world map. That is the one place this harness can drift from the plugin it is testing, and it is
 * written down here rather than left to be discovered: a change to the order of things in
 * {@code onGameTick} belongs here too. Everything the tick <em>calls</em> is the plugin's own.
 *
 * <h2>The protocol</h2>
 *
 * <p>One JSON object per line in, one per line out, so an orchestrator can wait on a condition
 * rather than on a sleep. Commands: {@code login}, {@code logout}, {@code nick}, {@code join},
 * {@code leave}, {@code tick}, {@code move}, {@code xp}, {@code loot}, {@code settledReward}, {@code varbit},
 * {@code varp}, {@code config}, {@code copyInvite}, {@code state}, {@code quit}. Events: {@code hello},
 * {@code done}, {@code state} (whenever it changes — what the sidebar would print), {@code http}
 * (every request this plugin makes and what came back), {@code posted}, {@code refused},
 * {@code directive}, {@code streamState}, {@code invite}, {@code joined}, {@code error}.
 */
public final class HarnessMain
{
	/** A game tick, which is what the plugin's own cadence is written against. */
	private static final long TICK_MS = 600L;

	/** {@code MapCompanionPlugin}'s own three numbers, transcribed with the loop that uses them. */
	private static final long MIN_POST_INTERVAL_MS = 2_000L;
	private static final long HEARTBEAT_MS = 6_000L;
	private static final long STREAMED_HEARTBEAT_MS = 20_000L;

	/** What the plugin is given, and what RuneLite gives it: a plain one. */
	private final Gson gson = new Gson();
	/**
	 * And a second one for stdout alone, which prints nulls.
	 *
	 * <p>Not the plugin's: {@code serializeNulls} on the injected Gson would change what this
	 * plugin puts on the wire, and an absent field is load-bearing in that protocol — the map reads
	 * "not shared" from a missing key. Here the opposite is wanted, because an orchestrator waiting
	 * for {@code destination} to become null cannot wait for a key that is simply not printed.
	 */
	private final Gson wire = new GsonBuilder().serializeNulls().create();
	private final PrintStream out;
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config;
	private final long tickMs;

	private final RelayClient relay;
	private final ScheduledExecutorService reconnects = Executors.newSingleThreadScheduledExecutor(action ->
	{
		Thread thread = new Thread(action, "companion-harness-reconnect");
		thread.setDaemon(true);
		return thread;
	});
	private final DirectiveStream directiveStream;
	/** Who {@link GroupMarkers} would be drawing, written by the stream's thread. */
	private volatile List<String> groupOnMap = new ArrayList<>();
	private final SnapshotBuilder snapshotBuilder;
	private final SnapshotFingerprint snapshotFingerprint;
	private final DestinationState destinationState;
	private final MilestoneOutbox milestones;
	private final XpTracker xpTracker;
	private final ClueReader clueReader;
	private final DiaryReader diaryReader;
	private final FarmingObserver farmingObserver;
	private final LootLog lootLog;
	private final MemberIdentity identity;
	private final Pairing pairing;

	private volatile boolean dirty = true;
	private long lastPostAtMs = 0L;
	private WorldPoint lastPosition = null;
	private String lastSentFingerprint = null;
	private volatile boolean suffixClaimed = false;
	private volatile String issuedSecretCode = null;
	private volatile String issuedSecret = null;
	private volatile String issuedSecretMember = null;
	/** The group's name from the last reply and the code it answered, as the plugin holds them. */
	private volatile String toldNameCode = null;
	private volatile String toldName = null;

	private String status = "Not paired";
	private Health health = Health.IDLE;
	private String lastStateLine = null;
	private boolean streamConnected = false;
	private volatile boolean running = true;

	public static void main(String[] args) throws Exception
	{
		Map<String, String> flags = flags(args);
		String map = flags.getOrDefault("map", "http://localhost:3000");
		long tickMs = Long.parseLong(flags.getOrDefault("tick-ms", String.valueOf(TICK_MS)));
		new HarnessMain(map, tickMs, System.out).run();
	}

	private HarnessMain(String mapUrl, long tickMs, PrintStream out)
	{
		this.out = out;
		this.tickMs = tickMs;
		this.config = new HarnessConfig(store, mapUrl);

		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(watcher()).build();
		/**
		 * Guice, because that is how this plugin is really assembled: every constructor here is the
		 * one RuneLite's own injector calls. The four instances bound by hand are the four classes
		 * that keep their own state in the settings — see {@link ConfigStore}, which is the seam
		 * that makes them constructible at all outside a client.
		 */
		Injector injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(game.client());
				bind(Gson.class).toInstance(gson);
				bind(OkHttpClient.class).toInstance(http);
				bind(ScheduledExecutorService.class).toInstance(reconnects);
				bind(MapCompanionConfig.class).toInstance(config);
				bind(MemberIdentity.class).toInstance(new MemberIdentity(store));
				bind(Pairing.class).toInstance(new Pairing(store, gson, config::relayUrl));
				bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
				bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
				bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
			}
		});

		this.relay = injector.getInstance(RelayClient.class);
		this.directiveStream = injector.getInstance(DirectiveStream.class);
		this.snapshotBuilder = injector.getInstance(SnapshotBuilder.class);
		this.snapshotFingerprint = injector.getInstance(SnapshotFingerprint.class);
		this.destinationState = injector.getInstance(DestinationState.class);
		this.milestones = injector.getInstance(MilestoneOutbox.class);
		this.xpTracker = injector.getInstance(XpTracker.class);
		this.clueReader = injector.getInstance(ClueReader.class);
		this.diaryReader = injector.getInstance(DiaryReader.class);
		this.farmingObserver = injector.getInstance(FarmingObserver.class);
		this.lootLog = injector.getInstance(LootLog.class);
		this.identity = injector.getInstance(MemberIdentity.class);
		this.pairing = injector.getInstance(Pairing.class);

		/** The event bus, for the one event this plugin acts on — see {@link HarnessStore}. */
		store.onChanged(this::onConfigChanged);
	}

	/**
	 * Every request this plugin makes, as it goes out and as it comes back.
	 *
	 * <p>An interceptor rather than anything inside the plugin, because {@link RelayClient} is
	 * deliberately silent about the words a refusal carries — it turns every failure into a sentence
	 * for the sidebar or into a null for the invite button — and a story that wants to assert
	 * <i>which</i> refusal happened has to read the response. This is the seam that class's own
	 * docblock names: the {@link OkHttpClient} is injected, so a test can watch what it sends.
	 *
	 * <p>{@code peekBody} rather than {@code body().string()}, and only for a refusal: reading the
	 * body would consume it out from under the plugin, and a 200 here may be a stream that stays
	 * open for minutes.
	 */
	private Interceptor watcher()
	{
		return chain ->
		{
			Request request = chain.request();
			Response response = chain.proceed(request);
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("method", request.method());
			event.put("path", request.url().encodedPath());
			event.put("status", response.code());
			event.put("suffixHeader", request.header("X-Companion-Suffix") != null);
			event.put("secretHeader", request.header("X-Companion-Member-Secret") != null);
			if (!response.isSuccessful())
			{
				event.put("reason", reasonOf(response));
			}
			emit("http", event);
			return response;
		};
	}

	@Nullable
	private String reasonOf(Response response)
	{
		try
		{
			String body = response.peekBody(4_096).string();
			JsonObject parsed = gson.fromJson(body, JsonObject.class);
			return parsed != null && parsed.has("reason") ? parsed.get("reason").getAsString() : null;
		}
		catch (IOException | RuntimeException e)
		{
			return null;
		}
	}

	private void run() throws IOException
	{
		Map<String, Object> hello = new LinkedHashMap<>();
		hello.put("map", config.relayUrl());
		hello.put("version", CompanionVersion.USER_AGENT);
		hello.put("memberId", identity.id());
		emit("hello", hello);
		publishState();

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String line;
		while (running && (line = in.readLine()) != null)
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}
			JsonObject command;
			try
			{
				command = gson.fromJson(trimmed, JsonObject.class);
			}
			catch (RuntimeException e)
			{
				command = null;
			}
			if (command == null)
			{
				emit("error", one("said", "that line is not a JSON object"));
				continue;
			}
			try
			{
				handle(command);
			}
			catch (RuntimeException e)
			{
				Map<String, Object> failure = new LinkedHashMap<>();
				failure.put("cmd", text(command, "cmd", ""));
				failure.put("said", String.valueOf(e));
				emit("error", failure);
			}
			publishState();
			Map<String, Object> done = new LinkedHashMap<>();
			done.put("cmd", text(command, "cmd", ""));
			if (command.has("id"))
			{
				done.put("id", command.get("id").getAsString());
			}
			emit("done", done);
		}
		shutDown();
	}

	private void handle(JsonObject command)
	{
		String cmd = text(command, "cmd", "");
		switch (cmd)
		{
			case "login":
				game.login(
					text(command, "name", "Bob"),
					number(command, "world", 444),
					number(command, "x", 3222),
					number(command, "y", 3218),
					number(command, "plane", 0));
				/** What {@code onGameStateChanged} does for LOGGED_IN, and nothing more. */
				dirty = true;
				break;
			case "logout":
				logout();
				break;
			case "nick":
				config.nickname(text(command, "nick", "Me"));
				dirty = true;
				break;
			case "join":
				join(text(command, "text", ""));
				break;
			case "leave":
				/** Exactly what the sidebar's <i>Leave group</i> does, in its order. */
				pairing.leave();
				identity.forget();
				destinationState.clear();
				break;
			case "tick":
				for (int i = 0; i < number(command, "n", 1) && running; i++)
				{
					game.tick();
					tick();
					publishState();
					sleep(tickMs);
				}
				break;
			case "move":
				game.moveTo(number(command, "x", 3222), number(command, "y", 3218), number(command, "plane", 0));
				break;
			case "xp":
				xp(command);
				break;
			case "loot":
				loot(command);
				break;
			case "settledReward":
				settledReward(command);
				break;
			case "varbit":
				game.setVarbit(number(command, "id", 0), number(command, "value", 0));
				dirty = true;
				break;
			case "varp":
				game.setVarp(number(command, "id", 0), number(command, "value", 0));
				dirty = true;
				break;
			case "config":
				config.set(text(command, "key", ""), command.has("value") && command.get("value").getAsBoolean());
				onConfigChanged(text(command, "key", ""));
				dirty = true;
				break;
			case "copyInvite":
				copyInvite();
				break;
			case "state":
				lastStateLine = null;
				break;
			case "quit":
				running = false;
				break;
			default:
				emit("error", one("said", "no such command: " + cmd));
				break;
		}
	}

	/** {@code MapCompanionPlugin.onGameStateChanged}, for the one state this harness can be put in. */
	private void logout()
	{
		xpTracker.reset();
		clueReader.reset();
		farmingObserver.onSessionEnded();
		diaryReader.onSessionEnded();
		lastPosition = null;
		lastSentFingerprint = null;
		directiveStream.stop();
		game.logout();
		setStatus(Health.WAITING, "Logged out");
	}

	/** The sidebar's Connect button: whatever was pasted, through the real {@link JoinCode}. */
	private void join(String pasted)
	{
		JoinCode joined = JoinCode.parse(pasted);
		if (joined == null)
		{
			emit("joined", one("found", null));
			return;
		}
		pairing.join(joined);
		pairing.approveServer();
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("found", joined.code);
		event.put("suffix", joined.suffix != null);
		emit("joined", event);
	}

	/** {@code MapCompanionPlugin.onStatChanged}, which is where a level-up becomes an announcement. */
	private void xp(JsonObject command)
	{
		Skill skill = Skill.valueOf(text(command, "skill", "MINING").toUpperCase(java.util.Locale.ROOT));
		int level = number(command, "level", game.level(skill));
		long now = System.currentTimeMillis();
		xpTracker.onXpChanged(skill, number(command, "xp", 0), now);
		game.setLevel(skill, level);
		boolean gained = xpTracker.observeLevel(skill, level);
		if (gained && sharingMilestones())
		{
			milestones.levelUp(XpTracker.displayName(skill), level, now);
		}
		dirty = true;
	}

	/** Legacy NPC protocol fixture plus the real local kill log; not the new reward observer. */
	private void loot(JsonObject command)
	{
		String npc = text(command, "npc", "Zulrah");
		String item = text(command, "item", "Tanzanite fang");
		int itemId = number(command, "itemId", 12_922);
		int quantity = number(command, "quantity", 1);
		game.nameItem(itemId, item);
		List<ItemStack> items = new ArrayList<>();
		items.add(new ItemStack(itemId, quantity));
		List<LootLog.Notable> notable = lootLog.record(new ServerNpcLoot(game.npc(npc), items));
		if (sharingMilestones())
		{
			long now = System.currentTimeMillis();
			for (LootLog.Notable drop : notable)
			{
				milestones.npcDrop(drop.npc, drop.item, drop.itemId, drop.quantity, now);
			}
		}
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("npc", npc);
		event.put("item", item);
		event.put("notable", notable.size());
		event.put("pending", milestones.pendingCount());
		emit("loot", event);
		dirty = true;
	}

	/**
	 * Wire-only reward fixture. JUnit drives the real cache/event observers; this deliberately
	 * begins after their settling boundary to test a real outbox, snapshot and relay together.
	 */
	private void settledReward(JsonObject command)
	{
		long now = System.currentTimeMillis();
		String type = text(command, "rewardType", "item");
		if (sharingMilestones() && (!"pet".equals(type) || config.sharePets())
			&& (!"collection_log".equals(type) || config.shareCollectionLog()))
		{
			JsonObject source = command.has("source") && command.get("source").isJsonObject()
								? command.getAsJsonObject("source") : new JsonObject();
			Integer id = command.has("itemId") && !command.get("itemId").isJsonNull()
						 ? command.get("itemId").getAsInt() : null;
			Integer quantity = command.has("quantity") && !command.get("quantity").isJsonNull()
							   ? command.get("quantity").getAsInt() : null;
			Boolean collection = config.shareCollectionLog() && command.has("collectionNew")
								 && command.get("collectionNew").getAsBoolean() ? true : null;
			milestones.reward(new RewardObservation(type, text(command, "item", "Pet"), id, quantity,
													text(source, "kind", null), text(source, "name", null), collection, now), now);
			dirty = true;
		}
		emit("settledReward", milestones.take(now));
	}

	private boolean sharingMilestones()
	{
		return config.shareMilestones() && game.gameState() == GameState.LOGGED_IN
			   && !config.pairingCode().trim().isEmpty();
	}

	/** The sidebar's <i>Copy invite</i>, including the plain-text message it falls back to. */
	private void copyInvite()
	{
		String code = config.pairingCode().trim();
		if (code.isEmpty())
		{
			emit("invite", one("ok", false));
			return;
		}
		relay.requestInvite(config.relayUrl(), code, identity.id(), pairing.secret(identity.id()), invite ->
		{
			boolean linked = invite != null && invite.url != null && !invite.url.isEmpty();
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("ok", linked);
			event.put("text", linked
					  ? "Join my OSRS map: " + invite.url + " — code " + code
					  : "Join my OSRS map: " + config.relayUrl() + "/map?join=" + code + "&task=companion"
					  + " — then install the \"OSRS Map Companion\" RuneLite plugin and enter code " + code);
			emit("invite", event);
		});
	}

	/**
	 * {@code MapCompanionPlugin.onConfigChanged}, which is the whole of what a settings write means.
	 */
	private void onConfigChanged(String key)
	{
		if (MapCompanionConfig.KEY_SHARE_MILESTONES.equals(key)
			|| MapCompanionConfig.KEY_SHARE_PETS.equals(key)
			|| MapCompanionConfig.KEY_SHARE_COLLECTION.equals(key))
		{
			milestones.reset();
		}
		if (MapCompanionConfig.KEY_PAIRING_CODE.equals(key) || MapCompanionConfig.KEY_RELAY_URL.equals(key))
		{
			relay.reset();
			directiveStream.stop();
			lastSentFingerprint = null;
			if (MapCompanionConfig.KEY_PAIRING_CODE.equals(key))
			{
				/** {@code onGroupChanged}: neither a suffix nor an unsent drop crosses a group. */
				pairing.clearSuffix();
				milestones.reset();
			}
		}
		else if (MapCompanionConfig.KEY_PAIRING_SUFFIX.equals(key))
		{
			relay.clearRefusal();
		}
		dirty = true;
	}

	/**
	 * One game tick — {@code MapCompanionPlugin.onGameTick}, transcribed.
	 *
	 * <p>The parts left out are the ones that draw: the world-map marker, the scene overlay, the
	 * world hopper and the meetup notification. Everything that decides what goes on the wire is
	 * here, in the order the plugin does it.
	 */
	private void tick()
	{
		long now = System.currentTimeMillis();

		trackMovement(now);
		observeGameState(now);

		if (suffixClaimed)
		{
			suffixClaimed = false;
			pairing.clearSuffix();
		}
		storeIssuedSecret();
		storeToldName();

		String code = config.pairingCode().trim();
		if (code.isEmpty())
		{
			setStatus(Health.IDLE, "Not paired");
			return;
		}

		if (!pairing.hasServerConsent())
		{
			directiveStream.stop();
			setStatus(Health.WAITING, "Press Connect to choose " + pairing.serverLabel());
			return;
		}

		String fatal = relay.getFatalError();
		if (fatal != null)
		{
			directiveStream.stop();
			setStatus(Health.ERROR, fatal);
			return;
		}

		if (game.gameState() != GameState.LOGGED_IN)
		{
			setStatus(Health.WAITING, "Waiting for login");
			return;
		}

		boolean showGroup = config.showGroupOnWorldMap();
		if (config.showDestination() || showGroup)
		{
			directiveStream.start(config.relayUrl(), code, identity.id(), pairing.secret(identity.id()), showGroup, directive ->
			{
				if (config.showDestination())
				{
					destinationState.accept(directive);
					emit("directive", describe(directive));
				}
				if (config.showGroupOnWorldMap() && directive.members != null)
				{
					/** The names the world map would draw; the harness has no world map to draw them on. */
					List<String> names = new ArrayList<>();
					for (RelayResponse.MemberDot dot : directive.members)
					{
						names.add(MapPin.safe(dot.name));
					}
					groupOnMap = names;
				}
				/**
				 * And what the sidebar now says, from the thread the stream delivered on. A
				 * directive that arrives between ticks is the whole point of the stream, so a
				 * state published only by the tick loop would report it a heartbeat late — or,
				 * for a reader waiting on the line rather than on a clock, not at all.
				 */
				publishState();
			});
		}
		else
		{
			directiveStream.stop();
		}
		if (!showGroup || !directiveStream.isConnected())
		{
			groupOnMap = new ArrayList<>();
		}

		if (directiveStream.isConnected() != streamConnected)
		{
			streamConnected = directiveStream.isConnected();
			emit("streamState", one("connected", streamConnected));
		}

		publishHealth();

		long heartbeat = directiveStream.isConnected() ? STREAMED_HEARTBEAT_MS : HEARTBEAT_MS;
		boolean heartbeatDue = now - lastPostAtMs >= heartbeat;
		if (!dirty && !heartbeatDue)
		{
			return;
		}
		if (now - lastPostAtMs < MIN_POST_INTERVAL_MS || !relay.isReady(now))
		{
			return;
		}

		dirty = false;
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

		/* As the plugin does: the member this post is made as, with this RuneLite's key. */
		String postedAs = snapshot.member == null ? null : snapshot.member.id;
		if (!relay.post(config.relayUrl(), code, pairing.suffix(), pairing.secret(postedAs), identity.installationKey(),
			snapshot, response ->
	{
		milestones.acknowledge(response.acknowledgedMilestones);
			if (Boolean.TRUE.equals(response.claimed))
			{
				suffixClaimed = true;
			}
			if (response.memberSecret != null && !response.memberSecret.isEmpty())
			{
				issuedSecretCode = code;
				issuedSecretMember = postedAs;
				issuedSecret = response.memberSecret;
			}
			if (response.groupName != null && !response.groupName.isEmpty())
			{
				toldNameCode = code;
				toldName = response.groupName;
			}
			if (config.showDestination())
			{
				destinationState.accept(response.directive);
			}
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("claimed", Boolean.TRUE.equals(response.claimed));
			event.put("memberSecret", response.memberSecret != null && !response.memberSecret.isEmpty());
			event.put("groupName", response.groupName);
			event.put("acknowledged", response.acknowledgedMilestones == null
					  ? new ArrayList<String>()
					  : new ArrayList<>(response.acknowledgedMilestones));
			event.put("directive", describe(response.directive == null ? null : response.directive));
			emit("posted", event);
			publishState();
		}))
		{
			dirty = true;
			return;
		}

		lastSentFingerprint = fingerprint;
		lastPostAtMs = now;
	}

	/** {@code MapCompanionPlugin.storeIssuedSecret}, on the thread that is allowed to write settings. */
	private void storeIssuedSecret()
	{
		String secret = issuedSecret;
		String code = issuedSecretCode;
		String member = issuedSecretMember;
		if (secret == null || code == null)
		{
			return;
		}
		issuedSecret = null;
		issuedSecretCode = null;
		issuedSecretMember = null;
		pairing.rememberSecret(code, member, secret);
	}

	/** {@code MapCompanionPlugin.storeToldName}, on the same thread and for the same reason. */
	private void storeToldName()
	{
		String name = toldName;
		String code = toldNameCode;
		if (name == null || code == null)
		{
			return;
		}
		toldName = null;
		toldNameCode = null;
		pairing.rememberName(code, name);
	}

	private void trackMovement(long now)
	{
		if (game.gameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WorldPoint where = game.where();
		if (lastPosition == null || !where.equals(lastPosition))
		{
			lastPosition = where;
			xpTracker.onActivity(now);
			dirty = true;
		}
	}

	private void observeGameState(long now)
	{
		if (game.gameState() != GameState.LOGGED_IN)
		{
			return;
		}
		farmingObserver.onSessionResumed(now);
		diaryReader.onSessionResumed(now);
		if (config.shareFarming())
		{
			farmingObserver.observe(now, game.where().getRegionID());
		}
		else
		{
			farmingObserver.resetSession();
		}
	}

	private void publishHealth()
	{
		int failures = relay.getConsecutiveFailures();
		boolean live = relay.isEverSucceeded();
		String transientError = relay.getLastTransientError();
		Health next = MapCompanionPlugin.healthFor(live, failures, null);
		String sentence;
		if (failures > 0 && live)
		{
			sentence = transientError != null && !transientError.isEmpty() ? transientError : "Reconnecting…";
		}
		else if (live)
		{
			sentence = "Connected";
		}
		else
		{
			sentence = "Connecting…";
		}
		setStatus(next, sentence);
	}

	private void setStatus(Health health, String status)
	{
		this.health = health;
		this.status = status;
	}

	/**
	 * What the sidebar would print, emitted only when it changes.
	 *
	 * <p>The panel is Swing and is not built here, so this is its content rather than its pixels:
	 * the status line and its colour, the pairing, whether a suffix is still being sent and whether
	 * a secret is held, the groups the menu offers and their names, and the destination and meetup
	 * the panel draws under them.
	 */
	private synchronized void publishState()
	{
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("status", status);
		state.put("health", health.name());
		String code = config.pairingCode().trim();
		state.put("code", code.isEmpty() ? null : JoinCode.forDisplay(code));
		state.put("suffix", pairing.suffix() != null);
		state.put("secret", pairing.secret(identity.id()) != null);
		state.put("knownCodes", new ArrayList<>(pairing.knownCodes()));
		/* A key of its own rather than a new shape for knownCodes, which readers already parse. */
		Map<String, String> names = new LinkedHashMap<>();
		for (String known : pairing.knownCodes())
		{
			String name = pairing.name(known);
			if (name != null)
			{
				names.put(known, name);
			}
		}
		state.put("groupNames", names);
		state.put("stream", directiveStream.isConnected());
		state.put("groupOnMap", groupOnMap);
		state.put("pendingMilestones", milestones.pendingCount());
		RelayResponse.Destination destination = destinationState.getDestination();
		state.put("destination", destination == null ? null : destination.name);
		RelayResponse.Event event = destinationState.getEvent();
		state.put("meetup", event == null ? null : event.title);
		String line = wire.toJson(state);
		if (line.equals(lastStateLine))
		{
			return;
		}
		lastStateLine = line;
		emit("state", state);
	}

	@Nullable
	private Map<String, Object> describe(@Nullable RelayResponse.Directive directive)
	{
		if (directive == null)
		{
			return null;
		}
		Map<String, Object> described = new LinkedHashMap<>();
		if (directive.destination != null)
		{
			Map<String, Object> destination = new LinkedHashMap<>();
			destination.put("name", directive.destination.name);
			destination.put("x", directive.destination.x);
			destination.put("y", directive.destination.y);
			destination.put("plane", directive.destination.plane);
			destination.put("world", directive.destination.world);
			described.put("destination", destination);
		}
		if (directive.event != null)
		{
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("title", directive.event.title);
			event.put("x", directive.event.x);
			event.put("y", directive.event.y);
			event.put("startsAtMs", directive.event.startsAtMs);
			described.put("event", event);
		}
		return described;
	}

	private void shutDown()
	{
		directiveStream.stop();
		reconnects.shutdown();
		out.flush();
	}

	/** One line of JSON, whichever thread produced it. */
	private synchronized void emit(String event, @Nullable Object payload)
	{
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("event", event);
		line.put("atMs", System.currentTimeMillis());
		line.put("body", payload);
		out.println(wire.toJson(line));
		out.flush();
	}

	private static Map<String, Object> one(String key, @Nullable Object value)
	{
		Map<String, Object> map = new LinkedHashMap<>();
		map.put(key, value);
		return map;
	}

	private static String text(JsonObject object, String key, String fallback)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static int number(JsonObject object, String key, int fallback)
	{
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
	}

	private static void sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	/** {@code --map http://…} and {@code --map=http://…} alike, because both get typed. */
	private static Map<String, String> flags(String[] args)
	{
		Map<String, String> flags = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if (!arg.startsWith("--"))
			{
				continue;
			}
			int equals = arg.indexOf('=');
			if (equals > 0)
			{
				flags.put(arg.substring(2, equals), arg.substring(equals + 1));
			}
			else if (i + 1 < args.length && !args[i + 1].startsWith("--"))
			{
				flags.put(arg.substring(2), args[++i]);
			}
			else
			{
				flags.put(arg.substring(2), "true");
			}
		}
		return flags;
	}
}
