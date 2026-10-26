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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Every quest this plugin asks about is one the client can answer.
 *
 * <p><b>The failure this catches has no symptom at all.</b> {@code QuestReader} walks
 * {@code Quest.values()} and reports the ones its whitelist names; a name that matches no
 * {@code Quest} is simply never mentioned, on the wire or anywhere else. The map then treats that
 * quest as one nobody asked about — which is exactly what it should do for a plugin that does not
 * send it — so a route gated on it stays uncertain forever and nothing, on either side, is wrong
 * enough to notice.
 *
 * <p>Two entries were wrong that way, and they came from opposite directions. "Freeing King
 * Awowogei" is what the map's transport table calls a Recipe for Disaster subquest that the client
 * calls "Recipe for Disaster - King Awowogei", so the Ape Atoll teleport was unrankable for
 * everybody. "Barbarian Assault tutorial" is not a quest in the client at all. The first is
 * translated; the second is gone.
 *
 * <p>The map's own contract test checks the other direction — that nothing here is asked about
 * that no transport gates on. Between them, a name has to be both useful and answerable.
 */
public class QuestReaderTest
{
	/** Every travel quest costs a client script, and a snapshot goes out every few seconds. */
	@Test
	public void travelQuestsAreReadOnceEveryThirtySeconds()
	{
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		net.runelite.api.Client client = (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
			net.runelite.api.Client.class.getClassLoader(), new Class<?>[] {net.runelite.api.Client.class}, (proxy, method, args) ->
			{
				if (method.getName().equals("runScript")) { calls.incrementAndGet(); return null; }
				if (method.getName().equals("getIntStack")) return new int[] {2};
				throw new UnsupportedOperationException(method.getName());
			});
		QuestReader reader = new QuestReader(client);
		java.util.Map<String, String> first = reader.readStates(100_000);
		int once = calls.get();
		assertTrue(once >= QuestReader.GATED_QUESTS.size());
		assertEquals(first, reader.readStates(110_000));
		assertEquals(first.size(), reader.readFinished(120_000).size());
		assertEquals("within the window nothing is read again", once, calls.get());
		reader.readStates(130_000);
		assertEquals(2 * once, calls.get());
	}

		/** The first seconds of a session read every quest as not started, which is a claim, not a gap. */
	@Test
	public void travelQuestsAreNotBelievedUntilTheSessionSettles()
	{
		QuestReader reader = new QuestReader(null);
		assertFalse("not yet resumed", reader.settled(100_000));
		reader.onSessionResumed(100_000);
		assertFalse(reader.settled(114_999));
		assertTrue(reader.settled(115_000));
		reader.onSessionEnded();
		assertFalse("a logout starts the wait again", reader.settled(200_000));
	}

		@Test
	public void theFullLogSettlesCachesAndResetsWithoutChangingTheTravelWhitelist()
	{
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		net.runelite.api.Client client = (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
			net.runelite.api.Client.class.getClassLoader(), new Class<?>[] {net.runelite.api.Client.class}, (proxy, method, args) ->
			{
				if (method.getName().equals("runScript")) { calls.incrementAndGet(); return null; }
				if (method.getName().equals("getIntStack")) return new int[] {2};
				throw new UnsupportedOperationException(method.getName());
			});
		QuestReader reader = new QuestReader(client);
		org.junit.Assert.assertNull(reader.readLog(100_000));
		reader.onSessionResumed(100_000);
		org.junit.Assert.assertNull(reader.readLog(114_999));
		java.util.Map<String, String> first = reader.readLog(115_000);
		assertTrue(first.size() > QuestReader.GATED_QUESTS.size());
		assertEquals("finished", first.get("Cook's Assistant"));
		assertTrue(first.size() <= 300);
		assertTrue(first.keySet().stream().allMatch(name -> name.length() <= 60));
		assertTrue(first.values().stream().allMatch("finished"::equals));
		int initialCalls = calls.get();
		reader.readLog(144_999);
		assertEquals(initialCalls, calls.get());
		reader.readLog(145_000);
		assertEquals(initialCalls * 2, calls.get());
		first.clear();
		assertFalse(reader.readLog(145_001).isEmpty());
		reader.onSessionEnded();
		org.junit.Assert.assertNull(reader.readLog(200_000));
		reader.onSessionResumed(200_000);
		org.junit.Assert.assertNull(reader.readLog(214_999));
		assertFalse(reader.readLog(215_000).isEmpty());
	}

	@Test
	public void everyQuestItAsksAboutIsOneTheClientNames()
	{
		Set<String> reachable = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			reachable.add(QuestReader.mapName(quest.getName()));
		}

		List<String> unanswerable = new ArrayList<>();
		for (String wanted : QuestReader.GATED_QUESTS)
		{
			if (!reachable.contains(wanted))
			{
				unanswerable.add(wanted);
			}
		}
		assertEquals(
			"the client has no Quest that reaches these names, so they are never reported at all: "
			+ unanswerable,
			new ArrayList<String>(),
			unanswerable);
	}

	/**
	 * Translated to "Freeing King Awowogei" until the map's table took the client's own spelling of
	 * that subquest on 22 September 2026. Translating it since then sent a name the map no longer
	 * gated on, so the Ape Atoll teleport stayed locked for every paired player; it now travels as
	 * the client spells it, like everything else.
	 */
	@Test
	public void sendsEveryQuestUnderTheNameBothSidesUse()
	{
		assertEquals("Recipe for Disaster - King Awowogei",
					 QuestReader.mapName("Recipe for Disaster - King Awowogei"));
		assertTrue(QuestReader.GATED_QUESTS.contains("Recipe for Disaster - King Awowogei"));
		assertFalse(QuestReader.GATED_QUESTS.contains("Freeing King Awowogei"));
		assertEquals("Lunar Diplomacy", QuestReader.mapName("Lunar Diplomacy"));
		assertEquals("The Fremennik Trials", QuestReader.mapName("The Fremennik Trials"));
	}

	@Test
	public void asksAboutTheTravelQuestsAndNotTheQuestLog()
	{
		/**
		 * The count is pinned so that dropping a row while adding one cannot pass unnoticed, and
		 * because the README states this number to a reader deciding whether to install the plugin.
		 */
		assertEquals(49, QuestReader.GATED_QUESTS.size());
		assertTrue("a whitelist the size of the quest log is not a whitelist",
				   QuestReader.GATED_QUESTS.size() < Quest.values().length / 2);
	}
}
