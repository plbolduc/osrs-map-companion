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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** Server consent and credentials share the request's destination, even when codes collide. */
public class ConnectionConsentTest
{
	/** The member every secret here is filed for; a member is one character. */
	private static final String MEMBER = "member-1";

	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	@Test
	public void cancellingConnectChangesNothingAndLeavesRequestsPaused()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		AtomicInteger writes = new AtomicInteger();
		store.onChanged(key -> writes.incrementAndGet());
		assertFalse(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> false));
		assertEquals(0, writes.get());
		assertEquals("", pairing.code());
		assertNull(pairing.suffix());
		assertFalse(pairing.hasServerConsent());
	}

	@Test
	public void approvalIsRememberedButEachChangedDestinationNeedsConnect()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		AtomicInteger dialogs = new AtomicInteger();
		assertTrue(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> { dialogs.incrementAndGet(); return true; }));
		assertTrue(pairing.hasServerConsent());
		assertTrue(pairing.connect(JoinCode.parse("DEF567"), () -> { fail("already disclosed this server"); return false; }));
		store.set(MapCompanionConfig.KEY_RELAY_URL, "http://localhost:3000/");
		assertFalse(pairing.hasServerConsent());
		assertFalse(pairing.connect(JoinCode.parse("ABC234"), () -> false));
		assertEquals("DEF567", pairing.code());
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> { dialogs.incrementAndGet(); return true; }));
		assertTrue(pairing.hasServerConsent());
		store.set(MapCompanionConfig.KEY_RELAY_URL, "https://OSRS-MAP.com:443/");
		assertFalse(pairing.hasServerConsent());
		assertTrue(pairing.connect(JoinCode.parse("ABC234"), () -> { fail("same disclosed server after normalization"); return false; }));
		assertTrue(pairing.hasServerConsent());
		assertEquals(2, dialogs.get());
	}

	@Test
	public void credentialsNamesAndSuffixesNeverCrossServersWithTheSameCode()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> true);
		pairing.rememberSecret("ABC234", MEMBER, SECRET);
		pairing.rememberName("ABC234", "Sunday raids");
		store.set(MapCompanionConfig.KEY_RELAY_URL, "http://localhost:3000");
		assertNull(pairing.secret(MEMBER));
		assertNull(pairing.name("ABC234"));
		assertNull(pairing.suffix(pairing.serverUrl(), "ABC234"));
		String other = SECRET.substring(1) + SECRET.charAt(0);
		pairing.connect(JoinCode.parse("ABC234-ABCDEFGH"), () -> true);
		pairing.rememberSecret("ABC234", MEMBER, other);
		pairing.rememberName("ABC234", "Local test");
		assertEquals(other, pairing.secret(MEMBER));
		assertEquals(SECRET, pairing.secret(MapCompanionConfig.DEFAULT_MAP_URL, "ABC234", MEMBER));
		assertNull(pairing.suffix(MapCompanionConfig.DEFAULT_MAP_URL, "ABC234"));
		store.set(MapCompanionConfig.KEY_RELAY_URL, MapCompanionConfig.DEFAULT_MAP_URL);
		assertEquals(SECRET, pairing.secret(MEMBER));
		assertEquals("Sunday raids", pairing.name("ABC234"));
	}

	@Test
	public void aReplyPinsItsServerEvenWhenSettingsChangeBeforeTheTickStoresIt()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		pairing.join(JoinCode.parse("ABC234"));
		MapCompanionPlugin plugin = new MapCompanionPlugin();
		plugin.pairing = pairing;
		plugin.noteIssuedSecret(MapCompanionConfig.DEFAULT_MAP_URL, "ABC234", MEMBER, SECRET);
		plugin.noteGroupName(MapCompanionConfig.DEFAULT_MAP_URL, "ABC234", "Sunday raids");
		store.set(MapCompanionConfig.KEY_RELAY_URL, "http://localhost:3000");
		plugin.storeIssuedSecret();
		plugin.storeToldName();
		assertNull(pairing.secret(MEMBER));
		assertNull(pairing.name("ABC234"));
		store.set(MapCompanionConfig.KEY_RELAY_URL, MapCompanionConfig.DEFAULT_MAP_URL);
		assertEquals(SECRET, pairing.secret(MEMBER));
		assertEquals("Sunday raids", pairing.name("ABC234"));
	}

	@Test
	public void editingTheServerInsideTheModalCannotApproveAnotherServer()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		assertFalse(pairing.connect(JoinCode.parse("ABC234"), () ->
		{
			store.set(MapCompanionConfig.KEY_RELAY_URL, "http://localhost:3000");
			return true;
		}));
		assertEquals("", pairing.code());
		assertFalse(pairing.hasServerConsent());
		assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
	}

	@Test
	public void olderCredentialsAreNotAssignedToAnUnknownDestination()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		pairing.join(JoinCode.parse("ABC234"));
		store.set("memberSecrets", "{\"ABC234\":\"" + SECRET + "\"}");
		assertNull(pairing.secret(MEMBER));
		assertFalse(pairing.hasServerConsent());
		pairing.leave();
		assertNull(store.get("memberSecrets"));
	}

	@Test
	public void aConfigListenerCannotChangeTheDestinationBeingApproved()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		store.onChanged(key ->
		{
			if (MapCompanionConfig.KEY_PAIRING_CODE.equals(key))
			{
				store.set(MapCompanionConfig.KEY_RELAY_URL, "http://localhost:3000");
			}
		});
		assertFalse(pairing.connect(JoinCode.parse("ABC234-K7PQ2M3X"), () -> true));
		assertFalse(pairing.hasServerConsent());
		assertNull(store.get(Pairing.KEY_SERVER_CONSENT));
		assertNull(pairing.suffix("http://localhost:3000", "ABC234"));
		assertEquals("K7PQ2M3X", pairing.suffix(MapCompanionConfig.DEFAULT_MAP_URL, "ABC234"));
	}

	@Test
	public void malformedServersAndChangedDisclosuresCannotCarryApproval()
	{
		HarnessStore store = new HarnessStore();
		Pairing pairing = new Pairing(store, new Gson());
		store.set(MapCompanionConfig.KEY_RELAY_URL, "https://user:password@example.test");
		assertFalse(pairing.connect(JoinCode.parse("ABC234"), () -> { fail("invalid address"); return true; }));
		assertNull(Pairing.normalizeServer("file:///tmp/map"));
		assertNull(Pairing.normalizeServer("https://example.test?key=value"));
		/** The code and the secrets are headers, so plain http is refused anywhere but this machine. */
		assertNull(Pairing.normalizeServer("http://example.test"));
		assertNull(Pairing.normalizeServer("http://osrs-map.com"));
		assertEquals("http://127.0.0.1:3000", Pairing.normalizeServer("http://127.0.0.1:3000/"));
		assertEquals("http://[::1]:3000", Pairing.normalizeServer("http://[::1]:3000"));
		assertNotEquals(Pairing.normalizeServer("http://localhost:3000"), Pairing.normalizeServer("http://localhost:3001"));
		assertNotEquals(Pairing.normalizeServer("https://example.test/a"), Pairing.normalizeServer("https://example.test/b"));
		store.set(MapCompanionConfig.KEY_RELAY_URL, MapCompanionConfig.DEFAULT_MAP_URL);
		pairing.connect(JoinCode.parse("ABC234"), () -> true);
		store.set(Pairing.KEY_SERVER_CONSENT, "{\"https://osrs-map.com\":\"0\"}");
		assertFalse(pairing.hasServerConsent());
		assertFalse(pairing.connect(JoinCode.parse("ABC234"), () -> false));
	}
}
