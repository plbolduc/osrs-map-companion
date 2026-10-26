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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Which repeated reward receipts deserve an announcement, independently of drop-rate coverage.
 *
 * <p>The collection catalog owns the broad vocabulary. Three ordinary repeat supplies are quiet
 * even if they have a collection slot; their first unlock is still an achievement and is handled
 * separately by RewardObserver. Outside that catalog only three explicitly reviewed signature
 * rewards qualify. Neither market price nor a small drop probability makes coins or runes notable.
 *
 * <p>Ids are transcribed from RuneLite 1.12.38 gameval ItemID, with names cross-checked against
 * the map's data/item-icons.json (2026-09-04) and RuneLite item_variations.json for the bracelet.
 * The rare non-log exceptions were reviewed against the map's harvested drop sections; Unsired
 * appears as Abyssal Sire's pre-roll in the 2026-09-09 drop-selected-bosses audit. The matching
 * ItemID constants are pinned by RewardPolicyTest so an update cannot quietly change these ids.
 */
@Singleton
public final class RewardPolicy
{
	/** SNAKEBOSS_SCALE; WILD_CAVE_BRACELET_CHARGED/UNCHARGED; ANCIENT_ESSENCE. */
	private static final Set<Integer> QUIET_REPEATS = new HashSet<>(Arrays.asList(12934, 21816, 21817, 27616));
	/** ABYSSALSIRE_UNSIRED; DRAGONFIRE_VISAGE; BRUT_DRAGON_FULL_HELM. */
	private static final Set<Integer> SIGNATURE_REWARDS = new HashSet<>(Arrays.asList(13273, 11286, 11335));
	private final CollectionItemCatalog catalog;

	@Inject
	public RewardPolicy(CollectionItemCatalog catalog)
	{
		this.catalog = catalog;
	}

	public boolean includes(int itemId)
	{
		Integer id = catalog.canonicalId(itemId);
		return id != null && !QUIET_REPEATS.contains(id)
			   && (SIGNATURE_REWARDS.contains(id) || catalog.contains(id));
	}
}
