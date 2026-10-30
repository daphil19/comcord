# Messenger authentication and Account restoration (#24)

Implementation on `codex/24-messenger-auth-restoration` follows spec #2 and
ADR 0005. Gates #21–23 are treated as passed on the user's explicit confirmation
on October 29, 2026. This work does not add new agent-observed #23 platform runtime
results; the historical gate guides remain records of their original evidence.

## Ownership and behavior

`ComcordApplicationHost` retains Messenger client/storage lifetime separately from
shell composition. It supplies the root VM's explicit Essenty lifecycle and
closes the full MultiMessenger on destruction. Authentication, discovery,
settings, initialization/restoration, initial sync and regular foreground sync
remain Messenger workflows. AccountStore continues to own visible Channel/View
selection; no SDK room-selection callback is bound to the visible shell. Existing
SDK setup/verification routes remain available for #15, but observers without UI
do not deliver verification or recovery.

The supported password flow presents actionable failure messages and can retry credentials or
Homeserver discovery. The discovery Retry action updates the SDK TextField epoch
with the same URL; it does not require changing a correct address. A Homeserver
without password login has an explicit explanation. Root initialization failures
provide non-destructive retry or the SDK's applicable local-data recovery action,
with explicit confirmation and data-loss wording. The root UIA stack renders its
supported password, dummy, confirmation, token and browser-fallback states;
email/phone challenges expose the SDK's unsupported state and cancellation.
This does not add new authentication methods.

`ForegroundMessengerPolicy` is an explicit product-policy override: Messenger's
initial-sync default allows background work, while Comcord's scope is foreground
sync. The policy invokes the actual SDK `RunInitialSyncImpl` only while foregrounded
and cancels its pending invocation on stop; it defers the default Main VM's first
`start()` until its lifecycle starts. The public Root VM factory binds VM routing
and callbacks to Main, matching host lifecycle events; application clients and
workers retain the SDK default background dispatcher. It preserves the SDK's
initial-sync algorithm, regular sync owner, routes and crypto. It adds no copied timeline or sync state.
The host waits for foreground before creating the root after storage bootstrap.

Platform entrypoints supply actual host events: retained Android Activity owner,
desktop minimization/restoration/close, browser visibility/page transitions and
iOS active/background/controller teardown. Native runtime behavior still needs
targeted checks; static binding and successful compilation alone are not runtime
proof. Browser back-forward-cache transitions stop/resume rather than destroying
retained application state. Async page unload cannot guarantee cleanup completes.

## Verification record

- The public host regression for closing during suspended startup was observed
  failing before cancellation ownership was added, then passing afterward.
  A virtual-time bootstrap timeout regression also verifies that timeout exposes
  recovery while host cancellation still propagates.
- JVM production and integration-test source compilation completed.
- `MessengerAuthenticationSmokeTest` exercises actual discovery/password VMs,
  controlled HTTP discovery failure and unchanged-URL retry, wrong-password
  correction, foreground sync stop/resume, persistent same-Account/device restart
  and restored usable Channel projections with test-managed Synapse.
- The final headless authentication scenario, startup cancellation regression and
  both Android/JVM type-resolved Detekt tasks passed in 22 seconds after adding
  the supported foreground policy and explicit cancellation handling.
  Held real login and initial-sync responses across
  host stop, normal stop/resume, invalid filesystem storage-root failure with
  retry into the unchanged saved Account, backgrounded startup before resume,
  persistent same-device restart and fresh post-restart room sync all passed.
  Earlier desktop/Swing test attempts were
  canceled after the host slept; no runtime pass is inferred from those attempts.
  The headless runner uses one dedicated serialized dispatcher for Main and the official Decompose test
  setting that disables native main-thread logging checks. MultiMessenger,
  clients and workers retain their production default background dispatchers.
  The scenario preserves real Messenger, Synapse, client storage, sync and crypto;
  it does not prove native rendering.
- Interactive JS browser checks at the initial implementation revision confirmed
  real Homeserver discovery, wrong-password recovery, correct login into the
  synced shell, and shell restoration after browser reload. These checks used the
  documented local development homeserver; the disposable JVM fixture remains
  independent. The in-app browser did not expose actual visibility changes, so
  these observations do not prove browser lifecycle stop/resume. Native desktop
  interaction was unavailable while the host computer was locked.
- Formatting and focused source/static checks passed. Final `buildDev` and any
  additional target runtime evidence are reported with the integration PR.
  No production timeline, composer or encryption feature ticket
  is closed by this authentication slice.

Focused commands (Java 17, Docker):

```sh
./gradlew :composeApp:jvmTest --tests '*ComcordApplicationHostTest'
./gradlew :composeApp:matrixSmokeTest --tests '*MessengerAuthenticationSmokeTest'
./gradlew buildDev
```

The smoke fixture owns its random-port Synapse database, HTTP fault proxy and
persistent temporary client directory. It disables OS keychain access only for
its disposable test secrets. Never apply that test override to production.

## Storage and security limits

Production keeps Messenger's existing settings, repository and platform secret
providers. Its pinned platform implementations use Android encrypted preferences,
iOS Keychain and JVM macOS Keychain/Windows credential storage; the Linux JVM
provider supplies no equivalent platform key, and web supplies no platform key
provider or native database encryption. Browser storage remains subject to origin
isolation, eviction and profile access. Persistent restoration does not establish
cross-signing, backup recovery, safe profile migration, device-keystore guarantees
or protection from a compromised operating system/browser profile. See the
[pinned platform research](../2026-10-26-trixnity-boundary.md).

Foreground sync is not background execution, push or notification delivery.
