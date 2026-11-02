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
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * The plugin's own way into a group, its way out to the map as the player, and the group's name:
 * <i>Connect</i>, <i>Join</i> with a pasted invite, <i>Open the map</i> and <i>Rename group</i>.
 *
 * <p><b>Why the plugin asks for a group at all.</b> Making a group and joining one both used to
 * happen in a browser: the map's panel made the group and handed the plugin a RuneLite code to
 * paste. This asks the map for that same code, so everything after the answer is the path a pasted
 * code already takes. The code is kept by {@link Pairing}, its suffix is sent until the map says
 * {@code claimed}, and the map issues the member secret.
 *
 * <ul>
 *   <li><b>Connect</b> — {@code POST /api/companion/connect} with nothing in it. The map makes a
 *       group whose maker is this RuneLite, and names it as it names a group nobody has named.
 *   <li><b>Join</b> — the same address with the group's code and the invite's token out of a pasted
 *       link. The map spends one use of the invite.
 *   <li><b>Open the map</b> — {@code POST /api/companion/link} under the member's own headers. The
 *       map answers with a one-time link that opens the map as this member, and as the group's owner
 *       when this RuneLite made the group. The token is in the link's fragment, which no browser
 *       sends to a server. On the login screen the member is any character of this RuneLite the
 *       group has issued a secret to.
 *   <li><b>Rename group</b> — {@code PATCH /api/companion/group} with the new name, under the same
 *       headers, for a member the map has said may ({@code groupOwner}): the group's maker, or a
 *       member bound to an owner's browser. A group made by Connect is "Our group" until then.
 * </ul>
 *
 * <p><b>What it sends.</b> The installation key the snapshot already carries, so the map knows which
 * RuneLite made or joined a group, and on Join the code and token the player pasted. Open the map
 * sends the code, member id and secret every member request sends, and Rename group sends them with
 * the new name. No character data: a group is made before anything is shared, and the sharing
 * choices are offered after.
 *
 * <p><b>When.</b> Only on a press, and one at a time. On the map the plugin was installed for, the
 * install warning was the notice; a different map address is asked about by name first. Nothing is
 * retried: a refusal is a sentence for the player, and a link that cannot be had opens the plain map.
 *
 * <p><b>Threads.</b> Presses are Swing's; the call and its answer are OkHttp's. The answer is handed
 * to the caller, which takes it back to Swing.
 */
@Singleton
final class ConnectClient
{
	static final String CONNECT = "/api/companion/connect";
	static final String LINK = "/api/companion/link";
	static final String GROUP = "/api/companion/group";
	private static final String INSTALLATION_HEADER = "X-Companion-Installation";

	static final String UNREACHABLE = Words.of("connect.unreachable");
	static final String BUSY = Words.of("connect.busy");
	static final String FAILED = Words.of("connect.failed");
	static final String ONE_AT_A_TIME = "Still waiting for the map.";
	/** The longest sentence the map is trusted to have written; anything longer is not one of its own. */
	static final int MAX_SENTENCE = 200;

	/** What a press came back with: a code to keep as if pasted, or a sentence for the player. */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Outcome
	{
		@Nullable
		final JoinCode joined;
		/** The group's name, as the map spelled it, or null for a group it has not named. */
		@Nullable
		final String name;
		@Nullable
		final String message;

		static Outcome joined(JoinCode joined, @Nullable String name)
		{
			return new Outcome(joined, name, null);
		}

		static Outcome refused(String message)
		{
			return new Outcome(null, null, message);
		}

		/** A rename the map took: the name as it now has it, and no code to keep. */
		static Outcome named(String name)
		{
			return new Outcome(null, name, null);
		}
	}

	/** What {@code /api/companion/connect} reads: nothing, or a code and the token beside it. */
	static final class Ask
	{
		String code;
		String invite;
	}

	/** What a rename sends. From RuneLite the map takes a group's name and nothing else. */
	static final class Rename
	{
		String name;
	}

	/** And what it answers. {@code runeliteCode} is absent when the map needs no ticket. */
	static final class Answer
	{
		String code;
		String name;
		String runeliteCode;
	}

	/** {@code /api/companion/link}'s answer. */
	static final class Link
	{
		String url;
	}

