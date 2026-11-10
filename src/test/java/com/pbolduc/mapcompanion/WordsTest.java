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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The words file and the source that reads it agree: every key a {@code Words.of} call names is a
 * line of words.txt, and every line is named by a call.
 *
 * <p>A key the file lacks is no exception in a player's client: {@link Words#of} answers with the
 * key itself, so a label would read "route.needsTile" rather than the sidebar going down. That
 * makes this the one place a missing line is found before a player finds it. A line nothing asks
 * for is a sentence somebody edits and never sees change.
 */
public class WordsTest
{
	private static final Path FILE = Paths.get("src/main/resources/words.txt");
	private static final String KEY = "[a-z]+\\.[A-Za-z0-9]+";
	/** A call, wherever it is; the key it is handed must be a literal, or this test cannot see it. */
	private static final Pattern CALL = Pattern.compile("\\bWords\\.of\\(");
	private static final Pattern LITERAL_KEY = Pattern.compile("\"(" + KEY + ")\"\\)");
	/** A key, =, and words that do not end in a space, which an editor would trim without a word. */
	private static final Pattern LINE = Pattern.compile("(" + KEY + ")=(.*\\S)");

	@Test
	public void everyKeyTheSourceAsksForIsALine() throws IOException
	{
		Set<String> missing = new TreeSet<>(asked());
		missing.removeAll(lines().keySet());
		assertEquals("keys the source asks for that words.txt lacks", new TreeSet<String>(), missing);
	}

	/**
	 * What a player is shown for each key the source asks for, through the reader the plugin uses
	 * rather than this test's own reading of the file: never the key itself, which is what a line
	 * missing from the jar, or one whose words are its own key, would put on the sidebar.
	 */
	@Test
	public void everyKeyTheSourceAsksForReadsAsWords() throws IOException
	{
		Set<String> unread = new TreeSet<>();
		for (String key : asked())
		{
			if (Words.of(key).equals(key))
			{
				unread.add(key);
			}
		}
		assertEquals("keys that read as themselves", new TreeSet<String>(), unread);
	}

	@Test
	public void everyLineIsAskedFor() throws IOException
	{
		Set<String> unused = new TreeSet<>(lines().keySet());
		unused.removeAll(asked());
		assertEquals("lines of words.txt that nothing asks for", new TreeSet<String>(), unused);
	}

	/** The reader the plugin uses gives back each line's words, a \n in them a line break. */
	@Test
	public void eachKeyReadsAsItsLineSays() throws IOException
	{
		Map<String, String> lines = lines();
		assertTrue("words.txt has no lines", lines.size() > 10);
		for (Map.Entry<String, String> line : lines.entrySet())
		{
			assertEquals(line.getKey(), line.getValue().replace("\\n", "\n"), Words.of(line.getKey()));
		}
	}

	@Test
	public void aKeyTheFileLacksIsItsOwnTextRatherThanAnException()
	{
		assertEquals("route.noSuchLine", Words.of("route.noSuchLine"));
	}

	/** Every key a call in the main source names, with comments left out so prose cannot count. */
	private static Set<String> asked() throws IOException
	{
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			sources = files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertTrue("the plugin's sources were not found from " + Paths.get("").toAbsolutePath(), sources.size() > 10);
		Set<String> keys = new TreeSet<>();
		List<String> notLiteral = new ArrayList<>();
		for (Path source : sources)
		{
			String code = withoutComments(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
			Matcher call = CALL.matcher(code);
			while (call.find())
			{
				Matcher key = LITERAL_KEY.matcher(code).region(call.end(), code.length());
				if (key.lookingAt())
				{
					keys.add(key.group(1));
				}
				else
				{
					notLiteral.add(source.getFileName() + ": " + code.substring(call.start(), Math.min(code.length(), call.end() + 40)));
				}
			}
		}
		assertEquals("Words.of is handed its key as a literal, so this test sees every one", new ArrayList<String>(), notLiteral);
		assertTrue("no Words.of call was found, so the pattern no longer matches the source", keys.size() > 10);
		return keys;
	}

	/** The file's lines by key, failing on a line that is not a key and its words, or a key twice. */
	private static Map<String, String> lines() throws IOException
	{
		Map<String, String> lines = new LinkedHashMap<>();
		for (String line : new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8).split("\r?\n"))
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			Matcher row = LINE.matcher(line);
			assertTrue("not a key and its words: " + line, row.matches());
			assertTrue("a key twice: " + row.group(1), lines.put(row.group(1), row.group(2)) == null);
		}
		return lines;
	}

	/** Java source with its comments taken out; a literal is kept whole, // in a link included. */
	private static String withoutComments(String source)
	{
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < source.length(); i++)
		{
			char c = source.charAt(i);
			if (c == '"' || c == '\'')
			{
				int end = i + 1;
				while (end < source.length() && source.charAt(end) != c)
				{
					end += source.charAt(end) == '\\' ? 2 : 1;
				}
				end = Math.min(end, source.length() - 1);
				code.append(source, i, end + 1);
				i = end;
			}
			else if (source.startsWith("//", i))
			{
				int end = source.indexOf('\n', i);
				i = (end < 0 ? source.length() : end) - 1;
			}
			else if (source.startsWith("/*", i))
			{
				int end = source.indexOf("*/", i + 2);
				i = end < 0 ? source.length() : end + 1;
			}
			else
			{
				code.append(c);
			}
		}
		return code.toString();
	}
}
