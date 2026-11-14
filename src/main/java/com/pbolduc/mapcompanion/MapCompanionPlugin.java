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
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
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
	description = "Step-by-step routes with teleports, search, farm runs, drop luck and shooting stars from osrs-map.com in your sidebar, "
		+ "with the friends you invite on the world map.",
	tags = {"map", "routes", "teleport", "navigation", "search", "stars", "farming", "slayer", "clue", "sailing", "group", "friends", "travel", "loot", "drops", "luck", "dry"}
)
public class MapCompanionPlugin extends Plugin implements CompanionLoop.Surface
{
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
	 * Everything a game tick owns: the readers, the relay, what is waiting to be posted. The plugin
	 * hands it the client's events and is its {@link CompanionLoop.Surface}. Package-private so a
	 * test can stand one in.
	 */
	@Inject
	CompanionLoop loop;

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
	private CargoHolds cargoHolds;

	@Inject
	private ClientToolbar clientToolbar;

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

	@Inject
	private ShortestPath shortestPath;

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
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	private NavigationButton navButton;
	private MapCompanionPanel panel;

	/**
	 * The group this installation last heard it may rename, from the map's {@code groupOwner}.
	 *
	 * <p>Memory only, and replaced by every reply, because the map says it on each one: a reply
	 * without it means "may not", so losing ownership on the website shows here a post later. Kept
	 * with the server and code it was said for, so a reply from the group the player just left
	 * cannot offer <i>Rename group…</i> in the one they joined.
	 */
	@Nullable
	private volatile CompanionLoop.ForGroup runsGroup = null;

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
	@Override
	public void setStatus(Health health, String status)
	{
		this.health = health;
		this.status = status;
	}

