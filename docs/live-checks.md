# Live checks

Status on 25 October 2026: **partial; testing stopped at the owner's request**.
The real-client observations below used plugin commit
`13e9e489735527ff467f384430c7c37b45457526`, RuneLite 1.13.4, a separate local
RuneLite settings profile, a disposable local map/group and a free-to-play test character.
The human performed every login and game action.

## Passed in the client

- Character-sharing groups, name and death sharing start off.
- Remote destinations, group markers and meetup notifications start off; no stream opens.
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
- [ ] On a members character with **Slayer task** sharing on, check the map names the task as the
      game's slayer helper does: an ordinary task, a Konar task with its area, and a boss task.
      The names come from the client's database by value; until this is seen, no build has read
      one for real.
- [ ] With a destination in the loaded scene, see its dot on the minimap as well as the tile.
- [ ] Carry two clue tiers, read the one in the later slot, and check the map shows that step.
- [ ] Kill a few monsters and log out within thirty seconds; the kill log keeps them.
- [ ] Kill a monster and close RuneLite within thirty seconds; after a restart the kill is there.
- [ ] Paste a full RuneLite code (with its suffix) into a fresh install; the first post is
      accepted and the map links the dot to the browser without a second paste.

## The members session for the where-to-go plan

One session on a members character, run with `./gradlew runClient`, which starts RuneLite with
this plugin and the developer tools. Several of these read game variables the plugin does not
read yet. The Var Inspector (developer tools) shows them changing, so the readers are written
from what the game did rather than from a guess. Record each value seen. The website plan's
steps wait on these readings (`osrs-stars-map/docs/plan-where-to-go-for-a-task.md`,
*Order of work*).

- [ ] **Slayer task name.** With *Slayer task* sharing on, the map names an ordinary task, a
      boss task if one is assigned, and a Konar task's area.
- [ ] **Port task slots.** Take a courier task at a port task board and note
      `PORT_TASK_SLOT_0_ID` (19574): is it the task's id in the map's `data/port-tasks.json`?
      Collect the crates and watch `..._CARGO_TAKEN`, then deliver and watch `..._DELIVERED`.
      An empty slot should read 0. Take a bounty task and watch `BOUNTY_TASK_ITEM_COUNTER_1`
      (14662) count down as the parts drop.
- [ ] **Where the boat is.** Note `SAILING_BOAT_1_PORT` (19260) where the boat is moored. Dock
      at another port and note it again, to learn how the value names a dock. Note
      `SAILING_BOAT_1_TELEPORT_FOCUS` (19270) for a boat with and without a teleportation focus,
      and `SAILING_BOARDED_BOAT_LAST_DOCK` (19145) after docking.
- [ ] **The Colosseum unlock.** The plugin reads varbit 11410 for the Colosseum's travel unlock.
      The client calls it `COLOSSEUM_HIGHEST_WAVE`, where the code says glory rank. Note its
      value beside what the game shows.
- [ ] **Farming.** The farming items above, and a herb patch replanted while the map's farm run
      is open: the stop should tick.
- [ ] **If convenient: a death in an instance.** Note whether a gravestone appears. The map
      offers *Route back* only where one is left.
- [ ] **If convenient: a ladder.** Count the game ticks a ladder climb takes. The map's routes
      charge 2.

## The routes session

The website plan's second session, once the routes in the game are built: a members character,
`./gradlew runClient`, and a local map whose card can *Send to RuneLite* with its steps.

- [ ] **The steps arrive.** Send a destination from a card with a route. The steps appear under
      the destination's card in the card's own words, the first one marked; the other ways fold
      behind one line, and pressing one shows its steps.
- [ ] **A teleport moves the mark on.** Take the first step's teleport. The landing is within four
      tiles of the step's tile and the next step is marked; note any landing that scatters further.
- [ ] **A floor is its own.** On a route with a staircase, the climb is marked done only upstairs,
      not standing at its foot or under its top.
- [ ] **Instances.** In a boss lair copied whole (Vorkath, Zulrah) the steps move on; inside a
      raid's rooms they do not.
