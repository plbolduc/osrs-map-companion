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
import com.google.gson.JsonObject;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The switch RuneLite's list shows for each sharing group, and the fields it stands for.
 *
 * <p>The fields are what is sent, so every test here checks them, not only the switch: an update
 * keeps exactly what a player ticked, a press anywhere writes the fields it names and nothing more,
 * and a disagreement is settled the way {@link SharingGroups} says, by the newer press.
 */
public class SharingGroupsTest
{
	/**
	 * A player on 1.3.0, which drew a box per field, ticked all of Map presence, two of Progress and
	 * travel and two of Tasks and timers, none of Loot and milestones, and the name. RuneLite wrote
	 * every default when the update loaded, the four new switches' "false" among them, before the
	 * plugin started.
	 *
	 * <p>The start keeps every one of those choices — the snapshot is the same JSON, field for field —
	 * and writes only the switches: on for the whole group, off for the two partly shared and the one
	 * not shared at all. A second start changes nothing.
	 */
	@Test
	public void anUpdateKeepsEveryChoiceAMixedGroupIncluded()
	{
		Fixture fixture = new Fixture();
		List<String> ticked = Arrays.asList("sharePosition", "shareActivity", "shareAccountType", "shareSkills", "shareBoats",
			"shareSlayer", "shareClue", MapCompanionConfig.KEY_SHARE_NAME);
		for (String key : fieldKeys())
		{
			fixture.store.set(key, Boolean.toString(ticked.contains(key)));
		}
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			fixture.store.set(section.key, "false");
		}
		Map<String, String> fields = fixture.fields();
		JsonObject sent = fixture.sent();
		assertTrue("the fixture shares what it ticked", sent.has("pos") && sent.has("skills") && !sent.has("loot"));
		fixture.written.clear();

		SharingGroups.settle(fixture.config, fixture.store);

		assertEquals("every field exactly as it was", fields, fixture.fields());
		assertEquals("so the snapshot carries exactly what it did", sent, fixture.sent());
		assertEquals("true", fixture.store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
		assertEquals("false", fixture.store.get(MapCompanionConfig.KEY_SHARE_PROGRESS));
		assertEquals("false", fixture.store.get(MapCompanionConfig.KEY_SHARE_TASKS));
		assertEquals("false", fixture.store.get(MapCompanionConfig.KEY_SHARE_LOOT_GROUP));
		assertEquals("only the switch that disagreed, and the marker",
			Arrays.asList(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE + "=true", SharingGroups.KEY_SETTLED + "=" + SharingGroups.GENERATION), fixture.written);
		assertEquals(Arrays.asList(SharingFields.PROGRESS.fields.get(0), SharingFields.PROGRESS.fields.get(7)),
			SharingGroups.shared(SharingFields.PROGRESS, fixture.config, fixture.store));
		assertEquals("Skill levels", SharingFields.PROGRESS.fields.get(0).label);
		assertEquals("Boats and moorings", SharingFields.PROGRESS.fields.get(7).label);

		fixture.written.clear();
		SharingGroups.settle(fixture.config, fixture.store);
		assertTrue("a second start agrees with everything and writes nothing", fixture.written.isEmpty());
		assertEquals(sent, fixture.sent());
	}

