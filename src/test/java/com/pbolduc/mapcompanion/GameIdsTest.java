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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The game ids the readers keep as numbers, against the client's own names for them, and a guard
 * that no reader hands the client a number nobody has asserted.
 *
 * <p>{@link VarbitIdsTest} does this for {@link VarbitIds} and {@link VarPlayerIds}. These are the
 * rest: ids that live beside the reader that uses them. Each is a literal for the reason those two
 * tables give, and each has the same failure if it is wrong — the game answers for some other
 * variable or creature, and a reader written to degrade quietly sends that answer or nothing.
 *
 * <p>Not every id has a {@code gameval} name. The collection log's enum and param ids do not, and
 * {@link CollectionItemCatalog} cites where they were read instead.
 */
public class GameIdsTest
{
	/**
	 * A client call whose id argument is written as a number. A named constant is fine — it is
	 * asserted here or is the client's own — but a bare number in the call is an id no test sees.
	 * The readers' own wrappers around those calls ({@code bit}, {@code varPlayer}, {@code value})
	 * are listed too; a new wrapper belongs here when it is written.
	 */
	private static final Pattern LITERAL_ID = Pattern.compile(
		"\\b(getVarbitValue|getVarpValue|getServerVarbitValue|getServerVarpValue|getVarcIntValue|getVarcStrValue"
			+ "|getDBTableField|getDBRowsByValue|getDBTableRows|getNpcDefinition|getItemDefinition|getObjectDefinition"
			+ "|getEnum|getStructComposition|getWidget|getItemContainer|bit|varPlayer|value)\\(\\s*\\d");

	/**
	 * Every boat part {@link CargoHolds} reads, kept as numbers in cargo-holds.txt: each hold by tier and
	 * each hull by size, in the order the file's rows give the tier and size.
	 */
	@Test
	public void theCargoHoldsAndHullsAreTheObjectsTheClientNames()
	{
		assertArrayEquals(new int[][]{
			{ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_NO_CARGO},
			{ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_NO_CARGO},
			{ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_NO_CARGO},
			{ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_NO_CARGO},
			{ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_NO_CARGO},
			{ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_NO_CARGO},
			{ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT, ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5, ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_NO_CARGO,
				ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE, ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_CARGO, ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_NO_CARGO}
		}, CargoHolds.HOLDS);
		assertArrayEquals(new int[][]{
			{ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_WOOD, ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_OAK, ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_TEAK, ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_MAHOGANY,
				ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_CAMPHOR, ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_IRONWOOD, ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_ROSEWOOD},
			{ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_WOOD, ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_OAK, ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_TEAK, ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_MAHOGANY,
				ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_CAMPHOR, ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_IRONWOOD, ObjectID.SAILING_BOAT_HULL_KANDARIN_2X5_ROSEWOOD},
			{ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_WOOD, ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_OAK, ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_TEAK, ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_MAHOGANY,
				ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_CAMPHOR, ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_IRONWOOD, ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_ROSEWOOD}
		}, CargoHolds.HULLS);
	}

	@Test
	public void theAccountTypeIsTheVarbitTheClientNames()
	{
		assertEquals(VarbitID.IRONMAN, AccountTypes.VARBIT);
	}

	@Test
	public void theTravelUnlocksAreTheVariablesTheClientNames()
	{
		/** In the order of the reader's flag names, which is what pairs a bit with a route. */
		assertArrayEquals(new int[] {
			VarbitID.ZEP_MULTI_CAST, VarbitID.ZEP_MULTI_GNO, VarbitID.ZEP_MULTI_CRAFT, VarbitID.ZEP_MULTI_VARR,
			VarbitID.PENDANT_OF_ATES_DARKFROST_FOUND, VarbitID.PENDANT_OF_ATES_TWILIGHT_FOUND,
			VarbitID.PENDANT_OF_ATES_RALOS_FOUND, VarbitID.PENDANT_OF_ATES_ALDARIN_FOUND,
			VarbitID.SAILORS_AMULET_ROBERTS, VarbitID.SAILORS_AMULET_REDROCK, VarbitID.SAILORS_AMULET_DEEPFIN,
			VarbitID.AMENITY_ROWBOAT_VATRACHOS, VarbitID.AMENITY_ROWBOAT_ANGLERS, VarbitID.AMENITY_ROWBOAT_SOUL_TEAR,
			VarbitID.AMENITY_ROWBOAT_YNYSDAIL, VarbitID.AMENITY_ROWBOAT_BUCCANEERS,
			VarbitID.PHARAOHS_SCEPTRE_NECROPOLIS}, TravelUnlockReader.BITS);
		assertEquals(VarPlayerID.QUETZALS_UNLOCKED, TravelUnlockReader.QUETZALS_UNLOCKED_VARP);
		assertEquals(VarbitID.COLOSSEUM_HIGHEST_WAVE, TravelUnlockReader.COLOSSEUM_GLORY_VARBIT);
		assertEquals(VarbitID.BARBASSAULT_ARENANEWB, TravelUnlockReader.BA_TUTORIAL_VARBIT);
		assertEquals(VarbitID.POH_HOUSE_LOCATION, TravelUnlockReader.HOUSE_LOCATION_VARBIT);
	}

	@Test
	public void theBirdhouseSlotsAreTheVarpsTheClientNames()
	{
		/** Time Tracking keys its saved birdhouses by these varps, so they are its keys as well. */
		assertArrayEquals(new int[] {
			VarPlayerID.BIRDHOUSE_TRANSMIT_A, VarPlayerID.BIRDHOUSE_TRANSMIT_B,
			VarPlayerID.BIRDHOUSE_TRANSMIT_C, VarPlayerID.BIRDHOUSE_TRANSMIT_D}, BirdhouseReader.VARPS);
	}

	@Test
	public void theHuntersAreTheCreaturesTheClientNames()
	{
		/** In the order of the reader's names: Wolf, Teco, Aco, Cervus, Ornus, Gilman. */
		assertArrayEquals(new int[] {
			NpcID.HG_WOLF, NpcID.HG_TECO, NpcID.HG_ACO, NpcID.HG_CERVUS, NpcID.HG_ORNUS, NpcID.HG_GILMAN},
			AssignmentReader.HUNTER_IDS);
	}

	@Test
	public void noReaderHandsTheClientAnUnassertedNumber() throws IOException
	{
		List<String> found = new ArrayList<>();
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			sources = files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertTrue("the plugin's sources were not found from " + Paths.get("").toAbsolutePath(), sources.size() > 10);
		for (Path source : sources)
		{
			String[] lines = new String(Files.readAllBytes(source), StandardCharsets.UTF_8).split("\n", -1);
			for (int i = 0; i < lines.length; i++)
			{
				Matcher literal = LITERAL_ID.matcher(lines[i]);
				if (literal.find())
				{
					found.add(source.getFileName() + ":" + (i + 1) + " " + lines[i].trim());
				}
			}
		}
		assertEquals("name these ids and assert them against gameval here, or use the client's constant",
			new ArrayList<String>(), found);
	}
}
