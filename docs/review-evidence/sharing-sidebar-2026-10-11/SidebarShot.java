package com.pbolduc.mapcompanion;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteLAF;
import okhttp3.OkHttpClient;

/**
 * Paints the actual MapCompanionPanel with RuneLite 1.13.1's look and feel at 225px wide.
 * The Overview, grouped Sharing, individual choices and Advanced views use synthetic local
 * state; neither a game account nor a network connection is opened. Heights are printed at 1x
 * and PNGs are painted at 2x. The status is set because no game ticks run in this fixture.
 *
 * It lives in this package because the panel's constructor and the harness's stand-ins are
 * package-private. The collaborators a Guice binding would member-inject — the plugin, the config
 * manager, the marker, the hopper — are allocated without their constructors and bound through
 * providers, since binding them as instances would inject them, and they need a live client.
 *
 * Reproduction commands are in docs/sidebar-layout.md. The harness's generated classpath includes
 * RuneLite's FlatLaf fork. logback.xml disables client logging so the fixture writes only its PNGs.
 */
public class SidebarShot
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

	static MapCompanionPanel build(String state) throws Exception
	{
		HarnessGame game = new HarnessGame();
		HarnessStore store = new HarnessStore();
		HarnessConfig config = new HarnessConfig(store, "https://osrs-map.com");
		Gson gson = new Gson();
		MapCompanionPlugin plugin = (MapCompanionPlugin) allocate(MapCompanionPlugin.class);
		ConfigManager configManager = (ConfigManager) allocate(ConfigManager.class);
		DestinationMarker marker = (DestinationMarker) allocate(DestinationMarker.class);
		WorldHopper hopper = (WorldHopper) allocate(WorldHopper.class);
		/* Drop luck's sprites: with no item cache it draws names, which is all a layout needs. */
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
				/* Providers, not instances: toInstance would member-inject these, which need a client. */
				bind(MapCompanionPlugin.class).toProvider(() -> plugin);
				bind(ConfigManager.class).toProvider(() -> configManager);
				bind(DestinationMarker.class).toProvider(() -> marker);
				bind(WorldHopper.class).toProvider(() -> hopper);
				bind(net.runelite.client.game.ItemManager.class).toProvider(() -> itemManager);
			}
		});

		boolean paired = !state.equals("fresh");
		if (paired)
		{
			pairing.join(JoinCode.parse("PMN934"));
			pairing.join(JoinCode.parse("KQX482"));
			pairing.approveServer();
			pairing.rememberName("KQX482", "Sunday raids");
			if (state.equals("menu"))
			{
				/* What the map's replies would have filed: one named group, one named after its code. */
				pairing.approveServer();
			pairing.rememberName("KQX482", "Sunday raids");
				pairing.rememberName("PMN934", "Clan hunt");
			}
			set(plugin, "status", "Connected to osrs-map.com");
			set(plugin, "health", Health.LIVE);
			config.set("sharePosition", true);
			config.set("shareSkills", true);
		}
		else
		{
			set(plugin, "status", "Not paired");
			set(plugin, "health", Health.IDLE);
		}
		if (state.equals("all"))
		{
			for (java.lang.reflect.Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				net.runelite.client.config.ConfigItem item = method.getAnnotation(net.runelite.client.config.ConfigItem.class);
				if (item != null && item.keyName().startsWith("share")) config.set(item.keyName(), true);
			}
		}
		if (state.equals("client"))
		{
			config.set("showDestination", true);
			config.set("showGroupOnWorldMap", true);
		}
		if (state.startsWith("busy"))
		{
			config.set("showDestination", true);
			config.set("notifyMeetups", true);
			if (state.equals("busy-hop")) config.set("enableWorldHop", true);
			config.set(MapCompanionConfig.KEY_SHARE_NAME, false);
			config.set("shareDeath", false);
			RelayResponse.Directive directive = new RelayResponse.Directive();
			directive.destination = new RelayResponse.Destination();
			directive.destination.name = "Rimmington mine";
			directive.destination.world = 302;
			directive.destination.note = "T9 star, ~40m left";
			directive.event = new RelayResponse.Event();
			directive.event.title = "Star hunt at the bank";
			directive.event.startsAtMs = System.currentTimeMillis() + (state.equals("busy-far") ? 26 * 60 : 45) * 60_000L;
			directive.event.world = 444;
			directive.event.x = 3185; directive.event.y = 3436;
			injector.getInstance(DestinationState.class).accept(directive);
			DeathWatcher deaths = injector.getInstance(DeathWatcher.class);
			set(deaths, "diedAt", new WorldPoint(3222, 3218, 0));
			set(deaths, "diedAtMs", System.currentTimeMillis() - 7 * 60_000L);
		}
		game.login("Bob", 302, 3222, 3218, 0);
		if (!state.equals("fresh"))
		{
			game.nameItem(526, "Bones");
			for (int i = 0; i < 1_300; i++)
			{
				List<ItemStack> items = new ArrayList<>();
				items.add(new ItemStack(526, 1));
				lootLog.record(new ServerNpcLoot(game.npc("Aberrant spectre"), items));
			}
		}
		System.out.println(state + " scope=[" + store.scopeKey() + "] types=" + game.client().getWorldType() + " summary=" + lootLog.summary() + " hasLog=" + lootLog.hasLog());
		return injector.getInstance(MapCompanionPanel.class);
	}

	static void layoutTree(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container) layoutTree((Container) child);
		}
	}

	static void expand(Container parent, String text)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && ((JButton) child).getText().equals("▸ " + text))
			{
				((JButton) child).doClick();
			}
			else if (child instanceof Container) expand((Container) child, text);
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
		System.out.println(path + " 225x" + height);
	}

	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(RuneLiteLAF::setup);
		for (String state : new String[]{"fresh", "overview", "sharing", "presence", "progress", "tasks", "loot", "privacy", "client", "all", "advanced", "busy", "busy-hop"})
		{
			MapCompanionPanel[] panel = new MapCompanionPanel[1];
			SwingUtilities.invokeAndWait(() -> {
				try { panel[0] = build(state); }
				catch (Exception e) { throw new RuntimeException(e); }
			});
			SwingUtilities.invokeAndWait(() -> { });
			SwingUtilities.invokeAndWait(() -> {
				try {
					if (!state.equals("fresh") && !state.equals("overview") && !state.equals("advanced") && !state.startsWith("busy"))
					{
						named(panel[0], "companionView.sharing").doClick();
					}
					if (state.equals("presence") || state.equals("progress") || state.equals("tasks") || state.equals("loot"))
					{
						named(panel[0], "customise." + state).doClick();
					}
					if (state.equals("privacy")) expand(panel[0], "Name and death location");
					/* Destinations and the group on the world map on, as somebody using both would have them. */
					if (state.equals("client")) expand(panel[0], "In the client");
					if (state.equals("advanced")) expand(panel[0], "Advanced");
					shoot(panel[0], args[0] + "/plugin-" + state + ".png");
				} catch (Exception e) { throw new RuntimeException(e); }
			});
		}
		System.exit(0);
	}
}
