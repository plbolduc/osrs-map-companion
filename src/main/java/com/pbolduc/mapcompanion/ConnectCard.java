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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.BoxLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import static com.pbolduc.mapcompanion.MapCompanionPanel.card;
import static com.pbolduc.mapcompanion.MapCompanionPanel.CARD_TEXT_WIDTH;
import static com.pbolduc.mapcompanion.MapCompanionPanel.column;
import static com.pbolduc.mapcompanion.MapCompanionPanel.fill;
import static com.pbolduc.mapcompanion.MapCompanionPanel.gap;
import static com.pbolduc.mapcompanion.MapCompanionPanel.left;
import static com.pbolduc.mapcompanion.MapCompanionPanel.linkButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.row;
import static com.pbolduc.mapcompanion.MapCompanionPanel.sectionHead;
import static com.pbolduc.mapcompanion.MapCompanionPanel.setWrapped;
import static com.pbolduc.mapcompanion.MapCompanionPanel.smallButton;
import static com.pbolduc.mapcompanion.MapCompanionPanel.syncRow;

/**
 * The connection, at the top of the daily page: the status card with its dot and the gear, the one
 * orange button, the field that takes an invite or a code with Paste and Join under it, and the
 * remembered groups. Also the group's own buttons, Copy invite and Switch ▾, which sit under the
 * Group tab, and every dialog and message those presses open.
 *
 * <p>A class of its own because all of it is one conversation with the map — Connect, Join, Open the
 * map, Rename, an invite — and the state that conversation keeps, whether a press is waiting, why
 * the last Connect made no group, whether the field is open, belongs to nothing else in the sidebar.
 * {@link MapCompanionPanel} places it and calls its two refreshes: the status card's first, and its
 * buttons' after the Group tab's own refresh, which decides where Copy invite goes.
 *
 * <p><b>The plugin is enough on its own.</b> Before a group the orange button is <i>Connect</i>, which
 * makes one for the player ({@link ConnectClient}), and the field under it takes a friend's invite, a
 * RuneLite code from the map or an old code. Once connected the button is <i>Open the map</i>, which
 * opens the website already in the group as this player. The field folds away until switching or
 * reconnecting needs it.
 *
 * <p><b>Reading the clipboard is a button, never a background job.</b> Nothing here looks at the
 * clipboard unless somebody pressed <i>Paste</i>, which is the same standard the invite button has
 * always held to in the other direction — copying, not typing into the game.
 */
@Slf4j
final class ConnectCard extends JPanel
{
	private final MapCompanionConfig config;
	private final MapCompanionPlugin plugin;
	private final MemberIdentity identity;
	private final Pairing pairing;
	private final RelayClient relay;
	private final DirectiveStream directiveStream;
	/** The step after a first Connect, which Connect and Join offer. See {@link RecommendedSharing}. */
	private final RecommendedSharing recommendedSharing;
	/** The whole sidebar, which this card's dialogs are centred on rather than the card itself. */
	private final Component sidebar;
	/** The sidebar's refresh, run after a press has changed what it should say. */
	private final Runnable refresh;
	/** Settings in place of the daily page, or the daily page back. See {@link SidebarPages}. */
	private final Consumer<Boolean> settingsOpen;
	/** The address typed in Settings, applied before Connect or Join asks; false when it is not one. */
	private final BooleanSupplier applyMapAddress;
	private final Supplier<String> mapUrl;

	/* The status card: connection, group and a short summary; the snapshot preview is in Advanced. */
	private final JLabel dot = new JLabel("●");
	private final JLabel statusLabel = new JLabel();
	/**
	 * The first line's own colour, for every line that is not a refusal. While the map refuses this
	 * member it is {@link MapCompanionPanel#REFUSED}, the pink the sidebar gives what cannot be done.
	 */
	private final Color statusInk = statusLabel.getForeground();
	/** What the map holds to refusing, from the status card's refresh for the buttons' just after it. */
	@Nullable
	private RelayClient.Refused refused;
	private final JLabel groupLabel = new JLabel();
	/**
	 * Rename group…, in the status card, for whoever runs a group still called what Connect named it.
	 * Renaming lives in the menu under Switch ▾; a new group is when a name is most wanted, so the card
	 * says so beside the name, and the link goes once the group has one. A link, as Meet here… is.
	 */
	private final JButton nameLink = linkButton(RENAME);
	private final JLabel sharingLabel = MapCompanionPanel.label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	/** Settings, as a page in place of this one. See {@link SidebarPages}. */
	private final JButton gearButton = new JButton(SidebarIcons.gear());

	/**
	 * The one filled button. See the constructor for why it is filled and nothing else is.
	 *
	 * <p><i>Connect</i> until the plugin is in a group, and <i>Open the map</i> after: the one thing
	 * to press is a different thing before and after, and two orange buttons would answer "what do I
	 * press" with two answers.
	 */
	private final JButton openMapButton = new JButton(OPEN_MAP);
	static final String CONNECT = "Connect";
	static final String OPEN_MAP = "Open the map";
	/** Asks for groups and links; see {@link ConnectClient}. */
	private final ConnectClient connectClient;
	/** True while Connect, Join or Open the map waits on the map, so a second press sends nothing. */
	private boolean asking = false;
	/**
	 * Why the last Connect made no group, until the next Connect or Join. The status card's first
	 * line says it: printed under the invite field, it read as being about the invite.
	 */
	@Nullable
	private String connectFailed;

	/**
	 * The foot of the Group tab: Copy invite and Switch ▾, the line that answers them, and the join
	 * field while the menu's Join another group… has it open. Each press is answered under itself,
	 * where the player is looking, which a line above the tabs is not once the tab has scrolled.
	 */
	final JPanel groupActions = column(ColorScheme.DARK_GRAY_COLOR);
	private final JPanel groupButtons = row(ColorScheme.DARK_GRAY_COLOR);
	private List<JButton> shownGroupActions = new ArrayList<>();
	/** What Copy invite and the menu's Rename group… said, under the row they are pressed in. */
	private final JLabel groupMessage = new JLabel();
	/** What the status card's own Rename group… said, in the card. */
	private final JLabel cardMessage = new JLabel();
	/** The group the two lines above were said about: a line about one group is not news in the next. */
	private String saidFor = "";
	/**
	 * When the line under the field, the one under the group's buttons and the card's own last said
	 * a confirmation, or zero while each says anything else: see {@link #letGo}.
	 */
	private long joinConfirmedAtMs;
	private long groupConfirmedAtMs;
	private long cardConfirmedAtMs;
	/**
	 * Copy invite says Copied for a moment when the clipboard has the invite, on the button itself,
	 * which in a group of one sits in the card at the top of the tab, far from the line under the row.
	 */
	private final Timer copiedFlash = new Timer(2000, e -> showCopied(false));
	static final String COPIED = "Copied";
	/**
	 * A size down, like every other button that is not <i>Open the map</i>. At the default size
	 * these two were as large as the one filled button, so its colour was the only thing marking
	 * which of the three was the one to press.
	 */
	final JButton copyInviteButton = smallButton("Copy invite");
	/**
	 * The remembered groups, as a menu rather than a column of buttons. They were one full-width
	 * button each under their own heading, which on a player with three groups was four rows to
	 * answer a question asked once a week. The list is read when the menu opens, so it cannot be
	 * stale, and the group being posted to is in it, marked and disabled — the answer to "which
	 * one am I in". Joining another group is the menu's last row rather than a fold of its own
	 * under the buttons: it is the same question, asked less often still.
	 */
	private final JButton switchButton = smallButton("Switch ▾");
	/**
	 * The same menu before a group is joined, under the code field: the tabs are not drawn then,
	 * and a player who left one group may still be remembered in others.
	 */
	private final JButton knownGroupsButton = smallButton("Your groups ▾");
	private final JPanel knownGroupsRow;
	boolean joinOpen = false;
	private final JPanel joinBlock;
	/**
	 * The field and its line, as one piece that sits in one of two places: under the orange button
	 * before a group, after a new map address and while the map refuses the group, where nothing else
	 * offers a way in; and in the Group tab under Copy invite and Switch ▾ while the menu that opened
	 * it is what the player pressed.
	 */
	private final JPanel field = column(ColorScheme.DARK_GRAY_COLOR);
	private final JPanel fieldAbove = column(ColorScheme.DARK_GRAY_COLOR);
	private final JPanel fieldBelow = column(ColorScheme.DARK_GRAY_COLOR);