	/** A refusal's one word and its sentence. */
	static final class Refusal
	{
		String reason;
		String error;
	}

	private final OkHttpClient http;
	private final Gson gson;
	private boolean busy;

	@Inject
	ConnectClient(OkHttpClient http, Gson gson)
	{
		this.http = CompanionHttp.pinnedToServer(http);
		this.gson = gson;
	}

	/** Connect: make a group, which the map names. */
	void start(String server, String installationKey, Consumer<Outcome> done)
	{
		send(server, installationKey, new Ask(), null, done);
	}

	/** Join: spend {@code invite} for the group {@code code}. */
	void join(String server, String installationKey, String code, String invite, Consumer<Outcome> done)
	{
		Ask ask = new Ask();
		ask.code = code;
		ask.invite = invite;
		send(server, installationKey, ask, code, done);
	}

	private void send(String server, String installationKey, Ask ask, @Nullable String expectedCode, Consumer<Outcome> done)
	{
		HttpUrl url = HttpUrl.parse(server + CONNECT);
		if (url == null || !JoinCode.isWellFormedSecret(installationKey))
		{
			done.accept(Outcome.refused(FAILED));
			return;
		}
		synchronized (this)
		{
			if (busy)
			{
				done.accept(Outcome.refused(ONE_AT_A_TIME));
				return;
			}
			busy = true;
		}
		Request request = CompanionHttp.request(url)
			.header(INSTALLATION_HEADER, installationKey)
			.post(CompanionHttp.json(gson, ask))
			.build();
		CompanionHttp.enqueue(http.newCall(request),
			(call, error) -> finish(done, Outcome.refused(UNREACHABLE)),
			(call, response) -> finish(done, read(response, expectedCode)));
	}

	private void finish(Consumer<Outcome> done, Outcome outcome)
	{
		synchronized (this)
		{
			busy = false;
		}
		done.accept(outcome);
	}

