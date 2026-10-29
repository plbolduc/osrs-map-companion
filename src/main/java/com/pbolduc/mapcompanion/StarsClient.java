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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reads only this installation's approved map server, while Stars is visible and logged in.
 * No Star Miners address or key exists in the plugin. Cancelling or changing a character,
 * group or server invalidates callbacks before they can touch the new reader's preferences.
 * RuneLite's character profile keeps the independent copy and sync choice on this machine.
 */
@Singleton
final class StarsClient
{
	static final class Saved
	{
		boolean sync = true;
		StarsModel.Preferences preferences = new StarsModel.Preferences();
	}

	/**
	 * How often an open Stars tab asks whether the browser changed something. The map spends its
	 * shared read bucket on this, the same one the group panel's reads draw from, so it is paced
	 * for an idle tab rather than for immediacy; an edit made here is sent within a beat anyway,
	 * see {@link #changed()}, and opening the tab asks at once.
	 */
	static final long PREFERENCE_POLL_MS = 30_000L;
	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final MapCompanionConfig config;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final ConfigStore store;
	final StarsModel model = new StarsModel();
	boolean sync = true;
	boolean syncReady;
	String message = "Open Stars while logged in and connected to your map.";
	private boolean active, eligible, dirty;

	/**
	 * Quick re-sends left for an edit the map has not yet confirmed. A conflict is answered at once,
	 * with the map's revision; a map that keeps answering without the preferences it holds would
	 * otherwise be asked again three times a second for as long as the tab is open.
	 */
	private static final int QUICK_RESENDS = 3;
	private int quickResends = QUICK_RESENDS;
	private long generation, editVersion, revision, nextFeed, nextPreferences;
	private String connection, scope, storageKey;
	private Call feedCall, preferenceCall;

