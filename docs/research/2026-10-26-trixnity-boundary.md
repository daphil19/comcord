# Pinned Trixnity boundary research

Date: 2026-10-26. Architecture investigation; the resulting accepted direction is
recorded in [ADR 0005](../adr/0005-messenger-first-matrix-workflows.md). Earlier
proposals below are research history; no implementation or experiment was run. Read alongside
[checkout inventory](2026-10-26-comcord-boundary-checkout.md). “Available” below
means implemented in the pinned dependency, not demonstrated in Comcord's UI.

## Method and version identity

Inspected official published source JARs from the local Gradle cache for
`de.connect2x.trixnity.messenger:trixnity-messenger:4.10.3` (common, JVM, Android,
iOS, JS, Wasm source sets) and `de.connect2x.trixnity:trixnity-client:5.9.2`.
The published Messenger POM declares client, crypto drivers, repository and media
modules **5.9.2**, Ktor **3.5.2**, Decompose **3.5.0**, Kotlin **2.3.21** and
coroutines **1.11.0**. This establishes its published baseline, not every resolved
version after Comcord's overrides. Sources: [official Maven POM][pom],
[Messenger v4.10.3 tag][tag]. Source links below are pinned to tags; the GitHub
repositories identify themselves as mirrors of connect2x's GitLab repositories.
The tagged README and timeline source were also retrieved from the official
GitHub mirror; web rendering of some GitLab pages failed, so source inspection
used published artifacts rather than assuming current-main behavior.

## Three independent decisions

Initial framing; the follow-up below records the user's subsequently reopened
UI-boundary preference and supersedes an unconditional SDK-free Compose rule.

1. Comcord-owned UI contracts are compatible with either SDK backing. Messenger
   explicitly separates view models from views and supports DI replacement of VM
   factories. A custom Server/Category/Channel layout is not evidence that its VMs
   cannot be used. [Tagged README][readme]
2. Choosing a VM backend is a reuse/behavior/lifecycle decision. A Comcord interface
   may adapt a Messenger VM, call client services, or mix both by feature.
3. State ownership is independent: AccountStore can own account/room selection
   and a room-session handle while a room-scoped timeline component owns events,
   paging and viewport behavior. Its backend may be Messenger or client Timeline.
   This decomposition is an **inference/recommendation**, not an SDK requirement.

## Capability inventory

| Responsibility | Messenger 4.10.3 provides | Direct client 5.9.2 already provides; Comcord glue still required |
| --- | --- | --- |
| Accounts and restoration | `MatrixClients` restores settings-listed accounts concurrently, exposes success/failure results, creates/removes/logs out clients and cleans account data. Settings persist selected account; account VM updates it. | `MatrixClient.create` restores account/device/authentication from supplied repositories; no need to invent token persistence or per-client restoration. Comcord must enumerate accounts, choose stores, manage clients and selection/failures/cleanup. |
| Authentication | Discovery, password, SSO, OAuth authorization-code/device authorization and registration VMs, routing, URL handling and UIA orchestration. Availability does not prove each flow is connected in Comcord. | API/auth-provider layer, authentication store, supplied auth-data creation/restoration and reauthentication. Comcord owns login UX, redirect/platform hooks, account orchestration and error presentation. |
| Sync and lifetime | Legacy Main VM starts regular sync after initial-sync routing, reacts to client membership/presence settings, stops on lifecycle stop and restarts on start; also wires verification/setup/notification permissions. Separate worker-based APIs exist. | `startSync`, `stopSync`, `cancelSync`, `syncOnce`, sync/login/initial-sync state and underlying sync machinery. Comcord owns when/which accounts run, host/background lifetime and user-facing recovery. |
| Persistence | Chooses per-account paths, repositories/media/crypto modules, database keys, encrypted SQLite configuration and settings storage; reports initialization errors. | Repository interfaces plus published Room/IndexedDB implementations; media-store modules; account/auth/crypto data and migrations. Comcord must assemble/configure platform modules and preserve compatibility with existing files/keys if replacing Messenger. |
| Platform secrets | Android encrypted preferences; iOS Keychain; JVM macOS Keychain/Windows credential store. JVM Linux platform provider returns null; web module installs no platform key provider. Database encryption defaults true on supported native/JVM repository path; web IndexedDB generates no database key. | Crypto/persistence abstractions do not automatically replace Messenger's platform key wrapping and path/settings policy. Security behavior must be specified per platform, not inferred from “SDK supports encryption.” |
| Encryption and verification | Self/device/user verification, account-setup and recovery/secret-storage presentation/routing; encrypted-wait/error timeline VMs. | Olm/Megolm room encryption, key/trust/secret services, key backup, device dehydration and verification request/state APIs. Removing Messenger means owning workflows and presentation, **not reimplementing Matrix cryptography**. |
| Notifications | Sysnotify presentation hooks, permission handling, providers and notification-sync orchestration; push providers require application/platform integration. | Notification streams/counts/read status, pending processing and push event handling. Comcord still supplies OS display, permissions, delivery/background hooks and navigation. |
| Media | Element VMs, upload/download/attachment orchestration, players/recording/platform helpers; native/JVM Okio media, web OPFS with IndexedDB fallback. | Cached/encrypted media download, thumbnails, prepare/upload encrypted/plain media and media-store implementations. Comcord owns controls, progress/errors, file pickers, playback and attachment UX as needed. |
| Timeline | Wraps client Timeline; maps event/subtype VMs, reconciles outbox, manages per-element lifecycles, receipts/read markers/unread state, jump/scroll/viewport coordination, input/header/attachment/report workflows. | Generic `Timeline<T>` with init/load-before/load-after/drop-before/drop-after and loading/boundary state; room-event observation, outbox and room/user services. Comcord must map events and implement only the higher-level behavior its product promises. |

