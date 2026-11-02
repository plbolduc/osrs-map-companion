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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.callback.ClientThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A group the map refuses, on the status card: a few words in the refusal pink beside a red dot,
 * and the invite field under the card with what to put in it, for as long as the map holds to it.
 *
 * <p>The card reads the refusal the relay keeps ({@link RelayClient#getRefused}) rather than its
 * sentence, which ages out every five minutes for one retry. The connection's own health in that
 * retry can be green, which is how the group's name came back between two refusals, and why these
 * draw the card with a healthy connection under a refusal.
 */
public class RefusedGroupTest
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";
	private static final Color PINK = new Color(239, 150, 150);

	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final RelayClient relay = new RelayClient(new OkHttpClient(), new Gson());
	private final ConnectCard card = new ConnectCard(new JPanel(), config, new MapCompanionPlugin(), identity, pairing,
		relay, new DirectiveStream(new OkHttpClient(), new Gson(), (action, delay) -> null),
		new ConnectClient(new OkHttpClient(), new Gson()), new RecommendedSharing(config, store, () -> { }),
		() -> { }, open -> { }, () -> true, () -> MapCompanionConfig.DEFAULT_MAP_URL);
	private GroupPanel groupPanel;

	@Test
	public void aRemovedMemberSeesTheFieldAndTheWayOutThroughTheRetries() throws Exception
	{
		joined("ABC234");
		relay.handle(reply(410, "{\"reason\":\"removed\"}"), null);

		/* The retry: the sentence has aged out and the last post to land still reads as live. */
		draw(Health.LIVE);

		List<String> words = shownWords(card);
		assertTrue(words.toString(), words.contains("Removed from this group"));
		assertEquals(PINK, label("Removed from this group").getForeground());
		assertTrue("the group's name is the second line", words.contains("ABC 234"));
		assertTrue(words.toString(), words.contains("Have an invite?"));
		assertTrue(words.toString(), words.contains(
			"Paste another group's invite here, or use Leave group in Settings → Advanced to start over."));
		assertTrue("the field itself", shown(named(card, "join.field")));

		relay.handle(reply(200, "{\"pollIntervalMs\":2000}"), null);
		draw(Health.LIVE);

		words = shownWords(card);
		assertTrue(words.toString(), words.contains("ABC 234"));
		assertFalse(words.toString(), words.contains("Removed from this group"));
		assertFalse("a group that took the player back folds the field away", shown(named(card, "join.field")));
		assertFalse(words.toString(), words.contains("Have an invite?"));
	}

	@Test
	public void aGroupThatNeedsAnInviteSaysWhoToAskByName() throws Exception
	{
		joined("ABC234");
		pairing.rememberName("ABC234", "Sunday raids");
		relay.handle(reply(403, "{\"reason\":\"needs_code\"}"), null);
		draw(Health.ERROR);

		List<String> words = shownWords(card);
		assertTrue(words.toString(), words.contains("Needs an invite"));
		assertTrue(words.toString(), words.contains("Ask someone in Sunday raids for an invite and paste it here."));
		assertTrue(shown(named(card, "join.field")));
	}

	/** An invite cannot answer a map that cannot read this version, so it gets the words and no field. */
	@Test
	public void aPluginTheMapCannotReadGetsNoField() throws Exception
	{
		joined("ABC234");
		relay.handle(reply(422, "{}"), null);
		draw(Health.ERROR);

		List<String> words = shownWords(card);
		assertTrue(words.toString(), words.contains("Plugin out of date"));
		assertEquals(PINK, label("Plugin out of date").getForeground());
		assertFalse(shown(named(card, "join.field")));
	}

	/** The card's words for each refusal, and what the field under it is for. */
	@Test
	public void eachRefusalHasItsWordsAndItsNextStep()
	{
		assertEquals("Group is full", ConnectCard.refusedWords(RelayClient.Refused.FULL));
		assertEquals("This group was closed", ConnectCard.refusedWords(RelayClient.Refused.CLOSED));
		assertEquals("Group gone from the map", ConnectCard.refusedWords(RelayClient.Refused.GONE));
		assertEquals("Group code not accepted", ConnectCard.refusedWords(RelayClient.Refused.BAD_CODE));
		assertEquals("Paste the invite again.", ConnectCard.refusedNextStep(RelayClient.Refused.BAD_CODE, "KQX 482"));
		assertEquals("Ask someone in KQX 482 to make room, or paste another group's invite here.",
			ConnectCard.refusedNextStep(RelayClient.Refused.FULL, "KQX 482"));
		assertEquals(ConnectCard.refusedNextStep(RelayClient.Refused.REMOVED, "KQX 482"),
			ConnectCard.refusedNextStep(RelayClient.Refused.CLOSED, "KQX 482"));
		assertNull(ConnectCard.refusedNextStep(RelayClient.Refused.OUT_OF_DATE, "KQX 482"));
		assertNull(ConnectCard.refusedNextStep(null, "KQX 482"));
	}

	private void joined(String code)
	{
		assertTrue(pairing.connect(JoinCode.parse(code), () -> true));
		pairing.rememberSecret(pairing.code(), identity.id(), SECRET);
	}

	/** The sidebar's own order: the Group tab, then the status card, then the buttons under it. */
	private void draw(Health health)
	{
		if (groupPanel == null)
		{
			DestinationState destinations = new DestinationState(new Gson());
			GroupState group = new GroupState(destinations);
			ClientThread now = new ClientThread()
			{
				@Override
				public void invokeLater(Runnable r)
				{
					r.run();
				}
			};
			RoutePress press = new RoutePress(new OkHttpClient(), new Gson(), MeetupClientTest.game(false), now, pairing,
				identity, new DestinationRoute(), destinations, new DestinationMarker(null, null, null, null, null, null)
				{
					@Override
					void sync()
					{
					}
				});
			MeetupClient meetups = new MeetupClient(new OkHttpClient(), new Gson(), MeetupClientTest.game(false), now,
				pairing, identity, destinations, group, link -> { });
			groupPanel = new GroupPanel(destinations, group, press, meetups, config, new GameSprites(null, null, null),
				card.copyInviteButton);
		}
		groupPanel.refresh(true);
		card.refreshStatus(health, pairing.code(), true, true);
		card.refreshButtons(true, true, pairing.knownCodes(), groupPanel);
	}

	/** The words a player can see: every label and button inside nothing hidden, without their markup. */
	private static List<String> shownWords(Component root)
	{
		List<String> out = new ArrayList<>();
		collect(root, out);
		return out;
	}

	private static void collect(Component component, List<String> out)
	{
		if (!component.isVisible())
		{
			return;
		}
		if (component instanceof JLabel && ((JLabel) component).getText() != null)
		{
			out.add(plain(((JLabel) component).getText()));
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, out);
			}
		}
	}

	private static String plain(String html)
	{
		return html.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
	}

	private JLabel label(String words)
	{
		JLabel found = find(card, words);
		assertTrue("no label reads " + words, found != null);
		return found;
	}

	private static JLabel find(Component component, String words)
	{
		if (component instanceof JLabel && words.equals(plain(String.valueOf(((JLabel) component).getText()))))
		{
			return (JLabel) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JLabel found = find(child, words);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static Component named(Component component, String name)
	{
		if (name.equals(component.getName()))
		{
			return component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				Component found = named(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Whether nothing between this and the card is hidden. */
	private boolean shown(Component component)
	{
		for (Component at = component; at != null && at != card.getParent(); at = at.getParent())
		{
			if (!at.isVisible())
			{
				return false;
			}
		}
		return component != null;
	}

	private static Response reply(int code, String body)
	{
		return new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid/api/companion/snapshot").build())
			.protocol(Protocol.HTTP_1_1)
			.code(code)
			.message("")
			.body(ResponseBody.create(JSON, body))
			.build();
	}
}
