package com.pbolduc.mapcompanion;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteLAF;
import okhttp3.OkHttpClient;

/**
 * Paints the real MapCompanionPanel at 225px wide with RuneLite's own look and feel, in the states
 * the where-to-go plan's steps 5a, 6, 8 and 9 added: a destination with its steps, the search box
 * with results, the Group section with three friends, the meetups with their answers, and the
 * death card's Route back. Synthetic local state and made-up names; no game account or network.
 *
 * Built like sharing-sidebar-2026-10-11/SidebarShot.java, whose helpers it copies rather than
 * imports, because that one still names a world-hop class this branch no longer has. Reproduce
 * from the plugin root after `gradlew harnessJar`, with the harness classpath and that folder's
 * logback.xml (docs/sidebar-layout.md has the PowerShell and shell forms):
 *
 *   javac -proc:none --release 11 -cp $cp -d build/where-shot-classes docs/review-evidence/where-to-go-2026-10-29/WhereToGoShot.java
 *   java -Djava.awt.headless=true -Dlogback.configurationFile=... -cp "build/where-shot-classes;$cp" com.pbolduc.mapcompanion.WhereToGoShot <out>
 */
public class WhereToGoShot
{
	static Object allocate(Class<?> type) throws Exception
	{
		Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		f.setAccessible(true);
		Object unsafe = f.get(null);
		return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
	}

	static void set(Object target, String field, Object value) throws Exception
	{
		Class<?> c = target.getClass();
		while (c != null)
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
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(field);
	}

	static Object get(Object target, String field) throws Exception
	{
		Class<?> c = target.getClass();
		while (c != null)
		{
			try
			{
				Field f = c.getDeclaredField(field);
				f.setAccessible(true);
				return f.get(target);
			}
			catch (NoSuchFieldException e)
			{
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(field);
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
			step("method", "Cast Lumbridge Home Teleport", "Standard spellbook", false, 3222, 3218, null, "Ten seconds to cast, and a combat hit stops it"),
			step("walk-leg", "Walk to the castle's staircase", "18 tiles", true, 3206, 3209, null),
			step("walk-leg", "Climb to the bank", "2nd floor", true, 3208, 3220, 2)));
		RelayResponse.RouteOption ring = new RelayResponse.RouteOption();
		ring.id = "ring-of-dueling";
		ring.label = "Ring of dueling";
		ring.tiles = 80;
		ring.steps = new ArrayList<>(Collections.singletonList(step("method", "Rub a ring of dueling", "Ferox Enclave", false, 3150, 3635, null)));
		RelayResponse.Route route = new RelayResponse.Route();
		route.origin = "shared";
		route.options = new ArrayList<>(Arrays.asList(best, ring));
		return route;
	}