	/**
	 * The join field, and what it says back.
	 *
	 * <p>One line of text and two buttons, in the order a player uses them. The message label is
	 * hidden until there is something to say, because an empty label still takes a line — the same
	 * lesson the destination card carries a comment about.
	 */
	private final JTextField joinField = new JTextField();
	static final String JOIN_PLACEHOLDER = "Paste an invite or code";
	private final JButton pasteButton = smallButton("Paste");
	private final JButton joinButton = smallButton("Join");
	private final JLabel joinMessage = new JLabel();
	/**
	 * Over the field, before a group: the orange button makes one, and this is the other way in. And
	 * over it while the map refuses the group, when the field is the way back in.
	 */
	private final JLabel joinHeading = sectionHead("Have an invite?");
	/** Under Paste and Join while the map refuses the group: what to put in the field. See {@link #refusedNextStep}. */
	private final JLabel nextStep = MapCompanionPanel.label(11f, ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel refusedGap = gap(8, ColorScheme.DARK_GRAY_COLOR);

	/**
	 * True while the map is being asked for an invite link.
	 *
	 * <p>The once-a-second refresh would otherwise re-enable the button under the cursor while the
	 * request it started is still in flight, which is an invitation to send three of them.
	 */
	private boolean copyingInvite = false;

	ConnectCard(Component sidebar, MapCompanionConfig config, MapCompanionPlugin plugin, MemberIdentity identity,
		Pairing pairing, RelayClient relay, DirectiveStream directiveStream, ConnectClient connectClient,
		RecommendedSharing recommendedSharing, Runnable refresh, Consumer<Boolean> settingsOpen,
		BooleanSupplier applyMapAddress, Supplier<String> mapUrl)
	{
		this.sidebar = sidebar;
		this.config = config;
		this.connectClient = connectClient;
		this.plugin = plugin;
		this.identity = identity;
		this.pairing = pairing;
		this.relay = relay;
		this.directiveStream = directiveStream;
		this.recommendedSharing = recommendedSharing;
		this.refresh = refresh;
		this.settingsOpen = settingsOpen;
		this.applyMapAddress = applyMapAddress;
		this.mapUrl = mapUrl;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		left(this);

		/* The status card. Dot beside the sentence, so the state reads at a glance and again in words. */
		JPanel status = card();
		JPanel statusRow = new JPanel(new BorderLayout(6, 0));
		statusRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		dot.setFont(dot.getFont().deriveFont(12f));
		statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
		/*
		 * The dot, the words and the gear keep to the first line, so a sentence that wraps does not
		 * draw the other two beside its middle, where they read as belonging to the group's name. The
		 * gear is the tallest of the three, so the dot and the first line drop to where Swing centres
		 * a line of their height beside it, and a line on its own sits as centred as a label would.
		 */
		int line = statusLabel.getFontMetrics(statusLabel.getFont()).getHeight();
		int drop = Math.max(0, SidebarIcons.SIZE / 2 - line / 2);
		dot.setVerticalAlignment(SwingConstants.TOP);
		dot.setBorder(new EmptyBorder(drop, 0, 0, 0));
		statusRow.add(dot, BorderLayout.WEST);
		statusLabel.setVerticalAlignment(SwingConstants.TOP);
		statusLabel.setBorder(new EmptyBorder(drop, 0, 0, 0));
		statusRow.add(statusLabel, BorderLayout.CENTER);
		gearButton.setName("companion.settings");
		gearButton.setToolTipText("Settings: what you share, the map address and Show what's sent");
		gearButton.getAccessibleContext().setAccessibleName("Settings");
		gearButton.setBorderPainted(false);
		gearButton.setContentAreaFilled(false);
		gearButton.setFocusPainted(false);
		gearButton.setBorder(new EmptyBorder(0, 4, 0, 0));
		gearButton.setVerticalAlignment(SwingConstants.TOP);
		gearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		statusRow.add(gearButton, BorderLayout.EAST);
		status.add(left(statusRow));
		groupLabel.setFont(groupLabel.getFont().deriveFont(12f));
		status.add(left(groupLabel));
		status.add(sharingLabel);
		nameLink.setName("companion.rename");
		nameLink.setForeground(ColorScheme.BRAND_ORANGE);
		nameLink.setToolTipText("Change the name everybody in the group sees");
		nameLink.addActionListener(e -> renameGroup(true));
		nameLink.setVisible(false);
		status.add(nameLink);
		cardMessage.setName("companion.cardMessage");
		cardMessage.setFont(cardMessage.getFont().deriveFont(11f));
		cardMessage.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cardMessage.setVisible(false);
		status.add(left(cardMessage));
		add(status);
		add(gap(6, ColorScheme.DARK_GRAY_COLOR));

		/*
		 * The one button that matters, in the brand's colour, and the only filled one. It was the
		 * first of seven identical grey buttons, and a panel where everything is a button of the same
		 * weight is a panel with no answer to "what do I press".
		 */
		openMapButton.setBackground(ColorScheme.BRAND_ORANGE);
		openMapButton.setForeground(Color.WHITE);
		add(fill(openMapButton, ColorScheme.DARK_GRAY_COLOR));
		add(gap(10, ColorScheme.DARK_GRAY_COLOR));

		/* The connection is reachable from every tab, including after changing the server. */
		joinBlock = buildJoinBlock();
		field.add(joinBlock);
		field.add(left(joinMessage));
		fieldAbove.add(field);
		add(fieldAbove);
		/* Between the field and the tabs, which follow it only while the map refuses the group. */
		refusedGap.setVisible(false);
		add(refusedGap);
		knownGroupsRow = row(ColorScheme.DARK_GRAY_COLOR);
		knownGroupsRow.add(knownGroupsButton);
		knownGroupsRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		knownGroupsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, knownGroupsButton.getPreferredSize().height + 4));
		add(left(knownGroupsRow));

		groupActions.add(left(groupButtons));
		groupMessage.setName("group.message");
		groupMessage.setFont(groupMessage.getFont().deriveFont(11f));
		groupMessage.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		groupMessage.setVisible(false);
		groupActions.add(left(groupMessage));
		fieldBelow.setBorder(new EmptyBorder(6, 0, 0, 0));
		fieldBelow.setVisible(false);
		groupActions.add(fieldBelow);
		copiedFlash.setRepeats(false);

