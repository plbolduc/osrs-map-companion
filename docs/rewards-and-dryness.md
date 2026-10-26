# Observed rewards and defensible dryness

The plugin separates two questions: which reward is worth announcing, and which repeated trial
has a supported constant drop probability. Collection membership answers the first. It cannot
establish a drop rate, a lifetime kill count, or a pet reset.

## What is sent

The existing v1 envelope and `level`/legacy `npc_drop` kinds remain. A settled observation can add
`kind: "reward"`, `rewardType: "item" | "pet" | "collection_log"`, an item name, an optional
item id, observed quantity, optional `{kind: "npc" | "activity", name}` source, and an optional
`collectionNew: true` badge. A normal item receipt needs its item id and positive quantity.
Unresolved pets and collection additions retain a name and omit unknown metadata; Gson's omitted
nulls are accepted by the matching web release. No source is guessed from the last boss seen.

The observer waits six game ticks before assigning an outbox id. Complementary loot, local pet
and collection notices can join; distinct loot receipts remain distinct. If two receipts make
the source of a collection addition ambiguous, both receipts remain and the collection addition
has no claimed source or quantity. Duplicate collection callbacks within the pending window do
not create a second addition. The existing 32-entry queue, eight-entry batch, five-minute retry
window, ids, acknowledgement handling and snapshot cadence are unchanged.

The milestone switch is the master. Pets and collection additions have separate switches, and
all three start off. Nothing is queued while unpaired or logged out. Settings/group
resets share a lock with observation flushing and outbox enqueue; context checks also catch a
setting write becoming visible before its event callback. Character and session boundaries clear
unsettled evidence. The member/profile identifiers used locally for storage are never added to
reward payloads.

## Catalog and item selection

