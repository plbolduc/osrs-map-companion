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
import java.util.EnumSet;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reads only this installation's approved map server, while Stars is visible and logged in.
 * No Star Miners address or key exists in the plugin. Cancelling or changing a character,
 * group or server invalidates callbacks before they can touch the new reader's preferences.
 * The filters and favourite sites are kept in RuneLite's character profile on this machine and
 * are never sent: the list is fetched whole and filtered here.
 */
@Singleton
final class StarsClient
{
	/** What a character's filters are kept as. A save from a release that synced them reads the same. */
	static final class Saved
	{
		StarsModel.Preferences preferences = new StarsModel.Preferences();
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final Pairing pairing;
	private final MemberIdentity identity;
	private final ConfigStore store;
	final StarsModel model = new StarsModel();
	/**
	 * Why the last ask for the list failed, or null. Nothing else: the tab says "Loading stars…" and
	 * "log in" from {@link #eligible()} and the feed, and how old a held list is from the feed, so a
	 * sentence set here can never outlive what it described.
	 */
	String message;
	private boolean active, eligible;
	private long generation, nextFeed;
	private String connection, scope, storageKey;
	private Call feedCall;

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
		feedCall = null;
	}

	void tick()
	{
		if (!active) return;
		clientThread.invokeLater(() -> {
			String server = pairing.serverUrl(), code = pairing.code(), profile = store.scopeKey();
			GameState state = client.getGameState();
			boolean connected = profile != null && code != null && !code.isEmpty() && pairing.hasServerConsent(server, code);
			boolean allowed = connected && state == GameState.LOGGED_IN;
			boolean between = connected && betweenWorlds(state);
			Integer mining = allowed ? client.getRealSkillLevel(Skill.MINING) : null;
			Integer total = allowed ? client.getTotalLevel() : null;
			if (mining != null && mining < 1) mining = null;
			if (total != null && total < 1) total = null;
			Integer knownMining = mining, knownTotal = total;
			String game = allowed ? gameOf(client.getWorldType()) : null;
			Integer world = allowed ? (Integer) client.getWorld() : null;
			SwingUtilities.invokeLater(() -> update(server, code, profile, allowed, between, knownMining, knownTotal, game, world));
		});
	}

	/**
	 * A hop, a loading screen, and a dropped connection the game is still reconnecting: the same
	 * character on the same connection, which is how the plugin's own stream treats all three. A
	 * reconnect that fails ends on the login screen, and that is where the list is cleared.
	 */
	private static boolean betweenWorlds(GameState state)
	{
		return state == GameState.HOPPING || state == GameState.LOADING || state == GameState.CONNECTION_LOST;
	}

	/**
	 * The game a world's type says the player is in, named as the map names a star's. Quest
	 * Speedrunning is a game no star is listed in, so its list is empty and says why. Everything
	 * else, Deadman and PvP included, is the main game's characters; the risk filters own those.
	 */
	static String gameOf(EnumSet<WorldType> types)
	{
		if (types == null) return StarsModel.MAIN;
		if (types.contains(WorldType.SEASONAL)) return StarsModel.LEAGUE;
		if (types.contains(WorldType.HARDCORE_MAIN)) return StarsModel.HARDCORE;
		if (types.contains(WorldType.QUEST_SPEEDRUNNING) || types.contains(WorldType.FRESH_START_WORLD)) return StarsModel.NONE;
		return StarsModel.MAIN;
	}

	private void update(String server, String code, String profile, boolean allowed, boolean between, Integer mining, Integer total, String game, Integer world)
	{
		if (!active) return;
		/* The list is a character's: none until RuneLite names the one logged in. */
		String member = allowed || between ? identity.id() : null;
		if (member == null)
		{
			eligible = false;
			cancel();
			model.feed = null;
			message = null;
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
			message = null;
			nextFeed = 0;
			Saved saved = null;
			try
			{
				saved = gson.fromJson(store.getScoped(scope, storageKey), Saved.class);
			}
			catch (RuntimeException ignored)
			{
				/* An unreadable save starts from the defaults rather than from half of one. */
			}
			model.preferences = saved == null ? new StarsModel.Preferences() : validPreferences(saved.preferences);
		}
		eligible = true;
		/*
		 * Between worlds the list, the pick, its words and a call in flight all stay, and nothing is
		 * asked until the game is back on one: a hop costs no request and moves neither schedule. The
		 * levels are not read there either, so the filters do not change under the player mid-hop.
		 */
		if (between) return;
		model.miningLevel = mining;
		model.skillTotal = total;
		model.playerWorld = world;
		if (game != null) model.playerGame = game;
		if (feedCall == null && System.currentTimeMillis() >= nextFeed) fetchFeed(server);
	}

	/** A filter or a favourite changed on the tab: kept for this character, on this machine. */
	void changed()
	{
		if (scope == null || storageKey == null) return;
		Saved saved = new Saved();
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
		preferences.favorites.removeIf(favorite -> favorite == null || favorite.key == null || favorite.name == null
			|| favorite.name.isEmpty() || favorite.name.length() > 200);
		return preferences;
	}

	private Request.Builder request(String server, String path)
	{
		HttpUrl url = HttpUrl.parse(server + path);
		if (url == null) throw new IllegalArgumentException("Invalid map address");
		return CompanionHttp.request(url);
	}

	private void fetchFeed(String server)
	{
		long epoch = generation;
		nextFeed = System.currentTimeMillis() + 60_000;
		feedCall = http.newCall(request(server, "/api/companion/stars").get().build());
		send(feedCall, epoch, response -> {
			StarsModel.Feed incoming = gson.fromJson(response, StarsModel.Feed.class);
			model.accept(incoming);
			nextFeed = System.currentTimeMillis() + Math.max(60_000, Math.min(300_000, incoming.pollIntervalMs));
			message = null;
		});
	}

	private void send(Call call, long epoch, Consumer<String> accept)
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
					long delay = MapWait.retryDelay(close.header("Retry-After"));
					if (close.isRedirect())
					{
						finish(null, Words.of("map.redirects"), 300_000);
						return;
					}
					if (!close.isSuccessful())
					{
						finish(null, "Stars unavailable. Retrying shortly.", delay);
						return;
					}
					finish(CompanionHttp.readBounded(close.body()), null, delay);
				}
				catch (IOException | RuntimeException error)
				{
					finish(null, RoutePress.UNREADABLE, 30_000);
				}
			}

			private void finish(String text, String error, long delay)
			{
				SwingUtilities.invokeLater(() -> {
					if (epoch != generation) return;
					/* Released before the checks below, so a reply that is then ignored cannot hold the slot. */
					feedCall = null;
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
							errorResult(RoutePress.UNREADABLE, delay);
						}
					}
					else errorResult(error, delay);
				});
			}

			private void errorResult(String error, long delay)
			{
				message = error;
				nextFeed = System.currentTimeMillis() + Math.max(60_000, delay);
			}
		});
	}
}
