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
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The one press after a first Connect: the recommended set is a press, never a default.
 *
 * <p>Every setting still starts off, so the reviewer notes stay true; what the press writes is
 * exactly the listed keys, as the group shortcuts write them; nothing is written until it is
 * pressed; and a player who connected before, or chose anything first, is never offered it.
 */
public class RecommendedSharingTest
{
	@Test
	public void everySettingThePressCanTurnOnStillStartsOff()
	{
		MapCompanionConfig fresh = new MapCompanionConfig()
		{
		};
		for (SharingFields.Field field : RecommendedSharing.all())
		{
			assertFalse("a sharing default stays off; the press is the opt-in: " + field.key, field.read.test(fresh));
		}
		assertFalse(fresh.notifyMeetups().isEnabled());
		assertTrue("a fresh install is offered the step", RecommendedSharing.offered(fresh));
	}

	/**
	 * While the step is up its own press is the page's one filled button, and Open the map is drawn
	 * plain, in the look and feel's own colours, rather than hidden; given the orange back, it is the
	 * filled one again. Before, the loudest thing on the page invited a new player to the browser
	 * before choosing, and the step's press was a grey button at its foot.
	 */
	@Test
	public void theStepsPressHasTheOrangeWhileItIsUp() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			AbstractButton share = (AbstractButton) named(fixture.step, "recommended.share");
			assertEquals(net.runelite.client.ui.ColorScheme.BRAND_ORANGE, share.getBackground());
			assertEquals(java.awt.Color.WHITE, share.getForeground());

