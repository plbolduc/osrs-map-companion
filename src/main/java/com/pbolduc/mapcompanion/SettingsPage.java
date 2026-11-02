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
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import static com.pbolduc.mapcompanion.MapCompanionPanel.column;
import static com.pbolduc.mapcompanion.MapCompanionPanel.fill;
import static com.pbolduc.mapcompanion.MapCompanionPanel.gap;
import static com.pbolduc.mapcompanion.MapCompanionPanel.hug;
import static com.pbolduc.mapcompanion.MapCompanionPanel.left;
import static com.pbolduc.mapcompanion.MapCompanionPanel.linkButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.row;
import static com.pbolduc.mapcompanion.MapCompanionPanel.sectionHead;
import static com.pbolduc.mapcompanion.MapCompanionPanel.setWrapped;
import static com.pbolduc.mapcompanion.MapCompanionPanel.smallButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.syncRow;

/**
 * Settings, the page behind the gear: What's new, every sharing choice ({@link SharingControls}),
 * then Advanced with the map address, Show what's sent, Leave group and Forget kill log.
 *
 * <p>A class of its own because it is a page of its own ({@link SidebarPages}), set once and seldom
 * read again, and nothing on it is part of a session. What Leave group and a new map address did is
 * said on the daily page they turn back to, under the join field, through {@link ConnectCard}; an
 * address Apply refuses is said under Apply. {@link MapCompanionPanel} hands it to the pages and
 * calls {@link #refresh(boolean)} last in its own.
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
	private final ConfigManager configManager;
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
	private final Supplier<String> mapUrl;

	private final JButton previewButton = smallButton("Show what's sent");
	private final SharingControls sharingControls;
	private final JTextField mapAddress = new JTextField();
	/** Under Apply: why the address typed was not taken. It only ever says why Apply refused an address. */
	private final JLabel addressSaid = MapCompanionPanel.label(11f, MapCompanionPanel.REFUSED);
	private String displayedAddress;

	/*
	 * The reset actions live in Advanced: neither is a thing a player does every session.
	 */
	private final JPanel foot = row(ColorScheme.DARK_GRAY_COLOR);
	private List<JButton> shownFoot = new ArrayList<>();
	private final JButton leaveButton = smallButton("Leave group");
	/** Only ever present once there is a log to forget. A log you cannot clear is one you cannot trust. */
	private final JButton forgetLootButton = smallButton("Forget kill log");

	SettingsPage(MapCompanionConfig config, DestinationState destinationState, LootLog lootLog,
		SnapshotBuilder snapshotBuilder, ClientThread clientThread, ConfigManager configManager, MemberIdentity identity,
		Pairing pairing, Gson gson, FarmRunState farmRunState, FarmRunClient farmRunClient, ConnectCard connectCard,
		Runnable refresh, Consumer<Boolean> settingsOpen, Supplier<String> mapUrl)
	{
		this.config = config;
		this.destinationState = destinationState;
		this.lootLog = lootLog;
		this.snapshotBuilder = snapshotBuilder;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.identity = identity;
		this.pairing = pairing;
		this.gson = gson;
		this.farmRunState = farmRunState;
		this.farmRunClient = farmRunClient;
		this.connectCard = connectCard;
		this.refresh = refresh;
		this.settingsOpen = settingsOpen;
		this.mapUrl = mapUrl;
		this.sharingControls = new SharingControls(config, ConfigStore.of(configManager));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		left(this);

		/*
		 * Where an update's changes are written down, the same page the once-per-version chat line
		 * names: RuneLite shows no changelog, and the README carries only the latest few lines.
		 */
		JButton whatsNew = linkButton("What's new in " + CompanionVersion.VERSION + " ›");
		whatsNew.setName("settings.whatsNew");
		whatsNew.setForeground(ColorScheme.BRAND_ORANGE);
		whatsNew.setToolTipText("osrs-map.com's What's new page, where every update is written down");
		whatsNew.addActionListener(e -> LinkBrowser.browse(mapUrl.get() + "/whats-new"));
		add(whatsNew);
		add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		add(sectionHead("What you share"));
		add(sharingControls);
		add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		add(buildAdvanced());

		previewButton.setToolTipText("Print the exact snapshot this plugin would send right now");
		previewButton.addActionListener(e -> showSnapshotPreview());
		leaveButton.addActionListener(e -> leaveGroup());
		forgetLootButton.addActionListener(e ->
		{
			forgetKillLog(lootLog, this::confirmForgetKillLog);
			refresh.run();
		});
	}

	/** The actual address is editable here; there is no second default or explanatory footnote. */
	private JPanel buildAdvanced()
	{
		JPanel contents = column(ColorScheme.DARK_GRAY_COLOR);
		contents.add(sectionHead("Map address"));
		mapAddress.setText(config.relayUrl());
		mapAddress.addActionListener(e -> applyMapAddress(true));
		contents.add(fill(mapAddress, ColorScheme.DARK_GRAY_COLOR));
		JButton apply = smallButton("Apply");
		apply.addActionListener(e -> applyMapAddress(true));
		contents.add(hug(apply, ColorScheme.DARK_GRAY_COLOR));
		addressSaid.setVisible(false);
		contents.add(addressSaid);
		contents.add(gap(8, ColorScheme.DARK_GRAY_COLOR));
		contents.add(hug(previewButton, ColorScheme.DARK_GRAY_COLOR));
		contents.add(gap(8, ColorScheme.DARK_GRAY_COLOR));
		contents.add(left(foot));
		return SharingControls.fold("Advanced", contents);
	}

	/** The address typed here, applied before Connect or Join asks, which answer on the page they are on. */
	boolean applyMapAddress()
	{
		return applyMapAddress(false);
	}

	/**
	 * Apply the address typed, with a scheme left off put back ({@link Pairing#withScheme}), so what was
	 * meant is written into the field rather than refused. A refusal is said where the press was:
	 * under Apply for Apply and Enter, which leave Settings open, and under the join field for Connect
	 * and Join, which are on the daily page.
	 */
	private boolean applyMapAddress(boolean here)
	{
		String server = Pairing.normalizeServer(Pairing.withScheme(mapAddress.getText()));
		if (server == null)
		{
			String why = "Enter an https:// map address without a login, query or fragment.";
			if (here)
			{
				setWrapped(addressSaid, why);
				addressSaid.setVisible(true);
			}
			else
			{
				connectCard.refuse(why);
			}
			return false;
		}
		addressSaid.setVisible(false);
		mapAddress.setText(server);
		if (!server.equals(pairing.serverUrl()))
		{
			configManager.setConfiguration(MapCompanionConfig.GROUP, MapCompanionConfig.KEY_RELAY_URL, server);
			connectCard.joinOpen = true;
			connectCard.say("Map address changed. Press Connect.");
			/* The field and the line that asks for it are on the daily page. */
			settingsOpen.accept(false);
			refresh.run();
		}
		return true;
	}

	/** The sharing choices, the map address as saved, and the reset actions there is something for. EDT. */
	void refresh(boolean paired)
	{
		sharingControls.refresh();
		String configuredAddress = config.relayUrl();
		if (!java.util.Objects.equals(displayedAddress, configuredAddress))
		{
			displayedAddress = configuredAddress;
			mapAddress.setText(configuredAddress == null ? MapCompanionConfig.DEFAULT_MAP_URL : configuredAddress);
		}

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
			return "Nothing goes out while you're logged out. Log in to see what would.";
		}
		StringBuilder text = new StringBuilder();
		if (!grouped)
		{
			text.append("You're not in a group, so nothing goes out. Connect would start sending this to ")
				.append(server).append(":\n\n");
		}
		else if (!approved)
		{
			text.append("You're not connected to ").append(server)
				.append(", so nothing goes out. Connect would start sending this:\n\n");
		}
		else
		{
			text.append("This goes to ").append(server)
				.append(" every couple of seconds while you're logged in. Your group sees it.\n\n");
		}
		text.append(memberKey
			? "Two keys go with it and are never shown here: your member key (X-Companion-Member-Secret), which "
			+ "proves you're this member of the group, and this RuneLite's own key (X-Companion-Installation), which "
			+ "lets your other characters join the same group. Leave group deletes the first and replaces the second."
			: "A key goes with it and is never shown here: this RuneLite's own key (X-Companion-Installation), which "
			+ "lets your other characters join the same group." + (grouped ? " Leave group replaces it." : ""));
		text.append("\n\n");
		/* It stops being sent the moment the map says it has connected this RuneLite to a browser. */
		if (runeLiteCode != null)
		{
			text.append("Your RuneLite code goes with it too (X-Companion-Suffix), ").append(runeLiteCode)
				.append(", until the map says it has connected your dot to your browser; then it is deleted.\n\n");
		}
		text.append(json);
		String nick = nick(json, gson);
		if (SnapshotBuilder.NO_NICKNAME.equals(nick))
		{
			text.append("\n\n\"Me\" means you set no name of your own: your group sees ")
				.append(nameShared ? "your character name." : "Member.");
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
		return "Your group stops seeing you, and you stop seeing them. Coming back needs a new invite, "
			+ "and you'll join as a new member."
			+ (others == 1 ? " Your other group will need a new invite too, so it leaves your list."
			: others > 1 ? " Your " + others + " other groups will need a new invite too, so they leave your list."
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
			"Forget every kill and drop counted for this character?\n\n"
			+ "Drop luck starts again from zero, and your pinned drop goes with it.\n"
			+ "This cannot be undone.", "Forget kill log", "Forget kill log");
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
		return config.pairingCode() == null ? "" : config.pairingCode().trim();
	}
}
