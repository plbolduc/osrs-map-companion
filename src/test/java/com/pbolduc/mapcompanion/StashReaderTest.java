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

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** A script failure has no construction verdict; position posts do not re-run the scripts. */
public class StashReaderTest
{
	@Test
	public void cachesOnlyWithinTheSettledSession()
	{
		AtomicInteger calls = new AtomicInteger();
		int[] stack = {0};
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("runScript"))
				{
					int count = calls.getAndIncrement();
					stack[0] = count == 0 ? 0 : count == 1 ? 2 : 1;
					return null;
				}
				if (method.getName().equals("getIntStack")) return stack;
				throw new UnsupportedOperationException(method.getName());
			});
		StashReader reader = new StashReader(client);
		reader.onSessionResumed(0);
		assertNull(reader.read(14_999));
		Map<Integer, Boolean> result = reader.read(15_000);
		assertEquals(Boolean.FALSE, result.get(STASHUnit.values()[0].getObjectId()));
		assertFalse(result.containsKey(STASHUnit.values()[1].getObjectId()));
		assertEquals(Boolean.TRUE, result.get(STASHUnit.values()[2].getObjectId()));
		assertSame(result, reader.read(74_999));
		assertEquals(STASHUnit.values().length, calls.get());
		assertNotSame(result, reader.read(75_000));
		reader.onSessionEnded();
		assertNull(reader.read(90_000));
	}
}
