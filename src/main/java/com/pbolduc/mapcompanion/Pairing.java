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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;
import okhttp3.HttpUrl;

/**
 * Which group this installation is posting to, which groups it remembers, and the one-time code
 * that connects its dot to a browser.
 *
 * <p>One small class for the shared connection state, because getting the order
 * wrong is silent. The rules they encode:
 *
 * <ul>
 *   <li><b>One live group at a time.</b> The plugin posts to exactly one code — the same shape as
 *       RuneLite's own Party plugin, which allows one party — so switching groups is replacing the
 *       code rather than accumulating destinations. Two codes would double this plugin's traffic
 *       and muddle the one thing that has to stay obvious, which is who can see you right now.
 *   <li><b>A group you have joined is remembered.</b> Eight of them, most recent first, so
 *       switching back is one press instead of finding the invite again. Beside each code is the
 *       name the map last sent for that group and nothing invented here: the map repeats it on
 *       every reply to a member, so this is a copy of the map's answer rather than a second answer
 *       to drift from it, and a group the map has not named is shown by its code.
 *   <li><b>The suffix belongs to one code and outlives nothing.</b> {@code ABC234-K7PQ2M3X} is a
 *       group code plus a suffix minted for one browser; it is sent as a header until the map
 *       answers {@code claimed}, and then it is deleted. Changing the code throws it away
 *       unasked, because a suffix minted for one group cannot mean anything in another.
 *   <li><b>The member secret belongs to one server and code, and is kept while the group is.</b> The map issues
 *       it once, in the reply to a post it accepted, and the plugin presents it from then on so
 *       that knowing a member's id is no longer enough to mint invites in that group or to read
 *       its directives. It is filed under the server and code because that is what it proves — <i>this
 *       installation is that member of that group</i> — so switching groups switches which secret
 *       is presented, and no secret can ever be offered to a server or group that did not issue it.
 * </ul>
 *
 * <p><b>Why a secret survives a switch when a suffix does not.</b> They look alike and their
 * lifecycles are opposites. A suffix is a one-time binding the map consumes and answers
 * {@code claimed} to; a secret is a standing credential the map issues once and, having seen the
 * plugin present it, never sends again. Throwing one away on the way out of a group would leave
 * the one-press way back into that group able to post and never able to receive — a stream refused
 * for good, with nothing on either side able to repair it. So the secrets of the groups this
 * installation remembers are kept, bounded by that same list, and {@link #leave} deletes every one
 * of them because leaving also throws away the member id every one of them was minted for.
 *
 * <p><b>Why there is a {@link ConfigStore} rather than a {@link ConfigManager} used directly.</b> The
 * lifecycle above — sent until claimed, cleared on a code change, forgotten on leaving — is the
 * part that can go wrong without a symptom anybody notices, and it cannot be exercised against a
 * real {@code ConfigManager} without a client, a profile directory and an event bus. Naming the
 * three lines of key-value store this actually uses costs one interface and makes the whole
 * lifecycle a unit test. It lives in {@link ConfigStore} rather than here because three other
 * classes keep their own state the same way and were unreachable for the same reason. Everything
 * else about the settings still goes through RuneLite.
 *
 * <p><b>Why the writers are synchronized.</b> Secrets and names are filed on the client thread
 * while Connect, Leave and Forget run on the EDT, and each rewrites a stored JSON map by reading it
 * and writing it back. Unlocked, a Leave that landed between a tick's read and write had the tick
 * write back the secret Leave had just deleted. The readers stay unlocked: each reads one value.
 */
@Singleton
public class Pairing
{
	/**
	 * Where the remembered codes live: a JSON array of six-character codes, most recent first.
	 *
	 * <p>An ordinary key in this plugin's config group rather than a declared setting, for the same
	 * reason the member id and the kill log are: it is this plugin's own state, it is written by
	 * pressing things rather than by typing, and a settings panel showing {@code ["ABC234"]} in a
	 * text box invites somebody to edit it into something that is not JSON. The README says it is
	 * kept, which is where a reader is owed the fact.
	 */
	static final String KEY_KNOWN_CODES = "knownCodes";

	/** Eight, because a ninth group is one nobody is switching back to by pressing a button. */
	static final int MAX_KNOWN_CODES = 8;

