package com.pbolduc.mapcompanion;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteLAF;
import okhttp3.OkHttpClient;

/**
 * Paints the real MapCompanionPanel at 225px with RuneLite 1.13.4's own look and feel, in the states
 * the last polish pass changed: a fresh install with and without a kill log, the step after a first
 * Connect, a busy Home, the Group tab, Settings with Advanced open, Stars with a star pressed, the
 * search answering "bank" and "whip", the death and clue cards, a meetup, and Drop luck alone.
 * The README's pictures are route, search-whip-card, farm-count, group, stars-route, dropluck-boss-card,
 * slayer-card, settings-closed, port-run and chart-run, with VoyageShot's voyage-sail; the card scenes
 * paint the real sections without the panel's surrounding controls. The tasks scenes are Home with
 * all three task cards, open and folded; {@code -Dslayer.options=<file>} gives any Slayer scene
 * another list, and {@code -Dchart.run=<file>} gives chart-run another run.
 * Synthetic local state and made-up names; no game account and no network — the map address is a
 * port nothing listens on. A paired scene shares what the one press at Connect turns on.
 *
 * <p>Written to run against the commit before the pass as well as after it, so one renderer takes
 * both photographs: everything it presses is found by component name or by its words, and a name
 * the older panel lacks is simply not pressed. Game sprites come from the website's copies of the
 * same files when {@code OSRS_MAP_PUBLIC} names that folder (the client's own sprite cache needs a
 * running game); without it a picture's place is left empty, as a sprite still loading would be.
 *
 * <p>Reproduce from the plugin root after {@code ./gradlew harnessJar}:
 *
 * <pre>
 * cp=$(tr -d '\r\n' &lt; build/harness/classpath.txt)
 * javac -proc:none -encoding UTF-8 --release 11 -cp "$cp" -d build/polish-shot-classes docs/review-evidence/sidebar-polish-2026-10-29/*.java
 * java -Djava.awt.headless=true -Dlogback.configurationFile=docs/review-evidence/sharing-sidebar-2026-10-11/logback.xml \
 *   -cp "build/polish-shot-classes;$cp" com.pbolduc.mapcompanion.SidebarPolishShot &lt;out&gt; [state…]
 * </pre>
 *
 * On Windows the classpath entries need Windows paths ({@code cygpath -w}).
 */
public class SidebarPolishShot
{
	static final List<String> STATES = Arrays.asList("fresh", "fresh-log", "connect", "home", "route", "group", "settings", "settings-closed",
		"stars", "stars-filters", "stars-route", "stars-pinned", "stars-refused", "stars-guess", "stars-busy", "stars-busy-pressed", "stars-busy-open", "stars-busy-ways", "search", "search-whip", "search-whip-card", "death", "meetup", "dropluck", "dropluck-card", "dropluck-boss-card", "status",
		"farm", "farm-card", "farm-bring", "farm-count", "slayer", "slayer-card", "slayer-open", "slayer-expanded", "slayer-blocked",
		"port-tasks", "port-tasks-card", "port-tasks-unread", "port-run", "port-run-changed", "chart-run", "quest-route", "tasks", "tasks-folded");

