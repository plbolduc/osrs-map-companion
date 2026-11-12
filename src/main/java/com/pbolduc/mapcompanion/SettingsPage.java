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
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import static com.pbolduc.mapcompanion.MapCompanionPanel.*;
import static net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR;

/**
 * Settings, the page behind the gear: What's new, every sharing choice ({@link SharingControls}),
 * then Show what's sent, Leave group and Forget kill log at its foot.
 *
 * <p>A class of its own because it is a page of its own ({@link SidebarPages}), set once and seldom
 * read again, and nothing on it is part of a session. What Leave group did is said on the daily page
 * it turns back to, under the join field, through {@link ConnectCard}. {@link MapCompanionPanel} hands
 * it to the pages and calls {@link #refresh(boolean)} last in its own.
 *
 * <p>Two of the buttons are about trust rather than about the map. <b>Show what's sent</b> prints
 * the actual snapshot, built by the same code that would post it, because a plugin that claims to
 * send only what a list says should be willing to show its working. <b>Leave group</b> is the
 * other half of joining one: it clears the code, forgets the id this installation was known by
 * along with every credential a map ever issued that id, and drops the group from the remembered
 * list, so that leaving is a thing a player can do rather than a thing they have to trust was
 * done.
 */
@Slf4j
final class SettingsPage extends JPanel
{
	private final MapCompanionConfig config;
	private final DestinationState destinationState;
	private final LootLog lootLog;
	private final SnapshotBuilder snapshotBuilder;
	private final ClientThread clientThread;
	private final ConfigStore settings;
	private final MemberIdentity identity;
	private final Pairing pairing;
	private final Gson gson;
	private final FarmRunState farmRunState;
	private final FarmRunClient farmRunClient;
	/** Where Leave group and a new map address say what they did, under the join field on the daily page. */
	private final ConnectCard connectCard;
	/** The sidebar's refresh, run after a press has changed what it should say. */
	private final Runnable refresh;
	/** Settings in place of the daily page, or the daily page back. See {@link SidebarPages}. */
	private final Consumer<Boolean> settingsOpen;

	private final JButton previewButton = smallButton("Show what's sent");
	private final SharingControls sharingControls;

	/* The reset actions are the page's foot: neither is a thing a player does every session. */
	private final JPanel foot = row(DARK_GRAY_COLOR);
	private List<JButton> shownFoot = new ArrayList<>();
	private final JButton leaveButton = smallButton("Leave group");
	/** Only ever present once there is a log to forget. A log you cannot clear is one you cannot trust. */
	private final JButton forgetLootButton = smallButton("Forget kill log");

	SettingsPage(MapCompanionConfig config, DestinationState destinationState, LootLog lootLog,
		SnapshotBuilder snapshotBuilder, ClientThread clientThread, ConfigStore settings, MemberIdentity identity,
		Pairing pairing, Gson gson, FarmRunState farmRunState, FarmRunClient farmRunClient, ConnectCard connectCard,
		Runnable refresh, Consumer<Boolean> settingsOpen, Supplier<String> mapUrl, java.util.function.BooleanSupplier paths)
	{
		this.config = config;
		this.destinationState = destinationState;
		this.lootLog = lootLog;
		this.snapshotBuilder = snapshotBuilder;
		this.clientThread = clientThread;
		this.settings = settings;
		this.identity = identity;
		this.pairing = pairing;
		this.gson = gson;
		this.farmRunState = farmRunState;
		this.farmRunClient = farmRunClient;
		this.connectCard = connectCard;
		this.refresh = refresh;
		this.settingsOpen = settingsOpen;
		this.sharingControls = new SharingControls(config, settings, paths);
		column(this, DARK_GRAY_COLOR);

		/*
		 * Where an update's changes are written down, the same page the once-per-version chat line
		 * names: RuneLite shows no changelog, and the README carries only the latest few lines.
		 */
		JButton whatsNew = linkButton("What's new in " + CompanionVersion.VERSION + " ›");
		whatsNew.setName("settings.whatsNew");
		whatsNew.setForeground(ColorScheme.BRAND_ORANGE);
		whatsNew.setToolTipText(Words.of("settings.whatsNewTip"));
		whatsNew.addActionListener(e -> LinkBrowser.browse(mapUrl.get() + "/whats-new"));
		add(whatsNew);
		add(gap(4, DARK_GRAY_COLOR));
		add(sectionHead("What you share"));
		add(MapCompanionPanel.wrapped(Words.of("settings.shareSub"), 11f, ColorScheme.LIGHT_GRAY_COLOR, MapCompanionPanel.CARD_TEXT_WIDTH + 16));
		add(sharingControls);
		/*
		 * The foot: the three presses that are about trust or about leaving, none a setting. The map
		 * address is in RuneLite's own list alone: only somebody running a map of their own changes it.
		 */
		add(gap(8, DARK_GRAY_COLOR));
		add(hug(previewButton, DARK_GRAY_COLOR));
		add(gap(8, DARK_GRAY_COLOR));
		add(left(foot));

		previewButton.setToolTipText(Words.of("settings.previewTip"));
		previewButton.addActionListener(e -> showSnapshotPreview());
		leaveButton.addActionListener(e -> leaveGroup());
		forgetLootButton.addActionListener(e ->
		{
			forgetKillLog(lootLog, this::confirmForgetKillLog);
			refresh.run();
		});
	}

