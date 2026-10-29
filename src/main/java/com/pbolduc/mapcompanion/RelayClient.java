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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Locale;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
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
 * The snapshot and invite requests this plugin makes.
 *
 * <p>Both POSTs live here so a reviewer can read the send policy beside {@link SnapshotBuilder}'s
 * fields. The receive-only connection lives in {@link DirectiveStream}; the Stars tab's reads and
 * preference writes live in {@link StarsClient}. These two POST addresses use the configured map:
 *
 * <ul>
 *   <li>{@code POST /api/companion/snapshot} — one snapshot every couple of seconds while a
 *       pairing code is set, carrying the code as a header; the member secret this group issued,
 *       as {@code X-Companion-Member-Secret}, once there is one; and, until the map says it has
 *       been claimed, the suffix of the reader's RuneLite code as a third.
 *   <li>{@code POST /api/companion/invite} — once, when somebody presses <i>Copy invite</i>, to
 *       ask for a link that expires. It carries the pairing code, the same member secret, and this
 *       installation's member id, and a failure means the button copies the old plain-text message
 *       instead.
 * </ul>
 *
 * <p>{@link DirectiveStream} holds one more connection, to {@code /api/companion/stream}, which
 * only receives. Everything this class will not do is as deliberate as what it does:
 *
 * <ul>
 *   <li><b>Nothing without a pairing code.</b> {@link #post} returns immediately when the code is
 *       blank, so a fresh install with the plugin enabled makes no requests at all.
 *   <li><b>It obeys the server's cadence.</b> The relay states an interval on every reply and a
 *       {@code Retry-After} when it is unhappy; both are honoured, so the operator has one lever
 *       that slows every client down without anybody shipping a new plugin.
 *   <li><b>It gives up on a permanent refusal.</b> 400, 413, 422 and 507 are refusals an
 *       immediate retry cannot fix, so posting stops and the panel says so — as are the
 *       refusals that arrive with a reason in the body, which are the map saying the group is
 *       closed, that this member was removed, or that it needs the reader's RuneLite code. A 4xx
 *       with nothing to say is still treated like a 5xx, because a Cloudflare 403 during a
 *       deploy is not a bad pairing code. Fatals expire after five minutes for one retry,
 *       because the relay is redeployed without notice and a code that was wrong then may be
 *       right now.
 *   <li><b>It never logs the code.</b> The code is the group's only secret, and a log file is
 *       shared far more casually than a password.
 * </ul>
 */
@Slf4j
@Singleton
public class RelayClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String CODE_HEADER = "X-Companion-Code";

	/**
	 * The other half of a RuneLite code, sent as a header rather than in the body.
	 *
	 * <p>A header because it is addressing rather than content: the map reads it before deciding
	 * anything about the snapshot, and a snapshot that gets trimmed to fit the wire cap must not be
	 * able to lose the field that says who is posting it.
	 */
	private static final String SUFFIX_HEADER = "X-Companion-Suffix";

	/**
	 * The credential that says which member of the group this installation is.
	 *
	 * <p>Issued by the map once and presented on every door that group guards — this endpoint, the
	 * invite request, and {@link DirectiveStream}'s connection. It exists because a member id is
	 * not a secret: every member's id is published to every other member on every read, so an
	 * invite request authenticated by an id alone lets somebody who was removed mint a fresh invite
	 * under a name the group still trusts.
	 *
	 * <p>A header rather than the body or the query, for all three doors at once. The body is
	 * trimmed to fit a cap and must not be able to lose this; the query lands in access logs, and a
	 * standing credential in somebody's logs is the thing this is trying to stop being true of the
	 * member id. It is never logged here either — nothing in this class prints a header.
	 */
	private static final String MEMBER_SECRET_HEADER = "X-Companion-Member-Secret";

	/**
	 * Which RuneLite this post comes from — {@link MemberIdentity#installationKey}.
	 *
	 * <p>On the snapshot alone, because the post is the only request that can make a member: it is
	 * what lets a second account of a linked RuneLite into an owned group without another pasted
	 * code, and onto the browser the first one linked. A header for the reason the two above are, and
	 * never logged, because it stands in for a pasted code.
	 */
	private static final String INSTALLATION_HEADER = "X-Companion-Installation";

	/**
	 * How long the invite request may take before the button gives up and copies the old message.
	 *
	 * <p>Short on purpose. The player is standing there with a chat window open waiting to paste,
	 * so a request still trying after a few seconds has already failed at the only thing that
	 * mattered, and the fallback text works.
	 */
	private static final long INVITE_TIMEOUT_MS = 6_000L;

	/**
	 * The four statuses the relay actually emits with meaning, and the only ones retrying
	 * cannot fix.
	 *
	 * <p>400 — the pairing code is the wrong shape. 413 — the body exceeded the cap. 422 — the
	 * snapshot was unreadable. 507 — no room, in the group or on the whole relay (a 5xx by number
	 * and a refusal by meaning; {@link #describeFull} says which). Anything else in 400–499 used
	 * to latch {@link #fatalError} forever, which is how a proxy 404 during a Fly deploy became
	 * "the plugin is broken until you edit the code".
	 */
	static final int[] PERMANENT_REFUSALS = {400, 413, 422, 507};

	/** The floor the relay itself enforces; used until it says otherwise. */
	private static final long DEFAULT_INTERVAL_MS = 2_000L;
	/** A flat pause after a network failure — long enough not to hammer, short enough to recover. */
	private static final long ERROR_BACKOFF_MS = 30_000L;

	/**
	 * How long a fatal refusal stays latched before one retry is allowed.
	 *
	 * <p>The relay is redeployed without notice. A code that was wrong for five minutes may
	 * be right now — {@code acceptSnapshot} re-registers the group on the next heartbeat.
	 * After the retry, a same-shaped refusal re-latches for another five minutes.
	 */
	static final long FATAL_RETRY_MS = 5 * 60_000L;

	private final OkHttpClient httpClient;
	private final Gson gson;

	/** One request in flight at a time; a tick that arrives mid-post is simply skipped. */
	private final AtomicBoolean inFlight = new AtomicBoolean(false);

	/** A reply belongs to the connection that sent it, even when the player rejoins the same code. */
	private long generation;
	@Nullable
	private Call activePost;

	@Getter
	private volatile long nextAllowedPostMs = 0L;

	/**
	 * Set when the relay refuses us in a way retrying cannot fix. Surfaced in the sidebar so the
	 * player can see that the code is wrong rather than wondering why their dot never appeared.
	 * Read through {@link #getFatalError()}, which treats an expired fatal as cleared.
	 */
	private volatile String fatalError = null;

	private volatile long fatalSinceMs = 0L;

	@Getter
	private volatile boolean everSucceeded = false;

	/**
	 * When the last 2xx landed. Zero means never. The sidebar tooltip ages this so a green
	 * dot that has not posted in a minute is visible as a sentence, not only as a colour.
	 */
	@Getter
	private volatile long lastSuccessMs = 0L;

	/** Consecutive posts that did not land. Reset on 2xx. The sidebar turns amber when this is up. */
	@Getter
	private volatile int consecutiveFailures = 0;

	/**
	 * One short sentence about the last transient failure, never the pairing code or the URL.
	 * Cleared on success. The sidebar prints this while {@link Health#DEGRADED}.
	 */
	@Getter
	private volatile String lastTransientError = null;

	/**
	 * Package-private rather than private, so a test can drive {@link #handle} with responses it
	 * builds by hand. OkHttp's {@code Response} needs no server to construct, which makes the whole
	 * of this class's reply policy — the cadence, the latch, {@code claimed}, the acknowledgements
	 * — testable offline. Guice does not care either way.
	 */
	@Inject
	RelayClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = CompanionHttp.pinnedToServer(httpClient);
		this.gson = gson;
	}

	/**
	 * Drop a latched refusal without forgetting everything else.
	 *
	 * <p>Called when the RuneLite code's suffix changes, which is the one input that can turn a
	 * permanent refusal into a request that would now succeed: {@code 403 needs_code} is the map
	 * saying it does not know this member, and pasting the code is the answer to it. A full
	 * {@link #reset} would also drop the connection and put the sidebar back to "Connecting…" for
	 * a member that never stopped being connected.
	 *
	 * <p><b>The cadence is deliberately left alone</b>, and that is the whole difference between
	 * this and {@link #reset}. {@code nextAllowedPostMs} is not part of a refusal — it is the
	 * interval the map asked for on its last reply, or the {@code Retry-After} it sent with a 429.
	 * Zeroing it here let a paste into the Join field cancel a back-off the server had just asked
	 * for, which is the one thing this plugin must never do: a stalled dot is exactly when somebody
	 * pastes their code again, so the moment the map is asking everybody to slow down is the moment
	 * this would have sped up. Nothing needs it cleared, either — a permanent refusal never sets it,
	 * so the retry it exists to allow was already due.
	 */
	public void clearRefusal()
	{
		fatalError = null;
		fatalSinceMs = 0L;
	}

	/** Called when the code changes, so an earlier failure does not haunt a corrected one. */
	public synchronized void reset()
	{
		generation++;
		Call previous = activePost;
		activePost = null;
		inFlight.set(false);
		if (previous != null)
		{
			previous.cancel();
		}
		fatalError = null;
		fatalSinceMs = 0L;
		nextAllowedPostMs = 0L;
		everSucceeded = false;
		lastSuccessMs = 0L;
		consecutiveFailures = 0;
		lastTransientError = null;
	}

	/**
	 * The latched refusal, or null when there is none or it has aged out for a retry.
	 *
	 * <p>Lombok is not generating this: an expired fatal must read as cleared so the tick
	 * will post once, without the panel still showing the old sentence.
	 */
	@Nullable
	public String getFatalError()
	{
		String error = fatalError;
		if (error == null)
		{
			return null;
		}
		return fatalExpired(fatalSinceMs, System.currentTimeMillis()) ? null : error;
	}

	public boolean isReady(long now)
	{
		return !hasActiveFatal(now) && !inFlight.get() && now >= nextAllowedPostMs;
	}

	/**
	 * Send one snapshot.
	 *
	 * <p>Asynchronous, and deliberately so: this is called from the game thread, and a blocking
	 * request there would stutter the client on every slow response.
	 *
	 * @param suffix the eight characters after the dash in the reader's RuneLite code, or null.
	 *     Sent as a header on every post until the reply says it has been claimed, at which point
	 *     {@link Pairing} deletes it and this goes back to being null forever. A wrong or expired
	 *     one is ignored by the map rather than refused, so sending it costs nothing when it is
	 *     stale and is the whole of "the map knows which dot is you" when it is not.
	 * @param memberSecret the credential this group issued, or null when it has not issued one.
	 *     Sent from then on, and never a reason not to post: the map accepts a snapshot without it
	 *     so that a plugin older than the feature keeps a group's dots alive. It is the reply to
	 *     one of these posts that carries the secret in the first place, so a post that refused to
	 *     go without one could never get one.
	 * @return true if the request was handed to OkHttp. False if this returned before touching
	 *     the network — no pairing code, a latched fatal, or another post already in flight —
	 *     so the caller must not record the snapshot as sent.
	 */
	public synchronized boolean post(
		String baseUrl,
		String code,
		@Nullable String suffix,
		@Nullable String memberSecret,
		CompanionSnapshot snapshot,
		Consumer<RelayResponse> onDirective)
	{
		return post(baseUrl, code, suffix, memberSecret, null, snapshot, onDirective);
	}

	public synchronized boolean post(
		String baseUrl,
		String code,
		@Nullable String suffix,
		@Nullable String memberSecret,
		@Nullable String installationKey,
		CompanionSnapshot snapshot,
		Consumer<RelayResponse> onDirective)
	{
		if (code == null || code.isEmpty())
		{
			/** Inert without a code. This is the guarantee the README makes; here is where it lives. */
			return false;
		}
		long now = System.currentTimeMillis();
		if (hasActiveFatal(now) || !inFlight.compareAndSet(false, true))
		{
			return false;
		}
		if (fatalError != null)
		{
			/**
			 * Expired for exactly one retry. Cleared here rather than in {@link #getFatalError}
			 * so a second tick cannot sneak another post in while this one is in flight; if
			 * the retry fails the same way, {@link #handle} re-latches.
			 */
			fatalError = null;
			fatalSinceMs = 0L;
		}

		HttpUrl url = snapshotUrl(baseUrl);
		if (url == null)
		{
			fatalError = "The map address in the settings isn't a valid URL.";
			fatalSinceMs = now;
			inFlight.set(false);
			return false;
		}

		Request.Builder builder = new Request.Builder()
		.url(url)
		.header(CODE_HEADER, code)
		.header("User-Agent", CompanionVersion.USER_AGENT)
		.post(RequestBody.create(JSON, gson.toJson(snapshot)));
		if (JoinCode.isWellFormedSuffix(suffix))
		{
			/** Checked rather than trusted: this comes out of a settings field a player can type in. */
			builder.header(SUFFIX_HEADER, suffix);
		}
		if (JoinCode.isWellFormedSecret(memberSecret))
		{
			builder.header(MEMBER_SECRET_HEADER, memberSecret);
		}
		if (JoinCode.isWellFormedSecret(installationKey))
		{
			builder.header(INSTALLATION_HEADER, installationKey);
		}
		Request request = builder.build();
		long requestGeneration = generation;
		activePost = httpClient.newCall(request);

		activePost.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				synchronized (RelayClient.this)
				{
					if (requestGeneration != generation || activePost != call)
					{
						return;
					}
					log.debug("companion relay unreachable", e);
					recordTransient("Couldn't reach the map", System.currentTimeMillis());
					activePost = null;
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
					(Response r = response)
				{
					handle(r, onDirective, requestGeneration);
				}
				catch (Exception e)
				{
					synchronized (RelayClient.this)
					{
						if (requestGeneration == generation && activePost == call)
						{
							log.debug("companion relay response unreadable", e);
							recordTransient("The map's reply was unreadable", System.currentTimeMillis());
						}
					}
				}
				finally
				{
					synchronized (RelayClient.this)
					{
						if (requestGeneration == generation && activePost == call)
						{
							inFlight.set(false);
							activePost = null;
						}
					}
				}
			}
		});
		return true;
	}

	/** What to tell a player whose map address answers with a redirect. */
	static String redirectMessage(HttpUrl requested, @Nullable String location)
	{
		HttpUrl target = location == null ? null : requested.resolve(location);
		if (target == null)
		{
			return "This map address redirects. Enter the address it points to under Settings → Advanced → Map address.";
		}
		/* The origin only: the path is this plugin's own, and a full URL from a server is not ours to print. */
		String origin = target.scheme() + "://" + target.host()
			+ (target.port() == HttpUrl.defaultPort(target.scheme()) ? "" : ":" + target.port());
		return "This map address redirects to " + origin + ". Enter that under Settings → Advanced → Map address.";
	}

	private void recordTransient(String message, long now)
	{
		nextAllowedPostMs = now + ERROR_BACKOFF_MS;
		consecutiveFailures++;
		lastTransientError = message;
		inFlight.set(false);
	}

	/**
	 * What one reply means. Package-private so it can be driven with a hand-built {@code Response}:
	 * every decision this class makes after a request lands is in here, and none of it was reachable
	 * from a test before.
	 */
	void handle(Response response, Consumer<RelayResponse> onDirective) throws IOException
	{
		long requestGeneration;
		synchronized (this)
		{
			requestGeneration = generation;
		}
		handle(response, onDirective, requestGeneration);
	}

	private void handle(Response response, Consumer<RelayResponse> onDirective, long requestGeneration)
	throws IOException
	{
		int code = response.code();
		String reason = code == 507 || code == 403 || code == 410 ? readReason(response) : null;
		RelayResponse parsed = null;
		ResponseBody body = response.body();
		if (response.isSuccessful() && body != null)
		{
			parsed = gson.fromJson(CompanionHttp.readBounded(body, CompanionHttp.MAX_REPLY_BYTES), RelayResponse.class);
		}

		/* Read the body without holding the game thread's reset lock; only applying it is atomic. */
		synchronized (this)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			applyReply(response, code, reason, parsed, onDirective);
		}
	}

	private void applyReply(
		Response response, int code, @Nullable String reason, @Nullable RelayResponse parsed,
		Consumer<RelayResponse> onDirective)
	{
		long now = System.currentTimeMillis();

		if (code == 429)
		{
			long retryAfter = parseRetryAfterSeconds(response.header("Retry-After"));
			nextAllowedPostMs = now + Math.max(retryAfter * 1000L, DEFAULT_INTERVAL_MS);
			return;
		}

		if (isPermanent(code))
		{
			fatalError = code == 507 ? describeFull(reason) : describeClientError(code);
			fatalSinceMs = now;
			return;
		}

		if (code == 403 || code == 410)
		{
			/**
			 * The two statuses whose meaning is in the body. The map answers a member it has never
			 * heard of with {@code 403 needs_code} and a member it will never hear from again with
			 * {@code 410 closed} or {@code 410 removed}; a proxy answers 403 with HTML. Reading the
			 * reason is what tells those apart, and getting it wrong in either direction is bad:
			 * retrying a removal every thirty seconds forever, or telling somebody their group is
			 * closed because a deploy was in progress.
			 */
			String permanent = permanentReason(code, reason);
			if (permanent != null)
			{
				fatalError = permanent;
				fatalSinceMs = now;
				return;
			}
		}

		if (response.isRedirect())
		{
			/*
			 * Redirects are not followed (see CompanionHttp), and retrying one never changes the
			 * answer. Latched like a refusal, with the address to type instead, so a www or apex
			 * address that used to work says what to do rather than "answered 301" every thirty
			 * seconds.
			 */
			fatalError = redirectMessage(response.request().url(), response.header("Location"));
			fatalSinceMs = now;
			return;
		}

		if (!response.isSuccessful())
		{
			/**
			 * 5xx, and 4xx that are not a permanent refusal. A proxy 404 during a deploy is
			 * not a bad pairing code; retrying it is how the plugin recovers without anyone
			 * editing the settings.
			 */
			consecutiveFailures++;
			lastTransientError = code >= 400 && code < 500
								 ? "The map refused this request (" + code + ")"
								 : "The map answered " + code;
			nextAllowedPostMs = now + ERROR_BACKOFF_MS;
			return;
		}

		everSucceeded = true;
		consecutiveFailures = 0;
		lastTransientError = null;
		lastSuccessMs = now;

		long interval = parsed != null && parsed.pollIntervalMs > 0
						? parsed.pollIntervalMs
						: DEFAULT_INTERVAL_MS;
		nextAllowedPostMs = now + interval;

		if (parsed != null && onDirective != null)
		{
			onDirective.accept(parsed);
		}
	}

	private boolean hasActiveFatal(long now)
	{
		return fatalError != null && !fatalExpired(fatalSinceMs, now);
	}

	/**
	 * The four codes the relay emits with meaning. Package-private so the test can pin the
	 * set without standing up OkHttp.
	 */
	static boolean isPermanent(int code)
	{
		for (int refusal : PERMANENT_REFUSALS)
		{
			if (refusal == code)
			{
				return true;
			}
		}
		return false;
	}

	/** True when a latched fatal is old enough that one retry is allowed. */
	static boolean fatalExpired(long fatalSinceMs, long now)
	{
		return fatalSinceMs > 0L && now - fatalSinceMs >= FATAL_RETRY_MS;
	}

	/**
	 * The sentence to print for a refusal that carries a reason, or null to keep retrying.
	 *
	 * <p>Three of these are the map speaking on purpose, and each is something the player can act
	 * on or has to accept:
	 *
	 * <ul>
	 *   <li>{@code 403 needs_code} — the group knows nothing about this installation, which is what
	 *       a bare code gets in a group that keeps members. The answer is a paste, and the sentence
	 *       says where from.
	 *   <li>{@code 410 closed} — the group's owner ended it. Nothing to retry: the map keeps a
	 *       tombstone so that an old plugin cannot bring it back.
	 *   <li>{@code 410 removed} — an owner removed this member. Retrying that thirty seconds later,
	 *       which is what the previous release did, is a plugin arguing with a decision.
	 * </ul>
	 *
	 * <p><b>403 with anything else is not permanent</b>, and that asymmetry is deliberate: a proxy
	 * in front of the map answers 403 with HTML during a deploy, and latching on it is how "the
	 * plugin is broken until you edit the settings" happened before. 410 with anything else is,
	 * because 410 is not a status anything in the path emits by accident — it means gone, and the
	 * five-minute retry window still gives a redeployed map one chance to disagree.
	 *
	 * <p>Package-private so every case can be pinned without standing up OkHttp.
	 */
	@Nullable
	static String permanentReason(int status, @Nullable String reason)
	{
		String tidy = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
		if (status == 403)
		{
			return "needs_code".equals(tidy)
				   ? "The map needs your RuneLite code — copy it from the map's Companion panel and "
				   + "paste it here."
				   : null;
		}
		if (status == 410)
		{
			if ("closed".equals(tidy))
			{
				return "This group was closed.";
			}
			if ("removed".equals(tidy))
			{
				return "You were removed from this group.";
			}
			return "That group is gone from the map.";
		}
		return null;
	}

	/**
	 * Which ceiling a 507 hit, as a sentence the player can act on.
	 *
	 * <p>The map says {@code group_full} when this group has no room for another member and
	 * {@code capacity} when the relay itself has none, and the answers differ: the first needs
	 * somebody in the group to leave or be removed, and the second clears by itself as other groups
	 * go quiet, so the five-minute retry is all there is to do. A 507 with no word comes from a map
	 * older than the words and gets the sentence every 507 used to get.
	 *
	 * <p>Package-private so both words can be pinned without standing up OkHttp.
	 */
	static String describeFull(@Nullable String reason)
	{
		String tidy = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
		if ("capacity".equals(tidy))
		{
			return "The map has no room for anyone new right now. This will try again in five minutes.";
		}
		return "That group is full.";
	}

	/**
	 * The {@code reason} out of a refusal's body, or null.
	 *
	 * <p>Everything about this is defensive. The body may be HTML from a proxy, empty, or absent;
	 * none of those is an error worth reporting, they are simply "no reason given", which
	 * {@link #permanentReason} already knows what to do with. Bounded because a refusal body is a
	 * few dozen bytes and anything larger is not one.
	 */
	@Nullable
	private String readReason(Response response)
	{
		try
		{
			ResponseBody body = response.body();
			if (body == null)
			{
				return null;
			}
			String text = CompanionHttp.readBounded(body, 4_096);
			if (text.isEmpty())
			{
				return null;
			}
			Refusal refusal = gson.fromJson(text, Refusal.class);
			return refusal == null ? null : refusal.reason;
		}
		catch (IOException | RuntimeException e)
		{
			/** Unreadable is "no reason given" rather than a second failure to report. */
			log.debug("companion relay refusal body unreadable", e);
			return null;
		}
	}

	/**
	 * Ask the map for an invite link, and hand back what it says or null.
	 *
	 * <p>An invite that expires cannot be built from a code alone — the
	 * link carries a token only the map can mint — and because the alternative was a plain-text
	 * message that admits anyone who finds it in a Discord channel three months later.
	 *
	 * <p>The callback always runs, with null for every failure there is: unreachable, refused,
	 * unparseable, or answered without a URL. The panel copies today's message in that case, so a
	 * map that has not shipped this endpoint yet behaves exactly as it did before.
	 *
	 * <p>Its own call timeout rather than the client's, because a player is standing at a chat
	 * window waiting to paste. Derived from the injected client so the connection pool, the proxy
	 * settings and the user agent RuneLite configured are all still the ones in use.
	 *
	 * <p><b>This is the door the member secret was invented for.</b> An invite is the one thing a
	 * member can make that admits a stranger, and until now the only thing proving the request came
	 * from a member was a member id — which the map publishes to every other member on every read.
	 * So the secret goes on this request too, and a map that wants one answers
	 * {@code 403 needs_code} without it. That is a failure like any other here: the callback gets
	 * null and the button copies the plain-text message, which is also what happens against a map
	 * that has never heard of the endpoint.
	 */
	/**
	 * Whether an invite link is one to hand a player to paste to friends: on the map that was asked,
	 * over the same scheme, one line and short. The link comes from the server, and the clipboard
	 * text says "Join my OSRS map", so a server that answered with any other address would have the
	 * player send their friends wherever it liked.
	 */
	static boolean isInviteUnder(HttpUrl asked, @Nullable String link)
	{
		if (link == null || link.isEmpty() || link.length() > 512 || !link.equals(link.trim())
			|| link.chars().anyMatch(c -> c < 0x20 || c == 0x7f || Character.isWhitespace(c)))
		{
			return false;
		}
		HttpUrl parsed = HttpUrl.parse(link);
		return parsed != null && parsed.scheme().equals(asked.scheme()) && parsed.host().equals(asked.host())
			&& parsed.port() == asked.port();
	}

	public void requestInvite(
		String baseUrl,
		String code,
		String memberId,
		@Nullable String memberSecret,
		Consumer<Invite> onResult)
	{
		HttpUrl url = pathUnder(baseUrl, "api/companion/invite");
		if (url == null || code == null || code.isEmpty())
		{
			onResult.accept(null);
			return;
		}

		InviteRequest payload = new InviteRequest();
		payload.memberId = memberId;

		Request.Builder builder = new Request.Builder()
		.url(url)
		.header(CODE_HEADER, code)
		.header("User-Agent", CompanionVersion.USER_AGENT)
		.post(RequestBody.create(JSON, gson.toJson(payload)));
		if (JoinCode.isWellFormedSecret(memberSecret))
		{
			builder.header(MEMBER_SECRET_HEADER, memberSecret);
		}
		Request request = builder.build();

		httpClient.newBuilder()
		.callTimeout(INVITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
		.build()
		.newCall(request)
		.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("companion invite request failed", e);
				onResult.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				Invite invite = null;
				try
					(Response r = response)
				{
					ResponseBody body = r.body();
					if (r.isSuccessful() && body != null)
					{
						Invite parsed = gson.fromJson(CompanionHttp.readBounded(body, CompanionHttp.MAX_REPLY_BYTES), Invite.class);
						if (parsed != null && isInviteUnder(url, parsed.url))
						{
							invite = parsed;
						}
					}
				}
				catch (Exception e)
				{
					log.debug("companion invite reply unreadable", e);
				}
				onResult.accept(invite);
			}
		});
	}

	/** What {@code POST /api/companion/invite} sends. One field, and it is not the player's. */
	private static class InviteRequest
	{
		String memberId;
	}

	/**
	 * What it answers: a link, and when the link stops working.
	 *
	 * <p>{@code expiresAt} is a String so that either shape survives — the map may state an instant
	 * as milliseconds or as text, and this only ever prints it. Guessing wrong about a number's
	 * units would put a wrong sentence beside a right link.
	 */
	public static class Invite
	{
		public String url;
		public String expiresAt;
	}

	/** The body a refusal carries: one word saying which refusal it is. */
	private static class Refusal
	{
		String reason;
	}

	private static String describeClientError(int code)
	{
		switch (code)
		{
			case 400:
				return "That pairing code isn't the right shape — check it on the map's Companion panel.";
			case 413:
				return "This plugin sent more than the map accepts. Please report this.";
			case 422:
				return "The map didn't recognise this plugin's data. Update the plugin.";
			default:
				/** Reached for a malformed URL, not for a status we retry. Does not claim permanence. */
				return "The map refused this plugin's data (" + code + ").";
		}
	}

	private static long parseRetryAfterSeconds(@Nullable String header)
	{
		if (header == null)
		{
			return 30L;
		}
		try
		{
			return Math.max(1L, Long.parseLong(header.trim()));
		}
		catch (NumberFormatException e)
		{
			return 30L;
		}
	}

	@Nullable
	private static HttpUrl snapshotUrl(String baseUrl)
	{
		return pathUnder(baseUrl, "api/companion/snapshot");
	}

	/**
	 * One of this plugin's two addresses, under whatever map the settings name.
	 *
	 * <p>Null for anything that is not a URL, which both callers treat as a refusal rather than
	 * guessing at what was meant — a typo in the map address should say so, not silently post
	 * somewhere else.
	 */
	@Nullable
	private static HttpUrl pathUnder(String baseUrl, String path)
	{
		HttpUrl parsed = HttpUrl.parse(baseUrl == null ? "" : baseUrl.trim());
		if (parsed == null)
		{
			return null;
		}
		return parsed.newBuilder()
			   .addPathSegments(path)
			   .build();
	}
}
