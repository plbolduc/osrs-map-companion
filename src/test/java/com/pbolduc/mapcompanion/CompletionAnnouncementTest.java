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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The plugin's own subscriptions deciding when a finished quest or diary tier is news.
 *
 * <p>The real readers, outbox and watcher, read through {@link HarnessGame}, with the game's
 * boundaries raised through the plugin's handlers. What only a client can say — that a quest's
 * state or a diary flag really moves when it is finished — is in {@code docs/live-checks.md}.
 */
public class CompletionAnnouncementTest
{
	private final HarnessGame game = new HarnessGame();
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, "http://localhost");
	private final MapCompanionPlugin plugin = new MapCompanionPlugin();
	private final QuestReader quests = new QuestReader(game.client());
	private final DiaryReader diaries;
	private long now = 1_000_000L;

	public CompletionAnnouncementTest() throws Exception
	{
		config.set(MapCompanionConfig.KEY_SHARE_MILESTONES, true);
		config.set("shareQuests", true);
		config.set("shareQuestLog", true);
		config.set("shareDiaries", true);
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "ABC234");
		game.login("Bob", 444, 3222, 3218, 0);
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.IN_PROGRESS);
		game.setQuest(Quest.COOKS_ASSISTANT, QuestState.FINISHED);
		/* Varrock easy done, medium plainly not. */
		game.setVarbit(VarbitIds.DIARY_VARROCK[0], 1);

		diaries = construct(DiaryReader.class, game.client());
		RewardTestClient definitions = new RewardTestClient();
		CollectionItemCatalog catalog = definitions.catalog();
		plugin.milestones = new MilestoneOutbox();
		plugin.pairing = new Pairing(store, new Gson());
		plugin.destinationState = new DestinationState(new Gson());
		plugin.destinationMarker = new TestDestinationMarker();
		plugin.groupMarkers = new GroupMarkers(null, null);
		plugin.rewardObserver = new RewardObserver(definitions.client, catalog, catalog::contains, () -> 1000L);
		TestFarmRuns.attach(plugin);
		set("client", game.client());
		set("config", config);
		set("questReader", quests);
		set("diaryReader", diaries);
		set("xpTracker", new XpTracker());
		set("clueReader", new ClueReader(game.client()));
		set("farmingObserver", new FarmingObserver(game.client(), store, new Gson()));
		set("travelUnlockReader", new TravelUnlockReader(game.client()));
		set("stashReader", new StashReader(game.client()));
		set("boatReader", new BoatReader(game.client()));
		set("birdhouseReader", new BirdhouseReader(key -> null));
		set("assignmentReader", new AssignmentReader(game.client(), config));
		set("deathWatcher", construct(DeathWatcher.class, game.client()));
		set("lootLog", new LootLog(game.client(), store, new Gson()));
		set("directiveStream", new DirectiveStream(new okhttp3.OkHttpClient(), new Gson(), (action, delay) -> null));
		set("groupState", new GroupState(plugin.destinationState));
	}

	@Test
	public void aQuestAndATierFinishedWhileWatchingAreAnnouncedAndDeliveredOnce()
	{
		settleThenWatch();
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		readAgain();

		List<CompanionSnapshot.Milestone> sent = plugin.milestones.take(now);
		assertEquals(2, sent.size());
		assertEquals("quest", sent.get(0).kind);
		assertEquals("Dragon Slayer I", sent.get(0).quest);
		assertEquals("diary", sent.get(1).kind);
		assertEquals("Varrock", sent.get(1).region);
		assertEquals("medium", sent.get(1).tier);

		readAgain();
		assertEquals("watching on does not announce them again", 2, plugin.milestones.pendingCount());
		plugin.milestones.acknowledge(Arrays.asList(sent.get(0).id, sent.get(1).id));
		readAgain();
		assertTrue(plugin.milestones.take(now).isEmpty());
	}

	@Test
	public void theFirstLoginAnnouncesNothingItFindsDone()
	{
		settleThenWatch();
		readAgain();
		assertEquals("Cook's Assistant and Varrock easy were done before anybody watched", 0,
			plugin.milestones.pendingCount());
	}

	@Test
	public void aWorldHopIsANewBaseline()
	{
		settleThenWatch();
		plugin.onGameStateChanged(state(GameState.HOPPING));
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		settleThenWatch();
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void aLogoutAndLoginIsANewBaseline()
	{
		settleThenWatch();
		plugin.onGameStateChanged(state(GameState.LOGIN_SCREEN));
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		settleThenWatch();
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void aLostConnectionToTheGameIsANewBaseline()
	{
		settleThenWatch();
		plugin.onGameStateChanged(state(GameState.CONNECTION_LOST));
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		/* Nothing ended the readers' session, so they answer at once: only the watcher can refuse. */
		readAgain();
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void loadingARegionIsNotABoundary()
	{
		settleThenWatch();
		plugin.onGameStateChanged(state(GameState.LOADING));
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		readAgain();
		assertEquals("a quest handed in just before a teleport is still watched", 1,
			plugin.milestones.pendingCount());
	}

	@Test
	public void anotherCharacterIsANewBaseline()
	{
		settleThenWatch();
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged(null, "a"));
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("a", "b"));
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		settleThenWatch();
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void turningAnnouncementsBackOnIsANewBaseline()
	{
		settleThenWatch();
		toggle(MapCompanionConfig.KEY_SHARE_MILESTONES, false);
		readAgain();
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		readAgain();
		toggle(MapCompanionConfig.KEY_SHARE_MILESTONES, true);
		readAgain();
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void eachKindNeedsItsOwnSharingChoiceAndTurningItBackOnIsANewBaseline()
	{
		settleThenWatch();
		config.set("shareQuestLog", false);
		toggle("shareQuests", false);
		toggle("shareDiaries", false);
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		readAgain();
		assertEquals("neither is watched with its choice off", 0, plugin.milestones.pendingCount());
		config.set("shareQuestLog", true);
		toggle("shareQuests", true);
		toggle("shareDiaries", true);
		readAgain();
		readAgain();
		assertEquals("and what was done meanwhile is not news when they come back", 0,
			plugin.milestones.pendingCount());
	}

	/**
	 * Within one tick, so the plugin's own per-tick check never sees the choice off: only the
	 * setting's handler can make the next reading a baseline.
	 */
	@Test
	public void aChoiceOffAndOnBetweenTwoTicksIsStillANewBaseline()
	{
		settleThenWatch();
		toggle("shareDiaries", false);
		game.setVarbit(VarbitIds.DIARY_VARROCK[1], 1);
		toggle("shareDiaries", true);
		readAgain();
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	@Test
	public void theTravelQuestsAloneAnnounceATravelQuest()
	{
		config.set("shareQuestLog", false);
		game.setQuest(Quest.PRIEST_IN_PERIL, QuestState.IN_PROGRESS);
		settleThenWatch();
		game.setQuest(Quest.PRIEST_IN_PERIL, QuestState.FINISHED);
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		readAgain();
		List<CompanionSnapshot.Milestone> sent = plugin.milestones.take(now);
		assertEquals("only the quest that choice shares", 1, sent.size());
		assertEquals("Priest in Peril", sent.get(0).quest);
	}

	@Test
	public void nothingIsWatchedUnpaired()
	{
		store.unset(MapCompanionConfig.KEY_PAIRING_CODE);
		settleThenWatch();
		game.setQuest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED);
		readAgain();
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "ABC234");
		readAgain();
		assertEquals(0, plugin.milestones.pendingCount());
	}

	/** The half of {@code observeGameState} the readers need: the client is up, so start settling. */
	private void settleThenWatch()
	{
		if (game.gameState() != GameState.LOGGED_IN)
		{
			game.login("Bob", 444, 3222, 3218, 0);
		}
		quests.onSessionResumed(now);
		diaries.onSessionResumed(now);
		now += 15_000L;
		plugin.announceCompletions(now);
	}

	/** A tick past the quest readers' thirty-second cache, so the next reading is a new one. */
	private void readAgain()
	{
		now += 30_000L;
		plugin.announceCompletions(now);
	}

	private void toggle(String key, boolean on)
	{
		config.set(key, on);
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(MapCompanionConfig.GROUP);
		changed.setKey(key);
		plugin.onConfigChanged(changed);
	}

	/** A boundary as the client raises it: logged out first where the game would be. */
	private GameStateChanged state(GameState state)
	{
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			game.logout();
		}
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private static <T> T construct(Class<T> type, Client client) throws Exception
	{
		Constructor<T> make = type.getDeclaredConstructor(Client.class);
		make.setAccessible(true);
		return make.newInstance(client);
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = MapCompanionPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