	static MapCompanionPanel build(String state) throws Exception
	{
		HarnessGame game = new HarnessGame();
		HarnessStore store = new HarnessStore();
		/* A port nothing listens on: a stray request from a debounce or a press goes nowhere. */
		HarnessConfig config = new HarnessConfig(store, "http://127.0.0.1:9");
		Gson gson = new Gson();
		MapCompanionPlugin plugin = (MapCompanionPlugin) allocate(MapCompanionPlugin.class);
		ConfigManager configManager = (ConfigManager) allocate(ConfigManager.class);
		DestinationMarker marker = (DestinationMarker) allocate(DestinationMarker.class);
		net.runelite.client.game.ItemManager itemManager = (net.runelite.client.game.ItemManager) allocate(net.runelite.client.game.ItemManager.class);
		LootLog lootLog = new LootLog(game.client(), store, gson);
		Pairing pairing = new Pairing(store, gson, config::relayUrl);
		Injector injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(game.client());
				bind(Gson.class).toInstance(gson);
				bind(OkHttpClient.class).toInstance(new OkHttpClient());
				bind(MapCompanionConfig.class).toInstance(config);
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
			}
		});
		game.login("Sunday", 302, 3222, 3218, 0);
		pairing.join(JoinCode.parse("KQX482"));
		pairing.approveServer();
		pairing.rememberName("KQX482", "Sunday raids");
		MemberIdentity identity = injector.getInstance(MemberIdentity.class);
		pairing.rememberSecret("KQX482", identity.id(), "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23");
		set(plugin, "status", "Connected");
		set(plugin, "health", Health.LIVE);
		config.set("sharePosition", true);
		config.set("shareSkills", true);
		config.set("showDestination", true);
		Scenes.fill(state, injector, config);
		return injector.getInstance(MapCompanionPanel.class);
	}

	/** What each state puts in the panel's collaborators before it is built. */
	static final class Scenes
	{
		static void fill(String state, Injector injector, HarnessConfig config) throws Exception
		{
			DestinationState destinations = injector.getInstance(DestinationState.class);
			DestinationRoute route = injector.getInstance(DestinationRoute.class);
			if (state.equals("route"))
			{
				RelayResponse.Directive directive = new RelayResponse.Directive();
				directive.destination = new RelayResponse.Destination();
				directive.destination.name = "Lumbridge Castle bank";
				directive.destination.x = 3208;
				directive.destination.y = 3220;
				directive.destination.plane = 2;
				directive.destination.note = "From Cara";
				directive.destination.route = route();
				destinations.accept(directive);
				route.sync(destinations.getDestination());
				route.advance(new WorldPoint(3221, 3219, 0));
			}
			if (state.equals("group") || state.equals("meetup"))
			{
				/* The map's own description of a group of four (lib/contract/social.json), times moved to now. */
				String social = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/contract/stream.json")),
					java.nio.charset.StandardCharsets.UTF_8);
				RelayResponse.StreamDirective directive = new Gson().fromJson(social, RelayResponse.StreamDirective.class);
				long now = System.currentTimeMillis();
				directive.roster.get(0).news.atMs = now - 5 * 60_000L;
				directive.roster.get(1).news.atMs = now - 9 * 60_000L;
				directive.roster.get(2).news.atMs = now - 41 * 60_000L;
				directive.events.get(0).startsAtMs = now + 80 * 60_000L;
				if (state.equals("meetup"))
				{
					directive.events.get(0).mine = null;
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
				directive.event = null;
				destinations.acceptSocial(directive, true);
			}
			if (state.equals("death"))
			{
				config.set("shareDeath", false);
				DeathWatcher deaths = injector.getInstance(DeathWatcher.class);
				set(deaths, "diedAt", new WorldPoint(2272, 4049, 0));
				set(deaths, "diedAtMs", System.currentTimeMillis() - 7 * 60_000L);
				CompanionSnapshot.Clue clue = new CompanionSnapshot.Clue();
				clue.tier = "hard";
				clue.text = "01 degrees 18 minutes south 14 degrees 15 minutes east";
				clue.coordTile = ClueReader.parseCoordinate(clue.text);
				set(injector.getInstance(ClueReader.class), "current", clue);
			}
		}
	}

	static void layoutTree(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container) layoutTree((Container) child);
		}
	}

	static void shoot(JComponent panel, String path) throws Exception
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(panel, BorderLayout.NORTH);
		int height = Math.max(panel.getPreferredSize().height, 100);
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

	/** Run the panel's private refresh now, then drain what it queued on the event thread. */
	static void refresh(MapCompanionPanel panel) throws Exception
	{
		Method method = MapCompanionPanel.class.getDeclaredMethod("refresh");
		method.setAccessible(true);
		method.invoke(panel);
	}

	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(RuneLiteLAF::setup);
		List<String> states = args.length > 1 ? Arrays.asList(args).subList(1, args.length)
			: Arrays.asList("route", "search", "death", "star", "group", "meetup");
		for (String state : states)
		{
			MapCompanionPanel[] panel = new MapCompanionPanel[1];
			SwingUtilities.invokeAndWait(() -> {
				try { panel[0] = build(state); }
				catch (Exception e) { throw new RuntimeException(e); }
			});
			SwingUtilities.invokeAndWait(() -> {
				try { Interact.after(state, panel[0]); }
				catch (Exception e) { throw new RuntimeException(e); }
			});
			SwingUtilities.invokeAndWait(() -> { });
			SwingUtilities.invokeAndWait(() -> {
				try { shoot(panel[0], args[0] + "/where-" + state + ".png"); }
				catch (Exception e) { throw new RuntimeException(e); }
			});
		}
		System.exit(0);
	}

	/** What each state does to the built panel: fill the search's answer, open a form. */
	static final class Interact
	{
		static void after(String state, MapCompanionPanel panel) throws Exception
		{
			if (state.equals("search"))
			{
				SearchPanel search = (SearchPanel) get(panel, "searchPanel");
				SearchClient client = (SearchClient) get(search, "search");
				List<SearchClient.Hit> hits = new ArrayList<>();
				hits.add(new SearchClient.Hit("bank:lumbridge", "Lumbridge Castle bank", "Bank · Lumbridge Castle · 2nd floor · ~40 tiles", null, new int[]{3208, 3220, 2}));
				hits.add(new SearchClient.Hit("bank:draynor", "Draynor Village bank", "Bank · Draynor Village · ~95 tiles", null, new int[]{3092, 3243, 0}));
				hits.add(new SearchClient.Hit("bank:al-kharid", "Al Kharid bank", "Bank · Al Kharid · ~108 tiles", null, new int[]{3269, 3167, 0}));
				hits.add(new SearchClient.Hit("npc:banker", "Banker", "Character · nearest of 112 places · ~40 tiles", null, new int[]{3208, 3221, 2}));
				hits.add(new SearchClient.Hit("shop:bobs-axes", "Bob's Brilliant Axes", "Shop · Lumbridge · ~22 tiles", "Sold at", new int[]{3230, 3203, 0}));
				hits.add(new SearchClient.Hit("item:Bank filler", "Bank filler", "Item · sold at 1", null, null));
				search.box().setText("bank");
				/* Cancel the debounce the typing started, then stand the answer in for one. */
				Object pending = get(client, "pendingTimer");
				if (pending != null) ((Runnable) pending).run();
				set(client, "pendingTimer", null);
				set(client, "result", new SearchClient.Result("bank", hits, null, null, false));
				search.refresh();
			}
			if (state.equals("meetup"))
			{
				GroupPanel group = (GroupPanel) get(panel, "groupPanel");
				group.openForm(true);
				group.title().setText("Barrows run");
			}
			if (state.equals("star"))
			{
				StarsPanel stars = (StarsPanel) get(panel, "starsPanel");
				StarsClient client = (StarsClient) get(stars, "service");
				String feed = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/companion-stars.sample.json")), java.nio.charset.StandardCharsets.UTF_8);
				client.model.accept(new Gson().fromJson(feed, StarsModel.Feed.class));
				client.message = null;
				client.model.miningLevel = 72;
				client.model.skillTotal = 1620;
				named(panel, "companionView.stars").doClick();
				stars.refresh();
				for (StarsModel.Site site : client.model.sites())
				{
					if (site.name.equals("Rimmington mine"))
					{
						named(panel, "stars.expand." + site.key).doClick();
						named(panel, "stars.world." + site.worlds.get(0).id).doClick();
					}
				}
			}
			refresh(panel);
		}
	}

	static AbstractButton named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof AbstractButton && name.equals(child.getName())) return (AbstractButton) child;
			if (child instanceof Container)
			{
				AbstractButton result = named((Container) child, name);
				if (result != null) return result;
			}
		}
		return null;
	}
}
