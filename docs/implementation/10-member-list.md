# Member List grouped by Role (#10)

The shared shell can show or hide the selected Channel's Member List. The panel
observes that Channel's joined SDK users and `m.room.power_levels`; it does not
read a Server's power levels. Comcord groups each member once as Admin (100),
Moderator (50), Member (the Channel default), or a numeric custom Role. Explicit
Admin and Moderator levels take precedence when the room default is 100 or 50.
Groups sort by descending level and members by name, then user ID.

This grouping is Comcord product policy, so it uses a small pure projection over
SDK member data rather than duplicating Messenger's member view models. Member
flow collectors belong to the panel composition and stop on hide, room change,
or foreground disposal. Member loading and its cache remain SDK-managed. Hiding
the list releases its width without changing the selected Channel's timeline key or live SDK timeline VM.
The compact swipe/reveal arrangement remains ticket #16.

## Verification

- `MemberRolesTest.channelDefaultAndCustomRolesEachHaveOneGroup`: red before
  implementation, then green; covers default 10, Admin, Moderator, and custom 75.
- `MemberListSmokeTest.selectedChannelRolesAndToggleKeepTheLiveTimeline`: red
  when the production toggle was absent, then green against disposable Synapse.
  It reads the real production shell and SDK members/power levels, verifies Admin
  in Channel A and custom Role 75 in Channel B, and proves hide/show keeps the
  existing live timeline VM and returns its full width. A rendered screenshot is
  written to `composeApp/build/feature-evidence/10/member-list.png`.
- `verifyJvmDev` completed all tests, host compilation and Detekt checks; its
  remaining width-assertion formatting failure was fixed and the failed ktlint
  check rerun separately.
- The production Compose screenshot was visually inspected; native manual
  interaction was not performed in the ticket worktree.
- No dependency or tooling versions changed. Combined cross-platform `buildDev`
  and final native UI checks are recorded with the integration batch.
