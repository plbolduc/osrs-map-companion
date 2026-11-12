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

import com.google.gson.annotations.JsonAdapter;
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
	 * True when this member may rename the group from the sidebar: its RuneLite made the group, or it
	 * is bound to an owner's browser. Absent otherwise, and absent means "may not" — unlike the name,
	 * there is nothing here worth keeping from an older reply, because standing in a group changes.
	 */
	public Boolean groupOwner;
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
		/** Public relay-memory lifetime; its counters start again after the server restarts. */
		@JsonAdapter(DirectiveEpoch.Lenient.class)
		public String relayEpoch;
		public Destination destination;
		/** The member's own ordered farm run; a malformed one cannot cost the rest of the reply. */
		@JsonAdapter(FarmRunWire.Lenient.class)
		public FarmRun run;
		/** Also sent after End run, so a stale reply cannot revive its old list. */
		@JsonAdapter(FarmRunWire.Revision.class)
		public long runRevision;
		/** Own task's places, sent only until this client acknowledges their revision. */
		@JsonAdapter(SlayerWire.Lenient.class)
		public SlayerOptions slayerOptions;
		@JsonAdapter(FarmRunWire.Revision.class)
		public long slayerRevision;
		/**
		 * The other members of the group, one row each, worded on the map by the website's own rules:
		 * {@code buildRoster}'s order, *near* a landmark, *Near you*, the newest news. Null from a map
		 * that sends none, which is today's sidebar exactly; an empty list is a group of one. No tile
		 * is in it — tiles stay in {@link MemberDot}, for members who share their position. Read
		 * leniently by {@link SocialWire}, so a bad row costs that row and not the reply.
		 */
		@JsonAdapter(SocialWire.LenientRoster.class)
		public List<RosterRow> roster;
		/**
		 * Every meetup within the week, soonest first, each with its roll-up. Null from a map that
		 * sends none, which leaves {@link #event} — still sent beside it — as the only meetup shown.
		 */
		@JsonAdapter(SocialWire.LenientMeetups.class)
		public List<Meetup> events;
	}

	public static class SlayerOptions
	{
		public String taskName;
		public String areaName;
		public String order;
		public boolean hideWilderness;
		public List<SlayerSpot> rows;
		public int ranked;
		public int hiddenWilderness;
		public List<SlayerKind> kinds;
		public List<SlayerBoss> bosses;
		public String rate;
		public String page;
		public String note;
		public List<String> notes;
	}

	public static class SlayerSpot
	{
		public String id;
		public int rank;
		public String name;
		public List<String> kinds;
		public List<String> labels;
		public String tier;
		public List<SlayerAsk> asks;
		public boolean bestForYou;
		public RouteTile target;
	}

	public static class SlayerAsk
	{
		public String text;
		public String tier;
		public String skill;
	}

	public static class SlayerKind
	{
		public String name;
		public String facts;
		public String best;
	}

	public static class SlayerBoss
	{
		public String name;
		public String where;
		public RouteTile entrance;
	}

	/** One other member, as the website's Members list says them. */
	public static class RosterRow
	{
		public String id;
		public String name;
		/** Their disc's colour, {@code #rrggbb}: the same person as their dot on the world map. */
		public String colour;
		/** {@code online}, {@code idle} or {@code offline}: drawn as a small dot, never printed. */
		public String presence;
		public Integer world;
		/** Where they are in words, "near Lumbridge Castle", or absent for a member who shares no position. */
		public String near;
		/** Within a hundred tiles of you on your world; absent otherwise. */
		public Boolean nearYou;
		/** A position the map draws, not in a copy of a place, and fresh: the only kind Route may go to. */
		public boolean routable;
		/** Their newest level or drop while the group keeps its feed, worded by the feed's own titles. */
		public News news;
	}

	public static class News
	{
		public String kind;
		public String title;
		public long atMs;
		/** For a level, the skill's name, whose icon the news line draws. */
		public String skill;
		/** For a drop or a reward the game identified, the item, whose sprite the news line draws. */
		public Integer itemId;
	}

	/** A meetup as the group's card lists it. */
	public static class Meetup
	{
		/** The group's row id, which an answer names. */
		public String id;
		public String title;
		public int x;
		public int y;
		public int plane;
		public Integer world;
		/** The one absolute time in the protocol — a meetup is an instant people must agree on. */
		public long startsAtMs;
		public String near;
		/** The card's roll-up: going, on my way, maybe, can't, then arrived. */
		public List<MeetupAnswer> who;
		/** This member's own answer, as the browser it is bound to gave it; absent when none. */
		public String mine;
	}

	public static class MeetupAnswer
	{
		public String name;
		public String colour;
		/** {@code going}, {@code maybe}, {@code no}, {@code on_my_way}, or {@code arrived}. */
		public String answer;
		/** The face that is the reader's own. */
		public Boolean you;
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
		 * — see {@link DestinationMarker} and {@link NowCards} — and never acted on: the plugin does not hop, so the player uses the
		 * game's own world switcher.
		 */
		public Integer world;
		/** One short line of why, in the sender's words: "T7 · ~16m left". */
		public String note;
		/**
		 * The member this was sent to alone, or null for a send to the whole group.
		 *
		 * <p>The map lets a browser name only a RuneLite bound to it, so a destination naming this
		 * client's member is its player's own Send to RuneLite, and {@link DestinationRoute} follows it
		 * from the start, as it follows a place picked in the sidebar. One naming nobody is a send to
		 * everyone, a friend's included, and arrives unfollowed.
		 */
		public String targetMemberId;
		/**
		 * The map's ways there, each step by step in the website's own words, or null.
		 *
		 * <p>Null is today's destination exactly: a pin, an arrow and a line in the sidebar. The map
		 * sends it once a route is ranked, which can be a few seconds after the place itself, so the
		 * same destination can arrive without one and then with one. Read leniently by
		 * {@link RouteWire}: a route this build cannot read is dropped and the destination kept.
		 */
		@JsonAdapter(RouteWire.Lenient.class)
		public Route route;
	}

	/** An ordered run sent to this member alone; stops carry their own local progress evidence ids. */
	public static class FarmRun
	{
		public String id;
		public String kind;
		public String name;
		public long startedAtMs;
		public List<FarmBring> bring;
		public List<FarmStop> stops;
	}

	public static class FarmBring
	{
		public String kind;
		public String name;
		public Integer uses;
		public String note;
	}

	public static class FarmStop
	{
		public int n;
		public String name;
		public RouteTile tile;
		public List<String> patchIds;
		public String birdhouseId;
		public String method;
		public String item;
		public int costTiles;
		public List<RouteStep> steps;
		public boolean done;
		public boolean visited;
		/**
		 * A port-task stop's ticks, one per task slot it serves, or empty on a farm stop. The stop is
		 * done when every one is reached, read from this client's own task slots and never from where
		 * the player stands: see {@link FarmRunState}.
		 */
		public List<RunTask> tasks;
		/** The voyage this stop's leg sails, drawn and ticked as a destination's voyage is; null otherwise. */
		public Voyage voyage;
		/**
		 * The tool a sea charting stop's task takes, by its item's name, for the stop's picture: the
		 * kind of task at a glance. Never a thing to bring, since a boat's cargo hold hands it out.
		 */
		public String tool;
		/** A sea charting stop's leg in numbers, for a column of them; null where the map sent only its words. */
		public FarmLeg leg;
	}

	/** The map's {@code FarmRunLeg}: how far a leg goes, by sail or on foot, and what its line says besides. */
	public static class FarmLeg
	{
		public String by;
		public int tiles;
		public String more;
	}

	/**
	 * What finishes one task's part of a port-task stop: the slot's {@code counter} reaching {@code n}
	 * (crates {@code taken} or {@code delivered} at least, parts {@code remaining} at most), or for
	 * {@code held} the slot no longer holding the task. {@code ends} marks the stop where the task
	 * finishes, which may take the slot letting go of it when it was never seen held this run; see
	 * {@link FarmRunState#tick}. The map's {@code RunTaskTick}.
	 */
	public static class RunTask
	{
		public int slot;
		public int taskId;
		public String counter;
		public int n;
		public boolean ends;
		public String text;
	}

	/**
	 * A route as the map hands it to the game, from a destination or from a press of Reroute.
	 *
	 * <p>The website's travel engine ranks it; this plugin computes nothing about the way and
	 * prints what it was told. The shape is the map's {@code lib/route-wire.ts}, and its contract
	 * test holds this class to it.
	 */
	public static class Route
	{
		/**
		 * Where it was ranked from, by its source and never its tile: {@code shared}, the position
		 * this member shares; {@code request}, the tile a Reroute press sent; or null, from nowhere,
		 * which the sidebar says rather than pretending the first walk starts where you are.
		 */
		public String origin;
		/** The card's ways in its order, the best of them always among them. */
		public List<RouteOption> options;
	}

	public static class RouteOption
	{
		/** The transport's id: stable, and what a later press could name. */
		public String id;
		public String label;
		public boolean best;
		/** A way the account cannot take yet, kept in its place as the card keeps it. */
		public boolean blocked;
		/** The whole trip in run tiles, a tick worth two, or null where the ranking priced none. */
		public Integer tiles;
		public List<RouteStep> steps;
		/**
		 * The voyage on this way, when it sails one: the moorings it boards and docks at, the sail as
		 * a line, and the docking mooring's gates. Null on a way that does not sail. The steps that
		 * belong to it are the ones whose slot is {@code board}, {@code sail} or {@code dock}, or, for
		 * a boat the map does not know, the one {@code method} step that sails.
		 */
		public Voyage voyage;
	}

	/**
	 * A sail in the player's own boat, as the map traces it. Shortest Path has no row for one, so
	 * this plugin draws its line on the world map itself (the map's plan, <i>A sailing leg in the
	 * game</i>) and never hands it a voyage.
	 */
	public static class Voyage
	{
		/** The mooring boarded at, by the name the game's {@code SailingDock} table gives it. */
		public String from;
		/** The mooring docked at by the same name, or null for a sail out to a spot at sea. */
		public String to;
		/** The sail, simplified to a few dozen {@code [x, y]} tiles on the sea. */
		public int[][] line;
		/** What docking at {@link #to} asks, each with the account's tier: met, unmet or unknown. */
		public List<Gate> gates;
	}

	public static class Gate
	{
		public String text;
		public String tier;
	}

	public static class RouteStep
	{
		/** What the step is — a teleport, a ride, a walk — as a word; see the map's journey steps. */
		public String slot;
		public String title;
		public String detail;
		/** The words under the row on the card: a Wilderness warning, a ride's asks, a note. */
		public List<String> notes;
		/** A walking row: the only kind Shortest Path is ever aimed at. */
		public boolean walk;
		/**
		 * The game's own picture for the step, where the card's sprite is an item or a spell: the item
		 * clicked, by its item id, or the spell cast, by its icon's sprite id. One at most, and null
		 * on a walk, a ride or a teleport the map names no id for, which is drawn as words alone.
		 */
		public Integer itemId;
		public Integer spriteId;
		/** Where the step ends, or null for a step that has words and no pin. */
		public RouteTile at;
	}

	/** A step's far end. No floor means the ground floor, as the map's own tiles read it. */
	public static class RouteTile
	{
		public int x;
		public int y;
		public Integer plane;
	}
}
