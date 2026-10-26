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
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.timetracking.farming.Produce;
import net.runelite.client.util.Text;

/**
 * Only an observed assignment leaves the client, never the dialogue it was read from.
 * Rumours are confirmed by the whistle's current-target message; merely discussing a creature
 * is not an assignment. Mahogany Homes uses the accepted-job and reminder sentences. Their
 * reviewed source pins and licences travel with the generated name lists in resources.
 *
 * <p>These observations expire on logout, hopping and withdrawal of consent. Farming contracts
 * are read from the same variables and Produce catalogue as RuneLite Time Tracking, after the
 * login settling window; no regional patch state is used to infer the current assignment.
 */
@Singleton
public class AssignmentReader
{
	private static final long MAX_AGE_MS = 2 * 60 * 60_000L;
	private static final List<String> RUMOURS = names("assignment-rumours.txt");
	private static final List<String> HOMES = names("assignment-homes.txt");
	private static final String[] HUNTERS = {"Wolf", "Teco", "Aco", "Cervus", "Ornus", "Gilman"};
	private static final int[] HUNTER_IDS = {13126, 13125, 13124, 13123, 13122, 13121};
	private static final Pattern HOME_ASSIGNED = Pattern.compile("(\\w+) Contract: Go see (\\w+)[ ,][\\w\\s,-]*\\.");
	private static final Pattern HOME_DIALOG = Pattern.compile("(?:Please could you g|G)o see (\\w+)[ ,][\\w\\s,-]*[?.] You can get another job once you have furnished \\w* home\\.");
	private static final Pattern HOME_REMINDER = Pattern.compile("You're currently on an? (\\w+) Contract\\. Go see (\\w+)[ ,][\\w\\s,-]*\\. You can get another job once you have furnished \\w* home\\.");
	private static final Pattern HOME_FINISHED = Pattern.compile("You have completed [\\d,]+ contracts with a total of [\\d,]+ points?\\.");
	private final Client client;
	private final MapCompanionConfig config;
	private final SessionSettle settle = new SessionSettle(15_000L);
	private CompanionSnapshot.Assignment rumour;
	private CompanionSnapshot.Assignment home;
	private long rumourAt;
	private long homeAt;
	private String lastHomeDialog;

	@Inject
	AssignmentReader(Client client, MapCompanionConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void onSessionEnded()
	{
		settle.ended();
		rumour = null;
		home = null;
		lastHomeDialog = null;
	}

	void onSharingChanged()
	{
		if (!config.shareHunterRumours())
		{
			rumour = null;
		}
		if (!config.shareMahoganyHomes())
		{
			home = null;
			lastHomeDialog = null;
		}
	}

	void observe(long now)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		settle.resumed(now);
		onSharingChanged();
		if (config.shareMahoganyHomes())
		{
			Widget dialog = client.getWidget(InterfaceID.ChatLeft.TEXT);
			String text = dialog == null ? null : Text.sanitizeMultilineText(dialog.getText());
			if (text != null && !text.equals(lastHomeDialog))
			{
				readHomeDialog(text, now);
			}
			lastHomeDialog = text;
		}
	}