	/**
	 * Where credentials live: a JSON object keyed by canonical server URL and group code.
	 *
	 * <p>An ordinary key rather than a declared setting, and here rather than beside the pairing
	 * code in {@link MapCompanionConfig}, for a stronger version of the reason the remembered codes
	 * are: those are merely awkward to edit by hand, this is a credential. A settings panel printing
	 * it is a settings panel inviting somebody to paste it into the same Discord channel the invite
	 * came from. It is never logged and never leaves this machine except as the header the map asks
	 * for; the README says it is kept and what it is for, which is where a reader is owed the fact.
	 */
	/* Older unscoped credentials have no trustworthy destination and are never migrated. */
	static final String KEY_MEMBER_SECRETS = "serverMemberSecrets";
	static final String KEY_SERVER_CONSENT = "serverConsent";
	private static final String KEY_ACTIVE_CONNECTION = "activeConnection";
	private static final String KEY_SUFFIX_CONNECTION = "suffixConnection";
	private static final String DISCLOSURE_VERSION = "1";

	private static final Type SECRETS_TYPE = new TypeToken<Map<String, String>>()
	{
	} .getType();

	/**
	 * Where names live: a JSON object keyed by canonical server URL and group code.
	 *
	 * <p>An ordinary key beside the remembered codes, and bounded by the same list, because it is
	 * the same fact from the other side: the list says which groups there is a button for, this
	 * says what the button reads. Not a credential and not personal — a name is what the group's
	 * members already see on its card — but it is written by the map rather than by the player, so
	 * {@link #tidyName} decides whether what arrived is a name at all before it is kept.
	 */
	static final String KEY_GROUP_NAMES = "serverGroupNames";

	/** The map refuses a longer one ({@code MAX_GROUP_NAME_LENGTH}), so anything longer did not come from it. */
	static final int MAX_GROUP_NAME = 24;

	private final ConfigStore store;
	private final Gson gson;
	private final Supplier<String> address;

	@Inject
	private Pairing(ConfigManager configManager, Gson gson, MapCompanionConfig config)
	{
		this(ConfigStore.of(configManager), gson, config::relayUrl);
	}

	Pairing(ConfigStore store, Gson gson)
	{
		this(store, gson, () -> store.get(MapCompanionConfig.KEY_RELAY_URL));
	}

	Pairing(ConfigStore store, Gson gson, Supplier<String> address)
	{
		this.store = store;
		this.gson = gson;
		this.address = address;
	}

	private static boolean isLoopback(String host)
	{
		return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
	}

	/** Server identity includes the scheme, port and base path that requests actually use. */
	@Nullable
	static String normalizeServer(@Nullable String address)
	{
		String value = address == null || address.trim().isEmpty()
			? MapCompanionConfig.DEFAULT_MAP_URL : address.trim();
		HttpUrl url = HttpUrl.parse(value);
		if (url == null || !url.username().isEmpty() || !url.password().isEmpty()
			|| url.query() != null || url.fragment() != null)
		{
			return null;
		}
		/*
		 * The code, the member secret and the installation key travel in headers, so plain http
		 * would hand them to anyone on the network. Loopback is the one exception: a map running
		 * on this machine for development has no network to cross.
		 */
		if (!url.isHttps() && !isLoopback(url.host()))
		{
			return null;
		}
		return url.toString().replaceAll("/+$", "");
	}

	@Nullable
	public String serverUrl()
	{
		return normalizeServer(address.get());
	}

	public String serverLabel()
	{
		String server = serverUrl();
		return server == null ? "an invalid map address" : server.replaceFirst("^https?://", "");
	}

	/** A changed destination pauses requests until Connect explicitly chooses it. */
	public boolean hasServerConsent()
	{
		return hasServerConsent(serverUrl(), code());
	}

	/** Pin the destination before testing it; a URL read after consent could be a different server. */
	public boolean hasServerConsent(String server, String code)
	{
		return server != null && server.equals(serverUrl()) && tidyCode(code).equals(tidyCode(code()))
			&& DISCLOSURE_VERSION.equals(readMap(KEY_SERVER_CONSENT).get(server))
			&& scopedKey(server, code).equals(store.get(KEY_ACTIVE_CONNECTION));
	}

	/** The dialog precedes any group change; cancelling writes nothing. */
	public boolean connect(JoinCode joined, BooleanSupplier confirm)
	{
		String server = serverUrl();
		if (server == null)
		{
			return false;
		}
		if (!DISCLOSURE_VERSION.equals(readMap(KEY_SERVER_CONSENT).get(server)) && !confirm.getAsBoolean())
		{
			return false;
		}
		/* A modal dialog runs configuration listeners too. Never approve a changed destination. */
		if (!server.equals(serverUrl()))
		{
			return false;
		}
		join(joined, server);
		if (!server.equals(serverUrl()))
		{
			return false;
		}
		approveServer(server);
		return true;
	}

