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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Whatever the map last asked this client to show.
 *
 * <p>A place and a name, or a meetup and a time. Both arrive on the reply to a snapshot the plugin
 * was posting anyway, and on the receive-only stream — there is no write path from this plugin
 * other than the snapshot POST.
 *
 * <p><b>It is a thing to look at and nothing else.</b> A world-map point is registered for the
 * place; the sidebar prints a line. No code here or anywhere else in this plugin clicks, walks, or
 * queues an action. That is a rule about what this feature is, not an unimplemented feature.
 *
 * <p>Dismiss is local. The map has {@code DELETE /api/companion/destination}, but issuing that
 * from here would be a new write path and would break the README's "that connection only
 * receives". The same directive keeps arriving on stream frames and POST replies; a fingerprint
 * of the dismissed destination is what keeps it hidden without touching the relay. A
 * <em>different</em> destination clears the dismissal.
 */
@Singleton
public class DestinationState
{
	private final Gson gson;

	@Getter
	private volatile RelayResponse.Destination destination;

	@Getter
	private volatile RelayResponse.Event event;

	/** Gson of the destination the player hid. Null when nothing is dismissed. */
	private volatile String dismissedFingerprint;
	/** A pin chosen here survives unchanged group heartbeats; a new map destination replaces it. */
	private volatile RelayResponse.Destination localDestination;
	private volatile String lastRemoteFingerprint;

	public synchronized void selectLocal(RelayResponse.Destination selected)
	{
		localDestination = selected;
		destination = selected;
		dismissedFingerprint = null;
	}

	public boolean hasLocalDestination() { return localDestination != null; }

	@Inject
	DestinationState(Gson gson)
	{
		this.gson = gson;
	}

	public synchronized void accept(@Nullable RelayResponse.Directive directive)
	{
		if (directive == null)
		{
			destination = localDestination;
			event = null;
			lastRemoteFingerprint = null;
			return;
		}
		event = directive.event;
		if (directive.destination == null)
		{
			destination = localDestination;
			lastRemoteFingerprint = null;
			return;
		}
		String incoming = fingerprint(directive.destination);
		if (localDestination != null && incoming.equals(lastRemoteFingerprint))
		{
			destination = localDestination;
			return;
		}
		lastRemoteFingerprint = incoming;
		localDestination = null;
		if (incoming.equals(dismissedFingerprint))
		{
			destination = null;
			return;
		}
		dismissedFingerprint = null;
		destination = directive.destination;
	}

	/**
	 * Hide the current destination on this client only.
	 *
	 * <p>Does not talk to the relay. The next identical directive is dropped; a new one shows.
	 */
	public synchronized void dismiss()
	{
		RelayResponse.Destination current = destination;
		if (current == null)
		{
			return;
		}
		dismissedFingerprint = localDestination != null && lastRemoteFingerprint != null ? lastRemoteFingerprint : fingerprint(current);
		localDestination = null;
		destination = null;
	}

	public synchronized void clear()
	{
		destination = null;
		event = null;
		dismissedFingerprint = null;
		localDestination = null;
		lastRemoteFingerprint = null;
	}

	/**
	 * The place, without its route. The map ranks a route after the place is sent, so one
	 * destination arrives first bare and then with steps; counted as two, the second would bring
	 * back a destination the player dismissed and replace a star they had just pinned.
	 */
	private String fingerprint(RelayResponse.Destination dest)
	{
		RelayResponse.Destination place = new RelayResponse.Destination();
		place.x = dest.x;
		place.y = dest.y;
		place.plane = dest.plane;
		place.name = dest.name;
		place.world = dest.world;
		place.note = dest.note;
		return gson.toJson(place);
	}
}
