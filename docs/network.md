<!-- Copyright (c) 2026, pbolduc. All rights reserved. SPDX-License-Identifier: BSD-2-Clause -->
# Network and storage

The README's [What you share](../README.md#what-you-share) names every category a player can
send, and `NetworkDisclosureTest` holds it to `SnapshotBuilder`. This page is the rest: which
requests carry it, what identifies a member, and what is kept. The
[submission guide](plugin-hub-submission.md#network-requests) lists every endpoint.

## Requests

Requests go to **Advanced → Map address**, default `https://osrs-map.com`; it must be https
unless the map runs on this machine, and redirects are not followed. With no pairing code or no
approval of the selected server, the plugin makes no requests. First **Connect** discloses that
destination; changing it pauses requests until you connect there.

While logged in, connecting sends a random member id, your alias, a sender timestamp and pairing
credentials even with character sharing off. Snapshots are spaced at least two seconds apart;
an unchanged character normally sends a heartbeat every six seconds, or twenty with the receive
stream connected. Automatic character sharing stops while logged out.

**Show destinations from the map** and **Show my group on the world map** start off. Enabling
either opens a receive stream; group positions are requested only with the latter. **Copy invite**
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

Local game messages provide bounded reward and assignment facts only. **Advanced → Show what's
sent** previews the current snapshot; credentials are named without displaying standing secrets.

## Storage

The plugin keeps pairing settings, credentials, preferences, patch observations and a bounded kill
log in its own RuneLite config group. Kill logs, pins and observations are character-scoped.
**Forget kill log** clears the current character's counts and pin.

On the default map server, live positions and progress stay in temporary memory; there is no stored
movement history. Group membership, last shared names, invites, meetups and group pins persist.
An owner may enable a kept activity feed: up to 200 drop/level announcements for 14 days.
Leaving stops new sharing; retained announcements expire under the server's
[privacy policy](https://osrs-map.com/privacy).

Drop luck pauses on F2P and special worlds. Pet and collection announcements have separate
switches; collection notices also require the game's chat or popup notification. See
[reward coverage](rewards-and-dryness.md). Task observations such as rumours and Mahogany Homes
expire after two hours or a session change.
