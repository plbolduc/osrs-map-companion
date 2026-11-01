# A port-task run in the sidebar — 1 November 2026

`docs/mockups/sidebar-port-tasks.png` is now the real `PortTasksPanel` following a port-task run,
painted at 225px with RuneLite 1.13.4's look and feel by
`sidebar-polish-2026-10-29/SidebarPolishShot.java`, scene `port-run`. It replaces the card of held
tasks that `port-tasks-2026-11-01.md` describes, which is still the `port-tasks-card` scene and now
ends in **Start run**.

The run is `src/test/resources/contract/port-task-run.json`, the map's own planner answer for
three slots — Port Khazard's fruit aboard, its sand waiting for Ardougne, and Brimhaven's osprey
bounty with nine feathers owed — with a boat at Port Sarim. The scene accepts it into the real
`FarmRunState`, then gives `observePorts` two readings: the slots as planned, and the fruit's slot
let go of after its crates were loaded with the sand loaded beside it. Stop 1 ticks from those
counters alone, and the leg to Ardougne is the followed destination: the walk to the boat at Port
Khazard, *Board*, *Sail to Ardougne* with its Sailing gate, *Dock*, and the walk to the ledger
table. The card is 503px tall, 195px wide.

Scenes painted for this change and looked at:

- `port-run`: the picture above. The card's own heading names the run, so the run's title is not
  repeated under it; *Before you leave* is absent, since a port-task run brings nothing.
- `port-run-changed`: the sand task swapped for another in its slot. The amber line *Your port
  tasks have changed since this run was planned. Reroute plans from what you hold now.* sits
  under the progress line, and stop 1 stays undone.
- `port-tasks-card`: the held tasks and the footer, then **Start run**. The footer no longer sends
  the order to the website's page.
- `port-tasks-unread`: with *Boats and moorings* off, *Turn on Boats and moorings to plan these as
  one run.* in place of the button.
- `farm-card`: the farm run card after the run view moved into `RunView`, unchanged in what it
  shows.

## What this does not show

The slots, counters and boats are synthetic; `PortTaskRunTest` drives the real state through the
same rules, but no counter has been read from a live client and no voyage line has been drawn on
a live world map. `docs/live-checks.md` names those checks, including whether the game clears a
courier slot on its last crate, which decides which of the two tick rules a drop-off meets.
