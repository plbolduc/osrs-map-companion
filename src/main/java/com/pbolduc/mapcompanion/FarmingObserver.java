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
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.function.IntUnaryOperator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;

/**
 * Locally witnessed patches and replants, never progress inferred from merely arriving.
 *
 * <p>Farming varbits are shared transmit slots whose meanings depend on the player's region.
 * Outside that region they retain another site's value; modal interfaces can delay updates too.
 * We read only the relevant slots, discard the first two ticks after an observation gap, and
 * accept completion only when consecutive local ticks show empty then a newly planted crop.
 * Natural growth, harvesting, health checks and a newly loaded growing patch do not satisfy it.
 *
 * <p>History survives restart under RuneScape profile storage, but continuity never does. The
 * earlier observer's installation used invented slot/state associations, so a new store key
 * deliberately starts from no observations instead of relabelling those readings as reliable.
 * Completion ages and random ids let the map reject an old run without trusting either clock.
 */
@Slf4j
@Singleton
public class FarmingObserver
{
	private static final String STORE_KEY = "patchObservationsV4";
	private static final Type STORE_TYPE = new TypeToken<Map<String, Observation>>() {} .getType();
	private static final long MAX_CONTINUITY_GAP_MS = 5_000L;
	private static final long RECEIPT_TTL_MS = 6 * 60 * 60_000L;
	private final SessionSettle settle = new SessionSettle(15_000L);

	private static final class Observation
	{
		int value;
		long atMs;
		Long completedAtMs;
		String completionId;
	}

	private static final class LocalReading
	{
		int value;
		int consecutive;
	}

	private final Client client;
	private final Gson gson;
	private final ConfigStore store;
	private final Map<String, Observation> observations = new HashMap<>();
	private final Map<String, LocalReading> local = new HashMap<>();
	private boolean loaded;
	private String loadedScope;
	private int lastTick = -1;
	private long lastReadAtMs;

	/**
	 * A newer "last seen" than the stored one, for a patch whose state has not changed. Standing
	 * at a patch moves only that time, so it is written when the session ends rather than every
	 * few seconds: the map is told the age live, and the store only has to know it after a restart.
	 */
	private boolean pendingPersist;

	@Inject
	private FarmingObserver(Client client, ConfigManager configManager, Gson gson)
	{
		this(client, ConfigStore.forRuneScapeProfile(configManager), gson);
	}

	FarmingObserver(Client client, ConfigStore store, Gson gson)
	{
		this.client = client;
		this.gson = gson;
		this.store = store;
	}

	/** Keep the existing login delay as well as the consecutive-tick observation baseline. */
	public void onSessionResumed(long nowMs)
	{
		settle.resumed(nowMs);
	}

	public void onSessionEnded()
	{
		settle.ended();
		resetSession();
		flush();
	}

	/** Write a pending "last seen" now: the session is ending or the character is changing. */
	void flush()
	{
		if (pendingPersist && loadedScope != null)
		{
			persist();
		}
	}

	/** One cheap local scan per game tick. Network scheduling remains the plugin's existing floor. */
	public boolean observe(long nowMs, int regionId)
	{
		if (!settle.settled(nowMs) || regionId < 0 || client.getGameState() != GameState.LOGGED_IN
			|| client.getLocalPlayer() == null || WorldTiles.isInstance(client) || client.getComponentTable() == null)
		{
			resetSession();
			return false;
		}
		// RuneLite's FarmingTracker also suppresses reads while a modal blocks fresh varbits.
		for (WidgetNode widget : client.getComponentTable())
		{
			if (widget.getModalMode() != WidgetModalMode.NON_MODAL)
			{
				resetSession();
				return false;
			}
		}
		return observeAt(client.getLocalPlayer().getWorldLocation(), client.getTickCount(), nowMs, client::getVarbitValue);
	}