	/** RuneLite's settings have never been written for a profile that has no switches yet either. */
	/**
	 * A field an update adds to a group the player had switched on starts off all the same: the press
	 * that turned the group on never named it. Port tasks stands in for the new field, as if a release
	 * after the first generation had added it.
	 */
	@Test
	public void aFieldAddedByAnUpdateStaysOffInAGroupThatWasOn()
	{
		Fixture fixture = new Fixture();
		for (SharingFields.Field field : SharingFields.TASKS.fields)
		{
			fixture.store.set(field.key, Boolean.toString(!field.key.equals("sharePortTasks")));
		}
		fixture.store.set(MapCompanionConfig.KEY_SHARE_TASKS, "true");
		fixture.store.set(SharingGroups.KEY_SETTLED, "1");
		Map<String, Integer> added = new LinkedHashMap<>();
		added.put("sharePortTasks", 2);
		fixture.written.clear();

		SharingGroups.settle(fixture.config, fixture.store, added, 2);

		assertFalse("the new field is not shared until chosen", fixture.store.on("sharePortTasks"));
		assertTrue("the fields the press named stay shared", fixture.store.on("shareSlayer") && fixture.store.on("shareClue"));
		assertEquals("the switch says what is shared now, and the profile is read with the new list",
			Arrays.asList(MapCompanionConfig.KEY_SHARE_TASKS + "=false", SharingGroups.KEY_SETTLED + "=2"), fixture.written);

		/* Once read with the new list, a switch pressed while the plugin was not listening wins again, the new field too. */
		fixture.store.set(MapCompanionConfig.KEY_SHARE_TASKS, "true");
		SharingGroups.settle(fixture.config, fixture.store, added, 2);
		assertTrue(fixture.store.on("sharePortTasks"));
	}

	@Test
	public void anUpdateWithNoSwitchesStoredReadsThemAsOff()
	{
		Fixture fixture = new Fixture();
		for (SharingFields.Field field : SharingFields.LOOT.fields)
		{
			fixture.store.set(field.key, "true");
		}
		fixture.written.clear();
		SharingGroups.settle(fixture.config, fixture.store);
		assertEquals(Arrays.asList(MapCompanionConfig.KEY_SHARE_LOOT_GROUP + "=true", SharingGroups.KEY_SETTLED + "=" + SharingGroups.GENERATION), fixture.written);
	}

	/**
	 * A switch pressed in RuneLite's list writes each of its fields once and nothing else: every field's
	 * own event finds the switch already agreeing, so nothing is written back and nothing loops.
	 */
	@Test
	public void aSwitchPressedInRuneLitesListWritesItsFieldsOnceAndNothingLoops()
	{
		Fixture fixture = new Fixture().listening();
		SharingGroups.settle(fixture.config, fixture.store);
		fixture.store.set(SharingFields.TASKS.fields.get(0).key, "true");
		fixture.written.clear();

		fixture.store.set(MapCompanionConfig.KEY_SHARE_TASKS, "true");
		List<String> on = new ArrayList<>();
		on.add(MapCompanionConfig.KEY_SHARE_TASKS + "=true");
		for (SharingFields.Field field : SharingFields.TASKS.fields.subList(1, SharingFields.TASKS.fields.size()))
		{
			on.add(field.key + "=true");
		}
		assertEquals("the press, then each field that was off", on, fixture.written);
		assertTrue(SharingGroups.on(SharingFields.TASKS, fixture.config, fixture.store));
		assertFalse("no other group moves", fixture.store.on("shareSkills") || fixture.config.sharePosition() || fixture.store.on("shareLoot"));

		fixture.written.clear();
		fixture.store.set(MapCompanionConfig.KEY_SHARE_TASKS, "false");
		assertEquals(1 + SharingFields.TASKS.fields.size(), fixture.written.size());
		assertTrue(SharingGroups.shared(SharingFields.TASKS, fixture.config, fixture.store).isEmpty());
		assertEquals("false", fixture.store.get(MapCompanionConfig.KEY_SHARE_TASKS));
	}

