# Port tasks in the sidebar — 1 November 2026

`docs/mockups/sidebar-port-tasks.png` was the real `PortTasksPanel` (since replaced by the run,
`port-task-run-2026-11-01.md`), painted at 225px with
RuneLite 1.13.4's look and feel by `sidebar-polish-2026-10-29/SidebarPolishShot.java`, scene
`port-tasks-card`. The `port-tasks` scene photographs it inside Home and `port-tasks-unread`
with one slot the client could not read and no hold seen. The card is 226px tall, 195px wide.

The renderer lays a reading into `PortTaskReader` directly rather than through the harness client,
which answers no port task table. The rows are the map's own `data/port-tasks.json` tasks 81, 84,
121 and 466 — Brimhaven's sword crates aboard, its fruit and Port Khazard's sand still to collect,
and the osprey bounty with nine feathers owed — and the website's Port tasks scene holds the same
five slots, so the two pictures describe one account. The Sailing skill sprite is the website's
copy of the game's icon, through `OSRS_MAP_PUBLIC`.

The first render clipped the heading's last letter: the bold face draws its final "s" past the
advance width. Two pixels of right border on the heading fixed it.

## What this does not show

The reading is synthetic. `PortTaskReaderTest` and `CargoHoldsTest` drive the real reader against
a database shaped like the game's, with row ids that differ from the task ids, but no slot,
counter or hold has been read from a live client. `docs/live-checks.md` names those checks.
