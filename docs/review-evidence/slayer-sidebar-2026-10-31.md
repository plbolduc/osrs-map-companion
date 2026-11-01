# Slayer in the sidebar — 31 October 2026

The pictures are the actual `SlayerPanel` and Home, painted at 225px with RuneLite 1.13.4's
look and feel by `sidebar-polish-2026-10-29/SidebarPolishShot.java`. The `slayer-card`,
`slayer-open`, `slayer-expanded` and `slayer-blocked` scenes were inspected. The first two are
the component alone; expanded also photographs it inside Home. The closed card is 196px tall,
the expanded card 544px, and its preferred width is 195px inside the sidebar. The first build
preferred 215px: the wrapped heading and requirement each added their sprite outside the text
width. The heading now sizes naturally and requirements allow room for their skill sprite.

The ordinary fixture is the map's maintained Gargoyles list, with all optional wire fields
populated for the contract. The renderer clears its illustrative notice and redundant sole-kind
labels, which the real builder omits. The blocked scene is the same builder's answer for a
synthetic profile with 70 Slayer. It exercises an unmet requirement, not a claim that a real
character received that assignment below its level. Characters and counters are synthetic.
The native Slayer sprite comes from the website's existing game-asset copy through
`OSRS_MAP_PUBLIC`; no replacement art was drawn and no live sprite-cache loading is proved.

`docs/mockups/sidebar-slayer.png` is the real closed card at 2×. The README keeps its former
Slayer section anchor. GitHub's Markdown renderer supplied the local phone preview; all ten
pictures and internal links worked at 320, 390 and 736px in both themes without horizontal
overflow. The light and dark Slayer previews were inspected. Disposable captures are under
`build/slayer-shots` and `build/readme-preview`.

## What was exercised

The map's `companion-slayer-story.mjs` runs the real Java task DB reader, snapshot builder,
list receiver and Route press against an isolated production server. The fake task column
value is 7 and its DB row 9007, deliberately different. It waits through the normal login
settle and heartbeat delivery rather than speeding either up. It passed with position sharing
off and the receive stream initially off: the task and ranked list arrived, a changed local
counter kept the list's revision, changing the Wilderness filter delivered a new list on the
stream, and the authenticated Route press resolved its public spot id. Keeping the same Java
process open across a real server restart accepted the new relay's revision one. Withdrawing
Slayer sharing cleared the list and omitted both outgoing fields.

The pure receiver tests cover task, area, ranking choice, character and relay changes;
delayed old frames; receipt of a removal; quiet acknowledged lists remaining fresh on
heartbeats; malformed feature fields preserving pairing and reward acknowledgements; and
bounded names, labels and rows. The map tests every ranked place in all maintained tasks
against its public route target and checks Konar's narrowed list separately.

These are synthetic game values and renderer fixtures. They do not establish live task DB
contents, login/hop ordering, the world-map pin or hint arrow. The members-client procedure
in `docs/live-checks.md` remains pending.
