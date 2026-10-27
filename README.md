# OSRS Map Companion

A RuneLite plugin for [osrs-map.com](https://osrs-map.com): follow your character, plan travel
around your unlocks, and share a map with friends.

- Live position, group members, slayer tasks and clue destinations.
- Farming observations, birdhouses, contracts, quest progress and travel unlocks.
- Shooting stars, favourite sites and optional website filter sync.
- Local drop luck, death-location links, destinations and meetup reminders.

<p>
  <img src="docs/mockups/sharing-sidebar-overview.png" width="240" alt="The sidebar's Overview tab: connected to a group, Open the map, and a Drop luck card for an aberrant spectre">
  <img src="docs/mockups/sharing-sidebar-sharing.png" width="240" alt="The Sharing tab: map presence, progress and travel, tasks and timers, and loot, each chosen separately">
  <img src="docs/mockups/stars-tab-list.png" width="240" alt="The Stars tab: shooting stars by site, world, tier and time left, with filters">
</p>
<p>
  <img src="docs/mockups/world-map-pins.png" width="510" alt="Pins on the game's world map: two friends, a quiet friend, a destination and a meetup">
</p>

The pictures are the plugin's own panels drawn with made-up data.

**Not yet listed on the Plugin Hub.** The [submission guide](docs/plugin-hub-submission.md)
and [live-check record](docs/live-checks.md) distinguish completed checks from remaining work.

## Setting it up

1. Install **OSRS Map Companion** from the Plugin Hub once it is listed, or start the
   [development client](#building-and-running).
2. Open the map's [Companion panel](https://osrs-map.com/map?task=companion), then create a group
   or follow a friend's invite.
3. Press **Copy connection code** on the map, then **Paste → Connect** in the plugin.
4. Read the server disclosure. Under **Sharing → Map presence → Customise**, enable
   **Position and world** if you want a dot on the map. Choose other fields individually.

Sharing starts off. Joining does not enable it. **Name to show** sets an alias; **Character name**
and **Last death location** are separate choices. The connection field accepts a full RuneLite
code, an invite link, a pasted invite message or a bare group code. **Copy invite** copies a
message to your clipboard; it never types into the game. **Switch** remembers up to eight groups.

## Network disclosure

**This plugin contacts a third-party server, which receives your IP address.** Requests go to
**Advanced → Map address**, default `https://osrs-map.com`; it must be https unless the map runs
on this machine, and redirects are not followed. With no pairing code or no approval
of the selected server, it makes no requests. First **Connect** discloses that destination;
changing it pauses requests until you connect there.

While logged in, connecting sends a random member id, your alias, a sender timestamp and pairing
credentials even with character sharing off. Snapshots are spaced at least two seconds apart;
an unchanged character normally sends a heartbeat every six seconds, or twenty with the receive
stream connected. Automatic character sharing stops while logged out.

Each enabled category can send:

- **Name and presence:** the display name of the character you are logged in as; your account type;
  your position, your world and what kind of world it is; whether you are inside an instance and
  whether that instance is one place or rooms put together; which skill you are currently training,
  recent experience and how long since you last moved.
- **Progress and travel:** skill levels; started, finished, or neither for the 49 quests that unlock
  travel; optionally the full quest and miniquest log; which achievement diary tier you have finished
  and which of those rewards you have collected; derived travel unlocks from carried/equipped items
  as yes/no flags; verified permanent transport unlocks, your house town and which STASH units you
  have built. STASH contents are not read.
- **Tasks and timers:** your current slayer task and remaining count; your active clue step
  (tier, item id, observed clue text and resolved tile); your observed hunter rumour;
  your Mahogany Homes customer and tier; the crop requested by your farming contract;
  birdhouse states and observation ages; the state of farming patches you have walked past,
  including crop, stages, observation age, estimated growth windows and witnessed replant receipts.
- **Loot:** observed loot kills for every monster, how long ago each was last killed, notable drop
  counts and observation baselines, kills since a drop, slayer-task/diary rate conditions, the
  monster currently being killed and your pinned drop. Completeness flags identify omitted history.
- **Announcements and death:** an announcement of a notable reward or a real level gained,
  with observed item name/id, quantity, NPC/activity source and optional pet/collection badge;
  the tile where you most recently died and its age. Unknown reward details remain absent.
  Large snapshots include a flag saying some of it was left out.

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

**Show destinations from the map** and **Show my group on the world map** start off. Enabling
either opens a receive stream; group positions are requested only with the latter. **Copy invite**
makes one request for an expiring invite. While **Stars** is open, logged in and connected,
the star list is fetched about once a minute without character or group credentials. Star data
comes from Star Miners by way of osrs-map.com; the plugin never contacts Star Miners, who do not
endorse it. **Sync filters
with website** starts on for that tab: it reads/writes filters and favourite sites every thirty
seconds and after changes, using the group code, member id and member secret. Mining level and
skill total filter stars locally. Turn sync off to keep those preferences local.
The [reviewer guide](docs/plugin-hub-submission.md#network-requests) lists every endpoint and credential.

Raw chat, other players read from the game, bank/inventory contents, existing collection-log slots
and a complete loot history are never sent. Local game messages provide bounded reward/assignment
facts only. **Advanced → Show what's sent** previews the current snapshot; credentials are named
without displaying standing secrets. The server operator and your group's authorised members can
read what you share.

## Storage and observation limits

The plugin keeps pairing settings, credentials, preferences, patch observations and a bounded kill
log in its own RuneLite config group. Kill logs, pins and observations are character-scoped.
**Forget kill log** clears the current character's counts and pin.

On the default map server, live positions and progress stay in temporary memory; there is no stored
movement history. Group membership, last shared names, invites, meetups and group pins persist.
An owner may enable a kept activity feed: up to 200 drop/level announcements for 14 days.
Leaving here stops new sharing; retained announcements expire under the server's
[privacy policy](https://osrs-map.com/privacy).

Drop luck counts observed loot receipts on normal members worlds, not lifetime KC.
It pauses on F2P and special worlds; only sourced rates receive odds. Activity rewards require
RuneLite **Loot Tracker**. Pet and collection announcements have separate switches; collection
notices also require the game's chat or popup notification.
See [reward coverage](docs/rewards-and-dryness.md).

Patch timers are estimates; disease and later actions can change them. A farm stop completes only
after a continuously witnessed empty-to-newly-planted transition. Birdhouses require RuneLite
**Time Tracking**. Recheck rumours with the whistle after login/hopping; recover a home assignment
from an estate agent. These task observations expire after two hours or a session change.
These are covered by tests but not yet confirmed in a live client; see
[live verification](docs/live-checks.md).

## Client actions

The plugin does not walk, click objects, send chat or hop worlds. A star's or destination's world
is printed for the game's own world switcher.

Destinations, meetups and consenting members can appear on the game's world map. A destination
already in the loaded scene may use the game's hint arrow; an existing arrow is preserved.
Local drop-luck chat and destination hint arrows start on; they do not enable network sharing.
**Show on map** beside a death opens its tile and floor in a browser URL when pressed,
including when death sharing is off. The world-map pins, the preserved hint arrow and the death
link are covered by tests; their live-client checks are still pending.

## Building and running

```sh
./gradlew clean build -PruneLiteVersion=1.13.4
./gradlew runClient
```

On Windows use `gradlew.bat`; PowerShell needs `'-PruneLiteVersion=1.13.4'` quoted.
The build fetches a Java 11 toolchain. The Plugin Hub uses its own standard build and client version.

[Contributing](docs/contributing.md) covers formatting, the local map and integration harness.
[Submitting](docs/plugin-hub-submission.md) covers the pinned source commit and review process.

## Licence

BSD 2-Clause; see [LICENSE](LICENSE) and [NOTICE](NOTICE) for bundled third-party notices.
The drop rates in `src/main/resources/unique-rates.json` are transcribed from the OSRS Wiki,
CC BY-NC-SA 3.0; NOTICE names the sources of every bundled table.
