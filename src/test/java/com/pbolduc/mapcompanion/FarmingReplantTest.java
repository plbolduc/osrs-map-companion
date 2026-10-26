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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Replants need a continuously observed empty-to-new-crop transition, rather than the old
 * low-bit state guess. These drive the actual observer so the rule cannot pass independently
 * of the code which emits receipts. A ready-to-growing jump alone is deliberately insufficient.
 */
public class FarmingReplantTest
{
	private static final WorldPoint FALADOR = new WorldPoint(3056, 3307, 0);
	private final ConfigStore store = new ConfigStore()
	{
		private final Map<String, String> values = new HashMap<>();
		public String get(String key)
		{
			return values.get(key);
		}

		public void set(String key, String value)
		{
			values.put(key, value);
		}

		public void unset(String key)
		{
			values.remove(key);
		}
	};
	private final FarmingObserver observer = new FarmingObserver(null, store, new Gson());
	private int tick;
	private long now = 20_000L;
	private void read(int value)
	{
		observer.observeAt(FALADOR, tick++, now, ignored -> value);
		now += 600L;
	}

	private void settle(int value)
	{
		read(value);
		read(value);
		read(value);
	}

	private String receipt()
	{
		return observer.observations(now).get(0).completionId;
	}

	@Test
	public void aReadyToGrowingJumpWithoutTheEmptyReadingIsNotAReplant()
	{
		settle(8); // Ready guam.
		read(4); // Fresh guam, but its intervening empty state was never witnessed.
		assertNull(receipt());
	}

	@Test
	public void clearingADeadCropAndPlantingAgainIsAReplant()
	{
		int dead = IntStream.range(0, 256).filter(value -> PatchStates.DEAD.equals(PatchStates.decode("HERB", value).state)).findFirst().getAsInt();
		settle(dead);
		read(3); // Raked empty patch.
		assertNull(receipt());
		read(4); // Fresh guam.
		assertNotNull(receipt());
	}

	@Test
	public void arrivingAtAPatchThatIsAlreadyGrowingIsNotOne()
	{
		settle(4);
		assertNull(receipt());
	}

	@Test
	public void aCropAdvancingAStageIsNotOne()
	{
		settle(4);
		read(5);
		assertNull(receipt());
	}

	@Test
	public void harvestingWithoutReplantingIsNotOne()
	{
		settle(8);
		read(3);
		assertNull(receipt());
	}

	@Test
	public void aReadingFromTheLastVisitCannotWitnessAnything()
	{
		settle(3);
		tick += 3;
		now += 2 * 60 * 60_000L;
		settle(4);
		assertNull(receipt());
	}
}