	/** The synthetic harness's explicit join command grants consent to its fixture server. */
	void approveServer()
	{
		approveServer(serverUrl());
	}

	private synchronized void approveServer(String server)
	{
		if (server == null || !server.equals(serverUrl()) || !JoinCode.isWellFormedCode(tidyCode(code())))
		{
			return;
		}
		Map<String, String> approved = readMap(KEY_SERVER_CONSENT);
		approved.remove(server);
		approved.put(server, DISCLOSURE_VERSION);
		while (approved.size() > MAX_KNOWN_CODES)
		{
			approved.remove(approved.keySet().iterator().next());
		}
		store.set(KEY_SERVER_CONSENT, gson.toJson(approved, SECRETS_TYPE));
		store.set(KEY_ACTIVE_CONNECTION, scopedKey(server, code()));
	}

	private static String scopedKey(String server, String code)
	{
		return server + "|" + tidyCode(code);
	}

	/** The code snapshots are posted to, or "" when this installation is not in a group. */
	public String code()
	{
		String value = store.get(MapCompanionConfig.KEY_PAIRING_CODE);
		return value == null ? "" : value.trim();
	}

	/**
	 * The suffix to send as a header, or null when there is nothing to prove.
	 *
	 * <p>Null once the map has claimed it, and null for anything that is not eight characters of
	 * the code alphabet — this is a settings field, and a header this plugin already knows to be
	 * nonsense is one it should not send.
	 */
	@Nullable
	public String suffix()
	{
		String value = store.get(MapCompanionConfig.KEY_PAIRING_SUFFIX);
		String tidy = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		return JoinCode.isWellFormedSuffix(tidy) ? tidy : null;
	}

	/**
	 * The secret to present for the group this installation is posting to, or null when the map has
	 * not issued one.
	 *
	 * <p>Null is an ordinary state and not an error: an older map issues nothing, and a map that
	 * has just met this member has not issued anything yet. Every caller sends the header only when
	 * this is non-null and simply carries on when it is, because the one door that refuses a
	 * missing secret — the directive stream — already retries on a back-off that is right for
	 * "the map has not got round to us yet" as well as for "the map is down".
	 *
	 * <p>Checked on the way out as well as on the way in, like {@link #suffix}: this reads a config
	 * key, config keys can be edited, and a header this plugin can already tell is nonsense is one
	 * it should not send.
	 */
	@Nullable
	public String secret(@Nullable String memberId)
	{
		return secret(serverUrl(), code(), memberId);
	}

	/**
	 * Callers pin the server with the request so a concurrent address edit cannot swap credentials,
	 * and name the member, because the map issues one per member and a member is one character: an
	 * alt presenting its main's secret is refused at both doors it guards.
	 */
	@Nullable
	public String secret(String server, String code, @Nullable String memberId)
	{
		if (memberId == null)
		{
			return null;
		}
		String held = secrets().get(secretKey(server, code, memberId));
		return JoinCode.isWellFormedSecret(held) ? held : null;
	}

	/** Server, code and member: what a secret proves, and so what it is filed under. */
	private static String secretKey(String server, String code, String memberId)
	{
		return scopedKey(server, code) + "#" + memberId;
	}

	@Nullable
	public String suffix(String server, String code)
	{
		/* One stored value keeps the proof and destination together across concurrent config writes. */
		String held = store.get(KEY_SUFFIX_CONNECTION);
		String prefix = scopedKey(server, code) + "|";
		if (held == null || !held.startsWith(prefix))
		{
			return null;
		}
		String proof = held.substring(prefix.length());
		return JoinCode.isWellFormedSuffix(proof) ? proof : null;
	}

