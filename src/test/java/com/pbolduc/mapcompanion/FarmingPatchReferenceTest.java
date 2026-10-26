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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.*;
import org.junit.Test;

/** The local table must choose the same transmit slot and crop as RuneLite at its boundaries. */
public class FarmingPatchReferenceTest
{
	private static final class Reference
	{
		final Object world;
		final Method regions;
		final Method regionId;
		final Method patches;
		final Method implementation;
		final Method varbit;
		Reference() throws ReflectiveOperationException
		{
			Class<?> worldClass = Class.forName("net.runelite.client.plugins.timetracking.farming.FarmingWorld");
			Constructor<?> constructor = worldClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			world = constructor.newInstance();
			regions = worldClass.getDeclaredMethod("getRegionsForLocation", WorldPoint.class);
			regions.setAccessible(true);
			Class<?> regionClass = Class.forName("net.runelite.client.plugins.timetracking.farming.FarmingRegion");
			regionId = regionClass.getDeclaredMethod("getRegionID");
			patches = regionClass.getDeclaredMethod("getPatches");
			Class<?> patchClass = Class.forName("net.runelite.client.plugins.timetracking.farming.FarmingPatch");
			implementation = patchClass.getDeclaredMethod("getImplementation");
			varbit = patchClass.getDeclaredMethod("getVarbit");
			implementation.setAccessible(true);
			varbit.setAccessible(true);
		}
		boolean matches(FarmingPatchCatalog.Patch expected, WorldPoint point) throws ReflectiveOperationException
		{
			if (point.getPlane() != 0 && !expected.kind.equals("SEAWEED"))
			{
				return false;    // Intentional ground-floor-only observation.
			}
			for (Object region : (Collection<?>) regions.invoke(world, point))
			{
				if ((Integer) regionId.invoke(region) != expected.regions[0])
				{
					continue;
				}
				for (Object patch : (Object[]) patches.invoke(region))
					if (expected.kind.equals(implementation.invoke(patch).toString())
						&& expected.varbit == (Integer) varbit.invoke(patch))
					{
						return true;
					}
			}
			return false;
		}
	}

	@Test
	public void everyCatalogRegionAndSlotMatchesTheClient() throws Exception
	{
		Reference reference = new Reference();
		for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
			for (int region : patch.regions)
				for (int dx : new int[] {0, 7, 31, 63})
					for (int dy : new int[] {0, 7, 31, 63})
					{
						WorldPoint point = new WorldPoint((region >> 8) * 64 + dx, (region & 255) * 64 + dy, 0);
						assertEquals(patch.id + " at " + point, reference.matches(patch, point), patch.isLocal(point));
					}
	}

	@Test
	public void catherbyBoundaryAndUpstairsSlotsNeverGetConfused() throws Exception
	{
		Reference reference = new Reference();
		for (FarmingPatchCatalog.Patch patch : FarmingPatchCatalog.PATCHES)
		{
			if (!patch.id.startsWith("catherby-"))
			{
				continue;
			}
			for (int x : new int[] {2815, 2816, 2839, 2840, 2879})
				for (int y : new int[] {3439, 3440, 3455, 3456})
					for (int plane : new int[] {0, 1})
					{
						WorldPoint point = new WorldPoint(x, y, plane);
						assertEquals(patch.id + " at " + point, reference.matches(patch, point), patch.isLocal(point));
					}
		}
	}

	@Test
	public void faladorStopsBeforePortSarimTransmitSlots() throws Exception
	{
		Reference reference = new Reference();
		FarmingPatchCatalog.Patch herb = FarmingPatchCatalog.PATCHES.get(0);
		for (int y : new int[] {3271, 3272, 3273})
		{
			WorldPoint point = new WorldPoint(3056, y, 0);
			assertEquals(reference.matches(herb, point), herb.isLocal(point));
		}
	}
}
