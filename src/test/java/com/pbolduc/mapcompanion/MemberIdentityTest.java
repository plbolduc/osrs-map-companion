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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A member is one character and an installation is one RuneLite.
 *
 * <p>What these pin is the two lifetimes: a member id per RuneScape profile, so an alt is never
 * the main's dot, and one installation key, so the map can link both to one browser; and that
 * leaving reaches every character at once, including the ones not logged in. And where the two
 * are kept: the key in one RuneLite profile's settings, the ids in a store every RuneLite profile
 * shares, so that two profiles do not take turns minting one character's id.
 */
public class MemberIdentityTest
{
	/** Ordinary settings plus RuneScape profiles, with the selected profile settable. */
	private static final class Profiles implements ConfigStore
	{
		final Map<String, String> settings = new HashMap<>();
		final Map<String, String> scoped = new HashMap<>();
		@Nullable
		String selected;

		final ConfigStore global = new ConfigStore()
		{
			@Override public String get(String key) { return settings.get(key); }
			@Override public void set(String key, String value) { settings.put(key, value); }
			@Override public void unset(String key) { settings.remove(key); }
		};

		@Override public String scopeKey() { return selected; }
		@Override public String get(String key) { return getScoped(selected, key); }
		@Override public void set(String key, String value) { setScoped(selected, key, value); }
		@Override public void unset(String key) { unsetScoped(selected, key); }
		@Override public String getScoped(String scope, String key) { return scope == null ? null : scoped.get(scope + "." + key); }
		@Override public void setScoped(String scope, String key, String value) { if (scope != null) scoped.put(scope + "." + key, value); }
		@Override public void unsetScoped(String scope, String key) { if (scope != null) scoped.remove(scope + "." + key); }
	}

	/** One RuneLite profile's own settings, which is where its installation key is. */
	private static ConfigStore settings()
	{
		Map<String, String> held = new HashMap<>();
		return new ConfigStore()
		{
			@Override public String get(String key) { return held.get(key); }
			@Override public void set(String key, String value) { held.put(key, value); }
			@Override public void unset(String key) { held.remove(key); }
		};
	}

	/**
	 * RuneLite keeps a RuneScape profile's values in one store that all its own profiles share, and
	 * this plugin's settings in the profile that is open. So a player with the plugin on two RuneLite
	 * profiles is two settings stores, each with its own installation key, over one place for a
	 * character's id. The id was one slot a character, accepted only under the open profile's key:
	 * each switch minted it again over the other profile's, the map met a new member every time, and
	 * the secret it had issued the old one stopped matching.
	 */
	@Test
	public void twoRuneLiteProfilesEachKeepTheirOwnIdForOneCharacter()
	{
		Profiles shared = new Profiles();
		MemberIdentity first = new MemberIdentity(settings(), shared);
		MemberIdentity second = new MemberIdentity(settings(), shared);
		shared.selected = "main";

		String onFirst = first.id();
		String onSecond = second.id();
		assertNotEquals("each RuneLite profile is an installation, and a member, of its own", onFirst, onSecond);

		for (int round = 0; round < 3; round++)
		{
			assertEquals("back on the first profile it is who it was", onFirst, first.id());
			assertEquals("and on the second", onSecond, second.id());
		}
		assertEquals("two ids for the character however often the player switches", 2, shared.scoped.size());
	}

	/** Leave group on one RuneLite profile replaces that profile's ids, and no other's. */
	@Test
	public void leavingOnOneRuneLiteProfileLeavesTheOthersIdsAlone()
	{
		Profiles shared = new Profiles();
		MemberIdentity first = new MemberIdentity(settings(), shared);
		MemberIdentity second = new MemberIdentity(settings(), shared);
		shared.selected = "alt";
		String altOnFirst = first.id();
		shared.selected = "main";
		String onFirst = first.id();
		String onSecond = second.id();

		first.forget();

		assertNotEquals(onFirst, first.id());
		assertEquals("the other profile never left", onSecond, second.id());
		shared.selected = "alt";
		assertNotEquals("the alt was not logged in and is forgotten anyway", altOnFirst, first.id());
	}

	/**
	 * An id stored before its key named the installation, as "tag:id" in the character's one slot,
	 * is still the character's on the RuneLite profile whose key it was minted under, so an update
	 * re-mints nobody. Any other profile finds another's tag there, leaves it, and keeps its own.
	 */
	@Test
	public void anIdFromBeforeTheUpdateIsKeptByTheProfileItWasMintedOn() throws Exception
	{
		Profiles shared = new Profiles();
		MemberIdentity first = new MemberIdentity(settings(), shared);
		MemberIdentity second = new MemberIdentity(settings(), shared);
		shared.selected = "main";
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(first.installationKey().getBytes(StandardCharsets.UTF_8));
		String tag = String.format("%02x%02x%02x%02x", digest[0], digest[1], digest[2], digest[3]);
		shared.scoped.put("main.memberId", tag + ":5f0c1d3e-0000-4000-8000-000000000001");

		assertNotEquals("another profile's id is not this one's", "5f0c1d3e-0000-4000-8000-000000000001", second.id());
		assertEquals("5f0c1d3e-0000-4000-8000-000000000001", first.id());
		assertEquals("and stays the first profile's after the second has been played", "5f0c1d3e-0000-4000-8000-000000000001", first.id());
		assertEquals("the old slot is left as it was found", tag + ":5f0c1d3e-0000-4000-8000-000000000001",
			shared.scoped.get("main.memberId"));
	}

	@Test
	public void thereIsNobodyToBeBeforeACharacterIsSelected()
	{
		Profiles profiles = new Profiles();
		MemberIdentity identity = new MemberIdentity(profiles.global, profiles);
		assertNull(identity.id());
		assertNull("and asking twice still mints nothing", identity.id());
		assertTrue(profiles.scoped.isEmpty());
	}

	@Test
	public void eachCharacterIsItsOwnMemberAndKeepsIt()
	{
		Profiles profiles = new Profiles();
		MemberIdentity identity = new MemberIdentity(profiles.global, profiles);
		profiles.selected = "main";
		String main = identity.id();
		profiles.selected = "alt";
		String alt = identity.id();
		assertNotEquals(main, alt);
		profiles.selected = "main";
		assertEquals("a restart rejoins as the same dot", main, identity.id());
	}

	@Test
	public void oneRuneLiteHasOneKeyOfTheShapeTheMapAccepts()
	{
		Profiles profiles = new Profiles();
		MemberIdentity identity = new MemberIdentity(profiles.global, profiles);
		String key = identity.installationKey();
		assertTrue(JoinCode.isWellFormedSecret(key));
		profiles.selected = "alt";
		assertEquals("the key is the RuneLite's, not the character's", key, identity.installationKey());
	}

	@Test
	public void leavingGivesEveryCharacterANewIdIncludingTheOnesNotLoggedIn()
	{
		Profiles profiles = new Profiles();
		MemberIdentity identity = new MemberIdentity(profiles.global, profiles);
		profiles.selected = "alt";
		String alt = identity.id();
		profiles.selected = "main";
		String main = identity.id();
		String key = identity.installationKey();

		identity.forget();

		assertNotEquals(key, identity.installationKey());
		assertNotEquals(main, identity.id());
		profiles.selected = "alt";
		assertNotEquals("the alt was not logged in and is forgotten anyway", alt, identity.id());
	}
}
