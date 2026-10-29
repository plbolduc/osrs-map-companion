# Plugin Hub submission

**On the Plugin Hub since 27 October 2026**, merged in
[runelite/plugin-hub#19494](https://github.com/runelite/plugin-hub/pull/19494). The steps below
are how it was submitted; a release after that is a merged plugin commit, then `commit=` updated
in the Hub's `plugins/osrs-map-companion`. Completed and outstanding game checks
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

4. Open a PR titled **osrs-map-companion: initial submission**, using the reviewer notes below.
   Include the actual validation results and outstanding live checks.
5. Address reviewer feedback in the plugin repository, then update `commit=` in the same Hub PR.

The warning belongs in the Hub manifest. The root `runelite-plugin.properties` declares
`build=standard`; the root icon is 48×48. See the Hub's
[submission instructions](https://github.com/runelite/plugin-hub#submitting-a-plugin) and
[third-party disclosure rule](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub).

## Reviewer notes

OSRS Map Companion shares selected character state with a private group on osrs-map.com, supports
map destinations and shooting stars, and keeps local drop-luck counts. It reads only the local
character's game state. Other members' positions come from their consenting installations via the map.

Three things reviewers ask about first:

- **Other players' locations.** The plugin reads no other player from the game. A friend's dot on
  the world map is that friend's own installation sharing their own position, opted into per
  category, to a group both joined with a code; the same consent shape as RuneLite's Party.
  Nothing is crowdsourced and no position is published outside the group. The same answers the
  rule against exposing player information over HTTP: only the logged-in character's own state is
  sent, each field by its own opt-in, and only to the group the player joined.
- **Star data.** The Stars tab shows a list osrs-map.com relays from Star Miners, fetched once a
  minute through the map's own coalesced cache. The plugin never contacts Star Miners, who have
  their own plugin and do not endorse this one.

All character-sharing categories, remote destinations, group markers, and meetup notifications
start off. First Connect discloses the destination and IP transmission before any
request. Server approval and credentials are scoped to the chosen address; changing it pauses
requests until Connect. No pairing code or no approved server means no network requests.
Joining with sharing off still sends a random member id, alias, timestamp and connection credentials.

### Network requests

All paths use **Map address** (`https://osrs-map.com` by default); clipboard contents cannot change it.
Only `RelayClient`, `DirectiveStream`, `StarsClient` and `RouteClient` construct HTTP requests:

- **POST /api/companion/snapshot** — logged-in state changes, at least two seconds apart, plus
  six-second idle heartbeats (twenty seconds with a stream). JSON comes from `SnapshotBuilder`;
  each character field is gated independently. Headers: `X-Companion-Code`, optional
  `X-Companion-Member-Secret`, `X-Companion-Installation`, and `X-Companion-Suffix`
  until the browser-link suffix is claimed.
- **POST /api/companion/invite** — one **Copy invite** press. Group code and optional member-secret
  headers; random member id in JSON. A failure copies a plain-text invite message.
- **GET /api/companion/stream** — SSE while destinations or group markers are enabled and logged in.
  Group code and member id are the `code` and `for` query parameters; the member secret is a
  header. `members=1` is added only for group markers. Receives destinations, meetups and the
  requested members; never initiates gameplay.
- **GET /api/companion/stars** — while Stars is open, logged in and connected; normally once a
  minute, respecting a longer feed interval. No character/group credentials.
- **GET /api/companion/star-preferences** and **PUT** to the same path — only with Stars open and
  filter sync enabled (on by default for that tab), a logged-in character and an issued secret.
  Reads every thirty seconds; changes can trigger a write sooner. Headers:
  `X-Companion-Code`, `X-Companion-Member`, `X-Companion-Member-Secret`.
  JSON contains filters, favourite sites and a revision. Mining level and total remain local.
- **POST /api/companion/route** — one **Reroute** press, under the steps of a destination the
  map sent. One in flight, and none before the wait the map last asked for (Retry-After, at least
  five seconds). Headers: `X-Companion-Code`, `X-Companion-Member`, `X-Companion-Member-Secret`.
  JSON is the destination's tile and the player's own tile, sent on the press even with position
  sharing off; the README says so. The answer replaces the sidebar's steps. Nothing is walked.

Requests carry the versioned User-Agent. The address must be https (plain http only on loopback),
redirects are not followed, and reply bodies are capped at 1 MB. Snapshot and stream failures back
off; the Stars and Reroute clients also honour Retry-After. Nothing requests the upstream star feed directly.
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
minute.

The plugin takes no gameplay action. `StashReader` uses
the read-only `WATSON_STASH_UNIT_CHECK` script used by RuneLite's clue plugin, only with STASH
sharing enabled, at most once a minute. `DestinationArrow` sets the hint arrow on a loaded
destination, or on the end of the current step of its route, without replacing another arrow.
A route's steps are numbered `MapPin`s on the world map; like every pin here, a click pans the
map. Group pins clear on connection loss, logout, group change, sharing withdrawal and shutdown;
queued old frames are invalidated.

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

## Before submission

- [ ] Review and commit the final source; match `build.gradle` and `CompanionVersion.VERSION`.
- [ ] Run `./gradlew clean build -PruneLiteVersion=1.13.4` from a clean checkout.
      Recheck the Hub's [current client version](https://github.com/runelite/plugin-hub/blob/master/runelite.version).
- [ ] Verify production sources against the Hub's
      [standard template](https://github.com/runelite/plugin-hub-tooling/blob/v4/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle).
- [ ] Run the map's required plugin-contract tests and integration story against the submitted SHA.
- [ ] Complete the outstanding [live checks](live-checks.md); deploy map support before the plugin.
      In particular, the map must accept `/map?p=x,y,plane` death links.
- [ ] Make the source public, include the installer warning, and pin the pushed SHA.

The live checks currently remain incomplete. Publishing source for review and claiming a fully
verified release are separate milestones. RuneLite's review and merge control Hub availability.
