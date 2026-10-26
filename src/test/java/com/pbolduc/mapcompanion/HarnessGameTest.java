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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A leash on the fake client, which is the one thing in the integration harness that could start
 * lying.
 *
 * <p>{@link HarnessGame} exists so the story in the map's repository can drive this plugin's real
 * readers without a game. The danger in a fake like that is not that it breaks — a broken one fails
 * loudly — it is that it grows. The moment it starts computing an answer rather than repeating one
 * the story set, every result the harness produces is a fact about the fake rather than about this
 * plugin, and a green run is worth nothing while still reading as coverage.
 *
 * <p>So this pins the whole of what it knows how to answer, by name and by count, and asserts that
 * everything else falls through to the zero value of its return type — the same answer a client
 * that has loaded nothing gives. A change here should be a deliberate line in a diff, next to the
 * plugin code that made a fourteenth method necessary.
 *
 * <p>It lives in the test source set rather than in {@code src/harness} because that is where JUnit
 * is; {@code build.gradle} puts the harness classes on the test classpath for exactly this file.
 */
public class HarnessGameTest
{
	/**
	 * Every method the fake answers, written out again here rather than read from the set it is
	 * checking.
	 *
	 * <p>A test that asserted {@code ANSWERED.equals(ANSWERED)} would pass through any edit at
	 * all. This is a second copy on purpose: adding a method means changing two files, and the
	 * second one is this sentence asking whether the plugin really needs to read one more thing
	 * off a client that is not there.
	 */
	private static final Set<String> EXPECTED = new LinkedHashSet<>(Arrays.asList(
			"getGameState", "getLocalPlayer", "getWorld", "getWorldType", "getVarbitValue", "getVarpValue",
			"getRealSkillLevel", "getTickCount", "getPlane", "getItemDefinition", "getComponentTable",
			"getName", "getWorldLocation", "getLocalLocation"));

	@Test
	public void answersExactlyTheseFourteenMethods()
	{
		assertEquals(EXPECTED, HarnessGame.ANSWERED);
		// Modal state became an explicit farming input; the rest of the fake stays bounded.
		assertEquals(14, HarnessGame.ANSWERED.size());
	}

	/**
	 * And every one of them is a method the client really has.
	 *
	 * <p>The proxy answers by name, so a typo in that set is not a compile error: it is an entry
	 * that never matches, and the method it was meant to answer quietly returns zero instead. That
	 * failure looks exactly like a plugin reading an empty game.
	 *
	 * <p>This has already earned its place: the set carried {@code isInScene}, which the plugin
	 * really does call — on {@code LocalPoint}, not on a {@link Player}. The answer could never
	 * have been reached, and nothing else here would ever have said so.
	 */
	@Test
	public void andEveryOneOfThemIsRealApi()
	{
		Set<String> declared = Arrays.stream(Client.class.getMethods()).map(Method::getName)
							   .collect(Collectors.toCollection(LinkedHashSet::new));
		Arrays.stream(Player.class.getMethods()).map(Method::getName).forEach(declared::add);
		for (String name : HarnessGame.ANSWERED)
		{
			assertTrue(name + " is not a method of Client or Player", declared.contains(name));
		}
	}

	/**
	 * Everything else is the zero value, found by reflection rather than by naming a method.
	 *
	 * <p>Naming one would tie this test to a particular client release for no gain; what is being
	 * asserted is the fall-through itself, and any unanswered method proves it.
	 */
	@Test
	public void andGivesEverythingElseTheZeroValue()
	{
		Client client = new HarnessGame().client();
		Method anInt = unanswered(int.class);
		Method anObject = unanswered(Object.class);
		try
		{
			assertEquals(0, anInt.invoke(client));
			assertNull(anObject.invoke(client));
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError("the proxy threw rather than answering " + anInt.getName(), e);
		}
	}

	/** One no-argument {@link Client} method the fake has no answer for, returning the wanted kind. */
	private static Method unanswered(Class<?> returning)
	{
		for (Method method : Client.class.getMethods())
		{
			boolean wanted = returning.isPrimitive()
							 ? method.getReturnType() == returning
							 : !method.getReturnType().isPrimitive();
			if (wanted && method.getParameterCount() == 0 && !HarnessGame.ANSWERED.contains(method.getName()))
			{
				return method;
			}
		}
		throw new AssertionError("no unanswered no-argument method returning " + returning);
	}

	/**
	 * Nothing is invented: what has not been set reads as absent, not as a plausible default.
	 *
	 * <p>This is the property that lets the story's assertions mean something. A varbit nobody set
	 * is zero because that is the zero value, not because the fake decided a quest was unstarted;
	 * a player nobody logged in is null rather than a character standing at the origin.
	 */
	@Test
	public void andInventsNothingTheStoryDidNotSet()
	{
		HarnessGame game = new HarnessGame();
		Client client = game.client();
		assertEquals(GameState.LOGIN_SCREEN, client.getGameState());
		assertNull("a client nobody logged in has no local player", client.getLocalPlayer());
		assertEquals(0, client.getVarbitValue(4488));
		assertEquals(1, game.level(Skill.SLAYER));
		assertNull("an item nobody named has no definition", client.getItemDefinition(4151));

		game.login("Bob", 444, 3222, 3218, 0);
		assertEquals(GameState.LOGGED_IN, client.getGameState());
		Player bob = client.getLocalPlayer();
		assertNotNull(bob);
		assertEquals("Bob", bob.getName());
		assertEquals(3222, bob.getWorldLocation().getX());
		assertEquals(444, client.getWorld());
		/** Still nothing invented about the world it just logged into. */
		assertEquals(0, client.getVarbitValue(4488));
		/**
		 * And the same player is the same object. A proxy whose {@code equals} fell through to the
		 * zero value would be false against itself, which breaks every collection it enters and
		 * reads as a bug in the plugin holding it.
		 */
		assertEquals(bob, client.getLocalPlayer());
	}
}
