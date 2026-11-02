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
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What Copy invite puts on the clipboard, and when it puts nothing there.
 *
 * <p>The old plain-code message admits only a group that still takes a bare code. Every group
 * Connect makes is owned and refuses a bare code from anybody new, so copying it for every failure
 * would tell the inviter "Invite copied" while the friend's paste could only fail. Anything else
 * the map could not make an invite for copies nothing and says so.
 */
public class CopyInviteTest
{
	private static final String MAP = "https://osrs-map.com";

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Test
	public void aMapThatMadeNoInviteCopiesNothingAndSaysSo()
	{
		assertNull("unreachable", MapCompanionPanel.inviteText(null, RelayClient.NO_ANSWER, true, MAP, "KQX482"));
		assertNull("having trouble", MapCompanionPanel.inviteText(null, 503, true, MAP, "KQX482"));
		assertNull("busy", MapCompanionPanel.inviteText(null, 429, true, MAP, "KQX482"));
		assertNull("refused", MapCompanionPanel.inviteText(null, 403, true, MAP, "KQX482"));
		assertNull("closed", MapCompanionPanel.inviteText(null, 410, true, MAP, "KQX482"));
		/** And the line says the clipboard is as it was, so nothing stale gets pasted to a friend. */
		assertEquals("Couldn't make an invite, so nothing was copied. Try again in a minute.",
			MapCompanionPanel.INVITE_FAILED);
	}

	/**
	 * A group that never issued a secret is one that admits by its code — unless the map has just
	 * answered as an owned group does, which it does in the second or two before its first secret.
	 */
	@Test
	public void anOwnedGroupBeforeItsFirstSecretIsNotGivenTheBareCode()
	{
		assertNull(MapCompanionPanel.inviteText(null, 403, false, MAP, "KQX482"));
		assertNull(MapCompanionPanel.inviteText(null, 410, false, MAP, "KQX482"));
	}

	@Test
	public void aGroupThatAdmitsByItsCodeStillGetsThePlainMessage()
	{
		/** The invite door's answer for a group nobody owns, and a map with no such door. */
		String plain = MapCompanionPanel.inviteText(null, 404, true, MAP, "KQX482");
		assertNotNull(plain);
		assertTrue(plain, plain.contains(MAP + "/map?join=KQX482"));
		assertTrue(plain, plain.endsWith("enter code KQX482"));
		/** A legacy group never issues a secret, and its code admits whether or not the map answered. */
		assertEquals(plain, MapCompanionPanel.inviteText(null, RelayClient.NO_ANSWER, false, MAP, "KQX482"));
		assertEquals(plain, MapCompanionPanel.inviteText(null, 503, false, MAP, "KQX482"));
	}

	@Test
	public void aLinkTheMapMadeIsTheInvite()
	{
		RelayClient.Invite invite = new RelayClient.Invite();
		invite.url = "https://osrs-map.com/map?join=KQX482&task=companion&i=K7PQ2M3XABCDEFGH";
		assertEquals(MapCompanionPanel.inviteMessage(invite.url),
			MapCompanionPanel.inviteText(invite, 201, true, MAP, "KQX482"));
	}

	/** The status reaches the panel with the null, which is the whole of how it tells the cases apart. */
	@Test
	public void theRequestHandsBackTheStatusItWasRefusedWith() throws InterruptedException
	{
		assertEquals(503, refusedWith(answering(503)));
		assertEquals(404, refusedWith(answering(404)));
		assertEquals(RelayClient.NO_ANSWER, refusedWith(unreachable()));
	}

	/** The status the callback was handed, after checking it was handed no invite. */
	private static int refusedWith(OkHttpClient http) throws InterruptedException
	{
		BlockingQueue<Object[]> answers = new LinkedBlockingQueue<>();
		new RelayClient(http, new Gson()).requestInvite(MAP, "KQX482", "member-1", null,
			(invite, status) -> answers.add(new Object[]{invite, status}));
		Object[] answer = answers.poll(5, TimeUnit.SECONDS);
		assertNotNull("the callback never ran", answer);
		assertNull(answer[0]);
		return (Integer) answer[1];
	}

	private static OkHttpClient answering(int status)
	{
		return new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
			.request(chain.request())
			.protocol(Protocol.HTTP_1_1)
			.code(status)
			.message("")
			.body(ResponseBody.create(JSON, "{\"reason\":\"not_found\"}"))
			.build()).build();
	}

	private static OkHttpClient unreachable()
	{
		return new OkHttpClient.Builder().addInterceptor(chain ->
		{
			throw new IOException("the test's stand-in for an unreachable map");
		}).build();
	}
}
