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
import javax.swing.JButton;
import okhttp3.Response;

/**
 * The wait the plugin keeps before the map is asked again, in the words every line under a press
 * says it with: "The map is busy. Try again in 25s."
 *
 * <p>A press that met a wait keeps only which wait it met, one of the two leads here. The seconds
 * are said when the line is drawn, from the gate that holds the wait — {@link DestinationRoute}'s,
 * which every route, Reroute and run shares, and the search's and the meetups' own — so they count
 * down on the sidebar's one-second refresh and never outlive the wait. A sentence that wrote them
 * in when the refusal arrived still said "4s" a minute later. The gate is what decides when a
 * press may go: the five seconds after a worded no, the half minute after a failure, the map's own
 * Retry-After. Nothing here asks the map anything, or asks it sooner.
 *
 * <p>It is also where a reply that is no answer is read, the same way for every press: how long
 * its Retry-After asks for, and whether it says the map is busy or this member is not in the group.
 */
final class MapWait
{
	/** A press the map turned away with 429 or 503. */
	static final String BUSY = "The map is busy.";
	/** A press made inside a wait the plugin was already keeping. */
	static final String ASKED = "The map asked to wait.";

	private MapWait()
	{
	}

	/**
	 * A Retry-After header as a wait in milliseconds: its seconds, held between five and three
	 * hundred, and thirty for a reply that names none or names something that is not a number. Held
	 * as seconds, before they are multiplied: a number near the largest there is would wrap to a
	 * negative one and read as the shortest wait.
	 */
	static long retryDelay(@Nullable String header)
	{
		try
		{
			return Math.max(5, Math.min(300, Long.parseLong(header))) * 1_000;
		}
		catch (RuntimeException invalid)
		{
			return 30_000;
		}
	}

	/** The wait a reply's Retry-After names, as {@link #retryDelay} reads it, or 0 when it names none. */
	static long named(Response reply)
	{
		String wait = reply.header("Retry-After");
		return wait == null ? 0L : retryDelay(wait);
	}

	/** A map with no room for the press right now, which says when to ask again. */
	static boolean busy(int status)
	{
		return status == 429 || status == 503;
	}

	/**
	 * How long after a reply the next press may go: a busy map's own Retry-After, and otherwise
	 * {@code gap} or the wait the reply names, whichever is longer.
	 */
	static long after(Response reply, long gap)
	{
		return busy(reply.code()) ? retryDelay(reply.header("Retry-After")) : Math.max(gap, named(reply));
	}

	/**
	 * Why a reply that is no answer is none: the map is busy, this member is not in the group it
	 * asked as, or {@code otherwise}, the press's own word for anything else.
	 */
	static String why(int status, String otherwise)
	{
		return busy(status) ? BUSY : status == 403 || status == 410 ? RoutePress.NOT_CONNECTED : otherwise;
	}

	/** What a press said, as it is drawn {@code waitMs} short of its gate opening: a wait it met counts down. */
	@Nullable
	static String words(@Nullable String message, long waitMs)
	{
		return waits(message) ? tryAgain(message, waitMs) : message;
	}

	/** Whether a line is a wait, which counts down after its words; any other line is said as it is. */
	static boolean waits(@Nullable String message)
	{
		return BUSY.equals(message) || ASKED.equals(message);
	}

	/**
	 * How long is left: "25s", or "2m" once past a minute and a half. Rounded up, so a press made the
	 * moment the number runs out is never made inside the wait.
	 */
	static String left(long waitMs)
	{
		long seconds = Math.max(1L, (waitMs + 999L) / 1000L);
		return seconds <= 90L ? seconds + "s" : (seconds + 59L) / 60L + "m";
	}

	/**
	 * A refusal that met a wait, as it is drawn at a moment {@code waitMs} short of the gate opening:
	 * "The map is busy. Try again in 25s." Once the wait is over the clause goes, and a busy map is
	 * said in the past, since whether it still is is only known when the next press asks.
	 */
	static String tryAgain(String lead, long waitMs)
	{
		if (waitMs > 0L)
		{
			return lead + " Try again in " + left(waitMs) + ".";
		}
		return BUSY.equals(lead) ? "The map was busy." : lead;
	}

	/** Enter pressed inside the search's wait: nothing refused, and the search goes by itself when it is over. */
	static String searching(long waitMs)
	{
		return ASKED + " Searching in " + left(waitMs) + ".";
	}

	/** The words of {@link #tip(JButton, String, long)}'s tooltip, for a press that is not a button. */
	static String tip(long waitMs)
	{
		return "The map asked to wait: " + left(waitMs);
	}

	/**
	 * A greyed press's tooltip while the gate holds it, "The map asked to wait: 25s": only the press
	 * that met the wait has a line to say it, and every other one the gate greys gave no reason.
	 * Otherwise the button's own words, {@code own}.
	 */
	static void tip(JButton button, @Nullable String own, long waitMs)
	{
		String tip = !button.isEnabled() && waitMs > 0L ? tip(waitMs) : own;
		if (tip == null ? button.getToolTipText() != null : !tip.equals(button.getToolTipText()))
		{
			button.setToolTipText(tip);
		}
	}
}
