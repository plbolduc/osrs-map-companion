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

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * The plugin's settings, as a real object rather than as RuneLite's dynamic proxy.
 *
 * <p>Every method on {@link MapCompanionConfig} has a default, so this class is the interface's own
 * answers with three of them redirected: the pairing code and its suffix come out of
 * {@link HarnessStore}, because {@link Pairing} writes them there and the plugin reads them back
 * through the config, and the map address is whatever the harness was pointed at. A toggle the
 * story has not touched answers exactly what a fresh install answers — {@code
 * MapCompanionConfig.super} rather than a second copy of the defaults, so this cannot drift from
 * the settings panel.
 */
final class HarnessConfig implements MapCompanionConfig
{
	private final HarnessStore store;
	private final String mapUrl;
	private final Map<String, Boolean> toggles = new ConcurrentHashMap<>();
	private volatile String nickname = MapCompanionConfig.super.nickname();

	HarnessConfig(HarnessStore store, String mapUrl)
	{
		this.store = store;
		this.mapUrl = mapUrl;
	}

	/** Choose one category explicitly, the way a reader does in the settings panel. */
	void set(String key, boolean value)
	{
		toggles.put(key, value);
	}

	void nickname(String nickname)
	{
		this.nickname = nickname;
	}

	private boolean on(String key, boolean fallback)
	{
		Boolean set = toggles.get(key);
		return set == null ? fallback : set;
	}

	@Override
	public String pairingCode()
	{
		String held = store.get(MapCompanionConfig.KEY_PAIRING_CODE);
		return held == null ? "" : held;
	}

	@Override
	public String pairingSuffix()
	{
		String held = store.get(MapCompanionConfig.KEY_PAIRING_SUFFIX);
		return held == null ? "" : held;
	}

	@Override
	public String nickname()
	{
		return nickname;
	}

	@Override
	public String relayUrl()
	{
		return mapUrl;
	}

	@Override
	public boolean shareName()
	{
		return on(MapCompanionConfig.KEY_SHARE_NAME, MapCompanionConfig.super.shareName());
	}

	@Override
	public boolean sharePosition()
	{
		return on("sharePosition", MapCompanionConfig.super.sharePosition());
	}

	@Override
	public boolean shareSkills()
	{
		return on("shareSkills", MapCompanionConfig.super.shareSkills());
	}

	@Override
	public boolean shareActivity()
	{
		return on("shareActivity", MapCompanionConfig.super.shareActivity());
	}

	@Override
	public boolean shareAccountType()
	{
		return on("shareAccountType", MapCompanionConfig.super.shareAccountType());
	}

	@Override
	public boolean shareQuests()
	{
		return on("shareQuests", MapCompanionConfig.super.shareQuests());
	}

	@Override
	public boolean shareDiaries()
	{
		return on("shareDiaries", MapCompanionConfig.super.shareDiaries());
	}

	@Override
	public boolean sharePermanentTravel()
	{
		return on("sharePermanentTravel", MapCompanionConfig.super.sharePermanentTravel());
	}

	@Override
	public boolean shareStash()
	{
		return on("shareStash", MapCompanionConfig.super.shareStash());
	}

	@Override
	public boolean shareBirdhouses()
	{
		return on("shareBirdhouses", MapCompanionConfig.super.shareBirdhouses());
	}

	@Override
	public boolean shareHunterRumours()
	{
		return on("shareHunterRumours", MapCompanionConfig.super.shareHunterRumours());
	}

	@Override
	public boolean shareMahoganyHomes()
	{
		return on("shareMahoganyHomes", MapCompanionConfig.super.shareMahoganyHomes());
	}

	@Override
	public boolean shareFarmingContracts()
	{
		return on("shareFarmingContracts", MapCompanionConfig.super.shareFarmingContracts());
	}

	@Override
	public boolean shareQuestLog()
	{
		return on("shareQuestLog", MapCompanionConfig.super.shareQuestLog());
	}

	@Override
	public boolean shareCapabilities()
	{
		return on("shareCapabilities", MapCompanionConfig.super.shareCapabilities());
	}

	@Override
	public boolean shareSlayer()
	{
		return on("shareSlayer", MapCompanionConfig.super.shareSlayer());
	}

	@Override
	public boolean shareClue()
	{
		return on("shareClue", MapCompanionConfig.super.shareClue());
	}

	@Override
	public boolean shareFarming()
	{
		return on("shareFarming", MapCompanionConfig.super.shareFarming());
	}

	@Override
	public boolean shareLoot()
	{
		return on("shareLoot", MapCompanionConfig.super.shareLoot());
	}

	@Override
	public boolean shareMilestones()
	{
		return on("shareMilestones", MapCompanionConfig.super.shareMilestones());
	}

	@Override
	public boolean sharePets()
	{
		return on("sharePets", MapCompanionConfig.super.sharePets());
	}

	@Override
	public boolean shareCollectionLog()
	{
		return on("shareCollectionLog", MapCompanionConfig.super.shareCollectionLog());
	}

	@Override
	public boolean shareDeath()
	{
		return on("shareDeath", MapCompanionConfig.super.shareDeath());
	}

	@Override
	public boolean showDestination()
	{
		return on("showDestination", MapCompanionConfig.super.showDestination());
	}

	@Override
	public boolean notifyMeetups()
	{
		return on("notifyMeetups", MapCompanionConfig.super.notifyMeetups());
	}

	@Override
	public boolean enableWorldHop()
	{
		return on("enableWorldHop", MapCompanionConfig.super.enableWorldHop());
	}

	@Override
	public boolean dropLuckChat()
	{
		return on("dropLuckChat", MapCompanionConfig.super.dropLuckChat());
	}

	@Override
	public boolean showGroupOnWorldMap()
	{
		return on("showGroupOnWorldMap", MapCompanionConfig.super.showGroupOnWorldMap());
	}

	@Override
	public boolean destinationHintArrow()
	{
		return on("destinationHintArrow", MapCompanionConfig.super.destinationHintArrow());
	}
}
