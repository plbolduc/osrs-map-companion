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

import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.config.RuneLiteConfig;

/**
 * A RuneLite notification setting turned on or off by a box in the sidebar.
 *
 * <p>RuneLite keeps a notification setting as a notification of its own: a box, and behind a cog
 * beside it in RuneLite's configuration, how that one notification arrives. Its {@code Notifier}
 * holds a notification while RuneLite's window is focused unless that notification is sent when
 * focused, and one without settings of its own follows RuneLite's, where <i>Send notifications when
 * focused</i> starts off. Turned on with nothing more, a meetup reminder reaches nobody who is
 * playing: RuneLite holds it while they look at the game, and a reminder is said only once.
 *
 * <p>So the first time the sidebar's box turns one on, it gives it settings of its own, as RuneLite's
 * panel does when <i>Customize notification</i> is ticked: tray, focus request, sound, flash and chat
 * line copied from RuneLite's own, so they arrive the way the player's other notifications do. The
 * one difference is that it is sent while RuneLite is focused, because a reminder that waits for the
 * player to look away is no reminder; the box's description says so. All of it is RuneLite's to show
 * and change behind that cog, and a notification the player has customised there is theirs: the box
 * only turns it on and off.
 */
final class NotificationChoice
{
	private NotificationChoice()
	{
	}

	/** A press of the sidebar's box, stored through RuneLite's own serializer for notifications. */
	static void choose(ConfigManager configManager, String key, boolean on)
	{
		Notification stored = configManager.getConfiguration(MapCompanionConfig.GROUP, key, Notification.class);
		configManager.setConfiguration(MapCompanionConfig.GROUP, key, chosen(stored, on, configManager.getConfig(RuneLiteConfig.class)));
	}

	/**
	 * The setting a press leaves: null {@code stored} is one never written, or unreadable, which
	 * RuneLite treats as off.
	 */
	static Notification chosen(@Nullable Notification stored, boolean on, RuneLiteConfig runeLite)
	{
		if (!on)
		{
			return stored == null ? Notification.OFF : stored.withEnabled(false);
		}
		if (stored != null && stored.isInitialized())
		{
			return stored.withEnabled(true);
		}
		/* Field by field rather than RuneLite's all-arguments constructor, which grows with the class. */
		return Notification.OFF
			.withEnabled(true)
			.withInitialized(true)
			.withOverride(true)
			.withTray(runeLite.enableTrayNotifications())
			.withRequestFocus(runeLite.notificationRequestFocus())
			.withSound(runeLite.notificationSound())
			.withVolume(runeLite.notificationVolume())
			.withTimeout(runeLite.notificationTimeout())
			.withGameMessage(runeLite.enableGameMessageNotification())
			.withFlash(runeLite.flashNotification())
			.withFlashColor(runeLite.notificationFlashColor())
			.withSendWhenFocused(true);
	}
}