	/** The actual address is editable here; there is no second default or explanatory footnote. */
	void refresh(boolean paired)
	{
		sharingControls.refresh();

		List<JButton> footRow = new ArrayList<>();
		if (paired)
		{
			footRow.add(leaveButton);
		}
		if (lootLog.hasLog())
		{
			footRow.add(forgetLootButton);
		}
		shownFoot = syncRow(foot, footRow, shownFoot);
		foot.setVisible(!footRow.isEmpty());
	}

	/**
	 * Show the snapshot that would go out, built by the code that would build it.
	 *
	 * <p>Not a description of the wire format and not a second implementation of one: this calls
	 * {@link SnapshotBuilder} exactly as the game tick does, so anything the toggles let through is
	 * visible here and anything they do not cannot appear. The README asks a reviewer to read two
	 * files and believe them; this asks nobody to believe anything.
	 *
	 * <p>Built on the client thread because every field in it is live game state, then handed back to
	 * Swing to be shown.
	 */
	private void showSnapshotPreview()
	{
		clientThread.invoke(() ->
		{
			String json;
			try
			{
				CompanionSnapshot snapshot = snapshotBuilder.build(System.currentTimeMillis());
				json = snapshot == null
				? null
				: gson.newBuilder().setPrettyPrinting().create().toJson(snapshot);
			}
			catch (RuntimeException e)
			{
				log.debug("could not build a preview snapshot", e);
				json = null;
			}
			String finished = json;
			SwingUtilities.invokeLater(() -> presentPreview(finished));
		});
	}

	private void presentPreview(@Nullable String json)
	{
		String suffix = pairing.suffix();
		String text = previewText(json, !pairingCode().isEmpty(), pairing.hasServerConsent(), pairing.serverLabel(),
			suffix == null ? null : pairingCode() + "-" + masked(suffix), pairing.secret(identity.id()) != null,
			config.shareName(), gson);
		JTextArea area = new JTextArea(text, 22, 44);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setCaretPosition(0);
		/* Its one button only closes it, so it says so rather than agreeing to something with OK. */
		JOptionPane.showOptionDialog(
			SwingUtilities.getWindowAncestor(this),
			new JScrollPane(area),
			"What this plugin sends",
			JOptionPane.DEFAULT_OPTION,
			JOptionPane.PLAIN_MESSAGE,
			null,
			new Object[]{"Close"},
			"Close");
	}

	/**
	 * The words around the snapshot, chosen by what is true now: a snapshot heads only a window that has
	 * one, so logged out it says nothing goes out rather than how often something does, and before a
	 * group, or before Connect approves the map, it says what Connect would start.
	 *
	 * <p>The keys are named, never printed. The RuneLite code is shown by its ends because it is on
	 * the player's own card on the map and they may want to check the two agree; the member key and
	 * this RuneLite's key are standing credentials, and a window whose whole purpose is to be read —
	 * and screenshotted into a Discord channel when somebody is asking for help — is the last place to
	 * spell one out. What each is for is the part a player is owed, in a sentence each, with its header
	 * in brackets for anybody checking a network log.
	 */
	static String previewText(@Nullable String json, boolean grouped, boolean approved, String server,
		@Nullable String runeLiteCode, boolean memberKey, boolean nameShared, Gson gson)
	{
		if (json == null)
		{
			return Words.of("settings.loggedOut");
		}
		StringBuilder text = new StringBuilder();
		if (!grouped)
		{
			text.append(Words.of("settings.notGrouped") + " ").append(server).append(":\n\n");
		}
		else if (!approved)
		{
			text.append("You're not connected to ").append(server).append(Words.of("settings.notConnected"));
		}
		else
		{
			text.append("This goes to ").append(server).append(Words.of("settings.sendsEvery"));
		}
		text.append(memberKey
			? Words.of("settings.twoKeys")
			: Words.of("settings.oneKey") + (grouped ? " Leave group replaces it." : ""));
		text.append("\n\n");
		/* It stops being sent the moment the map says it has connected this RuneLite to a browser. */
		if (runeLiteCode != null)
		{
			text.append(Words.of("settings.codeGoes") + " ").append(runeLiteCode)
				.append(Words.of("settings.codeDeleted"));
		}
		text.append(json);
		String nick = nick(json, gson);
		if (SnapshotBuilder.NO_NICKNAME.equals(nick))
		{
			text.append(Words.of("settings.meMeans") + " ").append(nameShared ? "your character name." : "Member.");
		}
		return text.toString();
	}

