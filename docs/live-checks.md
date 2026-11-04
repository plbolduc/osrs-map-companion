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
- [ ] Open Stars and pick a world, then hop with the world switcher and cross a loading line: the
      list and the pick stay through both, and no line says to log in. Log out: the list clears.
- [ ] With friends whose news carries a picture, keep Group open past a minute: no card stretches or
      loses a line, and Copy invite and Switch stay at the foot. On Stars, with More filters closed,
      *Data from Star Miners* shows under the list.
- [ ] A friend's news of a finished quest leads with the world map's quest-start star, and of a
      diary with the diary tab's icon, no larger than a skill icon; its words start in the same
      column as a level's or a drop's.
- [ ] On a meetup's world, Group draws its *W302* green, on its card or a later meetup's line. Hop
      away: it is grey again within a second of landing, and green once you hop back. Logged out,
      no meetup's world is green.
- [ ] A friend's disc under Group carries the initials their world-map pin does, and its presence dot
      leaves both letters readable. In a meetup's roll-up, hovering a disc names that member and
      hovering an answer's word names everyone under it.
- [ ] Receive a destination, preserve an existing quest hint arrow, and test clearing it.
- [ ] Follow a route and hold Shift while right-clicking the step box: Pause, Reroute and Dismiss
      sit under the game's first entry, End run in Dismiss's place on a farm run's leg, and each
      does what the sidebar's button does. A plain left-click on the box still walks.
- [ ] Send a place from the website: one chat line names it, with its world while you are on
      another, once. A hop or a relog does not say it again, and a pin from Stars or a search pick
      says nothing.
- [ ] Use two characters in one group: see only the other member on the game world map, including
      an edge pin when panned away. Turn sharing off and log out; pins must disappear.
- [ ] Recover after an unavailable map; switch groups with a snapshot outstanding and confirm the
      old reply cannot affect the new connection.
- [ ] With the map unreachable from the start, log in: once a post has failed the status card says
      *Can't reach the map · retrying* rather than *Connecting…*, and the dot stays amber. With a
      group saved, turn the plugin off and on at the login screen: the card says *Logged out*.
- [ ] Log out with a route under Now and a friend's Route reading *Finding…*: within a second the
      search box goes, and Reroute, every Route, the meetup answers, Meet here, Start run and Copy
      invite grey, a Route's tooltip saying *Log in to plan a route.*; the friend's row says the same,
      and half a minute later still does not say they share no position. Follow, Dismiss, End run,
      World map, Switch ▾ and Open the map still work, and Stars says *Log in to see stars.* Log in
      again: all of it comes back.
- [ ] Replace a clue scroll and compare charged versus uncharged jewellery capabilities.
- [ ] With Carried travel items shared, carry a construction cape, then wear a trimmed one:
      Show what's sent lists `constructionCape` both times.
- [ ] Verify an upstairs death's Open website opens the correct tile and floor.
- [ ] Exercise farming, birdhouses, rumours and contracts on a members character.
- [ ] On a members character with **Slayer task** sharing on, check the map names the task as the
      game's slayer helper does: an ordinary task, a Konar task with its area, and a boss task.
      The names come from the client's database by value; until this is seen, no build has read
      one for real.
- [ ] With a destination in the loaded scene, see its dot on the minimap as well as the tile.
- [ ] Follow a route whose step uses a teleport item: it is outlined in the inventory, and in the
      worn-equipment tab when worn. A glory(4) is outlined for a glory(6) step; an uncharged glory,
      a noted one and a lower diary tier are not. On a step that casts a spell, with the spellbook
      open, the spell is edged in amber while it can be cast, and not while it is greyed or filtered
      out of the book. Pause, and the outlines go.
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
      boss task if one is assigned, and a Konar task's area. Hop: the sidebar's Slayer card keeps
      the task, and its places greyed, until the new world settles, and never says there is no
      task meanwhile; log out and in, and it waits for the new session's reading instead. With no
      assignment, `SLAYER_TARGET` (395) should read 0 once settled, and the card then says *No
      Slayer task* and to get one from a master; note what it reads after a task is finished. If
      the task stays with 0 left, the card folds to *Gargoyles · Complete* (or whichever task) and
      *Get your next task from a Slayer master.* until the next task is read.
