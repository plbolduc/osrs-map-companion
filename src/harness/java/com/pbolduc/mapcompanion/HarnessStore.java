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

import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * RuneLite's settings, in a map — and the event bus that goes with them.
 *
 * <p>Two things happen when this plugin writes a setting on a real client, and a fake that did only
 * the first would be a fake that quietly changed the behaviour under test. The value is stored, and
 * a {@code ConfigChanged} is posted; {@link MapCompanionPlugin#onConfigChanged} is where a new
 * pairing code tears down the stream, resets the relay and throws away the suffix and the outbox
 * that belonged to the last group. {@link Pairing#join} depends on that ordering explicitly — it
 * writes the code first <em>because</em> the write clears a stale suffix — so the notification is
 * synchronous here, as it is there.
 *
 * <p>Nothing is written to disk. The harness is one process per plugin installation and each one
 * starts with nothing, which is the state a fresh install is in.
 */
final class HarnessStore implements ConfigStore
{
	private final Map<String, String> values = new LinkedHashMap<>();

	/** What stands in for the event bus: told the key, told nothing else. */
	private volatile Consumer<String> onChanged = key ->
	{
	};

	void onChanged(Consumer<String> listener)
	{
		this.onChanged = listener;
	}

	@Nullable
	@Override
	public synchronized String get(String key)
	{
		return values.get(key);
	}

	@Override
	public void set(String key, String value)
	{
		synchronized (this)
		{
			values.put(key, value);
		}
		onChanged.accept(key);
	}

	@Override
	public void unset(String key)
	{
		synchronized (this)
		{
			values.remove(key);
		}
		onChanged.accept(key);
	}
}
