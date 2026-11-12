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

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.input.KeyManager;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import static com.pbolduc.mapcompanion.DestinationRoute.standing;

/**
 * OSRS Map Companion.
 *
 * <p>Sends this client's game state to the map at osrs-map.com, where it becomes a live dot,
 * a ranked list of places to go for the current slayer task, a route to the current clue step, and —
 * for anyone else holding the same pairing code — a shared view of where the group is.
 *
 * <p><b>What it does not do, by construction:</b>
 *
 * <ul>
 *   <li><b>Nothing without a pairing code.</b> With the field empty this plugin opens no connection.
 *       {@link RelayClient#post} returns before touching the network, and there is no other caller.
 *   <li><b>No input, ever.</b> Destinations received from the map are drawn on the world map and
 *       written in the sidebar. Nothing is clicked, walked, or queued — Jagex's rules prohibit
 *       software performing actions the player is meant to perform, and this plugin stays on the
 *       informational side of that line by having no code that could cross it.
 *   <li><b>No identity.</b> Members are told apart by a random UUID minted by this installation and
 *       never by anything derived from the account: there is no field in the wire format for an
 *       account hash, for chat, or for bank or inventory contents. The character name travels as a
 *       <i>label</i> under its own toggle — the map draws it beside a dot and looks nothing up by
 *       it — and the nickname is still there for anybody who would rather appear as something else.
 * </ul>
 *
 * <p><b>When it sends.</b> On a game tick, at most every two seconds, when the snapshot's content
 * has changed — position, task, levels, unlocks, clue, patches, kills, death — or when the
 * heartbeat is due (six seconds without a stream, twenty with one). Logging out stops it entirely.
 * The server states its own cadence on every reply and this obeys it.
 */
