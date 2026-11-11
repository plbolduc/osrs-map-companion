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
import java.util.Arrays;
import java.util.List;
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
 * The tables the source reads from src/main/resources are there, and hold what their readers expect.
 *
 * <p>{@link Resources#lines} answers a missing or unreadable table with an empty list rather than
 * an exception while a class initializes, so a table lost in a move fails in no build of its own:
 * the farm run picker would then have no runs, and its first press would read row -1 on the Swing
 * thread. These read the real files the jar carries.
 */
public class ResourcesTest
{
	/** A literal resource name handed to {@code Resources.lines}. */
	private static final Pattern READ = Pattern.compile("Resources[.]lines[(]\"(/[^\"]+)\"[)]");

	/**
	 * The picker's eleven runs, in its order: the wire kind the map plans, then the name in the list.
	 * The dailies and sea charting runs come last, the two runs in the list that visit no patch.
	 */
	@Test
	public void theFarmRunPickerHasItsElevenRunsInOrder()
	{
		assertEquals(Arrays.asList(
			"ready-now|Ready now",
			"preset:herb|Herb run",
			"preset:tree|Tree run",
			"preset:fruit|Fruit tree run",
			"preset:hardwood|Hardwood run",
			"preset:trees|Tree + fruit tree",
			"preset:herb-allotment|Herb + allotment + flower",
			"birdhouses|Birdhouses",
			"contract|Farming contract",
			"dailies|Dailies run",
			"sea-charts|Sea charting run"), Resources.lines("/farm-starts.txt"));
	}

	/** The game's three pet lines, which a chat message must match whole. */
	@Test
	public void thePetMessagesAreTheGamesThree()
	{
		assertEquals(Arrays.asList(
			"You have a funny feeling like you're being followed.",
			"You have a funny feeling like you would have been followed...",
			"You feel something weird sneaking into your backpack."), Resources.lines("/pet-messages.txt"));
	}

	/** Every table the source names by a literal is in the jar's resources and has rows. */
	@Test
	public void everyTableTheSourceReadsHasRows() throws IOException
	{
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			sources = files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList());
		}
		Set<String> named = new TreeSet<>();
		for (Path source : sources)
		{
			Matcher read = READ.matcher(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
			while (read.find())
			{
				named.add(read.group(1));
			}
		}
		assertTrue("no Resources.lines call was found, so the pattern no longer matches the source: " + named,
			named.containsAll(Arrays.asList("/farm-starts.txt", "/pet-messages.txt", "/words.txt", "/game-reads.txt")));
		Set<String> empty = new TreeSet<>();
		for (String table : named)
		{
			if (Resources.lines(table).isEmpty())
			{
				empty.add(table);
			}
		}
		assertEquals("tables the source reads that are missing or have no rows", new TreeSet<String>(), empty);
	}
}
