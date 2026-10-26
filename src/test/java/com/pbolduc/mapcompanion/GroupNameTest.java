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
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JMenuItem;
import javax.swing.plaf.basic.BasicHTML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * A group's name, from the map's reply to the sidebar's menu of groups.
 *
 * <p>The map sends it on every reply to a member, beside {@code memberSecret}; the tick files it
 * under the group that reply answered; the Switch menu prints it beside the code. Each step can go
 * wrong without a symptom: silence from a legacy group or an older map read as "forget it", a reply
 * that lands after a group change naming the wrong group, and a name — text somebody else typed on
 * the map — rendered as markup inside this client. The storage rules, including why a name repeated
 * on every reply is not a config write on every reply, are in {@code PairingTest}.
 */
public class GroupNameTest
{
	private final Gson gson = new Gson();

	@Test
	public void aReplyNamesTheGroupAndTheTickFilesIt()
	{
		MapCompanionPlugin plugin = pluginJoinedTo("KQX482");
		RelayResponse reply = gson.fromJson(
								  "{\"pollIntervalMs\":2000,\"groupName\":\"Sunday raids\"}", RelayResponse.class);

		plugin.noteGroupName("KQX482", reply.groupName);
		plugin.storeToldName();

		assertEquals("Sunday raids", plugin.pairing.name("KQX482"));
	}

	@Test
	public void aReplyWithoutTheFieldKeepsTheNameWeHold()
	{
		/**
		 * A legacy group has no name to send and an older map sends none. Either is "keep what you
		 * have", as it is for the secret — read as "forget it", one reply from an older map would
		 * turn every name in the menu back into a code.
		 */
		MapCompanionPlugin plugin = pluginJoinedTo("KQX482");
		plugin.noteGroupName("KQX482", "Sunday raids");
		plugin.storeToldName();

		RelayResponse quiet = gson.fromJson("{\"pollIntervalMs\":2000}", RelayResponse.class);
		assertNull(quiet.groupName);
		plugin.noteGroupName("KQX482", quiet.groupName);
		plugin.storeToldName();

		assertEquals("Sunday raids", plugin.pairing.name("KQX482"));
	}

	@Test
	public void aReplyThatLandsAfterAGroupChangeNamesTheGroupItAnswered()
	{
		/** The same race as the secret's: the reply is about the post, not about whoever is current. */
		MapCompanionPlugin plugin = pluginJoinedTo("KQX482");
		plugin.noteGroupName("KQX482", "Sunday raids");

		plugin.pairing.join(JoinCode.parse("PMN934"));
		plugin.onGroupChanged();
		plugin.storeToldName();

		assertNull("the group joined since is not given the other one's name", plugin.pairing.name("PMN934"));
		assertEquals("Sunday raids", plugin.pairing.name("KQX482"));
	}

	@Test
	public void theMenuSaysTheNameBesideTheCodeOrTheCodeAlone()
	{
		assertEquals("Sunday raids · KQX 482", MapCompanionPanel.groupLabel("KQX482", "Sunday raids"));
		assertEquals("KQX 482", MapCompanionPanel.groupLabel("KQX482", null));
		/** An adopted legacy group is named after its own code until its owner names it: once is enough. */
		assertEquals("KQX 482", MapCompanionPanel.groupLabel("KQX482", "KQX 482"));
	}

	@Test
	public void aNameIsShownAsWordsAndNeverAsMarkup()
	{
		/**
		 * Nineteen characters, so the map would accept it as a name. A Swing menu item renders any
		 * text starting with {@code <html>} as markup, which from a name somebody else typed means
		 * anything laid out inside this client, images fetched from anywhere included.
		 */
		String name = "<html><b>Sunday</b>";
		JMenuItem item = MapCompanionPanel.groupMenuItem(MapCompanionPanel.groupLabel("KQX482", name));

		assertEquals(name + " · KQX 482", item.getText());
		assertNull("no markup view was built for it", item.getClientProperty(BasicHTML.propertyKey));
		/** And the switch is what did that: the same words on a plain item are built as markup. */
		assertNotNull(new JMenuItem(item.getText()).getClientProperty(BasicHTML.propertyKey));
	}

	/** The plugin with the collaborators these paths touch, as {@code MemberSecretTest} builds it. */
	private MapCompanionPlugin pluginJoinedTo(String code)
	{
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		plugin.pairing = new Pairing(new FakeStore(), gson);
		plugin.milestones = new MilestoneOutbox();
		plugin.destinationState = new DestinationState(gson);
		plugin.destinationMarker = new TestDestinationMarker();
		plugin.groupMarkers = new GroupMarkers(null, null);
		HarnessGame game = new HarnessGame();
		plugin.rewardObserver = new RewardObserver(game.client(), new CollectionItemCatalog(game.client()));
		plugin.pairing.join(JoinCode.parse(code));
		return plugin;
	}

	private static final class FakeStore implements ConfigStore
	{
		private final Map<String, String> values = new HashMap<>();

		@Nullable
		@Override
		public String get(String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String key, String value)
		{
			values.put(key, value);
		}

		@Override
		public void unset(String key)
		{
			values.remove(key);
		}
	}
}
