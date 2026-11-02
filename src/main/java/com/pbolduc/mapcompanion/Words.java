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

import java.util.HashMap;
import java.util.Map;

/**
 * The longer things the plugin says to a player, by key, from words.txt:
 * {@code Words.of("route.needsTile")} is the text after the = on that key's line, where \n is a line
 * break and a leading space is kept.
 *
 * <p>Why they live there rather than in Java: the Plugin Hub reviews an update automatically only
 * below a size it counts in the source, and it does not count resources. Only a player's words move.
 * Where a sentence has a name or a number in it, the words around it move and the Java still puts
 * the sentence together. What the map's own tests read in this source, and what is not a player's
 * words, such as a log line, a pattern, a key or a header, stays beside the code that uses it.
 *
 * <p>The table is read once, when a word is first asked for, and only read after that, so any thread
 * may ask. A key the file lacks is its own text rather than an exception, so a missing line costs a
 * player one odd label and never the sidebar; {@code WordsTest} fails the build on it first.
 */
final class Words
{
	private static final Map<String, String> WORDS = new HashMap<>();

	static
	{
		for (String line : Resources.lines("/words.txt"))
		{
			int split = line.indexOf('=');
			if (split > 0)
			{
				WORDS.put(line.substring(0, split), line.substring(split + 1).replace("\\n", "\n"));
			}
		}
	}

	private Words()
	{
	}

	static String of(String key)
	{
		return WORDS.getOrDefault(key, key);
	}
}