Inventory sources: [MatrixClients][clients], [MatrixClientFactory][factory],
[AccountViewModel][account-vm], [MainViewModel][main-vm], [connecting VM directory][auth],
[platform repository setup][repos], [web repository setup][web-repos],
[Android secrets][android-secret], [iOS secrets][ios-secret], [JVM secrets][jvm-secret],
[web secrets][web-secret], [configuration defaults][config],
[client MatrixClient][client], [client services][services],
[notification service][notification], [Messenger notification directory][messenger-notification],
[media service][media], [web media selection][web-media], [timeline VM][timeline-vm],
[client Timeline][timeline]. These are source facts about pinned versions; there
was no end-to-end verification of these capabilities in this research task.

## Strongest case for adapting more Messenger VMs (B)

Current product scope matters: [issue #2](https://github.com/daphil19/comcord/issues/2)
requires multi-account retention, text sending/status/read state, encrypted
messaging, cross-signing and backup recovery. It excludes rich timeline features
(threads/reactions/edits/replies and rich media beyond basic display, unless
trivial through SDK reuse) and platform/push notifications from v1. Therefore
the strongest immediate B argument is reuse of sending/read/crypto workflows,
not an assumption that v1 must reproduce every Messenger feature. Notification
and richer-media inventory above describes potential future responsibility.

The timeline factory accepts a `MatrixClientViewModelContext`, room ID and callbacks
for navigation actions. It does not require Messenger to own Comcord's room
selection. `ViewModelContext` supports child contexts with an explicit lifecycle;
its coroutine scope is cancelled by instance-keeper destruction. Thus adapting
behind a Comcord room session is plausible. [Factory and implementation][timeline-vm],
[context lifecycle][context]

The default timeline behavior expects viewport feedback including visible and
loaded element keys, focus and finished-scroll acknowledgement. However,
`timelineViewStateSupported = false` explicitly enables manual paging/drop/read
methods, so a custom viewport is not automatically incompatible. The choice must
be made deliberately; manually calling these methods with the default enabled
throws. [Timeline API and implementation][timeline-vm], [default][config]

The benefit is substantive when replies, receipts, sending/outbox, encrypted-error
states, media and verification are near-term requirements. The cost is also
substantive: nested element VMs, Koin services, lifecycle contexts, settings,
input/header VMs and attachment/report routing come with the timeline. A wrapper
that merely copies all SDK structures loses much of the hoped-for isolation.
That last cost/benefit assessment is **inference** from the dependency graph,
not proof that B is too complex. Direct client integration has analogous behavior
costs, even if its initial plain-text interface is smaller.

## Fair option comparison (inference from inventory)

| Option | Retain / replace / duplicate | Prefer when | Strongest objection |
| --- | --- | --- | --- |
| A: selective Messenger | Retain account/bootstrap/storage/settings services and selected workflows; retain current direct client room/timeline adapter. Comcord implements timeline presentation behavior not borrowed. | Near-term product differs in timeline semantics or has narrow requirements; minimize migration now while evaluating additional VMs by feature. | Can pay for two orchestration models, retain hidden root side effects, and accidentally duplicate mature timeline behavior one feature at a time. |
| B: more Messenger VMs behind Comcord contracts | Retain A's infrastructure; replace selected hand-built behavior with adapted SDK VMs; keep Comcord vocabulary/navigation contracts. | Standard Matrix behavior is wanted soon and adapters can preserve Comcord semantics with explicit lifecycle/viewport ownership. | Mapping, nested VM lifecycle and SDK routing may overwhelm reuse; upgrade changes can reach many adapters and callback paths. |
| C: direct client | Retain Trixnity protocol, sync, crypto, stores, outbox, media and verification engines; replace Messenger account/platform/secret/workflow/presentation glue. | Comcord intentionally wants to own those workflows, has sustained capacity for all target platforms, and demonstrates a concrete VM constraint or lower total complexity. | A small timeline migration understates the account, recovery, secret storage and platform workload; data/key compatibility can make rollback harder. |

All three can keep Compose APIs Comcord-owned. A and B preserve the current
account/store format most directly. C need not discard stored data or force
logout in principle, but compatibility is **unknown until demonstrated**. No
source-only argument establishes an upgrade advantage: A has fewer VM contracts
but custom behavior; B reuses behavior but maps more contracts; C has fewer
Messenger dependencies but owns more code and platform policy. Contract tests
with a fake source remain useful under every option; adapter/integration tests
are required wherever real SDK behavior crosses the boundary.

## Unknowns and smallest experiments, only after product decisions

No experiment has been built or run.
The DTO-only B experiment below is historical initial framing; use the revised
direct-VM experiment in the follow-up section after the user's boundary change.

- **B fit/lifecycle:** adapt one timeline VM in an isolated throwaway fixture,
  with Comcord-owned DTOs and selection, an explicit child lifecycle, one chosen
  viewport mode and no Messenger room router. Success: initial messages,
  paging and incoming updates map to Comcord DTOs; room A→B cancellation and
  destruction leave no obsolete observers; no SDK types reach Compose and no
  competing room-selection owner is required. Fail if this requires duplicate
  selection or uncancellable hidden state. Start on JVM. Only if that passes,
  extend a second stage to outbox-to-confirmed identity and undecryptable
  placeholders, then validate affected target-platform behavior before claiming
  platform fit. Reply-jump and richer media are not prerequisites for this v1
  decision.
- **C cost/data compatibility:** if C becomes attractive, first create a client
  with the same repositories/media/crypto modules against a *copy* of a disposable
  Messenger-created account store. Success: same account/device restores,
  sync/decryption/media work after restart, and deleting the fixture leaves the
  original untouched. This tests compatibility, not a production migration.
- **Current host lifecycle:** trace root/host start-stop-destroy and account
  cancellation on each target. Success: sync follows the agreed foreground or
  background policy and switching UI does not accidentally stop other accounts.
  Source inspection shows lifecycle hooks, not correct host binding.
- **Platform/security coverage:** product priority and promised background/push,
  recovery and secret-protection behavior remain decisions. Published artifacts
  include Comcord's Android/JVM/iOS/JS/Wasm targets, but the tagged README calls
  its iOS implementation partly incomplete/experimental. Treat compilation as
  availability, not complete runtime/platform assurance. [Tagged README][readme]

## Provisional recommendation to test with the user

Historical initial recommendation: the follow-up below supersedes the blanket
Comcord-owned UI-contract prerequisite. Feature Compose may use SDK VMs directly.

Retain Messenger during this decision, retain Comcord-owned UI contracts, and
choose A versus B per feature based on required behavior rather than visual
layout. Prefer testing B before committing to own rich timeline workflows. Do
not choose C until its account/platform/workflow ownership is intentional and
its benefit survives the strongest B case. Timeline lifecycle should have one
explicit Comcord owner; whether that stays in AccountStore or becomes a separate
room session depends on desired retention, simultaneous rooms and account-switch
semantics. This is a recommendation, not an accepted ADR.

[pom]: https://repo.maven.apache.org/maven2/de/connect2x/trixnity/messenger/trixnity-messenger/4.10.3/trixnity-messenger-4.10.3.pom
[tag]: https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger/-/tags/v4.10.3
[readme]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/README.md
[clients]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/MatrixClients.kt
[factory]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/MatrixClientFactory.kt
[account-vm]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/viewmodel/roomlist/AccountViewModel.kt
[main-vm]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/viewmodel/MainViewModel.kt
[auth]: https://github.com/connect2x-de/trixnity-messenger/tree/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/viewmodel/connecting
[repos]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/jvmAndNativeMain/kotlin/de/connect2x/trixnity/messenger/CreateRepositoriesModule.jvmAndNative.kt
[web-repos]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/webMain/kotlin/de/connect2x/trixnity/messenger/CreateRepositoriesModule.web.kt
[android-secret]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/androidMain/kotlin/de/connect2x/trixnity/messenger/secrets/PlatformSecretByteArrayKeyProvider.android.kt
[ios-secret]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/iosMain/kotlin/de/connect2x/trixnity/messenger/secrets/PlatformSecretByteArrayKeyProvider.ios.kt
[jvm-secret]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/jvmMain/kotlin/de/connect2x/trixnity/messenger/secrets/PlatformSecretByteArrayKeyProvider.jvm.kt
[web-secret]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/webMain/kotlin/de/connect2x/trixnity/messenger/secrets/PlatformSecretByteArrayKeyProvider.web.kt
[config]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/MatrixMessengerConfiguration.kt
[client]: https://github.com/connect2x-de/trixnity/blob/v5.9.2/trixnity-client/src/commonMain/kotlin/de/connect2x/trixnity/client/MatrixClient.kt
[services]: https://github.com/connect2x-de/trixnity/tree/v5.9.2/trixnity-client/src/commonMain/kotlin/de/connect2x/trixnity/client
[notification]: https://github.com/connect2x-de/trixnity/blob/v5.9.2/trixnity-client/src/commonMain/kotlin/de/connect2x/trixnity/client/notification/NotificationService.kt
[messenger-notification]: https://github.com/connect2x-de/trixnity-messenger/tree/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification
[media]: https://github.com/connect2x-de/trixnity/blob/v5.9.2/trixnity-client/src/commonMain/kotlin/de/connect2x/trixnity/client/media/MediaService.kt
[web-media]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/webMain/kotlin/de/connect2x/trixnity/messenger/CreateMediaStore.web.kt
[timeline-vm]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/viewmodel/room/timeline/TimelineViewModel.kt
[timeline]: https://github.com/connect2x-de/trixnity/blob/v5.9.2/trixnity-client/src/commonMain/kotlin/de/connect2x/trixnity/client/room/Timeline.kt
[context]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/viewmodel/ViewModelContext.kt

## Follow-up: reopened UI boundary, license, and multi-account notifications

The user reopened the first decision: feature Compose code may consume useful
Messenger VMs directly, and source code may use Trixnity terminology, provided
visible UI preserves Comcord vocabulary. This supersedes the earlier assumption
that every experiment must eliminate SDK types from Compose. Comcord-owned
models remain useful for its custom Server/Category projections; they are not
an unconditional prerequisite for reusing SDK feature VMs. Licensing preference
and desired notification policy are unresolved product decisions.

### Exact license evidence, not a legal determination

- Messenger v4.10.3's [tagged LICENSE][messenger-license] is GNU Affero General
  Public License version 3; its [published POM][pom] also declares AGPL version 3.
  The inspected license is the standard AGPL text; no SDK-specific linking
  exception was identified in that file. This is not an exhaustive assertion
  about every file or possible commercial agreement.
- Trixnity v5.9.2's [tagged LICENSE][client-license] is Apache License version 2.0.
  The cached published `trixnity-client:5.9.2` POM declares Apache License 2.0.
  This establishes the client artifact's declared license, not the license of
  every optional crypto driver, native binary, or other transitive dependency.
- Messenger's [tagged README][readme] explicitly offers a commercial license
  and support via connect2x contact. Terms, price, applicability, and availability
  for Comcord have not been established.
- Comcord's current [LICENSE](../../LICENSE) is already AGPL version 3. That fact
  neither decides the user's desired future licensing policy nor demonstrates
  that removing one dependency would allow any particular relicensing outcome.

Architecture implication (**inference**): adapting Messenger behind DTOs does
not remove the dependency or its declared license; direct VM consumption does
not add a new Messenger dependency when it is already shipped. If excluding
AGPL dependencies is a firm project constraint, ordinary A/B arrangements do
not meet that dependency constraint. C or a verified alternative licensing
arrangement becomes relevant. Source inspection alone cannot settle legal
obligations or a relicensing decision.

### Multi-account sync: supported machinery, explicit host policy still needed

The legacy Main VM starts sync for *all* loaded clients after its initial-sync
route clears, observes new accounts, and stops/restarts all clients according to
its lifecycle. This is not limited to `selectedAccount`. Its separate worker-based
`SyncService` also follows lifecycle STARTED, waits for each client's initial sync,
and starts each client with its account's presence policy. The latter is an
alternative upstream architecture, not evidence that Comcord currently runs both.
[Legacy Main][main-vm], [worker service][sync-worker]

Thus keeping inactive accounts syncing does not require Comcord to write a Matrix
sync loop. C likewise retains client `startSync`/`syncOnce`; Comcord would own the
multi-client registry and foreground/background scheduling policy. Foreground
multi-account sync is distinct from OS push delivery or a suspended/terminated
app continuing to work. Host lifecycle wiring remains unverified in Comcord.

### Notifications: what actually runs and what it does not establish

`NotificationSyncService` is registered as a worker and Messenger starts registered
workers. It combines every loaded account's notification settings and provider
enabled state, then collects each account's client notification updates. Handlers
are per account, with profile/account in the handler ID and account identity in
the handler name. This is meaningful existing multi-account reuse.
[Notification worker][notification-sync], [handler implementation][handlers],
[module wiring][notification-module], [Messenger startup][messenger-start]

When no concrete provider is registered, `NotificationProviders` supplies a
`NoOpNotificationProvider`. Despite its name this enables/disables foreground
notification *presentation*: its account setting defaults **false**, and enabling
it gates the worker's system display. It does not create a push delivery service.
When disabled, the worker still consumes updates but does not display them.
Comcord registers no concrete provider and exposes no notification settings UI
in the inspected checkout; current/default configuration is not a demonstrated
working notification feature. Existing persisted settings could differ from the
fresh default. [Providers][providers], [NoOp implementation][noop-provider],
[checkout inventory](2026-10-26-comcord-boundary-checkout.md)

The notification worker has **no active-room, focus, or Comcord-selection input**.
It displays/removes notifications based on client updates and notification
settings. Client read/dismiss behavior can affect subsequent updates, but that
is not proof of race-free suppression for a currently focused conversation.
A hidden Main VM does not synchronize its own selection with AccountStore, and
does not by itself provide Comcord's desired foreground suppression policy.
[Worker constructor and dispatch][notification-sync],
[client notification state/read/dismiss APIs][notification]

Detailed notification callback data is `appUri` plus a Matrix room URI; anonymous
notifications use `appUri` alone. **No account ID is encoded in this callback.**
Legacy `RoomListViewModel` listens to the URI handler, extracts a room ID, resolves
that through its room map and calls Messenger navigation. This may select a
Messenger room while Comcord's visible shell retains its own selection. Correct
routing when two accounts share a room is not established by this source path.
The host URI callback must also be connected. Comcord's inspected entrypoints do
not show that integration. [Callback construction][notification-sync],
[legacy URI navigation][room-list], [URI handler][uri-handler]

Platform handler factories provide Sysnotify integration; Android supplies
context/activity/channel and an activation ACTION_VIEW intent, iOS requests
remote registration after permission is granted, JVM/web use their generic
handlers. These hooks are not proof of a delivered APNs/FCM/browser push service,
OS protocol registration, or correct account switching on click.
[Android factory][android-handlers], [iOS factory][ios-handlers],
[JVM factory][jvm-handlers], [web factory][web-handlers]

`PushNotificationProvider` is an **abstract** integration base. It supports
profile/account-aware `onPush` and pending processing, account-specific pusher
settings, and `append = true` when registering pushers so accounts can share a
push key. A subclass must supply service enable/disable, pusher settings and
custom fields; the host must call delivery/pending hooks. This is useful reuse,
not a complete configured push service. Account-aware inbound push targeting is
separate from the room-only outbound click callback described above.
[Push provider][push-provider]

Direct client already exposes notification streams/counts/read state/dismissal,
`onPush`/`processPending`, `syncOnce`, and API pusher operations. C need not
reimplement push-rule evaluation or decryption; it does require Comcord to supply
account routing, OS display/permissions/activation, service registration,
background execution and platform payload integration. A/B can retain
Messenger's machinery but still require fixes or extensions for Comcord's
selection/click policy. These are architecture cost inferences from the APIs,
not claims that C is cheaper or that Messenger cannot be adapted.

### Revised smallest proposed experiments

No experiment has been built. If B's fit is decision-critical, create one
throwaway room timeline using its actual Messenger VM in feature Compose.
Preserve visible Comcord vocabulary and custom shell selection. Choose one
viewport mode. Success: initial/paged/incoming messages are correct, room A→B
cancels obsolete work, destruction leaves no observers, and selection has one
owner. SDK types in the feature UI are permitted; duplication or loss of product
behavior is the failure condition. Only then extend to send/outbox and encrypted
placeholder behavior.

If notification policy becomes in scope, the smallest useful *foreground*
experiment is two disposable accounts sharing one room, both actively syncing,
with an injected recording notification-handler factory. Explicitly enable the
NoOp provider. Given an agreed focus policy, success: updates are attributed to
the receiving account, focused-room suppression follows that policy, and clicking
an inactive-account notification activates the correct Comcord account and room
without relying on a competing hidden selection. A failure at callback routing
would quantify the extension needed. This does **not** test push or background
delivery; those require a separate platform/service experiment once promised
behavior is chosen. Notifications remain outside issue #2 v1 unless scope is
changed deliberately.

[messenger-license]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/LICENSE
[client-license]: https://github.com/connect2x-de/trixnity/blob/v5.9.2/LICENSE
[sync-worker]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/internal/workers/SyncService.kt
[notification-sync]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationSyncService.kt
[handlers]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationHandlers.kt
[notification-module]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification/notificationModule.kt
[messenger-start]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/MatrixMessenger.kt
[providers]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationProviders.kt
[noop-provider]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification/NoOpNotificationProvider.kt
[room-list]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/viewmodel/roomlist/RoomListViewModel.kt
[uri-handler]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/util/UriHandler.kt
[android-handlers]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/androidMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationHandlers.android.kt
[ios-handlers]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/iosMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationHandlers.ios.kt
[jvm-handlers]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/jvmMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationHandlers.jvm.kt
[web-handlers]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/webMain/kotlin/de/connect2x/trixnity/messenger/notification/NotificationHandlers.web.kt
[push-provider]: https://github.com/connect2x-de/trixnity-messenger/blob/v4.10.3/trixnity-messenger/src/commonMain/kotlin/de/connect2x/trixnity/messenger/notification/PushNotificationProvider.kt

## Agreed constraints from the interview

- AGPL is acceptable if Messenger provides substantial reusable behavior. The
  concern is retaining its licensing constraint while duplicating its value;
  permissive licensing is not a hard requirement.
- Visible Comcord terminology is required. Source-level Trixnity names and
  direct consumption of useful Messenger feature VMs are acceptable; translating
  every SDK field into an owned DTO is not a requirement.
- All signed-in Accounts may sync while the application is foregrounded.
  Notification delivery stays outside this build round and needs account-aware
  routing/suppression checks before enablement. Background/push is separate.
- Messenger-first reuse is agreed: feature UI may consume useful SDK VMs
  directly; Messenger behavior is the default without an explicit Comcord
  requirement. Broader adoption is gated on the timeline experiment; substantial
  duplicate orchestration reopens client-only integration.
- The accepted decision, ownership policy, consequences and staged work are
  recorded in [ADR 0005](../adr/0005-messenger-first-matrix-workflows.md).
  Earlier provisional recommendations and capability snapshots above remain
  research history. Application changes and experiments remain unstarted.
