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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;

/**
 * Who this character is, as far as the map is concerned — and which RuneLite it plays from.
 *
 * <p>Two values with two lifetimes. The <b>member id</b> is a random UUID kept per RuneScape
 * profile, so each account is its own member of a group: two clients running two accounts are two
 * dots rather than one dot flickering between two places, and an alt's levels never arrive under
 * the main's name. The <b>installation key</b> is thirty-two random characters kept once in this
 * plugin's own settings and sent only on the snapshot post, where the map keeps it as a hash. It
 * is what tells the map that two characters are one RuneLite, so a reader pastes their RuneLite
 * code once and every account they log into joins the group and follows their browser.
 *
 * <p>Neither is derived from the account — not hashed from the username, not taken from an account
 * id — and that is the point: the map needs to tell members apart and link a reader's own
 * characters, and nothing more. Deriving either from the account would make it a stable identifier
 * for a person across every group they ever join, which is exactly the thing this feature is
 * arranged not to create.
 *
 * <p>Kept rather than regenerated per session so that a restart rejoins as the same dot instead of
 * accumulating ghosts in a friend's party list. A member id is filed under the installation key it
 * was minted with, so {@link #forget} — a new key — gives every character a new id at once,
 * including the ones not logged in, whose profiles this class cannot reach.
 */
@Singleton
public class MemberIdentity
{
	private static final String KEY = "memberId";
	private static final String KEY_INSTALLATION = "installationKey";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final ConfigStore settings;
	private final ConfigStore profile;

	@Inject
	private MemberIdentity(ConfigManager configManager)
	{
		this(ConfigStore.of(configManager), ConfigStore.forRuneScapeProfile(configManager));
	}

	/**
	 * The same identity, kept somewhere a test or the integration harness can hold — see
	 * {@link ConfigStore}. Package-private, because a seam is not an API.
	 */
	MemberIdentity(ConfigStore settings, ConfigStore profile)
	{
		this.settings = settings;
		this.profile = profile;
	}

	/** One store playing both parts: the harness has one character, and its scope is its own. */
	MemberIdentity(ConfigStore store)
	{
		this(store, store);
	}

	/**
	 * This character's member id, or null while no RuneScape profile is selected.
	 *
	 * <p>Null rather than a fresh UUID: before login, and for a tick or two after it, RuneLite has
	 * no profile to keep an id in, and minting one there would write it nowhere and mint a different
	 * one on the next call — a new member on every post. Every caller treats null as "not yet".
	 *
	 * <p>Synchronized because this is a read, a decision and a write, and two threads reach it. The
	 * game tick asks for the id to build a snapshot; the sidebar asks for it to request an invite.
	 * On the first call for a character those interleaving would mean two ids generated, the later
	 * write winning, and an invite asked for on behalf of a member the map is not being told about.
	 */
	@Nullable
	public synchronized String id()
	{
		String scope = profile.scopeKey();
		if (scope == null)
		{
			return null;
		}
		String tag = tag(installationKey());
		String existing = profile.getScoped(scope, KEY);
		if (existing != null && existing.startsWith(tag + ":") && existing.length() > tag.length() + 1)
		{
			return existing.substring(tag.length() + 1).trim();
		}
		String minted = UUID.randomUUID().toString();
		profile.setScoped(scope, KEY, tag + ":" + minted);
		return minted;
	}

	/**
	 * This RuneLite's key, minted on first use. Sent as {@code X-Companion-Installation} on the
	 * snapshot post and nowhere else; the map stores its SHA-256 and never sends it back.
	 */
	public synchronized String installationKey()
	{
		String held = settings.get(KEY_INSTALLATION);
		if (JoinCode.isWellFormedSecret(held))
		{
			return held;
		}
		StringBuilder minted = new StringBuilder(JoinCode.SECRET_LENGTH);
		for (int i = 0; i < JoinCode.SECRET_LENGTH; i++)
		{
			minted.append(JoinCode.ALPHABET.charAt(RANDOM.nextInt(JoinCode.ALPHABET.length())));
		}
		settings.set(KEY_INSTALLATION, minted.toString());
		return minted.toString();
	}

	/**
	 * Forgetting the identity is part of leaving a group cleanly: a new installation key, so every
	 * character's next id is new and the map has nothing to link the old ones to.
	 */
	public synchronized void forget()
	{
		settings.unset(KEY_INSTALLATION);
		String scope = profile.scopeKey();
		if (scope != null)
		{
			profile.unsetScoped(scope, KEY);
		}
	}

	/**
	 * Which installation key a stored id was minted under: eight hex characters of its SHA-256.
	 * Enough to tell a key from its replacement, and nothing a reader of the settings file could
	 * turn back into the key.
	 */
	private static String tag(String key)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(8);
			for (int i = 0; i < 4; i++)
			{
				hex.append(String.format("%02x", digest[i] & 0xff));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
		}
	}
}
