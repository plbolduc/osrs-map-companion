# Notes for coding agents

Read [`docs/contributing.md`](docs/contributing.md) before changing anything. Its section **Rules a
change must keep** applies to every change here: what the Plugin Hub allows, which thread owns
what, what may leave the machine, and how to commit.

- **Done means** `./gradlew clean build -PruneLiteVersion=1.13.4` passes, and a test added for a
  fix fails when the fix is reverted.
- **The plugin's game readers are unverified until seen in a live client.** A green build does not
  prove an id, a varbit or an event order; [`docs/live-checks.md`](docs/live-checks.md) lists
  what still needs checking in game.
- **The network disclosure is a promise.** If a change sends something new, update the README's
  *Network disclosure* and [`docs/plugin-hub-submission.md`](docs/plugin-hub-submission.md) in
  the same commit; `NetworkDisclosureTest` checks the README against `SnapshotBuilder`.
- **Ask before** pushing, opening a Plugin Hub pull request, or anything else that leaves this
  checkout.
