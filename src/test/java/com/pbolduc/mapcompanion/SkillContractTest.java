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
import java.util.List;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The client's own skill list, pinned, because {@link SnapshotBuilder} walks it to decide what a
 * player's levels are and skips a member by name while doing so.
 *
 * <p>The shape it walks has already moved once. {@code OVERALL} used to be an enum constant sitting
 * at the end of {@code values()}, and reading its real level indexed the raw array past the game's
 * skills — which is why {@code skills()} skips it. In 1.12.38 it is a deprecated field holding
 * {@code null} and is not in {@code values()} at all, so that skip no longer fires. Nothing broke,
 * because there is now nothing to skip. But the same move in the other direction — a client that
 * restores the constant, or adds a skill after the last one — would change what this plugin reports
 * with no change on our side and nothing failing anywhere.
 *
 * <p>Almost every version-sensitive constant in this plugin is transcribed and unverifiable without
 * a running game: {@code VarbitIds}, {@code VarPlayerIds}, {@code DbTables}. This one is different,
 * because the client is on the test classpath and can simply be asked. So it is asked.
 */
public class SkillContractTest
{
	@Test
	@SuppressWarnings("deprecation")
	public void overallIsADeprecatedNullRatherThanASkill()
	{
		assertNull("OVERALL is an enum constant again. SnapshotBuilder's skip starts firing, which "
				   + "is what it is there for — but check it is skipping the sentinel and not a skill",
				   Skill.OVERALL);
		assertFalse("values() now carries the sentinel; a junk total would reach the map",
					Arrays.asList(Skill.values()).contains(Skill.OVERALL));
	}

	/**
	 * The guards in {@link XpTracker} and {@link SnapshotBuilder} ask for the sentinel by name, so
	 * they compile without the deprecated field and keep compiling when it is removed. This is
	 * what they must still answer: the null the current client hands over is the sentinel, and no
	 * real skill is.
	 */
	@Test
	public void theSentinelIsRecognisedWithoutTheDeprecatedField()
	{
		assertTrue(XpTracker.isOverallSentinel(null));
		for (Skill skill : Skill.values())
		{
			assertFalse(skill.getName(), XpTracker.isOverallSentinel(skill));
		}
	}

	@Test
	public void theClientStillHasTheTwentyFourSkillsThisPluginReports()
	{
		assertEquals("the skill count moved. Decide whether the new one should be sent, then change "
					 + "this number deliberately rather than to make the build pass", 24, Skill.values().length);
	}

	@Test
	public void sailingIsARealSkillAndTheLastOne()
	{
		/** It arrived recently enough to be worth naming: a skill added after it shifts the end. */
		List<Skill> all = Arrays.asList(Skill.values());
		assertTrue(all.contains(Skill.SAILING));
		assertSame("something now sits after Sailing", Skill.SAILING, all.get(all.size() - 1));
	}

	@Test
	public void everySkillCanNameItself()
	{
		/** These names go on the wire as map keys, and a null would arrive as a missing skill. */
		for (Skill skill : Skill.values())
		{
			assertNotNull(skill.getName());
			assertFalse(skill.getName().trim().isEmpty());
		}
	}
}
