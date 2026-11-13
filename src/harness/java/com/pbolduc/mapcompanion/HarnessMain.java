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
import com.google.inject.Provides;
import com.google.inject.Singleton;
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
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.config.Notification;
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
 * {@link CompanionLoop}, {@link RelayClient}, {@link SnapshotBuilder}, {@link DirectiveStream},
 * {@link Pairing}, {@link JoinCode}, {@link MilestoneOutbox}, {@link LootLog} and readers, wired to
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
 * <p><b>The tick, the logout and the resume are the plugin's own.</b> {@link CompanionLoop} holds
 * them, and the plugin and this harness call the same methods: a {@code tick} command is
 * {@link CompanionLoop#tick}, a {@code logout} is {@link CompanionLoop#gameState} at the login
 * screen. This harness is the loop's {@link CompanionLoop.Surface}, the one that draws nothing, so
 * a step the plugin takes on a tick cannot be missing here, and {@code HarnessLoopTest} fails if a
 * copy of one is written here again.
 *
 * <p><b>What is still written out again.</b> The commands that stand in for the plugin's other
 * handlers: {@link #xp} for {@code onStatChanged}, {@link #onConfigChanged} for its namesake and
 * the group change under it, and {@code leave} for the sidebar's button. They are short, and each
 * says which handler it follows; a change to one of those handlers belongs here too.
 *
 * <h2>The protocol</h2>
 *
 * <p>One JSON object per line in, one per line out, so an orchestrator can wait on a condition
 * rather than on a sleep. Commands: {@code login}, {@code logout}, {@code nick}, {@code connect}
 * (the sidebar's Connect with no group, optionally with a {@code name}), {@code join}, {@code openMap},
 * {@code leave}, {@code tick}, {@code move}, {@code xp}, {@code loot}, {@code settledReward}, {@code varbit},
 * {@code varp}, {@code quest}, {@code config}, {@code copyInvite}, {@code state}, {@code quit}. Events: {@code hello},
 * {@code done}, {@code state} (whenever it changes — what the sidebar would print), {@code http}
 * (every request this plugin makes and what came back), {@code posted}, {@code refused},
 * {@code directive}, {@code streamState}, {@code invite}, {@code joined}, {@code link}, {@code error}.
 */
public final class HarnessMain implements CompanionLoop.Surface
{
	/** A game tick, which is what the plugin's own cadence is written against. */
	private static final long TICK_MS = 600L;

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
	/**
	 * The plugin's tick, its endings and the state they keep, made by the same injector that makes
	 * the plugin's. Package-private so a test can ask its readers what a command did to them.
	 */
	final CompanionLoop loop;
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
	private final DestinationState destinationState;
	private final FarmRunState farmRunState;
	private final SlayerState slayerState;
	private final RoutePress routePress;
	private final FarmRunClient farmRunClient;
	private final MilestoneOutbox milestones;
	private final XpTracker xpTracker;
	private final QuestReader questReader;
	private final LootLog lootLog;
	private final MemberIdentity identity;
	private final Pairing pairing;
	private final ConnectClient connectClient;

	/* Written by the tick and read by whichever thread prints the state: the stream's, or a reply's. */
	private volatile String status = "Not paired";
	private volatile Health health = Health.IDLE;
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

	/** Package-private so a test can make one, hand it commands and read what it printed. */
	HarnessMain(String mapUrl, long tickMs, PrintStream out)
	{
		this.out = out;
		this.tickMs = tickMs;
		this.config = new HarnessConfig(store, mapUrl);

		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(watcher()).build();
		/*
		 * The two doors the map's words come in through, each with this harness listening behind the
		 * plugin: what the loop does with a reply or a frame is the loop's, and a story hears of it
		 * only once the loop has taken it in.
		 */
		RelayClient posts = new RelayClient(http, gson)
		{
			@Override
			public synchronized boolean post(String baseUrl, String code, @Nullable String suffix, @Nullable String memberSecret,
				@Nullable String installationKey, CompanionSnapshot snapshot, Consumer<RelayResponse> onDirective)
			{
				return super.post(baseUrl, code, suffix, memberSecret, installationKey, snapshot, response ->
				{
					onDirective.accept(response);
					posted(response);
				});
			}
		};
		DirectiveStream frames = new DirectiveStream(http, gson, reconnects)
		{
			@Override
			public synchronized void start(String baseUrl, String code, String memberId, @Nullable String memberSecret,
				boolean withMembers, Consumer<? super RelayResponse.StreamDirective> onDirective)
			{
				super.start(baseUrl, code, memberId, memberSecret, withMembers, directive ->
				{
					onDirective.accept(directive);
					streamed(directive);
				});
			}
		};
		/**
		 * Guice, because that is how this plugin is really assembled: every constructor here is the
		 * one RuneLite's own injector calls. The four instances bound by hand are the four classes
		 * that keep their own state in the settings — see {@link ConfigStore}, which is the seam
		 * that makes them constructible at all outside a client. The loop's surface is this harness,
		 * and the reward observer is made as the plugin's own provider makes it.
		 */
		Injector injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(game.client());
				bind(net.runelite.client.callback.ClientThread.class).toInstance(new net.runelite.client.callback.ClientThread()
				{
					@Override
					public void invokeLater(Runnable action)
					{
						action.run();
					}
				});
				bind(Gson.class).toInstance(gson);
				bind(OkHttpClient.class).toInstance(http);
				bind(ScheduledExecutorService.class).toInstance(reconnects);
				bind(RelayClient.class).toInstance(posts);
				bind(DirectiveStream.class).toInstance(frames);
				bind(CompanionLoop.Surface.class).toInstance(HarnessMain.this);
				bind(MapCompanionConfig.class).toInstance(config);
				bind(ConfigStore.class).toInstance(store);
				bind(MemberIdentity.class).toInstance(new MemberIdentity(store));
				bind(Pairing.class).toInstance(new Pairing(store, gson, config::relayUrl));
				bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
				bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
				bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
			}

			/** {@code MapCompanionPlugin.provideRewardObserver}, which this injector has no plugin to ask for. */
			@Provides
			@Singleton
			RewardObserver provideRewardObserver(Client client, CollectionItemCatalog catalog, RewardPolicy policy)
			{
				return new RewardObserver(client, catalog, policy::includes);
			}
		});

		/* The loop's own collaborators, so a command here and a tick there can only mean the same object. */
		this.loop = injector.getInstance(CompanionLoop.class);
		this.relay = loop.relay;
		this.directiveStream = loop.directiveStream;
		this.destinationState = loop.destinationState;
		this.farmRunState = loop.farmRunState;
		this.slayerState = loop.slayerState;
		this.routePress = loop.routePress;
		this.farmRunClient = loop.farmRunClient;
		this.milestones = loop.milestones;
		this.xpTracker = loop.xpTracker;
		this.questReader = loop.questReader;
		this.lootLog = loop.lootLog;
		this.identity = loop.identity;
		this.pairing = loop.pairing;
		this.connectClient = injector.getInstance(ConnectClient.class);

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

	/** One command, as {@link #run} reads it off stdin. Package-private so a test can hand one over. */
	void handle(JsonObject command)
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
				/** The state change the client raises, less the update's chat line, which the plugin says itself. */
				loop.gameState(GameState.LOGGED_IN);
				break;
			case "logout":
				logout();
				break;
			case "nick":
				config.nickname(text(command, "nick", "Me"));
				loop.dirty = true;
				break;
			case "join":
				join(text(command, "text", ""));
				break;
			case "connect":
				connect();
				break;
			case "openMap":
				openMap();
				break;
			case "leave":
				/** Exactly what the sidebar's <i>Leave group</i> does, in its order. */
				pairing.leave();
				identity.forget();
				farmRunClient.clear();
				farmRunState.clear();
				slayerState.clear();
				destinationState.clear();
				break;
			case "tick":
				for (int i = 0; i < number(command, "n", 1) && running; i++)
				{
					game.tick();
					loop.tick();
					if (directiveStream.isConnected() != streamConnected)
					{
						streamConnected = directiveStream.isConnected();
						emit("streamState", one("connected", streamConnected));
					}
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
				loop.dirty = true;
				break;
			case "varp":
				game.setVarp(number(command, "id", 0), number(command, "value", 0));
				loop.dirty = true;
				break;
			case "quest":
				game.setQuest(Quest.valueOf(text(command, "quest", "DRAGON_SLAYER_I")),
					QuestState.valueOf(text(command, "state", "FINISHED")));
				/*
				 * The full log is read at most every thirty seconds, which a story should not sit
				 * through; JUnit holds the cache. Dropping it is the only thing here the plugin does
				 * not do, and only the full log's cache: the travel quests keep theirs.
				 */
				questReader.clearLog();
				loop.dirty = true;
				break;
			case "config":
				config.set(text(command, "key", ""), command.has("value") && command.get("value").getAsBoolean());
				onConfigChanged(text(command, "key", ""));
				loop.dirty = true;
				break;
			case "slayer":
				game.setDbValue(net.runelite.api.gameval.DBTableID.SlayerTask.ID,
					net.runelite.api.gameval.DBTableID.SlayerTask.COL_ID, 7, 9007,
					net.runelite.api.gameval.DBTableID.SlayerTask.COL_NAME_UPPERCASE, text(command, "name", "Dust Devils"));
				game.setVarp(VarPlayerIds.SLAYER_TARGET, 7);
				game.setVarp(VarPlayerIds.SLAYER_COUNT, number(command, "remaining", 100));
				game.setVarp(VarPlayerIds.SLAYER_AREA, 0);
				loop.dirty = true;
				break;
			case "slayerOrder":
				store.set("slayerOrder", text(command, "order", "best"));
				loop.dirty = true;
				break;
			case "routeSlayer":
				SlayerState.View slayer = slayerState.view(System.currentTimeMillis());
				if (slayer.options != null && !slayer.options.rows.isEmpty())
				{
					RelayResponse.SlayerSpot spot = slayer.options.rows.stream().filter(row -> row.bestForYou).findFirst().orElse(slayer.options.rows.get(0));
					routePress.press(RoutePress.Target.place(spot.id, spot.id, spot.name,
						new WorldPoint(spot.target.x, spot.target.y, spot.target.plane), null));
				}
				break;
			case "copyInvite":
				copyInvite();
				break;
			case "startRun":
				farmRunClient.start(text(command, "kind", "ready-now"));
				break;
			case "rerouteRun":
				farmRunClient.reroute();
				break;
			case "endRun":
				farmRunClient.end();
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

	/** The login screen, as the client raises it: the game is there first, and then the plugin hears. */
	private void logout()
	{
		game.logout();
		loop.gameState(GameState.LOGIN_SCREEN);
	}

	/**
	 * The sidebar's Join button: whatever was pasted, through the real {@link JoinCode}. An invite
	 * goes to the map to be spent, through the real {@link ConnectClient}, as the sidebar sends it.
	 */
	private void join(String pasted)
	{
		JoinCode joined = JoinCode.parse(pasted);
		if (joined == null)
		{
			emit("joined", one("found", null));
			return;
		}
		if (joined.invite != null)
		{
			ConnectClient.Outcome outcome = await(done ->
				connectClient.join(pairing.serverUrl(), identity.installationKey(), joined.code, joined.invite, done));
			keep(outcome, "invite");
			return;
		}
		pairing.join(joined);
		pairing.approveServer();
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("found", joined.code);
		event.put("suffix", joined.suffix != null);
		emit("joined", event);
	}

	/** The sidebar's Connect with no group: the map makes one, and names it. */
	private void connect()
	{
		ConnectClient.Outcome outcome = await(done ->
			connectClient.start(pairing.serverUrl(), identity.installationKey(), done));
		keep(outcome, "connect");
	}

	/** What the sidebar's {@code finishConnect} does with an answer: keep the code, or say why not. */
	private void keep(ConnectClient.Outcome outcome, String via)
	{
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("via", via);
		if (outcome.joined == null)
		{
			event.put("found", null);
			event.put("message", outcome.message);
			emit("joined", event);
			return;
		}
		pairing.join(outcome.joined);
		pairing.approveServer();
		pairing.rememberName(outcome.joined.code, outcome.name);
		event.put("found", outcome.joined.code);
		event.put("suffix", outcome.joined.suffix != null);
		event.put("name", outcome.name);
		emit("joined", event);
	}

	/** The sidebar's Open the map: the link the map hands this member, or null for the plain map. */
	private void openMap()
	{
		String member = identity.id();
		String server = pairing.serverUrl();
		String url = this.<String>await(done -> connectClient.link(server, pairing.code(), member,
			member == null ? null : pairing.secret(server, pairing.code(), member), done));
		emit("link", one("url", url));
	}

	/**
	 * A request's answer, waited for. The sidebar hands it back to Swing; this harness has one thread
	 * reading stdin and wants the next command to see the answer, so it waits, for as long as the
	 * plugin's own call may take.
	 */
	@Nullable
	private <T> T await(java.util.function.Consumer<java.util.function.Consumer<T>> ask)
	{
		java.util.concurrent.CompletableFuture<T> answer = new java.util.concurrent.CompletableFuture<>();
		ask.accept(answer::complete);
		try
		{
			return answer.get(30, java.util.concurrent.TimeUnit.SECONDS);
		}
		catch (Exception e)
		{
			emit("error", one("message", "no answer from the map: " + e));
			return null;
		}
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
		loop.dirty = true;
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
		loop.dirty = true;
	}

	/**
	 * Wire-only reward fixture. JUnit drives the real cache/event observers; this deliberately
	 * begins after their settling boundary to test a real outbox, snapshot and relay together.
	 */
	private void settledReward(JsonObject command)
	{
		long now = System.currentTimeMillis();
		String type = text(command, "rewardType", "item");
		if (sharingMilestones() && (!"pet".equals(type) || store.on(MapCompanionConfig.KEY_SHARE_PETS))
			&& (!"collection_log".equals(type) || store.on(MapCompanionConfig.KEY_SHARE_COLLECTION)))
		{
			JsonObject source = command.has("source") && command.get("source").isJsonObject()
								? command.getAsJsonObject("source") : new JsonObject();
			Integer id = command.has("itemId") && !command.get("itemId").isJsonNull()
						 ? command.get("itemId").getAsInt() : null;
			Integer quantity = command.has("quantity") && !command.get("quantity").isJsonNull()
							   ? command.get("quantity").getAsInt() : null;
			Boolean collection = store.on(MapCompanionConfig.KEY_SHARE_COLLECTION) && command.has("collectionNew")
								 && command.get("collectionNew").getAsBoolean() ? true : null;
			milestones.reward(new RewardObservation(type, text(command, "item", "Pet"), id, quantity,
													text(source, "kind", null), text(source, "name", null), collection, now), now);
			loop.dirty = true;
		}
		emit("settledReward", milestones.take(now));
	}

	private boolean sharingMilestones()
	{
		return store.on(MapCompanionConfig.KEY_SHARE_MILESTONES) && game.gameState() == GameState.LOGGED_IN
			   && !config.pairingCode().trim().isEmpty();
	}

	/**
	 * The sidebar's <i>Copy invite</i>, including the plain-text message it falls back to for a group
	 * that admits by its code, and no text at all when the map made no invite a friend could use.
	 */
	private void copyInvite()
	{
		String code = config.pairingCode().trim();
		if (code.isEmpty())
		{
			emit("invite", one("ok", false));
			return;
		}
		String secret = pairing.secret(identity.id());
		relay.requestInvite(config.relayUrl(), code, identity.id(), secret, (invite, status) ->
		{
			boolean linked = invite != null && invite.url != null && !invite.url.isEmpty();
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("ok", linked);
			event.put("text", linked
					  ? "Join my OSRS map: " + invite.url + " — code " + code
					  : RelayClient.admitsByCode(status, secret != null)
					  ? "Join my OSRS map: " + config.relayUrl() + "/map?join=" + code + "&task=companion"
					  + " — then install the \"OSRS Map Companion\" RuneLite plugin and enter code " + code
					  : null);
			emit("invite", event);
		});
	}

	/**
	 * {@code MapCompanionPlugin.onConfigChanged}, which is the whole of what a settings write means.
	 */
	private void onConfigChanged(String key)
	{
		if (key.equals("shareSlayer") && !store.on("shareSlayer"))
		{
			slayerState.clear();
		}
		if (key.equals("shareQuests") || key.equals("shareQuestLog"))
		{
			loop.completions.forgetQuests();
		}
		if (key.equals("shareQuestLog"))
		{
			questReader.clearLog();
		}
		if (key.equals("shareDiaries"))
		{
			loop.completions.forgetDiaries();
		}
		if (MapCompanionConfig.KEY_SHARE_MILESTONES.equals(key)
			|| MapCompanionConfig.KEY_SHARE_PETS.equals(key)
			|| MapCompanionConfig.KEY_SHARE_COLLECTION.equals(key))
		{
			milestones.reset();
			loop.completions.reset();
		}
		if (MapCompanionConfig.KEY_PAIRING_CODE.equals(key) || MapCompanionConfig.KEY_RELAY_URL.equals(key))
		{
			farmRunClient.clear();
			farmRunState.clear();
			slayerState.clear();
			relay.reset();
			directiveStream.stop();
			loop.lastSentFingerprint = null;
			if (MapCompanionConfig.KEY_PAIRING_CODE.equals(key))
			{
				/** {@code onGroupChanged}: neither a suffix nor an unsent drop crosses a group. */
				pairing.clearSuffix();
				milestones.reset();
				loop.completions.reset();
			}
		}
		else if (MapCompanionConfig.KEY_PAIRING_SUFFIX.equals(key))
		{
			relay.clearRefusal();
		}
		loop.dirty = true;
	}

	/**
	 * A frame of the stream, after the loop has taken it in, on the stream's thread.
	 *
	 * <p>And what the sidebar now says, from the thread the stream delivered on. A directive that
	 * arrives between ticks is the whole point of the stream, so a state published only by the tick
	 * would report it a heartbeat late — or, for a reader waiting on the line rather than on a
	 * clock, not at all.
	 */
	private void streamed(RelayResponse.StreamDirective directive)
	{
		if (config.showDestination())
		{
			emit("directive", describe(directive));
		}
		publishState();
	}

	/** The map's reply to a post, after the loop has taken it in, on OkHttp's thread. */
	private void posted(RelayResponse response)
	{
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
	}

	/* ---- The loop's surface. Nothing here draws, so each call is noted for the state line or left. ---- */

	@Override
	public void begin()
	{
	}

	@Override
	public void drawn(long now)
	{
	}

	@Override
	public void setStatus(Health health, String status)
	{
		this.health = health;
		this.status = status;
	}

	/** The names the world map would draw; the harness has no world map to draw them on. */
	@Override
	public void group(@Nullable List<RelayResponse.MemberDot> members)
	{
		if (members == null)
		{
			return;
		}
		List<String> names = new ArrayList<>();
		for (RelayResponse.MemberDot dot : members)
		{
			names.add(MapPin.safe(dot.name));
		}
		groupOnMap = names;
	}

	@Override
	public void noGroup()
	{
		groupOnMap = new ArrayList<>();
	}

	@Override
	public void destination()
	{
	}

	@Override
	public void remind(Notification notification, String line)
	{
	}

	@Override
	public void noteGroupOwner(String server, String code, @Nullable Boolean owner)
	{
	}

	@Override
	public void ended(SessionEnd end)
	{
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
		/* The soonest of the week's meetups, which is what the story calls the meetup. */
		List<RelayResponse.Meetup> meetups = destinationState.getEvents();
		state.put("meetup", meetups == null || meetups.isEmpty() ? null : meetups.get(0).title);
		FarmRunState.View farm = farmRunState.view();
		Map<String, Object> run = new LinkedHashMap<>();
		if (farm != null)
		{
			run.put("id", farm.run.id);
			run.put("name", farm.run.name);
			run.put("next", farm.next() == null ? null : farm.next().n);
			run.put("done", farm.done);
			run.put("visited", farm.visited);
			run.put("revision", farm.revision);
		}
		state.put("farmRun", farm == null ? null : run);
		SlayerState.View slayer = slayerState.view(System.currentTimeMillis());
		Map<String, Object> task = new LinkedHashMap<>();
		task.put("task", slayer.task);
		task.put("remaining", slayer.remaining);
		task.put("options", slayer.options);
		task.put("seenRevision", slayerState.preferences().seenRevision);
		state.put("slayer", task);
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
		/* Under the name the story has always read: the soonest of the week's meetups. */
		if (directive.events != null && !directive.events.isEmpty())
		{
			RelayResponse.Meetup soonest = directive.events.get(0);
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("title", soonest.title);
			event.put("x", soonest.x);
			event.put("y", soonest.y);
			event.put("startsAtMs", soonest.startsAtMs);
			described.put("event", event);
		}
		return described;
	}

	private void shutDown()
	{
		farmRunClient.clear();
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
