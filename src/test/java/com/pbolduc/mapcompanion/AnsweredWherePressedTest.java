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
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Each of the group's presses is answered under itself, and the field the menu opens opens where the
 * menu was.
 *
 * <p>One line above the tabs answered all of them: Copy invite and the menu at the foot of Group were
 * answered against the tab strip, out of sight once the tab had scrolled, and Join another group…
 * opened its field there too, with nothing over it. The menu's answers are now a line under Copy
 * invite and Switch ▾, the status card's Rename group… answers in the card, and the field the menu
 * opens sits under the same row. Before a group, and while the map refuses one, it stays under the
 * orange button, where nothing else offers a way in.
 */
public class AnsweredWherePressedTest
{
	private final HarnessStore store = new HarnessStore();
	private final HarnessConfig config = new HarnessConfig(store, MapCompanionConfig.DEFAULT_MAP_URL);
	private final Pairing pairing = new Pairing(store, new Gson());
	private final MemberIdentity identity = new MemberIdentity(store);
	private final ConnectCard card = new ConnectCard(new JPanel(), config, new MapCompanionPlugin(), identity, pairing,
		new RelayClient(new OkHttpClient(), new Gson()), new DirectiveStream(new OkHttpClient(), new Gson(), (action, delay) -> null),
		new ConnectClient(new OkHttpClient(), new Gson()), new RecommendedSharing(config, store, () -> { }),
		() -> { }, open -> { }, () -> true, () -> MapCompanionConfig.DEFAULT_MAP_URL);
	private GroupPanel groupPanel;

	@Test
	public void joinAnotherGroupOpensTheFieldUnderTheGroupsButtons()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		draw();
		assertFalse("folded in a group", shown(named("join.field")));

		/* Switch ▾ → Join another group… */
		card.joinOpen = true;
		draw();
		Component field = named("join.field");
		assertTrue("under Copy invite and Switch ▾, in the Group tab", card.groupActions.isAncestorOf(field));
		assertTrue(shown(field));

