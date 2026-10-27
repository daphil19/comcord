# Desktop Messenger timeline ownership gate (#21)

Verdict: **PASS**, 2026-10-27, Messenger 4.10.3 / Trixnity client 5.9.2.
This is an isolated experiment on `codex/messenger-adoption-gates`, not production
adoption, delivery of #6, or completion of the cross-platform gate. PR #19 is
unchanged. The successful desktop verdict permits starting #22; it does not
permit skipping #22 or #23.

## Reproduce

Requirements: Java 17, Docker, and a macOS desktop session. From this experimental
branch run:

```sh
./gradlew :composeApp:matrixSmokeTest --tests '*MessengerTimelineGateSmokeTest' \
  :composeApp:detektJvmTest :composeApp:ktlintCheck
```

The checked-in `MessengerTimelineGate` and `MessengerTimelineGateSmokeTest` live
in `composeApp/src/jvmTest/kotlin/dev/phillipslabs/comcord`. The test starts its
own Synapse v1.149.1 container with a random loopback port and fresh database,
creates disposable Messenger client storage, signs in, seeds two Channels, then
runs an actual Compose desktop scene. It removes its container and storage on
normal success or failure. No development homeserver or existing Account is used;
test secrets never consult the OS keychain. Test-only Synapse message rate limits
are raised so history seeding does not wait on ordinary-user throttling.

The command passed in 10 seconds. `matrixSmokeTest` remains explicitly runnable;
ordinary `jvmTest` excludes this `*SmokeTest`. Screenshots are regenerated under
`composeApp/build/gate-evidence/21`. The four images in this directory preserve the
observed render from the successful run. No shared or production Kotlin code was
changed, so verification covers the affected JVM test fixture and its lint rules;
this run makes no Android, browser or iOS compilation/runtime claim.

## Criteria and observed outcomes

| #21 requirement | Outcome and evidence |
| --- | --- |
| Sender, avatar/time/body; initial, paged and incoming events | PASS. Actual Messenger holder and element VMs render sender name, avatar initials fallback, formatted time and message body. Forty messages are seeded before sync; the initial window (configured limit five) excludes message 0. UI paging then renders message 0; an API-sent incoming event appears without recreating the VM. See [initial](initial.png), [paged](paged.png) and [incoming](incoming.png). No uploaded-image avatar was seeded; the observed avatar path is the initials fallback. |
| Comcord selection A to B; obsolete work disposed; late results cannot replace B | PASS. Channel buttons call the existing `AccountStore.selectRoom`; keyed Compose creates the selected room VM. A has a suspended manual `loadAfter` when B is selected. A's UI paging loop exits and all room-context coroutine descendants disappear. Late A and incoming B events sync; only B appears and the old A VM stays frozen. See [switched](switched.png). |
| One explicit SDK room lifecycle and viewport mode; destruction cleans room work | PASS. One `LifecycleRegistry` per selected room is resumed on creation and destroyed by `DisposableEffect.onDispose`. `timelineViewStateSupported = false` selects only manual paging. Destroying the room UI stops its paging loop and removes SDK room-scope descendants. After another real sync event, the disposed B VM remains unchanged. |
| Actual Messenger VM in Compose; one selection authority; no copied timeline state | PASS. `TimelineViewModelFactory.create` constructs the real VM from the Messenger Koin graph. Compose collects `vm.elements` and each holder's `element`, `sender`, and related flows directly. The existing AccountStore is the sole room-selection authority. Retained session handles and atomic pending-call counts are test evidence, not another event/outbox/paging model. No Messenger Main/room router is created. |
| Setup, outcomes, obligations and runnable fixture preserved separately | PASS. This document, the experimental branch's JVM fixture, reproducible command and screenshots preserve the evidence outside production delivery. |
| Stop if substantial duplicate orchestration is required | PASS. No duplicate timeline state, SDK navigation owner or protocol implementation was needed. The remaining glue is room-context lifetime, direct rendering and the selected manual viewport contract. |
| Neither deliver #6 nor merge/close PR #19 | PASS. No production timeline files or PR #19 changes are included. |

## Lifecycle and callback obligations

The fixture creates `MatrixClientViewModelContextImpl` with a fresh component
context, `LifecycleRegistry`, and explicit parent Job. Messenger's instance-keeper
scopes inherit this Job, and its nested element lifecycles inherit room
destruction. Assertions leave the parent Job active while checking its children
have disappeared, so parent cancellation cannot conceal a missing SDK disposal
hook. Removing `session.lifecycle.destroy()` was also run as a negative control:
the test failed at the post-switch cleanup assertion after 10 seconds. Restoring
the hook passes. Frozen old VMs and a real sync barrier provide an additional
behavior check beyond coroutine counts. This proves the selected room's observed
work is disposed; it is not a general proof that the SDK has no process-global
workers. Account/client sync intentionally continues until the fixture closes.

Manual mode requires the UI to call `loadBefore`/`loadAfter` and acknowledge
`scrollTo` via `finishedScrollTo`. The fixture supplies older-history controls and
one cancellable forward-loading loop. It never calls `setViewState`, mixes the
other viewport mode, or maintains a second timeline collection. The initial
small window fits the scene, and subsequent test scrolling targets actual visible
message nodes. Production scroll retention, focus/read-marker policy, and rich
navigation remain separate implementation work.

Back, Room Settings, user-profile, mention and event-metadata callbacks are
explicit inert experiment routes. Attachment/report subroutes are not opened.
No hidden Messenger navigation or notification delivery is enabled. SDK startup
prints its development Noop notification-handler notice; that is not evidence of
notification delivery. #22 must extend this exact real-VM seam to sending/outbox
and readable/undecryptable encrypted states before claiming those workflows.

The first integration run exposed default Synapse throttling during history
seeding; the test-only rate configuration above resolved it. A first screenshot
also exposed transparent-background text contrast despite passing semantic UI
assertions; adding the Material3 Surface made content readable. The final images
were visually reviewed. This is why semantic visibility alone was not counted as
rendering evidence.

## Review follow-up: teardown failures

Resource acquisition is guarded from Synapse startup onward, including temporary
storage and Messenger creation. A failed setup cancels the Account scope and
releases any acquired Messenger, container and storage in a non-cancellable
cleanup context. Nested `finally` blocks ensure a Messenger shutdown exception
cannot skip container removal or storage deletion. The same release function is
used by normal fixture closure; a setup error is preserved with any cleanup error
attached as suppressed evidence.

Two temporary faults were executed through the actual fixture entry/close seam:
throw instead of returning a Messenger from creation, and throw after its SDK
shutdown completes. Each run failed with the intended injected exception, and
before/after snapshots confirmed no new fixture storage directories or Synapse
containers remained. The temporary source edits were restored, then the normal
Compose integration, JVM Detekt and ktlint checks were rerun. No test-only factory
interface or production injection hook was added for these checks. Cleanup inside
a Messenger factory that never returns a handle remains the SDK's responsibility.
