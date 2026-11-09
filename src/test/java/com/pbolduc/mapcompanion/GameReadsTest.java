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
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Every number in game-reads.txt is the gameval constant the row names beside it. */
public class GameReadsTest
{
	private static final Map<String, Class<?>[]> CLASSES = new HashMap<>();

	static
	{
		CLASSES.put("daily", new Class<?>[] {VarbitID.class});
		CLASSES.put("charting", new Class<?>[] {VarPlayerID.class});
		CLASSES.put("mortimer", new Class<?>[] {VarbitID.class});
	}

	@Test
	public void everyIdIsTheConstantItIsNamedFor() throws Exception
	{
		int checked = 0;
		for (String kind : CLASSES.keySet())
		{
			for (String[] row : GameReads.rows(kind))
			{
				for (int cell = 1; cell < row.length; cell++)
				{
					if (!row[cell].contains("="))
					{
						continue;
					}
					int[] ids = GameReads.ids(row[cell]);
					String[] named = row[cell].split(",");
					for (int i = 0; i < ids.length; i++)
					{
						String name = named[i].substring(0, named[i].indexOf('='));
						assertEquals(kind + " " + name, constant(kind, name), ids[i]);
						checked++;
					}
				}
			}
		}
		assertTrue("the table was read", checked > 0);
	}

	@Test
	public void theDailiesAreTheMapsEightThatHaveAClaimVarbit()
	{
		assertEquals(8, GameReads.rows("daily").size());
		for (String[] row : GameReads.rows("daily"))
		{
			assertFalse(row[0], row[0].equals("miscellania-kingdom"));
		}
	}

	private static int constant(String kind, String name) throws Exception
	{
		for (Class<?> type : CLASSES.get(kind))
		{
			try
			{
				return type.getField(name).getInt(null);
			}
			catch (NoSuchFieldException e)
			{
				// Not in this class; the next one may hold it.
			}
		}
		assertNotNull("no gameval constant " + name, null);
		return -1;
	}
}
