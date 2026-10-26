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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TaskKillsTest
{
	private final TaskKills tasks = new TaskKills();

	@Test
	public void aKillTheCounterFallsWithIsOnTheTask()
	{
		tasks.onTick(1, 42, 150);
		tasks.onKill("Abyssal demon", 10);
		tasks.onTick(11, 42, 149);
		assertTrue(tasks.onTask("Abyssal demon"));
		assertEquals(Integer.valueOf(149), tasks.remaining("abyssal demon"));
	}

	/** The counter can move on the death and the loot arrive a tick later. */
	@Test
	public void theCounterMayFallBeforeTheLootArrives()
	{
		tasks.onTick(1, 42, 150);
		tasks.onTick(10, 42, 149);
		tasks.onKill("Abyssal demon", 11);
		assertTrue(tasks.onTask("Abyssal demon"));
	}

	@Test
	public void aKillTheCounterIgnoresIsNot()
	{
		tasks.onTick(1, 42, 150);
		tasks.onKill("Chicken", 10);
		tasks.onTick(11, 42, 150);
		tasks.onTick(30, 42, 149);
		assertFalse(tasks.onTask("Chicken"));
		assertNull(tasks.remaining("Chicken"));
	}

	@Test
	public void aNewTaskForgetsWhatTheLastOneTaught()
	{
		tasks.onTick(1, 42, 150);
		tasks.onKill("Abyssal demon", 10);
		tasks.onTick(11, 42, 149);
		tasks.onTick(500, 7, 80);
		assertFalse(tasks.onTask("Abyssal demon"));
	}

	/**
	 * The game leaves the task's target set when the count reaches zero. A drake killed after its
	 * task was finished is not on task — but the kill that finished it was, even when its loot
	 * arrives a tick after the count fell to zero.
	 */
	@Test
	public void aFinishedTaskIsNotOnTaskButItsLastKillWas()
	{
		tasks.onTick(1, 42, 2);
		tasks.onKill("Drake", 10);
		tasks.onTick(11, 42, 1);
		assertTrue(tasks.onTask("Drake"));
		tasks.onTick(20, 42, 0);
		assertTrue("the finishing kill, a tick late", tasks.onTaskAt("Drake", 21));
		assertFalse("and nothing after it", tasks.onTask("Drake"));
		assertFalse(tasks.onTaskAt("Drake", 40));
		assertNull(tasks.remaining("Drake"));
	}

	@Test
	public void noTaskMeansNothingIsOnIt()
	{
		tasks.onTick(1, 0, 0);
		tasks.onKill("Abyssal demon", 10);
		tasks.onTick(11, 0, 0);
		assertFalse(tasks.onTask("Abyssal demon"));
	}
}