	/**
	 * File the secret the map issued for one group.
	 *
	 * <p>Called from the game tick rather than from the reply that carried it, for the reason
	 * {@link MapCompanionPlugin} gives about writing settings on an OkHttp thread — which is also
	 * why the code it belongs to is passed in rather than read here. The reply is an answer to a
	 * post made under <em>that</em> code, and by the time it lands the player may have joined
	 * somewhere else; filing it under whatever the current code happens to be would give one
	 * group's credential another group's name.
	 *
	 * <p>Case is left exactly as it arrived. {@link #suffix} upper-cases because a person reads a
	 * suffix off a screen and types it; nobody types this, so a character this plugin "corrects"
	 * is a credential it has quietly broken. Anything that is not the shape the map issues is
	 * dropped instead, which is the honest answer to a reply this build cannot understand.
	 *
	 * <p>Writing the same value again is skipped, and that is not a micro-optimisation: the map
	 * repeats the secret on every reply until it sees the plugin present one, so the first few
	 * ticks of a new group would otherwise write a setting — and raise a {@code ConfigChanged} —
	 * several times over for a value that never changed.
	 */
	public void rememberSecret(String code, @Nullable String memberId, @Nullable String secret)
	{
		rememberSecret(serverUrl(), code, memberId, secret);
	}

	public synchronized void rememberSecret(String server, String code, @Nullable String memberId, @Nullable String secret)
	{
		String normalized = normalizeServer(server);
		String tidySecret = secret == null ? "" : secret.trim();
		if (server == null || normalized == null || !JoinCode.isWellFormedCode(tidyCode(code))
			|| memberId == null || memberId.isEmpty() || !JoinCode.isWellFormedSecret(tidySecret))
		{
			return;
		}
		String key = secretKey(normalized, code, memberId);
		Map<String, String> secrets = secrets();
		if (tidySecret.equals(secrets.get(key)))
		{
			return;
		}
		secrets.put(key, tidySecret);
		store.set(KEY_MEMBER_SECRETS, gson.toJson(bounded(secrets), SECRETS_TYPE));
	}

	/**
	 * Forget every secret this installation holds.
	 *
	 * <p>Called from {@link #leave}, which is the one action that also throws away the member id —
	 * and a secret proves that a particular member id belongs to this installation, so once the id
	 * is gone every one of them proves nothing about anybody. Keeping them would leave credentials
	 * on disk for a member that no longer exists.
	 */
	public synchronized void forgetSecrets()
	{
		store.unset("memberSecrets");
		store.unset(KEY_ACTIVE_CONNECTION);
		if (store.get(KEY_MEMBER_SECRETS) != null)
		{
			store.unset(KEY_MEMBER_SECRETS);
		}
	}

	/**
	 * The name the map last sent for a group, or null when it has sent none.
	 *
	 * <p>Null is ordinary: a legacy group has no name to send, an older map sends none, and a group
	 * joined a moment ago has not been answered yet. Every reader falls back to the code.
	 */
	@Nullable
	public String name(String code)
	{
		return tidyName(names().get(scopedKey(serverUrl(), code)));
	}

	/**
	 * File the name the map sent for one group.
	 *
	 * <p>Called from the game tick with the code the post was made under, for the reasons
	 * {@link #rememberSecret} is. And like it, a write only when the name differs: the map sends it
	 * on every reply, and a config write raises a {@code ConfigChanged} — every two seconds, for a
	 * value that never changed, without that check. Absent, empty or not a name leaves the one
	 * already held alone, because silence from the map means "keep what you have".
	 */
	public void rememberName(String code, @Nullable String name)
	{
		rememberName(serverUrl(), code, name);
	}

	public synchronized void rememberName(String server, String code, @Nullable String name)
	{
		String normalized = normalizeServer(server);
		String key = scopedKey(normalized, code);
		String tidy = tidyName(name);
		if (server == null || normalized == null || !JoinCode.isWellFormedCode(tidyCode(code)) || tidy == null)
		{
			return;
		}
		Map<String, String> names = names();
		if (tidy.equals(names.get(key)))
		{
			return;
		}
		names.put(key, tidy);
		store.set(KEY_GROUP_NAMES, gson.toJson(bounded(names), SECRETS_TYPE));
	}