		openMapButton.setName("companion.orange");
		openMapButton.addActionListener(e -> pressOrange());
		pasteButton.setToolTipText("Read the clipboard. Only ever when you press this.");
		pasteButton.addActionListener(e -> pasteIntoJoinField());
		joinButton.addActionListener(e -> joinFromField());
		copyInviteButton.addActionListener(e -> copyInvite());
		switchButton.addActionListener(e -> showGroupMenu(switchButton));
		knownGroupsButton.addActionListener(e -> showGroupMenu(knownGroupsButton));
		gearButton.addActionListener(e -> settingsOpen.accept(true));
	}

	/**
	 * A full-width field, then Paste and Join, in the order the reader uses them.
	 *
	 * <p>One small heading, and only before a group: the orange button above it now makes a group,
	 * so the field is the <i>other</i> way in, and "Have an invite?" is what says which of the two a
	 * friend's message is for. The placeholder says what goes in the field and the tooltip where it
	 * comes from.
	 */
	private JPanel buildJoinBlock()
	{
		JPanel block = column(ColorScheme.DARK_GRAY_COLOR);
		block.add(joinHeading);
		block.add(gap(3, ColorScheme.DARK_GRAY_COLOR));
		joinField.setName("join.field");
		joinField.setToolTipText("Paste the invite a friend sent you, or the code from osrs-map.com's Companion panel");
		joinButton.setName("join.button");
		joinButton.setToolTipText("Join the group this invite or code is for");
		joinField.putClientProperty("JTextField.placeholderText", JOIN_PLACEHOLDER);
		joinField.addActionListener(e -> joinFromField());
		/* Connect joins an invite in the field, so its tooltip follows a paste or a keystroke at once. */
		joinField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh.run();
			}
		});
		block.add(fill(joinField, ColorScheme.DARK_GRAY_COLOR));
		block.add(gap(4, ColorScheme.DARK_GRAY_COLOR));
		JPanel buttons = row(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(pasteButton);
		buttons.add(joinButton);
		block.add(left(buttons));
		nextStep.setVisible(false);
		block.add(nextStep);
		joinMessage.setFont(joinMessage.getFont().deriveFont(11f));
		joinMessage.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		joinMessage.setVisible(false);
		return left(block);
	}

	/**
	 * The one question before anything leaves for a map, asked only of a map that is not osrs-map.com.
	 *
	 * <p>The warning RuneLite requires is shown at install, from the Hub's manifest: what this plugin
	 * sends, and that the server is a third party's. Pressing Connect or Join on the map the plugin
	 * was installed for is the choice that warning was about, so nothing more is asked. A different
	 * map address is a different server, and no install warning could have named it: a player told
	 * to paste some address into Settings would be sending their position somewhere they never saw
	 * named. So that map is asked about by name, once — {@link Pairing} remembers the answer per map
	 * — and cancelling sends nothing.
	 */
	private boolean confirmMap()
	{
		String server = pairing.serverUrl();
		if (server == null)
		{
			return false;
		}
		if (isDefaultMap(server))
		{
			return true;
		}
		return JOptionPane.showOptionDialog(sidebar, otherMapQuestion(pairing.serverLabel()),
			"Connect to another map", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
			null, new Object[]{"Connect", "Cancel"}, "Cancel") == JOptionPane.OK_OPTION;
	}

	/**
	 * The question {@link #confirmMap} asks, a paragraph to a line. RuneLite's look and feel wraps a
	 * dialog's line at 80 characters itself, so a break written in here as well would leave two words
	 * of a sentence on a line of their own.
	 */
	static String otherMapQuestion(String server)
	{
		return "Send your data to " + server + " instead of osrs-map.com?\n\n"
			+ "It is a map server RuneLite has not checked. It receives your IP address, and what you "
			+ "choose to share goes to it and to your group there.";
	}

	/** The map the plugin is installed for, whose install warning already said all of this. */
	static boolean isDefaultMap(@Nullable String server)
	{
		return server != null && server.equals(Pairing.normalizeServer(MapCompanionConfig.DEFAULT_MAP_URL));
	}

	/**
	 * The orange button: Connect before a group, Open the map after.
	 *
	 * <p>Connect with a code already set — a changed map address, say — connects to that code again
	 * rather than making another group: "Connect" always means "connect me", and only a player with
	 * no group at all is given a new one. See {@link #orangePress} for which.
	 */
	private void pressOrange()
	{
		switch (orangePress(pairingCode(), pairing.hasServerConsent(), joinField.getText()))
		{
			case OPEN_MAP:
				openMap();
				break;
			case JOIN:
				joinFromField();
				break;
			default:
				startGroup();
				break;
		}
	}

	/** What a press of the orange button does. */
	enum OrangePress
	{
		/** {@link #openMap}: the website, already in the group. */
		OPEN_MAP,
		/** {@link #joinFromField}: the field's invite or code, else the code already set. */
		JOIN,
		/** {@link #startGroup}: a new group, made by the map. */
		START
	}

	/**
	 * Which press the orange button makes, from the code set, the map's approval and the field.
	 *
	 * <p>With no group, a friend's invite in the field is joined rather than left behind. Pasting it
	 * and pressing the loudest button on the page is the likeliest thing an invited friend does, and
	 * making a group then would put them alone in a new one and empty the field the invite was in.
	 * The button keeps the word Connect, which says what it does for every other player.
	 */
	static OrangePress orangePress(String code, boolean consent, @Nullable String field)
	{
		if (!code.isEmpty())
		{
			return consent ? OrangePress.OPEN_MAP : OrangePress.JOIN;
		}
		return joinsInstead(field) != null ? OrangePress.JOIN : OrangePress.START;
	}

	/**
	 * What Connect joins rather than making a group beside: an invite, or a RuneLite code with its
	 * suffix, or null.
	 *
	 * <p>Never a code alone, though Join takes one. Its alphabet admits ordinary words — see
	 * {@link JoinCode} — so a word left in the field would send this press to whatever group it
	 * spells instead of making the player's own; Join is the press that says a code is meant.
	 */
	@Nullable
	static JoinCode joinsInstead(@Nullable String field)
	{
		JoinCode found = JoinCode.parse(field);
		return found != null && (found.invite != null || found.suffix != null) ? found : null;
	}

	/**
	 * Connect with no group: one press, and the map makes the group.
	 *
	 * <p>Nothing is asked. The group is called what the map calls a group nobody has named, and its
	 * maker can rename it from the groups menu; a player who plays alone never has to think of a
	 * name at all. What to share comes next, as the step it has always been.
	 */
	private void startGroup()
	{
		if (asking || !applyMapAddress.getAsBoolean())
		{
			return;
		}
		connectFailed = null;
		String server = pairing.serverUrl();
		if (!pairing.approvedMap(server) && !confirmMap())
		{
			/* Connect's answer, on the card beside it as its refusals are, rather than under the field. */
			refused("Connection cancelled.", true);
			refresh.run();
			return;
		}
		ask();
		connectClient.start(server, identity.installationKey(),
			outcome -> SwingUtilities.invokeLater(() -> finishConnect(server, outcome, true)));
	}

	/** Join with a friend's invite: the one question for another map, then the map spends the invite. */
	private void joinWithInvite(JoinCode invited)
	{
		if (asking)
		{
			return;
		}
		String server = pairing.serverUrl();
		if (!pairing.approvedMap(server) && !confirmMap())
		{
			refuse("Connection cancelled.");
			return;
		}
		ask();
		connectClient.join(server, identity.installationKey(), invited.code, invited.invite,
			outcome -> SwingUtilities.invokeLater(() -> finishConnect(server, outcome, false)));
	}

	/**
	 * While the map is asked: the button says so, the status card says Connecting…, and the line
	 * under the field is quiet rather than saying it a second time.
	 */
	private void ask()
	{
		asking = true;
		quiet();
		refresh.run();
	}

	/**
	 * The map's answer to Connect or Join, on Swing's thread: keep the code as if it had been pasted,
	 * or say why not.
	 *
	 * <p>The map was approved before anything was sent, so the code is connected with an approval
	 * that answers yes — which is also what lets {@link RecommendedSharing} offer the sharing step to a
	 * player meeting this map for the first time. An answer that arrives after the map address has
	 * changed is for a map the player has left, and is dropped.
	 */
	private void finishConnect(String server, ConnectClient.Outcome outcome, boolean making)
	{
		asking = false;
		if (!server.equals(pairing.serverUrl()))
		{
			quiet();
			refresh.run();
			return;
		}
		if (outcome.joined == null)
		{
			refused(outcome.message != null ? outcome.message : ConnectClient.FAILED, making);
			refresh.run();
			return;
		}
		if (!RecommendedSharing.connect(pairing, outcome.joined, () -> true, config,
			() -> recommendedSharing.offer(pairing.serverLabel())))
		{
			refused(ConnectClient.FAILED, making);
			refresh.run();
			return;
		}
		pairing.rememberName(outcome.joined.code, outcome.name);
		/* The map has just taken this installation in, which answers whatever it was refusing. */
		relay.clearRefusal();
		joinField.setText("");
		joinOpen = false;
		quiet();
		settingsOpen.accept(false);
		refresh.run();
	}

	/**
	 * Why a press made no group or joined none. Connect's goes on the status card, until the next
	 * press, because it is about the connection; under the invite field it read as being about the
	 * invite. Join's stays under the field, because an expired or used-up invite is news about it.
	 */
	private void refused(String message, boolean making)
	{
		if (making)
		{
			connectFailed = message;
			quiet();
		}
		else
		{
			refuse(message);
		}
	}

	/**
	 * Open the map: the website, already in the group as this player.
	 *
	 * <p>The map is asked for a one-time link ({@link ConnectClient#link}) and the browser opens it;
	 * anything short of a link — no secret yet, a legacy group, the map unreachable — opens the
	 * plain map, which is what this button always did. On the login screen no character is logged in
	 * to ask as, so it asks as one this RuneLite has already been in the group as
	 * ({@link Pairing#memberWithSecret}): the link puts the player in the group, whichever account.
	 */
	private void openMap()
	{
		String server = pairing.serverUrl();
		String code = pairingCode();
		String member = pairing.memberWithSecret(server, code, identity.id());
		String secret = member == null ? null : pairing.secret(server, code, member);
		if (asking || server == null || member == null || secret == null)
		{
			LinkBrowser.browse(mapUrl.get() + "/map?task=companion");
			return;
		}
		asking = true;
		refresh.run();
		connectClient.link(server, code, member, secret, url -> SwingUtilities.invokeLater(() ->
		{
			asking = false;
			LinkBrowser.browse(url != null ? url : mapUrl.get() + "/map?task=companion");
			refresh.run();
		}));
	}

	/**
	 * Read the clipboard, once, because somebody pressed the button.
	 *
	 * <p>It fills the field rather than joining, so an accidental press cannot move somebody out of
	 * the group they are in — and it says what it found, because a paste that silently did nothing
	 * is indistinguishable from a paste that worked.
	 */
	private void pasteIntoJoinField()
	{
		String pasted = null;
		try
		{
			Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor))
			{
				pasted = (String) contents.getTransferData(DataFlavor.stringFlavor);
			}
		}
		catch (IOException | UnsupportedFlavorException | RuntimeException e)
		{
			/** A clipboard held by another application, or holding a file. Not worth a stack trace. */
			log.debug("could not read the clipboard", e);
		}

		if (pasted == null || pasted.trim().isEmpty())
		{
			refuse("There is nothing to paste. Copy the invite first, then press Paste.");
			return;
		}
		joinField.setText(pasted.trim());
		/* The start of the message, which says what it is, rather than the tail of a link's token. */
		joinField.setCaretPosition(0);
		JoinCode found = JoinCode.parse(pasted);
		if (found == null)
		{
			refuse("No invite or code found. Ask a friend in the group for an invite.");
			return;
		}
		say(found.invite != null ? "Found an invite to " + JoinCode.forDisplay(found.code) + ". Press Join."
			: "Found " + JoinCode.forDisplay(found.code) + ". Press Join.");
	}

	/**
	 * Join whatever is in the field.
	 *
	 * <p>Everything that makes this work is in {@link JoinCode}; this is the half that has to be
	 * honest when it fails. One sentence, naming both things that are accepted, because "invalid
	 * input" tells somebody holding a Discord message nothing at all. An invite goes to the map to be
	 * spent ({@link #joinWithInvite}); a RuneLite code or an old code is kept as it is.
	 */
	private void joinFromField()
	{
		if (!applyMapAddress.getAsBoolean())
		{
			return;
		}
		connectFailed = null;
		String entered = joinField.getText().trim();
		JoinCode joined = JoinCode.parse(entered.isEmpty() ? pairingCode() : entered);
		if (joined == null)
		{
			refuse(pairingCode().isEmpty()
				? "Paste the invite a friend sent you, or press Connect to start your own group."
				: "Paste an invite, or the code from osrs-map.com's Companion panel.");
			return;
		}
		if (joined.invite != null)
		{
			joinWithInvite(joined);
			return;
		}
		if (!connect(joined))
		{
			refuse("Connection cancelled.");
			return;
		}
		joinField.setText("");
		joinOpen = false;
		quiet();
		refresh.run();
	}

	/** Connect, then offer the recommended set if this was a first Connect to the map. */
	private boolean connect(JoinCode joined)
	{
		return RecommendedSharing.connect(pairing, joined, this::confirmMap, config,
			() -> recommendedSharing.offer(pairing.serverLabel()));
	}

	/** One line under the join field, hidden when there is nothing to say: here, a step on the way. */
	void say(String message)
	{
		joinConfirmedAtMs = 0L;
		setWrapped(joinMessage, message);
		MapCompanionPanel.tone(joinMessage, false);
		joinMessage.setVisible(true);
	}

	/** The same line, for a press there that did not happen: in the refusal pink until the next press. */
	void refuse(String message)
	{
		say(message);
		MapCompanionPanel.tone(joinMessage, true);
	}

	/** The same line, for a press that did what it asked: news for a moment, then gone. */
	void confirm(String message)
	{
		say(message);
		joinConfirmedAtMs = System.currentTimeMillis();
	}

	/**
	 * Nothing to say under the field. A Connect used to say "Connecting to … Waiting for the map."
	 * here while the status card said the same beside its dot; the card says it, once.
	 */
	private void quiet()
	{
		joinMessage.setVisible(false);
	}

	/** One line under Copy invite and Switch ▾, for what was pressed there: here, a press on its way. */
	void sayUnderGroup(String message)
	{
		saidFor = pairingCode();
		groupConfirmedAtMs = 0L;
		setWrapped(groupMessage, message);
		MapCompanionPanel.tone(groupMessage, false);
		groupMessage.setVisible(true);
	}

	/** The same line, for a press that did not happen. */
	void refuseUnderGroup(String message)
	{
		sayUnderGroup(message);
		MapCompanionPanel.tone(groupMessage, true);
	}

	/** The same line, for a press that did what it asked: news for a moment, then gone. */
	void confirmUnderGroup(String message)
	{
		sayUnderGroup(message);
		groupConfirmedAtMs = System.currentTimeMillis();
	}

	/** One line in the status card, under the Rename group… link that was pressed: here, a rename on its way. */
	private void sayOnCard(String message)
	{
		saidFor = pairingCode();
		cardConfirmedAtMs = 0L;
		setWrapped(cardMessage, message, CARD_TEXT_WIDTH);
		MapCompanionPanel.tone(cardMessage, false);
		cardMessage.setVisible(true);
	}

	/** The same line, for a rename that did not happen. */
	private void refuseOnCard(String message)
	{
		sayOnCard(message);
		MapCompanionPanel.tone(cardMessage, true);
	}

	/** The same line, for a rename that landed. */
	private void confirmOnCard(String message)
	{
		sayOnCard(message);
		cardConfirmedAtMs = System.currentTimeMillis();
	}

	/**
	 * A confirmation is news for the ten seconds a run's card gives one
	 * ({@link FarmRunClient.Said#CONFIRMATION_MS}) and then gives its line back, on the sidebar's
	 * one-second refresh. Anything else a press said stays until the next press says something else.
	 */
	private static void letGo(JLabel line, long confirmedAtMs, long now)
	{
		if (confirmedAtMs > 0L && now - confirmedAtMs >= FarmRunClient.Said.CONFIRMATION_MS)
		{
			line.setVisible(false);
		}
	}

	/**
	 * Copy invite's own words for a moment, while the clipboard has the invite, or its name back.
	 * Only its own Copied is put back: a press since has the button saying something else.
	 */
	private void showCopied(boolean copied)
	{
		if (copied)
		{
			copyInviteButton.setText(COPIED);
			copiedFlash.restart();
		}
		else if (COPIED.equals(copyInviteButton.getText()))
		{
			copyInviteButton.setText("Copy invite");
		}
	}

	/**
	 * The remembered groups, as a menu under the button that opened it.
	 *
	 * <p>Built at the press rather than kept up to date, because the list is only ever read here:
	 * the old column of buttons was rebuilt whenever the list or the active code changed so that a
	 * row would not move under the cursor, and a menu that exists only while it is open has no such
	 * problem. The group currently being posted to is in it, marked and disabled.
	 *
	 * <p>Each group by the name the map last sent for it, beside its code — see {@link #groupLabel}.
	 */
	private void showGroupMenu(JButton anchor)
	{
		String active = pairingCode();
		JPopupMenu menu = new JPopupMenu();
		for (String code : pairing.knownCodes())
		{
			boolean current = code.equalsIgnoreCase(active);
			String label = groupLabel(code, pairing.name(code));
			JMenuItem item = groupMenuItem((current ? "● " : "") + label);
			if (current)
			{
				item.setEnabled(false);
			}
			else
			{
				item.setToolTipText("Post to this group instead. One group at a time, like a party.");
				item.addActionListener(e ->
				{
					if (!connect(JoinCode.parse(code)))
					{
						return;
					}
					quiet();
					refresh.run();
				});
			}
			menu.add(item);
		}
		if (!active.isEmpty())
		{
			menu.addSeparator();
			JMenuItem another = groupMenuItem(joinMenuWords(joinOpen));
			another.setToolTipText("Paste another group's connection code or invite");
			another.addActionListener(e ->
			{
				joinOpen = !joinOpen;
				refresh.run();
			});
			menu.add(another);
			/* Only for whoever the map said may, so nobody is offered a press it would refuse. */
			if (plugin.runsGroup(pairing.serverUrl(), active))
			{
				JMenuItem rename = groupMenuItem(RENAME);
				rename.setToolTipText("Change the name everybody in the group sees");
				rename.addActionListener(e -> renameGroup(false));
				menu.add(rename);
			}
		}
		/*
		 * Hung from the button's right edge, so it opens into the sidebar. Names made it wider than
		 * the button, and hung from the left edge the way a column of codes could be, it ran off the
		 * sidebar's right side.
		 */
		menu.show(anchor, anchor.getWidth() - menu.getPreferredSize().width, anchor.getHeight());
	}

	static final String RENAME = "Rename group…";

	/**
	 * What the map calls a group Connect made until somebody names it: the map's
	 * {@code DEFAULT_RUNELITE_GROUP_NAME}. If the two ever differ, the status card's link simply
	 * stops showing; the menu still renames.
	 */
	static final String STARTING_NAME = "Our group";

	/** Whether the status card offers Rename group…: to whoever runs the group, while it has its starting name. */
	static boolean offersRename(boolean paired, boolean runs, @Nullable String name)
	{
		return paired && runs && STARTING_NAME.equals(name);
	}

	/**
	 * Rename the group: a name typed in a small dialog that starts on the current one, sent to the map,
	 * and kept here only once the map has taken it.
	 *
	 * <p>A group made by Connect is "Our group" until somebody names it, and this is where its maker
	 * does, without opening the map. The menu offers it only to a member the map said may, and asks
	 * as one of this RuneLite's members of the group even on the login screen.
	 *
	 * <p>The dialog's own button says Rename, and its field holds no more than the map takes
	 * ({@link #nameField}): a limit kept while typing has nothing to explain afterwards, where a
	 * refusal would close the dialog on what was typed and say why somewhere else. An empty name
	 * sends nothing, as Cancel does.
	 *
	 * <p>What it says is said where it was pressed: in the status card for the card's own link, and
	 * under Copy invite and Switch ▾ for the menu's row.
	 */
	void renameGroup(boolean fromCard)
	{
		Consumer<String> answer = fromCard ? this::sayOnCard : this::sayUnderGroup;
		Consumer<String> refused = fromCard ? this::refuseOnCard : this::refuseUnderGroup;
		Consumer<String> renamed = fromCard ? this::confirmOnCard : this::confirmUnderGroup;
		String server = pairing.serverUrl();
		String code = pairingCode();
		String member = pairing.memberWithSecret(server, code, identity.id());
		String secret = member == null ? null : pairing.secret(server, code, member);
		if (server == null || member == null || secret == null)
		{
			refused.accept("Log in once while connected, then rename the group.");
			return;
		}
		JTextField typed = nameField(pairing.name(code));
		Object[] choices = {"Rename", "Cancel"};
		JOptionPane question = new JOptionPane(new Object[]{RENAME_PROMPT, typed}, JOptionPane.PLAIN_MESSAGE,
			JOptionPane.OK_CANCEL_OPTION, null, choices, choices[0])
		{
			/* Into the field, as an input dialog's own is, rather than onto the button. Enter still renames. */
			@Override
			public void selectInitialValue()
			{
				typed.requestFocusInWindow();
			}
		};
		JDialog dialog = question.createDialog(sidebar, "Rename group");
		dialog.setVisible(true);
		dialog.dispose();
		if (!choices[0].equals(question.getValue()))
		{
			return;
		}
		String name = typed.getText().replaceAll("\\s+", " ").trim();
		if (name.isEmpty())
		{
			return;
		}
		answer.accept("Renaming…");
		connectClient.rename(server, code, member, secret, name, outcome -> SwingUtilities.invokeLater(() ->
		{
			if (outcome.name != null && server.equals(pairing.serverUrl()) && code.equalsIgnoreCase(pairingCode()))
			{
				pairing.rememberName(code, outcome.name);
				renamed.accept("Renamed to " + outcome.name + ".");
			}
			else
			{
				refused.accept(outcome.message != null ? outcome.message : ConnectClient.FAILED);
			}
			refresh.run();
		}));
	}

	/** What Rename asks, with the one rule a name has. */
	static final String RENAME_PROMPT = "The name everybody in the group sees (up to " + Pairing.MAX_GROUP_NAME
		+ " characters):";

	/**
	 * Rename's field: the current name, and never more characters than the map takes for one. A paste
	 * that would run past the limit keeps what fits, so the dialog has no refusal to give.
	 */
	static JTextField nameField(@Nullable String current)
	{
		JTextField field = new JTextField();
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
				throws BadLocationException
			{
				replace(bypass, offset, 0, text, attributes);
			}

			@Override
			public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
				throws BadLocationException
			{
				String added = text == null ? "" : text;
				int room = Pairing.MAX_GROUP_NAME - (bypass.getDocument().getLength() - length);
				super.replace(bypass, offset, length, added.substring(0, Math.max(0, Math.min(added.length(), room))),
					attributes);
			}
		});
		field.setText(current);
		return field;
	}

	/**
	 * The groups menu's join row: it opens the code field under Copy invite and Switch ▾, where the
	 * menu was opened, and while that is open it closes it again, as the fold it replaced did.
	 */
	static String joinMenuWords(boolean open)
	{
		return open ? "Hide the code field" : "Join another group…";
	}

	/**
	 * One group as the menu says it: "Sunday raids · KQX 482", or the code alone.
	 *
	 * <p>The code stays beside the name because it is what the status line prints and what a friend
	 * is told to type; the name is what a person recognises. A name that is only the code — what the
	 * map calls an adopted legacy group until its owner names it — is not printed twice, and a group
	 * the map has not named is its code, which is all this menu used to say about any of them.
	 */
	static String groupLabel(String code, @Nullable String name)
	{
		String printed = JoinCode.forDisplay(code);
		if (name == null || name.replace(" ", "").equalsIgnoreCase(code.replace(" ", "")))
		{
			return printed;
		}
		return name + " · " + printed;
	}

	/**
	 * A menu row that shows its words as words.
	 *
	 * <p>A Swing menu item renders any text that starts with {@code <html>} as markup, and a group's
	 * name is whatever its owner typed on the map — so a name could lay out anything inside this
	 * client, or load an image from anywhere. {@code html.disable} is Swing's own switch for that,
	 * and it is set before the text because the renderer is chosen when the text is. The wrapped
	 * labels elsewhere in this panel escape instead ({@link MapCompanionPanel#setWrapped}); a menu row has no width to
	 * wrap to, so it has no reason to be markup at all.
	 */
	static JMenuItem groupMenuItem(String text)
	{
		JMenuItem item = new JMenuItem();
		item.putClientProperty("html.disable", Boolean.TRUE);
		item.setText(text);
		return item;
	}

	/**
	 * The group as the status line says it: the name alone once the map has sent one, else the code.
	 *
	 * <p>Not {@link #groupLabel}'s "name · code". The line is the card's width less the dot, and the
	 * code is the half of that pair nobody recognises, so on this line it is the tooltip. A name
	 * that is only the code — an adopted legacy group's, until its owner renames it — is printed as
	 * the code.
	 */
	static String statusGroup(String code, @Nullable String name)
	{
		if (name == null || name.trim().isEmpty()
			|| name.replace(" ", "").equalsIgnoreCase(code.replace(" ", "")))
		{
			return JoinCode.forDisplay(code);
		}
		return name;
	}

	/**
	 * The status card's first line, beside the dot: the group's name while the map is being fed,
	 * and otherwise what the connection is doing. "Not connected" before a group, where the plugin's
	 * own word was "Not paired": pairing is this code's word for it, and nobody joins a map to be
	 * paired. "Connected" is the dot's colour and tooltip rather than a line of its own.
	 *
	 * <p>Before a group it is also where Connect and Join are answered: "Connecting…" while the map
	 * is asked, rather than "Not connected" beside a button that says otherwise, and why the last
	 * Connect made no group ({@code failed}) until the next press.
	 */
	static String statusWords(boolean paired, boolean ready, boolean asking, @Nullable String failed, Health health,
		String status, @Nullable String group)
	{
		if (asking && !ready)
		{
			return "Connecting…";
		}
		if (!paired)
		{
			return failed != null ? failed : "Not connected";
		}
		if (!ready)
		{
			return "Press Connect";
		}
		return health == Health.LIVE && group != null ? group : status;
	}

	/**
	 * The dot's colour. Before a group it is grey, whatever the connection's own state, which is
	 * amber at the login screen and grey in game and would colour the same "Not connected" two ways;
	 * and amber while Connect or Join is asked, as every "Connecting…" is.
	 */
	static Health dotHealth(boolean paired, boolean asking, Health health)
	{
		if (paired)
		{
			return health;
		}
		return asking ? Health.WAITING : Health.IDLE;
	}

	/**
	 * The status card's first line while the map refuses this member, in a few words beside the dot.
	 * The map's sentence is a column of three lines in the 141px there, and a refusal the field can
	 * answer has its next step under the field ({@link #refusedNextStep}); the whole sentence stays in
	 * the dot's tooltip.
	 */
	static String refusedWords(RelayClient.Refused refusal)
	{
		switch (refusal)
		{
			case NEEDS_INVITE:
				return "Needs an invite";
			case REMOVED:
				return "Removed from this group";
			case CLOSED:
				return "This group was closed";
			case GONE:
				return "Group gone from the map";
			case FULL:
				return "Group is full";
			case BAD_CODE:
				return "Group code not accepted";
			default:
				return "Plugin out of date";
		}
	}

	/**
	 * What to do about a refusal, under the field it is done in, or null when the field cannot answer
	 * it — a plugin the map cannot read, or no refusal at all. {@code group} is the status line's own
	 * name for it.
	 *
	 * <p>A group that keeps members takes an invite; one that has gone, or let this member go, takes
	 * somebody else's, or Leave group to start a new one of the player's own.
	 */
	@Nullable
	static String refusedNextStep(@Nullable RelayClient.Refused refusal, String group)
	{
		if (refusal == null)
		{
			return null;
		}
		switch (refusal)
		{
			case NEEDS_INVITE:
				return "Ask someone in " + group + " for an invite and paste it here.";
			case FULL:
				return "Ask someone in " + group + " to make room, or paste another group's invite here.";
			case BAD_CODE:
				return "Paste the invite again.";
			case REMOVED:
			case CLOSED:
			case GONE:
				return "Paste another group's invite here, or use Leave group in Settings → Advanced to start over.";
			default:
				return null;
		}
	}

	/**
	 * The status card, from the connection the sidebar's refresh has just read: the dot and its
	 * tooltip, the two lines beside and under it, Rename group… and what is shared. EDT.
	 *
	 * <p>A refusal the map holds to ({@link RelayClient#getRefused}) is the first line in a few words, in
	 * the refusal pink beside a red dot, through the five-minute retries that would otherwise bring the
	 * group's name back between two refusals. A press that may answer it is Connecting…, amber.
	 */
	void refreshStatus(Health health, String code, boolean paired, boolean ready)
	{
		if (paired)
		{
			/* A failed Connect is news until a group is had, by that press or any other. */
			connectFailed = null;
		}
		refused = paired ? relay.getRefused() : null;
		Health shown = refused == null ? health : asking ? Health.WAITING : Health.ERROR;
		dot.setForeground(dotHealth(paired, asking, shown).getColor());
		/* The status a transient failure came with, which the first line leaves out, for a bug report. */
		int answered = relay.getFatalError() == null ? relay.getLastTransientStatus() : 0;
		dot.setToolTipText(plugin.getStatus()
						   + (answered > 0 ? " · HTTP " + answered : "")
						   + " · last post " + ageOf(relay.getLastSuccessMs())
						   + (directiveStream.isConnected() ? " · stream open" : " · stream closed"));
		/*
		 * Two lines. While the map is being fed, the dot's green says connected, and its tooltip says
		 * it in words with the last post, so the first line is the group's name and the second what
		 * is shared. Otherwise the first line is what is wrong or awaited, and the group's name is
		 * the second, because which group a "Connecting…" is for still matters.
		 */
		boolean live = ready && shown == Health.LIVE;
		String group = paired ? statusGroup(code, pairing.name(code)) : null;
		setWrapped(statusLabel, refused == null
			? statusWords(paired, ready, asking, connectFailed, health, plugin.getStatus(), group)
			: asking ? "Connecting…" : refusedWords(refused), CARD_TEXT_WIDTH - 18 - SidebarIcons.SIZE - 4);
		statusLabel.setForeground(paired && shown == Health.ERROR ? MapCompanionPanel.REFUSED : statusInk);
		statusLabel.setToolTipText(live ? "Group code " + JoinCode.forDisplay(code) + " · " + dot.getToolTipText()
			: dot.getToolTipText());
		/* Before a group, no second line: the map's address is in Settings, and is not news. */
		groupLabel.setVisible(paired && !live);
		if (paired && !live)
		{
			setWrapped(groupLabel, group, CARD_TEXT_WIDTH);
		}
		groupLabel.setToolTipText(paired ? "Group code " + JoinCode.forDisplay(code) : null);
		/* Not under a group that refuses the player: the map would refuse the rename too. */
		nameLink.setVisible(refused == null
			&& offersRename(paired, paired && plugin.runsGroup(pairing.serverUrl(), code), pairing.name(code)));

		/*
		 * Character fields are separate from membership: all opt-ins off still sends the latter
		 * after Connect. The tooltip and Advanced preview keep the precise disclosure reachable.
		 */
		sharingLabel.setVisible(live);
		if (live)
		{
			setWrapped(sharingLabel, sharingLine(), CARD_TEXT_WIDTH);
		}
		sharingLabel.setToolTipText(paired ? sharingList() : null);
	}

	/**
	 * The group's buttons, the join field and the orange button, from the remembered groups the
	 * sidebar's refresh read. Called after the Group tab's own refresh, which decides whether its card
	 * holds Copy invite. EDT.
	 */
	void refreshButtons(boolean paired, boolean ready, List<String> known, GroupPanel groupPanel)
	{
		refreshButtons(paired, ready, true, known, groupPanel, System.currentTimeMillis());
	}

	/** The same, as of {@code now}: a confirmation is drawn only while it is news. */
	void refreshButtons(boolean paired, boolean ready, List<String> known, GroupPanel groupPanel, long now)
	{
		refreshButtons(paired, ready, true, known, groupPanel, now);
	}

	/**
	 * The same, knowing whether a character is logged in: an invite is asked for as a member, and a
	 * member is a character, so Copy invite waits for one. Switch ▾, Rename group… and the orange
	 * button act as the player, and stay.
	 */
	void refreshButtons(boolean paired, boolean ready, boolean loggedIn, List<String> known, GroupPanel groupPanel, long now)
	{
		copyInviteButton.setEnabled(paired && loggedIn && pairing.hasServerConsent() && !copyingInvite);
		knownGroupsRow.setVisible(!paired && !known.isEmpty());
		List<JButton> groupRow = new ArrayList<>();
		/* A group of one has Copy invite in the card that says so, from the Group tab's refresh just before. */
		if (paired && !(ready && groupPanel.holdsInvite()))
		{
			groupRow.add(copyInviteButton);
		}
		if (paired)
		{
			/* Always, now that it also holds Join another group. */
			groupRow.add(switchButton);
		}
		shownGroupActions = syncRow(groupButtons, groupRow, shownGroupActions);
		groupButtons.setVisible(!groupRow.isEmpty());
		groupActions.setVisible(paired);
		/* A line about one group is not news in the next, nor after leaving it. */
		if (!paired || !saidFor.equalsIgnoreCase(pairingCode()))
		{
			groupMessage.setVisible(false);
			cardMessage.setVisible(false);
		}
		letGo(groupMessage, groupConfirmedAtMs, now);
		letGo(cardMessage, cardConfirmedAtMs, now);
		letGo(joinMessage, joinConfirmedAtMs, now);
		/*
		 * A refusal the field can answer opens it under the card that says so, as before a group: the
		 * field Join another group… opens, with its heading, because it is the way back in.
		 */
		String step = refusedNextStep(refused, statusGroup(pairingCode(), pairing.name(pairingCode())));
		joinBlock.setVisible(!paired || !pairing.hasServerConsent() || joinOpen || step != null);
		/* "Have an invite?" says which way in the field is; once in a group it is only Join another. */
		joinHeading.setVisible(!paired || step != null);
		nextStep.setVisible(step != null);
		refusedGap.setVisible(step != null);
		if (step != null)
		{
			setWrapped(nextStep, step);
		}
		/* A line about a field that has folded away has nothing to point at. */
		if (!joinBlock.isVisible())
		{
			joinMessage.setVisible(false);
		}
		placeField(ready && joinOpen && step == null);
		joinButton.setEnabled(!asking);
		openMapButton.setText(asking ? (ready ? "Opening…" : "Connecting…") : ready ? OPEN_MAP : CONNECT);
		openMapButton.setEnabled(!asking);
		JoinCode pasted = joinsInstead(joinField.getText());
		openMapButton.setToolTipText(ready
			? "Open " + pairing.serverLabel() + " in your browser, already in your group as you"
			: pasted != null ? "Join the group this " + (pasted.invite != null ? "invite" : "code") + " is for"
			: paired ? "Connect to this group again"
			: "Make a group for you on " + pairing.serverLabel() + ". Play alone, or invite friends to it.");
		/* While the step after Connect is up, its press has the orange and this waits, plain, by name. */
		RecommendedSharing.orange(openMapButton, !recommendedSharing.isVisible());
	}

	/**
	 * The field where it is wanted: in the Group tab under the group's buttons while the menu there
	 * has it open, and under the orange button otherwise. It is one piece moved between two places,
	 * so what was typed in it and what it said go with it.
	 */
	private void placeField(boolean below)
	{
		JPanel slot = below ? fieldBelow : fieldAbove;
		fieldBelow.setVisible(below);
		if (field.getParent() == slot)
		{
			return;
		}
		slot.add(field);
		fieldAbove.revalidate();
		fieldAbove.repaint();
		fieldBelow.revalidate();
		fieldBelow.repaint();
	}

	/** A short summary of character sharing in Settings' switches, never a claim that membership sends nothing. */
	private String sharingLine()
	{
		return SharingSummary.line(config);
	}

	/** Every switch for the line's tooltip, one a line, wrapped to fit the screen. */
	private String sharingList()
	{
		return SharingSummary.list(config);
	}

	/**
	 * How long since a post actually landed, for the tooltip.
	 *
	 * <p>The colour of the dot is a summary; this is the sentence that makes a green that
	 * has not posted in a minute visible as a number rather than a feeling.
	 */
	static String ageOf(long lastSuccessMs)
	{
		if (lastSuccessMs <= 0L)
		{
			return "never";
		}
		long ago = System.currentTimeMillis() - lastSuccessMs;
		if (ago < 2_000L)
		{
			return "just now";
		}
		if (ago < 60_000L)
		{
			return (ago / 1000L) + "s ago";
		}
		return (ago / 60_000L) + "m ago";
	}

	/**
	 * Copy an invitation to the clipboard — asking the map for a link that expires, if it can.
	 *
	 * <p>The clipboard, not the chat box: a plugin that typed into the game would be generating
	 * input, which is exactly the line this feature does not cross. Pasting it is the player's own
	 * action, in whatever channel they choose.
	 *
	 * <p><b>Why it asks the map first.</b> A message that spells out the code admits anybody who
	 * finds it in a Discord channel three months later, and there is nothing the holder of the
	 * group can do about that short of closing the group. An invite link carries a token the map
	 * minted, which expires and can be revoked, so the group keeps a way to stop admitting people
	 * without ending the group. Only the map can mint one.
	 *
	 * <p><b>And why the old message is still here.</b> A group nobody owns still admits by its bare
	 * code, and the map's invite door answers 404 for one, as a map without the door does; a friend
	 * waiting to be invited there is not the person to make pay for that. The fallback is the old
	 * text, unchanged, and it works for exactly those groups ({@link RelayClient#admitsByCode}).
	 *
	 * <p><b>And why it is not copied for anything else.</b> Every group Connect makes is owned, and an
	 * owned group refuses a bare code from anybody new. Copied there, it would say "Invite copied" to
	 * an inviter whose friend's paste is certain to fail, and neither of them could tell why. A
	 * failure copies nothing and says so, while the inviter can still try again.
	 *
	 * <p>The request carries the member secret the map issued this installation, because minting an
	 * invite is the one thing a member can do that admits a stranger and a member id is not a
	 * secret. A map that wants one and does not get it refuses, which is a failure like any other.
	 */
	private void copyInvite()
	{
		String code = pairingCode();
		String server = pairing.serverUrl();
		if (code.isEmpty() || !pairing.hasServerConsent(server, code) || copyingInvite)
		{
			return;
		}
		/* An invite is asked for as a member, and a member is a character: nobody before login. */
		String memberId = identity.id();
		if (memberId == null)
		{
			refuseUnderGroup("Log in to a character to copy an invite.");
			return;
		}
		copyingInvite = true;
		copiedFlash.stop();
		copyInviteButton.setEnabled(false);
		copyInviteButton.setText("Asking the map…");
		String secret = pairing.secret(server, code, memberId);
		relay.requestInvite(server, code, memberId, secret, (invite, status) ->
							SwingUtilities.invokeLater(() -> finishInvite(server, code, invite, status, secret != null)));
	}

	/**
	 * What Copy invite puts on the clipboard: what to do with it, then the link.
	 *
	 * <p>Both ways in are named, because a friend meets the message in a chat window and may be
	 * holding either a browser or RuneLite: pasted into this plugin's sidebar the invite is spent
	 * there ({@link JoinCode} reads the link out of the whole message), and opened in a browser it
	 * joins the map. The link comes last, so a chat client that links what it recognises ends the
	 * link where the link ends.
	 */
	static String inviteMessage(String url)
	{
		return "Join my group on the OSRS map: paste this into OSRS Map Companion in RuneLite, or open " + url;
	}

	/** What Copy invite says when the map made no invite a friend could use: the clipboard is as it was. */
	static final String INVITE_FAILED = "Couldn't make an invite, so nothing was copied. Try again in a minute.";

	/**
	 * What Copy invite puts on the clipboard for the map's answer, or null for nothing at all: the
	 * link when the map made one, and the plain message only for a group that still admits by its
	 * code. Package-private so every answer can be pinned without a clipboard or a server.
	 */
	@Nullable
	static String inviteText(@Nullable RelayClient.Invite invite, int status, boolean heldSecret, String map, String code)
	{
		if (invite != null && invite.url != null && !invite.url.isEmpty())
		{
			return inviteMessage(invite.url);
		}
		if (RelayClient.admitsByCode(status, heldSecret))
		{
			return "Join my OSRS map: " + map + "/map?join=" + code + "&task=companion"
				+ " — then install the \"OSRS Map Companion\" RuneLite plugin and enter code " + code;
		}
		return null;
	}

	/** Put the invitation on the clipboard, or say that there was none to put there. */
	private void finishInvite(String server, String code, @Nullable RelayClient.Invite invite, int status, boolean heldSecret)
	{
		copyingInvite = false;
		copyInviteButton.setText("Copy invite");
		copyInviteButton.setEnabled(!pairingCode().isEmpty() && pairing.hasServerConsent());
		/* An invite arriving after a switch belongs to the old connection, not the current clipboard action. */
		if (!server.equals(mapUrl.get()) || !code.equalsIgnoreCase(pairingCode()))
		{
			return;
		}

		String message = inviteText(invite, status, heldSecret, mapUrl.get(), code);
		if (message == null)
		{
			refuseUnderGroup(INVITE_FAILED);
			return;
		}
		boolean linked = invite != null && invite.url != null && !invite.url.isEmpty();

		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(message), null);
		}
		catch (RuntimeException e)
		{
			log.debug("could not copy invite", e);
			refuseUnderGroup("Couldn't put the invite on the clipboard.");
			return;
		}
		showCopied(true);
		confirmUnderGroup("Invite copied."
			+ (linked ? describeExpiry(invite.expiresAt, System.currentTimeMillis()) : ""));
	}

	/**
	 * "It expires in 7 days.", from the instant the map stated — or nothing at all.
	 *
	 * <p>Nothing at all is the important half. The expiry is the map's number in the map's units,
	 * and this only ever prints it: anything that is not a future instant in milliseconds produces
	 * no sentence rather than a confident wrong one, which is the right way to be wrong about
	 * somebody else's field.
	 *
	 * <p>Days are rounded, because the map's seven days arrive here a moment short of seven, and a
	 * floor would call the invite the website labels <i>7 days</i> six. Below a day it counts whole
	 * hours, so twenty hours is not "1 day".
	 *
	 * <p>Package-private so the cases can be pinned without a clipboard or a server.
	 */
	static String describeExpiry(@Nullable String expiresAt, long now)
	{
		if (expiresAt == null || expiresAt.trim().isEmpty())
		{
			return "";
		}
		long at;
		try
		{
			at = Long.parseLong(expiresAt.trim());
		}
		catch (NumberFormatException e)
		{
			return "";
		}
		long remaining = at - now;
		if (remaining <= 0L)
		{
			return "";
		}
		if (remaining >= 86_400_000L)
		{
			long days = Math.round(remaining / 86_400_000.0);
			return " It expires in " + days + (days == 1L ? " day." : " days.");
		}
		long hours = remaining / 3_600_000L;
		if (hours >= 1L)
		{
			return " It expires in " + hours + (hours == 1L ? " hour." : " hours.");
		}
		return " It expires within the hour.";
	}

	private String pairingCode()
	{
		return config.pairingCode() == null ? "" : config.pairingCode().trim();
	}
}