	@Provides
	MapCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MapCompanionConfig.class);
	}

	/** The loop draws through the plugin, which is the one thing here that can. */
	@Provides
	CompanionLoop.Surface provideSurface()
	{
		return this;
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
		loop.relay.reset();
		loop.lootLog.reload();
		clientThread.invokeLater(() -> { loop.collectionItems.initialize(); });
		clientThread.invokeLater(this::restStatus);
		loop.dirty = true;
	}

	/**
	 * Split by thread, because RuneLite calls this on the EDT straight after unregistering the
	 * plugin from the event bus, and unregistering does not wait for a game tick already running.
	 * Torn down here, a tick past its checks could open the stream again after {@link CompanionLoop#stopStream}
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
				loop.lootLog.flush();
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
		loop.lootLog.flush();
		loop.farmingObserver.flush();
	}

	/** Everything a game tick reads, writes or starts. Client thread only; see {@link #shutDown}. */
	private void endLifetime()
	{
		loop.end(SessionEnd.OFF);
		restStatus();
	}

	/** The overlays this plugin draws, added at start-up and removed with the interface. */
	private Overlay[] overlays()
	{
		return new Overlay[]{destinationSceneOverlay, destinationMinimapOverlay, voyageMapOverlay, routeStepOverlay,
			farmBankOverlay, teleportOutlineOverlay};
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
		if (loop.pickpocketTick == client.getTickCount())
		{
			/** Thieving, not a kill. See {@link CompanionLoop#PICKPOCKET_MESSAGE}. */
			return;
		}
		/* Read only for a monster with a drop the diary boosts: most kills need no diary at all. */
		String npc = event.getComposition() == null ? null : event.getComposition().getName();
		List<LootLog.Notable> drops = loop.lootLog.record(event,
			npc != null && loop.lootLog.boosts(npc, LootLog.KOUREND_EASY_DIARY) ? kourendEasyDone() : null);
		if (config.dropLuckChat())
		{
			for (LootLog.Notable drop : drops)
			{
				announceDropLuck(drop);
			}
		}
		loop.whileRewarding(() -> loop.rewardObserver.onNpcLoot(event));
		loop.dirty = true;
	}

	/**
	 * Whether the easy Kourend &amp; Kebos diary is done, for the drops it boosts; null where the
	 * game has not said yet (the login window) or said something this reader cannot vouch for.
	 */
	@Nullable
	private Boolean kourendEasyDone()
	{
		Map<String, String> done = loop.diaryReader.readCompleted(System.currentTimeMillis());
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
		RelayResponse.Destination arrived = loop.destinationState.takeArrival();
		if (arrived != null && config.showDestination())
		{
			boolean yours = DestinationRoute.sentHere(arrived, arrived.targetMemberId != null ? loop.identity.id() : null);
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
		loop.whileRewarding(() -> loop.rewardObserver.onLootReceived(event));
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		loop.onScriptPreFired(event);
	}

	/** Chat is read, never sent: {@link CompanionLoop#onChatMessage} has what it is read for. */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		loop.onChatMessage(event);
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
		loop.lootLog.reload();
		loop.rewardObserver.reset();
		String next = event.getNewProfile();
		loop.lastProfile = rememberedProfile(loop.lastProfile, event.getPreviousProfile());
		boolean switched = isCharacterSwitch(loop.lastProfile, next);
		if (next != null)
		{
			loop.lastProfile = next;
		}
		if (!switched)
		{
			return;
		}
		loop.end(SessionEnd.SWITCH);
		loop.dirty = true;
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
		loop.deathWatcher.record(event);
		loop.dirty = true;
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
			loop.slayerState.clear();
		}
		if (key.equals("slayerOrder") || key.equals("slayerHideWilderness"))
		{
			loop.slayerState.choices(config.slayerOrder(), config.slayerHideWilderness());
		}
		/*
		 * A quest or diary finished while its sharing choice was off was watched by nobody allowed to
		 * say so; forgetting the baseline here makes the next reading, after it is back on, a new one.
		 */
		if (key.equals("shareQuests") || key.equals("shareQuestLog"))
		{
			loop.completions.forgetQuests();
		}
		if (key.equals("shareDiaries"))
		{
			loop.completions.forgetDiaries();
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
			loop.resetRewardDelivery();
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
		loop.dirty = true;
	}

	/**
	 * Another RuneLite profile is in use, with its own sharing choices: read its switches as a start
	 * reads them. RuneLite raises this without restarting a plugin that is on in both profiles.
	 */
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		settleSharing();
		loop.dirty = true;
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
			loop.clueReader.reset();
		}
		if (key.equals("shareQuestLog"))
		{
			loop.questReader.clearLog();
		}
		if (key.equals("shareHunterRumours") || key.equals("shareMahoganyHomes"))
		{
			loop.assignmentReader.onSharingChanged();
		}
		if (CONNECTION_KEYS.contains(key))
		{
			/** A corrected code deserves a clean slate rather than inheriting the old one's failure. */
			loop.relay.reset();
			loop.stopStream();
			loop.lastSentFingerprint = null;
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
			loop.relay.clearRefusal();
		}
		loop.dirty = true;
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
			loop.clueReader.onScrollRead(event.getItemId());
		}
	}

	/** Notice an inventory gap between snapshots, including replacement beginner scrolls. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		/* Any container: the bank's changes too, which costs one more reading and no rule about which ones matter. */
		loop.carriedStale = true;
		if (client.getGameState() == GameState.LOGGED_IN
			&& event.getContainerId() == net.runelite.api.gameval.InventoryID.INV)
		{
			loop.clueReader.read();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		loggedOut = loggedOutAfter(loggedOut, state);
		loop.gameState(state);
		if (state == GameState.LOGGED_IN)
		{
			announceUpdate();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		long now = System.currentTimeMillis();
		loop.xpTracker.onXpChanged(event.getSkill(), event.getXp(), now);
		/*
		 * Always observed, announced only when sharing is on. Observing unconditionally is what
		 * keeps the baseline honest: a tracker that only started watching when the setting was
		 * turned on would treat the first level it saw afterwards as one just gained.
		 */
		boolean gained = loop.xpTracker.observeLevel(event.getSkill(), event.getLevel());
		if (gained)
		{
			loop.whileRewarding(() -> loop.milestones.levelUp(XpTracker.displayName(event.getSkill()), event.getLevel(), now));
		}
		loop.dirty = true;
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
			loop.boatPinsStale = true;
			return;
		}
		if (!loop.boatPinsStale)
		{
			return;
		}
		List<CompanionSnapshot.Boat> boats = loop.boatReader.read(now);
		if (boats != null)
		{
			boatPins.sync(boats);
			loop.boatPinsStale = false;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		/**
		 * Varbits fire constantly, so this only flags the snapshot as worth re-sending rather than
		 * doing any reading here. What actually changed is worked out once, on the tick.
		 */
		loop.dirty = true;
		if (BoatReader.watches(event.getVarbitId()))
		{
			loop.boatPinsStale = true;
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
				loop.clueReader.onClueWidget(client.getWidget(InterfaceID.TrailCluetext.TEXT));
			}
			else
			{
				loop.clueReader.onMapWidget(event.getGroupId());
			}
			if (settings().on("shareClue"))
			{
				loop.dirty = true;
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		loop.tick();
	}

	@Override
	public void begin()
	{
		routeHere.tick();
		farmBankOverlay.refresh();
	}

	@Override
	public void drawn(long now)
	{
		/* The step pins follow the step; unchanged, this compares one string and returns. */
		destinationMarker.sync();
		syncBoatPins(now);
		destinationArrow.sync();
		announceArrival();
		shortestPath.tick(client.getTickCount(), loop.destinationRoute.view(), config.drawWithShortestPath(),
			standing(client), loop.farmRunRoute::takenOver);
	}

	@Override
	public void group(@Nullable List<RelayResponse.MemberDot> members)
	{
		groupMarkers.show(members);
	}

	@Override
	public void noGroup()
	{
		groupMarkers.clear();
	}

	@Override
	public void destination()
	{
		destinationMarker.sync();
	}

	@Override
	public void remind(Notification notification, String line)
	{
		notifier.notify(notification, line);
	}

	/**
	 * What is drawn of a session, taken down where {@link CompanionLoop#end} asks: the route's marks
	 * with the lifetime, the boats' pins unless it was a hop, and with the lifetime whether this
	 * member may rename its group, which is the plugin's to hold because the sidebar reads it.
	 */
	@Override
	public void ended(SessionEnd end)
	{
		boolean off = end == SessionEnd.OFF;
		if (off)
		{
			shortestPath.shutDown();
			destinationMarker.remove();
		}
		if (end != SessionEnd.HOP)
		{
			/* Another character's boats are not this one's. A hop is the same character, whose pins stay until the next reading settles. */
			boatPins.clear();
		}
		if (off)
		{
			destinationArrow.clear();
			runsGroup = null;
		}
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
		loop.farmRunClient.clear();
		loop.farmRunState.clear();
		loop.slayerState.clear();
		loop.pairing.clearStaleSuffix();
		loop.resetRewardDelivery();
		loop.destinationState.clear();
		destinationMarker.remove();
		groupMarkers.clear();
		loop.meetupReminder.clear();
		loop.suffixClaimed = false;
		loop.dirty = true;
	}

	/** Hold whether this member may rename its group, from a reply; absent is "may not". OkHttp's thread. */
	@Override
	public void noteGroupOwner(String server, String code, @Nullable Boolean owner)
	{
		runsGroup = Boolean.TRUE.equals(owner) ? new CompanionLoop.ForGroup(server, code, "owner") : null;
	}

	/** Whether the last reply said this member may rename the group it is in now. Swing's thread. */
	boolean runsGroup(@Nullable String server, String code)
	{
		CompanionLoop.ForGroup held = runsGroup;
		return held != null && held.server.equals(server) && held.code.equalsIgnoreCase(code);
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
		String cleaned = RouteWire.oneLine(title);
		return cleaned.length() <= 60 ? cleaned : cleaned.substring(0, 57) + "…";
	}

}
