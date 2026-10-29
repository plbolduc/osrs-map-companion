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

/**
 * The handful of game variables this plugin reads, in one place.
 *
 * <p><b>Why these are here rather than inline.</b> This is the single most version-sensitive
 * surface in the plugin: the ids themselves are stable game data, but the constants that name them
 * have moved between {@code net.runelite.api.Varbits}, {@code VarPlayer}, and the generated
 * {@code gameval} classes across client releases. Collecting them means a client update touches one
 * file, and a reviewer can see the entire set of game variables this plugin reads without reading
 * the rest of it.
 *
 * <p><b>Each literal is asserted.</b> Literals let this repository build against a range of client
 * releases, and the price is that the compiler checks none of them. {@code VarbitIdsTest} compares
 * every one with its {@code net.runelite.api.gameval.VarPlayerID} constant, so a new id here gets
 * its line there in the same commit: the rule in CONTRIBUTING's <i>Rules a change must keep</i>.
 *
 * <p>A wrong one fails quietly by design: {@link SlayerTaskReader} returns null when it cannot
 * resolve a name, and the map shows no task rather than the wrong one. That quiet is why the
 * assertion is a test and not a step someone remembers before a release.
 */
final class VarPlayerIds
{
	/** How many of the current assignment are left. */
	static final int SLAYER_COUNT = 394;

	/** Which creature is assigned. 98 is the sentinel meaning "a boss, named separately". */
	static final int SLAYER_TARGET = 395;

	/** The area the assignment is restricted to, when a master gives one. 0 for none. */
	static final int SLAYER_AREA = 2096;

	private VarPlayerIds()
	{
	}
}
