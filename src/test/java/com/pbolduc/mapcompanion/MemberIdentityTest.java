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
 * leaving reaches every character at once, including the ones not logged in.
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