	void onChatMessage(ChatMessage event, long now)
	{
		if (client.getGameState() != GameState.LOGGED_IN || event.getMessage() == null)
		{
			return;
		}
		String text = Text.removeTags(event.getMessage());
		String lower = text.toLowerCase(Locale.ROOT);
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			if (config.shareHunterRumours())
			{
				if (text.startsWith("Your current rumour target is"))
				{
					String name = referenced(RUMOURS, lower);
					String hunter = referenced(java.util.Arrays.asList(HUNTERS), lower);
					// Rechecking the same assignment cannot un-find an observed rare part.
					boolean same = rumour != null && rumour.name.equals(name) && java.util.Objects.equals(rumour.detail, hunter);
					if (!same)
					{
						rumour = name == null ? null : assignment(name, hunter);
					}
					rumourAt = now;
				}
				else if (lower.contains("you find a rare piece of the creature! you should take it back to the hunter guild.") && rumour != null)
				{
					rumour.state = "return";
					rumourAt = now;
				}
			}
			if (config.shareMahoganyHomes())
			{
				Matcher assigned = HOME_ASSIGNED.matcher(text);
				if (assigned.matches())
				{
					setHome(assigned.group(2), assigned.group(1), now);
				}
				else if (HOME_FINISHED.matcher(text).matches())
				{
					home = null;
				}
			}
		}
		else if (config.shareHunterRumours() && event.getType() == ChatMessageType.DIALOG && inBurrows())
		{
			for (int id : HUNTER_IDS)
			{
				if (client.getNpcDefinition(id) != null && text.startsWith(client.getNpcDefinition(id).getName() + "|")
					&& (lower.contains("would you like another rumour?") || lower.contains("here's your reward.") || lower.contains("another one done?")))
				{
					rumour = null;
					return;
				}
			}
		}
	}

	void readHomeDialog(String text, long now)
	{
		Matcher reminder = HOME_REMINDER.matcher(text);
		Matcher assigned = HOME_DIALOG.matcher(text);
		if (reminder.matches())
		{
			setHome(reminder.group(2), reminder.group(1), now);
		}
		else if (assigned.matches())
		{
			setHome(assigned.group(1), null, now);
		}
	}

	private void setHome(String name, String tier, long now)
	{
		String known = HOMES.stream().filter(value -> value.equalsIgnoreCase(name)).findFirst().orElse(null);
		home = known == null ? null : assignment(known, tier);
		homeAt = now;
	}

	@Nullable
	CompanionSnapshot.Assignment rumour(long now)
	{
		return config.shareHunterRumours() ? aged(rumour, rumourAt, now) : null;
	}

	@Nullable
	CompanionSnapshot.Assignment home(long now)
	{
		return config.shareMahoganyHomes() ? aged(home, homeAt, now) : null;
	}

	@Nullable
	CompanionSnapshot.Assignment farming(long now)
	{
		if (!config.shareFarmingContracts() || !settle.settled(now))
		{
			return null;
		}
		try
		{
			if (client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE) != 0)
			{
				return null;
			}
			int value = client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE);
			if (value <= 0)
			{
				return null;
			}
			for (Produce produce : Produce.values())
			{
				if (produce.getContractVarbitValue() == value)
				{
					String crop = String.valueOf(produce.getPatchImplementation()).toLowerCase(Locale.ROOT).replace('_', ' ');
					return assignment(produce.getName(), crop);
				}
			}
		}
		catch (RuntimeException ignored)
		{
			// An unreadable contract is unknown, not the absence of one.
		}
		return null;
	}

	private boolean inBurrows()
	{
		WorldPoint tile = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
		return tile != null && tile.getPlane() == 0 && tile.getX() >= 1549 && tile.getX() <= 1565
			&& tile.getY() >= 9449 && tile.getY() <= 9464;
	}

	static CompanionSnapshot.Assignment assignment(String name, String detail)
	{
		CompanionSnapshot.Assignment result = new CompanionSnapshot.Assignment();
		result.name = name;
		result.detail = detail;
		result.state = "active";
		return result;
	}

	private static CompanionSnapshot.Assignment aged(CompanionSnapshot.Assignment source, long at, long now)
	{
		if (source == null || now < at || now - at > MAX_AGE_MS)
		{
			return null;
		}
		CompanionSnapshot.Assignment result = assignment(source.name, source.detail);
		result.state = source.state;
		result.observedMsAgo = now - at;
		return result;
	}

	private static String referenced(List<String> names, String lower)
	{
		for (String name : names)
		{
			if (Pattern.compile("(?<![a-z])" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "(?![a-z])").matcher(lower).find())
			{
				return name;
			}
		}
		return null;
	}

	private static List<String> names(String resource)
	{
		List<String> names = new ArrayList<>();
		try (InputStream stream = AssignmentReader.class.getResourceAsStream("/" + resource))
		{
			if (stream == null)
			{
				return names;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
			{
				reader.lines().filter(line -> !line.trim().isEmpty()).forEach(names::add);
			}
		}
		catch (java.io.IOException ignored)
		{
			return new ArrayList<>();
		}
		// A red salamander must not be accepted as a generic salamander by substring order.
		names.sort(Comparator.comparingInt(String::length).reversed());
		return names;
	}
}
