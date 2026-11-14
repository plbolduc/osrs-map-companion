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

import java.lang.reflect.Field;

/**
 * The loop behind a plugin that a focused test assembles by hand, without Guice.
 *
 * <p>The plugin holds what RuneLite gives it to draw with, and {@link CompanionLoop} holds what a
 * game tick owns: the readers, the relay, what is waiting to be posted. A test that makes a plugin
 * with {@code new} gets neither, and gives it only the collaborators its path reaches. This gives
 * it the loop, drawing through that plugin as the real one does, and puts a collaborator named by
 * a test wherever it is held, which for a few is both.
 */
final class TestLoop
{
	/** The plugin, with a loop of its own that asks it for whatever is drawn. */
	static <P extends MapCompanionPlugin> P in(P plugin)
	{
		if (plugin.loop == null)
		{
			plugin.loop = new CompanionLoop();
			plugin.loop.surface = plugin;
		}
		return plugin;
	}

	/** Puts {@code value} in the field of that name, on the plugin, on its loop, or on both. */
	static void set(MapCompanionPlugin plugin, String name, Object value)
	{
		boolean held = put(MapCompanionPlugin.class, plugin, name, value);
		held |= put(CompanionLoop.class, in(plugin).loop, name, value);
		if (!held)
		{
			throw new AssertionError("neither the plugin nor its loop holds a field named " + name);
		}
	}

	private static boolean put(Class<?> type, Object holder, String name, Object value)
	{
		try
		{
			Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			field.set(holder, value);
			return true;
		}
		catch (NoSuchFieldException absent)
		{
			return false;
		}
		catch (IllegalAccessException error)
		{
			throw new AssertionError(error);
		}
	}
}
