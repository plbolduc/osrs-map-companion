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
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A relay process owns its in-memory counters. A restart starts them again.
 *
 * <p>The random public epoch changes only when the relay's memory is recreated. Retired epochs
 * stay rejected, so a delayed heartbeat cannot switch a receiver back after the new stream.
 * This is a lifecycle marker, never a credential or an account identifier.
 */
final class DirectiveEpoch
{
	enum Seen { INVALID, SAME, NEW }
	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{16,48}");
	private String current;
	private final Set<String> retired = new LinkedHashSet<>();

	@Nullable
	static String clean(@Nullable String value)
	{
		return value != null && FORMAT.matcher(value).matches() ? value : null;
	}

	Seen accept(@Nullable String epoch)
	{
		if (clean(epoch) == null || retired.contains(epoch))
		{
			return Seen.INVALID;
		}
		if (epoch.equals(current))
		{
			return Seen.SAME;
		}
		if (current != null)
		{
			retired.add(current);
		}
		while (retired.size() > 64)
		{
			retired.remove(retired.iterator().next());
		}
		current = epoch;
		return Seen.NEW;
	}

	void clear()
	{
		current = null;
		retired.clear();
	}

	/** Consume a malformed field without losing the reply's pairing secret or acknowledgements. */
	public static final class Lenient implements TypeAdapterFactory
	{
		@Override
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			if (type.getRawType() != String.class)
			{
				return null;
			}
			TypeAdapter<JsonElement> whole = gson.getAdapter(JsonElement.class);
			return (TypeAdapter<T>) new TypeAdapter<String>()
			{
				@Override
				public void write(JsonWriter out, String value) throws IOException
				{
					out.value(value);
				}

				@Override
				public String read(JsonReader in) throws IOException
				{
					JsonElement value = whole.read(in);
					return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
						   ? clean(value.getAsString()) : null;
				}
			};
		}
	}
}
