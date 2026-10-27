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

import java.util.List;

/**
 * What the relay says back when a snapshot is accepted.
 *
 * <p>The response carries a body rather than being a bare 204 because the plugin is already
 * talking to the server every couple of seconds, so anything the map wants to tell it can ride
 * home on a request that was happening anyway: slow down, somebody sent you a destination, there
 * is a meetup in ten minutes. Directives also arrive on the receive-only stream; this body is
 * the fallback when the stream is not open.
 */
public class RelayResponse
{
	public long pollIntervalMs;
	public Directive directive;
	/**
	 * True once the map has bound this installation to the browser whose RuneLite code was pasted.
	 *
	 * <p>The receipt for a one-time secret, and the whole of that secret's lifecycle: the plugin
	 * sends the suffix as a header on every post until this comes back true, then deletes it and
	 * never sends it again — see {@link Pairing}. Boxed because absent is what an older map
	 * answers, and absent has to keep meaning "carry on sending it": read as a claim, it would
	 * leave every plugin talking to an older map unable to say who it belongs to.
	 */
	public Boolean claimed;
	/**
	 * The credential this member presents to that group from now on, or absent to keep what we hold.
	 *
	 * <p>The map issues it once — on the post that claims a RuneLite code, or on the first post from
	 * a member it knows and has never given one to — and having seen the plugin present it, stops
	 * sending it. So <b>absent means "keep what you have"</b> and never "you have none": read as a
	 * revocation it would throw away, on the very next reply, the only thing that lets this
	 * installation ask for an invite or open a stream. See {@link Pairing#rememberSecret}.
	 *
	 * <p>It exists because a member id is not a secret: every member's id is published to every
	 * other member on every read, so a door guarded by an id alone lets somebody who has been
	 * removed mint invites under a name the group still trusts.
	 */
	public String memberSecret;
	/**
	 * The group's name as its owner set it on the map, or absent to keep what we hold.
	 *
	 * <p>For the sidebar's menu of groups, which was a column of codes because codes were all this
	 * installation knew. The map sends it on every reply to a member, so a rename reaches somebody
	 * who was offline when it happened. Absent from a legacy group, which has none, and from an
	 * older map, and both read as "keep what you have" rather than "forget it" — the same rule as
	 * {@link #memberSecret}. {@link Pairing#rememberName} decides whether it is a name at all and
	 * whether it is worth a config write.
	 */
	public String groupName;
	/**
	 * The ids of the milestones this reply is the receipt for.
	 *
	 * <p>Every id the map read, whether or not it had already seen it — which is the property that
	 * makes a retry converge. Until an id appears here the plugin keeps sending that event, so a
	 * lost reply costs one repeat rather than one lost announcement. See {@link MilestoneOutbox}.
	 */
	public List<String> acknowledgedMilestones;

	public static class Directive
	{
		public Destination destination;
		public Event event;
	}

	/**
	 * A message on the held stream: the directive, and — only when this client asked for them —
	 * the other members of the group, see {@link GroupMarkers}.
	 *
	 * <p>A class of its own rather than a field on {@link Directive}, because a snapshot reply
	 * never carries members and {@code reply.json} is checked field by field against what the map
	 * actually sends on that reply. {@code stream.json} is this one's fixture.
	 */
	public static class StreamDirective extends Directive
	{
		/**
		 * Null means "not asked", and never clears what is drawn; an empty list means nobody is
		 * sharing a position right now.
		 */
		public List<MemberDot> members;
	}

	/**
	 * One other member, as the game's world map draws them. The map decides who is in the list —
	 * only members who share their position, never this character, never a raid room — so the
	 * plugin draws what it is given and decides nothing about who may be seen.
	 */
	public static class MemberDot
	{
		public String id;
		/** What the group calls them; "Member" when they share no name. */
		public String name;
		/** Their disc's colour on the map, {@code #rrggbb}. */
		public String colour;
		public int x;
		public int y;
		public int plane;
		public Integer world;
		/** In their own copy of this place, so not beside anybody else who is "there". */
		public boolean instance;
		/** Not heard from in thirty seconds; drawn faded. */
		public boolean stale;
	}

	public static class Destination
	{
		public int x;
		public int y;
		public int plane;
		public String name;
		/**
		 * The world to be on when you get there, or null for a place that is on every world.
		 *
		 * This is the field that closes the loop the map was built around: a shooting star is at a
		 * place <i>and</i> on a world, and half that answer is not an answer. It is <b>displayed</b>
		 * — see {@link DestinationMarker} and {@link MapCompanionPanel} — and never acted on: the plugin does not hop, so the player uses the
		 * game's own world switcher.
		 */
		public Integer world;
		/** One short line of why, in the sender's words: "T7 · ~16m left". */
		public String note;
	}

	public static class Event
	{
		public String title;
		public int x;
		public int y;
		public int plane;
		public Integer world;
		/** The one absolute time in the protocol — a meetup is an instant people must agree on. */
		public long startsAtMs;
	}
}
