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

import java.util.ArrayList;
import java.util.List;

/**
 * The rows of game-reads.txt, the table of game variables the progress readers read.
 *
 * <p>A table rather than constants in each reader because the Plugin Hub counts the source and not
 * its resources; the file names every id's gameval constant beside its number, and its test holds
 * the two together.
 */
final class GameReads
{
	private GameReads()
	{
	}

	/** Every row of one kind, split on its bars, without the kind. */
	static List<String[]> rows(String kind)
	{
		List<String[]> rows = new ArrayList<>();
		for (String line : Resources.lines("/game-reads.txt"))
		{
			String[] row = line.split("\\|");
			if (row[0].equals(kind))
			{
				String[] rest = new String[row.length - 1];
				System.arraycopy(row, 1, rest, 0, rest.length);
				rows.add(rest);
			}
		}
		return rows;
	}

	/** The numbers of a NAME=id,NAME=id cell, in order. */
	static int[] ids(String cell)
	{
		String[] named = cell.split(",");
		int[] ids = new int[named.length];
		for (int i = 0; i < named.length; i++)
		{
			ids[i] = Integer.parseInt(named[i].substring(named[i].indexOf('=') + 1));
		}
		return ids;
	}
}
