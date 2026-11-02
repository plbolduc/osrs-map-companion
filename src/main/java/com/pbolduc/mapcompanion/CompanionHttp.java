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
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The one way this plugin derives an HTTP client from RuneLite's.
 *
 * <p>Consent is given to one server address ({@link Pairing}), and every request to it carries the
 * group code, the member secret and the installation key in headers of this plugin's own. OkHttp
 * strips only {@code Authorization} when a redirect changes host, so a following client would
 * hand those headers to whatever host a misconfigured proxy or a taken-over subdomain named — a
 * snapshot post answered with 302 becomes a GET that still carries them — and it would follow
 * https down to plain http to do it.
 * The map never redirects these paths, so refusing to follow costs nothing: a 3xx arrives as an
 * unsuccessful reply and takes the ordinary retry path.
 *
 * <p>Derived with {@code newBuilder()}, which keeps RuneLite's dispatcher and connection pool; the
 * Hub asks plugins not to build clients of their own.
 */
final class CompanionHttp
{
	/**
	 * The most any reply from the map is read into memory. A group's reply is a few kilobytes and
	 * the star feed under a hundred; this is far above both and far below what a server that never
	 * stopped sending could otherwise make the client hold.
	 */
	static final int MAX_REPLY_BYTES = 1_048_576;

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private CompanionHttp()
	{
	}

	static OkHttpClient pinnedToServer(OkHttpClient shared)
	{
		/* Already refusing: keep the instance, since a copy would drop anything it overrides. */
		if (!shared.followRedirects() && !shared.followSslRedirects())
		{
			return shared;
		}
		return shared.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
	}

	/**
	 * A request to the map with the plugin's User-Agent. Every client builds from here, so what
	 * identifies the plugin is written once.
	 */
	static Request.Builder request(String url)
	{
		return new Request.Builder().header("User-Agent", CompanionVersion.USER_AGENT).url(url);
	}

	static Request.Builder request(HttpUrl url)
	{
		return new Request.Builder().header("User-Agent", CompanionVersion.USER_AGENT).url(url);
	}

	/** The same, signed as a member of the group: its code, the member id and its secret. */
	static Request.Builder member(String url, String code, String member, String secret)
	{
		return signed(request(url), code, member, secret);
	}

	static Request.Builder member(HttpUrl url, String code, String member, String secret)
	{
		return signed(request(url), code, member, secret);
	}

	private static Request.Builder signed(Request.Builder request, String code, String member, String secret)
	{
		return request
			.header("X-Companion-Code", code)
			.header("X-Companion-Member", member)
			.header("X-Companion-Member-Secret", secret);
	}

	/** A JSON body, as every client sends one. */
	static RequestBody json(Gson gson, Object body)
	{
		return RequestBody.create(JSON, gson.toJson(body));
	}

	/**
	 * Sends a call and hands its failure or its response to the two given, on OkHttp's thread as
	 * an anonymous Callback would. Each client's own code is what it was; only the class around it
	 * is gone.
	 */
	static void enqueue(Call call, BiConsumer<Call, IOException> failed, BiConsumer<Call, Response> answered)
	{
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				failed.accept(call, error);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				answered.accept(call, response);
			}
		});
	}

	/**
	 * The sentence a refusal's body carries as {"error": "..."}, tidied and capped at {@code max}, or
	 * null when it carries none or is not JSON. The map wrote it, so it is plain text and nothing
	 * more. The caller reads the body, so a body too large to read fails where it always failed.
	 */
	@Nullable
	static String said(Gson gson, String body, int max)
	{
		try
		{
			JsonObject refusal = gson.fromJson(body, JsonObject.class);
			JsonElement error = refusal == null ? null : refusal.get("error");
			return error != null && error.isJsonPrimitive() && error.getAsJsonPrimitive().isString()
				? RouteWire.text(MapPin.plain(error.getAsString()), max) : null;
		}
		catch (RuntimeException unreadable)
		{
			return null;
		}
	}

	/** The body as text, refusing one longer than {@code limit} bytes before it is all in memory. */
	static String readBounded(@Nullable ResponseBody body, int limit) throws IOException
	{
		if (body == null || body.contentLength() > limit)
		{
			throw new IOException("Response too large");
		}
		try (InputStream stream = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream())
		{
			byte[] buffer = new byte[4096];
			int read;
			while ((read = stream.read(buffer)) != -1)
			{
				if (output.size() + read > limit)
				{
					throw new IOException("Response too large");
				}
				output.write(buffer, 0, read);
			}
			return new String(output.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