		/* Hide the code field */
		card.joinOpen = false;
		draw();
		assertFalse(shown(named("join.field")));
		assertFalse("back above the tabs, for the next time something there needs it", card.groupActions.isAncestorOf(field));
	}

	@Test
	public void beforeAGroupTheFieldIsUnderTheOrangeButton()
	{
		draw();
		Component field = named("join.field");
		assertTrue(shown(field));
		assertFalse(card.groupActions.isAncestorOf(field));
	}

	/** The menu's Rename group… answers under the row it was pressed in; the card's link in the card. */
	@Test
	public void aRenameIsAnsweredWhereItWasPressed()
	{
		/* No secret yet, so the press stops at its first sentence and opens no dialog. */
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		draw();

		card.renameGroup(false);
		draw();
		JLabel menu = label("Log in once while connected, then rename the group.");
		assertTrue("under the group's buttons", card.groupActions.isAncestorOf(menu));
		assertTrue(shown(menu));

		card.renameGroup(true);
		draw();
		JLabel link = labelOutside(card.groupActions, "Log in once while connected, then rename the group.");
		assertTrue("in the status card, beside the link", shown(link));
		assertEquals("companion.cardMessage", link.getName());
	}

	/** A line about one group is not news under the next. */
	@Test
	public void aGroupsAnswerGoesWithTheGroup()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		card.renameGroup(false);
		draw();
		assertTrue(shown(named("group.message")));

		assertTrue(pairing.connect(JoinCode.parse("DEF567"), () -> true));
		draw();
		assertFalse(shown(named("group.message")));
	}

	/**
	 * "Invite copied. It expires in 7 days.", "Renamed to Sunday raids." and "Left the group." stayed
	 * on their lines until the next press, long after they were news. A confirmation is said for the
	 * ten seconds a run's "Run ended." is, then gives its line back; a refusal stays, since it still
	 * asks something of the player.
	 */
	@Test
	public void aConfirmationGoesAfterTenSecondsAndARefusalStays()
	{
		long now = System.currentTimeMillis();
		card.confirm("Left the group.");
		draw(now);
		JLabel left = label("Left the group.");
		assertTrue(shown(left));
		draw(now + FarmRunClient.Said.CONFIRMATION_MS + 1_000L);
		assertFalse(shown(left));

		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		card.confirmUnderGroup("Invite copied. It expires in 7 days.");
		draw(now + 9_000L);
		assertTrue("news for a moment", shown(named("group.message")));
		draw(now + FarmRunClient.Said.CONFIRMATION_MS + 1_000L);
		assertFalse(shown(named("group.message")));

		card.refuseUnderGroup(ConnectCard.INVITE_FAILED);
		draw(now + 60_000L);
		assertTrue("a refusal is still said a minute on", shown(named("group.message")));
	}

	/**
	 * Every line under a group's press was one grey, so "Paste the invite a friend sent you…" read as
	 * "Found an invite to KQX 482. Press Join." does. A press that did not happen is in the refusal
	 * pink; a step on the way and a press that worked keep the line's grey.
	 */
	@Test
	public void aRefusalIsPinkAndAStepOrAConfirmationIsGrey()
	{
		draw();
		((AbstractButton) named("join.button")).doClick();
		draw();
		assertEquals(MapCompanionPanel.REFUSED,
			label("Paste the invite a friend sent you, or press Connect to start your own group.").getForeground());
		card.say("Found an invite to KQX 482. Press Join.");
		draw();
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label("Found an invite to KQX 482. Press Join.").getForeground());

		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		card.renameGroup(false);
		draw();
		assertEquals(MapCompanionPanel.REFUSED, label("Log in once while connected, then rename the group.").getForeground());
		card.confirmUnderGroup("Invite copied.");
		draw();
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label("Invite copied.").getForeground());
	}

	/**
	 * An invite is asked for as a member, and a member is a character: at the login screen Copy invite
	 * waits for one, where it answered a press with "Log in to a character to copy an invite.". Switch ▾
	 * acts as the player, and stays.
	 */
	@Test
	public void atTheLoginScreenCopyInviteWaitsAndSwitchStays()
	{
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> true));
		draw();
		assertTrue(card.copyInviteButton.isEnabled());
		card.refreshButtons(true, true, false, pairing.knownCodes(), groupPanel, System.currentTimeMillis());
		assertFalse(card.copyInviteButton.isEnabled());
		assertTrue(labelled(card.groupActions, "Switch ▾").isEnabled());
	}

	/** The first button reading these words. */
	private static AbstractButton labelled(Component component, String words)
	{
		if (component instanceof AbstractButton && words.equals(((AbstractButton) component).getText()))
		{
			return (AbstractButton) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				AbstractButton found = labelled(child, words);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * An address typed without its scheme is the map meant, not a mistake: https for a map anywhere,
	 * http for one on this machine, and nothing else the address rule refuses is let through.
	 */
	@Test
	public void anAddressTypedWithoutItsSchemeIsCompleted()
	{
		assertEquals("https://osrs-map.com", Pairing.normalizeServer(Pairing.withScheme(" osrs-map.com ")));
		assertEquals("https://example.test/map", Pairing.normalizeServer(Pairing.withScheme("example.test/map/")));
		assertEquals("http://localhost:3000", Pairing.normalizeServer(Pairing.withScheme("localhost:3000")));
		assertEquals("http://127.0.0.1:3000", Pairing.normalizeServer(Pairing.withScheme("127.0.0.1:3000")));
		assertEquals("http://localhost:3000", Pairing.withScheme("http://localhost:3000"));
		assertNull("a scheme of its own is judged as it is", Pairing.normalizeServer(Pairing.withScheme("http://example.test")));
		assertNull(Pairing.normalizeServer(Pairing.withScheme("file:///tmp/map")));
		assertNull(Pairing.normalizeServer(Pairing.withScheme("example.test?key=value")));
		assertEquals("empty is still the default map", MapCompanionConfig.DEFAULT_MAP_URL,
			Pairing.normalizeServer(Pairing.withScheme("")));
	}

	/** The sidebar's own order: the Group tab, then the status card, then the buttons under it. */
	private void draw()
	{
		draw(System.currentTimeMillis());
	}

	/** The same, as of {@code atMs}. */
	private void draw(long atMs)
	{
		boolean paired = !pairing.code().isEmpty();
		boolean ready = paired && pairing.hasServerConsent();
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
		groupPanel.refresh(ready, atMs);
		card.refreshStatus(Health.LIVE, pairing.code(), paired, ready);
		card.refreshButtons(paired, ready, pairing.knownCodes(), groupPanel, atMs);
	}

	/** The first label reading these words, in the card above the tabs or in the Group tab's foot. */
	private JLabel label(String words)
	{
		JLabel found = find(card, words, null);
		found = found != null ? found : find(card.groupActions, words, null);
		assertTrue("no label reads " + words, found != null);
		return found;
	}

	/** The same, in the card above the tabs only. */
	private JLabel labelOutside(Container outside, String words)
	{
		JLabel found = find(card, words, outside);
		assertTrue("no label outside reads " + words, found != null);
		return found;
	}

	/** By name, in the card above the tabs or in the Group tab's foot, which the sidebar places apart. */
	private Component named(String name)
	{
		Component found = named(card, name);
		return found != null ? found : named(card.groupActions, name);
	}

	private static JLabel find(Component component, String words, Container outside)
	{
		if (component == outside)
		{
			return null;
		}
		if (component instanceof JLabel && words.equals(plain(((JLabel) component).getText())))
		{
			return (JLabel) component;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JLabel found = find(child, words, outside);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static String plain(String html)
	{
		return html == null ? "" : html.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
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

	/** Whether nothing between this and the card, or the Group tab's foot, is hidden. */
	private boolean shown(Component component)
	{
		if (component == null)
		{
			return false;
		}
		for (Component at = component; at != null; at = at.getParent())
		{
			if (!at.isVisible())
			{
				return false;
			}
			if (at == card || at == card.groupActions)
			{
				return true;
			}
		}
		return true;
	}
}