	@Inject
	StarsClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread,
		MapCompanionConfig config, Pairing pairing, MemberIdentity identity, ConfigManager manager)
	{
		this(http, gson, client, clientThread, config, pairing, identity, ConfigStore.forRuneScapeProfile(manager));
	}

	StarsClient(OkHttpClient http, Gson gson, Client client, ClientThread clientThread,
		MapCompanionConfig config, Pairing pairing, MemberIdentity identity, ConfigStore store)
	{
		this.http = CompanionHttp.pinnedToServer(http);
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.pairing = pairing;
		this.identity = identity;
		this.store = store;
	}

	void active(boolean on)
	{
		active = on;
		eligible = false;
		cancel();
		if (on)
		{
			nextPreferences = 0;
			tick();
		}
	}

	void stop()
	{
		active(false);
	}

	boolean eligible()
	{
		return eligible;
	}

	private void cancel()
	{
		generation++;
		if (feedCall != null) feedCall.cancel();
		if (preferenceCall != null) preferenceCall.cancel();
		feedCall = null;
		preferenceCall = null;
	}

	void tick()
	{
		if (!active) return;
		clientThread.invokeLater(() -> {
			String server = pairing.serverUrl(), code = pairing.code(), profile = store.scopeKey();
			boolean allowed = client.getGameState() == GameState.LOGGED_IN && profile != null
				&& code != null && !code.isEmpty() && pairing.hasServerConsent(server, code);
			Integer mining = allowed ? client.getRealSkillLevel(Skill.MINING) : null;
			Integer total = allowed ? client.getTotalLevel() : null;
			if (mining != null && mining < 1) mining = null;
			if (total != null && total < 1) total = null;
			Integer knownMining = mining, knownTotal = total;
			SwingUtilities.invokeLater(() -> update(server, code, profile, allowed, knownMining, knownTotal));
		});
	}

	private void update(String server, String code, String profile, boolean allowed, Integer mining, Integer total)
	{
		if (!active) return;
		/* The mailbox is this character's member's, so no character means no mailbox yet. */
		String member = allowed ? identity.id() : null;
		if (member == null)
		{
			eligible = false;
			cancel();
			model.feed = null;
			syncReady = false;
			message = "Log in and connect to your map to see stars.";
			return;
		}
		String incoming = server + "\n" + code + "\n" + member + "\n" + profile;
		if (!incoming.equals(connection))
		{
			cancel();
			connection = incoming;
			scope = profile;
			storageKey = "starPreferences:" + Base64.getUrlEncoder().withoutPadding().encodeToString(server.getBytes(StandardCharsets.UTF_8));
			model.feed = null;
			model.selected = null;
			revision = 0;
			syncReady = false;
			dirty = false;
			nextFeed = 0;
			nextPreferences = 0;
			Saved saved = null;
			try
			{
				saved = gson.fromJson(store.getScoped(scope, storageKey), Saved.class);
			}
			catch (RuntimeException ignored)
			{
				/* An unreadable save starts from the defaults rather than from half of one. */
			}
			sync = saved == null || saved.sync;
			model.preferences = saved == null ? new StarsModel.Preferences() : validPreferences(saved.preferences);
		}
		eligible = true;
		model.miningLevel = mining;
		model.skillTotal = total;
		long now = System.currentTimeMillis();
		if (feedCall == null && now >= nextFeed) fetchFeed(server);
		if (sync && preferenceCall == null && pairing.secret(server, code, member) != null && now >= nextPreferences) fetchPreferences(server, code, member);
	}

	void sync(boolean on)
	{
		sync = on;
		dirty = false;
		editVersion++;
		nextPreferences = 0;
		cancel();
		save();
		tick();
	}

	void changed()
	{
		editVersion++;
		quickResends = QUICK_RESENDS;
		dirty = sync;
		nextPreferences = Math.min(nextPreferences, System.currentTimeMillis() + 300);
		save();
	}

	private void save()
	{
		if (scope == null || storageKey == null) return;
		Saved saved = new Saved();
		saved.sync = sync;
		saved.preferences = model.preferences;
		store.setScoped(scope, storageKey, gson.toJson(saved));
	}

	static StarsModel.Preferences validPreferences(StarsModel.Preferences preferences)
	{
		if (preferences == null || preferences.filters == null || preferences.favorites == null || preferences.favorites.size() > 200)
			return new StarsModel.Preferences();
		StarsModel.Filters f = preferences.filters;
		f.minTier = Math.max(1, Math.min(9, f.minTier));
		if (!f.showMembers && !f.showFreeToPlay) f.showMembers = true;
		if (!"only".equals(f.leagueWorlds)) f.leagueWorlds = "exclude";
		preferences.favorites.removeIf(favorite -> favorite == null || favorite.key == null || favorite.name == null
			|| favorite.name.isEmpty() || favorite.name.length() > 200);
		return preferences;
	}

	private Request.Builder request(String server, String path)
	{
		HttpUrl url = HttpUrl.parse(server + path);
		if (url == null) throw new IllegalArgumentException("Invalid map address");
		return new Request.Builder().url(url).header("User-Agent", CompanionVersion.USER_AGENT);
	}

	private void fetchFeed(String server)
	{
		long epoch = generation;
		nextFeed = System.currentTimeMillis() + 60_000;
		feedCall = http.newCall(request(server, "/api/companion/stars").get().build());
		send(feedCall, false, epoch, response -> {
			StarsModel.Feed incoming = gson.fromJson(response, StarsModel.Feed.class);
			model.accept(incoming);
			nextFeed = System.currentTimeMillis() + Math.max(60_000, Math.min(300_000, incoming.pollIntervalMs));
			message = incoming.stale ? "Saved feed · upstream unavailable" : null;
		});
	}

	private void fetchPreferences(String server, String code, String member)
	{
		long epoch = generation, edits = editVersion;
		nextPreferences = System.currentTimeMillis() + PREFERENCE_POLL_MS;
		Request.Builder builder = request(server, "/api/companion/star-preferences")
			.header("X-Companion-Code", code).header("X-Companion-Member", member)
			.header("X-Companion-Member-Secret", pairing.secret(server, code, member));
		boolean writing = dirty && syncReady;
		if (writing)
		{
			StarsModel.PreferenceReply body = new StarsModel.PreferenceReply();
			body.revision = revision;
			body.preferences = model.preferences;
			builder.put(RequestBody.create(MediaType.get("application/json; charset=utf-8"), gson.toJson(body)));
		}
		else builder.get();
		preferenceCall = http.newCall(builder.build());
		send(preferenceCall, true, epoch, text -> {
			StarsModel.PreferenceReply reply = gson.fromJson(text, StarsModel.PreferenceReply.class);
			if (reply == null || reply.revision == null || reply.revision < 0) throw new IllegalArgumentException("Unreadable preferences");
			revision = reply.revision;
			syncReady = true;
			if (reply.preferences != null && edits == editVersion && (!dirty || writing))
			{
				model.preferences = validPreferences(reply.preferences);
				dirty = false;
				save();
			}
			if (!dirty) quickResends = QUICK_RESENDS;
			else if (quickResends > 0)
			{
				quickResends--;
				nextPreferences = System.currentTimeMillis() + 300;
			}
		});
	}

	private void send(Call call, boolean preference, long epoch, Consumer<String> accept)
	{
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failed, IOException error)
			{
				finish(null, "The map is unreachable.", 30_000);
			}

			@Override public void onResponse(Call completed, Response response)
			{
				try (Response close = response)
				{
					long delay = retryDelay(close.header("Retry-After"));
					if (close.isRedirect())
					{
						finish(null, "This map address redirects. Check Settings → Advanced → Map address.", 300_000);
						return;
					}
					if (!close.isSuccessful() && !(preference && close.code() == 409))
					{
						finish(null, preference ? "Pair with your personal RuneLite code to sync filters." : "Stars unavailable. Retrying shortly.", delay);
						return;
					}
					finish(readBounded(close.body()), null, delay);
				}
				catch (IOException | RuntimeException error)
				{
					finish(null, "The map returned unreadable data.", 30_000);
				}
			}

			private void finish(String text, String error, long delay)
			{
				SwingUtilities.invokeLater(() -> {
					if (epoch != generation) return;
					/* Released before the checks below, so a reply that is then ignored cannot hold the slot. */
					if (preference) preferenceCall = null;
					else feedCall = null;
					if (!active || !pairing.hasServerConsent() || scope == null || !scope.equals(store.scopeKey())) return;
					if (error == null)
					{
						try
						{
							accept.accept(text);
							return;
						}
						catch (RuntimeException unreadable)
						{
							errorResult("The map returned unreadable data.", delay);
						}
					}
					else errorResult(error, delay);
				});
			}

			private void errorResult(String error, long delay)
			{
				if (preference)
				{
					syncReady = false;
					nextPreferences = System.currentTimeMillis() + Math.max(5_000, delay);
				}
				else
				{
					message = error;
					nextFeed = System.currentTimeMillis() + Math.max(60_000, delay);
				}
			}
		});
	}

	static long retryDelay(String header)
	{
		try
		{
			return Math.max(5_000, Math.min(300_000, Long.parseLong(header) * 1_000));
		}
		catch (RuntimeException invalid)
		{
			return 30_000;
		}
	}

	static String readBounded(ResponseBody body) throws IOException
	{
		return CompanionHttp.readBounded(body, CompanionHttp.MAX_REPLY_BYTES);
	}
}
