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

import com.google.gson.annotations.SerializedName;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The bundled rate table uses in-game NPC names, not wiki page titles.
 *
 * <p>A row titled {@code Skeleton (Melzar's Maze)} can never match a kill the client reports
 * as {@code Skeleton}. The map's generator strips that disambiguator before this file is
 * copied across; this is the plugin-side guard against a future regenerate that forgets.
 */
public class LootLogRatesTest
{
	private static class Rate
	{
		String npc;
		String item;
		/** Rational drop probabilities need a decimal reciprocal, not an integer approximation. */
		double oneIn;
		String page;
	}

	private static class RateFile
	{
		@SerializedName("rates")
		List<Rate> rates;
	}

	@Test
	public void namesMonstersAsTheGameDoes() throws Exception
	{
		try
			(InputStream in = LootLogRatesTest.class.getResourceAsStream("/unique-rates.json"))
		{
			assertNotNull("bundled unique-rates.json missing from the test classpath", in);
			RateFile file = new Gson().fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), RateFile.class);
			assertNotNull(file);
			assertNotNull(file.rates);
			assertTrue("rate table was empty", file.rates.size() > 0);
			for (Rate rate : file.rates)
			{
				assertTrue("invalid reciprocal for " + rate.npc + "/" + rate.item,
						   Double.isFinite(rate.oneIn) && rate.oneIn >= 1);
				assertNotNull(rate.npc);
				assertFalse(
					"wiki disambiguator on npc '" + rate.npc + "' — the generator forgot to strip",
					rate.npc.contains(" ("));
			}
			assertTrue("the revised corpus preserves non-integer reciprocals",
					   file.rates.stream().anyMatch(rate -> rate.oneIn != Math.rint(rate.oneIn)));
		}
	}
}
