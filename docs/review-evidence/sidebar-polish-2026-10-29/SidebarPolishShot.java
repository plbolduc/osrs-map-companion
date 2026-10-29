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
 * Connect, a busy Home, the Group tab, Settings with Advanced open, Stars with a world picked, the
 * search answering "bank" and "whip", the death and clue cards, a meetup, and Drop luck alone.
 * Synthetic local state and made-up names; no game account and no network — the map address is a
 * port nothing listens on.
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
	static final List<String> STATES = Arrays.asList("fresh", "fresh-log", "connect", "home", "group", "settings",
		"stars", "search", "search-whip", "death", "meetup", "dropluck", "status");

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
			SwingUtilities.invokeAndWait(() -> call(() -> shoot(panel[0], args[0] + "/" + state + ".png")));
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
			config.set("sharePosition", true);
			config.set("shareSkills", true);
			config.set("showDestination", true);
		}
		Scenes.fill(state, injector, config, lootLog);
		return injector.getInstance(MapCompanionPanel.class);
	}

	/** What each state puts in the collaborators before the panel is built. */
	static final class Scenes
	{
		static void fill(String state, Injector injector, HarnessConfig config, LootLog log) throws Exception
		{
			DestinationState destinations = injector.getInstance(DestinationState.class);
			if (state.equals("home"))
			{
				route(injector, destinations);
				death(injector, config);
				social(destinations, false);
			}
			if (state.equals("group") || state.equals("meetup"))
			{
				social(destinations, state.equals("meetup"));
			}
			if (state.equals("death"))
			{
				death(injector, config);
			}
			if (state.equals("home") || state.equals("fresh-log") || state.equals("dropluck"))
			{
				loot(log);
			}
		}

		static void route(Injector injector, DestinationState destinations) throws Exception
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
			route.sync(destinations.getDestination());
			route.advance(new WorldPoint(3221, 3219, 0));
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

		/** The map's own description of a group of four (lib/contract/social.json), times moved to now. */
		static void social(DestinationState destinations, boolean twoMeetups) throws Exception
		{
			String social = new String(Files.readAllBytes(Paths.get("src/test/resources/contract/stream.json")), StandardCharsets.UTF_8);
			RelayResponse.StreamDirective directive = new Gson().fromJson(social, RelayResponse.StreamDirective.class);
			long now = System.currentTimeMillis();
			directive.roster.get(0).news.atMs = now - 5 * 60_000L;
			directive.roster.get(1).news.atMs = now - 9 * 60_000L;
			directive.roster.get(2).news.atMs = now - 41 * 60_000L;
			directive.events.get(0).startsAtMs = now + 80 * 60_000L;
			if (twoMeetups)
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
			Steps.icon(step("method", "Cast Lumbridge Home Teleport", "Standard spellbook", false, 3222, 3218, null,
				"Ten seconds to cast, and a combat hit stops it"), "spell:lumbridge-home-teleport"),
			step("walk-leg", "Walk to the castle's staircase", "18 tiles", true, 3206, 3209, null),
			step("walk-leg", "Climb to the bank", "2nd floor", true, 3208, 3220, 2)));
		RelayResponse.RouteOption ring = new RelayResponse.RouteOption();
		ring.id = "ring-of-dueling";
		ring.label = "Ring of dueling";
		ring.tiles = 80;
		ring.steps = new ArrayList<>(Collections.singletonList(
			Steps.icon(step("method", "Rub a ring of dueling", "Ferox Enclave", false, 3150, 3635, null), "item:2552")));
		RelayResponse.Route route = new RelayResponse.Route();
		route.origin = "shared";
		route.options = new ArrayList<>(Arrays.asList(best, ring));
		return route;
	}

	/** A step's icon where this build reads one; nothing where it does not. */
	static final class Steps
	{
		static RelayResponse.RouteStep icon(RelayResponse.RouteStep step, String icon)
		{
			try
			{
				Field field = RelayResponse.RouteStep.class.getDeclaredField("icon");
				field.setAccessible(true);
				field.set(step, icon);
			}
			catch (ReflectiveOperationException absent)
			{
				/* An older step reads no icon. */
			}
			return step;
		}
	}

	/* ---- What each state does to the built panel ---- */

	static void after(String state, MapCompanionPanel panel) throws Exception
	{
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
		if (state.equals("settings"))
		{
			if (!press(panel, "companion.settings"))
			{
				press(panel, "companionView.sharing");
			}
			expand(panel, "Advanced");
		}
		if (state.equals("stars"))
		{
			StarsPanel stars = (StarsPanel) get(panel, "starsPanel");
			StarsClient client = (StarsClient) get(stars, "service");
			String feed = new String(Files.readAllBytes(Paths.get("src/test/resources/companion-stars.sample.json")), StandardCharsets.UTF_8);
			client.model.accept(new Gson().fromJson(feed, StarsModel.Feed.class));
			client.message = null;
			client.model.miningLevel = 72;
			client.model.skillTotal = 1620;
			press(panel, "companionView.stars");
			stars.refresh();
			for (StarsModel.Site site : client.model.sites())
			{
				if (site.name.equals("Rimmington mine"))
				{
					press(panel, "stars.expand." + site.key);
					press(panel, "stars.world." + site.worlds.get(0).id);
				}
			}
		}
		if (state.startsWith("search"))
		{
			search(panel, state.equals("search") ? "bank" : "whip");
		}
		refresh(panel);
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