- [ ] **Port task slots.** `PortTaskReader` is written from the Hub's Sailing and Port Tasks
      plugins and the game's script, not from a session; these settle it. With *Tasks and timers*
      sharing on:
  - [ ] Take a courier task and note `PORT_TASK_SLOT_0_ID` (19574): the map's Port tasks page
        should name the same task. Collect some crates, then the rest, and watch
        `..._CARGO_TAKEN`; deliver and watch `..._DELIVERED`. The page's run should drop the
        pickup once every crate is collected, and the drop-off once every crate is delivered.
  - [ ] After the last delivery: does the slot clear at once, or hold the task until something
        else happens? The page says *all delivered* and assumes nothing beyond it.
  - [ ] Take a bounty task in slot 0 and watch `BOUNTY_TASK_ITEM_COUNTER_1` (14662): at the
        moment the task is taken it should read the parts asked for, and count down as they
        drop. Repeat in another slot for the other four counters if convenient.
  - [ ] An empty slot reads 0 or the *No Task* row and shows as free; a slot not yet bought is
        *locked*. Log out and in, and hop: for fifteen seconds nothing is sent, then the same
        tasks come back, never five empty slots. Through the hop the sidebar's Port tasks card
        keeps showing the tasks; after the logout it is gone until they are read again.
  - [ ] Cancel a task, and take a new one into the slot it left: the page replaces it.
- [ ] **A port-task run in the sidebar (built from the same sourced encodings, not yet seen live).**
      With *Tasks and timers* and *Progress and travel* on, press **Start run** on the Port tasks card:
  - [ ] The stops are the website's *Port tasks* order, one per port visit, then each bounty's hunt
        and its board. The first leg starts from where you stand and boards the boat the snapshot
        named; every later leg boards at the port the stop before docked at.
  - [ ] A collect stop ticks when `..._CARGO_TAKEN` reaches the task's crates, a drop-off when
        `..._DELIVERED` does, a hunt when the bounty counter reaches 0, and the board when the slot
        lets go of the bounty. Standing on a ledger table ticks nothing.
  - [ ] After the last crate is delivered, if the slot clears at once (the check above), the drop-off
        still ticks: the plugin last saw every crate loaded. If it holds the task instead, the
        counter ticks it.
  - [ ] Abandon a task mid-run, or take another into a free slot: the card says the tasks changed and
        Reroute plans again from the slots held now, keeping done stops at their numbers.
  - [ ] Each leg's voyage line draws on the world map, the Board, Sail and Dock rows tick as a
        destination's voyage does, and *Your boat is no longer at …* appears if the boat is moved
        away from the port the leg boards at. The hand-in after a hunt starts at sea, with no Board
        row: its Sail row ticks within 15 tiles of the mooring, aboard.
  - [ ] Board a boat with a cargo hold: the page should name its crates as the Sailing plugin's
        cargo overlay does, and keep it ashore. Board a second boat with a different hold; the
        page follows it. A boat without a hold leaves the last one. On an Ultimate Ironman, the
        hold should read the source table's limit (4 to 10); `CargoHoldTier` carries that column
        but its own capacity method never reads it, so this is the reading least supported.
