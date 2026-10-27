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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import net.runelite.client.ui.components.IconTextField;

/**
 * Reading and pressing a sidebar panel by its words, from outside it.
 *
 * <p>This lives under {@code src/test} because pressing a label means handing its listeners a
 * click nobody made, and the Plugin Hub does not allow shipped code to synthesise input even
 * when only a test would call it. Everything here goes through Swing's public component tree,
 * so the panel needs no hooks of its own.
 */
final class SidebarWords
{
	private SidebarWords()
	{
	}

	/** The words currently laid out, in order: label text without its markup, and button text. */
	static List<String> words(Component root)
	{
		List<String> out = new ArrayList<>();
		collect(root, out);
		return out;
	}

	private static void collect(Component component, List<String> out)
	{
		if (component instanceof JLabel)
		{
			String text = ((JLabel) component).getText();
			if (text != null && !text.isEmpty())
			{
				out.add(text.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"));
			}
		}
		else if (component instanceof JButton)
		{
			out.add(((JButton) component).getText());
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, out);
			}
		}
	}

	/** Press the first button or clickable label whose words are exactly {@code text}. */
	static boolean press(Component component, String text)
	{
		if (component instanceof JButton && text.equals(((JButton) component).getText()))
		{
			((JButton) component).doClick();
			return true;
		}
		if (component instanceof JLabel && text.equals(((JLabel) component).getText().replaceAll("<[^>]+>", "")))
		{
			MouseEvent click = new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false);
			for (MouseListener listener : component.getMouseListeners())
			{
				listener.mouseClicked(click);
			}
			return true;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				if (press(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Type into the first search field in the panel. Returns false when there is none. */
	static boolean search(Component component, String text)
	{
		if (component instanceof IconTextField)
		{
			((IconTextField) component).setText(text);
			return true;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				if (search(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}
}
