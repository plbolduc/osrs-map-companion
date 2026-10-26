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
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.*;

/** Login zeroes and unrecognised encodings must never withdraw a route. */
public class TravelUnlockReaderTest
{
	@Test
	public void settlesEachSessionAndReadsOnlyKnownValues()
	{
		Map<Integer, Integer> bits = new HashMap<>();
		bits.put(11175, 0);
		bits.put(11176, 1);
		bits.put(11177, 9);
		bits.put(2187, 4);
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("getVarbitValue"))
				{
					Integer value = bits.get((Integer) args[0]);
					if (value == null) throw new IllegalStateException("unreadable");
					return value;
				}
				if (method.getName().equals("getVarpValue")) return 256;
				throw new UnsupportedOperationException(method.getName());
			});
		TravelUnlockReader reader = new TravelUnlockReader(client);
		assertNull(reader.read(100_000));
		reader.onSessionResumed(100_000);
		assertNull(reader.house(114_999));
		reader.onSessionResumed(114_000); // Zone loading must not restart the clock.
		Map<String, Boolean> states = reader.read(115_000);
		assertEquals(Boolean.FALSE, states.get("ates-darkfrost"));
		assertEquals(Boolean.TRUE, states.get("ates-twilight-temple"));
		assertFalse(states.containsKey("ates-ralos-rise"));
		assertFalse(states.containsKey("ates-kastori"));
		assertEquals(Boolean.TRUE, states.get("quetzal-fortis-colosseum"));
		assertEquals(Boolean.FALSE, states.get("quetzal-kastori"));
		assertEquals("rellekka", reader.house(115_000));
		reader.onSessionEnded();
		assertNull(reader.read(200_000));
		reader.onSessionResumed(200_000);
		assertNull(reader.read(214_999));
		assertNotNull(reader.read(215_000));
	}

	@Test
	public void houseNumbersFollowTheGameRatherThanTheSettingsMenuOrder()
	{
		assertEquals("rellekka", TravelUnlockReader.houseFrom(4));
		assertEquals("hosidius", TravelUnlockReader.houseFrom(7));
		assertEquals("aldarin", TravelUnlockReader.houseFrom(9));
		assertNull(TravelUnlockReader.houseFrom(0));
		assertNull(TravelUnlockReader.houseFrom(10));
		assertNull(TravelUnlockReader.houseFrom(-1));
	}
}
