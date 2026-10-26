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

import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.RuneLite;

/**
 * Launches a RuneLite client with this plugin loaded, for developing it.
 *
 * <p>Not a test — it asserts nothing and JUnit never calls it. It lives under {@code src/test}
 * because that is where the client dependency is, and because a launcher that shipped inside the
 * plugin jar would be dead weight in everybody's install. This is the shape the Plugin Hub template
 * uses and the way essentially every plugin in the Hub is actually developed.
 *
 * <p>Run {@link #main} from an IDE with {@code -ea} enabled. RuneLite starts, this plugin appears in
 * the configuration list under its own name, and it can be turned on and watched against a real
 * account — which is the only way to check the parts of this repository that a unit test cannot
 * reach, since almost all of them are reads of live game state.
 *
 * <p>The one genuinely testable piece, the coordinate solver, has its own real test beside this
 * file. Everything else needs a client, and this is the client.
 */
public class MapCompanionPluginTest
{
	/**
	 * The varargs parameter is a generic array, which javac warns about at every call site and which
	 * no caller can avoid — the warning belongs to {@code loadBuiltin}'s signature, not to this line.
	 * Suppressed narrowly so that {@code -Xlint:all} stays worth reading, which is the whole reason
	 * this build turns it on.
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MapCompanionPlugin.class);
		RuneLite.main(args);
	}
}
