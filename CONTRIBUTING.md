<!-- Copyright (c) 2026, pbolduc. All rights reserved. SPDX-License-Identifier: BSD-2-Clause -->
# Contributing

## Building and running

```sh
./gradlew clean build -PruneLiteVersion=1.13.4
./gradlew runClient
```

On Windows use `gradlew.bat`; PowerShell needs `'-PruneLiteVersion=1.13.4'` quoted. The build
fetches a Java 11 toolchain, and the Plugin Hub uses its own standard build and client version.
[Network and storage](docs/network.md) is the full account of what the plugin sends and keeps.

## Changing the companion

Follow [RuneLite's code conventions](https://github.com/runelite/runelite/wiki/Code-Conventions):
tabs, braces on the next line, braces around conditional bodies, one logical import block, and
one extra tab inside multiline annotations. Keep annotations on their own lines. Import the
IntelliJ scheme from that page; `.editorconfig` also records the indentation and line endings.
New source files carry this repository's BSD notice from `LICENSE` before the package declaration.

For the brace and whitespace pass, Artistic Style 3.6.18 accepts the checked-in options:

```sh
astyle --options=.astylerc --recursive 'src/*.java'
```

On PowerShell, use `astyle.exe` with the same arguments. The formatter does not arrange Java
imports or split annotations from declarations; check those with RuneLite's IntelliJ scheme.
Do not put source-format changes into the same commit as a behavior change.

Non-obvious files open with a comment explaining their purpose and constraints. Inline comments
explain decisions: why a reading is uncertain, why evidence cannot survive a login or profile
boundary, or why an old callback cannot change a replacement connection. Keep measurements and
bug history in a commit or the matching document; keep the comment about the current rule.

Readers run on RuneLite's client thread. Network callbacks run on OkHttp threads and may finish
after cancellation. A connection generation identifies a lifetime even when the player rejoins
the same code. Do not hold its reset lock while reading a response body. Unit tests should drive
the real reader or callback at the boundary that could go wrong, without needing a game server.

Run `./gradlew clean build -PruneLiteVersion=1.13.4`; PowerShell needs the property argument
quoted, and Windows uses `gradlew.bat`. The version property pins local checks; the Hub controls
its own compiler and client dependency. The [submission guide](docs/plugin-hub-submission.md) describes
that separate build and the release order. The map repository's contract tests and `npm run story`
exercise the real plugin against a local map. They complement the [logged-in checks](docs/live-checks.md).
The build compiles with a Java 11 toolchain that Gradle downloads. Do not compile with a newer JDK
yourself: Lombok fails on it with an error about javac internals rather than the version.

## Rules a change must keep

**What the Plugin Hub allows.** No reflection, `Thread.sleep`, `Thread.interrupt`, threads of the
plugin's own, `KeyboardFocusManager`, or new `OkHttpClient` or `Gson` instances. HTTP clients are
derived from the injected one through `CompanionHttp.pinnedToServer`. Nothing automates input, and the
plugin takes no game action. It uses none of RuneLite's restricted APIs either — `Client.hopToWorld`
is one — because a single use puts every future update under manual review. See RuneLite's
[rejected and restricted features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

**Threads.** Game state is read and changed on the client thread, Swing on the EDT. `shutDown`
and `ConfigChanged` run on the EDT while a game tick may still be running, so anything a tick owns
is reset through `clientThread.invoke`. A field written on one thread and read on another needs a
lock, `volatile` or a hand-off.

**The network.** Nothing is sent before Connect, and each kind of character data only with its own
sharing choice. Redirects are refused, a map address must be https unless it is on this machine,
and every reply is read with a size limit. Anything the map sends, a name, a title or a link, was
typed by someone in the group: escape it, strip tags, cap its length, and check that a link is on
the approved map. Never raise a request rate; failures back off.

**Answers sent with confidence.** For a moment after a login or a world hop every varbit reads
zero. A reader that cannot know yet sends nothing, never "none", "not started" or "carrying
nothing". `SessionSettle` holds that rule; use it rather than writing another.

**Saved state.** RuneLite syncs config and announces every write to every plugin, so large values
are written in batches: the kill log at most every thirty seconds. Every way a session ends must
write what is pending: logout, a character switch, `shutDown`, and `ClientShutdown`, because
closing RuneLite does not call `shutDown`.

**Tests.** A test for a fix must fail when the fix is reverted, so revert it once and check. Do
not delete, skip or loosen a test to make it pass. Rewrite one only when the behaviour changed on
purpose, and say why in the test.

**The map's contract.** The map's tests read this source directly: capability flags from the
`new Capability("…")` entries in `CapabilityEvaluator`, the snapshot shape from
`CompanionSnapshot`, and `unique-rates.json`, which must stay identical to the map's copy. A flag
the map knows must stay in that list.

**Commits.** The message is an imperative sentence about what changed for the player, with no
`feat:` or `fix:` prefix. The body says what was wrong, what it does now and the decision worth
reading the diff for. One concern per commit, and formatting never mixed with behaviour. The
history is public, so commit under your GitHub noreply address.

**Pull requests.** One change a reviewer can read in one sitting, and nothing beside it. Every
commit builds and passes on its own. Fixups are folded into the commit they fix before the first
push, because GitHub keeps every commit a pull request ever showed. A pushed branch is not
rewritten without the owner's say. Nothing local goes in: no scratch scripts, research dumps,
machine paths, pairing codes or keys.

**The README is the Plugin Hub page.** RuneLite publishes it at the pinned commit, so it is the
first thing a player reads about the plugin. Keep it short and plain: what the plugin does in a
sentence, pictures before paragraphs, and the details in `docs/`. A change that alters what a
player sees updates its picture in the same pull request, and pictures use made-up characters.
Before every Hub release, read it top to bottom as a player who has never heard of the map.

## Local development

`./gradlew runClient` starts RuneLite with this plugin loaded and assertions enabled. Log in and
perform game actions yourself. IntelliJ also has checked-in Run client, Run client (Gradle) and
All tests configurations under `.run`. All tests is headless; the client configurations are not.

To test the map connection, run the map project (osrs-map.com's source, which is not public) with
its fixture star feed and an isolated database, then set **Advanced → Map address** to its
localhost URL. The first Connect still requires server approval. Never use an upstream write endpoint or increase upstream feed polling for a test.

## Integration harness

`./gradlew runHarness --args="--map http://localhost:3000"` runs the real pairing, snapshot, relay,
stream, outbox and reader classes against `HarnessGame`, a bounded fake RuneLite client.
It accepts one JSON command per line on stdin and emits one JSON event per line on stdout:

```json
{"cmd":"login","name":"Example","world":444,"x":3222,"y":3218}
{"cmd":"join","text":"ABC234-K7PQ2M3X"}
{"cmd":"tick","n":6}
```

`./gradlew harnessJar` packages this tool separately and writes its runtime classpath beside it,
so the map's `npm run story` can start multiple installations without launching Gradle each time.
The normal plugin jar contains only `src/main`; the harness is never shipped to players.

`settledReward` commands begin after reward observation and test delivery, not actual game events.
The fake cannot establish live reward coverage, farming varbit delivery or marker appearance.
See [reward source coverage](docs/rewards-and-dryness.md) and [live checks](docs/live-checks.md).

## Farming decoder

`./gradlew writeFarmingDecode` explicitly regenerates the bundled crop table. Normal builds only
verify it against the installed RuneLite client. Keep its source attribution and BSD notice in
`src/main/resources/farming-LICENSE.txt`, and repeat the live farming procedure after changing
slot boundaries, growth decoding or replant continuity.
