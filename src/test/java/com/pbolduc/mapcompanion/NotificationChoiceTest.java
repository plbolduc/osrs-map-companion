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

import java.awt.Color;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.config.RuneLiteConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The sidebar's meetup box: on, the reminder reaches a player at the game, arriving as their other
 * notifications do; a notification the player customised in RuneLite's panel stays as they set it.
 */
public class NotificationChoiceTest
{
	/** RuneLite's own notification settings, moved off how they ship so a copy shows as one. */
	private final RuneLiteConfig runeLite = new RuneLiteConfig()
	{
		@Override
		public boolean enableTrayNotifications()
		{
			return false;
		}

		@Override
		public RequestFocusType notificationRequestFocus()
		{
			return RequestFocusType.TASKBAR;
		}

		@Override
		public NotificationSound notificationSound()
		{
			return NotificationSound.CUSTOM;
		}

		@Override
		public int notificationVolume()
		{
			return 40;
		}

		@Override
		public boolean enableGameMessageNotification()
		{
			return true;
		}

		@Override
		public FlashNotification flashNotification()
		{
			return FlashNotification.FLASH_TWO_SECONDS;
		}

		@Override
		public Color notificationFlashColor()
		{
			return Color.BLUE;
		}
	};

	/**
	 * Whether RuneLite 1.13.4's {@code Notifier.notify(Notification, String)} returns before the tray,
	 * the sound and the chat line while its window is focused: a notification without settings of
	 * its own takes RuneLite's, whose <i>Send notifications when focused</i> ships off.
	 */
	private boolean heldWhileFocused(Notification notification)
	{
		boolean own = notification.isOverride() && notification.isInitialized();
		return !(own ? notification.isSendWhenFocused() : runeLite.sendNotificationsWhenFocused());
	}

	@Test
	public void turnedOnInTheSidebarItReachesAPlayerAtTheGame()
	{
		/* Never written; RuneLite's default written for it; and on as RuneLite's own box, or a "true" stored before, leaves it. */
		for (Notification before : new Notification[]{null, Notification.OFF, Notification.ON})
		{
			Notification after = NotificationChoice.chosen(before, true, runeLite);
			assertTrue(after.isEnabled());
			assertFalse("sent while RuneLite is focused", heldWhileFocused(after));
			assertFalse("the tray as RuneLite's settings have it", after.isTray());
			assertEquals(RequestFocusType.TASKBAR, after.getRequestFocus());
			assertEquals(NotificationSound.CUSTOM, after.getSound());
			assertEquals(40, after.getVolume());
			assertEquals(runeLite.notificationTimeout(), after.getTimeout());
			assertTrue("a chat line only because RuneLite's settings add one", after.isGameMessage());
			assertEquals(FlashNotification.FLASH_TWO_SECONDS, after.getFlash());
			assertEquals(Color.BLUE, after.getFlashColor());
		}
		assertTrue("on with RuneLite's defaults alone, it waits for the player to look away", heldWhileFocused(Notification.ON));
	}

	@Test
	public void aNotificationThePlayerCustomisedStaysAsTheySetIt()
	{
		Notification customised = NotificationChoice.chosen(null, true, runeLite)
			.withSendWhenFocused(false)
			.withTray(true)
			.withEnabled(false);
		Notification on = NotificationChoice.chosen(customised, true, runeLite);
		assertTrue(on.isEnabled());
		assertTrue(on.isTray());
		assertTrue("held while focused, as the player chose", heldWhileFocused(on));

		/* Customize notification unticked in RuneLite's panel: it follows RuneLite's settings, as chosen. */
		Notification uncustomised = NotificationChoice.chosen(customised.withOverride(false), true, runeLite);
		assertFalse(uncustomised.isOverride());
		assertTrue(heldWhileFocused(uncustomised));
	}

	@Test
	public void turnedOffItKeepsTheRestForNextTime()
	{
		Notification off = NotificationChoice.chosen(NotificationChoice.chosen(null, true, runeLite), false, runeLite);
		assertFalse(off.isEnabled());
		assertTrue(off.isOverride() && off.isInitialized() && off.isSendWhenFocused());
		assertFalse(NotificationChoice.chosen(null, false, runeLite).isEnabled());
		Notification again = NotificationChoice.chosen(off, true, runeLite);
		assertTrue(again.isEnabled());
		assertFalse(heldWhileFocused(again));
	}
}
