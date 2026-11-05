# Channel and DM timelines (#6)

Implemented against the Messenger foundation merged through PRs #25 and #26,
2026-10-30. Messenger 4.10.3 / Trixnity client 5.9.2 are unchanged.

## Prerequisite and ownership

The successful desktop workflow evidence is in [gate #21](../research/messenger-gate-21/README.md),
with sending/encryption follow-up in [gate #22](../research/messenger-gate-22/README.md).
[Gate #23 was accepted as passed by the user](https://github.com/daphil19/comcord/issues/23#issuecomment-6531761768).
That acceptance is distinct from agent-observed runtime results; the historical
platform guide retains its original limitations. Authentication and host lifecycle
come from the [merged #24 integration](../research/messenger-auth-24/README.md).

`AccountStore` remains the sole authority for Account, Server/View and Channel/DM
selection. The production `ComcordAccountShell` now renders `ChannelTimeline` for
that selected identity. No messages, pagination window, timeline errors or SDK
navigation state are added to AccountStore or MatrixSource.

A composition keyed by Account and conversation creates one SDK
`MatrixClientViewModelContextImpl` and `TimelineViewModel`. Its lifecycle resumes
on entry and is destroyed on disposal. SDK coroutines inherit only the composition's
parent Job, plus the background dispatcher; UI frame-clock/test-dispatcher context
is not passed into SDK workers. The SDK factory can be supplied by Messenger's
Koin graph; its real default factory is used otherwise.

Switching conversations, removing the selected conversation, closing the shell,
opening the compact Channel List or backgrounding the host disposes the live
room scope and UI paging collectors. Foreground state comes from the existing
native host. AccountStore and its selection remain alive when the host is hidden;
returning opens a fresh timeline. Scroll/history windows are not retained across
scope disposal. Draft/composer persistence is outside this read-only ticket.

## Rendering and viewport contract

The UI consumes Messenger holders, sender/avatar flows, formatted times and
message/decryption VMs directly. Room configuration and membership events are
omitted from this message reading surface. Unsupported message types retain an
explicit placeholder; rich-media interactions are outside #6.

The product uses the same **manual viewport mode** validated by #21:
`timelineViewStateSupported = false`. Scrolling to the top calls `loadBefore`;
a cancellable `loadAfter` collector receives incoming events. A short delay
between completed forward loads prevents a no-progress retry loop. Stable SDK
keys retain the scroll anchor while history is prepended. SDK `scrollTo` requests
are performed and acknowledged with `finishedScrollTo`; Latest messages calls
the SDK's `jumpToEndOfTimeline`. No `setViewState` mode is mixed in.

Loading and empty states are visible. SDK error messages and exceptions crossing
the manual-load boundary have a Retry messages action. Retry destroys and
recreates the room VM; it can reset the loaded history window. Network retry
inside the SDK remains SDK-owned. Back/settings/profile/mention/metadata callbacks
are inert because their actions are not exposed in this reading surface.

## PR #19 reconciliation

PR #19's old head `c46a5e6c0e32947646eb4334d4329dce1fa311cd` proposed a
store-owned timeline (`RoomTimeline`, `TrixnityRoomTimeline`, and copied timeline
state in AccountStore). None of those proposed APIs or models are adopted here.
The new implementation is based on current main and the actual Messenger gate.
The old head is preserved on `codex/pr19-store-owned-archive`; its Gradle cache/parallel
commit is not included in this feature replacement.

| Useful scenario from the old PR | Current evidence |
| --- | --- |
| Initial messages, sender and time | `selectedChannelRendersHistoryAndIncomingMessages` uses production Compose and SDK holders |
| History to the start | The same test scrolls through 40 seeded messages to message 0 in chronological order |
| Incoming without reopening | The same test receives an API-sent event in the selected Channel |
| Channel/DM switching and late events | A→B→A plus `directMessageUsesTheSameTimelineAndUploadedAvatar`; old A stays frozen while B receives events |
| Cancel pending history | `leavingAChannelCancelsPendingHistoryAndNeverShowsLateData` holds an actual HTTP history request while switching |
| Selected room removal | The main test leaves A through Matrix; the DM avatar scenario also leaves the DM. Both check selection clearing and SDK scope disposal |
| Retry | `emptyChannelCanRetryAndReceiveItsFirstMessage` recreates the SDK scope and receives the first message |

Existing common `AccountStoreTest` scenarios continue to cover Comcord-owned
selection, renaming, removal, Account isolation and Channel/DM projections.
The new JVM `ChannelTimelineSmokeTest` uses an isolated homeserver and disposable
client storage. `TimelineProbe` observes actual SDK factory contexts without
substituting timeline behavior. Additional scenarios cover foreground disposal
without losing Account navigation and selection at a 480 dp viewport width.

## Verification

- Red: production-shell integration initially failed compilation because
  `ComcordAccountShell` was absent; the narrow-screen selection test failed because
  the compact layout had no Channel selection control.
- Green: all six `ChannelTimelineSmokeTest` integration scenarios pass.
- Production Compose captures under `composeApp/build/timeline-6-evidence/` record
  history, incoming events, narrow-screen selection and a cyan uploaded DM avatar.
  These disposable screenshots are local artifacts, not committed assets.
- `./gradlew buildDev` passes: JVM has 13 tests; Android host, JS browser,
  Wasm browser and iOS simulator each have 11, with zero failures or skips.
  Desktop JAR, Android debug APK, JS/Wasm compilation and the iOS simulator debug
  framework build successfully; configured lint/static checks pass.
- The full `:composeApp:matrixSmokeTest` run is **not green**: 12 pass, one
  intentionally skipped opt-in host, and one timeout in the existing
  `MessengerOutboxGateSmokeTest.encryptedHistoryShowsContentWithKeysAndPlaceholderWithoutKeys`.
  Its 10-second room-reopening timeout also reproduces when run alone in a
  disposable pristine checkout of merged main `1717261`. It is not introduced
  by this feature. The #6 scenarios pass independently; no old gate fixture
  assertion or SDK workflow was weakened to hide the failure.
- Final feature verification **passes**: all six `ChannelTimelineSmokeTest` cases
  plus `:composeApp:detektTestJvm` and `:composeApp:ktlintCheck` (32 seconds).

Reproduce the production workflow tests with:

```sh
./gradlew :composeApp:matrixSmokeTest --tests '*ChannelTimelineSmokeTest'
```

## Standards review

Zero findings against the diff from merged main `1717261`. The independent
review checked documented conventions, SDK ownership/lifecycle boundaries,
vocabulary and meaningful code smells; tooling-enforced checks were left to Gradle.

## Spec review

Zero source defects or scope findings in the independent review. One verification gap: #6's new manual
production timeline checks on Android, browser and iOS are outstanding. Keep
PR #19 draft until those results are recorded. The later full-suite run also found
the pre-existing encryption-gate failure documented above. This is separate from the accepted
historical #23 adoption prerequisite.

Visual inspection covers those desktop Compose captures after scripted scrolling,
including the initials fallback and uploaded-avatar path. This is not a claim of
new manual Android, browser or iOS runtime verification. Those target-specific
production timeline checks remain required before treating all of #6's manual
acceptance criteria as complete or marking PR #19 ready.
