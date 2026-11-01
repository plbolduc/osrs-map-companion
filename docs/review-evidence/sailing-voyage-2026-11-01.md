# A voyage in the sidebar — 1 November 2026

`docs/mockups/sidebar-voyage.png` is the actual `RouteStepsView`, painted at 225px with RuneLite
1.13.4's look and feel by `sailing-voyage-2026-11-01/VoyageShot.java` from the reply fixture's
voyage. The walk to Port Sarim and boarding are done, *Sail to Catherby* is current, and its docking
gate is shown unmet: the fixture's gate was raised to 76 Sailing for the picture, to show the tier.
The two other scenes it paints were inspected and not published: boarding current, and the boat
read at Rimmington after the route was planned, which adds the line pointing at Reroute.

Nothing here is a game visit. The steps tick in `VoyageStepsTest` from a fake deck and fake boat
readings; what a live client still has to show is in [live checks](../live-checks.md), under
*Following a voyage*. The world map's voyage line is drawn by `VoyageMapOverlay` after the game's
world map interface and was not photographed: it needs a running client. Characters are synthetic.