- [ ] **Reroute.** Walk away from the route and press Reroute: the steps are planned from where
      you stand and the first is marked. Press it again at once: the sidebar says to wait.
- [ ] **Step pins.** The world map shows a numbered pin at each step's end, with the sidebar's
      numbers; only the current one holds the map's edge when panned away, the ones behind it fade,
      and the last step leaves the destination's own pin uncovered. All go with Dismiss.
- [ ] **The arrow follows the step.** With the current step's end in the loaded scene, the game's
      arrow points there rather than at the destination; it moves on as each step is reached, and a
      quest's arrow set first is left alone.
- [ ] **Shortest Path, installed.** With it running, Follow appears beside Reroute. A received
      destination draws nothing in it until Follow is pressed. On a teleport step it shows nothing
      new; on reaching the landing it draws the walk from your tile to the step's end, and the
      sidebar says Shortest Path is drawing it. Note whether the path still follows you as you
      walk, since this sends a `start`, and whether it ever adds a teleport of its own to a
      walking step (the owner's reason for not sending `config` yet).
- [ ] **Shortest Path keeps its own target.** Set a target of your own in Shortest Path while
      following: the sidebar stops following and says why, and finishing the walk does not clear
      your target. Press Stop while it draws one of ours: that path is cleared.
- [ ] **Shortest Path, not installed.** Without it, no Follow button and nothing about it in the
      sidebar; the steps and pins still work.
- [ ] **Three buttons fit.** Follow, Reroute and Dismiss sit on one row in the 225px sidebar
      without cutting a word.
- [ ] **Search.** Connected, type *bank* in the box at the top of Overview: the rows match the
      website's dropdown for the same word, in its order, and nothing is asked until the typing
      stops. Pick one: the destination's card fills with its steps, Shortest Path draws the first
      walk, and the list folds. Type *abysal whip* and see *Did you mean*. Clear the box while a
      search is out: nothing comes back. Check RuneLite's log holds nothing that was typed.
- [ ] **Route on a clue.** With *Share clue* off, read a coordinate clue and a beginner map: the
      clue card appears under *Now* with Route, and Route fills the destination's card. A step that
      is words shows no Route. Nothing about the clue reaches the map's member page.
- [ ] **Route back.** Die somewhere ordinary with *Share death* off, respawn and press Route back.
      Then, if convenient, die at a boss fought in an instance (Vorkath, Zulrah): note where the
      route goes. The map's half decides whether it is the boss's way in.
- [ ] **Route on a star and a meetup.** In Stars, a precise site's Route sits beside Pin
      destination without cutting a word; an approximate one shows Open on map. A meetup's Route
      prints its world in the destination's line and nobody in the group is told.
- [ ] **Friends.** With two characters in one group and the group layer off, the other appears
      under Group with their disc, presence dot, *near* and news. Show on map draws only them; Route
      asks for their position, then fills the destination's card once and does not follow them.
      Pressed again it reads Reroute. A friend in a raid has no Route.
- [ ] **Meetups.** Answer a meetup from the sidebar and see the same answer on that member's browser
      on the website; On my way is one of the four. Meet here on W302 makes a
      meetup at your tile whose copied link opens on the map; inside an instance it refuses.
- [ ] **The reminder.** With *Notify before a meetup* on and the minutes set to 2, a meetup two
      minutes off raises one RuneLite notification and no second one; with it off, none.

## Route review follow-up

- [ ] From Lumbridge Castle's bank, route to the ground floor, then to another tile on the
      bank's own floor. The first lists both climbs down; the second stays upstairs.
- [ ] Reroute during a walking step. Shortest Path must draw the new waypoint even when it is
      still the first step. Disable the companion immediately after selecting a different
      Shortest Path target: that newer path must remain. With the companion's path still selected,
      disabling it clears that path after checking who owns it.
- [ ] Leave two group members standing still for two minutes with group dots enabled, then Route
      to the other member. It must start immediately from the held position. Disconnect the stream
      and confirm its old positions are not used on a replacement connection before a new frame.

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
