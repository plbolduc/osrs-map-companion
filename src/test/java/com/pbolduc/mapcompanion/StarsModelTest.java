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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The actual website wire drives Java's grouping and filter axes, without a second geocoder. */
public class StarsModelTest
{
	static StarsModel fixture()
	{
		StarsModel model = new StarsModel();
		StarsModel.Feed feed = new Gson().fromJson(new InputStreamReader(StarsModelTest.class.getResourceAsStream("/companion-stars.sample.json"), StandardCharsets.UTF_8), StarsModel.Feed.class);
		model.accept(feed); model.miningLevel = 72; model.skillTotal = 1800;
		return model;
	}
	@Test public void worldsShareOneSiteAndFavoritesLeadWithoutChangingWorldOrder()
	{
		StarsModel model = fixture(); model.preferences.filters.safe(true);
		StarsModel.Star rimmington = model.feed.rows.stream().filter(s -> s.world == 302).findFirst().get();
		model.toggleFavorite(rimmington.favoriteKey, rimmington.name, rimmington.siteKey);
		model.toggleFavorite("varrock west mine", "Varrock west mine", null);
		List<StarsModel.Site> groups = model.sites();
		assertTrue(groups.get(0).favorite); assertEquals("Rimmington mine", groups.get(0).name);
		assertEquals(3, groups.get(0).worlds.size());
		assertEquals(302, groups.get(0).worlds.get(0).world); assertEquals(330, groups.get(0).worlds.get(1).world);
		assertTrue(groups.get(1).worlds.isEmpty()); assertFalse(groups.get(1).hiddenByFilters);
		assertEquals(4, groups.size());
	}
	@Test public void filtersUseEffectiveTierAndLocalLevelsAndRiskExclusionsDoNotVoteEachOtherBackIn()
	{
		StarsModel model = fixture();
		StarsModel.Star star = model.feed.rows.stream().filter(s -> s.world == 330).findFirst().get();
		assertEquals(5, star.tier(0)); model.miningLevel = 50;
		assertTrue(model.passes(star, 0));
		model.preferences.filters.minTier = 6; assertFalse(model.passes(star, 0));
		model.preferences.filters.minTier = 1; star.pvp = true;
		model.preferences.filters.hidePvpWorlds = true; assertFalse(model.passes(star, 0));
		model.preferences.filters.hidePvpWorlds = false; star.pvp = false; star.deadman = true;
		model.preferences.filters.hideDeadmanWorlds = true; assertFalse(model.passes(star, 0));
		star.deadman = false; star.remainingMs = 0; assertFalse(model.passes(star, 0));
	}
	@Test public void metadataAndAccountUnknownsAbstainAndTotalGatesStayLocal()
	{
		StarsModel model = fixture();
		StarsModel.Star star = model.feed.rows.stream().filter(s -> s.world == 416).findFirst().get();
		model.skillTotal = 1000; assertFalse(model.passes(star, 0));
		model.skillTotal = null; assertTrue(model.passes(star, 0));
		star.members = null; model.preferences.filters.showMembers = false; assertTrue(model.passes(star, 0));
	}
	@Test public void canonicalFavoriteAliasesShareTheirTileAndFilteredFavoritesAreNotCold()
	{
		StarsModel model = fixture(); model.preferences.filters.safe(true);
		StarsModel.Star star = model.feed.rows.stream().filter(s -> s.world == 302).findFirst().get();
		StarsModel.Favorite old = new StarsModel.Favorite("older name", "Older name"); old.siteKey = star.siteKey;
		model.preferences.favorites.add(old);
		assertTrue(model.sites().get(0).favorite);
		model.preferences.filters.minTier = 9;
		assertTrue(model.sites().get(0).hiddenByFilters);
		model.toggleFavorite(star.favoriteKey, star.name, star.siteKey);
		assertTrue(model.preferences.favorites.isEmpty());
	}
	@Test public void selectionSurvivesRecallsButNotAStarThatActuallyLeftTheFeed()
	{
		StarsModel model = fixture(); String chosen = model.feed.rows.get(0).id; model.selected = chosen;
		model.feed.rows.get(0).calledTier = 4; model.accept(model.feed); assertEquals(chosen, model.selected);
		model.feed.rows.remove(0); model.accept(model.feed); assertNotEquals(chosen, model.selected);
	}
	@Test public void nativeLabelsCannotInterpretNamesAsRemoteImages()
	{
		assertEquals("&lt;html&gt;&lt;img src=&quot;http://example&quot;&gt;&amp;", StarsPanel.escape("<html><img src=\"http://example\">&"));
	}
}
