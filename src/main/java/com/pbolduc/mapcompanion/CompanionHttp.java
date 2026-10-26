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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import okhttp3.OkHttpClient;
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
