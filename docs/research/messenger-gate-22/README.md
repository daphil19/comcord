# Desktop Messenger outbox and encrypted-state gate (#22)

Verdict: **PASS**, 2026-10-27, Messenger 4.10.3 / Trixnity client 5.9.2.
Prerequisite: [successful desktop ownership gate #21](../messenger-gate-21/README.md).
This extends that isolated experimental fixture on `codex/messenger-adoption-gates`.
It does not deliver production composer #7, encryption #14, timeline #6, or alter
PR #19. The successful verdict permits starting #23; broader adoption still needs
that gate's target-by-target runtime verdict.

## Reproduce

Requirements: Java 17, Docker and a macOS desktop session. From this experimental
branch:

```sh
./gradlew :composeApp:matrixSmokeTest --tests '*MessengerOutboxGateSmokeTest' \
  :composeApp:detektJvmTest :composeApp:ktlintCheck
```

`MessengerOutboxGateSmokeTest` contains four scenarios. They extend
`MessengerTimelineGate` with the actual Messenger `InputAreaViewModel`, outbox
holders and encrypted-message element VMs. Each scenario creates a fresh
Synapse v1.149.1 container, random loopback port, client storage, Accounts and
devices, then disposes them. The development homeserver and host credentials
are unused. `SendFaultProxy` intercepts the disposable client's HTTP boundary;
it supplies no outbox, timeline or crypto state. Screenshots regenerate under
`composeApp/build/gate-evidence/22` and remain local build artifacts; PNGs are
not tracked. The written observations below record the visually reviewed run.

## Criteria and observed outcomes

| #22 requirement | Outcome and evidence |
| --- | --- |
| Visible sending message reconciles once, with correct identity | PASS. Compose types into Messenger's own input VM and invokes its send action. The proxy holds the real send request; the SDK outbox body and `Sending` state remain visible. Releasing it produces one actual `TimelineElementHolderViewModel`, belonging to the sending Account, and exactly one total body holder. A sent outbox holder alone cannot satisfy confirmation. |
| Controlled failed state and successful retry | PASS. The proxy returns HTTP 403 with Matrix `M_FORBIDDEN` only for send requests. Messenger exposes its own localized send error and `canRetrySend`; the fixture renders `Failed` and invokes the holder's `retrySend`. Disabling the fault allows one confirmed event with no residual send error or duplicate body. |
| Real encrypted content with keys, clear placeholder without keys | PASS. Disposable Bob creates an encrypted Channel and sends through the actual SDK outbox. The fixture asserts the server event is `EncryptedMessageEventContent`. Bob stops sync before inviting Alice, leaving his historical session key unavailable to her. Alice's actual Messenger `EncryptedWaitTimelineElementViewModel` renders `Unable to decrypt message: waiting for keys`; the event remains visible. Alice sends a new encrypted message through Messenger, and its confirmed timeline holder shows readable content. Its server event is also asserted encrypted. |
| Switching/destruction while sending or decrypting retains #21 cleanup | PASS. With the HTTP send suspended, selecting B destroys A's SDK room scope and UI paging loop. After releasing the request and independently awaiting real sync, A stays frozen and selection stays B. Destroying B during another held send similarly removes SDK descendants; B stays frozen after that send syncs. The encrypted scenario also switches away from, reopens and destroys the Channel while its actual SDK missing-key wait remains active; obsolete room-context descendants and paging work disappear. |
| No second outbox, crypto or timeline owner | PASS. The existing AccountStore alone selects the Channel. Compose collects Messenger holders and their state directly. Input text, send eligibility, error, retry and decryption presentation come from SDK VMs. The HTTP proxy only delays/rejects network requests; no SDK algorithm is mocked or reproduced. Account outbox/sync intentionally outlives a selected room UI, as required by the ownership model. |
| Runnable evidence and honest verdict | PASS. This branch preserves the fixture, command, written observations and the obligations/limits below. All four new scenarios, original #21 scenario and existing adapter smoke test passed together; fast JVM tests also passed. The affected JVM fixture passed Detekt and ktlint. |

The tightened four-scenario integration and checks run passed in 27 seconds.
The final broader JVM run executed all six smoke scenarios successfully; a single
indentation issue was then formatted and lint rechecked. Rendering screenshots
were visually reviewed by the implementer and parent reviewer, including readable
contrast, the failed-state Retry button, missing-key placeholder and confirmed
message bodies. Initial failing tests were recorded before introducing the
composer, retry action and encrypted fixture setup. The original #21 destruction
negative-control evidence remains applicable.

## Obligations and limits

Use the same single manual viewport mode and explicit per-room lifecycle as #21.
The fixture's composer forwards to `inputAreaViewModel.textField.update` and
`sendMessage`; retry forwards to the SDK outbox holder. A production compositor
must render both SDK encrypted wait and error variants rather than dropping them
or treating null content as an ordinary empty message. The missing-key scenario
observed the wait variant: the SDK's default decryption wait is indefinite. The
fixture gives that state explicit copy and does not invent a separate timeout.
The error-variant rendering is present but a distinct decryption-error/recovery
scenario is not claimed here.

Bob's offline historical session is deliberately different from Alice's fresh
outbound session. No encrypted event DTO or ciphertext is fabricated, no key is
manually inserted and no crypto implementation is supplied by the fixture. This
proves the selected encrypted message/display paths, not cross-signing, backup
restoration, trust policy, invalid recovery-key handling or protection of stored
secrets. Disposable secrets use the same isolated non-keychain provider as #21;
no platform secret-protection claim is made.

The HTTP 403 fault represents a deterministic Matrix send rejection; it does not
claim coverage of every offline/server failure. Room disposal cancels room-owned
observation and presentation, while Account-owned sending can complete after
leaving the Channel. Sync barriers in the send cleanup scenario are temporary
integration observations, not another timeline presentation owner.

During setup exploration, a producer-content barrier waited on the wrong flow;
it was replaced by actual SDK outbox confirmation. Two overlapping diagnostic
Gradle runs produced invalid report artifacts and are excluded from success
evidence; final runs were serial. The valid final scenarios used actual Synapse
and SDK data throughout. No production/shared Kotlin, dependency or toolchain
was changed, so this ticket uses affected JVM verification and makes no Android,
JS, Wasm or iOS runtime claim. Those remain #23.
