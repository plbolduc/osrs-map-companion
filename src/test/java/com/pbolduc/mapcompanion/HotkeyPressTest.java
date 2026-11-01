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

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** A route key presses the sidebar's own button, so it can do nothing a click could not. */
public class HotkeyPressTest
{
	/** Reroute inside its wait is disabled, and a key pressed then must ask the map nothing. */
	@Test
	public void aKeyPressesOnlyAButtonThePlayerCouldClick() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AtomicInteger pressed = new AtomicInteger();
			JButton reroute = MapCompanionPanel.smallButton("Reroute");
			reroute.addActionListener(e -> pressed.incrementAndGet());

			reroute.setEnabled(false);
			assertFalse(MapCompanionPanel.press(reroute));
			assertEquals("a disabled button is not pressed", 0, pressed.get());

			reroute.setEnabled(true);
			assertTrue(MapCompanionPanel.press(reroute));
			assertEquals(1, pressed.get());

			assertFalse("no run and no destination shown: nothing to press", MapCompanionPanel.press(null));
		});
	}
}
