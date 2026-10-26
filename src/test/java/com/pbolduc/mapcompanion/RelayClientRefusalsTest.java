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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Which refusals are a mistake retrying cannot fix, and when a latched fatal expires.
 *
 * <p>RelayClient is not unit-testable without OkHttp mocking, and there is no mocking
 * dependency. These predicates are the whole of the policy: the four statuses the relay
 * emits with meaning by their number alone, the three refusals that state a reason in the
 * body, and the five-minute window after which one retry is allowed because the relay is
 * redeployed without notice.
 *
 * <p>Permanence has two inputs, and they are tested separately below because they answer
 * different questions. {@code isPermanent} is "permanent whatever the body says", which is
 * why 403 and 410 are false there; what those two mean is in the body, and
 * {@code permanentReason} is what reads it.
 */
public class RelayClientRefusalsTest
{
	@Test
	public void onlyTheFourRelayRefusalsArePermanent()
	{
		assertTrue(RelayClient.isPermanent(400));
		assertTrue(RelayClient.isPermanent(413));
		assertTrue(RelayClient.isPermanent(422));
		assertTrue(RelayClient.isPermanent(507));
		/** 403 and 410 are not permanent by number: see the reason tests below. */
		assertFalse(RelayClient.isPermanent(403));
		assertFalse(RelayClient.isPermanent(410));
		assertFalse(RelayClient.isPermanent(404));
		assertFalse(RelayClient.isPermanent(429));
		assertFalse(RelayClient.isPermanent(500));
		assertFalse(RelayClient.isPermanent(503));
		assertFalse(RelayClient.isPermanent(200));
	}

	@Test
	public void aRefusalThatNamesItsReasonIsPermanentAndSaysWhy()
	{
		/**
		 * Three sentences a player can act on or has to accept. The wording matters as much as the
		 * decision: this is the whole of what the sidebar shows somebody whose dot has stopped.
		 */
		assertEquals(
			"The map needs your RuneLite code — copy it from the map's Companion panel and paste it here.",
			RelayClient.permanentReason(403, "needs_code"));
		assertEquals("This group was closed.", RelayClient.permanentReason(410, "closed"));
		assertEquals("You were removed from this group.", RelayClient.permanentReason(410, "removed"));
	}

	@Test
	public void aRedirectNamesTheAddressToUseInstead()
	{
		okhttp3.HttpUrl asked = okhttp3.HttpUrl.get("https://www.example.test/api/companion/snapshot");
		assertEquals("This map address redirects to https://example.test. Enter that under Advanced → Map address.",
			RelayClient.redirectMessage(asked, "https://example.test/api/companion/snapshot?x=1"));
		assertEquals("This map address redirects to https://www.example.test:8443. Enter that under Advanced → Map address.",
			RelayClient.redirectMessage(asked, "https://www.example.test:8443/api/companion/snapshot"));
		assertTrue(RelayClient.redirectMessage(asked, null).startsWith("This map address redirects."));
	}

	@Test
	public void anInviteLinkMustBeOnTheMapThatWasAsked()
	{
		okhttp3.HttpUrl asked = okhttp3.HttpUrl.get("https://osrs-map.com/api/companion/invite");
		assertTrue(RelayClient.isInviteUnder(asked, "https://osrs-map.com/map?invite=ABCD"));
		assertFalse("another host", RelayClient.isInviteUnder(asked, "https://osrs-map.com.evil.test/map"));
		assertFalse("a downgrade", RelayClient.isInviteUnder(asked, "http://osrs-map.com/map"));
		assertFalse("a second line", RelayClient.isInviteUnder(asked, "https://osrs-map.com/map" + (char) 10 + "Visit evil.test"));
		assertFalse("a novel", RelayClient.isInviteUnder(asked, "https://osrs-map.com/" + "a".repeat(600)));
		assertFalse(RelayClient.isInviteUnder(asked, null));
	}

		@Test
	public void readsAReasonInAnyCaseAndWithWhitespace()
	{
		/** It is a word in somebody else's JSON, and a case change should not restart the retries. */
		assertNotNull(RelayClient.permanentReason(403, " NEEDS_CODE "));
		assertNotNull(RelayClient.permanentReason(410, "Removed"));
	}

	@Test
	public void a507NamesWhichCeilingItHit()
	{
		/** The relay's ceiling clears by itself; the group's needs somebody to leave. */
		assertEquals("The map has no room for anyone new right now. This will try again in five minutes.",
					 RelayClient.describeFull("capacity"));
		assertEquals(RelayClient.describeFull("capacity"), RelayClient.describeFull(" CAPACITY "));
		assertEquals("That group is full.", RelayClient.describeFull("group_full"));
		/** A map older than the words, or a word this plugin does not know yet. */
		assertEquals("That group is full.", RelayClient.describeFull(null));
		assertEquals("That group is full.", RelayClient.describeFull("some_future_word"));
	}

	@Test
	public void aBare403IsStillSomethingToRetry()
	{
		/**
		 * The failure this asymmetry exists for: a proxy in front of the map answers 403 with HTML
		 * during a deploy, and latching on that is how "the plugin is broken until you edit the
		 * settings" happened before. Only the map's own reason makes a 403 final.
		 */
		assertNull(RelayClient.permanentReason(403, null));
		assertNull(RelayClient.permanentReason(403, ""));
		assertNull(RelayClient.permanentReason(403, "forbidden"));
	}

	@Test
	public void a410IsPermanentEvenWithNothingToSay()
	{
		/**
		 * Gone means gone: nothing in the path emits 410 by accident, and the previous release
		 * retried one every thirty seconds forever. The five-minute window still gives a redeployed
		 * map one chance to disagree.
		 */
		assertNotNull(RelayClient.permanentReason(410, null));
		assertNotNull(RelayClient.permanentReason(410, "something new"));
	}

	@Test
	public void everyOtherStatusIsUnchangedByThisRule()
	{
		assertNull(RelayClient.permanentReason(404, "closed"));
		assertNull(RelayClient.permanentReason(500, "removed"));
		assertNull(RelayClient.permanentReason(200, "needs_code"));
	}

	@Test
	public void aFatalExpiresAfterFiveMinutesAndNotBefore()
	{
		long since = 1_000L;
		assertFalse(RelayClient.fatalExpired(since, since));
		assertFalse(RelayClient.fatalExpired(since, since + RelayClient.FATAL_RETRY_MS - 1));
		assertTrue(RelayClient.fatalExpired(since, since + RelayClient.FATAL_RETRY_MS));
		assertTrue(RelayClient.fatalExpired(since, since + RelayClient.FATAL_RETRY_MS + 60_000L));
		/** Never latched: a zero timestamp is not "expired", it is "there is nothing to retry". */
		assertFalse(RelayClient.fatalExpired(0L, System.currentTimeMillis()));
	}
}
