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
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
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
				if (item != null && method.getReturnType() == boolean.class)
				{
					expected.add(item.keyName());
				}
			}
			assertEquals(expected, actual);
			/*
			 * 26 data and client choices, and the three that only draw on this client: Drop luck's chat
			 * line, the destination's hint arrow, and a followed route's walks handed to Shortest Path,
			 * which is a plugin message inside the client rather than anything sent.
			 */
			assertEquals(29, actual.size());
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
			JCheckBox master = find(controls, "shareGroup.presence");
			master.doClick();
			assertTrue(config.sharePosition());
			assertTrue(config.shareActivity());
			assertTrue(config.shareAccountType());
			assertFalse(config.shareName());
			assertFalse(config.shareDeath());
			assertFalse(config.shareSkills());
			assertFalse(config.showDestination());
			assertFalse(config.notifyMeetups());
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
			assertNull(store.get("shareGroup.presence"));
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	@Test
	public void everyOtherGroupWritesOnlyItsKnownIndividualOptIns() throws Exception
	{
		String[][] groupKeys = {
			{"progress", "shareSkills", "shareQuests", "shareQuestLog", "shareDiaries", "sharePermanentTravel", "shareCapabilities", "shareStash"},
			{"tasks", "shareSlayer", "shareClue", "shareFarming", "shareBirdhouses", "shareHunterRumours", "shareMahoganyHomes", "shareFarmingContracts"},
			{"loot", "shareLoot", "shareMilestones", "sharePets", "shareCollectionLog"}
		};
		for (String[] group : groupKeys)
		{
			SwingUtilities.invokeAndWait(() ->
			{
				HarnessStore store = new HarnessStore();
				HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
				followStoredSettings(store, config);
				SharingControls controls = new SharingControls(config, store);
				find(controls, "shareGroup." + group[0]).doClick();
				Set<String> selected = new HashSet<>(java.util.Arrays.asList(group));
				for (Method method : MapCompanionConfig.class.getDeclaredMethods())
				{
					ConfigItem item = method.getAnnotation(ConfigItem.class);
					if (item == null || method.getReturnType() != boolean.class) continue;
					assertEquals(item.keyName(), selected.contains(item.keyName()) || defaultOf(item.keyName()), find(controls, item.keyName()).isSelected());
					assertEquals(item.keyName(), selected.contains(item.keyName()) ? "true" : null, store.get(item.keyName()));
				}
				assertNull(store.get("shareGroup." + group[0]));
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
			assertEquals("indeterminate", find(controls, "shareGroup.presence").getClientProperty("JButton.selectedState"));
			assertFalse(find(controls, "shareName").isSelected());
			config.set("sharePosition", false);
			controls.refresh();
			assertFalse(find(controls, "sharePosition").isSelected());
			assertNull(find(controls, "shareGroup.presence").getClientProperty("JButton.selectedState"));
			assertNull(store.get("sharePosition"));
		});
	}

	/** Replaces per-checkbox modal assertions: a choice must never bypass the Connect disclosure. */
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
			for (Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				if (item == null || method.getReturnType() != boolean.class) continue;
				JCheckBox box = find(controls, item.keyName());
				assertNotNull(box);
				boolean flipped = !defaultOf(item.keyName());
				box.doClick();
				controls.refresh();
				assertEquals(item.keyName(), flipped, box.isSelected());
				assertEquals(Boolean.toString(flipped), store.get(item.keyName()));
				assertFalse("choosing data is not permission to connect", pairing.hasServerConsent());
			}
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
				if (!key.startsWith("shareGroup.")) assertTrue("one checkbox per setting", keys.add(key));
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

	/** What an untouched install reads for a boolean choice. */
	private static boolean defaultOf(String key)
	{
		HarnessConfig fresh = new HarnessConfig(new HarnessStore(), MapCompanionConfig.DEFAULT_MAP_URL);
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || !item.keyName().equals(key)) continue;
			try
			{
				return (Boolean) method.invoke(fresh);
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
