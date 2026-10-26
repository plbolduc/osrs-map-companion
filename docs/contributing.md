<!-- Copyright (c) 2026, pbolduc. All rights reserved. SPDX-License-Identifier: BSD-2-Clause -->
# Changing the companion

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
its own compiler and client dependency. The [submission guide](plugin-hub-submission.md) describes
that separate build and the release order. The map repository's contract tests and `npm run story`
exercise the real plugin against a local map. They complement the [logged-in checks](live-checks.md).

## Local development

`./gradlew runClient` starts RuneLite with this plugin loaded and assertions enabled. Log in and
perform game actions yourself. IntelliJ also has checked-in Run client, Run client (Gradle) and
All tests configurations under `.run`. All tests is headless; the client configurations are not.

To test the map connection, run the map project (osrs-map.com's source, which is not public) with
its fixture star feed and an isolated database, then set **Advanced → Map address** to its localhost URL. The first Connect still requires
server approval. Never use an upstream write endpoint or increase upstream feed polling for a test.

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
See [reward source coverage](rewards-and-dryness.md) and [live checks](live-checks.md).

## Farming decoder

`./gradlew writeFarmingDecode` explicitly regenerates the bundled crop table. Normal builds only
verify it against the installed RuneLite client. Keep its source attribution and BSD notice in
`src/main/resources/farming-LICENSE.txt`, and repeat the live farming procedure after changing
slot boundaries, growth decoding or replant continuity.
