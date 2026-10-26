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

import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;

/**
 * The two lines of key-value storage this plugin keeps its own state in.
 *
 * <p>Four classes here write something that is nobody's business but this plugin's — the pairing
 * and the groups it remembers, the id this installation is known by, the kill log, and the farming
 * patches it has walked past. All four wrote it the same way, through {@link ConfigManager} with
 * this plugin's own config group, and all four were therefore unreachable without a running
 * client: {@code ConfigManager} has no constructor anything outside RuneLite can call, so a test
 * or a harness that wanted one of these objects could not have one at all.
 *
 * <p>Naming those three calls is what makes the state they hold exercisable. {@link Pairing}
 * already did this for itself and said why; this is that seam moved out to where the other three
 * can use it rather than each growing its own, because a second interface of the same shape is a
 * second thing to keep in step.
 *
 * <p>Every implementer stores under {@link MapCompanionConfig#GROUP}, so a key here is a key
 * there. Package-private on purpose: it is a seam, not an API, and nothing outside this plugin has
 * any business holding one.
 */
interface ConfigStore
{
	/** The stored value, or null when nothing has been written under that key. */
	@Nullable
	String get(String key);

	void set(String key, String value);

	void unset(String key);

	/** Null means no character is selected; the empty string is the ordinary settings store. */
	@Nullable
	default String scopeKey()
	{
		return "";
	}

	/** A compound observation read/write pins its owner even if the client switches meanwhile. */
	@Nullable
	default String getScoped(String scope, String key)
	{
		return get(key);
	}

	default void setScoped(String scope, String key, String value)
	{
		set(key, value);
	}

	default void unsetScoped(String scope, String key)
	{
		unset(key);
	}

	/**
	 * Character observations belong to RuneScape's save profile, including its league/beta variant.
	 * Pairing remains in ordinary settings. Using this adapter only for observations prevents a
	 * different account on the same RuneLite settings profile inheriting a previous account's log.
	 */
	static ConfigStore forRuneScapeProfile(ConfigManager configManager)
	{
		return new ConfigStore()
		{
			@Override
			public String scopeKey()
			{
				return configManager.getRSProfileKey();
			}

			@Override
			public String get(String key)
			{
				return getScoped(scopeKey(), key);
			}

			@Override
			public void set(String key, String value)
			{
				setScoped(scopeKey(), key, value);
			}

			@Override
			public void unset(String key)
			{
				unsetScoped(scopeKey(), key);
			}

			@Override
			public String getScoped(String scope, String key)
			{
				return scope == null ? null : configManager.getConfiguration(MapCompanionConfig.GROUP, scope, key);
			}

			@Override
			public void setScoped(String scope, String key, String value)
			{
				if (scope != null)
				{
					configManager.setConfiguration(MapCompanionConfig.GROUP, scope, key, value);
				}
			}

			@Override
			public void unsetScoped(String scope, String key)
			{
				if (scope != null)
				{
					configManager.unsetConfiguration(MapCompanionConfig.GROUP, scope, key);
				}
			}
		};
	}

	/** RuneLite's settings, which is where all of this lives for real. */
	static ConfigStore of(ConfigManager configManager)
	{
		return new ConfigStore()
		{
			@Nullable
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(MapCompanionConfig.GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				configManager.setConfiguration(MapCompanionConfig.GROUP, key, value);
			}

			@Override
			public void unset(String key)
			{
				configManager.unsetConfiguration(MapCompanionConfig.GROUP, key);
			}
		};
	}
}