	/**
	 * Fields written by Settings or the step after Connect move the switch: on with the last of its
	 * group, off with the first to go. A field going on never turns it off, which is what lets the
	 * fan-out above finish.
	 */
	@Test
	public void fieldsWrittenElsewhereMoveTheSwitchOnlyWhenTheGroupIsWholeOrBroken()
	{
		Fixture fixture = new Fixture().listening();
		SharingGroups.settle(fixture.config, fixture.store);
		List<SharingFields.Field> presence = SharingFields.PRESENCE.fields;
		fixture.store.set(presence.get(0).key, "true");
		fixture.store.set(presence.get(1).key, "true");
		assertNull("a partly shared group shows off, and nothing is written for it", fixture.store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
		fixture.store.set(presence.get(2).key, "true");
		assertEquals("true", fixture.store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
		assertTrue("every field kept", SharingGroups.on(SharingFields.PRESENCE, fixture.config, fixture.store));

		fixture.written.clear();
		fixture.store.set(presence.get(1).key, "false");
		assertEquals(Arrays.asList(presence.get(1).key + "=false", MapCompanionConfig.KEY_SHARE_MAP_PRESENCE + "=false"), fixture.written);
		assertTrue("the switch going off took no other field with it",
			fixture.config.sharePosition() && fixture.store.on("shareAccountType"));
	}

	/**
	 * RuneLite's list shows a disabled plugin's settings, so a switch can be pressed while nothing keeps
	 * its fields in step. At the next start, on a profile already read, the switch is the newer press.
	 */
	@Test
	public void aSwitchPressedWhileThePluginWasOffWinsAtTheNextStart()
	{
		Fixture fixture = new Fixture();
		SharingGroups.settle(fixture.config, fixture.store);
		for (SharingFields.Field field : SharingFields.LOOT.fields)
		{
			fixture.store.set(field.key, "true");
		}
		fixture.store.set(MapCompanionConfig.KEY_SHARE_LOOT_GROUP, "false");
		fixture.store.set(SharingFields.PROGRESS.fields.get(2).key, "true");
		fixture.store.set(MapCompanionConfig.KEY_SHARE_PROGRESS, "true");
		fixture.store.set(SharingFields.TASKS.fields.get(3).key, "true");

		SharingGroups.settle(fixture.config, fixture.store);

		assertTrue("switched off there, so no longer shared", SharingGroups.shared(SharingFields.LOOT, fixture.config, fixture.store).isEmpty());
		assertTrue("switched on there, so all of it", SharingGroups.on(SharingFields.PROGRESS, fixture.config, fixture.store));
		assertEquals("off over a partly shared group agrees with it, and keeps it",
			Arrays.asList(SharingFields.TASKS.fields.get(3)), SharingGroups.shared(SharingFields.TASKS, fixture.config, fixture.store));
	}

	/**
	 * RuneLite writes every setting's default into a profile it loads, and the plugin may be listening
	 * when it does. On a profile not yet read, or for a switch being unset, nothing is written: the
	 * start that reads the profile decides, and there the fields win.
	 */
	@Test
	public void aSwitchsEventChangesNothingOnAProfileNotYetReadOrWhenItIsUnset()
	{
		Fixture fixture = new Fixture();
		for (SharingFields.Field field : SharingFields.PRESENCE.fields)
		{
			fixture.store.set(field.key, "true");
		}
		fixture.store.set(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE, "false");
		SharingGroups.changed(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE, "false", fixture.config, fixture.store);
		assertTrue("a default written before the profile was read is not a press", SharingGroups.on(SharingFields.PRESENCE, fixture.config, fixture.store));

		SharingGroups.settle(fixture.config, fixture.store);
		assertEquals("true", fixture.store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));

		fixture.store.unset(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE);
		fixture.config.set(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE, false);
		SharingGroups.changed(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE, null, fixture.config, fixture.store);
		assertTrue("unsetting is how Reset starts, not a press", SharingGroups.on(SharingFields.PRESENCE, fixture.config, fixture.store));
	}

	/**
	 * The plugin's own {@code ConfigChanged} is what keeps them in step in a client, and a change of
	 * RuneLite profile reads the new profile's switches as a start does.
	 */
	@Test
	public void thePluginKeepsTheSwitchesInStepFromItsOwnEvents() throws Exception
	{
		Fixture fixture = new Fixture();
		MapCompanionPlugin plugin = new MapCompanionPlugin()
		{
			@Override
			ConfigStore settings()
			{
				return fixture.store;
			}
		};
		Field config = MapCompanionPlugin.class.getDeclaredField("config");
		config.setAccessible(true);
		config.set(plugin, fixture.config);
		fixture.store.onChanged(key ->
		{
			fixture.follow(key);
			ConfigChanged changed = new ConfigChanged();
			changed.setGroup(MapCompanionConfig.GROUP);
			changed.setKey(key);
			changed.setNewValue(fixture.store.get(key));
			plugin.onConfigChanged(changed);
		});
		fixture.store.set("sharePosition", "true");
		fixture.store.set("shareActivity", "true");
		fixture.store.set("shareAccountType", "true");

		plugin.onProfileChanged(new ProfileChanged());
		assertEquals("a profile read for the first time keeps its fields", "true", fixture.store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
		assertNotNull(fixture.store.get(SharingGroups.KEY_SETTLED));

		fixture.store.set(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE, "false");
		assertTrue("the press in RuneLite's list reached every field",
			SharingGroups.shared(SharingFields.PRESENCE, fixture.config, fixture.store).isEmpty());
		fixture.store.set(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE, "true");
		assertTrue(SharingGroups.on(SharingFields.PRESENCE, fixture.config, fixture.store));
		fixture.store.set("shareActivity", "false");
		assertEquals("and a field the sidebar writes moves the switch", "false", fixture.store.get(MapCompanionConfig.KEY_SHARE_MAP_PRESENCE));
	}

	/**
	 * One name for each setting: RuneLite's list names each switch as the sidebar does, and says every
	 * field it shares in the sidebar's words, in order; a field it hides is named as the sidebar names it.
	 * A field it does not declare at all is in no list of its own, and its one name is the sidebar's.
	 */
	@Test
	public void runeLitesListNamesEachSwitchAndItsFieldsInTheSidebarsWords() throws Exception
	{
		Map<String, ConfigItem> items = new LinkedHashMap<>();
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				items.put(item.keyName(), item);
			}
		}
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			ConfigItem item = items.get(section.key);
			assertNotNull(section.key, item);
			assertEquals(section.title, item.name());
			assertFalse(item.hidden());
			assertEquals(MapCompanionConfig.sharingSection, item.section());
			int from = 0;
			for (SharingFields.Field field : section.fields)
			{
				int at = item.description().indexOf(field.label, from);
				assertTrue(section.key + " names " + field.label + " after the field before it", at >= from);
				from = at + field.label.length();
				ConfigItem hidden = items.get(field.key);
				if (hidden != null)
				{
					assertTrue(field.key + " is drawn by its switch alone", hidden.hidden());
					assertEquals(field.key, field.label, hidden.name());
				}
			}
		}
		for (SharingFields.Field field : Arrays.asList(SharingFields.NAME, SharingFields.DEATH))
		{
			assertEquals(field.label, items.get(field.key).name());
			assertEquals(MapCompanionConfig.sharingSection, items.get(field.key).section());
		}
		assertEquals("Name to show instead", items.get("nickname").name());
		Set<String> declared = new LinkedHashSet<>(groupFieldKeys());
		declared.retainAll(items.keySet());
		assertEquals("the one field RuneLite declares, for the map's contract test", Arrays.asList("sharePosition"), new ArrayList<>(declared));
	}

	/**
	 * A field RuneLite does not declare is read by its key, which no compiler checks as it checked a
	 * getter: every key the plugin reads that way is a field of a group, so a misspelt key cannot read a
	 * setting nobody writes, and every such field is read somewhere, so none is offered and never sent.
	 */
	@Test
	public void everyKeyReadByKeyIsAFieldOfAGroupAndEveryFieldIsRead() throws Exception
	{
		Set<String> undeclared = new LinkedHashSet<>(groupFieldKeys());
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				undeclared.remove(item.keyName());
			}
		}
		Pattern literal = Pattern.compile("\\.on\\(\"(\\w+)\"\\)");
		Pattern constant = Pattern.compile("\\.on\\(MapCompanionConfig\\.(KEY_\\w+)\\)");
		Set<String> read = new HashSet<>();
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList()))
			{
				String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				for (Matcher found = literal.matcher(source); found.find();)
				{
					read.add(found.group(1));
				}
				for (Matcher found = constant.matcher(source); found.find();)
				{
					read.add((String) MapCompanionConfig.class.getField(found.group(1)).get(null));
				}
			}
		}
		Set<String> stray = new HashSet<>(read);
		stray.removeAll(undeclared);
		assertTrue("read by key, and no field of a group RuneLite leaves undeclared: " + stray, stray.isEmpty());
		assertEquals("every field RuneLite does not declare is read", undeclared, new LinkedHashSet<>(groupFieldKeys().stream()
			.filter(read::contains).collect(Collectors.toList())));
		assertEquals(23, undeclared.size());
	}

	/**
	 * Reset under RuneLite's list puts back only what the config declares, so the plugin turns the fields
	 * it reads by key off itself: a group partly on, whose switch Reset leaves as it was, is shared no more.
	 */
	@Test
	public void resetTurnsOffTheFieldsRuneLiteDoesNotDeclare()
	{
		Fixture fixture = new Fixture();
		for (String key : groupFieldKeys())
		{
			fixture.store.set(key, "true");
		}
		assertTrue("shared before the reset", fixture.sent().has("skills"));
		new MapCompanionPlugin()
		{
			@Override
			ConfigStore settings()
			{
				return fixture.store;
			}
		}.resetConfiguration();
		for (String key : groupFieldKeys())
		{
			assertFalse(key, fixture.store.on(key));
		}
		assertFalse("so a field read by key goes out no more", fixture.sent().has("skills"));
	}

	/** The fields under the four groups, in their order. */
	private static List<String> groupFieldKeys()
	{
		List<String> keys = new ArrayList<>();
		for (SharingFields.Section section : SharingFields.GROUPS)
		{
			for (SharingFields.Field field : section.fields)
			{
				keys.add(field.key);
			}
		}
		return keys;
	}

	private static List<String> fieldKeys()
	{
		List<String> keys = new ArrayList<>(groupFieldKeys());
		keys.add(SharingFields.NAME.key);
		keys.add(SharingFields.DEATH.key);
		return keys;
	}

	/**
	 * A logged-in, paired character's settings and the real snapshot builder over them, with every
	 * write recorded and the config following the store, as RuneLite's proxy reads its own store.
	 */
	private static final class Fixture
	{
		final HarnessGame game = new HarnessGame();
		final HarnessStore store = new HarnessStore();
		final HarnessConfig config = new HarnessConfig(store, "http://localhost");
		final List<String> written = new ArrayList<>();
		final Gson gson = new Gson();
		final SnapshotBuilder builder;

		Fixture()
		{
			game.login("Example", 444, 3222, 3218, 0);
			store.set(MapCompanionConfig.KEY_PAIRING_CODE, "ABC234");
			store.onChanged(this::follow);
			builder = Guice.createInjector(new AbstractModule()
			{
				@Override
				protected void configure()
				{
					bind(Client.class).toInstance(game.client());
					bind(Gson.class).toInstance(gson);
					bind(MapCompanionConfig.class).toInstance(config);
					bind(ConfigStore.class).toInstance(store);
					bind(MemberIdentity.class).toInstance(new MemberIdentity(store));
					bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
					bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
					bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
				}
			}).getInstance(SnapshotBuilder.class);
		}

		/** As the plugin's own event does: the switches and their fields kept in step from each write. */
		Fixture listening()
		{
			store.onChanged(key ->
			{
				follow(key);
				SharingGroups.changed(key, store.get(key), config, store);
			});
			return this;
		}

		void follow(String key)
		{
			String value = store.get(key);
			written.add(key + "=" + value);
			if ("true".equals(value) || "false".equals(value))
			{
				config.set(key, Boolean.parseBoolean(value));
			}
		}

		Map<String, String> fields()
		{
			Map<String, String> values = new LinkedHashMap<>();
			for (String key : fieldKeys())
			{
				values.put(key, store.get(key));
			}
			return values;
		}

		JsonObject sent()
		{
			return gson.toJsonTree(builder.build(1000L)).getAsJsonObject();
		}
	}
}