			javax.swing.JButton openMap = new javax.swing.JButton("Open the map");
			RecommendedSharing.orange(openMap, false);
			assertTrue("plain is the look and feel's own", openMap.getBackground() instanceof javax.swing.plaf.UIResource);
			assertFalse(net.runelite.client.ui.ColorScheme.BRAND_ORANGE.equals(openMap.getBackground()));
			assertEquals("by its own name", "Open the map", openMap.getText());
			RecommendedSharing.orange(openMap, true);
			assertEquals(net.runelite.client.ui.ColorScheme.BRAND_ORANGE, openMap.getBackground());
			assertEquals(java.awt.Color.WHITE, openMap.getForeground());
		});
	}

	@Test
	public void onePressWritesExactlyTheRecommendedKeysAndNothingElse() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			assertTrue(fixture.step.isVisible());
			assertTrue("offering writes nothing", fixture.written.isEmpty());
			click(fixture.step, "recommended.share");
			assertEquals(new LinkedHashSet<>(RecommendedSharing.KEYS), fixture.written);
			for (String key : RecommendedSharing.KEYS) assertEquals(key, "true", fixture.store.get(key));
			assertFalse(fixture.step.isVisible());
			assertEquals(1, fixture.closed.get());
		});
	}

	@Test
	public void whereYouDiedAndMeetupNotificationsStayOffAfterThePress() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			assertFalse("offered, and left unticked", box(fixture.step, "recommended.shareDeath").isSelected());
			click(fixture.step, "recommended.share");
			assertFalse(fixture.config.shareDeath());
			assertFalse(fixture.config.notifyMeetups().isEnabled());
			assertNull(fixture.store.get("shareDeath"));
			assertNull(fixture.store.get("notifyMeetups"));
			assertNull("the press approves no server", fixture.store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	@Test
	public void thePressWritesLikeAGroupShortcutAndLeavesWhatIsAlreadyOn() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			/* Turned on in RuneLite's own settings while the step stood open. */
			fixture.config.set("sharePosition", true);
			click(fixture.step, "recommended.share");
			Set<String> expected = new LinkedHashSet<>(RecommendedSharing.KEYS);
			expected.remove("sharePosition");
			assertEquals("a setting already on is not written again", expected, fixture.written);
		});
	}

	/**
	 * Ticking and unticking rows changes nothing until the press, which then writes each ticked
	 * group's every field and nothing of an unticked one. Rewritten when the step lost Customise: a
	 * group is all of its fields or none, as its switch in Settings is, so a field is no longer
	 * unticked on its own.
	 */
	@Test
	public void untickingChangesNothingUntilTheShareIsPressed() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			box(fixture.step, "recommended.group.progress").doClick();
			box(fixture.step, "recommended.group.tasks").doClick();
			box(fixture.step, "recommended.shareDeath").doClick();
			box(fixture.step, "recommended.showGroupOnWorldMap").doClick();
			assertTrue("ticking and unticking in the step writes nothing", fixture.written.isEmpty());
			assertFalse(box(fixture.step, "recommended.group.progress").isSelected());
			assertFalse(box(fixture.step, "recommended.group.tasks").isSelected());

			click(fixture.step, "recommended.skip");
			assertTrue("Not now writes nothing", fixture.written.isEmpty());
			assertEquals(1, fixture.closed.get());

			fixture.step.offer("osrs-map.com");
			assertTrue("a second offer starts from the recommended set", box(fixture.step, "recommended.group.progress").isSelected());
			assertFalse(box(fixture.step, "recommended.shareDeath").isSelected());
			box(fixture.step, "recommended.group.tasks").doClick();
			box(fixture.step, "recommended.shareDeath").doClick();
			assertTrue(fixture.written.isEmpty());
			click(fixture.step, "recommended.share");
			Set<String> expected = new LinkedHashSet<>(RecommendedSharing.KEYS);
			expected.removeAll(keys(SharingFields.TASKS));
			expected.add("shareDeath");
			assertEquals(expected, new LinkedHashSet<>(fixture.written));
			assertEquals("true", fixture.store.get("shareDeath"));
			for (String task : keys(SharingFields.TASKS))
			{
				assertNull("nothing of an unticked group: " + task, fixture.store.get(task));
			}
		});
	}

	@Test
	public void anExistingPlayerIsNeverOfferedTheStepOrChangedByIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			/* Connected under an earlier version: the map is approved, and they chose two things. */
			Fixture fixture = new Fixture();
			Pairing pairing = new Pairing(fixture.store, new Gson());
			assertTrue(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> true));
			fixture.store.set("sharePosition", "true");
			fixture.store.set("shareSkills", "false");
			Map<String, String> before = fixture.values();
			fixture.written.clear();

			/* The update: the sidebar is built again and the player connects as usual. */
			new SharingControls(fixture.config, fixture.store);
			new RecommendedSharing(fixture.config, fixture.store, () -> { });
			AtomicInteger offers = new AtomicInteger();
			AtomicInteger notices = new AtomicInteger();
			assertTrue(RecommendedSharing.connect(pairing, JoinCode.parse("ABC234"), () -> notices.incrementAndGet() > 0,
				fixture.config, offers::incrementAndGet));
			assertEquals("an approved map shows no notice", 0, notices.get());
			assertEquals("so nothing is offered", 0, offers.get());
			assertTrue(fixture.written.isEmpty());
			assertEquals(before, fixture.values());

			/* One who connected before and shares nothing at all is not offered it either. */
			Fixture quiet = new Fixture();
			Pairing quietPairing = new Pairing(quiet.store, new Gson());
			assertTrue(quietPairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> true));
			assertTrue(RecommendedSharing.offered(quiet.config));
			assertTrue(RecommendedSharing.connect(quietPairing, JoinCode.parse("ABC234"), () -> true,
				quiet.config, offers::incrementAndGet));
			assertEquals("only the Connect that approved its map offers it", 0, offers.get());
		});
	}

	@Test
	public void aFirstConnectOffersItOnlyToAPlayerWhoHasChosenNothing() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fresh = new Fixture();
			AtomicInteger offers = new AtomicInteger();
			assertTrue(RecommendedSharing.connect(new Pairing(fresh.store, new Gson()), JoinCode.parse("ABC234-K7PQ2M3X"),
				() -> true, fresh.config, offers::incrementAndGet));
			assertEquals(1, offers.get());

			Fixture cancelled = new Fixture();
			assertFalse(RecommendedSharing.connect(new Pairing(cancelled.store, new Gson()), JoinCode.parse("ABC234-K7PQ2M3X"),
				() -> false, cancelled.config, offers::incrementAndGet));
			assertEquals("a cancelled notice offers nothing", 1, offers.get());

			for (SharingFields.Field field : offeredOrAsked())
			{
				Fixture chosen = new Fixture();
				chosen.config.set(field.key, true);
				assertFalse(field.key, RecommendedSharing.offered(chosen.config));
				assertTrue(RecommendedSharing.connect(new Pairing(chosen.store, new Gson()), JoinCode.parse("ABC234-K7PQ2M3X"),
					() -> true, chosen.config, offers::incrementAndGet));
				assertEquals("a player who chose " + field.key + " keeps their choice", 1, offers.get());
			}
		});
	}

	/**
	 * The closed card is one row per choice, so the press is in reach; every field and the stream
	 * in full are one tap below, in What each one sends. Rewritten when the card was made compact:
	 * the field lists moved from under the rows into the fold, word for word.
	 */
	@Test
	public void theClosedCardNamesEveryChoiceAndTheStreamItOpens() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			String closed = visibleText(fixture.step);
			for (SharingFields.Section section : SharingFields.GROUPS)
			{
				assertTrue("the card has a row for " + section.title, closed.contains(section.title));
			}
			for (SharingFields.Field field : new SharingFields.Field[]{SharingFields.NAME, SharingFields.DEATH,
				SharingFields.DESTINATIONS, SharingFields.GROUP_ON_WORLD_MAP})
			{
				assertTrue("the card has a row for " + field.key, closed.contains(field.label));
			}
			assertTrue(closed.contains(RecommendedSharing.LOOT_ROW));
			assertTrue("the stream is said on the card", closed.contains(RecommendedSharing.STREAM_SHORT));
			assertTrue("the map it goes to", closed.contains("Sent to osrs-map.com while you're logged in; your group sees it."));
			assertTrue("What each one sends is on the card", closed.contains("What each one sends"));
			assertTrue("and where to change it later", closed.contains(RecommendedSharing.INTRO));
			assertTrue(RecommendedSharing.INTRO.endsWith("later in Settings."));
			assertFalse("the field lists wait in the fold", closed.contains(RecommendedSharing.STREAM));
		});
	}

	@Test
	public void theFoldNamesEveryFieldItSharesAndTheStreamInFull() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			click(fixture.step, "recommended.detailsToggle");
			String open = visibleText(fixture.step);
			for (SharingFields.Field field : RecommendedSharing.all())
			{
				assertTrue("the fold names " + field.key + " as \"" + field.label + "\"", open.contains(field.label));
			}
			assertTrue(open.contains(RecommendedSharing.STREAM));
			assertTrue(open.contains("receive stream"));
			assertTrue("beside Drops and levels", open.contains(MapCompanionConfig.FEED_KEPT));
			assertTrue(open.contains(RecommendedSharing.DEATH_DETAIL));
			assertTrue(open.contains(RecommendedSharing.DESTINATIONS_DETAIL));
			assertTrue(open.contains(RecommendedSharing.GROUP_DETAIL));
			assertTrue("opening the fold writes nothing", fixture.written.isEmpty());

			/*
			 * Customise drew a box per field under each row; a group is all of its fields or none now,
			 * as its switch in Settings is, so there is no box for a field of a group, and no Customise.
			 */
			assertNull(named(fixture.step, "recommended.customise"));
			for (SharingFields.Section section : SharingFields.GROUPS)
			{
				assertNotNull(named(fixture.step, "recommended.group." + section.id));
				for (SharingFields.Field field : section.fields)
				{
					assertNull("no box of its own: " + field.key, named(fixture.step, "recommended." + field.key));
				}
			}
			box(fixture.step, "recommended.group.loot").doClick();
			assertTrue("an unticked group says the press shares none of it",
				visibleText(fixture.step).contains("Nothing from this group"));
			assertTrue(fixture.written.isEmpty());
		});
	}

	/**
	 * The press shares a written list, so a field added to a group fails here until somebody adds it
	 * to {@link RecommendedSharing#KEYS} and gives it its line in the README, docs/network.md and the
	 * reviewer notes, in the same commit.
	 */
	@Test
	public void theRecommendedSetIsTheGroupsTheNameAndTheDrawingAndNothingElse()
	{
		Set<String> expected = new LinkedHashSet<>();
		for (SharingFields.Section section : SharingFields.GROUPS) expected.addAll(keys(section));
		expected.add(SharingFields.NAME.key);
		expected.add(SharingFields.DESTINATIONS.key);
		expected.add(SharingFields.GROUP_ON_WORLD_MAP.key);
		assertEquals(expected, new LinkedHashSet<>(RecommendedSharing.KEYS));
		assertEquals(26, RecommendedSharing.KEYS.size());
		Set<String> configKeys = new HashSet<>();
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null) configKeys.add(item.keyName());
		}
		assertTrue("every key is an existing setting", configKeys.containsAll(RecommendedSharing.KEYS));
		assertFalse(RecommendedSharing.KEYS.contains("shareDeath"));
		assertFalse(RecommendedSharing.KEYS.contains("notifyMeetups"));
	}

	/**
	 * Squeezed, a column takes each row down to its minimum, and a plain panel's minimum is 10px: the
	 * step's 4 and 6px gaps grew while the rows beside them were cut, as the sidebar's other gaps did
	 * before theirs were given none. A gap holds its height.
	 */
	@Test
	public void aSqueezedStepNeverGrowsItsGaps() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Fixture fixture = new Fixture();
			fixture.step.offer("osrs-map.com");
			click(fixture.step, "recommended.detailsToggle");
			fixture.step.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH - 20, fixture.step.getPreferredSize().height - 4);
			layout(fixture.step);
			int checked = 0;
			for (Component gap : gaps(fixture.step, new ArrayList<>()))
			{
				checked++;
				assertTrue("a " + gap.getPreferredSize().height + "px gap drawn " + gap.getHeight() + "px tall",
					gap.getHeight() <= gap.getPreferredSize().height);
			}
			assertTrue("the step's gaps, above and below the rows and the press", checked >= 5);
		});
	}

	private static void layout(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}

	/** The spacers: empty panels one pixel wide, which is how the step's gap() is made. */
	private static List<Component> gaps(Container parent, List<Component> found)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof javax.swing.JPanel && ((Container) child).getComponentCount() == 0 && child.getPreferredSize().width == 1)
			{
				found.add(child);
			}
			else if (child instanceof Container)
			{
				gaps((Container) child, found);
			}
		}
		return found;
	}

	private static List<SharingFields.Field> offeredOrAsked()
	{
		List<SharingFields.Field> fields = new ArrayList<>(RecommendedSharing.all());
		fields.add(SharingFields.NOTIFY_MEETUPS);
		return fields;
	}

	private static Set<String> keys(SharingFields.Section section)
	{
		Set<String> keys = new LinkedHashSet<>();
		for (SharingFields.Field field : section.fields) keys.add(field.key);
		return keys;
	}

	/** A fresh install's settings, with every write recorded and the config following the store. */
	private static final class Fixture
	{
		final HarnessStore store = new HarnessStore();
		final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
		final Set<String> written = new LinkedHashSet<>();
		final AtomicInteger closed = new AtomicInteger();
		final RecommendedSharing step;

		Fixture()
		{
			store.onChanged(key ->
			{
				String value = store.get(key);
				if ("true".equals(value) || "false".equals(value))
				{
					config.set(key, Boolean.parseBoolean(value));
					written.add(key);
				}
			});
			step = new RecommendedSharing(config, store, closed::incrementAndGet);
		}

		/*
		 * Every choice, as stored, the meetup reminder's notification setting among them. The pairing's
		 * own keys move on a reconnect and are not the step's to keep.
		 */
		Map<String, String> values()
		{
			Map<String, String> values = new HashMap<>();
			for (Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				if (item != null && (method.getReturnType() == boolean.class || method.getReturnType() == Notification.class))
				{
					values.put(item.keyName(), store.get(item.keyName()));
				}
			}
			return values;
		}
	}

	private static void click(Container parent, String name)
	{
		Component found = named(parent, name);
		assertNotNull(name, found);
		((AbstractButton) found).doClick();
	}

	private static JCheckBox box(Container parent, String name)
	{
		Component found = named(parent, name);
		assertNotNull(name, found);
		return (JCheckBox) found;
	}

	private static Component named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName())) return child;
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null) return found;
			}
		}
		return null;
	}

	/** What a reader can see: the text of every label and button whose whole chain is visible. */
	private static String visibleText(Container parent)
	{
		StringBuilder text = new StringBuilder();
		for (Component child : parent.getComponents())
		{
			if (!child.isVisible()) continue;
			if (child instanceof JLabel) text.append(plain(((JLabel) child).getText())).append(' ');
			if (child instanceof AbstractButton) text.append(plain(((AbstractButton) child).getText())).append(' ');
			if (child instanceof Container) text.append(visibleText((Container) child));
		}
		return text.toString().replaceAll("\\s+", " ");
	}

	private static String plain(String html)
	{
		return html == null ? "" : html.replaceAll("<[^>]+>", " ").replace("&amp;", "&").replace("&#39;", "'")
			.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
	}
}