	@Nullable
	private static String nick(String json, Gson gson)
	{
		try
		{
			JsonObject member = gson.fromJson(json, JsonObject.class).getAsJsonObject("member");
			return member == null || !member.has("nick") ? null : member.get("nick").getAsString();
		}
		catch (RuntimeException unreadable)
		{
			return null;
		}
	}

	/**
	 * Leave, properly.
	 *
	 * <p>Clearing the code stops the sending; forgetting the id is the other half, and it is the half
	 * a player cannot do for themselves. Without it a member who left and came back arrived as the
	 * same dot they were before — a stable identifier for a person across every group they ever join,
	 * which is exactly the thing {@link MemberIdentity} is arranged not to create.
	 *
	 * <p>The member secrets go with the id rather than with the group, and every one of them goes.
	 * A secret proves that a particular member id belongs to this installation, so an id that no
	 * longer exists leaves nothing for any of them to prove — and a credential kept for a member
	 * nobody can be is the opposite of what this button says. {@link Pairing#leave} does it.
	 *
	 * <p>The kill log is deliberately untouched. It is the player's own, it never needed the map, and
	 * leaving a group is not a reason to throw away a year of counting. It has its own button.
	 *
	 * <p>The group also leaves the remembered list, because a one-press way back into a group
	 * somebody deliberately left is the opposite of what this button says.
	 *
	 * <p>The question names the group in its title and the act on its button, with Cancel the default
	 * so Enter does not leave, and it says what the press costs in the player's words rather than
	 * the plugin's: the way back is a new invite, and so is the way back to any other group that
	 * keeps members, which goes from the list with this one ({@link Pairing#leave}).
	 */
	private void leaveGroup()
	{
		String code = pairingCode();
		int others = 0;
		for (String held : pairing.codesHoldingSecrets())
		{
			if (!held.equalsIgnoreCase(code))
			{
				others++;
			}
		}
		if (!MapCompanionPanel.confirm(SwingUtilities.getWindowAncestor(this), leaveMessage(others),
			"Leave " + ConnectCard.statusGroup(code, pairing.name(code)) + "?", "Leave group"))
		{
			return;
		}
		/* Clearing the code raises a ConfigChanged, which is what stops the stream and resets the relay. */
		pairing.leave();
		identity.forget();
		farmRunClient.clear();
		farmRunState.clear();
		destinationState.clear();
		connectCard.confirm("Left the group.");
		settingsOpen.accept(false);
		refresh.run();
	}

	/**
	 * What Leave group's question says, one paragraph that RuneLite's dialog wraps itself: what
	 * leaving does, the way back, what it costs the {@code others} remembered groups that keep
	 * members, and the one thing it keeps.
	 */
	static String leaveMessage(int others)
	{
		return Words.of("settings.leaving")
			+ (others == 1 ? Words.of("settings.leaveOther")
			: others > 1 ? " Your " + others + Words.of("settings.leaveOthers")
			: "")
			+ " Your kill log stays.";
	}

	/**
	 * Forget kill log's question, put the way Leave group beside it puts its own: the act on its
	 * button, and Cancel the default.
	 *
	 * <p>Both buttons erase something and sit side by side, so both ask. One press would clear every
	 * count this character has and its pinned drop, and the plugin keeps no other copy to give back.
	 */
	private boolean confirmForgetKillLog()
	{
		return MapCompanionPanel.confirm(SwingUtilities.getWindowAncestor(this),
			Words.of("settings.forgetLoot"), "Forget kill log", "Forget kill log");
	}

	/** Clear the kill log once the player has said yes, and only then. True when it was cleared. */
	static boolean forgetKillLog(LootLog log, BooleanSupplier confirmed)
	{
		if (!confirmed.getAsBoolean())
		{
			return false;
		}
		log.clear();
		return true;
	}

	/**
	 * Enough of a suffix to check against the map's card, not enough to paste. With the group code
	 * it admits a character to a group, and this window is the one people screenshot for help.
	 */
	static String masked(String suffix)
	{
		return suffix.length() <= 4 ? "••••" : suffix.substring(0, 2) + "••••" + suffix.substring(suffix.length() - 2);
	}

	private String pairingCode()
	{
		return Pairing.codeIn(config);
	}
}
