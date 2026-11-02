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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;
import org.junit.Test;
import static org.junit.Assert.*;

/** Choices select data without dialogs; Connect alone grants network approval for the server. */
public class SharingControlsTest
{
	@Test
	public void openingFoldsCannotEnableAnythingAndTheSidebarCoversAllRemoteChoices() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			SharingControls controls = new SharingControls(config, store);
			Set<String> actual = new HashSet<>();
			collect(controls, actual);
			Set<String> expected = new HashSet<>();
			for (Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				/*
				 * Every box RuneLite's list shows, the meetup reminder's notification setting among them,
				 * and the fields under the four groups, which the list hides and Settings still draws.
				 */
				if (item != null && (!item.hidden() || item.keyName().startsWith("share"))
					&& (method.getReturnType() == boolean.class || method.getReturnType() == Notification.class))
				{
					expected.add(item.keyName());
				}
			}
			assertEquals(expected, actual);
			/*
			 * 28 data and client choices; Route here on the world map, which asks the map only on a press
			 * of its own, as every Route does; the five that only draw on this client: Drop luck's
			 * chat line, the destination's hint arrow, the followed step's box over the game, a farm
			 * run's teleports outlined in the bank, and a followed route's walks handed to Shortest
			 * Path, which is a plugin message inside the client rather than anything sent; and the four
			 * groups' boxes, named after the switches RuneLite's list stores for them, so the sidebar is
			 * held to those too.
			 */
			assertEquals(38, actual.size());
			for (String key : actual)
			{
				if (key.startsWith("share")) assertFalse("every data choice starts off: " + key, defaultOf(key));
			}
			for (String id : new String[]{"presence", "progress", "tasks", "loot"})
			{
				JPanel contents = (JPanel) named(controls, "sharingOptions." + id);
				assertFalse(contents.isVisible());
				((JButton) named(controls, "customise." + id)).doClick();
				assertTrue(contents.isVisible());
				assertAllAtDefaults(controls);
				((JButton) named(controls, "customise." + id)).doClick();
				assertFalse(contents.isVisible());
			}
			for (String title : new String[]{"Name and death location", "In the client"})
			{
				JButton header = buttonWithText(controls, "▸ " + title);
				assertNotNull(header);
				header.doClick();
				assertAllAtDefaults(controls);
			}
			for (String key : actual)
			{
				assertNull("opening settings cannot write " + key, store.get(key));
			}
		});
	}

	@Test
	public void presenceGroupSelectsOnlyItsFieldsAndSupportsPartialSelection() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			Pairing pairing = new Pairing(store, new Gson());
			SharingControls controls = new SharingControls(config, store);
			JCheckBox master = find(controls, MapCompanionConfig.KEY_SHARE_MAP_PRESENCE);
			master.doClick();
			assertTrue(config.sharePosition());
			assertTrue(config.shareActivity());
			assertTrue(config.shareAccountType());
			assertFalse(config.shareName());
			assertFalse(config.shareDeath());
			assertFalse(config.shareSkills());
			assertFalse(config.showDestination());
			assertFalse(config.notifyMeetups().isEnabled());
			assertFalse(pairing.hasServerConsent());
			assertEquals("All 3 selected", ((javax.swing.JLabel) named(controls, "shareCount.presence")).getText());
			find(controls, "sharePosition").doClick();
			assertFalse(config.sharePosition());
			assertFalse(master.isSelected());
			assertEquals("indeterminate", master.getClientProperty("JButton.selectedState"));
			assertEquals("2 of 3 selected", ((javax.swing.JLabel) named(controls, "shareCount.presence")).getText());
			master.doClick();
			assertTrue(config.sharePosition());
			assertNull(master.getClientProperty("JButton.selectedState"));
			master.doClick();
			assertFalse(config.sharePosition());
			assertFalse(config.shareActivity());
			assertFalse(config.shareAccountType());
			assertNull("the box writes fields; the switch RuneLite's list shows follows them in the plugin",
				store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	/**
	 * Each group's box writes exactly its own fields. Every setting is checked, the hidden fields
	 * included, since the fields are what is sent; the group's own switch is the plugin's to write from
	 * those fields, never the box's.
	 */
	@Test
	public void everyOtherGroupWritesOnlyItsKnownIndividualOptIns() throws Exception
	{
		String[][] groupKeys = {
			{MapCompanionConfig.KEY_SHARE_PROGRESS, "shareSkills", "shareQuests", "shareQuestLog", "shareDiaries", "sharePermanentTravel", "shareCapabilities", "shareStash", "shareBoats"},
			{MapCompanionConfig.KEY_SHARE_TASKS, "shareSlayer", "shareClue", "shareFarming", "shareBirdhouses", "shareHunterRumours", "shareMahoganyHomes", "shareFarmingContracts", "sharePortTasks"},
			{MapCompanionConfig.KEY_SHARE_LOOT_GROUP, "shareLoot", "shareMilestones", "sharePets", "shareCollectionLog"}
		};
		for (String[] group : groupKeys)
		{
			SwingUtilities.invokeAndWait(() ->
			{
				HarnessStore store = new HarnessStore();
				HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
				followStoredSettings(store, config);
				SharingControls controls = new SharingControls(config, store);
				find(controls, group[0]).doClick();
				Set<String> selected = new HashSet<>(java.util.Arrays.asList(group).subList(1, group.length));
				for (Method method : MapCompanionConfig.class.getDeclaredMethods())
				{
					ConfigItem item = method.getAnnotation(ConfigItem.class);
					// The meetup reminder is a notification setting with a box like any other.
					if (item == null || (method.getReturnType() != boolean.class && method.getReturnType() != Notification.class)) continue;
					assertEquals(item.keyName(), selected.contains(item.keyName()) ? "true" : null, store.get(item.keyName()));
					/* Hidden Slayer list preferences are owned by the task card and have no box here. */
					JCheckBox shown = find(controls, item.keyName());
					if (shown != null)
					{
						assertEquals(item.keyName(), selected.contains(item.keyName()) || item.keyName().equals(group[0])
							|| defaultOf(item.keyName()), shown.isSelected());
					}
				}
				assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
			});
		}
	}

	@Test
	public void aSettingsChangeIsReflectedWithoutWritingAnotherChoice() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			SharingControls controls = new SharingControls(config, store);
			config.set("sharePosition", true);
			controls.refresh();
			assertTrue(find(controls, "sharePosition").isSelected());
			assertEquals("indeterminate", find(controls, MapCompanionConfig.KEY_SHARE_MAP_PRESENCE).getClientProperty("JButton.selectedState"));
			assertFalse(find(controls, "shareName").isSelected());
			config.set("sharePosition", false);
			controls.refresh();
			assertFalse(find(controls, "sharePosition").isSelected());
			assertNull(find(controls, MapCompanionConfig.KEY_SHARE_MAP_PRESENCE).getClientProperty("JButton.selectedState"));
			assertNull(store.get("sharePosition"));
		});
	}

	/** The box is only what the group sees while the name is off, so only then is it there. */
	@Test
	public void theNameBoxShowsOnlyWhileTheCharacterNameIsOff() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			config.nickname("Me");
			SharingControls controls = new SharingControls(config, store);
			JTextField box = (JTextField) named(controls, "nickname");
			assertTrue(box.isVisible());
			/* A saved "Me" is the map's word for none, so the box shows it as empty. */
			assertEquals("", box.getText());

			config.set(MapCompanionConfig.KEY_SHARE_NAME, true);
			controls.refresh();
			assertFalse(box.isVisible());

			config.nickname("Scout");
			config.set(MapCompanionConfig.KEY_SHARE_NAME, false);
			controls.refresh();
			assertTrue(box.isVisible());
			assertEquals("Scout", box.getText());
		});
	}

	/**
	 * Replaces per-checkbox modal assertions: a choice must never bypass the Connect disclosure.
	 *
	 * <p>Every box the sidebar draws, by the setting it is named after: a group's box writes its
	 * fields, which are what it stands for, and any other box its own setting. Each box flips from
	 * whatever it shows when pressed, since a group's press moves the boxes of its fields too.
	 */
	@Test
	public void everySidebarChoiceSavesWithoutADialogButCannotApproveTheServer() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			Pairing pairing = new Pairing(store, new Gson());
			pairing.join(JoinCode.parse("ABC234-K7PQ2M3X"));
			SharingControls controls = new SharingControls(config, store);
			int pressed = 0;
			for (Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				// The meetup reminder is a notification setting with a box like any other.
				if (item == null || (method.getReturnType() != boolean.class && method.getReturnType() != Notification.class)) continue;
				JCheckBox box = find(controls, item.keyName());
				/* Hidden Slayer list preferences are owned by the task card, not sharing controls. */
				assertEquals(item.keyName(), !item.hidden() || item.keyName().startsWith("share"), box != null);
				if (box == null) continue;
				boolean flipped = !box.isSelected();
				box.doClick();
				controls.refresh();
				pressed++;
				assertEquals(item.keyName(), flipped, box.isSelected());
				SharingFields.Section group = null;
				for (SharingFields.Section section : SharingFields.GROUPS)
				{
					if (section.key.equals(item.keyName())) group = section;
				}
				if (group == null)
				{
					assertEquals(Boolean.toString(flipped), store.get(item.keyName()));
				}
				else
				{
					for (SharingFields.Field field : group.fields)
					{
						assertEquals(field.key, flipped, field.read.test(config));
					}
				}
				assertFalse("choosing data is not permission to connect", pairing.hasServerConsent());
			}
			assertEquals(38, pressed);
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	@Test
	public void changingSharingAfterConnectDoesNotAskAgainOrEnableOtherFields() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			Pairing pairing = new Pairing(store, new Gson());
			AtomicInteger disclosures = new AtomicInteger();
			assertTrue(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> { disclosures.incrementAndGet(); return true; }));
			SharingControls controls = new SharingControls(config, store);
			JCheckBox position = find(controls, "sharePosition");
			position.doClick();
			assertTrue(config.sharePosition());
			assertFalse(config.shareName());
			assertFalse(config.shareSkills());
			assertTrue(pairing.hasServerConsent());
			position.doClick();
			assertFalse(config.sharePosition());
			position.doClick();
			assertTrue(config.sharePosition());
			assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> { fail("server already disclosed"); return false; }));
			assertEquals(1, disclosures.get());
		});
	}

	@Test
	public void cancellingConnectKeepsSelectedPreferencesButLeavesRequestsPaused() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			Pairing pairing = new Pairing(store, new Gson());
			SharingControls controls = new SharingControls(config, store);
			find(controls, "sharePosition").doClick();
			assertFalse(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> false));
			controls.refresh();
			assertTrue(config.sharePosition());
			assertTrue(find(controls, "sharePosition").isSelected());
			assertEquals("", pairing.code());
			assertFalse(pairing.hasServerConsent());
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	/**
	 * The meetup box writes a RuneLite notification setting as one, through the store that knows how
	 * (see {@link NotificationChoice}), and never as the word a plain box writes, which RuneLite reads
	 * as on with its own defaults: held while the game is focused, and so heard by nobody playing.
	 */
	@Test
	public void theMeetupBoxIsWrittenAsANotification() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore held = new HarnessStore();
			HarnessConfig config = new HarnessConfig(held, MapCompanionConfig.DEFAULT_MAP_URL);
			java.util.List<String> chosen = new java.util.ArrayList<>();
			ConfigStore store = new ConfigStore()
			{
				@Override
				public String get(String key)
				{
					return held.get(key);
				}

				@Override
				public void set(String key, String value)
				{
					held.set(key, value);
				}

				@Override
				public void unset(String key)
				{
					held.unset(key);
				}

				@Override
				public void chooseNotification(String key, boolean on)
				{
					chosen.add(key + "=" + on);
					config.set(key, on);
				}
			};
			SharingControls controls = new SharingControls(config, store);
			JCheckBox box = find(controls, "notifyMeetups");
			assertFalse(box.isSelected());
			box.doClick();
			assertNull("never the word a plain box writes", held.get("notifyMeetups"));
			assertEquals(java.util.Collections.singletonList("notifyMeetups=true"), chosen);
			assertTrue(box.isSelected());
			assertTrue(config.notifyMeetups().isEnabled());
			box.doClick();
			assertFalse(box.isSelected());
			assertEquals(java.util.Arrays.asList("notifyMeetups=true", "notifyMeetups=false"), chosen);
			assertNull(held.get("notifyMeetups"));
		});
	}

	/** RuneLite's config proxy reads persisted choices; the harness keeps its toggles separately. */
	private static void followStoredSettings(HarnessStore store, HarnessConfig config)
	{
		store.onChanged(key ->
		{
			String value = store.get(key);
			if ("true".equals(value) || "false".equals(value)) config.set(key, Boolean.parseBoolean(value));
		});
	}

	private static void collect(Container parent, Set<String> keys)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JCheckBox)
			{
				String key = child.getName();
				assertTrue("one checkbox per setting", keys.add(key));
				assertEquals(key, isConfigKey(key) && defaultOf(key), ((JCheckBox) child).isSelected());
			}
			else if (child instanceof Container) collect((Container) child, keys);
		}
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

	private static JButton buttonWithText(Container parent, String text)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child;
			if (child instanceof Container)
			{
				JButton found = buttonWithText((Container) child, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	/** Every choice as a fresh install has it: data choices off, the client's own as they ship. */
	private static void assertAllAtDefaults(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JCheckBox)
			{
				String key = child.getName();
				assertEquals(key, key != null && isConfigKey(key) && defaultOf(key), ((JCheckBox) child).isSelected());
			}
			else if (child instanceof Container) assertAllAtDefaults((Container) child);
		}
	}

	private static boolean isConfigKey(String key)
	{
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && item.keyName().equals(key)) return true;
		}
		return false;
	}

	/** What an untouched install reads for a boolean choice, or for a notification's enabled flag. */
	private static boolean defaultOf(String key)
	{
		HarnessConfig fresh = new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL);
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || !item.keyName().equals(key)) continue;
			try
			{
				Object value = method.invoke(fresh);
				return value instanceof Notification ? ((Notification) value).isEnabled() : (Boolean) value;
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		}
		throw new AssertionError("no setting " + key);
	}

	private static JCheckBox find(Container parent, String key)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JCheckBox && key.equals(child.getName())) return (JCheckBox) child;
			if (child instanceof Container)
			{
				JCheckBox found = find((Container) child, key);
				if (found != null) return found;
			}
		}
		return null;
	}
}
