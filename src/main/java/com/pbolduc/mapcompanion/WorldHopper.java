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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.EnumSet;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.World;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;

/**
 * Hopping to the world a star is on, when the player asks for it.
 *
 * <p>The same RuneLite switcher APIs used by the Star Miners plugin, behind an explicit request:
 *
 * <ul>
 *   <li><b>Off by default.</b> {@code enableWorldHop} starts false, so an install that never opens
 *       the settings can never hop.
 *   <li><b>Only from a press.</b> Nothing here is reachable from a tick, a directive, or anything
 *       arriving over the network. The map can put a world on your screen; only your finger moves
 *       you to it.
 *   <li><b>While logged in.</b> This checks the login state; the game's world switcher enforces
 *       its own hopping restrictions. Idle and combat state are not checked here.
 *   <li><b>Only to an ordinary world.</b> The world number comes from a group member's
 *       destination, so one press must never be able to carry a player onto a PvP or Deadman world,
 *       or onto a world with a separate save. Those are refused by type once the list has loaded.
 * </ul>
 *
 * <p>The mechanics mirror the plugin above deliberately, including opening the switcher first —
 * {@code hopToWorld} needs the world list loaded, and the client populates it when that interface
 * opens.
 */
@Slf4j
@Singleton
public class WorldHopper
{
	/** The switcher does not always open on the first ask; the star plugin gives it three too. */
	private static final int MAX_OPEN_ATTEMPTS = 3;

	private final Client client;
	private final ClientThread clientThread;

	/**
	 * The world a press asked for, written on the EDT. The client thread adopts it into
	 * {@code pendingWorld}, which with {@code openAttempts} only it touches, so a press and a
	 * cancel never race the tick that is driving the switcher.
	 */
	private volatile int requestedWorld = 0;
	private int pendingWorld = 0;
	private int openAttempts = 0;

	/** Why the last request was turned down once the world list was read, until the panel takes it. */
	@Nullable
	private volatile String refusal;

	/** The world {@link #refusal} is about, so it is not shown against another destination. */
	private volatile int refusedWorld;

	/**
	 * World types a press must never reach. Dangerous ones first — a death there costs items — then
	 * the ones that keep a separate save or a separate ruleset, where arriving by surprise is its own
	 * kind of loss. Members and skill-total worlds are left to the game, which refuses them itself.
	 */
	private static final EnumSet<WorldType> REFUSED = EnumSet.of(
		WorldType.PVP, WorldType.BOUNTY, WorldType.HIGH_RISK, WorldType.DEADMAN,
		WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING, WorldType.SEASONAL, WorldType.BETA_WORLD,
		WorldType.TOURNAMENT_WORLD, WorldType.FRESH_START_WORLD, WorldType.NOSAVE_MODE,
		WorldType.QUEST_SPEEDRUNNING);

	@Inject
	private WorldHopper(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	/**
	 * Ask to hop. Returns false when the request is refused, so the caller can say why rather than
	 * leaving a button that appears to do nothing.
	 *
	 * <p>Called only from a button press on the sidebar. If this ever gains a second caller, that
	 * caller has to be a person too.
	 */
	public boolean requestHop(int world)
	{
		if (world <= 0)
		{
			return false;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		refusal = null;
		requestedWorld = world;
		return true;
	}

	public void cancel()
	{
		requestedWorld = 0;
	}

	public boolean isHopPending()
	{
		return requestedWorld > 0;
	}

	/** The reason a request for this world was refused, once; null when there is nothing to say. */
	@Nullable
	public String takeRefusal(int world)
	{
		String said = refusal;
		if (said == null || refusedWorld != world)
		{
			return null;
		}
		refusal = null;
		return said;
	}

	/** Null for a world a press may reach, or the words for why it may not. */
	@Nullable
	static String refusalFor(@Nullable Collection<WorldType> types)
	{
		if (types == null)
		{
			/* A world whose type the client cannot say is not one to arrive on by surprise. */
			return "Can't tell what world that is";
		}
		if (WorldType.isPvpWorld(types) || types.contains(WorldType.BOUNTY) || types.contains(WorldType.DEADMAN))
		{
			return "Won't hop to a PvP world";
		}
		if (types.contains(WorldType.HIGH_RISK))
		{
			return "Won't hop to a high-risk world";
		}
		for (WorldType type : types)
		{
			if (REFUSED.contains(type))
			{
				return "Won't hop to a special world";
			}
		}
		return null;
	}


	/**
	 * Drive a requested hop forward one tick.
	 *
	 * <p>Called from the plugin's game tick and does nothing at all unless a press has set a
	 * target — which is what keeps "only from a press" true even though this runs constantly.
	 *
	 * <p>Must run on the client thread; the caller is already there.
	 */
	public void onGameTick()
	{
		int requested = requestedWorld;
		if (requested != pendingWorld)
		{
			pendingWorld = requested;
			openAttempts = 0;
		}
		if (pendingWorld <= 0)
		{
			return;
		}
		if (client.getWorld() == pendingWorld)
		{
			/** Already there. Not a failure, but not something to make the client do. */
			finish();
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			/** Logged out mid-request: the target is stale and hopping now would be a surprise. */
			finish();
			return;
		}

		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			clientThread.invokeLater(client::openWorldHopper);
			if (++openAttempts >= MAX_OPEN_ATTEMPTS)
			{
				log.debug("could not open the world switcher after {} attempts", openAttempts);
				finish();
			}
			return;
		}

		World[] worlds = client.getWorldList();
		if (worlds == null)
		{
			return;
		}

		for (World world : worlds)
		{
			if (world.getId() == pendingWorld)
			{
				String refused = refusalFor(world.getTypes());
				if (refused != null)
				{
					log.debug("refused a hop to world {}: {}", pendingWorld, world.getTypes());
					refusedWorld = pendingWorld;
					refusal = refused;
				}
				else
				{
					client.hopToWorld(world);
				}
				finish();
				return;
			}
		}

		/** The world list loaded and the target is not in it — nothing to wait for. */
		log.debug("world {} is not in the client's world list", pendingWorld);
		finish();
	}

	/** End the request being driven. Client thread only. */
	private void finish()
	{
		if (requestedWorld == pendingWorld)
		{
			requestedWorld = 0;
		}
		pendingWorld = 0;
		openAttempts = 0;
	}
}
