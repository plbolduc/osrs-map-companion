# The Tasks cards — 11 November 2026

Pictures for *The Tasks cards* in `docs/sidebar-layout.md`, and the four README pictures that show
a task card, painted again because every card's heading now carries its fold.

## How they were made

The real `MapCompanionPanel` painted at the sidebar's 225px, 2× into a 450px-wide PNG, with RuneLite
1.13.4's look and feel, by `sidebar-polish-2026-10-29/SidebarPolishShot.java` as
`readme-2026-11-02.md` describes, on Linux with OpenJDK 21. Synthetic state and made-up names, no
game account, and no request: the map address is a port nothing listens on.

The renderer has two scenes more for this, `tasks` and `tasks-folded`.

| Picture | Scene | Painted |
| --- | --- | --- |
| `tasks-cards-before.png` | `tasks`, at `6923394`, before the change | 225×1133 |
| `tasks-cards-after.png` | `tasks` | 225×1039 |
| `tasks-cards-folded.png` | `tasks-folded` | 225×443 |
| `sidebar-route.png` | `route`, Home | 225×624 |
| `sidebar-slayer.png` | `slayer-card`, the card alone | 225×191 |
| `sidebar-farm-run.png` | `farm-count`, the card alone | 225×311 |
| `sidebar-port-tasks.png` | `port-run`, the card alone | 225×444 |

The `tasks` scenes are Home with the farm run idle, a Slayer task with its list open and the port
tasks of the `port-tasks` scene. The three were painted with
`-Dslayer.options=docs/review-evidence/tasks-cards-2026-11-11/araxytes.json`, six places for
Araxytes written by hand after the owner's photograph of their own sidebar: the names and labels are
that photograph's, the ids and tiles are made up, and it is not the map's answer for the task. The
count reads *143 left* because the scene sets it. Without the property the scene uses the
contract's Gargoyles list, as the four README pictures do.

## Limitations

- Stand-ins: skill icons come from the website's copies of the game's sprites
  (`OSRS_MAP_PUBLIC`), as in every picture this renderer paints. The *Multi* chip's swords are
  the combat tab's, the website's `skill-combat.png`: it has no copy of the multi-combat zone's
  own sprite, which is what the client is asked for. The *Cannon* chip's barrels are the website's
  copy of that item's sprite, which sits smaller in its square than the client's own. The shield
  and the sword are the website's copies of the Defence and Attack skill icons.
- The arrows ▸ ▾ ↗ and the tick are drawn from a Linux fallback font and are a little heavier than
  in the pictures painted on Windows on 2 November. Which the client draws is a live check.
- Nothing here was seen in a running client. The fold kept across a restart, a closed place opened
  by its name, the arrows' glyphs and the two chips' pictures are in `docs/live-checks.md` under
  *The Tasks cards*.

The three pictures with a Slayer place in them were painted again the same day, after its labels
became chips and then pictures alone, its long names broke before their last two words, and the
level it asks for joined the chips' row; the sizes above are theirs.

## The README, read on a phone

`readme-phone-preview.mjs --section=slayer-in-runelite` loaded all fifteen pictures at 320, 390 and
736px in both themes, with no horizontal overflow and every internal link resolved. Its HTML came
from a local conversion of the README's Markdown (Python-Markdown 3.11) rather than GitHub's
renderer, so the check made no request.