	/** Package-visible seam for testing real region/value traces without a running game client. */
	boolean observeAt(WorldPoint location, int tick, long nowMs, IntUnaryOperator read)
	{
		ensureLoaded();
		if (!loaded)
		{
			return false;
		}
		if (tick == lastTick)
		{
			return false;
		}
		if (tick != lastTick + 1 || nowMs < lastReadAtMs || nowMs - lastReadAtMs > MAX_CONTINUITY_GAP_MS)
		{
			local.clear();
		}
		lastTick = tick;
		lastReadAtMs = nowMs;
		boolean changed = false;
		boolean completed = false;
		for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
		{
			if (!patch.isLocal(location))
			{
				local.remove(patch.id);
				continue;
			}
			int value;
			try
			{
				value = read.applyAsInt(patch.varbit);
			}
			catch (RuntimeException e)
			{
				local.remove(patch.id);
				continue;
			}
			PatchStates.Decoded decoded = PatchStates.decode(patch.kind, value);
			if (PatchStates.UNKNOWN.equals(decoded.state))
			{
				local.remove(patch.id);
				continue;
			}
			LocalReading previous = local.get(patch.id);
			LocalReading next = new LocalReading();
			next.value = value;
			next.consecutive = previous == null ? 1 : Math.min(3, previous.consecutive + 1);
			local.put(patch.id, next);
			// Two settling ticks protect against slots received just before the region changed.
			if (next.consecutive < 3)
			{
				continue;
			}
			Observation observation = observations.get(patch.id);
			if (observation == null)
			{
				observation = new Observation();
				observations.put(patch.id, observation);
				changed = true;
			}
			if (observation.value != value)
			{
				changed = true;
			}
			boolean replant = previous.consecutive >= 3 && decoded.newlyPlanted
							  && PatchStates.EMPTY.equals(PatchStates.decode(patch.kind, previous.value).state);
			if (replant)
			{
				observation.completedAtMs = nowMs;
				observation.completionId = UUID.randomUUID().toString();
				completed = true;
			}
			observation.value = value;
			observation.atMs = nowMs;
			pendingPersist = true;
		}
		if (changed || completed)
		{
			persist();
		}
		return changed || completed;
	}

	/** Stable catalogue order makes payload truncation predictable and never invents unknown ids. */
	public List<CompanionSnapshot.Patch> observations(long nowMs)
	{
		ensureLoaded();
		List<CompanionSnapshot.Patch> out = new ArrayList<>();
		for (FarmingPatchCatalog.Patch definition : FarmingPatchCatalog.PATCHES)
		{
			Observation o = observations.get(definition.id);
			if (o == null || o.atMs > nowMs)
			{
				continue;
			}
			PatchStates.Decoded decoded = PatchStates.decode(definition.kind, o.value);
			CompanionSnapshot.Patch patch = new CompanionSnapshot.Patch();
			patch.id = definition.id;
			patch.state = decoded.state;
			patch.produce = decoded.produce;
			patch.stagesLeft = decoded.stagesLeft;
			patch.observedMsAgo = nowMs - o.atMs;
			if (decoded.stagesLeft != null && decoded.growthMinutes != null)
			{
				long cycleMs = decoded.growthMinutes * 60_000L;
				patch.readyInMinMs = Math.max(0, decoded.stagesLeft - 1) * cycleMs;
				patch.readyInMaxMs = decoded.stagesLeft * cycleMs;
			}
			if (o.completedAtMs != null && o.completedAtMs <= nowMs && o.completionId != null
				&& nowMs - o.completedAtMs <= RECEIPT_TTL_MS)
			{
				patch.completedMsAgo = nowMs - o.completedAtMs;
				patch.completionId = o.completionId;
			}
			out.add(patch);
		}
		return out;
	}

	public void forget()
	{
		ensureLoaded();
		observations.clear();
		resetSession();
		pendingPersist = false;
		store.unsetScoped(loadedScope, STORE_KEY);
	}

	/** A gap (logout, hop, modal, disabled sharing) ends evidence continuity, never the history. */
	public void resetSession()
	{
		local.clear();
		lastTick = -1;
		lastReadAtMs = 0;
	}

	/** Persisted history belongs only to its RS profile; an account switch starts a new baseline. */
	public void resetProfile()
	{
		observations.clear();
		resetSession();
		loaded = false;
		pendingPersist = false;
	}

	private void ensureLoaded()
	{
		String scope = store.scopeKey();
		if (!Objects.equals(scope, loadedScope))
		{
			/* Still the previous character's: written there before it is dropped. */
			flush();
			resetProfile();
			loadedScope = scope;
		}
		if (scope == null)
		{
			return;
		}
		if (loaded)
		{
			return;
		}
		loaded = true;
		try
		{
			String raw = store.getScoped(scope, STORE_KEY);
			if (raw == null || raw.isEmpty())
			{
				return;
			}
			Map<String, Observation> stored = gson.fromJson(raw, STORE_TYPE);
			if (stored != null)
			{
				for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
				{
					Observation value = stored.get(patch.id);
					if (value != null && value.atMs >= 0 && value.value >= 0 && value.value <= 255)
					{
						observations.put(patch.id, value);
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read stored patch observations", e);
		}
	}

	private void persist()
	{
		try
		{
			// Pin the observed character; a profile switch must not redirect this write.
			store.setScoped(loadedScope, STORE_KEY, gson.toJson(observations, STORE_TYPE));
			pendingPersist = false;
		}
		catch (RuntimeException e)
		{
			log.debug("Could not store patch observations", e);
		}
	}
}