@Slf4j
@PluginDescriptor(
	name = "OSRS Map Companion",
	description = "Step-by-step routes with teleports, search, farm runs and shooting stars from osrs-map.com in your sidebar, "
		+ "with the friends you invite on the world map.",
	tags = {"map", "routes", "teleport", "navigation", "search", "stars", "farming", "slayer", "clue", "sailing", "group", "friends", "travel"}
)
public class MapCompanionPlugin extends Plugin
{
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
	 * The settings that decide <em>where</em> snapshots go, and so the only ones worth a reset.
	 *
	 * <p>This config group is also where the kill log, the patch observations and the member id are
	 * stored, and every one of those writes raises a {@code ConfigChanged}. Resetting on all of them
	 * meant a single tracked kill tore down the stream, reopened it, and cleared a fatal error the
	 * relay had deliberately set — a reconnect per kill, and a status dot flickering back to
	 * "Connecting…" in the middle of a slayer task.
	 */
	private static final Set<String> CONNECTION_KEYS = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(MapCompanionConfig.KEY_PAIRING_CODE, MapCompanionConfig.KEY_RELAY_URL)));


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
	private static final Pattern PICKPOCKET_MESSAGE =
		Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private MapCompanionConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ConfigStore settings;

	@Inject
	private RelayClient relay;

	@Inject
	private SnapshotBuilder snapshotBuilder;

	@Inject
	private SnapshotFingerprint snapshotFingerprint;

	@Inject
	private XpTracker xpTracker;

	@Inject
	private ClueReader clueReader;

	@Inject
	private FarmingObserver farmingObserver;

	@Inject
	private DiaryReader diaryReader;

	@Inject
	private TravelUnlockReader travelUnlockReader;

	@Inject
	private StashReader stashReader;

	@Inject
	private BoatReader boatReader;

	@Inject
	private PortTaskReader portTaskReader;

	@Inject
	private DailyReader dailyReader;

	@Inject
	private SeaChartReader seaChartReader;

	@Inject
	private MortimerOfferReader mortimerOfferReader;

	@Inject
	private CargoHolds cargoHolds;

	@Inject
	private BirdhouseReader birdhouseReader;

	@Inject
	private QuestReader questReader;

	@Inject
	private AssignmentReader assignmentReader;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	DestinationState destinationState;

	@Inject
	DestinationMarker destinationMarker;

	/** Package-private like {@link #destinationMarker}, so a test can stand one in. */
	@Inject
	GroupMarkers groupMarkers;

	@Inject
	DestinationArrow destinationArrow;

	/** Package-private like {@link #destinationMarker}, so a test can stand one in. */
	@Inject
	BoatPins boatPins;

	/**
	 * Whether the boats' pins should be read again: a boat's varbit moved, a session began, the
	 * setting came on, or the player stepped off a deck. Each read looks every port up in the game's
	 * database, so it is not done every tick for a reading that has not moved. Volatile because a
	 * character switch is announced off the client thread that reads it.
	 */
	private volatile boolean boatPinsStale = true;

	@Inject
	private DestinationRoute destinationRoute;

	@Inject
	private FarmRunState farmRunState;

	@Inject
	private SlayerState slayerState;

	@Inject
	private FarmRunClient farmRunClient;

	@Inject
	private FarmRunRoute farmRunRoute;

	@Inject
	private CapabilityEvaluator capabilityEvaluator;

	@Inject
	private ShortestPath shortestPath;

	/** Whose dot the player asked to see, and a Route to a friend waiting for their position. */
	@Inject
	private GroupState groupState;

	@Inject
	private RoutePress routePress;

	@Inject
	private WorldMapRouteHere routeHere;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DestinationSceneOverlay destinationSceneOverlay;

	@Inject
	private DestinationMinimapOverlay destinationMinimapOverlay;

	@Inject
	private VoyageMapOverlay voyageMapOverlay;

	@Inject
	private RouteStepOverlay routeStepOverlay;

	@Inject
	private FarmBankOverlay farmBankOverlay;

	@Inject
	private TeleportOutlineOverlay teleportOutlineOverlay;

	@Inject
	private KeyManager keyManager;

	/* The two route keys, unset until the player chooses one; see act. */
	private final HotkeyListener rerouteKey = new HotkeyListener(() -> config.rerouteHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			act(MapCompanionPanel.Act.REROUTE);
		}
	};

	private final HotkeyListener followKey = new HotkeyListener(() -> config.followHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			act(MapCompanionPanel.Act.FOLLOW);
		}
	};

	/**
	 * A press from the game, a route key's or the step box's menu's, handed to the sidebar's own button
	 * on the EDT ({@link MapCompanionPanel#act}), so it can do nothing a click could not.
	 */
	private void act(MapCompanionPanel.Act act)
	{
		MapCompanionPanel shown = panel;
		if (shown != null)
		{
			SwingUtilities.invokeLater(() -> shown.act(act));
		}
	}

	@Inject
	private DirectiveStream directiveStream;

	@Inject
	private MemberIdentity identity;

	/** Package-private, like {@link #milestones}, so {@link #onGroupChanged} has a unit test. */
	@Inject
	Pairing pairing;

	@Inject
	private LootLog lootLog;

	@Inject
	MilestoneOutbox milestones;

	@Inject
	RewardObserver rewardObserver;

	/** Guarded by rewardObserver, including the handoff from settled observations to the outbox. */
	private RewardContext rewardContext;

	/** Package-private so a test can watch a quest being finished; see {@link #announceCompletions}. */
	final CompletionWatcher completions = new CompletionWatcher();

	@Inject
	private CollectionItemCatalog collectionItems;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private DeathWatcher deathWatcher;

	@Inject
	private Notifier notifier;

	private NavigationButton navButton;
	private MapCompanionPanel panel;

	/** Set when anything worth reporting has happened since the last post. */
	private volatile boolean dirty = true;
	private long lastPostAtMs = 0L;
	/** When a snapshot was last built, posted or not: what {@link #MIN_POST_INTERVAL_MS} is measured from. */
	private long lastBuildAtMs = 0L;
	private WorldPoint lastPosition = null;
	/**
	 * Gson of the last snapshot that was actually handed to OkHttp, clocks zeroed.
	 * Null until the first successful enqueue; a fingerprint match with no heartbeat due
	 * is what stops varbit churn posting every two seconds.
	 */
	private String lastSentFingerprint = null;

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
	private volatile boolean suffixClaimed = false;

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
	 * The group this installation last heard it may rename, from the map's {@code groupOwner}.
	 *
	 * <p>Memory only, and replaced by every reply, because the map says it on each one: a reply
	 * without it means "may not", so losing ownership on the website shows here a post later. Kept
	 * with the server and code it was said for, so a reply from the group the player just left
	 * cannot offer <i>Rename group…</i> in the one they joined.
	 */
	@Nullable
	private volatile ForGroup runsGroup = null;

	/**
	 * The tick a pickpocket succeeded on. NPC loot arriving on it is thieving, not a kill.
	 *
	 * <p>Starts below every real tick count so the first kill of a session is not mistaken for one.
	 */
	private int pickpocketTick = -1;

	/**
	 * The last RuneScape profile this session actually played on, or null before the first login.
	 *
	 * <p>Kept because {@code RuneScapeProfileChanged} reports signing in and signing out as well as
	 * switching character, and only the third of those means the session's state belongs to
	 * somebody else. See {@link #onRuneScapeProfileChanged}.
	 */
	private String lastProfile = null;

	@Getter
	private volatile String status = "Not paired";

	/** The same state as {@link #status}, in the form the sidebar dot can be coloured by. */
	@Getter
	private volatile Health health = Health.IDLE;

	/**
	 * At the login screen, with no character to ask the map as: the sidebar stops offering the presses
	 * that need one, and the status card's Logged out is the one place that says so. Set and cleared
	 * by the game's own state changes, and read at the start and end of a lifetime, on the client
	 * thread; read by the sidebar's refresh, so volatile. A hop, a loading screen and a lost connection
	 * keep the character, and leave it as it was. False until something has been read, so a plugin
	 * made but not started offers what it always did.
	 */
	private volatile boolean loggedOut;

	/** Whether a character is logged in to press as, as the game last said. Any thread. */
	boolean loggedIn()
	{
		return !loggedOut;
	}

	/**
	 * Whether the player is at the login screen after {@code state}: reaching it says so, and logging
	 * in says otherwise, while every state between keeps what was, a hop and a loading screen among
	 * them. Package-private so the rule is pinned without a client.
	 */
	static boolean loggedOutAfter(boolean before, GameState state)
	{
		return state == GameState.LOGIN_SCREEN || (state != GameState.LOGGED_IN && before);
	}

	/** Status and health move together; setting one without the other is the bug this prevents. */
	private void setStatus(Health health, String status)
	{
		this.health = health;
		this.status = status;
	}

	@Provides
	MapCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MapCompanionConfig.class);
	}

	/** RuneLite's ordinary settings, which is where the sharing fields RuneLite does not declare are read. */
	@Provides
	ConfigStore provideSettings(ConfigManager configManager)
	{
		return ConfigStore.of(configManager);
	}

	/**
	 * Reset, under RuneLite's list: RuneLite puts back what the config declares, and the sharing fields
	 * under the four groups are not declared, so they are turned off here, as Reset always turned them.
	 */
	@Override
	public void resetConfiguration()
	{
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			for (SharingFields.Field field : section.fields)
			{
				settings().unset(field.key);
			}
		}
	}

	@Provides
	@javax.inject.Singleton
	RewardObserver provideRewardObserver(Client client, CollectionItemCatalog catalog, RewardPolicy policy)
	{
		return new RewardObserver(client, catalog, policy::includes);
	}

	@Override
	protected void startUp()
	{
		/* First, so no snapshot is built from a group whose switch was pressed while this was off. */
		settleSharing();
		lifetime++;
		panel = injector.getInstance(MapCompanionPanel.class);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
					.tooltip("OSRS Map Companion")
					.icon(icon)
					.priority(7)
					.panel(panel)
					.build();
		clientToolbar.addNavigation(navButton);
		routeStepOverlay.pressWith(this::act);
		/* Meet here on the world map: the sidebar comes forward with the Group tab's form open for the spot. */
		routeHere.meet = spot -> SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			panel.meetAt(spot);
		});
		for (Overlay overlay : overlays())
		{
			overlayManager.add(overlay);
		}
		keyManager.registerKeyListener(rerouteKey);
		keyManager.registerKeyListener(followKey);
		relay.reset();
		lootLog.reload();
		clientThread.invokeLater(() -> { collectionItems.initialize(); });
		clientThread.invokeLater(this::restStatus);
		dirty = true;
	}

	/**
	 * Split by thread, because RuneLite calls this on the EDT straight after unregistering the
	 * plugin from the event bus, and unregistering does not wait for a game tick already running.
	 * Torn down here, a tick past its checks could open the stream again after {@link #stopStream}
	 * — a disabled plugin then reconnecting and drawing a group forever — or post one more
	 * snapshot, or iterate a reader's map while this cleared it. Queued on the client thread, the
	 * teardown runs after that tick, and no later one can start.
	 */
	@Override
	protected void shutDown()
	{
		removeInterface();
		int ending = lifetime;
		clientThread.invoke(() ->
		{
			/*
			 * Enabled again before this ran: the new lifetime has already reset what it owns, and
			 * tearing it down now would stop its stream. Only the save the old one owed is kept.
			 */
			if (ending == lifetime)
			{
				endLifetime();
			}
			else
			{
				lootLog.flush();
			}
		});
	}

	/** Counts startUps, so a teardown queued by one lifetime cannot land in the next. EDT only. */
	private volatile int lifetime;

	/** The EDT's half of {@link #shutDown}: the panel, its button and the overlays. */
	void removeInterface()
	{
		/* Stops the sidebar timer even if the panel was never opened, so nothing survives the plugin. */
		if (panel != null)
		{
			panel.stopRefreshing();
		}
		routeHere.meet = null;
		clientToolbar.removeNavigation(navButton);
		for (Overlay overlay : overlays())
		{
			overlayManager.remove(overlay);
		}
		keyManager.unregisterKeyListener(rerouteKey);
		keyManager.unregisterKeyListener(followKey);
	}

	/**
	 * Closing RuneLite does not call {@link #shutDown}: it posts this, and ConfigManager writes the
	 * settings to disk in its own handler at a lower priority, after every plugin's. So kills still
	 * inside the log's save window and a patch's latest sighting are written here or not at all.
	 * Both writes are cheap and guarded; a farming write that races a final tick is skipped, not
	 * thrown.
	 */
	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		lootLog.flush();
		farmingObserver.flush();
	}

	/** Everything a game tick reads, writes or starts. Client thread only; see {@link #shutDown}. */
	private void endLifetime()
	{
		farmRunClient.clear();
		farmRunState.clear();
		slayerState.clear();
		lootLog.flush();
		relay.reset();
		stopStream();
		/* Cleared before the pins go, so a stream frame's queued sync cannot redraw one. */
		destinationState.clear();
		destinationRoute.clear();
		shortestPath.shutDown();
		destinationMarker.remove();
		boatPins.clear();
		destinationArrow.clear();
		resetRewardDelivery();
		farmingObserver.onSessionEnded();
		diaryReader.onSessionEnded();
		endSessions(false);
		lastPosition = null;
		meetupReminder.clear();
		pickpocketTick = -1;
		lastProfile = null;
		lastSentFingerprint = null;
		suffixClaimed = false;
		issuedSecret = null;
		toldName = null;
		runsGroup = null;
		restStatus();
	}

	/** The overlays this plugin draws, added at start-up and removed with the interface. */
	private Overlay[] overlays()
	{
		return new Overlay[]{destinationSceneOverlay, destinationMinimapOverlay, voyageMapOverlay, routeStepOverlay,
			farmBankOverlay, teleportOutlineOverlay};
	}

	/**
	 * What every way a session ends forgets: a logout or a hop, another character, the plugin
	 * stopping. A hop keeps the port tasks' last settled reading for the sidebar, as
	 * {@link PortTaskReader#onHop} says. Client thread.
	 */
	private void endSessions(boolean hop)
	{
		xpTracker.reset();
		clueReader.reset();
		for (SettlingReader reader : settling())
		{
			reader.onSessionEnded();
		}
		if (hop)
		{
			portTaskReader.onHop();
		}
		else
		{
			portTaskReader.onSessionEnded();
		}
		questReader.onSessionEnded();
		assignmentReader.onSessionEnded();
	}

	/**
	 * The readers told of a session's start and its end at the same moments, in every way one ends.
	 * The farming, diary, port task and death readers are not here: each ends at a moment of its own.
	 */
	private SettlingReader[] settling()
	{
		return new SettlingReader[]{travelUnlockReader, stashReader, boatReader, birdhouseReader, dailyReader,
			seaChartReader, mortimerOfferReader};
	}

	/**
	 * The status before a tick has said anything: at the start of a lifetime, and at its end.
	 *
	 * <p>No tick comes at the login screen. RuneLite starts its plugins before the client first
	 * reaches it, so a client start hears {@code Logged out} from that state change; a plugin turned
	 * on, or back on, while RuneLite already sits there hears nothing, and would print whatever this
	 * last said beside the name of the group it is in until the player logged in. Client thread.
	 */
	private void restStatus()
	{
		String code = Pairing.codeIn(config);
		boolean playing = client.getGameState() == GameState.LOGGED_IN;
		loggedOut = !playing;
		setStatus(code.isEmpty() ? Health.IDLE : Health.WAITING, restingStatus(!code.isEmpty(), playing));
	}

	/**
	 * {@link #restStatus}'s words: the tick's own for no code, {@code Logged out} as the login
	 * screen's state change says it, and {@code Connecting…} for a relay that has just been reset.
	 * Package-private so they can be pinned without a client.
	 */
	static String restingStatus(boolean hasCode, boolean loggedIn)
	{
		if (!hasCode)
		{
			return "Not paired";
		}
		return loggedIn ? "Connecting…" : "Logged out";
	}

	/**
	 * Every event subscription in this plugin lives on this class, including the two that only
	 * forward. RuneLite registers the plugin object on the event bus and nothing else, so an
	 * @Subscribe on an injected singleton compiles, reads correctly, and never runs — a failure with
	 * no symptom except a counter that stays at zero.
	 */
	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (pickpocketTick == client.getTickCount())
		{
			/** Thieving, not a kill. See {@link #PICKPOCKET_MESSAGE}. */
			return;
		}
		/* Read only for a monster with a drop the diary boosts: most kills need no diary at all. */
		String npc = event.getComposition() == null ? null : event.getComposition().getName();
		List<LootLog.Notable> drops = lootLog.record(event,
			npc != null && lootLog.boosts(npc, LootLog.KOUREND_EASY_DIARY) ? kourendEasyDone() : null);
		if (config.dropLuckChat())
		{
			for (LootLog.Notable drop : drops)
			{
				announceDropLuck(drop);
			}
		}
		whileRewarding(() -> rewardObserver.onNpcLoot(event));
		dirty = true;
	}

	/**
	 * Whether the easy Kourend &amp; Kebos diary is done, for the drops it boosts; null where the
	 * game has not said yet (the login window) or said something this reader cannot vouch for.
	 */
	@Nullable
	private Boolean kourendEasyDone()
	{
		Map<String, String> done = diaryReader.readCompleted(System.currentTimeMillis());
		String tier = done == null ? null : done.get("Kourend & Kebos");
		if (tier == null || DiaryReader.UNKNOWN.equals(tier))
		{
			return null;
		}
		return !DiaryReader.NONE.equals(tier);
	}

	/**
	 * One local chat line for a rated drop, saying how its wait compares.
	 *
	 * <p>The moment a drop lands is the moment anybody wants the number, and it is the one moment
	 * the sidebar is least likely to be open. Local only: a queued game message is drawn by this
	 * client and goes nowhere, and an unrated drop gets nothing because the game already prints its
	 * own collection-log line for it.
	 */
	private void announceDropLuck(LootLog.Notable drop)
	{
		String line = DropLuckView.chatLine(drop);
		if (line != null)
		{
			say("Drop luck: ", line);
		}
	}

	/** This version's one line, on the first login after an update; see {@link UpdateNotice}. */
	private void announceUpdate()
	{
		boolean usedBefore = !Pairing.codeIn(config).isEmpty();
		String line = UpdateNotice.due(ConfigStore.of(configManager), usedBefore);
		if (line != null)
		{
			say("OSRS Map Companion " + CompanionVersion.VERSION + ": ", line + " " + UpdateNotice.WHERE);
		}
	}

	/**
	 * One local chat line when a place arrives from the map. It shows in the sidebar and on the world
	 * map, but nothing over the game said so, and a place the group sends waits for Follow. One the
	 * player sent here themselves is followed from the start (see DestinationRoute.sync), and says so.
	 */
	private void announceArrival()
	{
		RelayResponse.Destination arrived = destinationState.takeArrival();
		if (arrived != null && config.showDestination())
		{
			boolean yours = DestinationRoute.sentHere(arrived, arrived.targetMemberId != null ? identity.id() : null);
			say("Destination: ", NowCards.arrivalLine(arrived, client.getWorld(), config.followHotkey(), yours));
		}
	}

	/** A game message drawn by this client and sent nowhere: its head highlighted, then its words. */
	private void say(String head, String line)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append(head)
				.append(ChatColorType.NORMAL).append(line)
				.build())
			.build());
	}

	/** Only the observer's named core activity sources qualify; NPC forwarding is ignored there. */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		whileRewarding(() -> rewardObserver.onLootReceived(event));
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
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
	@Subscribe
	public void onChatMessage(ChatMessage event)
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
	 * The player switched character, or hopped to a world with its own save.
	 *
	 * <p>Everything this plugin holds about "what has been happening" belongs to whoever was
	 * playing, and none of it transfers. A drop still waiting in the outbox would otherwise be
	 * posted under the new character's dot, the experience window would report the last account's
	 * training as this one's, and the death marker would send somebody walking back to a tile where
	 * a different character died.
	 *
	 * <p><b>Which is why an ordinary login must not land here.</b> This event fires whenever the
	 * profile key changes, and that includes signing in (nothing to something) and signing out
	 * (something to nothing) — so acting on every one of them would clear the death marker on every
	 * relog, which is exactly the moment {@link DeathWatcher} exists to survive. Only a move from
	 * one real profile to a <em>different</em> real profile is a change of character.
	 *
	 * <p><b>And why the event's own {@code previousProfile} is read as well.</b> The rule above
	 * needs to know who was playing before, and this plugin only learns that from an event it was
	 * running for. Enable it from the settings panel while already logged in and it has missed the
	 * one that said so — the sequence is then (A → null) on logging out and (null → B) on logging
	 * in as somebody else, neither of which names two real profiles, and B silently inherits A's
	 * outbox, experience window and death marker. Seeding from {@code previousProfile} the first
	 * time closes that: the logout event carries A even though it does not itself mean a switch.
	 */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		lootLog.reload();
		rewardObserver.reset();
		String next = event.getNewProfile();
		lastProfile = rememberedProfile(lastProfile, event.getPreviousProfile());
		boolean switched = isCharacterSwitch(lastProfile, next);
		if (next != null)
		{
			lastProfile = next;
		}
		if (!switched)
		{
			return;
		}
		// Old member replies and frames must not deliver a previous character's run.
		farmRunClient.clear();
		farmRunState.bind(null);
		slayerState.clear();
		milestones.reset();
		completions.reset();
		deathWatcher.clear();
		endSessions(false);
		/* Another character's boats are not this one's. */
		boatPins.clear();
		boatPinsStale = true;
		pickpocketTick = -1;
		dirty = true;
	}

	/** Shortest Path's answers to {@link ShortestPath}'s questions, forwarded from here for the reason above. */
	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		shortestPath.onPluginMessage(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		deathWatcher.record(event);
		dirty = true;
	}

	/**
	 * React to a setting the player changed — and, carefully, not to one this plugin wrote itself.
	 *
	 * <p>See {@link #CONNECTION_KEYS}. Only a new code or a new address is worth tearing the
	 * connection down for; everything else in this group either changes what a snapshot contains,
	 * which the next post picks up, or is this plugin's own stored state, which is nobody's business
	 * but its own.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!MapCompanionConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		/*
		 * A sharing switch and its fields, kept in step before anything below reads a field. A switch's
		 * fan-out raises each field's own event, so what follows still runs once per field that moved.
		 *
		 * Only for a write on the profile last settled. When RuneLite changes profile it loads the
		 * new one, then raises this for every setting that differs from the old, and only then says
		 * the profile changed. Those are not presses: read as presses, a switch that is on in the new
		 * profile would turn on every field under it, one an update added and the player never chose
		 * among them. They are left for the settle that follows, which knows which fields are new.
		 */
		if (event.getProfile() == null && settledProfile == settings().profile())
		{
			SharingGroups.changed(key, event.getNewValue(), config, settings());
		}
		if (key.equals("shareSlayer") && !settings().on("shareSlayer"))
		{
			slayerState.clear();
		}
		if (key.equals("slayerOrder") || key.equals("slayerHideWilderness"))
		{
			slayerState.choices(config.slayerOrder(), config.slayerHideWilderness());
		}
		/*
		 * A quest or diary finished while its sharing choice was off was watched by nobody allowed to
		 * say so; forgetting the baseline here makes the next reading, after it is back on, a new one.
		 */
		if (key.equals("shareQuests") || key.equals("shareQuestLog"))
		{
			completions.forgetQuests();
		}
		if (key.equals("shareDiaries"))
		{
			completions.forgetDiaries();
		}
		if (MapCompanionConfig.KEY_SHARE_MILESTONES.equals(key)
			|| MapCompanionConfig.KEY_SHARE_PETS.equals(key)
			|| MapCompanionConfig.KEY_SHARE_COLLECTION.equals(key))
		{
			/*
			 * A queued observation is still private when its sharing choice is withdrawn. Done on
			 * the writer's thread, under the observer's own lock, so the withdrawal cannot wait
			 * behind a tick that is about to deliver it.
			 */
			resetRewardDelivery();
		}
		/*
		 * ConfigChanged is posted on whichever thread wrote the setting — the EDT for the sidebar
		 * and the config panel — and the readers below are the client thread's, unsynchronized.
		 */
		if (key.equals("shareClue") || key.equals("shareQuestLog") || key.equals("shareHunterRumours")
			|| key.equals("shareMahoganyHomes") || CONNECTION_KEYS.contains(key)
			|| MapCompanionConfig.KEY_PAIRING_SUFFIX.equals(key))
		{
			clientThread.invoke(() -> applySettingChange(key));
		}
		dirty = true;
	}

	/**
	 * Another RuneLite profile is in use, with its own sharing choices: read its switches as a start
	 * reads them. RuneLite raises this without restarting a plugin that is on in both profiles.
	 */
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		settleSharing();
		dirty = true;
	}

	/** Read the open RuneLite profile's switches as a start does, and remember it is the one read. */
	private void settleSharing()
	{
		SharingGroups.settle(config, settings());
		settledProfile = settings().profile();
	}

	/** The RuneLite profile {@link #settleSharing} last read; see {@link #onConfigChanged}. Written and read on any thread. */
	private volatile long settledProfile;

	/** RuneLite's settings, as {@link SharingGroups} and the sharing fields read them; a test gives its own. */
	ConfigStore settings()
	{
		return settings;
	}

	/** The part of a setting change that touches what a game tick owns. Client thread only. */
	private void applySettingChange(String key)
	{
		if (key.equals("shareClue"))
		{
			clueReader.reset();
		}
		if (key.equals("shareQuestLog"))
		{
			questReader.clearLog();
		}
		if (key.equals("shareHunterRumours") || key.equals("shareMahoganyHomes"))
		{
			assignmentReader.onSharingChanged();
		}
		if (CONNECTION_KEYS.contains(key))
		{
			/** A corrected code deserves a clean slate rather than inheriting the old one's failure. */
			relay.reset();
			stopStream();
			lastSentFingerprint = null;
			onGroupChanged();
			setStatus(Health.WAITING, "Connecting…");
		}
		else if (MapCompanionConfig.KEY_PAIRING_SUFFIX.equals(key))
		{
			/**
			 * Not a reset: the pairing has not changed, so the stream and the "connected" state are
			 * still true. What has changed is the one input that can answer a {@code needs_code}
			 * refusal, and leaving that latched for five minutes after the player did exactly what
			 * the sentence asked would look like the paste had not worked.
			 */
			relay.clearRefusal();
		}
		dirty = true;
	}

	/**
	 * Which scroll the next clue interface belongs to. Read only, never changed or consumed: this
	 * is the menu entry the player already chose, and it is how the interface is told apart when
	 * scrolls of several tiers are carried.
	 *
	 * <p>The clue is read whether or not it is shared, because the sidebar's clue card offers Route
	 * either way. The snapshot carries it only with <i>Share clue</i>; see {@link ClueReader#current}.
	 */
	/** Where a right-click opened, for Route here on the world map; see {@link WorldMapRouteHere}. */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		routeHere.menuOpened();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		routeHere.entryAdded();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if ("Read".equals(event.getMenuOption()) && event.getItemId() > 0)
		{
			clueReader.onScrollRead(event.getItemId());
		}
	}

	/** Notice an inventory gap between snapshots, including replacement beginner scrolls. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (client.getGameState() == GameState.LOGGED_IN
			&& event.getContainerId() == net.runelite.api.gameval.InventoryID.INV)
		{
			clueReader.read();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		loggedOut = loggedOutAfter(loggedOut, state);
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
			/* On no world until the next tick names one, so no meetup's world reads as this one meanwhile. */
			groupState.onWorld(0);
			rewardObserver.reset();
			/**
			 * Logging out stops the wire. Not "sends a final snapshot saying goodbye" — simply stops,
			 * and the map's own staleness handling takes it from there, which is the same thing it
			 * does when a client crashes or a laptop lid closes.
			 */
			/** Patch and diary flags are about to read zero; {@link SessionSettle} has the window. */
			farmingObserver.onSessionEnded();
			diaryReader.onSessionEnded();
			endSessions(state == GameState.HOPPING);
			boatPinsStale = true;
			if (state == GameState.LOGIN_SCREEN)
			{
				/* A hop is the same character, whose pins stay until the next reading settles. */
				boatPins.clear();
			}
			if (state == GameState.HOPPING)
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
			deathWatcher.onSessionEnded();
			lastPosition = null;
			lastSentFingerprint = null;
			if (state == GameState.LOGIN_SCREEN)
			{
				/**
				 * A hop keeps the stream — it is the same pairing and the same member, and the gap is
				 * seconds. A logout does not: "logging out stops the wire" has to include the
				 * connection that was held open to listen, or the sentence is only half true.
				 */
				stopStream();
			}
			setStatus(Health.WAITING, "Logged out");
		}
		else if (state == GameState.LOGGED_IN)
		{
			dirty = true;
			announceUpdate();
		}
		else
		{
			// Loading gaps invalidate evidence without restarting the login delay.
			farmingObserver.resetSession();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		long now = System.currentTimeMillis();
		xpTracker.onXpChanged(event.getSkill(), event.getXp(), now);
		/*
		 * Always observed, announced only when sharing is on. Observing unconditionally is what
		 * keeps the baseline honest: a tracker that only started watching when the setting was
		 * turned on would treat the first level it saw afterwards as one just gained.
		 */
		boolean gained = xpTracker.observeLevel(event.getSkill(), event.getLevel());
		if (gained)
		{
			whileRewarding(() -> milestones.levelUp(XpTracker.displayName(event.getSkill()), event.getLevel(), now));
		}
		dirty = true;
	}

	/** A boat's cargo hold and hull, for how many crates it carries; see {@link CargoHolds}. */
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		cargoHolds.spawned(object.getWorldView().getId(), object.getId());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		cargoHolds.despawned(object.getWorldView().getId(), object.getId());
	}

	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		cargoHolds.unloaded(event.getWorldView().getId());
	}

	/**
	 * Pin the player's boats on the world map, from the reading voyages already tick by — see
	 * {@link BoatPins}. Aboard, or with the setting off, nothing is drawn. A reading that has not
	 * settled since a login or a hop keeps what is drawn: a logout cleared it already, and a hop is
	 * the same character with the same boats.
	 */
	private void syncBoatPins(long now)
	{
		if (!config.showBoatsOnWorldMap() || WorldTiles.aboard(client))
		{
			boatPins.sync(null);
			boatPinsStale = true;
			return;
		}
		if (!boatPinsStale)
		{
			return;
		}
		List<CompanionSnapshot.Boat> boats = boatReader.read(now);
		if (boats != null)
		{
			boatPins.sync(boats);
			boatPinsStale = false;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		/**
		 * Varbits fire constantly, so this only flags the snapshot as worth re-sending rather than
		 * doing any reading here. What actually changed is worked out once, on the tick.
		 */
		dirty = true;
		if (BoatReader.watches(event.getVarbitId()))
		{
			boatPinsStale = true;
		}
	}

	/**
	 * Read a clue step when — and only when — the clue interface is what opened.
	 *
	 * <p>This used to hand {@link ClueReader} child 1 of <i>every</i> interface that loaded, which was
	 * wrong twice over. Wrong in general, because opening a bank or a quest journal while carrying a
	 * scroll overwrote the remembered step with that interface's text and the map then reported the
	 * clue as unrecognised. And wrong in particular, because child 1 of the clue interface is its
	 * close button: {@code TrailCluetext.TEXT} is child 2, so the step text was never actually being
	 * read. Naming the widget the client itself names fixes both and cannot drift.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		/** Read on the client thread, because the widget tree is game state. */
		clientThread.invokeLater(() ->
		{
			if (event.getGroupId() == InterfaceID.TRAIL_CLUETEXT)
			{
				clueReader.onClueWidget(client.getWidget(InterfaceID.TrailCluetext.TEXT));
			}
			else
			{
				clueReader.onMapWidget(event.getGroupId());
			}
			if (settings().on("shareClue"))
			{
				dirty = true;
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		long now = System.currentTimeMillis();
		flushRewards(now);
		/* Kills inside the save window; a logout or character change writes them through reload. */
		lootLog.saveIfDue();
		routeHere.tick();
		farmBankOverlay.refresh();

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
			farmRunState.carried(capabilityEvaluator.evaluate());
			farmRunState.observe(farmingObserver.observations(now), birdhouseReader.read(now), standing(client), now);
			/* A port-task run's stops tick by the slots read this tick, and by nothing else. */
			farmRunState.observePorts(settings().on("sharePortTasks") ? portTaskReader.latest() : null);
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
		/* The step pins follow the step; unchanged, this compares one string and returns. */
		destinationMarker.sync();
		syncBoatPins(now);
		destinationArrow.sync();
		announceArrival();
		shortestPath.tick(client.getTickCount(), destinationRoute.view(), config.drawWithShortestPath(),
			standing(client), farmRunRoute::takenOver);
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
			setStatus(Health.IDLE, "Not paired");
			return;
		}

		String server = pairing.serverUrl();
		if (!pairing.hasServerConsent(server, code))
		{
			stopStream();
			setStatus(Health.WAITING, "Press Connect to choose " + pairing.serverLabel());
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
			setStatus(Health.ERROR, fatal);
			return;
		}

		/*
		 * A member is one character, and RuneLite names the character's profile a tick or two after
		 * the game says logged in. Until it does there is nobody to post as.
		 */
		String memberId = client.getGameState() == GameState.LOGGED_IN ? identity.id() : null;
		if (memberId == null)
		{
			setStatus(Health.WAITING, "Waiting for login");
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
					groupMarkers.show(directive.members);
				}
				else if (directive.members != null)
				{
					/* Only the friends asked for, while the group layer is off. */
					groupMarkers.show(groupState.shownDots(directive.members));
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
			groupMarkers.clear();
		}
		groupState.stream(positions && directiveStream.isConnected(), directiveStream.lastReadAtMs());

		destinationMarker.sync();

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
			noteGroupOwner(server, code, response.groupOwner);
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
	 * The code now names a different group, or none — so throw away what belonged to the last one.
	 *
	 * <p>The suffix, pending announcements and received instructions all belong to that group.
	 * Clear them together so an unreachable replacement cannot keep showing the previous meetup
	 * or destination. Per-group secrets stay filed under their own codes for switching back.
	 *
	 * <p>The server is part of the group identity. A copied code on another server cannot inherit
	 * its suffix, pending announcements or received instructions.
	 *
	 * <p>A group issues its member secret once. Keeping that group's stored credential makes a
	 * later switch back work; only <i>Leave group</i> discards it with the member id it belongs to.
	 *
	 * <p>This is the single place the rule lives, so it holds for the sidebar's Join and Leave
	 * buttons and for somebody typing a code into RuneLite's settings panel alike.
	 */
	void onGroupChanged()
	{
		farmRunClient.clear();
		farmRunState.clear();
		slayerState.clear();
		pairing.clearStaleSuffix();
		resetRewardDelivery();
		destinationState.clear();
		destinationMarker.remove();
		groupMarkers.clear();
		meetupReminder.clear();
		suffixClaimed = false;
		dirty = true;
	}

	/**
	 * Close the held connection and take down everything drawn from it. The group's dots are only
	 * true while the stream that brought them is open, so the two always end together.
	 */
	private void stopStream()
	{
		directiveStream.stop();
		groupState.stream(false, 0L);
		groupMarkers.clear();
	}

	private void resetRewardDelivery()
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
	private void whileRewarding(Runnable action)
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
		RewardContext current = new RewardContext(config, settings());
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
			if (!context.sameAs(new RewardContext(config, settings())))
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
			if (!context.sameAs(new RewardContext(config, settings())))
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
			if (settings().on("shareQuestLog") || settings().on("shareQuests"))
			{
				Map<String, String> quests = settings().on("shareQuestLog") ? questReader.readLog(now)
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
			if (settings().on("shareDiaries"))
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

	/** Hold whether this member may rename its group, from a reply; absent is "may not". OkHttp's thread. */
	void noteGroupOwner(String server, String code, @Nullable Boolean owner)
	{
		runsGroup = Boolean.TRUE.equals(owner) ? new ForGroup(server, code, "owner") : null;
	}

	/** Whether the last reply said this member may rename the group it is in now. Swing's thread. */
	boolean runsGroup(@Nullable String server, String code)
	{
		ForGroup held = runsGroup;
		return held != null && held.server.equals(server) && held.code.equalsIgnoreCase(code);
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
	 * about. One immutable object published through one volatile field cannot be half-read.
	 */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class ForGroup
	{
		private final String server;
		private final String code;
		/** The member a secret was issued to; null for a group's name, which is everyone's. */
		@Nullable
		private final String member;
		private final String value;

		private ForGroup(String server, String code, String value)
		{
			this(server, code, null, value);
		}
	}

	/**
	 * Whether moving from one profile key to another means a different character is now playing.
	 *
	 * <p>Only a move between two <em>different real</em> profiles counts. Signing in is null to
	 * something and signing out is something to null, and treating either as a switch is what would
	 * clear the death marker on every relog. Package-private so the four cases can be pinned
	 * without a client.
	 */
	static boolean isCharacterSwitch(String lastProfile, String next)
	{
		return next != null && lastProfile != null && !next.equals(lastProfile);
	}

	/**
	 * Who was playing before this event, given what this plugin had already seen.
	 *
	 * <p>What it was watching wins: {@code remembered} is the last profile that actually played
	 * while this plugin was running, and the event's {@code previous} is only worth having when
	 * there is nothing to compare against — a plugin enabled mid-session has missed every event so
	 * far, and the first one it does see is the only place the outgoing character is named.
	 *
	 * <p>Package-private so the case that needs it can be driven without a client, because it is
	 * the one nobody reaches by playing normally.
	 */
	static String rememberedProfile(String remembered, String previous)
	{
		return remembered != null ? remembered : previous;
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
		setStatus(healthFor(live, failures, null),
			healthSentence(live, failures, relay.getLastTransientError(), "Connected to " + pairing.serverLabel()));
	}

	/**
	 * The sentence beside {@link #healthFor}'s colour, from the same outcome.
	 *
	 * <p>Once a post has failed it says why, whether or not one ever landed: a map that is down, or
	 * that this network cannot reach, failed the first post as plainly as it fails a later one, and
	 * {@code Connecting…} there would never change while it looked exactly like a connection about
	 * to work. The colour still waits until a post has landed. Package-private for its test, and for
	 * the harness, which prints what the sidebar would.
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
			notifier.notify(notification, line);
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
		if (settings().on("shareSlayer"))
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
			destinationMarker.sync();
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
	 * A meetup title fit to hand to the client.
	 *
	 * <p>It arrives over the network from whoever holds the pairing code, so it is trimmed to a
	 * sentence and stripped of control characters before going anywhere near a notification. The
	 * sidebar escapes the same strings for the same reason — see {@code MapCompanionPanel.escape}.
	 *
	 * <p>Package-private so it can be tested: it handles text this plugin did not write.
	 */
	static String safeTitle(String title)
	{
		if (title == null || title.trim().isEmpty())
		{
			return "somewhere";
		}
		String cleaned = title.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
		return cleaned.length() <= 60 ? cleaned : cleaned.substring(0, 57) + "…";
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
		for (SettlingReader reader : settling())
		{
			reader.onSessionResumed(now);
		}
		farmingObserver.onSessionResumed(now);
		diaryReader.onSessionResumed(now);
		portTaskReader.onSessionResumed(now);
		questReader.onSessionResumed(now);
		deathWatcher.onSessionResumed(now);
		assignmentReader.observe(now);
		/*
		 * Read every tick while the choice is on, for the sidebar as much as the snapshot: the slots
		 * are five varbits and a cached row each. Off, nothing is read or shown.
		 */
		if (settings().on("shareSlayer"))
		{
			mortimerOfferReader.observe(now);
		}
		if (settings().on("sharePortTasks"))
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
		if (settings().on("shareFarming") || farmRunState.active())
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
