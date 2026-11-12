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
				/* Every box RuneLite's list shows, the meetup reminder's notification setting among them. */
				if (item != null && !item.hidden() && (method.getReturnType() == boolean.class || method.getReturnType() == Notification.class))
				{
					expected.add(item.keyName());
				}
			}
			/*
			 * Every setting RuneLite's list shows is under a box here: its row's, or the switch its
			 * group is under. This asserted a box for each of this client's eleven settings, then seven
			 * rows and six sharing switches. The character name is the one with no box: it is a line and
			 * a link under the switches, since the step after Connect shares it with whatever is ticked.
			 */
			Set<String> under = new HashSet<>();
			for (String key : expected)
			{
				JCheckBox box = covering(controls, key);
				if (MapCompanionConfig.KEY_SHARE_NAME.equals(key))
				{
					assertNull(box);
					assertNotNull(named(controls, "sharing.nameLink"));
					continue;
				}
				assertNotNull("under a box: " + key, box);
				under.add(box.getName());
			}
			assertEquals("and every box is over a setting RuneLite's list shows", actual, under);
			/*
			 * Sharing's three switches — where you are, your account, loot and milestones — and this
			 * client's seven rows: the group in RuneLite, which is the two settings that receive from the
			 * map; the meetup reminder; the followed step's box over the game, with its teleport outlined
			 * where it is clicked and a farm run's in the bank; the destination's hint arrow; a followed
			 * route's walks handed to Shortest Path, which is a plugin message inside the client rather
			 * than anything sent; your boats and Route here on the world map, the second asking the map
			 * only on a press of its own, as every Route does; and Drop luck's chat line. It was 34 while
			 * each field had a box, 17 while each of this client's settings had one, and 13 while the
			 * name, each of RuneLite's four groups and where you died had a switch each.
			 */
			assertEquals(10, actual.size());
			for (SharingFields.Section section : SharingFields.GROUPS)
			{
				assertNull("no box of its own, only its switch's: " + section.key, find(controls, section.key));
				for (SharingFields.Field field : section.fields)
				{
					assertNull("no box of its own, only its switch's: " + field.key, find(controls, field.key));
				}
			}
			Set<String> fields = new HashSet<>();
			for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
			{
				for (SharingFields.Field field : toggle.fields)
				{
					fields.add(field.key);
					assertFalse("every data choice starts off: " + field.key, field.on(config, store));
				}
			}
			assertEquals("the four groups' fields and where you died, each under one switch", 26, fields.size());
			for (String title : new String[]{"What each one sends", SharingControls.CLIENT_TITLE})
			{
				JButton header = buttonWithText(controls, "▸ " + title);
				assertNotNull(header);
				header.doClick();
				assertAllAtDefaults(controls);
			}
			fields.addAll(expected);
			for (String key : fields)
			{
				assertNull("opening settings cannot write " + key, store.get(key));
			}
		});
	}

	/**
	 * One name for each of this client's settings: RuneLite's list calls each what the sidebar does,
	 * under the section the sidebar's fold is named after, and the fold says where the settings with
	 * no box here are, since the README sends players to them.
	 */
	@Test
	public void eachClientSettingHasOneNameAndTheFoldSaysWhereTheKeysAre() throws Exception
	{
		for (SharingFields.Field field : SharingFields.CLIENT)
		{
			ConfigItem item = null;
			for (Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				ConfigItem candidate = method.getAnnotation(ConfigItem.class);
				if (candidate != null && candidate.keyName().equals(field.key)) item = candidate;
			}
			assertNotNull(field.key, item);
			assertEquals(field.key, field.label, item.name());
			assertEquals(field.key, MapCompanionConfig.clientSection, item.section());
		}
		assertEquals(SharingControls.CLIENT_TITLE, MapCompanionConfig.class.getField("clientSection")
			.getAnnotation(net.runelite.client.config.ConfigSection.class).name());
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			SharingControls controls = new SharingControls(new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL), store);
			assertFalse("folded with the settings it is about", named(controls, "sharing.keysElsewhere").getParent().isVisible());
			buttonWithText(controls, "▸ " + SharingControls.CLIENT_TITLE).doClick();
			assertTrue(named(controls, "sharing.keysElsewhere").getParent().isVisible());
			assertTrue(SidebarWords.words(controls).contains(SharingControls.KEYS_ELSEWHERE));
		});
	}

	/**
	 * A switch is all of its fields or none. One a player chose part of shows the dash and says it in
	 * the fewer words, what is off or what is on; a press then turns all of it on, as a press of any
	 * switch that is off does, and a second press all of it off. Rewritten when each field lost its own
	 * box, and again when six switches became three: Where you are is the map presence and where you
	 * died, so a player who shared all the step used to tick reads "Not shared: Where you died" under
	 * it, and nothing of theirs changes until they press. A switch never turns the name on.
	 */
	@Test
	public void aSwitchWritesAllOfItsFieldsAndNamesAPartlySharedOne() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			Pairing pairing = new Pairing(store, new Gson());
			SharingControls controls = new SharingControls(config, store);
			JCheckBox master = find(controls, "share.where");
			javax.swing.JLabel under = (javax.swing.JLabel) named(controls, "sharingPartly.where");
			String covers = SharingFields.TOGGLES.get(0).note;
			assertEquals("what it covers, while there is nothing else to say", covers, plain(under));
			master.doClick();
			assertTrue(config.sharePosition());
			assertTrue(store.on("shareActivity"));
			assertTrue(store.on("shareAccountType"));
			assertTrue("where you died is a tile of the same thing", config.shareDeath());
			assertFalse(config.shareName());
			assertFalse(store.on("shareSkills"));
			assertFalse(config.showDestination());
			assertFalse(config.notifyMeetups().isEnabled());
			assertFalse(pairing.hasServerConsent());
			assertEquals(covers, plain(under));

			store.set("shareDeath", "false");
			controls.refresh();
			assertFalse("on only while every field is", master.isSelected());
			assertEquals("indeterminate", master.getClientProperty("JButton.selectedState"));
			assertEquals("Not shared: Where you died", plain(under));
			store.set("sharePosition", "false");
			controls.refresh();
			assertEquals("Partly shared: Training and idle time, Account type", plain(under));
			master.doClick();
			assertTrue("a press turns all of it on",
				config.sharePosition() && store.on("shareActivity") && store.on("shareAccountType") && config.shareDeath());
			assertNull(master.getClientProperty("JButton.selectedState"));
			assertEquals(covers, plain(under));
			master.doClick();
			assertFalse(config.sharePosition());
			assertFalse(store.on("shareActivity"));
			assertFalse(store.on("shareAccountType"));
			assertFalse(config.shareDeath());
			assertNull("the box writes fields; the switches RuneLite's list shows follow them in the plugin",
				store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
			assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		});
	}

	private static String plain(javax.swing.JLabel label)
	{
		return label.isVisible() ? label.getText().replaceAll("<[^>]+>", "").trim() : "";
	}

	/**
	 * Each switch's box writes exactly its own fields. Every setting is checked, the fields RuneLite
	 * does not declare included, since the fields are what is sent; RuneLite's own switches are the
	 * plugin's to write from those fields, never the box's. Your account is the two groups that were
	 * Progress and travel and Tasks and timers.
	 */
	@Test
	public void everyOtherSwitchWritesOnlyItsKnownIndividualOptIns() throws Exception
	{
		String[][] groupKeys = {
			{"share.account", "shareSkills", "shareQuests", "shareQuestLog", "shareDiaries", "sharePermanentTravel", "shareCapabilities", "shareStash", "shareBoats", "shareSeaCharts",
				"shareSlayer", "shareClue", "shareFarming", "shareBirdhouses", "shareHunterRumours", "shareMahoganyHomes", "shareFarmingContracts", "sharePortTasks", "shareDailies"},
			{"share.loot", "shareLoot", "shareMilestones", "sharePets", "shareCollectionLog"}
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
				assertTrue(find(controls, group[0]).isSelected());
				Set<String> selected = new HashSet<>(java.util.Arrays.asList(group).subList(1, group.length));
				for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
				{
					for (SharingFields.Field field : toggle.fields)
					{
						assertEquals(field.key, selected.contains(field.key) ? "true" : null, store.get(field.key));
					}
				}
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
						assertEquals(item.keyName(), defaultOf(item.keyName()), shown.isSelected());
					}
				}
				assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
			});
		}
	}

	/**
	 * Without a box per field the page still says all of what each switch shares: What each one sends
	 * names every field in the sidebar's words, under its switch, and each switch says in its
	 * tooltip what every field under it sends. Opening the fold writes nothing.
	 */
	@Test
	public void whatEachOneSendsNamesEveryFieldUnderItsSwitch() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			SharingControls controls = new SharingControls(config, store);
			JPanel details = (JPanel) named(controls, "sharing.details");
			assertFalse("folded until asked for", details.isVisible());
			buttonWithText(controls, "▸ What each one sends").doClick();
			assertTrue(details.isVisible());
			String text = String.join(" ", SidebarWords.words(details)).replaceAll("\\s+", " ");
			int from = text.indexOf(SharingFields.NAME.label);
			assertTrue(from >= 0 && text.contains(SharingFields.NAME.detail));
			for (SharingFields.Toggle section : SharingFields.TOGGLES)
			{
				from = text.indexOf(section.title, from);
				assertTrue(section.title + " in order", from >= 0);
				String tip = find(controls, "share." + section.id).getToolTipText();
				for (SharingFields.Field field : section.fields)
				{
					int at = text.indexOf(field.label, from);
					assertTrue(field.label + " under " + section.title, at >= 0);
					from = at;
					assertTrue(field.key + " in the switch's tooltip", tip.contains(field.label) && tip.contains(field.detail));
				}
			}
			assertTrue(text.indexOf(MapCompanionConfig.FEED_KEPT, from) > 0);
			/* Where you died had a heading of its own after the groups; it is a field under Where you are. */
			assertTrue(SharingFields.TOGGLES.get(0).fields.contains(SharingFields.DEATH));
			assertAllAtDefaults(controls);
			for (Method method : MapCompanionConfig.class.getDeclaredMethods())
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				if (item != null)
				{
					assertNull("opening the fold writes nothing: " + item.keyName(), store.get(item.keyName()));
				}
			}
		});
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
			assertFalse(find(controls, "share.where").isSelected());
			assertEquals("indeterminate", find(controls, "share.where").getClientProperty("JButton.selectedState"));
			/* The line under a switch is always there now: what the switch covers, until part of it is on. */
			javax.swing.JLabel under = (javax.swing.JLabel) named(controls, "sharingPartly.where");
			assertEquals("Partly shared: Position and world", plain(under));
			assertEquals("the name is not a switch's to turn on", "Use my character name", ((JButton) named(controls, "sharing.nameLink")).getText());
			config.set("sharePosition", false);
			controls.refresh();
			assertNull(find(controls, "share.where").getClientProperty("JButton.selectedState"));
			assertEquals(SharingFields.TOGGLES.get(0).note, plain(under));
			assertNull(store.get("sharePosition"));
			assertNull(store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
		});
	}

	/**
	 * How the group sees the player starts under the switches' words, where the line under each
	 * switch starts, rather than at the page's edge, left of the boxes it follows. It was the box
	 * under the Character name switch; the name has no switch now, so it is the label, the field,
	 * the hint and the link that turns the character name on or off.
	 */
	@Test
	public void theNameBoxStartsUnderTheSwitchesWords() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			config.set("sharePosition", true);
			SharingControls controls = new SharingControls(config, store);
			controls.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH - 20, controls.getPreferredSize().height);
			layout(controls);
			int words = left(named(controls, "sharingPartly.where"), controls);
			assertEquals(SharingControls.TEXT_INSET, words);
			Container box = (Container) named(controls, "sharing.nameBox");
			assertEquals("the label, the field, the hint and the link", 4, box.getComponentCount());
			for (Component line : box.getComponents())
			{
				assertEquals(line.getClass().getSimpleName(), words, left(line, controls));
			}
		});
	}

	/** Where a component's own drawing starts across the page: its left edge, and its border's left inset. */
	private static int left(Component component, Container page)
	{
		int inset = component instanceof javax.swing.JLabel ? ((javax.swing.JLabel) component).getInsets().left : 0;
		return SwingUtilities.convertPoint(component.getParent(), component.getX(), 0, page).x + inset;
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

	/**
	 * The character name was the first of six switches. It has none now: the step after Connect
	 * shares it with whatever is ticked, so the page says how the group sees the player and offers
	 * the other way in a link. One who hid their name before keeps it hidden, with the box for
	 * another name open, and no switch turns it on for them.
	 */
	@Test
	public void theNameIsALineAndALinkRatherThanASwitch() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			SharingControls controls = new SharingControls(config, store);
			assertNull(find(controls, MapCompanionConfig.KEY_SHARE_NAME));
			JButton link = (JButton) named(controls, "sharing.nameLink");
			assertEquals("Use my character name", link.getText());
			assertTrue(SidebarWords.words(controls).contains("Name to show instead"));
			assertTrue(named(controls, "nickname").isVisible());

			link.doClick();
			assertEquals("true", store.get(MapCompanionConfig.KEY_SHARE_NAME));
			assertEquals("Use another name", link.getText());
			assertTrue(SidebarWords.words(controls).contains("Shown as your character name"));
			assertFalse(named(controls, "nickname").isVisible());

			link.doClick();
			assertEquals("false", store.get(MapCompanionConfig.KEY_SHARE_NAME));
			assertTrue(named(controls, "nickname").isVisible());
			for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
			{
				find(controls, "share." + toggle.id).doClick();
			}
			assertFalse("sharing everything from Settings still leaves a hidden name hidden", config.shareName());
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
	 * This client's settings were eleven boxes in a row, in no order, under a fold named "In RuneLite"
	 * that did not say how it differed from the sharing above it. They are seven rows in three groups
	 * under "In your game", with a line that says nothing there shares anything. A row over several
	 * settings is on while all of them are and writes them all on a press; partly on, it draws the dash
	 * a partly shared group does, so nothing changes for anyone until they press. Shortest Path's row
	 * is drawn only with that plugin installed.
	 */
	@Test
	public void aRowOfThisClientsSettingsWritesAllOfThemAndSaysWhenOnlyPartIsOn() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			HarnessStore store = new HarnessStore();
			HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
			followStoredSettings(store, config);
			boolean[] installed = {false};
			SharingControls controls = new SharingControls(config, store, () -> installed[0]);
			JCheckBox group = find(controls, "showDestination");
			assertNull("no box of its own for a setting under a row", find(controls, "showGroupOnWorldMap"));
			assertFalse(group.isSelected());

			store.set("showDestination", "true");
			controls.refresh();
			assertFalse("partly on is not on", group.isSelected());
			assertEquals("and says so with the dash", "indeterminate", group.getClientProperty("JButton.selectedState"));

			group.doClick();
			assertTrue(config.showDestination());
			assertTrue("a press turns the whole row on", config.showGroupOnWorldMap());
			assertTrue(group.isSelected());
			assertNull(group.getClientProperty("JButton.selectedState"));

			JCheckBox step = find(controls, "showStepOnScreen");
			assertTrue(step.isSelected());
			step.doClick();
			assertFalse(config.showStepOnScreen());
			assertFalse("the step's teleport outlined goes with its box", config.outlineStepTeleport());
			assertFalse("and a run's in the bank", config.markFarmRunInBank());

			JCheckBox paths = find(controls, MapCompanionConfig.KEY_SHORTEST_PATH);
			assertFalse("no row for a plugin that is not installed", paths.isVisible());
			installed[0] = true;
			controls.refresh();
			assertTrue(paths.isVisible());

			java.util.List<String> words = SidebarWords.words(controls);
			assertTrue(words.toString(), words.contains("With your group") && words.contains("Following a route") && words.contains("Extras"));
			assertTrue(words.contains("Only what you see on your own screen. Nothing here shares anything about you."));
		});
	}

	/**
	 * Replaces per-checkbox modal assertions: a choice must never bypass the Connect disclosure.
	 *
	 * <p>Every box the sidebar draws, by the settings it is over: a switch's box writes its fields,
	 * which are what it stands for, and a row's box every setting in its row. Every box RuneLite's
	 * list shows is under one but the character name, which is the link under the switches, and a
	 * field under a switch has none of its own.
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
				JCheckBox box = covering(controls, item.keyName());
				if (MapCompanionConfig.KEY_SHARE_NAME.equals(item.keyName()))
				{
					/* The one setting RuneLite's list shows that is a link here rather than a box. */
					assertNull(box);
					((JButton) named(controls, "sharing.nameLink")).doClick();
					pressed++;
					assertEquals("true", store.get(item.keyName()));
					assertFalse("choosing data is not permission to connect", pairing.hasServerConsent());
					continue;
				}
				/* Hidden Slayer list preferences are owned by the task card, not sharing controls. */
				assertEquals(item.keyName(), !item.hidden(), box != null);
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
						assertEquals(field.key, flipped, field.on(config, store));
					}
				}
				assertFalse("choosing data is not permission to connect", pairing.hasServerConsent());
			}
			/* Every setting above, each by what it is under: a switch's or a row's box is pressed once for each of its settings. */
			assertEquals(17, pressed);
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
			JCheckBox position = find(controls, "share.where");
			position.doClick();
			assertTrue(config.sharePosition());
			assertFalse(config.shareName());
			assertFalse(store.on("shareSkills"));
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
			find(controls, "share.where").doClick();
			assertFalse(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> false));
			controls.refresh();
			assertTrue(config.sharePosition());
			assertTrue(find(controls, "share.where").isSelected());
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

	/**
	 * The box a setting is under: its row's, which writes it with the settings it answers with, the
	 * switch over its group's fields or over where you died, or its own.
	 */
	private static JCheckBox covering(Container parent, String key)
	{
		for (SharingFields.Toggle toggle : SharingFields.TOGGLES)
		{
			for (SharingFields.Section section : SharingFields.GROUPS)
			{
				if (section.key.equals(key) && toggle.fields.containsAll(section.fields)) return find(parent, "share." + toggle.id);
			}
			if (SharingFields.DEATH.key.equals(key) && toggle.fields.contains(SharingFields.DEATH)) return find(parent, "share." + toggle.id);
		}
		for (java.util.List<SharingFields.Field> row : SharingFields.CLIENT_ROWS)
		{
			for (SharingFields.Field field : row)
			{
				if (field.key.equals(key)) return find(parent, row.get(0).key);
			}
		}
		return find(parent, key);
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
