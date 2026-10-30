# Notes for coding agents

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before changing anything. Its section **Rules a
change must keep** applies to every change here: what the Plugin Hub allows, which thread owns
what, what may leave the machine, and how to commit.

- **Done means** `./gradlew clean build -PruneLiteVersion=1.13.4` passes, and a test added for a
  fix fails when the fix is reverted.
- **The plugin's game readers are unverified until seen in a live client.** A green build does not
  prove an id, a varbit or an event order; [`docs/live-checks.md`](docs/live-checks.md) lists
  what still needs checking in game.
- **The network disclosure is a promise.** If a change sends something new, update the README's
  *What you share*, [`docs/network.md`](docs/network.md) and
  [`docs/plugin-hub-submission.md`](docs/plugin-hub-submission.md) in the same commit;
  `NetworkDisclosureTest` checks the README against `SnapshotBuilder`. The README is published
  on RuneLite's site at the pinned commit, so it is written for players: how the requests work
  belongs in `docs/network.md`, and building in `CONTRIBUTING.md`.
- **The history is public, so a pull request made here is clean before it is pushed.** Commits
  follow CONTRIBUTING's *Commits*. Every commit is one change a reviewer can read in one sitting,
  and builds and passes on its own. A pull request may carry several related commits; unrelated
  work does not ride along. Fixups are folded into the commit
  they fix before the first push, because GitHub keeps every commit a pull request ever showed. A
  pushed branch is not rewritten without the owner's say. These are merged with *Rebase and
  merge*, keeping the line straight; an outside contributor's pull request is squashed instead.
- **The README is the Plugin Hub page:** CONTRIBUTING's section of that name.
- **Ask before** pushing, opening a Plugin Hub pull request, or anything else that leaves this
  checkout.

## Keep the Plugin Hub README current

- When a change adds, removes or alters a player-facing feature, control, default or pictured
  surface, review the README in the same pull request and refresh the affected text and pictures.
  Internal changes that leave it accurate need no README edit. Website changes count when they
  affect a feature this README describes.
- Keep the introduction short and lead with features. Put each useful picture in the section
  for the feature it explains, before the explanation; use a focused component capture where
  it tells the story better than a whole sidebar. Keep setup concise and technical detail in docs.
- Keep release history on the website's **What's new** page, linked from the README. Describe
  permanent features in their feature sections rather than repeating them in a version changelog.
- Keep the server/IP notice visible under **What you share**. Full sharing details and Settings
  may be expandable, but the disclosure must stay complete and accurate; change it with the
  network behaviour and the disclosure tests, never just to shorten the page.
- Check the relevant plugin and website code and branches before claiming a feature is missing
  or complete. Label website features clearly; a game reader is not itself a sidebar page.
- Capture actual UI with synthetic names and real game sprites where available. Check missing
  sprites against the screenshot fixture before treating them as product bugs. Record stand-ins
  and pending live-client checks in the picture's evidence; never claim a renderer is a game visit.
- After an affected README change, read it as a new player, check links and picture files, and
  inspect a phone preview in both themes for legibility and overflow. Keep capture provenance in
  `docs/review-evidence/`; `readme-2026-10-29.md` records the current pictures and their limitations.
