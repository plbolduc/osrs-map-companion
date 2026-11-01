# OSRS Map Companion

Bring [osrs-map.com](https://osrs-map.com)'s routes, search and shooting stars into RuneLite,
and see your character and friends on the website.

[Getting started](#getting-started) · [Search](#search-for-a-place-or-item) ·
[Farm runs](#farm-runs-in-runelite) · [Slayer](#slayer-in-runelite) · [Sailing](#sailing-on-the-website) ·
[What's new](https://osrs-map.com/whats-new)

## Routes in RuneLite

<p>
  <img src="docs/mockups/sidebar-route.png" width="300" alt="A route to Lumbridge Castle bank in the real plugin sidebar: numbered steps, the home teleport's spell icon on its step, the current walk highlighted, and Follow, Reroute and Dismiss">
</p>

Send a place from the website or pick a search result, and the sidebar lists the way there.
A teleport step shows what to click: the item's sprite or the spell's icon, from the game.
Each step gets a numbered pin on the game's world map; the game's arrow points at the current
step when it is in the loaded area.

With **Shortest Path** installed, press **Follow** to draw each walking step as you reach it.
While you follow a route, the step you're on also shows in a small box over the game, so the
sidebar can stay closed. **Reroute** plans again from where you stand, and the settings can give
Reroute and Follow a key of your own. The website uses the progress and travel choices you share
to suggest routes around your account's quests, diaries, items and transport unlocks.

You can also plan a route to a friend, a meetup or a precise star site, back to where you died,
and to coordinate clues and recognised beginner map clues. Or right-click any spot on the game's
world map and choose **Route here**.

<p>
  <img src="docs/mockups/sidebar-voyage.png" width="300" alt="A voyage in the plugin sidebar: the walk to Port Sarim done, Board your boat at Port Sarim, Sail to Catherby current with its Sailing level marked unmet, then Dock at Catherby">
</p>

A voyage in your own boat is its own steps: board your boat, sail, and dock, where the boat then
stays. They tick when you step on deck, reach the far mooring and step ashore, the sail is drawn
as a line on the world map, and the docking mooring's Sailing level and quests show under it.
Shortest Path draws only the walks. With **Boats and moorings** shared, voyages start where your
boats are.

## Search for a place or item

<p>
  <img src="docs/mockups/sidebar-search.png" width="300" alt="Searching for whip: Abyssal demon and Abyssal Sire under Dropped by, and the Abyssal whip under Items">
</p>

Search for banks, shops, NPCs, monsters and items from **Home**. An item can show the monsters
that drop it and the shops that sell it. Pick a place, monster or shop to plan the way there.
Results use the game's map icons and item sprites.

## Farm runs in RuneLite

<p>
  <img src="docs/mockups/sidebar-farm-run.png" width="300" alt="A farm run in the real sidebar: a bank checklist, numbered stops with Done and Visited kept separate, the current leg's steps under This leg, and Pause, Reroute and End run buttons">
</p>

Build a run on the website and press **Send run to RuneLite**, or choose a preset, **Ready now**,
**Birdhouses** or your **Farming contract** under **Farm run** on Home and press **Start run**.
The sidebar lists your stops, how to reach each one, and **Before you leave**, a bank checklist.
In the bank, the checklist's teleports you are not carrying are outlined. Numbered pins mark the
stops on the game's world map.

A farm stop becomes **Done** after the plugin witnesses every patch it needs being replanted.
Arriving or harvesting alone does not finish it. Birdhouses use a fresh seeded Time Tracking
sighting, labelled as such. Personal stops say **Visited** when you reach them.
**Reroute** reorders what remains from where you stand; **End run** clears the run.
**Pause** keeps the itinerary, and **Follow** resumes it. A route you choose yourself
pauses the run. With Shortest Path installed, its walking legs can be drawn as you reach them.
Runs live in the map server's memory. If that server restarts, the old run ends and **Start run**
can make a new one in the same client session.

## Friends and meetups

<p>
  <img src="docs/mockups/sidebar-group.png" width="300" alt="The Group tab: friends' locations, a Woodcutting level and an Abyssal whip drop, then a star-hunt meetup with Route and four answers">
</p>

**Group** shows your friends' locations and latest levels and drops. Friends who share
their position appear on the website and, if you enable it, on RuneLite's world map in the same
colours. **Route** plans the way to a friend's current position.

Create a meetup with **Meet here…**, see who is coming, and answer **Going**, **Maybe**, **Can't**
or **On my way**. You can turn on a reminder before it starts.

## Shooting stars

<p>
  <img src="docs/mockups/stars-tab-list.png" width="300" alt="The Stars tab: Rimmington mine selected, tier and world filters, stars grouped by site, and Pin destination and Route buttons">
</p>

**Stars** lists active stars by site, world, tier and estimated time left. Filter by tier and
world type, save favourite sites, and optionally sync your filters and favourites with the website.
Pick a precise site to pin it on the world map or plan a route there.

To change worlds, type `::hop` and the world, for example `::hop 444`.

## Drop luck

<p>
  <img src="docs/mockups/sidebar-drop-luck.png" width="300" alt="Drop luck for an Aberrant spectre: 1,300 recorded kills without the selected robe bottom, a Very dry verdict, and the chance over the next 100 kills">
</p>

See how lucky or dry you are on the monster you are killing, with sourced drop odds, a dry-streak
comparison and the chance over your next kills. **All monsters** opens your recorded history.
Optional chat lines report your luck when a drop lands. Drop luck also works without connecting
to the website.

## Your character on the website

The website follows your character's position, floor and world as you play. See your slayer task,
clue step, hunter rumour, Mahogany Homes job and farming contract on the map, alongside birdhouse
timers and the farm patches you have visited.
Patch tracking includes hops, bushes, hardwood and spirit trees, cactus and the Guild's
special patches, alongside herbs, allotments, flowers and trees.
Completed farming contracts point back to Guildmaster Jane to hand them in.

<a id="slayer-on-the-website"></a>

## Slayer in RuneLite

<p>
  <img src="docs/mockups/sidebar-slayer.png" width="225" alt="The RuneLite Slayer card: remaining task count, best usable place, requirements and Route or World map actions">
</p>

Share your Slayer task to see the website's ranked places in Home. The counter follows kills
in this client. Better places you cannot use yet stay visible with their requirements; expand
the list for other places, ranking choices, the Wilderness filter and the kinds that count.
Wilderness places start hidden. **Route** plans from where you stand; **World map** pins the
spot locally. Locations and rates are from the [OSRS Wiki](https://oldschool.runescape.wiki/w/Slayer_training),
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

The website offers the same ranking with a larger map:

<p>
  <img src="docs/mockups/website-slayer.jpg" width="360" alt="The website's Slayer panel for 143 Aberrant spectres: ranked places, monster variants, access requirements and controls for the list">
</p>

Share your task to compare ranked places to do it and the other monsters that count.
See access requirements, filter out Wilderness or unusable places, and send your chosen place
to RuneLite to follow the route there.

## Sailing on the website

<p>
  <img src="docs/mockups/website-sailing.jpg" width="360" alt="Port Sarim's task board on the website: courier tasks ranked by XP per sailed tile, then bounty tasks ranked for the whole trip and hunt">
</p>

Open a port task board on osrs-map.com to compare courier and bounty jobs, their levels and XP.
Couriers are ranked by XP per sailed tile; bounties account for the sail out and back and the
expected hunting. A bounty's pin opens the creature on the map.

## Port tasks in RuneLite

<p>
  <img src="docs/mockups/sidebar-port-tasks.png" width="225" alt="A port-task run in the RuneLite Port tasks card: Port Khazard done, Ardougne next with its crates to deliver, then the osprey hunt and Brimhaven's task board, and under This leg the steps from boarding the boat to the ledger table">
</p>

Share your port tasks to list the ones you hold in Home: the crates still to collect and where
they go, or the bounty parts still owed and the board to hand them in at. With **Boats and
moorings** shared too, **Start run** turns them into one run: every pickup and drop-off in the
order that fits your boat's hold, then each bounty out to its creature and back to its board. Each
stop's voyage starts where your boat docked last, draws on the world map, and ticks off from the
task's own counters. The website's **You → Port tasks** shows the same order.

The pictures use made-up characters and stars.

## Getting started

1. Install **OSRS Map Companion** from the Plugin Hub and open its sidebar.
2. Press **Connect**. The plugin makes a group for you, called *Our group*; you can use it on your
   own, and rename it with **Switch ▾ → Rename group…** under **Group**.
3. Press **Share what's ticked**, or **Customise** to choose field by field first, or **Not now** to
   share nothing yet. The gear opens **Settings** whenever you want to change your choices.
4. To play with friends, press **Copy invite** under **Group** and send it. A friend pastes it into
   their own sidebar, under **Have an invite?**, and presses **Join**.

**Open the map** opens osrs-map.com already in your group, as you, for the bigger map on another
screen. A group made on the website joins the same way: paste its invite, or the connection code
from its Companion panel.

## What you share

**This plugin sends data to osrs-map.com, a third-party server, which receives your IP address.**
Nothing is sent until you press **Connect**. While you are connected and logged in, the plugin
sends a random member id, the name you chose and your group's credentials, even with every
sharing choice off.

<details>
<summary>Full sharing details and settings</summary>

<p>
  <img src="docs/mockups/sidebar-settings.png" width="300" alt="Settings with its four sharing categories, Name and death location, In the client and Advanced; Advanced is closed">
</p>

Every choice starts off. After your first Connect, one press of **Share what's ticked** turns on
everything below except where you died, and draws destinations, meetups and your group on the
world map, which keeps one connection open to the map while you are logged in. Nothing is turned
on until you press, and you can untick any of it first. Each choice can send:

- **Name and presence:** the display name of the character you are logged in as; your account type;
  your position, your world and what kind of world it is; whether you are inside an instance and
  whether that instance is one place or rooms put together; which skill you are currently training,
  recent experience and how long since you last moved.
- **Progress and travel:** skill levels; started, finished, or neither for the 49 quests that unlock
  travel; optionally the full quest and miniquest log; which achievement diary tier you have finished
  and which of those rewards you have collected; derived travel unlocks from carried/equipped items
  as yes/no flags; verified permanent transport unlocks, your house town and which STASH units you
  have built. STASH contents are not read. With **Boats and moorings**, where each of your boats is
  moored and whether it carries a teleport focus, so the map's voyages start at your boat; boat
  names are not sent.
- **Tasks and timers:** your current slayer task and remaining count; your active clue step
  (tier, item id, observed clue text and resolved tile); your observed hunter rumour;
  your Mahogany Homes customer and tier; the crop requested by your farming contract and whether
  it is ready to hand in;
  birdhouse states and observation ages; the state of farming patches you have walked past,
  including crop, stages, observation age, estimated growth windows and witnessed replant receipts
  for the 99 patches supported by RuneLite's crop tracker (including grapes, coral and seaweed);
  which Sailing port task each of your five slots holds, with crates collected and delivered or
  bounty parts still owed, and whether a slot is empty, locked or unreadable; and how many crates
  the cargo hold of the boat you last stood on carries. Cargo is not read.
- With **Slayer task** shared: the Slayer list's order, Wilderness filter and delivery receipt.
  These request your own sidebar list through the existing connection. They add no poll.
- **Loot:** observed loot kills for every monster, how long ago each was last killed, notable drop
  counts and observation baselines, kills since a drop, slayer-task/diary rate conditions, the
  monster currently being killed and your pinned drop. Completeness flags identify omitted history.
- **Announcements and death:** an announcement of a notable reward, a real level gained, or a quest or
  achievement diary tier finished while the plugin watched, with observed item name/id, quantity,
  NPC/activity source and optional pet/collection badge, or the quest's name or the diary's region
  and tier; the tile where you most recently died and its age. A quest or diary is announced only
  when its own choice above is shared too, and never for what was done before the plugin was watching.
  Unknown reward details remain absent. Large snapshots include a flag saying some of it was left out.

**Connect**, with no group yet, sends a random key for this RuneLite, once per press; the map makes
your group with it. **Join** with a pasted invite sends that key and the invite. Neither sends
anything about your character. If you have changed **Settings → Advanced → Map address** to a server
other than osrs-map.com, the first Connect to it asks you first, naming it.
**Open the map** sends your group's credentials once per press, and opens a one-time link that
adds that browser to your group as you. **Rename group…** sends the new name, once per press.

**Reroute**, under the steps of a destination the map sent, sends the tile you are standing on,
once per press and whatever you share, so the map can plan the way from there. The map plans with
it and does not keep it.

**Search**, at the top of Home once you are connected, sends what you type to the map, which
answers with places and keeps nothing. Picking a result sends the tile you are standing on, as
Reroute does, so the way is planned from there.

**Route**, beside your clue, your last death, a star or a meetup, sends the tile you are standing on
and the tile it goes to, once per press. That includes your clue's tile and where you died when you
do not share them: they leave in that one request and the map plans with them and keeps neither.
Your clue is read in this client whether or not you share it, so the sidebar can offer Route.

**Start run** sends your chosen run kind and the tile you stand on, once per press, even with
position sharing off. A run's **Reroute** also sends its id and which numbered stops are
done or visited. **End run** asks the map to remove your run. The map keeps the itinerary in
memory for up to three hours. While a run is active, patch replants and carried travel items
are read locally even with farming or item sharing off; only your enabled sharing choices
put readings in snapshots. The bank checklist's local item check sends nothing.
Enabling **Show destinations from the map** also tells the website this build can receive a
farm run, so it offers **Send run** only to a compatible live client of your own.

**Meet here** sends the title and time you choose, the tile you are standing on and your world,
once per press, to make a meetup your group can see. Answering a meetup (**Going**, **Maybe**,
**Can't** or **On my way**) sends that answer, once per press. Nothing answers for
you: Route to a meetup tells nobody you are coming.

Your chat, other players, your bank and inventory, your existing collection log slots, a complete
loot history and anything derived from your account's identifiers are never sent. **Settings →
Advanced → Show what's sent** shows what would go out right now. The server's operator and your group's
members can see what you share.

osrs-map.com keeps live positions and progress in memory only, with no movement history. Groups,
the names last shared in them, invites, meetups and group pins are kept. A new group keeps two
weeks of its members' notable drops, levels, quests and diaries, up to 200 entries; its owner can
turn this off. See the [privacy policy](https://osrs-map.com/privacy). **Leave group** stops
sharing and deletes the plugin's saved credentials; your kill log stays on your computer.

Shooting star data comes from Star Miners by way of osrs-map.com. The plugin never contacts Star
Miners, who do not endorse it.

Nothing is shared until you choose it, and joining a group does not turn anything on. With
**Character name** ticked your group sees it; untick it and **Name to show instead** sets what they
see, or Member if you leave it empty. **Copy invite** copies a message you can send a friend.
**Switch** remembers up to eight groups, and joins another; the person who made the group can
rename it there.

In the plugin's settings, **Show destinations from the map**, **Show my group on the world map**
and **Notify before a meetup** start off; the step after Connect ticks the first two for you to
confirm. **Drop luck in chat**, **Point the game's arrow at a destination**, **Show the step on
screen** and **Mark a farm run's teleports in the bank** start on; none of them sends anything.
**Route here on the world map** starts on too, and sends only when you choose it.

</details>

## Good to know

- Drop luck counts the kills the plugin has seen on normal members worlds, not your lifetime kill
  count, and only drops with a sourced rate get odds. Activity rewards need RuneLite's
  **Loot Tracker** plugin. **Forget kill log** clears the current character's counts.
- Birdhouses need RuneLite's **Time Tracking** plugin. Farm timers are estimates; disease and later
  visits can change them.
- After logging in or hopping, check your hunter rumour with the quetzal whistle again; ask an
  estate agent to see your Mahogany Homes job again.
- **Open website** beside a death, or beside a star whose spot is approximate, opens it on
  osrs-map.com in your browser. **World map** beside a friend draws them on the game's own map.

## Help and feedback

Found a bug, or have an idea? [Open an issue](https://github.com/plbolduc/osrs-map-companion/issues)
with what you did and what you expected to happen.

## Licence

BSD 2-Clause; see [LICENSE](LICENSE) and [NOTICE](NOTICE) for bundled third-party notices.
The drop rates in `src/main/resources/unique-rates.json` are transcribed from the OSRS Wiki,
CC BY-NC-SA 3.0; NOTICE names the sources of every bundled table.
