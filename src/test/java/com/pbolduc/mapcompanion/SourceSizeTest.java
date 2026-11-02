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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Keeps the plugin's source under the size the Plugin Hub reviews automatically.
 *
 * <p>The Hub's bot reviews an update by itself only while the source stays under 200k tokens, and
 * it counts src/main/java with comments removed: resources, tests and the README are not counted.
 * 1.4.0 measured 795,928 characters that way and the bot counted 206k tokens, about 3.86
 * characters a token, so it went to a person instead. The budget here is 752,700 characters, about
 * 195k tokens, which leaves room for the ratio to be a little off.
 *
 * <p>Over budget, the way down is the one the cut that made this test took: tables and words a
 * player reads belong in src/main/resources, behind Resources.lines and Words.of, and code that
 * says the same thing twice can say it once. Raising the number only moves the review to a person.
 */
public class SourceSizeTest
{
	/** About 195k tokens at 1.4.0's 3.86 characters a token, under the Hub's 200k. */
	static final int BUDGET = 752_700;

	@Test
	public void theSourceStaysUnderTheSizeTheHubReviewsAutomatically() throws IOException
	{
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			sources = files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertTrue("the plugin's sources were not found from " + Paths.get("").toAbsolutePath(), sources.size() > 10);
		long total = 0;
		for (Path source : sources)
		{
			total += strip(new String(Files.readAllBytes(source), StandardCharsets.UTF_8)).length();
		}
		assertTrue("src/main/java is " + total + " characters without comments, about " + Math.round(total / 3860.0)
			+ "k tokens, over this plugin's budget of " + BUDGET + " (about 195k). The Plugin Hub reviews an update"
			+ " automatically only under 200k tokens; move tables or words into src/main/resources, which it does"
			+ " not count, rather than raise the budget.", total <= BUDGET);
	}

	/** The counting rule on its own: what goes, what stays. */
	@Test
	public void commentsGoAndLiteralsStay()
	{
		assertEquals("\nint a = 1;\nString b = \"// kept\";\nchar c = '/';\nint d;\n",
			strip("/* licence */\nint a = 1; // a note\n\n\nString b = \"// kept\";   \nchar c = '/';\n/**\n * doc\n */\nint d;\n"));
		assertEquals("String e = \"a \\\" /* not a comment */\";\n", strip("String e = \"a \\\" /* not a comment */\";\n"));
	}

	/**
	 * What the Hub's count leaves: line and block comments removed, string, character and text-block
	 * literals kept whole, then trailing blanks and the empty lines comments leave behind.
	 */
	static String strip(String src)
	{
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < src.length(); )
		{
			char c = src.charAt(i);
			if (src.startsWith("\"\"\"", i))
			{
				int end = src.indexOf("\"\"\"", i + 3);
				int stop = end < 0 ? src.length() : end + 3;
				out.append(src, i, stop);
				i = stop;
			}
			else if (c == '"' || c == '\'')
			{
				int j = i + 1;
				while (j < src.length() && src.charAt(j) != c)
				{
					j += src.charAt(j) == '\\' ? 2 : 1;
				}
				int stop = Math.min(j + 1, src.length());
				out.append(src, i, stop);
				i = stop;
			}
			else if (src.startsWith("//", i))
			{
				while (i < src.length() && src.charAt(i) != '\n')
				{
					i++;
				}
			}
			else if (src.startsWith("/*", i))
			{
				int end = src.indexOf("*/", i + 2);
				i = end < 0 ? src.length() : end + 2;
			}
			else
			{
				out.append(c);
				i++;
			}
		}
		return out.toString().replaceAll("(?m)[ \\t]+$", "").replaceAll("\n{2,}", "\n");
	}
}
