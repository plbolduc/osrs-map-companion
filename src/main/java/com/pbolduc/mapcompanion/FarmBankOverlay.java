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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import static com.pbolduc.mapcompanion.CapabilityEvaluator.ItemTest;

/**
 * A farm run's teleports, outlined in the bank: the ones its Before you leave list names that the
 * sidebar can see are not on you, matched by the same rule it marks them by
 * ({@link CapabilityEvaluator#itemTest}). A line no known item names, a supply such as seeds, and one
 * already carried are left alone, as the sidebar leaves them; so is an emptied piece of jewellery,
 * because it carries nothing. A port-task run brings nothing, so it marks nothing.
 *
 * <p>Drawn, and only drawn: nothing in the bank moves, hides or changes, and nothing is sent. What
 * to mark is worked out once a game tick ({@link #refresh}) and each item's answer is kept until the
 * next, because a bank tab draws hundreds of items every frame. Client thread throughout.
 */
class FarmBankOverlay extends WidgetItemOverlay
{
	private final MapCompanionConfig config;
	private final FarmRunState state;
	private final ItemManager items;
	private List<ItemTest> wanted = Collections.emptyList();
	private final Map<Integer, Boolean> marked = new HashMap<>();

	@Inject
	FarmBankOverlay(MapCompanionConfig config, FarmRunState state, ItemManager items)
	{
		this.config = config;
		this.state = state;
		this.items = items;
		showOnBank();
	}

	/** Game tick: what the bank should mark now. */
	void refresh()
	{
		wanted = config.markFarmRunInBank() ? wanted(state.view()) : Collections.emptyList();
		marked.clear();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item)
	{
		if (wanted.isEmpty())
		{
			return;
		}
		Boolean hit = marked.get(itemId);
		if (hit == null)
		{
			hit = grants(wanted, itemId, items.getItemComposition(itemId).getName());
			marked.put(itemId, hit);
		}
		if (hit)
		{
			Rectangle bounds = item.getCanvasBounds();
			graphics.setColor(MapPin.AMBER);
			graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		}
	}

	/** The tests for the teleports the farm run going now needs and the sidebar can see are not carried. */
	static List<ItemTest> wanted(@Nullable FarmRunState.View view)
	{
		if (view == null || FarmRunWire.PORT_TASKS.equals(view.run.kind) || view.run.bring == null)
		{
			return Collections.emptyList();
		}
		List<ItemTest> tests = new ArrayList<>();
		for (RelayResponse.FarmBring line : view.run.bring)
		{
			if (!"teleport".equals(line.kind) || !Boolean.FALSE.equals(CapabilityEvaluator.carrying(line.name, view.carried)))
			{
				continue;
			}
			ItemTest test = CapabilityEvaluator.itemTest(line.name);
			if (test != null)
			{
				tests.add(test);
			}
		}
		return tests;
	}

	static boolean grants(List<ItemTest> tests, int itemId, @Nullable String name)
	{
		return tests.stream().anyMatch(test -> test.grants(itemId, name));
	}
}
