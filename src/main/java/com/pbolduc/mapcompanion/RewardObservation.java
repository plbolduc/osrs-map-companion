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

import javax.annotation.Nullable;

/**
 * One reward after the game's overlapping loot, pet and collection messages have settled.
 *
 * <p>This is evidence, not an outbox entry: identity and delivery belong to MilestoneOutbox.
 * Null means the client did not establish that fact. In particular a pet message does not invent
 * an item id, a quantity, or a boss, and a collection addition is not proof of a particular drop.
 */
public final class RewardObservation
{
	public final String rewardType;
	public final String item;
	@Nullable
	public final Integer itemId;
	@Nullable
	public final Integer quantity;
	@Nullable
	public final String sourceKind;
	@Nullable
	public final String sourceName;
	@Nullable
	public final Boolean collectionNew;
	public final long at;

	RewardObservation(String rewardType, String item, Integer itemId, Integer quantity,
					  String sourceKind, String sourceName, Boolean collectionNew, long at)
	{
		this.rewardType = rewardType;
		this.item = item;
		this.itemId = itemId;
		this.quantity = quantity;
		this.sourceKind = sourceKind;
		this.sourceName = sourceName;
		this.collectionNew = collectionNew;
		this.at = at;
	}
}