`CollectionItemCatalog` traverses game definitions: enum **2102**, tab struct parameter **683**,
page item enum parameter **690**, and replacements enum **3721**. Parameter **689** identifies
the *All pets* page. The path is independently implemented from the maintained
[WikiSync cache traversal](https://github.com/weirdgloop/WikiSync/blob/1ff4ea0653395b552844d658d3668815d82e0a79/src/main/java/com/andmcadams/wikisync/WikiSyncPlugin.java#L479).
It reads no obtained-item container, opens no interface, and contacts no wiki or game service.

Every referenced definition must resolve. Minimum counts detect a collapsed traversal; failures
retry locally at most 30 times, ten ticks apart. Incomplete definitions never become a complete
catalog. Notes and explicit cache replacements canonicalize; other variants are not guessed.
Names shared by more than one canonical id remain unresolved.

`RewardPolicy` selects collection items except repeated Zulrah's scales, charged/uncharged
bracelets of ethereum, and ancient essence. First collection additions remain eligible. Three
reviewed signature exceptions are Unsired (**13273**), draconic visage (**11286**) and dragon full
helm (**11335**). Ids are transcribed from the pinned RuneLite generated `ItemID` and checked in
tests. There is no price threshold or reuse of every drop-rate row: coins, ordinary runes, herbs
and other unselected supplies stay quiet.

## Finite source coverage

NPC rewards come from `ServerNpcLoot`. The event's item list is copied during synchronous handling:
core clears its backing list immediately after dispatch. The forwarded NPC `LootReceived` event
is ignored, so two core descriptions cannot double an NPC reward.

Activity receipts require **RuneLite Loot Tracker enabled**. Their exact finite vocabulary is
tracked in [reward-source-matrix.json](reward-source-matrix.json), including the item ids behind
dynamic container names. Every admitted source still passes the item policy above. A source that
only gives unselected supplies produces no announcement.

All references below are to the pinned
[RuneLite LootTrackerPlugin](https://github.com/runelite/runelite/blob/ac79ed8bd8926bec7bf172aa291574b4d944b0e7/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java).

| Core adapter | Reviewed source vocabulary | Source lines |
| --- | --- | --- |
| Reward interfaces | Barrows, CoX, ToB, ToA, Fishing Trawler, Lunar Chest, Fortis Colosseum, Kingdom of Miscellania, Drift Net | 829–955 |
| Doom claim | Doom of Mokhaiotl | 342, 1006 |
| Region chests | 19 exact names, including Brimstone, Crystal, Larran's, Elven Crystal and the newer key chests | 204–227; emitted 1037–1049 |
| Shades | 25 Bronze/Steel/Black/Silver/Gold key and trim combinations | 239–264; emitted 1344 |
| H.A.M. storeroom | Four exact key-based chest names | 1097 |
| Birdhouses | Nine exact wood-type labels | 280–288; emitted 1174 |
| Local gathering/processing | Herbiboar, Zombie Pirate's Locker, Golem Crafting, seed packs and bird nests | 1075, 1087, 1350–1354, 1631, 1640 |
| Salvage/tarnished processing | Eight salvage tiers, nine tarnished item names, Vale offerings, Dirty arrowtips, Rubium geode | 1060–1064, 1182–1217, 1254–1286 |
| Clues | Beginner, Easy, Medium, Hard, Elite and Master reward interfaces | 1123–1150 |
| Named minigame rewards | Tempoross, GOTR, Wintertodt, Unsired, Barbarian Assault, Mahogany Homes, Hallowed Sack, Spoils of war | 1223–1248, 1374–1409 |
| Containers | 21 exact item-definition names, plus Casket, the three gem-bag vendors, and Volcanic Mine ore packs | 1361–1412 |
| Hunter sacks | Five exact tier names | 1415–1433 |

**Excluded deliberately:** `Loot Chest` is PvP loot-key contents even though core labels it EVENT
(line 975). PLAYER, PICKPOCKET and UNKNOWN `LootReceived` types remain excluded, as do unknown
EVENT names. The API does not authenticate the producer of an event: another plugin could reuse a
known label, so the allowlist is a vocabulary boundary, not a security provenance guarantee.

Collection additions use the game's enabled chat or popup notification mode. Local pet triggers
can be named only with corroborating catalog pet evidence. A trigger without such evidence is
simply **Pet**. Neither path reads old collection slots or proves complete reward coverage when
the necessary core observer or game notification setting is disabled.

## Dryness changes

The local V2 log is scoped to the current RuneScape save profile. A compound read, write or delete
pins that key, so an account switch cannot redirect an operation to another character. The old
unscoped V1 blob is preserved as a backup and never assigned to the first character that logs in.

Farming patch observations are scoped the same way, under `patchObservationsV4`, so a character
who has never seen a patch is never told its herbs are ready. Older unscoped blobs record no owner
and are not read. Reading a patch with no character selected is refused outright.

Each watched item has `trackedSinceKill`, a zero/positive obtained quantity, and a last-drop count
only when known. New, contradictory or unreadable histories begin a new observation window.
Retired catalog rows are pruned, so removing and later restoring a rate cannot bridge an
unobserved interval. A drop is a kill that dropped it: a stack of 150 dragon knives, or two stacks of
one item in a receipt, is one drop, because the odds it is compared with are per kill. One accepted NPC
receipt increments the observed loot-kill counter once. The denominator is a `double`, preserving rational probabilities.

Counts are **observed loot kills**, not lifetime game KC. Core does not establish every zero-loot
outcome. The shared table now excludes pet odds and other unobservable/unsupported trials; a
generic pet message cannot establish which NPC's drought to reset. Normal members worlds only
are supported: F2P cannot roll every member-only reward, and special world modes can change rates.
Announcements remain available independently of this restriction.

The DTO transmits every history that says something the bundled rates do not already imply: an
obtained quantity, a last-drop count where one is known, and any tracking baseline other than the
first kill. The row for "never obtained, watched from the first kill" is **not** sent. One per rate
made the forty rate-heaviest monsters 16,456 bytes of loot against a 15,872-byte budget for the
whole snapshot, so the readers with the most history were exactly the ones permanently told part of
it was missing — and a notice that is always on says nothing on the occasion it is true.

The `dropsComplete` flag is what makes leaving it out safe, because it already asserts the list
arrived whole: under it, an absent row is that default rather than an unknown. Removing any drop to
meet the eight-drop cap clears the flag; either drop/NPC clipping or byte-budget trimming sets
`detailsTruncated`. Where the flag is false or absent — every released client predates it — readers
abstain on an omitted row instead of reading it as zero drops. Local waiting odds use the same
per-item baseline and print **<1%** for a nonzero probability that rounds below one percent.

## Drop luck

How Drop luck counts:

- **Every monster is counted**, not only the 164 with a rate. Beside the rated rows, a monster keeps
  any collection-log item that actually fell, read from the same cache vocabulary the announcements
  use; pets are left out for the reason above. A collection row is a count of what was seen and
  never the basis of a probability, so a kill the vocabulary could not judge moves its window
  rather than restarting its count. The rows' provenance is kept (`collection`), so a rate that
  leaves the table cannot survive as a collection row and later re-adopt an unwatched history.
- **The log is bounded by monsters, not by a table**: at most 300, least recently killed first out,
  never the pinned one. Each monster keeps the wall-clock time of its last kill; the wire sends its
  age, relative like every other age on it.
- **The wire adds `current`, `currentOnTask` and `pinned`**, and orders monsters pinned first, then
  most recently killed, because the forty-monster cap truncates from the end and the grind started
  today matters more than one from last year. `currentOnTask` is true only where the task's own
  counter fell with one of the monster's kills (`TaskKills`): task names are categories, and a name
  join would be wrong for most of them.
- **The arithmetic and the words moved to `DropOdds`**, mirrored by `lib/drop-luck.ts` on the map
  with the same pinned cases: overall luck is a binomial tail, the current wait a geometric one, and
  every threshold reads the printed percentage. `waitingShare` and `driest()` went with How dry.
- **Two rates, priced by both.** The bundled table now carries Zulrah's twice-rolled uniques folded
  into one per-kill rate, and a `boost` for drops with a better rate on a slayer task (drakes,
  wyrms, hydras, basilisk knights) or with the easy Kourend & Kebos diary (lizardmen). Kills under
  each condition are counted apart, per monster, with each boosted drop's baselines beside its
  others; a kill whose diary could not be read restarts the drops it boosts. The wire sends the
  split as `boostedSinceLast` and `boostedWatched`, and `ratedKey` fingerprints which rated drops
  this table watches, so a map with a newer table reads a missing row as unknown rather than zero.
  Overall luck is priced over the kills each drop was watched for, not every kill.
- **What a review of the build found**, each fixed with a test: a diary reading the game had not
  yet given (the fifteen seconds after every login and hop) restarted the talisman's count, so the
  last real reading is now kept per character and stands in; a finished task still counted as "on
  task", because the game leaves the task's target set at zero kills left; a diary's rate was
  dropped for the rest of a slayer task; an unreadable item in a receipt left a boosted drop's
  baselines behind its window; the search box lost focus whenever a kill redrew the list; the
  sidebar and the map rounded 0.95 differently; and the exact binomial underflowed past about
  745 expected drops, so past 200 the normal curve takes over, on both sides alike.
- **One local chat line per rated drop**, "Abyssal whip after 780 kills — drier than 78% of
  players.", behind *Drop luck in chat* (on by default; nothing leaves the machine).

## Validation and limits

- The unit suite drives the observer, the outbox and the relay paths with definition-only fake
  clients; the current counts are in [Live checks](live-checks.md).
- The map's browser story runs harness clients against a local map and checks the same wire
  shape, including retries after a withheld acknowledgement and deduplication; its latest result
  is in Live checks too.

The terminal `settledReward` fixture begins after the observer's settling boundary. It proves the
real outbox/serialization/relay/browser contract, while JUnit separately drives actual observer
callbacks and production subscriptions using definition-only fake clients. No test opens a real
account, profile or game. These fixtures do not prove runtime game-event completeness, cache
availability after a future game update, or accuracy of every source's underlying reward script.
Two independently running clients writing the same character's log are still not a distributed
counter; each instance keeps a local cache, so concurrent writers can overwrite each other's
totals. No account-wide or authoritative lifetime KC is claimed.
