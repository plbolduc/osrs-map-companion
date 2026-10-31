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

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Development-only access to RuneLite's package-private decoder, used for transcription and
 * parity checks. No reflection into another plugin is shipped in our runtime jar.
 */
final class FarmingDecodeReference
{
	static final String[] KINDS = {"HERB", "TREE", "FRUIT_TREE", "ALLOTMENT", "FLOWER", "SEAWEED", "CACTUS", "HOPS", "HARDWOOD_TREE", "BUSH", "BELLADONNA", "SPIRIT_TREE", "CORAL", "CALQUAT", "GRAPES", "MUSHROOM", "HESPORI", "ANIMA", "CELASTRUS", "REDWOOD", "CRYSTAL_TREE"};
	/** Pinned source census: a missing branch must fail even if a few weeds still decode. */
	private static final int[] ROW_COUNTS = {237, 246, 256, 256, 256, 256, 256, 256, 256, 256, 256, 64, 256, 256, 16, 256, 10, 256, 256, 53, 12};

	static List<String> rows() throws ReflectiveOperationException
	{
		Class<?> implementation = Class.forName("net.runelite.client.plugins.timetracking.farming.PatchImplementation");
		Method decode = implementation.getDeclaredMethod("forVarbitValue", int.class);
		decode.setAccessible(true);
		Class<?> stateClass = Class.forName("net.runelite.client.plugins.timetracking.farming.PatchState");
		Method produceMethod = stateClass.getDeclaredMethod("getProduce");
		Method stateMethod = stateClass.getDeclaredMethod("getCropState");
		Method stageMethod = stateClass.getDeclaredMethod("getStage");
		produceMethod.setAccessible(true);
		stateMethod.setAccessible(true);
		stageMethod.setAccessible(true);
		List<String> rows = new ArrayList<>();
		for (int kindIndex = 0; kindIndex < KINDS.length; kindIndex++)
		{
			String kind = KINDS[kindIndex];
			Object crop = implementation.getField(kind).get(null);
			int before = rows.size();
			for (int value = 0; value < 256; value++)
			{
				Object decoded = decode.invoke(crop, value);
				if (decoded == null)
				{
					continue;
				}
				Object produce = produceMethod.invoke(decoded);
				String name = (String) produce.getClass().getMethod("getName").invoke(produce);
				int stages = (Integer) produce.getClass().getMethod("getStages").invoke(produce);
				int minutes = (Integer) produce.getClass().getMethod("getTickrate").invoke(produce);
				rows.add(kind + "|" + value + "|" + stateMethod.invoke(decoded) + "|" + name
						 + "|" + stageMethod.invoke(decoded) + "|" + stages + "|" + minutes);
			}
			System.out.println(kind + ": " + (rows.size() - before) + " values");
			if (rows.size() - before != ROW_COUNTS[kindIndex])
			{
				throw new IllegalStateException("Collapsed farming decode for " + kind);
			}
		}
		return rows;
	}

	public static void main(String[] args) throws Exception
	{
		List<String> rows = rows();
		String header = "# Derived from RuneLite 1.13.4 PatchImplementation / Produce; BSD-2-Clause, see farming-LICENSE.txt.\n"
						+ "# crop|value|state|produce|stage|stages|growthMinutes\n";
		Files.write(Paths.get("src/main/resources/farming-crops.txt"),
					(header + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8));
		System.out.println("Wrote " + rows.size() + " decoded values for " + KINDS.length + " crop kinds; review the source version and diff before publishing.");
	}
}
