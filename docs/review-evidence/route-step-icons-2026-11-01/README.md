# Route-step pictures — 1 November 2026

A teleport step in the sidebar's route now carries the game's picture of what to click, by the
item id or spell sprite id the map sends (the map's `lib/route-step-icons.ts`; this repository's
copy is `src/test/resources/contract/route-step-icons.json`, asserted against `gameval` by
`RouteStepIconsTest`).

The four pictures here are the actual `RouteStepsView` and `FarmRunPanel`, painted by
`sidebar-polish-2026-10-29/SidebarPolishShot.java` with RuneLite 1.13.4's look and feel, before
(on `9b8f6a8`) and after this change, in the `route` and `farm-card` scenes:

- `route-before.png` / `route-after.png`: Lumbridge Home Teleport's step gains the spell's icon;
  the two walking steps stay words.
- `farm-card-before.png` / `farm-card-after.png`: the current leg's *Use Catherby tablet* gains the
  tablet's sprite. The fourth stop reads *Draynor Manor Teleport* after because the shared run
  fixture now reaches it by that spell, so the fixture carries a spell's picture as well as items.

`docs/mockups/sidebar-route.png` and `docs/mockups/sidebar-farm-run.png` are the `route` and
`farm-bring` scenes as they render now. The route picture is taller than the one it replaces
because Home has carried the *Farm run* picker since that picture was taken.

Sprites are the website's copies of the same art, read through `OSRS_MAP_PUBLIC`: the home
teleport's icon from `osrs-icons/1x/home-teleport.png` and the tablet's from its item file. A
running client draws them from its own caches through `ItemManager` and `SpriteManager`, which
this renderer cannot open; that is still a live check in `docs/live-checks.md`. Names are made up.
