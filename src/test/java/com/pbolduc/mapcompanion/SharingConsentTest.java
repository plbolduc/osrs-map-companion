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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Joining and enabling sharing are different choices, at both places a player can make them.
 *
 * <p>The snapshot checks use the real builder against a logged-in, paired harness client.
 * The warning RuneLite requires is the install's, from the Hub manifest; no setting repeats it.
 * Sidebar choices are exercised in SharingControlsTest, including cancellation with sharing already
 * selected.
 */
public class SharingConsentTest
{
	@Test
	public void aPairedFreshInstallSendsOnlyItsMembershipEnvelope()
	{
		Fixture fixture = new Fixture();
		JsonObject sent = fixture.sent();
		assertEquals(new HashSet<>(Arrays.asList("v", "member", "sentAtMs")), sent.keySet());
		assertEquals(new HashSet<>(Arrays.asList("id", "nick")), sent.getAsJsonObject("member").keySet());
		assertEquals("Me", sent.getAsJsonObject("member").get("nick").getAsString());
		assertFalse(fixture.config.showDestination());
		assertFalse(fixture.config.notifyMeetups());
		assertFalse(fixture.config.showGroupOnWorldMap());
	}

	@Test
	public void explicitSharingChoicesSendOnlyTheChosenCharacterFields()
	{
		Fixture fixture = new Fixture();
		fixture.config.set("sharePosition", true);
		fixture.config.set(MapCompanionConfig.KEY_SHARE_NAME, true);
		JsonObject sent = fixture.sent();
		assertEquals(new HashSet<>(Arrays.asList("v", "member", "sentAtMs", "world", "worldFlags", "instanced", "pos")),
			sent.keySet());
		assertEquals("Example", sent.getAsJsonObject("member").get("characterName").getAsString());
		assertEquals(444, sent.get("world").getAsInt());
		assertEquals(3222, sent.getAsJsonObject("pos").get("x").getAsInt());
		assertFalse(sent.has("skills"));
		assertFalse(sent.has("accountType"));
		assertFalse(sent.has("idleMs"));
		assertFalse(sent.has("milestones"));

		fixture.config.set(MapCompanionConfig.KEY_SHARE_NAME, false);
		assertFalse(fixture.sent().getAsJsonObject("member").has("characterName"));
	}

	/**
	 * One choice decides what the group sees. The map lets any nickname but "Me" win over the
	 * character name, so a ticked name sends "Me" whatever the box holds, and an empty box sends
	 * "Me" rather than a placeholder that would read as somebody's chosen name.
	 */
	@Test
	public void theGroupSeesTheCharacterNameOrTheAliasNeverAnAliasOverTheName()
	{
		Fixture fixture = new Fixture();
		fixture.config.nickname("Scout");
		fixture.config.set(MapCompanionConfig.KEY_SHARE_NAME, true);
		JsonObject member = fixture.sent().getAsJsonObject("member");
		assertEquals("Me", member.get("nick").getAsString());
		assertEquals("Example", member.get("characterName").getAsString());

		fixture.config.set(MapCompanionConfig.KEY_SHARE_NAME, false);
		assertEquals("Scout", fixture.sent().getAsJsonObject("member").get("nick").getAsString());

		for (String none : new String[]{"", "   ", " Me "})
		{
			fixture.config.nickname(none);
			assertEquals("'" + none + "'", "Me", fixture.sent().getAsJsonObject("member").get("nick").getAsString());
		}
	}

	/**
	 * Every remote feature starts off, and none asks "Are you sure?" with RuneLite's warning.
	 *
	 * <p>RuneLite asks for its warning "either on the plugin, or on the configuration option", and it
	 * shows the Hub manifest's on Install, so a setting repeating it would ask again about a decision
	 * already made. The warning that is kept is pinned here instead, where its removal from the
	 * submission guide fails a build.
	 */
	@Test
	public void everyRemoteFeatureStartsOffAndTheWarningIsTheInstallsAlone() throws Exception
	{
		MapCompanionConfig config = new MapCompanionConfig() {};
		int remoteFeatures = 0;
		for (Method method : MapCompanionConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && method.getReturnType() == boolean.class
				&& ((item.section().equals(MapCompanionConfig.presenceSection)
				|| item.section().equals(MapCompanionConfig.progressSection)
				|| item.section().equals(MapCompanionConfig.tasksSection)
				|| item.section().equals(MapCompanionConfig.lootSection))
				|| method.getName().equals("showDestination") || method.getName().equals("notifyMeetups")
				|| method.getName().equals("showGroupOnWorldMap")))
			{
				assertEquals(method.getName(), false, method.invoke(config));
				assertEquals(method.getName(), "", item.warning());
				remoteFeatures++;
			}
		}
		assertEquals(28, remoteFeatures);

		String guide = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("docs", "plugin-hub-submission.md")),
			java.nio.charset.StandardCharsets.UTF_8);
		java.util.regex.Matcher manifest = java.util.regex.Pattern.compile("(?m)^[ \t]*warning=(.+)$").matcher(guide);
		assertTrue("the Hub manifest in the submission guide carries the install warning", manifest.find());
		assertTrue(manifest.group(1), manifest.group(1).contains("IP address") && manifest.group(1).contains("third-party"));
	}

	private static final class Fixture
	{
		private final HarnessGame game = new HarnessGame();
		private final HarnessStore store = new HarnessStore();
		private final HarnessConfig config = new HarnessConfig(store, "http://localhost");
		private final Gson gson = new Gson();
		private final SnapshotBuilder builder;

		private Fixture()
		{
			game.login("Example", 444, 3222, 3218, 0);
			store.set(MapCompanionConfig.KEY_PAIRING_CODE, "ABC234");
			builder = Guice.createInjector(new AbstractModule()
			{
				@Override
				protected void configure()
				{
					bind(Client.class).toInstance(game.client());
					bind(Gson.class).toInstance(gson);
					bind(MapCompanionConfig.class).toInstance(config);
					bind(MemberIdentity.class).toInstance(new MemberIdentity(store));
					bind(LootLog.class).toInstance(new LootLog(game.client(), store, gson));
					bind(FarmingObserver.class).toInstance(new FarmingObserver(game.client(), store, gson));
					bind(BirdhouseReader.class).toInstance(new BirdhouseReader(key -> null));
				}
			}).getInstance(SnapshotBuilder.class);
		}

		private JsonObject sent()
		{
			return gson.toJsonTree(builder.build(1000L)).getAsJsonObject();
		}
	}
}