- [ ] **Where the boat is (`BoatReader`, built from the game's scripts, not yet seen live).** The
      encodings are sourced (the map's `docs/review-evidence/sailing-reader-sources-2026-10-30.md`):
      a stored port is a `SailingDock` `dock_id` value joined by value, 253–255 are lost, capsized
      and bottled, and the focus is 0, 1 or 2. What only a client can show:
      - with *Progress and travel* on, the map names the port each boat is moored at, after a login
        and after a hop, and never before the 15-second settle;
      - a boat moored at Port Sarim (dock id 0) is read as a boat: its owned flag, or a name part,
        is set. If neither is, the reader takes that slot for empty, and Port Sarim voyages start
        nowhere for that player;
      - a slot with no boat reads all zeros, so it is left out rather than sent;
      - while standing on a deck no boats are sent. Watch `SAILING_BOAT_n_PORT` while at sea: if
        it keeps the port the boat left until it docks, the reader could keep sending while aboard;
      - `SAILING_BOAT_1_TELEPORT_FOCUS` (19270) reads 0, 1 and 2 for no focus, a teleportation
        focus and a greater one.
- [ ] **Following a voyage (`DestinationRoute`, built and tested, not yet seen live).** Send a
      place across the water with *Progress and travel* on, press **Follow**, and sail it:
      - *Board your boat* ticks on stepping onto the deck, not at the gangplank. The deck test is
        the one `WorldTiles` already uses to place a sailing friend;
      - *Sail to …* ticks with the boat alongside the far mooring, within 15 tiles of its pin while
        still aboard. Note how far from the pin the boat actually lies when docked;
      - *Dock at …* ticks on stepping ashore at the pier. Note the order the game sends things in:
        whether the boat's stored port (`SAILING_BOAT_n_PORT`) changes on docking or only on
        disembarking, and whether the player's tile leaves the deck before or after it changes.
        The port evidence settles a landing on another floor, so its timing matters;
      - `SAILING_BOARDED_BOAT_LAST_DOCK` (19145): note it before boarding, at sea and after
        docking. The follower deliberately does not read it; if it changes exactly when the boat
        docks, it could tick *Sail to …* more reliably than distance;
      - the voyage's line on the world map follows the sail, Shortest Path draws nothing during
        it, and the hint arrow points at the far mooring once it is in the loaded scene;
      - move the boat elsewhere after the route arrives: the sidebar says the boat is no longer at
        the boarding port, and **Reroute** boards it where it is.
- [ ] **The Colosseum unlock.** The plugin reads varbit 11410 for the Colosseum's travel unlock.
      The client calls it `COLOSSEUM_HIGHEST_WAVE`, where the code says glory rank. Note its
      value beside what the game shows.
- [ ] **Farming.** The farming items above, and a herb patch replanted while the map's farm run
      is open: the stop should tick.
- [ ] **A quest and a diary tier announced.** With *Loot and milestones* and
      *Progress and travel* on, log in: nothing already done is announced. Finish a short quest:
      the group's feed shows it within about thirty seconds, once. Finish a diary tier's last task:
      the feed shows that tier, once, and nothing when its reward is claimed. Hop worlds and log
      out and in: nothing is announced. Note whether the tier's completion varbit moves on the
      last task or on claiming, and, if a Karamja easy, medium or hard tier is to hand, what its
      flag reads between the last task and the reward: only a change from 0 to 2 is announced.
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
- [ ] **Step pictures.** A route whose steps use an item (a games necklace, a tablet) and a spell
      (Lumbridge Home Teleport) shows each one's picture before its number, at 16px, as the
      card's sprite for that row: the item's sprite from the item cache and the spell's icon from
      the sprite cache. A walk's row has none. The renderer used the website's copies of the art.
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
      Panned away from a followed route, the edge shows one amber disc, the current step's; on the
      last step it is the destination's.
- [ ] **The arrow follows the step.** With the current step's end in the loaded scene, the game's
      arrow points there rather than at the destination; it moves on as each step is reached, and a
      quest's arrow set first is left alone.
- [ ] **The step on screen.** Follow a route with the sidebar closed: a small box in the top left
      names the destination and the step the sidebar marks, moves on with it, and goes on arrival.
      It can be dragged like any RuneLite box, and it is not drawn inside an instance. Search for
      *Civitas illa Fortis east bank deposit box* and follow it: the whole name wraps inside the box.
      Route to a star on another world: the box and the tile's label both end in its world, *(W302)*;
      hop to that world and both drop it.
      Follow a farm run: the box is headed by the stop's number, *2. Catherby*, as its pin on the
      world map is, and the step under it has no number of its own, *Use Catherby tablet*.
      A teleport step shows its picture before its words, bare, as the sidebar's row does: the
      Catherby tablet, Explorer's ring 2, the Lumbridge Home Teleport icon. The words stand a
      picture's width in from the first frame, and the picture fills that place without the words
      moving; a walk has no picture, and its box is narrower by one.
- [ ] **Route keys.** Set *Reroute key* to Ctrl+R and *Follow and pause key* to Ctrl+F. On a farm run,
      Ctrl+F pauses and follows it and Ctrl+R reroutes it; twice within five seconds asks once. With
      no run, they act on the destination's own buttons, and do nothing with neither shown. Mid-run,
      press Route on a search result: while that route is followed, both keys act on its buttons and
      leave the paused run as it is.
- [ ] **Shortest Path, installed.** With it running, Follow appears beside Reroute. A received
      destination draws nothing in it until Follow is pressed. On a teleport step it shows nothing
      new; on reaching the landing it draws the walk from your tile to the step's end, and the
      sidebar says Shortest Path is drawing it. Note whether the path still follows you as you
      walk, since this sends a `start`, and whether it ever adds a teleport of its own to a
      walking step (the owner's reason for not sending `config` yet).
- [ ] **Shortest Path keeps its own target.** Set a target of your own in Shortest Path while
      following: the sidebar stops following and says why, and finishing the walk does not clear
      your target. Press Pause while it draws one of ours: that path is cleared.
- [ ] **Shortest Path, not installed.** Without it, Follow still shows the step you're on in the
      box over the game, and nothing about Shortest Path is in the sidebar; the steps and pins still
      work. With *Step on screen* off as well, there is no Follow.
- [ ] **Three buttons fit.** Follow, Reroute and Dismiss sit on one row in the 225px sidebar
      without cutting a word.
- [ ] **Search.** Connected, type *bank* in the box at the top of Home: the rows match the
      website's dropdown for the same word, in its order, and nothing is asked until the typing
      stops. Pick one: the destination's card fills with its steps, Shortest Path draws the first
      walk, and the list folds. Type *abysal whip* and see *Did you mean*. Clear the box while a
      search is out: nothing comes back. Check RuneLite's log holds nothing that was typed.
- [ ] **Route on a clue.** With *Tasks and timers* off, read a coordinate clue and a beginner map: the
      clue card appears under *Now* with Route, and Route fills the destination's card. A step that
      is words, an anagram or an emote, shows its words and Route: the card is named after the place
      the map reads from them, and an emote's way goes by its STASH unit first. Carry a medium or
      hard map: before and after opening it, the card says *Map · Route finds where it shows* with
      Route, and Route names the place the picture shows. A step the map cannot place says why under
      Route, in the map's own sentence. Nothing about the clue reaches the map's member page.
- [ ] **Route back.** Die somewhere ordinary with *Share death* off, respawn and press Route back.
      Then, if convenient, die at a boss fought in an instance (Vorkath, Zulrah): note where the
      route goes. The map's half decides whether it is the boss's way in.
- [ ] **The gravestone's time left.** Die somewhere ordinary. Once you respawn, the death card says
      *Gravestone · 15m left* under *You died …*, over a full orange bar. For a few minutes, compare
      it with the game's own gravestone timer: the card's minutes are the game's, rounded up, which
      is the unit the game's HUD script gives the varbit (game ticks of 0.6s). If no line appears
      while the game's timer runs, `GRAVESTONE_VISIBLE` does not mean that a stone stands, and the
      card must stop asking it. Log out for two minutes and back in: note whether the game's timer
      paused, and check the line comes back about fifteen seconds after login, agreeing with it. If
      convenient, have the stone blessed and note what the game's timer shows; past fifteen minutes
      the card shows no time, as the game prints none. Pick the stone up: the line and the bar go,
      and *You died …* stays.
- [ ] **Route here.** Right-click empty ground on the world map: *Route here* sits below Cancel,
      and a left-click still drags the map and plans nothing. Choose it on a spot a few tiles from a
      landmark: the route under Now ends on that tile, not the one beside it. Logged out of the
      group, the entry is not offered; pressed twice within five seconds, the chat box says why once,
      with the seconds the wait has left.
      Where the map's answer names the landmark near the spot, the card, the box over the game and
      the pin's tooltip say it, *Near Lumbridge Castle*; where it names none, *Picked on the world map*.
      Between the press and the answer the spot has a faded amber pin, *Planning the way here…*,
      which stays put when the map is panned away and goes with the answer or the chat box's refusal.
- [ ] **Route on a star and a meetup.** In Stars, a precise site's Route sits beside Pin
      destination without cutting a word; an approximate one shows Open website. Press Route: the
      steps show under the star, Stars stays the tab, and Follow and Reroute there act as Home's do
      while you walk and hop; Unpin clears the star and Home's card together. A meetup's Route
      prints its world and its day and clock in the destination's line, with no countdown, and
      nobody in the group is told. Pressed on Group, a meetup's or a friend's Route turns the
      sidebar to Home once its way lands, with the card in view; a refusal stays under the button.
- [ ] **Friends.** With two characters in one group and the group layer off, the other appears
      under Group with their disc, presence dot, *near* and news. World map draws only them; Route
      asks for their position, then fills the destination's card once and does not follow them.
      Pressed again it reads Reroute. Route to a friend in a raid: nothing is planned, and their row
      says they are inside an instance and that Route works once they are out.
- [ ] **Meetups.** Answer a meetup from the sidebar and see the same answer on that member's browser
      on the website; On my way is one of the four. Meet here on W302 makes a
      meetup at your tile whose copied link opens on the map; inside an instance it refuses and says
      to step outside first.
- [ ] **The one press at Connect.** On a fresh RuneLite profile, the first Connect to osrs-map.com
      asks nothing and shows the step, with every group, the name and both drawing choices ticked
      and *Where you died* not; it fits 225px with no cut word. Untick a group, then Not
      now: the Settings page still shows everything off. Change the map address to a local map and
      Connect: one question names that server, and Cancel sends nothing. Connect and press Share
      what's ticked: Settings shows every switch on but *Where you died*, the receive stream opens,
      and *Meetup reminder* stays off. Restart RuneLite and reconnect: no step. In RuneLite's
      own config panel, ticking a sharing setting asks no "Are you sure?".
- [ ] **Sharing as six switches after an update.** On a RuneLite profile that ran 1.3.0 with all of
      Map presence and two fields of Tasks and timers ticked, start this version. RuneLite's list
      shows *What you share* with Map presence on and Tasks and timers off, the sidebar's Settings
      says *Partly shared* under Tasks and timers with the same two fields, Show what's sent carries
      the same fields as before, and the profile's settings file holds `mapcompanion.sharingGroups=1`.
      Tick Tasks and timers in RuneLite's list: Show what's sent then carries all eight of its fields.
      Turn the plugin off, untick Map presence in RuneLite's list and turn the plugin on: no position
      is sent. Switch to another RuneLite profile and back: each keeps its own switches. RuneLite
      redraws its list on a profile change rather than on every write, so note whether a switch moved
      by the sidebar shows there before the list is reopened.
- [ ] **Connect, Join and Open the map without the website.** With no group, Connect makes *Our
      group*; the sidebar's Group tab says *Just you so far* at once, with Copy invite in that card.
      Copy invite, paste the whole message into a second RuneLite's *Have an invite?* field and press
      Join: both see each other, and the maker's Copy invite is back beside Switch ▾. Paste it into a
      third and press the orange Connect rather than Join: it joins the same group, and no second
      *Our group* is made. The maker's Switch ▾ shows *Rename group…* and the friend's does not, and
      so does the maker's status card, under *Our group*; rename the group, and both status cards
      show the new name and the maker's link is gone. Open the map from each: the browser opens in
      the group as that player, the maker's as owner, with no `#link` left in the address. Log out
      and press Open the map on the login screen: the same. Press Connect on the login screen of a
      fresh profile and log in a few days later: the group is still there.
- [ ] **Copy invite.** In a group Connect made, Copy invite reads *Copied* for two seconds and the
      line under Copy invite · Switch ▾ says the invite expires in 7 days, with the Group tab scrolled
      to its foot and the tabs where they were; the line goes ten seconds later. With the map stopped
      it says nothing was copied, and that stays, and pasting gives what the clipboard held before.
- [ ] **Answered where pressed.** Switch ▾ → Join another group… opens the field under Copy invite ·
      Switch ▾, and an expired invite is refused under its Paste and Join; Hide the code field folds
      it. In Settings → Advanced, Apply with `osrs-map.com` writes `https://osrs-map.com` into the
      field, and with `http://example.com` says why under Apply, with Settings still open.
- [ ] **Remove from group.** In a group Connect made, the maker right-clicks a friend's card, on the
      name, on where they are and on Route: each offers *Remove from group…*, and the friend's own
      right-click offers nothing. The dialog names the friend and the group, and Cancel is the
      default. Remove: the line under Copy invite · Switch ▾ says *Removed Cara.*, Cara's card goes
      from the maker's Group tab, and Cara's status card says *Removed from this group*. Cara's old
      invite is refused; a new one lets her back. Right-click the maker's own second character, if
      it is listed: the map's refusal says to use the website.
- [ ] **The group's dialogs.** Rename group… opens with the cursor in its field, stops at the 24th
      character and renames on Enter. Leave group's window is titled with the group's name, its
      buttons are Leave group and Cancel, and Enter cancels; leave a group made by Connect while a
      second Connect group and a legacy code are remembered, and Your groups ▾ lists only the legacy
      code. Pointed at a local map, Connect's question wraps with no word alone on a line.
- [ ] **A refused group.** Remove a member on the website. Their status card says *Removed from this
      group* in pink beside a red dot, *Have an invite?* and the field show under the orange button
      with the way out under Paste and Join, and all of it stays through the next five minutes and
      the retry after them rather than flashing the group's name. Join with an invite to another
      group: the card is that group's at once. Paste a bare code of a group Connect made into a
      fresh RuneLite: *Needs an invite*, and an invite to the same group pasted and joined puts it
      back without waiting for the retry.
- [ ] **The reminder.** Leave RuneLite's own *Send notifications when focused* off, as it ships. Tick
      *Meetup reminder* in the sidebar and set *Minutes before a meetup* to 2. With RuneLite
      focused and the game being played, a meetup two minutes off raises one notification, with the
      tray and sound RuneLite's settings give, and no second one; with the box unticked, none. In
      RuneLite's configuration, the cog beside *Meetup reminder* shows *Customize notification*
      and *Send notifications when focused* ticked. Untick the latter there: the next reminder waits
      while RuneLite is focused, and unticking and ticking the sidebar's box keeps that choice.

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
4. With no active sidebar run, disable sharing, replant and re-enable it; repeat across a hop or logout. Unobserved actions
   remain unconfirmed; reconnect cannot replay a completion.
5. Switch RuneScape characters. History belongs to its character; returning cannot invent a receipt.
6. Repeat arrival, harvest-only and replant with hops, a bush, hardwood, a spirit tree, cactus
   and each special Guild crop. Check Fossil Island's excluded ladder/stair tiles and Port
   Sarim's 3272 boundary. Grapes, calquat, belladonna, mushrooms, Prifddinas, Hespori and coral
   now have source-checked readers too; coral must be seen on the nursery's own floor.

The bundled decoder is compared against RuneLite by automated tests. Growth windows assume healthy
ordinary cycles and are measured relative to the observation; disease, boosts and later actions
can change them. Two settling ticks and the login delay suppress stale patch slots. Only a
continuously witnessed empty-to-newly-planted transition mints a stable completion id.

### Farm-run follower (source and harness checked; a members-client visit remains pending)

- [ ] Send a two-stop herb run from your own browser. A fresh compatible client offers Send run;
      an older receiver does not. The sidebar and world map use the same stop numbers.
- [ ] Arrive, harvest without planting, then replant. Only the replant finishes that stop and
      moves the arrow/current leg. A mixed allotment stop waits for every selected patch.
- [ ] Start Ready now, each preset, Birdhouses and a contract from the sidebar. With position
      sharing off, Start still plans from the tile sent on that press; no live position is shared.
- [ ] Open Before you leave. Known carried travel items are checked locally; unknown supplies
      make no inventory claim. Closed, the fold counts the teleports not on you, and the count
      follows a teleport taken from the bank. Complete a seeded birdhouse and check its weaker
      sighting label.
- [ ] Open the bank during a herb run that names an Amulet of glory you are not carrying: charged
      glories are outlined and an emptied one is not; withdraw one and the outlines go within a
      tick. Seeds and a tablet no travel flag names are never outlined, and nothing moves or hides.
- [ ] Start a herb run: the kind of run and Start run leave the card while it is planned and walked,
      and come back after End run or once the run is gone.
- [ ] Reach a personal stop. It says Visited, never Done. Reroute keeps completed and visited
      stops at their original numbers and orders only the rest.
- [ ] Pause, then replant. The list can record it without reclaiming a Shortest Path route
      you chose yourself. Follow resumes at the next unfinished stop. An outside target also
      pauses the whole run, including when the next replant arrives.
- [ ] End while a plan is pending, and while Reroute is pending. No late answer revives it.
      Replace a run from the browser while End is in flight: the newer run must remain.
- [ ] With a run active, turn farming sharing off and replant. Local progress still works;
      Show what's sent has no farming block. Reroute sends only its done/visited ordinals.
- [ ] Switch characters, groups and server addresses with a stream and a Start in flight.
      The replacement character never sees the old run. End and shutdown remove its numbered pins.
- [ ] Leave the client connected through a local map-server restart. Its lost run disappears;
      Start makes another immediately after the ordinary press wait, without reconnecting the
      plugin. A delayed old-process heartbeat cannot restore the old run.

Automated reader, protocol and concurrency tests complement these checks. A fake client cannot
establish the events a real game session emits or verify every marker visually.
