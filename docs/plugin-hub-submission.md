# Plugin Hub submission

**On the Plugin Hub since 27 October 2026**, merged in
[runelite/plugin-hub#19494](https://github.com/runelite/plugin-hub/pull/19494), and updated to 1.3.0
(`fbf96fa`) in [runelite/plugin-hub#19712](https://github.com/runelite/plugin-hub/pull/19712) on
30 October, to 1.4.0 (`6df00cf`) in
[runelite/plugin-hub#19965](https://github.com/runelite/plugin-hub/pull/19965) on 2 November, and
to 1.5.0 (`eaa882b`) in [runelite/plugin-hub#20137](https://github.com/runelite/plugin-hub/pull/20137)
on 4 November, to 1.6.0 (`f2d681f`) in
[runelite/plugin-hub#20209](https://github.com/runelite/plugin-hub/pull/20209) on 6 November,
to 1.7.0 (`21e336e`) in [runelite/plugin-hub#20370](https://github.com/runelite/plugin-hub/pull/20370)
on 9 November, to 1.8.0 (`e70b06a`) in
[runelite/plugin-hub#20477](https://github.com/runelite/plugin-hub/pull/20477) on 11 November, and to
1.9.0 (`cdc324a`) in [runelite/plugin-hub#20581](https://github.com/runelite/plugin-hub/pull/20581)
on 12 November;
RuneLite's review bot approved and merged each update itself. The steps below are
how it was submitted; a release after that is a merged plugin
commit, then `commit=` updated in the Hub's `plugins/osrs-map-companion`. Completed and outstanding game checks
are recorded in [Live checks](live-checks.md). A successful build does not establish live-event
coverage or guarantee RuneLite approval.

## Submit

1. Push the reviewed plugin commit to a public repository. Confirm a clean checkout and read its
   full SHA with `git rev-parse HEAD`.
2. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) and create a branch.
3. Add `plugins/osrs-map-companion` (no extension):

   ```properties
   repository=https://github.com/plbolduc/osrs-map-companion.git
   commit=<reviewed 40-character SHA>
   warning=This plugin sends your IP address and selected character data, including your name, world, position and progress, to a third-party map server not controlled or verified by RuneLite developers.
   ```

4. Open a PR titled **osrs-map-companion: update to *version***. Its description is public, so keep
   it short: one line, a short list of what changed, and only what a reviewer needs besides, such
   as a new request or a changed warning, as in
   [runelite/plugin-hub#20137](https://github.com/runelite/plugin-hub/pull/20137). Validation
   results and live checks stay in this repository.
5. Address reviewer feedback in the plugin repository, then update `commit=` in the same Hub PR.

The warning belongs in the Hub manifest. The root `runelite-plugin.properties` declares
`build=standard`; the root icon is 48×48. See the Hub's
[submission instructions](https://github.com/runelite/plugin-hub#submitting-a-plugin) and
[third-party disclosure rule](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub).

## Reviewer notes

This update expands the existing **Farming patches** field, shared under *Tasks and timers*, to all 99 crop patches in
RuneLite 1.13.4's Time Tracking catalogue, excluding compost bins. The decoder is bundled data
with source attribution and a full parity check; it does not call another plugin's internals.
The existing cadence, consent, byte cap and local empty-to-newly-planted receipt rule remain.
Extra region/floor/boundary tests check slot reuse; the new readers still need a members-client
visit as listed in [Live checks](live-checks.md#farming-procedure).

OSRS Map Companion shares selected character state with a private group on osrs-map.com, supports
map destinations and shooting stars, and keeps local drop-luck counts. It reads only the local
character's game state. Other members' positions come from their consenting installations via the map.

Three things reviewers ask about first:

- **Other players' locations.** The plugin reads no other player from the game. A friend's dot on
  the world map is that friend's own installation sharing their own position, opted into per
  category, to a group both joined with a code; the same consent shape as RuneLite's Party.
  Nothing is crowdsourced and no position is published outside the group. The same answers the
  rule against exposing player information over HTTP: only the logged-in character's own state is
  sent, each field only once the player has chosen it, and only to the group the player joined.
- **Star data.** The Stars tab shows a list osrs-map.com relays from Star Miners, fetched once a
  minute through the map's own coalesced cache. The plugin never contacts Star Miners, who have
  their own plugin and do not endorse this one.

**Defaults, the warning, and the one press at Connect.** Every character-sharing setting, remote
destinations, group markers and meetup notifications still default to off in the config. The
third-party warning is the manifest's `warning=` above, which RuneLite shows on Install; RuneLite's
rule asks for it "either on the plugin, or on the configuration option", so no setting repeats it as
an "Are you sure?" any more. The first Connect to osrs-map.com is that press and asks nothing more; a
changed **Map address** is asked about once, naming the server, before any request goes there.
After the first Connect to a map, and only then, the sidebar shows one step: a row each for the
three sharing switches and one for destinations and group markers, all ticked, with a line
saying that one opens one connection. *What each one sends*, one tap below the rows and before the
press, names every field in the sidebar's own words and the receive stream in full (one held
connection to the named map, carrying the group code, member id and member key). The step's first
line says the group sees the character name and what is ticked. Meetup
notifications are not offered. One press, **Share what's ticked**, writes exactly those existing
settings and the character name, as the switches in Settings do: the press is the opt-in, and nothing turns on
without it. Ticking and **Not now** write nothing. The step follows only the Connect
that approved its map and only while every choice it offers is still off, so an update changes no
existing player's settings and opens no stream for them. `RecommendedSharing` is the step;
`RecommendedSharingTest` holds it to these sentences.

**Sharing as six switches.** RuneLite's list shows sharing as the sidebar does: *Character name*,
*Name to show instead*, a switch for each of the four groups, and *Where you died*. The 23 per-field
settings under the groups keep their keys and stay what `SnapshotBuilder` reads; none is in the list.
*Position and world* is declared `hidden`, and the other 22 are not declared at all: they are read
by key (`ConfigStore.on`), and **Reset** turns them off in `MapCompanionPlugin.resetConfiguration`,
since RuneLite resets only what is declared. Settings in the sidebar draws three switches over
them: *Where you are* is Map presence and Where you died, *Your account* is Progress and travel and
Tasks and timers, and *Loot and milestones* is itself. The character name is a line and a link under
them, and *What each one sends* names every field. `SharingGroups` writes a group's fields when its switch is pressed and moves the
switch when they are written, so a switch shows on only while every field in it is; a group a player
chose part of before the update shows off, and Settings names the fields that are on. Every switch
starts off.
The first start after the update writes each switch from the fields already chosen and never a field,
so no existing player's sharing changes, a group they chose part of included; `SharingGroupsTest`
holds that with the snapshot itself.

Server approval and credentials are scoped to the chosen address; changing it pauses requests
until Connect. No pairing code or no approved server means no network requests. Joining with
sharing off still sends a random member id, alias, timestamp and connection credentials.

### Network requests

All paths use **Map address** (`https://osrs-map.com` by default); clipboard contents cannot change it.
Only `RelayClient`, `DirectiveStream`, `StarsClient`, `RoutePress`, `SearchClient`,
`MeetupClient`, `FarmRunClient` and `ConnectClient` construct HTTP requests:

- **POST /api/companion/connect** — one **Connect** press with no group, or one **Join** press with a
  pasted invite (or **Connect** with one in the field). Header: `X-Companion-Installation`. JSON is
  empty, or the group code and the invite's token from the pasted link. No character data, member id
  or secret. The answer is a
  connection code, kept exactly as a pasted one; a refusal is printed. One at a time, no retry.
- **POST /api/companion/link** — one **Open the map** press while connected. Headers:
  `X-Companion-Code`, `X-Companion-Member`, `X-Companion-Member-Secret`. The answer is a one-time
  link, opened with RuneLite's `LinkBrowser` only if it is the approved map's own page with the
  token in the URL fragment; otherwise the plain map opens. Never retried.
- **PATCH /api/companion/group** — one **Rename group…** press, offered only when the map's reply said
  this member may (`groupOwner`). The same three headers; JSON is the new name. No retry.
- **DELETE /api/companion/member** — one **Remove from group…** press, from a friend's right-click,
  confirmed first and offered only where Rename is. The same three headers; JSON is the friend's
  member id from the roster. No retry.

- **POST /api/companion/snapshot** — logged-in state changes, at least two seconds apart, plus
  six-second idle heartbeats (twenty seconds with a stream). JSON comes from `SnapshotBuilder`;
  each character field is gated independently. With *Slayer task* shared it also sends
  the list's ranking order, Wilderness filter and delivery receipt (`slayerPreferences`).
  With *Notable drops and levels* and the matching quest or diary field on, `milestones` can carry a
  quest's name or a diary's region and tier, only for a completion the plugin watched change
  while logged in; never one already done at login, after a hop or after re-enabling sharing.
  Its ranked places arrive on the existing member directive, only until acknowledged;
  this adds no poll or stream. With Port tasks enabled it carries the five Sailing task
  slots (`portTasks`): each slot's state, the task id and the game's own counters, read from
  varbits and the client's `PortTask` table by value, and the crates the hold of the boat last
  stood on carries, from its spawned hold and hull objects. Cargo is never read. With Dailies
  collected enabled it carries `dailies`: for eight dailies, by the map's id, whether each was
  collected today, from the claim varbits RuneLite's Daily Task Indicators reads. With Sea charts
  charted enabled it carries `seaCharts`, the charted tasks' ids, from the twelve charting varps.
  With Slayer task enabled and no task held it carries `mortimerOffer`, the tasks Mortimer's choice
  interface offers, read from its varbits and the client's `SlayerTask` table by value. Headers: `X-Companion-Code`, optional
  `X-Companion-Member-Secret`, `X-Companion-Installation`, and `X-Companion-Suffix`
  until the browser-link suffix is claimed.
- **POST /api/companion/invite** — one **Copy invite** press. Group code and optional member-secret
  headers; random member id in JSON. A failure copies nothing and says so; only a group that still
  admits by its code (a 404, or no member secret held) gets a plain-text invite message instead.
- **GET /api/companion/stream** — SSE while destinations or group markers are enabled and logged in.
  Group code and member id are the `code` and `for` query parameters; the member secret is a
  header. `members=1` is added only for group markers, or for a friend the player pressed **Show on
  map** or **Route** beside. Receives destinations, meetups, the group's roster and the requested
  members; never initiates gameplay.
- **GET /api/companion/stars** — while Stars is open, logged in and connected; normally once a
  minute, respecting a longer feed interval. No character/group credentials. The tab's filters,
  favourite sites, Mining level and total stay on this machine.
- **POST /api/companion/route** — one **Reroute** press, under the steps of a destination the
  map sent. One in flight, and none before the wait the map last asked for (Retry-After, at least
  five seconds). Headers: `X-Companion-Code`, `X-Companion-Member`, `X-Companion-Member-Secret`.
  JSON is the destination's tile and the player's own tile, sent on the press even with position
  sharing off; the README says so. The answer replaces the sidebar's steps. Nothing is walked.
  `RoutePress` sends the same request for a pick from **Search**, with the row's id as the target,
  and for **Route** beside the carried clue, the last death, Quest Helper's step, a star and a
  meetup, and on a
  destination shown with no way yet, with that tile as the target; a clue step that is words goes
  as the clue itself, its tier, words and scroll item id, and a map above beginner as the same clue
  with its tier and scroll item id alone.
  A clue or death that is not shared leaves only in that request. Same
  one-at-a-time rule and wait; the answer becomes the destination. Nothing is walked. **Route here**
  on a right-click of the world map sends it too, with the clicked tile as the target. On Stars, a
  press on a star pins it in the client and, once it has stayed pinned for 0.7 seconds, sends this
  request once with the star's tile as the target and no further press: one request for the star
  stopped on and none for stars pressed past, never repeated on its own, under the same
  one-at-a-time rule and waits; the README says a press sends the two tiles.
- **PUT /api/companion/event/answer** — one press of a meetup's answer (**Going**, **Maybe**,
  **Can't**, **On my way**). JSON is the meetup's id and the answer. Headers:
  `X-Companion-Code`, `X-Companion-Member`, `X-Companion-Member-Secret`.
- **PUT /api/companion/event** — one **Meet here** press. JSON is a title, a start time, the player's
  world and a tile, the player's own or one right-clicked on the game's world map; the same headers. The path the map answers with is checked and copied to the
  clipboard. One meetup write at a time, and none before `Retry-After` has passed.
- **GET /api/companion/search** — the sidebar's search box, while connected: about 350ms after the
  last key or on Enter, two to sixty-four characters, one in flight, and nothing before a
  `Retry-After` has passed. Same three headers; the query is the `q` parameter and is never logged.
  The rows' icons are the client's own sprites (`SpriteManager`, `ItemManager`); no image is downloaded.
- **POST /api/companion/run** — one **Start run** or **Reroute** press, from the farm run or Port
  tasks card, with the same three credential headers as Route. Start carries a run kind (a farm
  run's, `port-tasks`, or `sea-charts` for the sea charting run); Reroute carries its id, done ordinals and
  visited reference ordinals. Both carry the local tile even with position sharing off. Route and
  farm planning share one flight and the server's Retry-After wait. No automatic retry.
  **DELETE /api/companion/run?run=<id>** ends only that run; a pending initial plan has no id yet.
  It also ends locally immediately. The map keeps a run privately in memory for up to three hours.
  The 204 reply carries `X-Companion-Run-Revision`, preventing a delayed initial-plan stream frame
  from reviving it even if the removal frame was missed.
  A public `relayEpoch` in replies/stream frames (and `X-Companion-Relay-Epoch` on End)
  scopes those counters to the server's memory lifetime, so a server restart cannot strand
  a client behind its former counter. No new request or outgoing character data.
  Reply/stream frames carry the itinerary and revision; no extra polling. The snapshot advertises
  `runReceiverVersion: 1` only with destinations enabled. Local replant observation while a run
  is active does not bypass the existing farming snapshot sharing choice; a checklist's local
  carried-item check also sends nothing. Reroute explicitly shares only the progress ordinals.
  A port-task run's stops are ticked from the task slots read locally; that ticking sends nothing.

Requests carry the versioned User-Agent and `Cache-Control: no-store`, so no reply is written to RuneLite's HTTP cache. The address must be https (plain http only on loopback),
redirects are not followed, and reply bodies are capped at 1 MB. Snapshot and stream failures back
off; the Stars, Reroute, route-press, farm-run, search and meetup clients also honour Retry-After. Nothing requests the upstream star feed directly.
The README's [What you share](../README.md#what-you-share) names every transmitted category, and
[Network and storage](network.md) covers credentials and storage.

### Credentials, client APIs and packaging

Member ids, installation keys and member secrets are random, never account-derived. Secrets are
scoped by server, group and character, stored in the plugin's RuneLite config, and never displayed
or logged. Leave group deletes them and resets installation identity. The member id and the
installation key are per installation, not per server or group, and [Network and storage](network.md) says so. Clipboard
reads (Paste) and writes (Copy invite, Copy connection code) happen only on a button press; an
invite link is copied only if it is on the approved map. Meetup notifications use RuneLite's
`Notifier`, with the title stripped of control characters, cut to 60 characters, and at most one a
minute. *Meetup reminder* is a RuneLite `Notification` setting, so its cog in RuneLite's panel
says how it arrives; the sidebar's box turns it on with RuneLite's own notification settings copied
and *Send notifications when focused* on, and leaves one the player customised as they set it.

`BoatReader` reads the five `SAILING_BOAT_n_*` varbits and looks a stored port up in the game's
`SailingDock` table by value, as the game's own dock lookup does; it is read-only.

`DeathWatcher` reads `GRAVESTONE_VISIBLE` and `GRAVESTONE_DURATION` on the game tick, once the
session has settled, to show the gravestone's own time left under the death card. It is read-only
and local: nothing about it joins a snapshot or any request.

The plugin takes no gameplay action. `StashReader` uses
the read-only `WATSON_STASH_UNIT_CHECK` script used by RuneLite's clue plugin, only with STASH
sharing enabled, at most once a minute. `DestinationArrow` sets the hint arrow on a loaded
destination, or on the end of the current step of its route, without replacing another arrow.
A route's steps are numbered `MapPin`s on the world map; like every pin here, a click pans the
map. A voyage's sail is the one route drawn as a line, by `VoyageMapOverlay`, because Shortest Path
has no row for sailing a player's own boat. Group pins clear on connection loss, logout, group change, sharing withdrawal and shutdown;
queued old frames are invalidated.

`ShortestPath` posts RuneLite `PluginMessage`s to the Shortest Path plugin in namespace
`shortestpath`, inside the client: `getTarget` to learn whether it is installed and what it draws,
`path` with `start` and `target` for the current walking step of a route the player pressed Follow
or Reroute on, and `clear` for a path this plugin set and Shortest Path still shows. Nothing goes
during a teleport or ride step, for a destination that merely arrived, or with the setting off, and
Shortest Path's `config` override is never sent. Drawing a path is information; nothing walks.
It also reads Quest Helper's own `path` and `clear` to Shortest Path, offering them only while
`PluginManager` and `ConfigManager` say Quest Helper is running with its Shortest Path setting on,
so Home can offer **Route** to the quest step; it posts nothing to Quest Helper.

Reconnects use RuneLite's injected scheduler; focus checks stay within the plugin's search field.
`shutDown` removes its panel and overlays on the EDT and queues everything a game tick touches on
the client thread, so a tick already running cannot reopen the stream after the plugin stops.
It uses none of the restricted APIs in RuneLite's
[current rules](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

The standard Hub template compiles/packages `src/main` with Java 11. There are no additional
runtime dependencies: it uses RuneLite's OkHttp, Gson and Guice. The relay, stream and Stars
clients are derived from the injected OkHttp client with `newBuilder()`, which keeps its dispatcher
and connection pool; the only change is that redirects are refused, plus a read deadline on the
stream. `java.lang.reflect.Type` appears only as Gson's `TypeToken`. The map's own source, where its
contract tests and browser story live, is not public. Tests, the integration harness and
screenshot tools are outside `src/main`. Bundled third-party notices accompany their resources.

## Changes since the approved submission

What the next Hub update brings. Its description names each in a few words; the detail here is for
answering a reviewer. Since 1.9.0, for 1.10.0, in review in
[runelite/plugin-hub#20645](https://github.com/runelite/plugin-hub/pull/20645):

- **Drop rates for thirteen more bosses:** the bundled `unique-rates.json` grows from 341 rates to
  374, with Commander Zilyana, the Corporeal Beast, Phantom Muspah, the Dagannoth Kings, Spindel,
  Calvar'ion, the Kalphite Queen, the Chaos Elemental, the Abyssal Sire, the Alchemical Hydra and
  Amoxliatl. Each is transcribed from the wiki page's own drop line. The counts stay on the
  computer; a player who shares *Loot and milestones* sends the same fields as before, now for
  these drops too.
- **Pets, sets, team bosses alone and awakened bosses:** the table grows on to 506 rates across 193
  monsters. A pet is counted from the game's own pet line in chat, against the kill beside it; a
  set that drops a piece at a time counts as one drop. Callisto, Venenatis, Vet'ion, the Hueycoatl
  and Nex count a kill other players hit as the player's share of it: their own damage over
  everyone's, from `HitsplatApplied` (`isMine()`, `isOthers()` and the amount, kept per fight for a
  minute), with Nex's MVP read from the game's "You were the MVP for this fight". The Nightmare,
  Scurrius and Zalcano count only kills no other player hit. The lizardman shaman counts none
  in an instance; Araxxor counts no destroyed corpse; and the Desert Treasure II bosses are told
  from their awakened fights by the loot NPC's combat level. All of it is read from events the
  plugin already receives or from the loot itself. Each drop's report gains four fields — how many
  of its kills were shared, and the summed share, since its last drop and since watching began —
  disclosed under *Loot and milestones*, and sent only where there were any.
- **A fairer Dry in All monsters:** a monster's chip and its place under *Driest* weigh its driest
  wait against how many of its drops could be that dry, and the drop the chip is about is outlined.
  Arithmetic on local counts; nothing is sent.
- **"Recorded kills":** the chat line for the first drop the log has seen says its wait counts
  only the kills the plugin recorded. The existing *Drop luck in chat* setting governs it.
- **Description and tags:** the manifest and the `@PluginDescriptor` name Drop luck, with the tags
  `loot`, `drops`, `luck` and `dry`.
- **The ways as chips:** a route's ways are a strip of chips over its steps, each with its first
  teleport's picture and the name the map gives it. The route reply gains one optional string per
  way, `face`, bounded as the label is and read only; a reply without it shows the label. Picking a
  chip, and keeping it through a Reroute before the player sets off, happen in the client and send
  nothing. A walk, a boarding, a sail, a dock, a way in and a way down are drawn with the client's own
  sprites by id; nothing is downloaded.
- **One press on a star:** a press on a star in the Stars tab pins it and then asks the way, where it
  opened Route and Pin to choose between. The request is Route's own, listed under Network requests,
  with the tile the player stands on as Route sends it: sent once, 0.7 seconds after the press if
  the star is still pinned, through the gate every route shares, and not repeated after a refusal.
  The star list's own request and its once-a-minute schedule are unchanged.
- **A farm run keeps Route's wait on a busy map:** after a 429 or 503 a run waits the `Retry-After`
  it names, held between five seconds and five minutes, as Route does, where it waited at least
  half a minute; the two share one gate. Asking again is still the player's press.
- **A redirecting map address** keeps search and meetups quiet for five minutes, as Route already
  did. Turning the plugin off cancels a search still waiting to be sent and forgets where the
  character died.
- **Less work each tick and frame:** the sidebar, the star list and the Port tasks card are drawn
  again only when what they show has changed, and the tick's own checks run once where they ran
  several times. Nothing about what is read or sent changes.
- **Words and layout:** a friend's row leads with their world, distances sit in a column at the
  right, the step box says how far through the route it is, and a few controls gained tooltips.
  Nothing is sent for any of it.
- **One reply handler and one way to end a session:** internal; every request, header and wait is
  the one it was.

No setting or warning is added. The one new field, `face`, arrives in a reply, and the one new send
is the route request a star press makes, which is Route's own.

## Before submission

- [ ] Review and commit the final source; match `build.gradle` and `CompanionVersion.VERSION`.
- [ ] Write `update.line` in `src/main/resources/words.txt` (`UpdateNotice.LINE`), the one chat line
      a player hears on the first login after updating, for what this release changed, and move
      `CompanionVersion.LINE_VERSION` with `VERSION`; `UpdateNoticeTest` fails until they match.
- [ ] Read the plugin's description and tags as a player searching the Hub would: `description=` and
      `tags=` in `runelite-plugin.properties`, and the same two on `MapCompanionPlugin`'s
      `@PluginDescriptor`, which RuneLite's plugin list shows. They must agree, which
      `PluginDescriptionTest` checks, and still say what the plugin does, which only a reader can.
- [ ] Run `./gradlew clean build -PruneLiteVersion=1.13.4` from a clean checkout.
      Recheck the Hub's [current client version](https://github.com/runelite/plugin-hub/blob/master/runelite.version).
- [ ] `SourceSizeTest` passes: `src/main/java` without comments stays under 772,000 characters,
      about 199.8k tokens, because the Hub reviews an update automatically only under 200k.
- [ ] Verify production sources against the Hub's
      [standard template](https://github.com/runelite/plugin-hub-tooling/blob/v4/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle).
- [ ] Run the map's required plugin-contract tests and integration story against the submitted SHA.
- [ ] Complete the outstanding [live checks](live-checks.md); deploy map support before the plugin.
      In particular, the map must accept `/map?p=x,y,plane` death links.
- [ ] Make the source public, include the installer warning, and pin the pushed SHA.

The live checks currently remain incomplete. Publishing source for review and claiming a fully
verified release are separate milestones. RuneLite's review and merge control Hub availability.
