# Build review

Use this policy when reviewing changes to build tasks or default verification
commands. Formatting and static analysis belong to the existing automated checks.

## Changes to everyday build commands

When a change adds work to `check`, `assembleDev`, `buildDev`, or development-server
startup, review its effect on the people who run those commands. The change
description must identify:

- Which commands gain work, including work added through task dependencies.
- What work is added, such as application linking, browser startup, or containers.
- The expected time and environment cost. Give measurements when available;
  label estimates and unknowns explicitly.
- Why the work belongs in each default command rather than a focused task or CI.

Judge the placement against the failure it catches and how often callers pay the
cost. A useful check can still be placed too broadly. Keep the immediate fix and
additional regression safeguards distinct in the description so each can be
assessed independently. Flag unexplained changes to a command's scope as a review
finding even when the added task passes.
