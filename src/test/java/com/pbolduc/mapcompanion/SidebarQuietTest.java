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

import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;

/**
 * The sidebar's refresh runs once a second for as long as it is open, so what it costs when
 * nothing has changed is what it costs nearly all of the time.
 *
 * <p>The real {@link MapCompanionPanel}, from {@link TestSidebar}, in a window that is packed and
 * never shown: a component only counts as laid out once it is in one, so on a machine with no
 * display there is nothing to check and the test does not run.
 */
public class SidebarQuietTest
{
	private final TestSidebar sidebar = new TestSidebar();

	public SidebarQuietTest() throws Exception
	{
	}

	/**
	 * A card that asks for a layout on every refresh, or empties a list that was already empty, has
	 * the whole column measured and placed again each second with nothing to show for it.
	 */
	@Test
	public void aRefreshThatChangesNothingLeavesTheLayoutAlone() throws Exception
	{
		Assume.assumeFalse("no display to lay a window out on", GraphicsEnvironment.isHeadless());
		JFrame[] window = new JFrame[1];
		SwingUtilities.invokeAndWait(() ->
		{
			window[0] = new JFrame();
			window[0].add(sidebar.panel);
			window[0].pack();
		});
		try
		{
			TestSidebar.settle();
			SwingUtilities.invokeAndWait(() ->
			{
				window[0].validate();
				assertTrue("laid out once, in its window", sidebar.panel.isValid());
				/* No run and no destination, so the key finds nothing to press: this is the refresh alone. */
				sidebar.panel.act(MapCompanionPanel.Act.FOLLOW);
				assertTrue("a refresh with nothing new marked no part of the sidebar for another layout",
					sidebar.panel.isValid());
			});
		}
		finally
		{
			SwingUtilities.invokeAndWait(window[0]::dispose);
		}
	}
}
