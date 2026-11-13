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

import com.google.gson.Gson;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;

/** The run collaborators for focused subscription tests which assemble the plugin without Guice. */
final class TestFarmRuns
{
	static FarmRunState attach(MapCompanionPlugin plugin)
	{
		CompanionLoop loop = TestLoop.in(plugin).loop;
		loop.slayerState = new SlayerState();
		FarmRunState state = new FarmRunState();
		DestinationRoute route = new DestinationRoute();
		HarnessGame game = new HarnessGame();
		ClientThread thread = new ClientThread()
		{
			@Override
			public void invokeLater(Runnable action)
			{
				action.run();
			}
		};
		FarmRunClient client = new FarmRunClient(new OkHttpClient(), new Gson(), game.client(), thread,
			loop.pairing, new MemberIdentity(new HarnessStore()), state, loop.destinationState, route);
		loop.farmRunState = state;
		loop.farmRunClient = client;
		loop.farmRunRoute = new FarmRunRoute(state, loop.destinationState, route, thread);
		return state;
	}

	static FarmRunState state(MapCompanionPlugin plugin)
	{
		return plugin.loop.farmRunState;
	}
}