	static HarnessGame game;

	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(RuneLiteLAF::setup);
		List<String> states = args.length > 1 ? Arrays.asList(args).subList(1, args.length) : STATES;
		for (String state : states)
		{
			MapCompanionPanel[] panel = new MapCompanionPanel[1];
			SwingUtilities.invokeAndWait(() -> call(() -> panel[0] = build(state)));
			SwingUtilities.invokeAndWait(() -> call(() -> refresh(panel[0])));
			SwingUtilities.invokeAndWait(() -> call(() -> after(state, panel[0])));
			SwingUtilities.invokeAndWait(() -> call(() -> refresh(panel[0])));
			SwingUtilities.invokeAndWait(() -> { });
			SwingUtilities.invokeAndWait(() -> call(() -> shoot(state.endsWith("-card") && state.startsWith("dropluck")
				? (JComponent) get(panel[0], "drySection") : state.equals("search-whip-card")
				? (JComponent) get(panel[0], "searchPanel") : state.equals("farm-card") || state.equals("farm-bring") || state.equals("farm-count")
				|| state.equals("chart-run")
				? (JComponent) get(panel[0], "farmRunPanel") : state.equals("slayer-card") || state.equals("slayer-open")
				? (JComponent) get(panel[0], "slayerPanel") : state.equals("quest-route")
				? (JComponent) get(get(panel[0], "nowCards"), "destinationSlot") : state.equals("slayer-blocked")
				? (JComponent) get(panel[0], "slayerPanel") : state.equals("port-tasks-card") || state.equals("port-tasks-unread") || state.startsWith("port-run")
				? (JComponent) get(panel[0], "portTasksPanel") : panel[0], args[0] + "/" + state + ".png")));
		}
		System.exit(0);
	}

	interface Body
	{
		void run() throws Exception;
	}

	static void call(Body body)
	{
		try
		{
			body.run();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	/* ---- The panel and its collaborators ---- */

	static MapCompanionPanel build(String state) throws Exception
	{
		game = new HarnessGame();
		HarnessStore store = new HarnessStore();
		HarnessConfig config = new HarnessConfig(store, "http://127.0.0.1:9");
		Gson gson = new Gson();
		MapCompanionPlugin plugin = (MapCompanionPlugin) allocate(MapCompanionPlugin.class);
		ConfigManager configManager = (ConfigManager) allocate(ConfigManager.class);
		DestinationMarker marker = (DestinationMarker) allocate(DestinationMarker.class);
		net.runelite.client.game.ItemManager itemManager = (net.runelite.client.game.ItemManager) allocate(net.runelite.client.game.ItemManager.class);
		/*
		 * The real client's collection catalog resolves a rated item that has not dropped yet.
		 * This scene supplies that one name/id without claiming a complete collection catalog.
		 */
		LootLog lootLog = new LootLog(game.client(), store, gson, new LootLog.NotableItems()
		{
			@Override
			public boolean ready()
			{
				return false;
			}

			@Override
			public boolean notable(int itemId)
			{
				return false;
			}

			@Override
			public boolean pet(int itemId)
			{
				return false;
			}

			@Override
			public Integer idFor(String name)
			{
				return "Mystic robe bottom (dark)".equals(name)
					? net.runelite.api.gameval.ItemID.MYSTIC_ROBE_BOTTOM_DARK : null;
			}
		});
		Pairing pairing = new Pairing(store, gson, config::relayUrl);
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
				/* Shortest Path as not installed: its own constructor asks RuneLite's plugin manager, which a renderer has none of. */
				bind(ShortestPath.class).toInstance(new ShortestPath(message -> { }));
				bind(OkHttpClient.class).toInstance(new OkHttpClient());
				bind(MapCompanionConfig.class).toInstance(config);
				bind(ConfigStore.class).toInstance(store);
				bind(MemberIdentity.class).toInstance(new MemberIdentity(store));
				bind(Pairing.class).toInstance(pairing);
				bind(LootLog.class).toInstance(lootLog);
				bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
				bind(MapCompanionPlugin.class).toProvider(() -> plugin);
				bind(ConfigManager.class).toProvider(() -> configManager);
				bind(DestinationMarker.class).toProvider(() -> marker);
				bind(net.runelite.client.game.ItemManager.class).toProvider(() -> itemManager);
				bind(java.util.concurrent.ScheduledExecutorService.class)
					.toInstance(java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
				Sprites.bind(binder());
			}
		});
		game.login("Sunday", 302, 3222, 3218, 0);
		boolean fresh = state.startsWith("fresh");
		if (!fresh)
		{
			pairing.join(JoinCode.parse("KQX482"));
			pairing.approveServer();
			pairing.rememberName("KQX482", "Sunday raids");
			MemberIdentity identity = injector.getInstance(MemberIdentity.class);
			pairing.rememberSecret("KQX482", identity.id(), "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23");
		}
		if (fresh)
		{
			set(plugin, "status", "Not paired");
			set(plugin, "health", Health.WAITING);
		}
		else if (state.equals("connect"))
		{
			set(plugin, "status", "Connecting…");
			set(plugin, "health", Health.WAITING);
		}
		else
		{
			set(plugin, "status", "Connected to 127.0.0.1:9");
			set(plugin, "health", Health.LIVE);
			/*
			 * What the one press at Connect turns on: every field it names, where you died among them.
			 * Of its two drawing choices the destinations are on and the whole group's dots are not, so a
			 * friend's row still offers World map.
			 */
			for (String key : RecommendedSharing.KEYS)
			{
				config.set(key, !key.equals("showGroupOnWorldMap"));
			}
		}
		/* A game tick has read which world this is, as one has by the time a player sees the sidebar. */
		lootLog.onTick(1, 0, 0);
		Scenes.fill(state, injector, config, lootLog);
		MapCompanionPanel panel = injector.getInstance(MapCompanionPanel.class);
		/* Drop luck uses its own picture seam, separate from the other panels' GameSprites. */
		set(get(panel, "dropLuck"), "pictures", (DropLuckPanel.Pictures) (id, loaded) -> Sprites.itemImage(id));
		return panel;
	}

	/** What each state puts in the collaborators before the panel is built. */
	static final class Scenes
	{
		static void fill(String state, Injector injector, HarnessConfig config, LootLog log) throws Exception
		{
			DestinationState destinations = injector.getInstance(DestinationState.class);
			/* A session long settled, with no Slayer task unless a Slayer scene gives one below. */
			injector.getInstance(SlayerState.class).local(injector.getInstance(MemberIdentity.class).id(), null, "best", false);
			if (state.startsWith("slayer") || state.startsWith("tasks"))
			{
				config.set("shareSlayer", true);
				RelayResponse.SlayerOptions options = new Gson().fromJson(Files.readString(Paths.get(System.getProperty("slayer.options", "src/test/resources/contract/" + (state.equals("slayer-blocked") ? "slayer-blocked.json" : "slayer-options.json"))), StandardCharsets.UTF_8), RelayResponse.SlayerOptions.class);
				options.note = null;
				if (options.kinds.size() == 1) for (RelayResponse.SlayerSpot spot : options.rows) spot.kinds.clear();
				options = SlayerWire.normalize(options);
				CompanionSnapshot.Slayer task = new CompanionSnapshot.Slayer();
				task.taskName = options.taskName; task.areaName = options.areaName; task.remaining = 143;
				String member = injector.getInstance(MemberIdentity.class).id();
				SlayerState slayer = injector.getInstance(SlayerState.class);
				slayer.local(member, task, options.order, options.hideWilderness);
				RelayResponse.Directive directive = new RelayResponse.Directive();
				directive.relayEpoch = "fixture-relay-epoch"; directive.slayerOptions = options; directive.slayerRevision = 1;
				slayer.accept(member, directive, System.currentTimeMillis());
			}
			if (state.startsWith("port-tasks") || state.startsWith("tasks"))
			{
				/*
				 * The reading is laid in directly: the reader's varbits and tables are PortTaskReaderTest's
				 * to prove, and the harness client answers neither. The map's own rows: Brimhaven's sword
				 * crates already aboard, its fruit and Port Khazard's sand still to collect, and the
				 * osprey bounty with nine feathers owed — the website's Port tasks scene holds the same.
				 */
				config.set("sharePortTasks", true);
				boolean unread = state.equals("port-tasks-unread");
				PortTaskReader reader = injector.getInstance(PortTaskReader.class);
				set(reader, "latest", Arrays.asList(
					new PortTaskReader.Slot(0, PortTaskReader.COURIER, 81, "Corsair Cove sword delivery", "Brimhaven", "Corsair Cove", 3, 3, 0, 0, null),
					new PortTaskReader.Slot(1, PortTaskReader.COURIER, 84, "Port Khazard fruit delivery", "Brimhaven", "Port Khazard", 3, 0, 0, 0, null),
					new PortTaskReader.Slot(2, PortTaskReader.COURIER, 121, "Ardougne sand delivery", "Port Khazard", "Ardougne", 3, 0, 0, 0, null),
					new PortTaskReader.Slot(3, PortTaskReader.BOUNTY, 466, "Brimhaven osprey bounty", null, "Brimhaven", 15, 0, 0, 9, "Osprey feather"),
					PortTaskReader.Slot.plain(4, unread ? PortTaskReader.UNKNOWN : PortTaskReader.LOCKED)));
				set(reader, "latestHold", unread ? null : 30);
				/* Start run is offered beside the card once the boats are shared too. */
				config.set("shareBoats", !unread);
			}
			if (state.startsWith("port-run"))
			{
				/*
				 * The map's own port-task run fixture (the planner's answer for these slots, from Port
				 * Sarim): Port Khazard, Ardougne, the osprey and Brimhaven's board. The fruit went in and
				 * the sand was loaded, so stop 1 has ticked by its counters and the leg to Ardougne is
				 * followed. The changed scene has the sand task swapped for another in its slot.
				 */
				config.set("sharePortTasks", true);
				config.set("shareBoats", true);
				boolean changed = state.equals("port-run-changed");
				PortTaskReader reader = injector.getInstance(PortTaskReader.class);
				List<PortTaskReader.Slot> planned = Arrays.asList(
					PortTaskReader.Slot.plain(0, PortTaskReader.EMPTY),
					new PortTaskReader.Slot(1, PortTaskReader.COURIER, 84, "Port Khazard fruit delivery", "Brimhaven", "Port Khazard", 3, 3, 0, 0, null),
					new PortTaskReader.Slot(2, PortTaskReader.COURIER, 121, "Ardougne sand delivery", "Port Khazard", "Ardougne", 3, 0, 0, 0, null),
					new PortTaskReader.Slot(3, PortTaskReader.BOUNTY, 466, "Brimhaven osprey bounty", null, "Brimhaven", 15, 0, 0, 9, "Osprey feather"),
					PortTaskReader.Slot.plain(4, PortTaskReader.LOCKED));
				List<PortTaskReader.Slot> now = Arrays.asList(
					PortTaskReader.Slot.plain(0, PortTaskReader.EMPTY),
					PortTaskReader.Slot.plain(1, PortTaskReader.EMPTY),
					changed
						? new PortTaskReader.Slot(2, PortTaskReader.COURIER, 2, "Port Sarim spice delivery", "Catherby", "Port Sarim", 3, 0, 0, 0, null)
						: new PortTaskReader.Slot(2, PortTaskReader.COURIER, 121, "Ardougne sand delivery", "Port Khazard", "Ardougne", 3, 3, 0, 0, null),
					new PortTaskReader.Slot(3, PortTaskReader.BOUNTY, 466, "Brimhaven osprey bounty", null, "Brimhaven", 15, 0, 0, 9, "Osprey feather"),
					PortTaskReader.Slot.plain(4, PortTaskReader.LOCKED));
				set(reader, "latest", now);
				set(reader, "latestHold", 30);
				String json = Files.readString(Paths.get("src/test/resources/contract/port-task-run.json"), StandardCharsets.UTF_8);
				RelayResponse.FarmRun run = new Gson().fromJson(json, RelayResponse.FarmRun.class);
				for (RelayResponse.FarmStop stop : run.stops)
				{
					stop.done = false;
				}
				FarmRunState farm = injector.getInstance(FarmRunState.class);
				farm.bind(injector.getInstance(MemberIdentity.class).id());
				farm.accept(run, 1, System.currentTimeMillis() - 1_000);
				farm.observePorts(planned);
				farm.observePorts(changed
					? Arrays.asList(now.get(0), new PortTaskReader.Slot(1, PortTaskReader.COURIER, 84, "Port Khazard fruit delivery", "Brimhaven", "Port Khazard", 3, 3, 3, 0, null),
						now.get(2), now.get(3), now.get(4))
					: now);
				injector.getInstance(FarmRunRoute.class).sync();
				set(injector.getInstance(ShortestPath.class), "installed", true);
			}
			if (state.equals("chart-run"))
			{
				/*
				 * A sea charting run as the map sends it: the contract's own, the Kharidian Sea's first four
				 * stops, or with -Dchart.run the whole run the map's planner made for the same boat. Its
				 * first task arrives ticked, since the map ticks a charting stop from the game's charts.
				 */
				String json = Files.readString(Paths.get(System.getProperty("chart.run", "src/test/resources/contract/sea-chart-run.json")),
					StandardCharsets.UTF_8);
				FarmRunState farm = injector.getInstance(FarmRunState.class);
				farm.bind(injector.getInstance(MemberIdentity.class).id());
				farm.accept(new Gson().fromJson(json, RelayResponse.FarmRun.class), 1, System.currentTimeMillis() - 1_000);
				injector.getInstance(FarmRunRoute.class).sync();
				set(injector.getInstance(ShortestPath.class), "installed", true);
			}
			if (state.startsWith("farm"))
			{
				String json = Files.readString(Paths.get("src/test/resources/contract/farm-run.json"), StandardCharsets.UTF_8);
				RelayResponse.FarmRun run = new Gson().fromJson(json, RelayResponse.FarmRun.class);
				boolean count = state.equals("farm-count");
				if (count)
				{
					/*
					 * Neither of the fixture's teleports, Explorer's ring 2 and the Catherby tablet, is one the
					 * client can see in a bag, so its checklist never counts. The count scene adds the teleport
					 * a birdhouse run takes to Fossil Island, and carries nothing.
					 */
					RelayResponse.FarmBring pendant = new RelayResponse.FarmBring();
					pendant.kind = "teleport";
					pendant.name = "Digsite pendant";
					run.bring.add(pendant);
				}
				long now = System.currentTimeMillis();
				FarmRunState farm = injector.getInstance(FarmRunState.class);
				farm.bind(injector.getInstance(MemberIdentity.class).id());
				farm.accept(run, 1, now - 1_000);
				CompanionSnapshot.Patch replanted = new CompanionSnapshot.Patch();
				replanted.id = "falador-herb";
				replanted.state = "growing";
				replanted.observedMsAgo = 0;
				replanted.completionId = "synthetic-replant";
				replanted.completedMsAgo = 100L;
				/* The count scene stands at the patch just replanted, so the leg to Catherby is still ahead. */
				farm.observe(Collections.singletonList(replanted), null, count ? new WorldPoint(3053, 3309, 0) : new WorldPoint(2811, 3462, 0), now);
				farm.carried(count ? Collections.emptyList() : Collections.singletonList("explorersRing"));
				injector.getInstance(FarmRunRoute.class).sync();
				set(injector.getInstance(ShortestPath.class), "installed", true);
			}
			if (state.equals("home"))
			{
				route(injector, destinations, new WorldPoint(3221, 3219, 0));
				death(injector, config);
				social(destinations, false, false);
			}
			if (state.equals("route"))
			{
				/*
				 * Standing at Draynor's bank, before the cast, so the step on now is the one with the spell's
				 * icon. No Shortest Path: Follow is offered on every route.
				 */
				route(injector, destinations, new WorldPoint(3092, 3243, 0));
			}
			if (state.equals("group") || state.equals("meetup"))
			{
				social(destinations, state.equals("meetup"), true);
			}
			if (state.equals("death"))
			{
				death(injector, config);
			}
			if (state.equals("dropluck-boss-card"))
			{
				bossLoot(log);
			}
			else if (state.equals("home") || state.equals("fresh-log") || state.startsWith("dropluck"))
			{
				loot(log);
			}
		}

		static void route(Injector injector, DestinationState destinations, WorldPoint standing) throws Exception
		{
			DestinationRoute route = injector.getInstance(DestinationRoute.class);
			RelayResponse.Directive directive = new RelayResponse.Directive();
			directive.destination = new RelayResponse.Destination();
			directive.destination.name = "Lumbridge Castle bank";
			directive.destination.x = 3208;
			directive.destination.y = 3220;
			directive.destination.plane = 2;
			directive.destination.note = "From Cara";
			directive.destination.route = SidebarPolishShot.route();
			destinations.accept(directive);
			route.sync(destinations.getDestination(), null);
			route.advance(standing, DestinationRoute.Sailing.ASHORE_UNKNOWN);
		}

		static void death(Injector injector, HarnessConfig config) throws Exception
		{
			config.set("shareDeath", false);
			DeathWatcher deaths = injector.getInstance(DeathWatcher.class);
			set(deaths, "diedAt", new WorldPoint(2272, 4049, 0));
			set(deaths, "diedAtMs", System.currentTimeMillis() - 7 * 60_000L);
			CompanionSnapshot.Clue clue = new CompanionSnapshot.Clue();
			clue.tier = "hard";
			clue.itemId = 2722;
			clue.text = "01 degrees 18 minutes south 14 degrees 15 minutes east";
			clue.coordTile = ClueReader.parseCoordinate(clue.text);
			set(injector.getInstance(ClueReader.class), "current", clue);
		}

		/**
		 * The map's own description of a group of four (lib/contract/social.json), times moved to now,
		 * with its news as sent: a level, a drop and a quest.
		 *
		 * @param unanswered the next meetup without this member's answer
		 * @param twoMeetups a second meetup tomorrow, which the Group tab folds to one line
		 */
		static void social(DestinationState destinations, boolean unanswered, boolean twoMeetups) throws Exception
		{
			String social = new String(Files.readAllBytes(Paths.get("src/test/resources/contract/stream.json")), StandardCharsets.UTF_8);
			RelayResponse.StreamDirective directive = new Gson().fromJson(social, RelayResponse.StreamDirective.class);
			long now = System.currentTimeMillis();
			directive.roster.get(0).news.atMs = now - 5 * 60_000L;
			directive.roster.get(1).news.atMs = now - 9 * 60_000L;
			directive.roster.get(2).news.atMs = now - 41 * 60_000L;
			directive.events.get(0).startsAtMs = now + 80 * 60_000L;
			if (unanswered)
			{
				directive.events.get(0).mine = null;
			}
			if (twoMeetups)
			{
				RelayResponse.Meetup later = new Gson().fromJson(new Gson().toJson(directive.events.get(0)), RelayResponse.Meetup.class);
				later.id = "raids";
				later.title = "Chambers of Xeric";
				later.near = "near the Chambers of Xeric";
				later.world = 416;
				later.startsAtMs = now + 26 * 3_600_000L;
				later.mine = "going";
				later.who = later.who.subList(0, 2);
				directive.events.add(later);
			}
			directive.destination = null;
			destinations.acceptSocial(directive, true);
		}

		/**
		 * A boss with three drops at one rate, for the README: eight berserker rings in 640 kills where
		 * five are expected, and 250 kills since the last, so the card says both things at once.
		 */
		static void bossLoot(LootLog log)
		{
			game.nameItem(6729, "Dagannoth bones");
			game.nameItem(6737, "Berserker ring");
			game.nameItem(6735, "Warrior ring");
			game.nameItem(6739, "Dragon axe");
			List<Integer> berserker = Arrays.asList(60, 95, 150, 210, 260, 300, 350, 390);
			List<Integer> warrior = Arrays.asList(120, 330, 480, 560, 610);
			List<Integer> axe = Arrays.asList(45, 280, 520);
			for (int kill = 1; kill <= 640; kill++)
			{
				List<ItemStack> items = new ArrayList<>();
				items.add(new ItemStack(6729, 1));
				if (berserker.contains(kill))
				{
					items.add(new ItemStack(6737, 1));
				}
				if (warrior.contains(kill))
				{
					items.add(new ItemStack(6735, 1));
				}
				if (axe.contains(kill))
				{
					items.add(new ItemStack(6739, 1));
				}
				log.record(new ServerNpcLoot(game.npc("Dagannoth Rex"), items));
			}
		}

		static void loot(LootLog log)
		{
			game.nameItem(526, "Bones");
			for (int i = 0; i < 1_300; i++)
			{
				List<ItemStack> items = new ArrayList<>();
				items.add(new ItemStack(526, 1));
				log.record(new ServerNpcLoot(game.npc("Aberrant spectre"), items));
			}
		}
	}

	static RelayResponse.RouteStep step(String slot, String title, String detail, boolean walk, int x, int y, Integer plane, String... notes)
	{
		RelayResponse.RouteStep step = new RelayResponse.RouteStep();
		step.slot = slot;
		step.title = title;
		step.detail = detail;
		step.walk = walk;
		step.notes = notes.length == 0 ? null : Arrays.asList(notes);
		step.at = new RelayResponse.RouteTile();
		step.at.x = x;
		step.at.y = y;
		step.at.plane = plane;
		return step;
	}

	static RelayResponse.Route route()
	{
		RelayResponse.RouteOption best = new RelayResponse.RouteOption();
		best.id = "lumbridge-home";
		best.label = "Lumbridge Home Teleport";
		best.best = true;
		best.tiles = 52;
		best.steps = new ArrayList<>(Arrays.asList(
			Steps.picture(step("method", "Cast Lumbridge Home Teleport", "Standard spellbook", false, 3222, 3218, null,
				"Ten seconds to cast, and a combat hit stops it"), "spriteId", net.runelite.api.gameval.SpriteID.Magicon2.LUMBRIDGE_HOME_TELEPORT),
			step("walk-leg", "Walk to the castle's staircase", "18 tiles", true, 3206, 3209, null),
			step("walk-leg", "Climb to the bank", "2nd floor", true, 3208, 3220, 2)));
		RelayResponse.RouteOption ring = new RelayResponse.RouteOption();
		ring.id = "ring-of-dueling";
		ring.label = "Ring of dueling: Ferox Enclave";
		/* The name the website's chip wears: where the ring lands. */
		ring.face = "Ferox Enclave";
		ring.tiles = 80;
		ring.steps = new ArrayList<>(Collections.singletonList(
			Steps.picture(step("method", "Rub a ring of dueling", "Ferox Enclave", false, 3150, 3635, null), "itemId", 2552)));
		RelayResponse.Route route = new RelayResponse.Route();
		route.origin = "shared";
		route.options = new ArrayList<>(Arrays.asList(best, ring));
		return route;
	}

	/**
	 * A way to the Rimmington mine star, in the shape of the map's answer to Route: the tablet's landing
	 * from the map's transport table, then the walk to the site's tile from the star fixture, priced as
	 * the map prices a tablet, the walk plus its four ticks at a run. Written for the picture; no map
	 * planned it.
	 */
	static RelayResponse.Route starRoute()
	{
		RelayResponse.RouteOption tablet = new RelayResponse.RouteOption();
		tablet.id = "teleport_item:2953,3224,0";
		tablet.label = "Rimmington tablet";
		tablet.best = true;
		tablet.tiles = 29;
		tablet.steps = new ArrayList<>(Arrays.asList(
			Steps.picture(step("method", "Use Rimmington tablet", null, false, 2953, 3224, null),
				"itemId", net.runelite.api.gameval.ItemID.NZONE_TELETAB_RIMMINGTON),
			step("arrival-walk", "Walk 21t north-east", "To Rimmington mine", true, 2974, 3241, 0)));
		RelayResponse.Route route = new RelayResponse.Route();
		route.origin = "shared";
		route.options = new ArrayList<>(Collections.singletonList(tablet));
		return route;
	}

	/** A step's icon where this build reads one; nothing where it does not. */
	static final class Steps
	{
		/** A step's picture by the id the map sends, {@code itemId} or {@code spriteId}; set by name so this still runs on a step that has neither. */
		static RelayResponse.RouteStep picture(RelayResponse.RouteStep step, String field, int id)
		{
			try
			{
				Field picture = RelayResponse.RouteStep.class.getDeclaredField(field);
				picture.setAccessible(true);
				picture.set(step, id);
			}
			catch (ReflectiveOperationException absent)
			{
				/* An older step reads no picture. */
			}
			return step;
		}
	}

	/* ---- What each state does to the built panel ---- */

	static void after(String state, MapCompanionPanel panel) throws Exception
	{
		if (state.equals("quest-route"))
		{
			questRouted(panel);
			refresh(panel);
		}
		if (state.equals("slayer-open") || state.equals("slayer-expanded"))
		{
			press(panel, "slayer.expand");
			press(panel, "slayer.kinds");
		}
		/* Home as a player on a task has it: the farm run idle, every Slayer place listed, the port tasks held. */
		if (state.equals("tasks"))
		{
			press(panel, "slayer.expand");
		}
		/* The same with the farm run and the port tasks folded away under their headings. */
		if (state.equals("tasks-folded"))
		{
			press(panel, "farmRunFolded");
			press(panel, "portTasksFolded");
		}
		if (state.equals("farm-bring"))
		{
			expand(panel, "Before you leave");
		}
		if (state.equals("connect"))
		{
			Pairing pairing = (Pairing) get(panel, "pairing");
			try
			{
				/* Before the pass, a Connect also said it was waiting under the field; after it, only the card does. */
				set(panel, "pendingConnectionMessage", true);
				Method say = MapCompanionPanel.class.getDeclaredMethod("say", String.class);
				say.setAccessible(true);
				say.invoke(panel, "Connecting to " + pairing.serverLabel() + "… Waiting for the map.");
				set(panel, "pendingConnectionMessage", true);
			}
			catch (NoSuchFieldException after)
			{
				/* The panel says nothing under the field after a Connect. */
			}
			((RecommendedSharing) get(panel, "recommendedSharing")).offer(pairing.serverLabel());
		}
		if (state.equals("group") || state.equals("meetup"))
		{
			press(panel, "companionView.group");
		}
		if (state.equals("meetup"))
		{
			GroupPanel group = (GroupPanel) get(panel, "groupPanel");
			group.openForm(true);
			group.title().setText("Barrows run");
		}
		if (state.startsWith("settings"))
		{
			if (!press(panel, "companion.settings"))
			{
				press(panel, "companionView.sharing");
			}
			if (state.equals("settings"))
			{
				expand(panel, "Advanced");
			}
		}
		if (state.startsWith("stars"))
		{
			StarsPanel stars = (StarsPanel) get(panel, "starsPanel");
			StarsClient client = (StarsClient) get(stars, "service");
			String feed = new String(Files.readAllBytes(Paths.get("src/test/resources/companion-stars.sample.json")), StandardCharsets.UTF_8);
			/* The website's four-site fixture, or an evening's worth of sites for the scenes that measure a long list. */
			boolean busy = state.contains("busy");
			client.model.accept(busy ? Busy.feed() : new Gson().fromJson(feed, StarsModel.Feed.class));
			client.message = null;
			client.model.miningLevel = busy ? 99 : 72;
			/* The world the scene logs into, which the tab reads on the client thread a renderer never drains. */
			client.model.playerWorld = 302;
			client.model.skillTotal = busy ? 2100 : 1620;
			if (busy)
			{
				client.model.preferences.filters.hideWilderness = false;
			}
			press(panel, "companionView.stars");
			stars.refresh();
			/* The star a scene is about: Rimmington mine's first world, or the busy list's mine by the hunter guild. */
			StarsModel.Star star = null;
			for (StarsModel.Site site : client.model.sites())
			{
				if (site.name.equals("Rimmington mine") && !busy)
				{
					/* The fold opened on its other worlds. */
					star = site.worlds.get(0);
					press(panel, "stars.expand." + site.key);
				}
				if (site.name.equals("Mine north-west of hunter guild"))
				{
					star = site.worlds.get(0);
				}
				if (site.name.equals("Varrock east bank") && state.equals("stars-busy-open"))
				{
					press(panel, "stars.expand." + site.key);
				}
				/* A star whose spot is a guess, picked by a press on its card: it is never pinned, and opens on the website. */
				if (site.name.equals("Mining Guild") && state.equals("stars-guess"))
				{
					press(panel, "stars.site." + site.key);
				}
			}
			DestinationState destinations = (DestinationState) get(panel, "destinationState");
			RoutePress.Target target = RoutePress.Target.tile(StarsPanel.SOURCE, new WorldPoint(star.x, star.y, star.plane), star.name, star.world, null);
			/*
			 * A star just pressed, by the steps its press takes, since the press itself needs the marker
			 * a renderer has none of: pinned as this client's destination with no way, and its question
			 * still waiting out the dwell, which is the head's Planning….
			 */
			if (state.equals("stars-pinned") || state.equals("stars-busy-pressed") || state.equals("stars-refused"))
			{
				RelayResponse.Destination pin = RoutePress.destination(target, null, null);
				destinations.selectLocal(pin);
				set(stars, "asking", pin);
				/* The question asked and refused: the map's words under the star, and Route beside Unpin to ask again. */
				if (state.equals("stars-refused"))
				{
					set(stars, "asking", null);
					set(stars, "asked", star.id);
					((RoutePress) get(get(panel, "nowCards"), "routePress")).refuse(StarsPanel.SOURCE, MapWait.BUSY);
				}
				refresh(panel);
				stars.refresh();
			}
			/* The same press answered with four ways, two of them not open to the account: the strip's chips, the blocked ones last. */
			if (state.equals("stars-busy-ways"))
			{
				RelayResponse.Destination answer = RoutePress.destination(target, null, Busy.route());
				destinations.selectLocal(answer);
				((DestinationRoute) get(get(panel, "nowCards"), "destinationRoute")).adoptPressed(answer);
				refresh(panel);
				stars.refresh();
			}
			if (state.equals("stars-filters"))
			{
				press(panel, "stars.filters");
			}
			/* A star's press answered, by RoutePress's own steps for an answer: pinned with its way, and followed. */
			if (state.equals("stars-route"))
			{
				RelayResponse.Destination answer = RoutePress.destination(target, null, starRoute());
				destinations.selectLocal(answer);
				((DestinationRoute) get(get(panel, "nowCards"), "destinationRoute")).adoptPressed(answer);
				refresh(panel);
				stars.refresh();
			}
		}
		if (state.startsWith("search"))
		{
			search(panel, state.equals("search") ? "bank" : "whip");
		}
		refresh(panel);
	}

	/**
	 * Home after Route on Quest Helper's step, as the press's answer leaves it: the step pinned at
	 * Lumbridge Castle's bank, the contract's own route for the way there, and the quest-start star
	 * the press draws beside the card's name. Quest Helper has moved on by the time of the picture,
	 * so Reroute waits. The press itself asks the map, which a renderer has none of.
	 */
	static void questRouted(MapCompanionPanel panel) throws Exception
	{
		Object cards = get(panel, "nowCards");
		GameSprites sprites = (GameSprites) get(cards, "sprites");
		RoutePress press = (RoutePress) get(cards, "routePress");
		WorldPoint step = new WorldPoint(3208, 3220, 2);
		RoutePress.Target target = RoutePress.Target.tile("quest" + step, step, Words.of("now.questStep"), null, null)
			.showing((label, box) -> sprites.sprite(label, net.runelite.api.gameval.SpriteID.Mapfunction.QUEST_START, 15));
		RelayResponse.Destination answer = RoutePress.destination(target, null, route());
		DestinationState destinations = (DestinationState) get(panel, "destinationState");
		destinations.selectLocal(answer);
		((DestinationRoute) get(cards, "destinationRoute")).adoptPressed(answer);
		java.lang.reflect.Constructor<?> shown = Class.forName("com.pbolduc.mapcompanion.RoutePress$Shown").getDeclaredConstructors()[0];
		shown.setAccessible(true);
		set(press, "shown", shown.newInstance(DestinationRoute.keyOf(answer), get(target, "picture"), destinations.generation()));
	}

	static List<Component> all(java.awt.Container root)
	{
		List<Component> found = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			found.add(c);
			if (c instanceof java.awt.Container)
			{
				found.addAll(all((java.awt.Container) c));
			}
		}
		return found;
	}

	/**
	 * A long list, as an evening's feed is: made-up worlds, tiers and times at real sites, the longest
	 * names the map gives a site among them, and a route with three other ways. Written for measuring
	 * the tab, and planned by no map.
	 */
	static final class Busy
	{
		static StarsModel.Feed feed()
		{
			Object[][] rows = {
				{"Port Piscarilius mine in Kourend", 472, 9, 60, 1762, 3715},
				{"West Lumbridge Swamp mine", 510, 8, 52, 3148, 3150},
				{"Mine north-west of hunter guild", 501, 8, 51, 1533, 3077},
				{"Gnome Stronghold spirit tree", 476, 7, 48, 2449, 3490},
				{"Gnome Stronghold spirit tree", 331, 5, 30, 2449, 3490},
				{"Gnome Stronghold spirit tree", 389, 3, 14, 2449, 3490},
				{"Varrock east bank", 545, 7, 45, 3258, 3408},
				{"Varrock east bank", 536, 7, 44, 3258, 3408},
				{"Varrock east bank", 302, 6, 40, 3258, 3408},
				{"Varrock east bank", 466, 4, 22, 3258, 3408},
				{"Varrock east bank", 420, 2, 4, 3258, 3408},
				{"Ardougne Monastery", 302, 6, 42, 2608, 3233},
				{"Ardougne Monastery", 487, 6, 39, 2608, 3233},
				{"Al Kharid bank", 325, 6, 42, 3276, 3164},
				{"Brimhaven northwest gold mine", 421, 6, 41, 2732, 3225},
				{"Brimhaven northwest gold mine", 444, 3, 18, 2732, 3225},
				{"Lava Maze runite mine", 493, 6, 41, 3059, 3884},
				{"Lletya", 493, 6, 41, 2318, 3269},
				{"East Falador bank", 542, 6, 40, 3030, 3348},
				{"Salvager Overlook in Varlamore", 582, 6, 40, 1614, 3300},
				{"Rimmington mine", 543, 6, 39, 2974, 3241},
				{"Corsair Cove bank", 558, 6, 38, 2567, 2858},
				{"Prifddinas Zalcano entrance", 353, 6, 37, 3273, 6023},
			};
			StarsModel.Feed feed = new StarsModel.Feed();
			feed.version = 1;
			feed.worldsAvailable = true;
			feed.rows = new ArrayList<>();
			int rank = 0;
			for (Object[] row : rows)
			{
				StarsModel.Star star = new StarsModel.Star();
				star.name = (String) row[0];
				star.world = (Integer) row[1];
				star.id = star.world + "|" + star.name.toLowerCase();
				star.siteKey = "tile:" + row[4] + "," + row[5];
				star.favoriteKey = star.name.toLowerCase();
				star.rank = rank++;
				star.calledTier = (Integer) row[2];
				star.ageMs = 120_000L + rank * 37_000L % 600_000L;
				star.calledTier = Math.min(9, star.calledTier + (int) (star.ageMs / StarsModel.TIER_MS));
				star.remainingMs = (Integer) row[3] * 60_000L - 20_000L;
				star.precise = true;
				star.x = (Integer) row[4];
				star.y = (Integer) row[5];
				star.members = true;
				star.wilderness = star.name.startsWith("Lava Maze");
				star.game = "main";
				feed.rows.add(star);
			}
			return feed;
		}

		static RelayResponse.Route route()
		{
			RelayResponse.Route route = new RelayResponse.Route();
			route.origin = "shared";
			route.options = new ArrayList<>(Arrays.asList(
				way("teleport_item:a", "Colossal wyrm teleport scroll", true, false, 192,
					Steps.picture(step("method", "Use Colossal wyrm teleport scroll", null, false, 1647, 2931, null), "itemId", 30040),
					step("arrival-walk", "Walk 192t north-west", "To the destination", true, 1533, 3077, 0)),
				way("walk:b", "Sunset Coast", false, false, 361,
					step("method", "Sail to Sunset Coast", null, false, 1511, 2975, null),
					step("arrival-walk", "Walk 120t north", "To the destination", true, 1533, 3077, 0)),
				way("teleport_item:c", "Pendant of ates: 3. Ralos' Rise", false, true, 65,
					Steps.picture(step("method", "Rub Pendant of ates: 3. Ralos' Rise", null, false, 1480, 3090, null), "itemId", 29892),
					step("arrival-walk", "Walk 55t east", "To the destination", true, 1533, 3077, 0)),
				way("teleport_item:d", "Quetzal whistle: Cam Torum", false, true, 78,
					Steps.picture(step("method", "Blow Quetzal whistle: Cam Torum", null, false, 1446, 3108, null), "itemId", 29271),
					step("arrival-walk", "Walk 70t east", "To the destination", true, 1533, 3077, 0))));
			/* The names the website's chips wear for these two: the landing, with its dial spelled with a colon. */
			route.options.get(2).face = "3: Ralos' Rise";
			route.options.get(3).face = "Cam Torum";
			return route;
		}

		static RelayResponse.RouteOption way(String id, String label, boolean best, boolean blocked, int tiles, RelayResponse.RouteStep... steps)
		{
			RelayResponse.RouteOption way = new RelayResponse.RouteOption();
			way.id = id;
			way.label = label;
			way.best = best;
			way.blocked = blocked;
			way.tiles = tiles;
			way.steps = new ArrayList<>(Arrays.asList(steps));
			return way;
		}
	}

	static void search(MapCompanionPanel panel, String query) throws Exception
	{
		SearchPanel search = (SearchPanel) get(panel, "searchPanel");
		SearchClient client = (SearchClient) get(search, "search");
		String answer = query.equals("bank")
			? "{\"rows\":["
			+ row("bank:lumbridge", "bank", "Lumbridge Castle bank", "Bank · Lumbridge Castle · 2nd floor", 3208, 3220, 2, 40, "Places") + ","
			+ row("bank:draynor", "bank", "Draynor Village bank", "Bank · Draynor Village", 3092, 3243, 0, 95, "Places") + ","
			+ row("bank:al-kharid", "bank", "Al Kharid bank", "Bank · Al Kharid", 3269, 3167, 0, 108, "Places") + ","
			+ row("npc:banker", "npc", "Banker", "Character · nearest of 112 places", 3208, 3221, 2, 40, "Places") + ","
			+ row("shop:bobs-axes", "poi", "Bob's Brilliant Axes", "Shop · Lumbridge", 3230, 3203, 0, 22, "Sold at")
			+ "],\"items\":[{\"id\":\"item:Bank filler\",\"itemId\":20594,\"name\":\"Bank filler\",\"droppedBy\":0,\"soldAt\":1}]}"
			: "{\"rows\":["
			+ row("monster:Abyssal demon", "monster", "Abyssal demon", "Slayer Tower · nearest of 6", 3417, 3566, 2, 210, "Dropped by") + ","
			+ row("monster:Abyssal Sire", "monster", "Abyssal Sire", "Abyssal Nexus", 2969, 4772, 0, 380, "Dropped by")
			+ "],\"items\":[{\"id\":\"item:Abyssal whip\",\"name\":\"Abyssal whip\",\"droppedBy\":3,\"soldAt\":0}]}";
		SearchClient.Answer parsed = new Gson().fromJson(answer, SearchClient.Answer.class);
		List<SearchClient.Hit> hits = SearchClient.normalize(parsed);
		Component box = (Component) get(search, "box");
		if (box instanceof JTextField)
		{
			((JTextField) box).setText(query);
		}
		else
		{
			box.getClass().getMethod("setText", String.class).invoke(box, query);
		}
		Object pending = get(client, "pendingTimer");
		if (pending != null)
		{
			((Runnable) pending).run();
		}
		set(client, "pendingTimer", null);
		set(client, "result", new SearchClient.Result(query, hits, null, null, false));
		search.refresh();
	}

	static String row(String id, String kind, String name, String line, int x, int y, int plane, int tiles, String section)
	{
		return "{\"id\":\"" + id + "\",\"kind\":\"" + kind + "\",\"name\":\"" + name.replace("\"", "\\\"") + "\",\"line\":\"" + line
			+ "\",\"x\":" + x + ",\"y\":" + y + ",\"plane\":" + plane + ",\"effortTiles\":" + tiles + ",\"section\":\"" + section + "\"}";
	}

	/* ---- Painting ---- */

	static void shoot(JComponent panel, String path) throws Exception
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(panel, BorderLayout.NORTH);
		int height = Math.max(panel.getPreferredSize().height, 60);
		holder.setSize(225, height);
		layoutTree(holder);
		BufferedImage image = new BufferedImage(450, height * 2, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.scale(2, 2);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		holder.paint(graphics);
		graphics.dispose();
		ImageIO.write(image, "png", new File(path));
		System.out.println(path + " 225x" + height + " preferredWidth=" + panel.getPreferredSize().width);
	}

	static void layoutTree(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container)
			{
				layoutTree((Container) child);
			}
		}
	}

	/** The panel's private refresh, which queues its work on this thread. */
	static void refresh(MapCompanionPanel panel) throws Exception
	{
		Method method = MapCompanionPanel.class.getDeclaredMethod("refresh");
		method.setAccessible(true);
		method.invoke(panel);
	}

	static boolean press(Container parent, String name)
	{
		AbstractButton button = named(parent, name);
		if (button == null)
		{
			return false;
		}
		button.doClick();
		return true;
	}

	static AbstractButton named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof AbstractButton && name.equals(child.getName()))
			{
				return (AbstractButton) child;
			}
			if (child instanceof Container)
			{
				AbstractButton result = named((Container) child, name);
				if (result != null)
				{
					return result;
				}
			}
		}
		return null;
	}

	static void expand(Container parent, String text)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && ("▸ " + text).equals(((JButton) child).getText()))
			{
				((JButton) child).doClick();
			}
			else if (child instanceof Container)
			{
				expand((Container) child, text);
			}
		}
	}

	/* ---- Reflection, for the harness's fakes ---- */

	static Object allocate(Class<?> type) throws Exception
	{
		Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		f.setAccessible(true);
		Object unsafe = f.get(null);
		return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
	}

	static void set(Object target, String field, Object value) throws Exception
	{
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass())
		{
			try
			{
				Field f = c.getDeclaredField(field);
				f.setAccessible(true);
				f.set(target, value);
				return;
			}
			catch (NoSuchFieldException e)
			{
				/* Try the superclass. */
			}
		}
		throw new NoSuchFieldException(field);
	}

	static Object get(Object target, String field) throws Exception
	{
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass())
		{
			try
			{
				Field f = c.getDeclaredField(field);
				f.setAccessible(true);
				return f.get(target);
			}
			catch (NoSuchFieldException e)
			{
				/* Try the superclass. */
			}
		}
		throw new NoSuchFieldException(field);
	}
}