	/**
	 * What arrived, if it is a name the map could have sent: control characters and runs of
	 * whitespace flattened to one space, as the map's own {@code normalizeGroupName} does, and
	 * nothing longer than it allows. Anything else is null, which every reader treats as no name.
	 */
	@Nullable
	static String tidyName(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}
		String tidy = name.replaceAll("[\\u0000-\\u001f\\u007f]", " ").replaceAll("\\s+", " ").trim();
		return tidy.isEmpty() || tidy.length() > MAX_GROUP_NAME ? null : tidy;
	}

	/** What is stored, as a map. Anything unreadable is simply not there, as with the secrets. */
	private Map<String, String> names()
	{
		return readMap(KEY_GROUP_NAMES);
	}

	private Map<String, String> readMap(String setting)
	{
		String json = store.get(setting);
		if (json == null || json.trim().isEmpty())
		{
			return new LinkedHashMap<>();
		}
		try
		{
			Map<String, String> parsed = gson.fromJson(json, SECRETS_TYPE);
			return parsed == null ? new LinkedHashMap<>() : parsed;
		}
		catch (JsonParseException e)
		{
			/** Hand-edited or half-written: the map sends the name again on the next reply. */
			return new LinkedHashMap<>();
		}
	}

	/**
	 * One code, written the way the stored keys are.
	 *
	 * <p>Both ends of the lookup go through this, because they do not agree otherwise: the code the
	 * post was made under comes from a settings field, where somebody may have typed lower case,
	 * while {@link #knownCodes} has upper-cased everything it holds since it was written. A secret
	 * filed under {@code ABC234} and looked for under {@code abc234} is a credential that is on
	 * disk, valid, and never sent — which is a stream that stays refused for no visible reason.
	 */
	private static String tidyCode(@Nullable String code)
	{
		return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
	}

	/** What is stored, as a map. Anything unreadable is simply not there, as with the code list. */
	private Map<String, String> secrets()
	{
		String json = store.get(KEY_MEMBER_SECRETS);
		if (json == null || json.trim().isEmpty())
		{
			return new LinkedHashMap<>();
		}
		Map<String, String> parsed;
		try
		{
			parsed = gson.fromJson(json, SECRETS_TYPE);
		}
		catch (JsonParseException e)
		{
			/** Hand-edited or half-written: the map will issue again for a member it has no secret for. */
			return new LinkedHashMap<>();
		}
		return parsed == null ? new LinkedHashMap<>() : parsed;
	}

	/**
	 * The secrets worth keeping: the group being posted to, and the groups there is a button to go
	 * back to.
	 *
	 * <p>Without this the object only ever grows, one entry per group ever joined, in a config file
	 * that is never pruned by anything else. Bounding it by the remembered list rather than by a
	 * count of its own is what makes forgetting a group forget its credential too — the list is
	 * already the answer to "which groups is this installation still in", and a second answer would
	 * drift from it.
	 */
	private Map<String, String> bounded(Map<String, String> secrets)
	{
		/* Keep remembered groups across at most eight servers; a URL change cannot re-key a secret. */
		Map<String, String> kept = new LinkedHashMap<>();
		List<String> codes = new ArrayList<>(knownCodes());
		codes.add(tidyCode(code()));
		for (Map.Entry<String, String> entry : secrets.entrySet())
		{
			String key = entry.getKey();
			/* A name is filed under server|code, a secret under server|code#member; both keep by code. */
			String scoped = key == null ? null : key.indexOf('#') < 0 ? key : key.substring(0, key.indexOf('#'));
			if (scoped != null && scoped.contains("|") && codes.contains(scoped.substring(scoped.lastIndexOf('|') + 1)))
			{
				kept.put(key, entry.getValue());
			}
		}
		while (kept.size() > MAX_KNOWN_CODES * MAX_KNOWN_CODES)
		{
			kept.remove(kept.keySet().iterator().next());
		}
		return kept;
	}

	/**
	 * Join what the player pasted.
	 *
	 * <p>The code goes in first and the suffix second, filed under the server and code it belongs
	 * to. Writing the code raises a {@code ConfigChanged}, and {@link MapCompanionPlugin} answers it
	 * on the client thread, possibly after this has returned, by throwing away a suffix filed under
	 * any other group ({@link #clearStaleSuffix}) — so the new one survives whichever runs first.
	 * The explicit clear in the middle drops the previous group's suffix even when nothing is
	 * listening, which is every unit test and a plugin that is not running.
	 */
	public void join(JoinCode joined)
	{
		join(joined, serverUrl());
	}

	private synchronized void join(JoinCode joined, String server)
	{
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, joined.code);
		clearSuffix();
		if (joined.suffix != null)
		{
			store.set(MapCompanionConfig.KEY_PAIRING_SUFFIX, joined.suffix);
			store.set(KEY_SUFFIX_CONNECTION, scopedKey(server, joined.code) + "|" + joined.suffix);
		}
		remember(joined.code);
	}

	/** Switch to a group already in the list. No suffix: the map knows this member there already. */
	public synchronized void switchTo(String code)
	{
		if (!JoinCode.isWellFormedCode(code))
		{
			return;
		}
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, code);
		clearSuffix();
		remember(code);
	}

	/**
	 * Stop sending the suffix.
	 *
	 * <p>Called when the map answers {@code claimed} — the binding is made, and a secret that has
	 * done its job is one worth deleting — and whenever the code changes.
	 */
	public synchronized void clearSuffix()
	{
		store.unset(KEY_SUFFIX_CONNECTION);
		if (store.get(MapCompanionConfig.KEY_PAIRING_SUFFIX) != null)
		{
			store.unset(MapCompanionConfig.KEY_PAIRING_SUFFIX);
		}
	}

	/**
	 * Drop a suffix minted for another group, and keep one filed under the current server and code.
	 *
	 * <p>For the plugin's reaction to a new code, which runs on the client thread some time after
	 * the write: by then {@link #join} has already filed the new code's suffix, and an unconditional
	 * clear would delete it before its first post.
	 */
	public synchronized void clearStaleSuffix()
	{
		String held = store.get(KEY_SUFFIX_CONNECTION);
		String code = code();
		if (held != null && code != null && held.startsWith(scopedKey(serverUrl(), code) + "|"))
		{
			return;
		}
		clearSuffix();
	}

	/**
	 * Leave: clear the code, drop the suffix and every secret, and forget the group was ever joined.
	 *
	 * <p>The list is a convenience for coming back, so a group somebody deliberately left has no
	 * business staying in it — a one-press way back into a group you left is the opposite of what
	 * the button says. Forgetting the installation's id is {@link MemberIdentity}'s half and stays
	 * with the panel, which is where the confirmation is.
	 *
	 * <p><b>Every</b> secret goes, not only this group's, and that is the id's doing rather than
	 * this group's: the panel forgets the member id in the same breath, and a secret says which
	 * member of a group this installation is. Leaving them behind would keep credentials for a
	 * member that no longer exists anywhere, which is the sort of thing a button called <i>Leave
	 * the group</i> is promising not to do.
	 */
	public synchronized void leave()
	{
		String leaving = code();
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "");
		clearSuffix();
		forgetSecrets();
		forget(leaving);
	}

	/** The remembered codes, most recent first. Anything unreadable is simply not there. */
	public List<String> knownCodes()
	{
		String json = store.get(KEY_KNOWN_CODES);
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		String[] parsed;
		try
		{
			parsed = gson.fromJson(json, String[].class);
		}
		catch (JsonParseException e)
		{
			/** A hand-edited or half-written value is not worth a stack trace or a crash. */
			return Collections.emptyList();
		}
		if (parsed == null)
		{
			return Collections.emptyList();
		}
		List<String> codes = new ArrayList<>();
		for (String candidate : parsed)
		{
			String tidy = candidate == null ? "" : candidate.trim().toUpperCase(Locale.ROOT);
			if (JoinCode.isWellFormedCode(tidy) && !codes.contains(tidy) && codes.size() < MAX_KNOWN_CODES)
			{
				codes.add(tidy);
			}
		}
		return codes;
	}

	/** Put a code at the head of the list, keeping it unique and bounded. */
	public void remember(String code)
	{
		String tidy = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
		if (!JoinCode.isWellFormedCode(tidy))
		{
			return;
		}
		List<String> codes = new ArrayList<>();
		codes.add(tidy);
		for (String existing : knownCodes())
		{
			if (!existing.equals(tidy) && codes.size() < MAX_KNOWN_CODES)
			{
				codes.add(existing);
			}
		}
		store.set(KEY_KNOWN_CODES, gson.toJson(codes));
	}

	public synchronized void forget(String code)
	{
		String tidy = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
		List<String> codes = new ArrayList<>();
		for (String existing : knownCodes())
		{
			if (!existing.equals(tidy))
			{
				codes.add(existing);
			}
		}
		store.set(KEY_KNOWN_CODES, gson.toJson(codes));
		/*
		 * And its name, now rather than at the next write: a group forgotten from the list is one
		 * nothing will ever name again, so waiting for {@link #bounded} would keep it indefinitely.
		 */
		Map<String, String> names = names();
		if (names.keySet().removeIf(key -> key != null && key.endsWith("|" + tidy)))
		{
			store.set(KEY_GROUP_NAMES, gson.toJson(names, SECRETS_TYPE));
		}
	}
}
