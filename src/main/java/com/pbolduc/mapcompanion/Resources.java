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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The plugin's own tables, read from src/main/resources: one entry a line, leaving out blank lines
 * and lines that start with #, so a table can say where its rows came from.
 *
 * <p>Why tables live there rather than in Java: the Plugin Hub reviews an update automatically only
 * below a size it counts in the source, and it does not count resources. A table is data its
 * tests hold to its origin; it never needed to be code.
 *
 * <p>A resource that cannot be read is an empty list rather than an exception thrown while a class
 * initializes, which would take the plugin down with it. Every table's tests read the real file, so
 * a missing one fails the build rather than a player's client.
 */
final class Resources
{
	private Resources()
	{
	}

	static List<String> lines(String resource)
	{
		List<String> lines = new ArrayList<>();
		try (InputStream stream = Resources.class.getResourceAsStream(resource))
		{
			if (stream != null)
			{
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
				for (String line; (line = reader.readLine()) != null;)
				{
					if (!line.trim().isEmpty() && !line.startsWith("#"))
					{
						lines.add(line);
					}
				}
			}
		}
		catch (IOException unreadable)
		{
			lines.clear();
		}
		return lines;
	}
}
