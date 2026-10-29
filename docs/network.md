<!-- Copyright (c) 2026, pbolduc. All rights reserved. SPDX-License-Identifier: BSD-2-Clause -->
# Network and storage

The README's [What you share](../README.md#what-you-share) names every category a player can
send, and `NetworkDisclosureTest` holds it to `SnapshotBuilder`. This page is the rest: which
requests carry it, what identifies a member, and what is kept. The
[submission guide](plugin-hub-submission.md#network-requests) lists every endpoint.

## Requests

Requests go to **Settings → Advanced → Map address**, default `https://osrs-map.com`; it must be https
unless the map runs on this machine, and redirects are not followed. With no pairing code or no
approval of the selected server, the plugin makes no requests. First **Connect** discloses that
destination; changing it pauses requests until you connect there.

While logged in, connecting sends a random member id, your alias, a sender timestamp and pairing
credentials even with character sharing off. Snapshots are spaced at least two seconds apart;
an unchanged character normally sends a heartbeat every six seconds, or twenty with the receive
stream connected. Automatic character sharing stops while logged out.

**The step after a first Connect.** Every sharing setting, **Show destinations from the map** and
**Show my group on the world map** start off. After the server notice, the sidebar shows a row each for the
four sharing groups, the character name and those two drawing choices, all ticked, and *Where you
died* unticked, with a line saying the drawing choices open one connection. *What each one sends*,
a fold under the rows, names every field and describes the receive stream in full. **Share what's ticked** writes exactly the ticked settings, as the group shortcuts in Settings
do, and is the only thing in the step that changes anything: ticking, **Customise** and
**Not now** write nothing, and nothing in the step makes a request. It follows only a Connect that
showed the notice, which runs once per map, and only while every choice it could turn on is still
off, so a player who connected before, or chose first, is never offered it. Meetup notifications
are not in it.

Enabling either drawing choice opens a receive stream; group positions are requested only with
the latter. **Copy invite**
makes one request for an expiring invite. While **Stars** is open, logged in and connected,
the star list is fetched about once a minute without character or group credentials. **Sync
filters with website** starts on for that tab: it reads and writes filters and favourite sites
every thirty seconds and after changes, using the group code, member id and member secret. Mining
level and skill total filter stars locally. Turn sync off to keep those preferences local.

A destination can carry the map's way there, step by step; reading it sends nothing, and the
sidebar moves its steps on from your position in the game without telling the map. **Reroute**
under those steps makes one `POST /api/companion/route` per press, with the group code, member id
and member secret. It carries the destination's tile and the tile you stand on, whether or not
position sharing is on, because a route from where you stand is what the press asks for. One is in
flight at a time, and none before the wait the map last asked for: its `Retry-After`, and at least
five seconds between presses that reached it. The map plans with both tiles and keeps neither.

**Search**, at the top of Home while connected, asks `GET /api/companion/search?q=` with the
same three credentials, about 350ms after the last key or at once on Enter, and never for fewer than
two characters. One is in flight at a time: a query typed meanwhile waits for it, and the older
answer is dropped if the box has moved on. Clearing the box cancels the call. A `Retry-After` is
honoured by asking nothing until it has passed, and a failure without one waits ten seconds. The
query is in the request's address, which the map leaves out of its logs; the plugin logs nothing it
types. A pick is one `POST /api/companion/route` with the row's id and the tile you stand on, under
Reroute's rules, and its answer becomes the destination, as a destination the map sent would.

**Route** beside the clue being carried, the last death, a star in the Stars tab or a meetup is the
same request with a tile as its target: the clue's dig spot or beginner map, the death's tile, the
star's tile, the meetup's. A clue or a death that is not shared leaves only in that request, once
per press; the map plans with it and keeps nothing. A clue whose step is words names no tile and
offers no Route yet. A death inside a boss's instance is recorded as the tile its arena is copied
from, and the map decides whether that route goes to the boss's way in. A star whose tile is
approximate offers **Open on map** instead, which opens the map's search for the site's name in
your browser and sends this client nothing. The clue is now read whether or not *Share clue* is on,
so the sidebar has it to offer; the snapshot still carries it only with that choice.

**The Group section** is read from what the map already sends: `roster`, one row per other member
with their name, colour, presence, *near*, *Near you* and newest news, arrives on every snapshot
reply and stream frame and is shown whatever the destination setting; `events`, the week's meetups
with their answers, is shown only with **Show destinations from the map**, as the single `event`
always was. A map that sends neither leaves the sidebar as it was. Neither carries a tile.
**Show on map** beside a friend, or **Route** to a friend whose position this client has not been
sent, opens the receive stream with `members=1`, the same question *Show my group on the world map*
asks, and draws only that friend; it sends nothing of yours. A Route to a friend goes once to the
tile in their dot, under Reroute's rules, and never into an instance.

The meetup answers are `PUT /api/companion/event/answer` with the meetup's id and one of `going`,
`maybe`, `no` and `on_my_way`; **Meet here** is `PUT /api/companion/event` with a title, the start
time, the tile you stand on and your world, and copies the link the map answers with after checking
it is a path on the map address. Both carry the group code, member id and member secret, one at a
time, and none before a `Retry-After` has passed. Neither is ever sent without a press.
**Notify before a meetup** (off) raises one RuneLite notification per meetup, the chosen number of
minutes before it starts; it reads only what the map already sent.

**Draw paths with Shortest Path** (on) talks to the Shortest Path plugin, if it is installed,
with RuneLite's plugin messages, which stay inside this client. While a route is shown it asks
`getTarget` every few seconds, which only reads what Shortest Path is drawing. On a route you chose
to follow, by pressing **Follow** or **Reroute**, it sends `path` with the current walking step's
start, your tile, and its end; it sends nothing for a teleport or a ride. It sends `clear` only for
a path it set, and only once Shortest Path's answer still names that path. It never sends
Shortest Path's `config`. A destination that merely arrives, a friend's included, sends nothing.

## Identity and credentials

Each character has a random id; account hashes and identifiers derived from your account are
never transmitted. The full code's suffix travels as `X-Companion-Suffix` until the
browser link is claimed, then is deleted. `X-Companion-Installation` is a random installation
key, sent on snapshots so another character can join the same browser's group without another
paste; with a group code it admits your characters to your groups, so connect only to a map you
trust. The member id and installation key are the same for every map and group this RuneLite
connects to until **Leave group**, and the receive stream carries the group code and member id in
its URL. `X-Companion-Member-Secret` is a random credential for one server, group and character,
used on snapshots, invites, the receive stream and star-preference sync. It is never displayed
or logged. **Leave group** deletes every one of these member secrets and replaces the installation
identity; your local kill log remains.

Local game messages provide bounded reward and assignment facts only. **Settings → Advanced → Show what's
sent** previews the current snapshot; credentials are named without displaying standing secrets.

## Storage

The plugin keeps pairing settings, credentials, preferences, patch observations and a bounded kill
log in its own RuneLite config group. Kill logs, pins and observations are character-scoped.
**Forget kill log** clears the current character's counts and pin.

On the default map server, live positions and progress stay in temporary memory; there is no stored
movement history. Group membership, last shared names, invites, meetups and group pins persist.
A new group keeps an activity feed of its members' notable drops, levels, quests and diaries:
up to 200 entries for 14 days. Its owner can turn this off, and a group made before 29 October 2026
keeps the setting it had. *Drops and levels* and the step after Connect say *Your group keeps the
last two weeks.*
Leaving stops new sharing; retained announcements expire under the server's
[privacy policy](https://osrs-map.com/privacy).

Drop luck pauses on F2P and special worlds. Pet and collection announcements have separate
switches; collection notices also require the game's chat or popup notification. See
[reward coverage](rewards-and-dryness.md). Task observations such as rumours and Mahogany Homes
expire after two hours or a session change.
