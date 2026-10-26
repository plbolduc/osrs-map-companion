# Live checks

Status on 25 October 2026: **partial; testing stopped at the owner's request**.
The real-client observations below used plugin commit
`13e9e489735527ff467f384430c7c37b45457526`, RuneLite 1.13.4, a separate local
RuneLite settings profile, a disposable local map/group and a free-to-play test character.
The human performed every login and game action.

## Passed in the client

- Character-sharing groups, name and death sharing start off.
- Remote destinations, group markers, meetup notifications and the hop button start off; no stream opens.
- The map's Copy connection code and the plugin's Paste transfer the full code.
- First Connect discloses the server, IP transmission, member identity, alias, code and selected sharing.
- Cancelling Connect sends no snapshots, invites or stream requests.
- Connecting with character sharing off joins using only the alias and connection metadata.
- Enabling only Position and world shows the correct world/tile on the map; other progress stays absent.
- RuneLite's native third-party warning appears; rejecting it leaves Position off.
- No snapshot posts were made during an observed logged-out interval of more than two minutes.

The local test client and map server were stopped. These checks have not been repeated since:
both source reviews below changed code they touch.

## Source review and automated checks

The 25 October review fixed queued group pins surviving a clear, pending snapshot callbacks after
shutdown, and missing farming/diary session resets. It also replaced the forbidden global focus
API and thread sleep/interrupt calls with component-local focus checks and scheduled retries.
The source, network disclosure, credential boundaries, game-action entry points and bundled
resource/build layout were reviewed against RuneLite's current submission rules.

Validation uses RuneLite 1.13.4 and Java 11:

- Clean-checkout build after the first review: 416 tests, no failures or skips.
- Hub standard-template compilation/package rehearsal: passed; the full Hub packager/API review
  has not run.
- Required map/plugin contract checks: 36 passed.
- Gitleaks history scan: four deliberate test-fixture matches, no real credential identified.
- The map's browser story (`npm run story` in the map repository), which pairs harness clients
  with a local map in headless Chrome: 88 checks passed after the second review.

A second review the same day stopped redirects carrying credentials to another host, required
https, capped reply sizes, refused hops to dangerous worlds, moved shutdown and setting resets onto
the client thread, filtered the per-script hook, batched kill-log writes, and corrected clue
pairing, travel quests at login, diary completion order, stream back-off, Stars resends and the
minimap dot.

A third pass reviewed those changes and the rest of the source again. It fixed two regressions
the second had introduced, a new member's suffix lost on the first join and kills not saved when
RuneLite closes, and then: a beginner picture beside another clue tier, redirects reported as the
address to use, a hop to a world the button did not show, server text in the sidebar, the game
scene and the clipboard, a stream that died silently, two threads rewriting pairing state, spent
jewellery offered as teleports, and the travel quests and account badge read too early or too
often. A clean run is now 443 tests with no failures or skips.

## Still to verify

- [ ] Log back in: the same character keeps its member identity. Withdraw Position while logged in
      and confirm the next accepted snapshot clears the map pin.
- [ ] Walk a short distance manually and verify live movement and reconnection.
- [ ] Receive a destination, preserve an existing quest hint arrow, and test clearing it.
- [ ] Use two characters in one group: see only the other member on the game world map, including
      an edge pin when panned away. Turn sharing off and log out; pins must disappear.
- [ ] Recover after an unavailable map; switch groups with a snapshot outstanding and confirm the
      old reply cannot affect the new connection.
- [ ] Replace a clue scroll and compare charged versus uncharged jewellery capabilities.
- [ ] Verify an upstairs death's Show on map opens the correct tile and floor.
- [ ] Exercise farming, birdhouses, rumours and contracts on a members character.
- [ ] With a destination in the loaded scene, see its dot on the minimap as well as the tile.
- [ ] Carry two clue tiers, read the one in the later slot, and check the map shows that step.
- [ ] Press the hop button for a PvP or high-risk world; it must refuse and say why.
- [ ] Kill a few monsters and log out within thirty seconds; the kill log keeps them.
- [ ] Kill a monster and close RuneLite within thirty seconds; after a restart the kill is there.
- [ ] Paste a full RuneLite code (with its suffix) into a fresh install; the first post is
      accepted and the map links the dot to the browser without a second paste.

## Farming procedure

With Farming patches enabled and the map's farm run open:

1. Arrive at a mature selected patch, harvest and leave it empty. Neither action completes the stop.
2. Replant; the next ordinary snapshot should complete it once. Repeat with tree and fruit-tree
   patches. Natural growth and checking health must not create receipts.
3. Cross Catherby's 2840/3440 boundary and visit an upstairs room. Region/floor changes cannot
   create completion.
4. Disable sharing, replant and re-enable it; repeat across a hop or logout. Unobserved actions
   remain unconfirmed; reconnect cannot replay a completion.
5. Switch RuneScape characters. History belongs to its character; returning cannot invent a receipt.

The bundled decoder is compared against RuneLite by automated tests. Growth windows assume healthy
ordinary cycles and are measured relative to the observation; disease, boosts and later actions
can change them. Two settling ticks and the login delay suppress stale patch slots. Only a
continuously witnessed empty-to-newly-planted transition mints a stable completion id.

Automated reader, protocol and concurrency tests complement these checks. A fake client cannot
establish the events a real game session emits or verify every marker visually.
