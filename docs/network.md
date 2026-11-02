<!-- Copyright (c) 2026, pbolduc. All rights reserved. SPDX-License-Identifier: BSD-2-Clause -->
# Network and storage

The README's [What you share](../README.md#what-you-share) names every category a player can
send, and `NetworkDisclosureTest` holds it to `SnapshotBuilder`. This page is the rest: which
requests carry it, what identifies a member, and what is kept. The
[submission guide](plugin-hub-submission.md#network-requests) lists every endpoint.

## Requests

Requests go to **Settings → Advanced → Map address**, default `https://osrs-map.com`; it must be https
unless the map runs on this machine, and redirects are not followed. With no pairing code or no
approval of the selected server, the plugin makes no requests. The warning RuneLite requires is the
Hub manifest's, shown when the plugin is installed; no setting repeats it. The first **Connect** to
osrs-map.com approves it with that press. A changed map address asks once, naming the server, before
anything is sent there, and changing the address pauses requests until you connect there. An address
typed without its scheme is taken as https, or as http on this machine.

**Connect and Join.** With no group, **Connect** makes one `POST /api/companion/connect` per press,
carrying this RuneLite's random installation key and nothing else. The map makes a group called
*Our group* whose maker is this installation, and answers with a connection code, which the plugin
keeps exactly as a pasted one. **Join** with a pasted invite makes the same request with the group
code and the invite's token, and so does **Connect** while the field holds an invite: it joins what
was pasted rather than making a group beside it. Neither request carries character data, a member
id or a secret, and neither is retried: a refusal to make a group is printed on the status card until
the next press, and a refusal to join under the field.

**Rename group.** The groups menu offers **Rename group…** only when the map's last reply said this
member may (`groupOwner`): its RuneLite made the group, or it is bound to an owner's browser. A press
makes one `PATCH /api/companion/group` with the group code, member id and member secret and the new
name, and keeps the name the map answers with.

**Open the map.** While connected, **Open the map** makes one `POST /api/companion/link` with
the group code, member id and member secret, and opens the link the map answers with: the map's own
page, with a one-time token in the URL fragment, which the browser does not send to the map. The
token is good for ten minutes and once. The map adds that browser to the group as this member, and
as the group's owner when this RuneLite made the group. A link on another site or in another shape
is not opened, and with no answer the plain map opens instead, which needs no request. On the login
screen, where no character names a member, it and **Rename group…** use a character of this RuneLite
that the group has already issued a secret to.

While logged in, connecting sends a random member id, your alias, a sender timestamp and pairing
credentials even with character sharing off. Snapshots are spaced at least two seconds apart;
an unchanged character normally sends a heartbeat every six seconds, or twenty with the receive
stream connected. Automatic character sharing stops while logged out.

With **Farming contracts** shared, the crop and patch kind remain on the snapshot when the
contract is complete, with state `return` instead of `active`. This means ready to hand in to
Guildmaster Jane; an absent or unreadable contract remains unknown. The existing setting guards both states.

**Port tasks** sends all five Sailing task slots, slot 0 first, each as `courier`, `bounty`,
`empty`, `locked` or `unknown`. A held task carries the game's task id and its own counters:
crates collected and delivered for a courier, parts still owed for a bounty. `unknown` is a slot
whose value names no row this client can read, and stays distinct from `empty`. Nothing is sent for
fifteen seconds after a login or a hop, while every varbit still reads zero. Task names, ports and
crate counts are read from the client's tables for the sidebar and are not sent; the map has the
same table. Cargo itself, and anything else in the hold, is never read. With the slots goes `hold`,
how many crates the cargo hold of the boat you last stood on carries, from the hold and hull objects
that boat spawns and RuneLite's account type for an Ultimate Ironman's limit. It is absent until you
stand on a boat with a hold, and forgotten at logout, a hop or a character change, so the map plans
as if only one task's crates fit until it has been seen.

**Farming patches** covers the 99 crop patches in RuneLite 1.13.4's Time Tracking catalogue,
excluding compost bins. This includes the additional hops, bushes, hardwood, spirit trees,
cactus, special Guild crops, calquat, belladonna, mushrooms, grapes, crystal trees, Hespori and
coral. The same setting, snapshot cadence and local replant evidence apply to every kind.
Off-surface patches can be observed even where the website's surface run planner cannot visit.
The snapshot byte cap is unchanged: it drops older observations first and flags omitted details.

**Boats and moorings** sends, for each boat slot that shows a boat, its slot number, the game's
dock id for where it is moored and that dock's name, and its teleport focus as none, teleport or
greater. Lost, capsized and bottled boats are sent without a dock. Nothing is sent for 15 seconds
after a login or hop, while the player stands on a deck, when a slot cannot be read, or when no slot
shows a boat; the map treats that absence as unknown, not as owning no boat. Boat names, health,
type and cargo are never sent. The same values are read locally, whatever the setting, to follow a
voyage's steps; that sends nothing.

**The step after a first Connect.** Every sharing setting, **Show destinations from the map** and
**Show my group on the world map** start off. After the first Connect to a map, the sidebar shows a row each for the
character name, the four sharing groups and those two drawing choices, all ticked, and *Where you
died* unticked, with a line saying the drawing choices open one connection. A group's row is all of
its fields or none, as its switch in Settings is. *What each one sends*,
a fold under the rows, names every field and describes the receive stream in full. **Share what's ticked** writes exactly the ticked fields, as the switches in Settings
do, and is the only thing in the step that changes anything: ticking and
**Not now** write nothing, and nothing in the step makes a request. It follows only the Connect that
approved its map, which happens once per map, and only while every choice it could turn on is still
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
sidebar moves its steps on from your position in the game without telling the map. A voyage's
steps also carry its moorings, a simplified line of the sail and the docking mooring's gates; its
rows tick from whether you stand on a deck and where your boats read moored, read locally whatever
the boats setting and never sent. **Reroute**
under those steps makes one `POST /api/companion/route` per press, with the group code, member id
and member secret. It carries the destination's tile and the tile you stand on, whether or not
position sharing is on, because a route from where you stand is what the press asks for. One is in
flight at a time, and none before the wait the map last asked for: its `Retry-After`, and at least
five seconds between presses that reached it. The map plans with both tiles and keeps neither.
A teleport step's picture is the game's own, from the client's sprite cache: the step names an
item's `itemId` or a spell icon's `spriteId`, and no picture is fetched from the map or elsewhere.

**Quest and diary announcements.** With *Notable drops and levels* on, a quest or achievement diary tier
finished while logged in rides the next snapshot as an announcement: the quest's name, or the
diary's region and tier, with a random id, retried until the map names it, like a drop. Each kind
also needs its own field, all three under **Progress and travel**: *Full quest progress* for any
quest, *Travel quest completion* for the 49 travel quests, *Achievement diaries* for a tier.
Announcements are built from those fields'
existing readings, so watching adds no client reads beyond them and no requests. Only a change
the plugin saw happen is announced: the first reading after a login, a world hop, a lost game
connection, a change of character or group, or one of those choices turned back on is a new
baseline, and nothing done before it is announced. Quest starts, single diary tasks and the
Recipe for Disaster subquests are never announced. The map keeps a quest only under a name its
own quest list carries.

**Farm runs.** With **Show destinations from the map** enabled, snapshots include the plumbing
field `runReceiverVersion: 1`. It contains no character data and is omitted with that setting off.
The website offers Send run only for a fresh, compatible receiver belonging to its own reader.
An itinerary arrives as `run` and a monotonic `runRevision` in the existing reply/stream; its
removal carries the revision without a run. There is no additional poll or automatic planning.

**Start run** and a run's **Reroute** each make one `POST /api/companion/run` with the same
three member credentials as Route. Start sends a bounded preset/run-kind string, `port-tasks`
from the Port tasks card; Reroute sends the run id and the `done` and `visited` ordinals. Both include the tile the player stands on,
even with position sharing off. They share Route's one-flight gate and Retry-After wait.
**End run** makes one `DELETE /api/companion/run?run=<id>`; the id guard prevents an old End
from erasing a replacement run. An initial plan can be cancelled before it has an id.
The 204 reply's `X-Companion-Run-Revision` supplies the removal counter even if the stream
coalesces that frame. Until removal is confirmed, a cancelled initial plan cannot return over
the stream; End remains available to retry if that request failed. A new explicit Start can
replace it by its own successful reply.
There is no automatic retry. A run is private to that member and kept in relay memory for up
to three hours; it is not saved in the database. The request's starting tile is used then dropped.

An active run continues local farming observation with farming sharing off, to follow witnessed
replants, and checks derived carried-item flags locally for its bank checklist. The existing
snapshot settings still gate all automatic disclosure. The explicit Reroute press shares only
its stop ordinals, not the underlying receipts or inventory. Birdhouse progress comes from the
existing read-only Time Tracking settings and requires a fresh seeded sighting after the run
was received. Custom stops are visited, never marked as a confirmed replant.

A port-task run is planned on the map from what the snapshot already carries under **Port tasks**
and **Boats and moorings**; Start adds nothing to it but the run kind and the tile. Its stops are
ticked locally from the task slots `PortTaskReader` reads each tick while **Port tasks** is on,
and nothing of that ticking is sent: Reroute shares only the ordinals, as for a farm run.
The receiver rejects old revisions, old character channels and callbacks from ended connections.
`relayEpoch` on replies/stream frames, and `X-Companion-Relay-Epoch` on End's 204 response,
identify the lifetime of that relay's memory. It is a random public process marker, not a
credential or a character identifier. A server restart clears the lost itinerary and resets
its counters locally; delayed frames from the retired process remain rejected. The marker is
received on existing requests, with no additional polling or outgoing character field.

**Slayer places.** With **Slayer task** shared, snapshots also carry `slayerPreferences`:
version 1, the kept `best`/`single` order, the Wilderness filter and `seenRevision`. The server
uses the website's ranking and the same fused account profile. Its own-member directive carries
`slayerOptions` and `slayerRevision` until acknowledged; a task, profile or choice change can
produce a new list. Remaining kills are displayed from the local reader and do not resend the
list. The existing heartbeat and optional receive stream deliver it; sharing Slayer alone opens
no stream. Turning sharing off clears the list and omits these fields. No list is written to disk.
The relay epoch also scopes this counter across server restarts.

**Route** on a Slayer place sends its public spot id and the player's current tile through
the existing route endpoint, even with position sharing off. The server chooses a recorded
spawn nearest the player already at that spot, otherwise the spot's recorded anchor. Boss
versions with a recorded entrance route to that entrance. **World map** sets a local destination
and hint arrow, with no request. The list credits the OSRS Wiki and its CC BY-NC-SA 3.0 licence.

**Search**, at the top of Home while connected, asks `GET /api/companion/search?q=` with the
same three credentials, about 350ms after the last key or at once on Enter, and never for fewer than
two characters. One is in flight at a time: a query typed meanwhile waits for it, and the older
answer is dropped if the box has moved on. Clearing the box cancels the call. A `Retry-After` is
honoured by asking nothing until it has passed, and a failure without one waits ten seconds. The
query is in the request's address, which the map leaves out of its logs; the plugin logs nothing it
types. A pick is one `POST /api/companion/route` with the row's id and the tile you stand on, under
Reroute's rules, and its answer becomes the destination, as a destination the map sent would.
**Route here**, on a right-click of the game's world map with a group set, is the same request with
the clicked tile as the target, once per press; it is a RuneLite menu entry that the client handles
itself and sends the game nothing. A refusal is said once in the chat box, locally.
Each row's picture is the game's own, from the client's sprite cache: the map's kind for a row picks
a world-map icon, and an item's `itemId`, where the map names one, picks its sprite. No picture is
ever fetched from the map or anywhere else.

**Route** beside the clue being carried, the last death, a star in the Stars tab or a meetup, or on
the destination's card while it has no way yet, is the same request with a tile as its target: the
clue's dig spot or beginner map, the death's tile, the star's tile, the meetup's, the destination's
own: a star pinned on Stars, a Slayer spot set on the world map, a place the map sent bare. A clue
or a death that is not shared leaves only in that request, once
per press; the map plans with it and keeps nothing. A clue whose step is words names no tile, so
its Route sends the clue as the snapshot carries it, `{ clue: { tier, text, itemId } }`, and the
map places it with its own clue table; the words leave only in that request, whether or not
*Clue step* is shared. A map above beginner has no words and no tile either, but each is a scroll of
its own, which RuneLite's clue plugin and the map's table both know by item id: its Route sends the
same shape with nothing but `{ clue: { tier, itemId } }`, and the map places the picture by the
scroll. A death inside a boss's instance is recorded as the tile its arena is copied
from, and the map decides whether that route goes to the boss's way in. A star whose tile is
approximate offers **Open website** instead, which opens the map's search for the site's name in
your browser and sends this client nothing. The clue is now read whether or not *Clue step* is shared,
so the sidebar has it to offer; the snapshot still carries it only with that choice.

**The Group section** is read from what the map already sends: `roster`, one row per other member
with their name, colour, presence, *near*, *Near you* and newest news, arrives on every snapshot
reply and stream frame and is shown whatever the destination setting; `events`, the week's meetups
with their answers, is shown only with **Show destinations from the map**, as the single `event`
always was. A map that sends neither leaves the sidebar as it was. Neither carries a tile.
**World map** beside a friend, or **Route** to a friend whose position this client has not been
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
paste, and on **Connect** and **Join**, where it names the RuneLite that made or joined a group; with
a group code it admits your characters to your groups, so connect only to a map you trust. The
member id and installation key are the same for every map and group this RuneLite connects to until
**Leave group**, and the receive stream carries the group code and member id in its URL.
`X-Companion-Member-Secret` is a random credential for one server, group and character, used on
snapshots, invites, the receive stream, star-preference sync and **Open the map**. It is never displayed
or logged. **Leave group** deletes every one of these member secrets and replaces the installation
identity; your local kill log remains. A remembered group that issued one keeps its members and
admits the new identity only by invite, so it leaves **Switch ▾** with the group left; a group that
never issued one still admits by its code, and stays.

Local game messages provide bounded reward and assignment facts only. **Settings → Advanced → Show what's
sent** previews the current snapshot; credentials are named without displaying standing secrets.

## Storage

The plugin keeps pairing settings, credentials, preferences, patch observations and a bounded kill
log in its own RuneLite config group. Kill logs, pins and observations are character-scoped.
**Forget kill log** clears the current character's counts and pin. Each sharing group's switch in
RuneLite's list is kept there too, beside the per-field choices it stands for, which stay what is sent;
the plugin keeps the two in step and never sends a switch. A marker, `sharingGroups`, records that a
profile's choices have been read as groups, so an update keeps every choice as it was.

On the default map server, live positions and progress stay in temporary memory; there is no stored
movement history. Group membership, last shared names, invites, meetups and group pins persist.
A new group keeps an activity feed of its members' notable drops, levels, quests and diaries:
up to 200 entries for 14 days. Its owner can turn this off, and a group made before 29 October 2026
keeps the setting it had. *Loot and milestones* and the step after Connect say *Your group keeps the
last two weeks.*
Leaving stops new sharing; retained announcements expire under the server's
[privacy policy](https://osrs-map.com/privacy).

Drop luck pauses on F2P and special worlds. Pet and collection announcements have separate
switches; collection notices also require the game's chat or popup notification. See
[reward coverage](rewards-and-dryness.md). Task observations such as rumours and Mahogany Homes
expire after two hours or a session change.