	/**
	 * One answer to Connect or Join, as the code to keep or the sentence to print.
	 *
	 * <p>The code the map hands back is checked as hard as a pasted one, and harder: a RuneLite code
	 * whose group is not the one in the same answer, or not the one the player pasted, is a map this
	 * build does not understand, and keeping it would post this player somewhere they did not choose.
	 * Package-private for its test; OkHttp's thread.
	 */
	Outcome read(Response response, @Nullable String expectedCode)
	{
		try (Response close = response)
		{
			String text = CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES);
			Outcome refused = whenRefused(close, text);
			if (refused != null)
			{
				return refused;
			}
			Answer answer = gson.fromJson(text, Answer.class);
			if (answer == null || !JoinCode.isWellFormedCode(answer.code)
				|| (expectedCode != null && !expectedCode.equals(answer.code)))
			{
				return Outcome.refused(FAILED);
			}
			JoinCode joined = JoinCode.parse(answer.runeliteCode != null ? answer.runeliteCode : answer.code);
			if (joined == null || !joined.code.equals(answer.code)
				|| (answer.runeliteCode != null && joined.suffix == null))
			{
				return Outcome.refused(FAILED);
			}
			return Outcome.joined(joined, Pairing.tidyName(answer.name));
		}
		catch (IOException | RuntimeException unreadable)
		{
			return Outcome.refused(FAILED);
		}
	}

	/**
	 * Rename the group, as a member who runs it. The sidebar offers this only after the map said this
	 * member may, so a refusal is rare and is printed as the map wrote it; a name the map did not take
	 * is never kept.
	 */
	void rename(String server, String code, @Nullable String memberId, @Nullable String secret, String name,
		Consumer<Outcome> done)
	{
		HttpUrl url = HttpUrl.parse(server + GROUP);
		if (url == null || code.isEmpty() || memberId == null || !JoinCode.isWellFormedSecret(secret))
		{
			done.accept(Outcome.refused(FAILED));
			return;
		}
		synchronized (this)
		{
			if (busy)
			{
				done.accept(Outcome.refused(ONE_AT_A_TIME));
				return;
			}
			busy = true;
		}
		Rename body = new Rename();
		body.name = name;
		Request request = CompanionHttp.member(url, code, memberId, secret).patch(CompanionHttp.json(gson, body))
			.build();
		CompanionHttp.enqueue(http.newCall(request),
			(call, error) -> finish(done, Outcome.refused(UNREACHABLE)),
			(call, response) -> finish(done, readRename(response)));
	}

	/** One answer to a rename: the name the map now has, or why not. Package-private for its test. */
	Outcome readRename(Response response)
	{
		try (Response close = response)
		{
			String text = CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES);
			Outcome refused = whenRefused(close, text);
			if (refused != null)
			{
				return refused;
			}
			Answer answer = gson.fromJson(text, Answer.class);
			String named = answer == null ? null : Pairing.tidyName(answer.name);
			return named != null ? Outcome.named(named) : Outcome.refused(FAILED);
		}
		catch (IOException | RuntimeException unreadable)
		{
			return Outcome.refused(FAILED);
		}
	}

	/**
	 * What Connect, Join and Rename say to an answer the map refused: busy, the map's own sentence, or
	 * FAILED when it wrote none. Null for an answer it did not refuse, which the caller reads.
	 */
	@Nullable
	private Outcome whenRefused(Response response, String text)
	{
		if (response.code() == 429 || response.code() == 503)
		{
			return Outcome.refused(BUSY);
		}
		if (response.isSuccessful())
		{
			return null;
		}
		Refusal refusal = gson.fromJson(text, Refusal.class);
		String sentence = refusal == null ? null : sentence(refusal.error);
		return Outcome.refused(sentence != null ? sentence : FAILED);
	}

	/**
	 * Open the map: a one-time link for the browser about to open, or null when there is none to be had.
	 *
	 * <p>Null for every failure — unreachable, refused, a legacy group, an answer on another map — and
	 * the caller opens the plain map then, which is all the button did before. No secret means the map
	 * has not issued one yet, and asking without it is a request certain to be refused.
	 */
	void link(String server, String code, @Nullable String memberId, @Nullable String secret, Consumer<String> done)
	{
		HttpUrl url = HttpUrl.parse(server + LINK);
		if (url == null || code.isEmpty() || memberId == null || !JoinCode.isWellFormedSecret(secret))
		{
			done.accept(null);
			return;
		}
		Request request = CompanionHttp.member(url, code, memberId, secret).post(CompanionHttp.json(gson, new JsonObject()))
			.build();
		CompanionHttp.enqueue(http.newCall(request),
			(call, error) -> done.accept(null),
			(call, response) -> done.accept(readLink(response, url)));
	}

	/** The link out of an answer, if it is one this plugin will open. Package-private for its test. */
	@Nullable
	String readLink(Response response, HttpUrl asked)
	{
		try (Response close = response)
		{
			if (!close.isSuccessful())
			{
				return null;
			}
			Link link = gson.fromJson(CompanionHttp.readBounded(close.body(), CompanionHttp.MAX_REPLY_BYTES), Link.class);
			return link != null && isMapLink(asked, link.url) ? link.url : null;
		}
		catch (IOException | RuntimeException unreadable)
		{
			return null;
		}
	}

	/**
	 * Whether a link is one to hand the player's browser: on the map that was asked, and the map's own
	 * page with the token where the map puts it, in the fragment.
	 *
	 * <p>The browser this opens is about to be made into this player, so the address is checked as an
	 * invite's is ({@link RelayClient#isInviteUnder}) and then held to the one shape the map makes: a
	 * server that answered with anywhere else would have the player's browser carry a key there.
	 */
	static boolean isMapLink(HttpUrl asked, @Nullable String link)
	{
		if (!RelayClient.isInviteUnder(asked, link))
		{
			return false;
		}
		HttpUrl parsed = HttpUrl.parse(link);
		String fragment = parsed == null ? null : parsed.fragment();
		return parsed != null && parsed.encodedPath().endsWith("/map") && fragment != null
			&& fragment.matches("link=[" + JoinCode.ALPHABET + "]{24}");
	}

	/**
	 * A sentence the map wrote, or null when what arrived is not one: one line, no markup, and short.
	 * The map's refusals are its own words rather than anything a member typed, but they are still
	 * text from a server, and the sidebar draws them in HTML labels.
	 */
	@Nullable
	static String sentence(@Nullable String said)
	{
		if (said == null)
		{
			return null;
		}
		String plain = said.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
		return plain.isEmpty() || plain.length() > MAX_SENTENCE ? null : plain;
	}
}
