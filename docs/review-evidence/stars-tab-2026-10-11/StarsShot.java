package com.pbolduc.mapcompanion;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * Paints the real Stars tab at RuneLite's 225px with the website's own projection of a fixed set
 * of calls — `src/test/resources/companion-stars.sample.json`, written by the map's
 * `scripts/generate-companion-star-fixture.ts`. The feed is handed to the model directly, so no
 * request is made and no game runs; the client thread never drains, which is why the sidebar's
 * eligibility notice is absent rather than "Log in".
 *
 * Built on SidebarShot (in the sharing-sidebar evidence folder) for the panel itself; compile the
 * two together. States: the collapsed list, a site opened with a world picked, that world pinned
 * with hopping on, and the filter fold open with a favourite that has no star right now.
 */
public class StarsShot
{
	static Object field(Object target, String name) throws Exception
	{
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(target);
	}

	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(RuneLiteLAF::setup);
		String feed = new String(Files.readAllBytes(Paths.get("src/test/resources/companion-stars.sample.json")), StandardCharsets.UTF_8);
		for (String state : new String[]{"list", "expanded", "pinned", "filters"})
		{
			SwingUtilities.invokeAndWait(() -> {
				try
				{
					MapCompanionPanel panel = SidebarShot.build("overview");
					if (state.equals("pinned"))
					{
						HarnessConfig config = (HarnessConfig) field(panel, "config");
						config.set("enableWorldHop", true);
					}
					StarsPanel stars = (StarsPanel) field(panel, "starsPanel");
					StarsClient client = (StarsClient) field(stars, "service");
					client.model.accept(new Gson().fromJson(feed, StarsModel.Feed.class));
					client.message = null;
					client.model.miningLevel = 72;
					client.model.skillTotal = 1620;
					if (state.equals("filters"))
					{
						client.model.preferences.favorites.add(new StarsModel.Favorite("crafting guild", "Crafting Guild"));
						client.model.preferences.filters.showFreeToPlay = false;
					}
					SidebarShot.named(panel, "companionView.stars").doClick();
					stars.refresh();
					if (!state.equals("list"))
					{
						for (StarsModel.Site site : client.model.sites())
						{
							if (site.name.equals("Rimmington mine") && !state.equals("filters"))
							{
								SidebarShot.named(panel, "stars.expand." + site.key).doClick();
								SidebarShot.named(panel, "stars.world." + site.worlds.get(1).id).doClick();
							}
						}
					}
					if (state.equals("pinned")) SidebarShot.named(panel, "stars.pin").doClick();
					if (state.equals("filters")) SidebarShot.named(panel, "stars.filters").doClick();
					SidebarShot.shoot(panel, args[0] + "/stars-" + state + ".png");
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			});
		}
		System.exit(0);
	}
}
