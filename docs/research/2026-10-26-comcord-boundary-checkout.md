# Comcord boundary reassessment: checkout evidence

Date: 2026-10-26. Checkout snapshot taken before the decision. The resulting
accepted direction is recorded in [ADR 0005](../adr/0005-messenger-first-matrix-workflows.md);
application code remains unchanged.

## Verified baseline

- Branch `codex/6-channel-timeline`, HEAD `c46a5e6c0e32947646eb4334d4329dce1fa311cd`.
- PR [19](https://github.com/daphil19/comcord/pull/19) is open against `comcord-vibed-take2`; [ticket 6](https://github.com/daphil19/comcord/issues/6) is open. Its acceptance text explicitly asks for Comcord models from the shell store rather than SDK timeline view models. That requirement explains the current choice; it does not establish its architectural superiority.
- `gradle/libs.versions.toml` declares Messenger **4.10.3**, Ktor **3.5.2**, Kotlin **2.4.21**, Compose Multiplatform **1.12.2**, Decompose **3.5.0**, wrappers **2026.6.1**. `settings.gradle.kts` agrees on Ktor. Published/cached Messenger metadata uses Trixnity **5.9.2**, a different version line.
- Targets: Android; JVM desktop; JS browser; Wasm browser; iOS ARM64 and ARM64 simulator. There are only two Gradle modules: `composeApp` and `androidApp`; no separate timeline module exists.
- ADRs [0001](../adr/0001-shared-compose-shell.md), [0002](../adr/0002-trixnity-boundary.md), [0003](../adr/0003-discord-vocabulary-for-matrix-concepts.md), [0004](../adr/0004-discord-layout-supersedes-cinny-target.md) are accepted. ADR 0002 permits SDK startup primitives and calls for owned models wherever practical; it does not mandate avoiding every Messenger view model behind those models.
- [GLOSSARY.md](../../GLOSSARY.md) requires several signed-in Accounts but one Active Account replacing the whole client. Parent issue [2](https://github.com/daphil19/comcord/issues/2) and [12](https://github.com/daphil19/comcord/issues/12) already require per-Account selection, drafts, collapsed Categories and hidden Views to survive switching, with signed-in/Active Accounts surviving restart. They do not settle inactive-account sync/notifications or timeline scroll restoration.

## Current integration, not dependency capability

| Concern | Current checkout evidence | Limit of the evidence |
| --- | --- | --- |
| Startup and restoration | All four entrypoints create `MatrixMultiMessenger`, use `singleModeMatrixMessenger().first()`, and call `createRoot()`. App observes the root router. | Restoration is delegated; no restart restoration test inspected. Single messenger mode is not proof of a single Matrix account. |
| Authentication | `App.kt` renders homeserver discovery and password login using Messenger VMs. | OAuth2, SSO, registration and initialization failures render status strings; the root UIA stack is not rendered. Available routes are not completed Comcord flows. |
| Accounts | `TrixnityMatrixSource.kt:accountSources()` chooses `entries.firstOrNull()` from `MatrixClients`; logout delegates to `clients.logout(userId)`. | No account switcher UI or explicit Active Account policy. First entry is a bootstrap policy, not the glossary contract. |
| Sync and lifecycle | Messenger root creates and starts its Main VM even though App replaces its visible screen. The Main VM starts sync and other observers. | Entrypoints call default `createRoot()` without a supplied host ComponentContext; host pause/resume/background correctness is not established by source wiring or compilation. |
| Settings and storage | Comcord registers platform settings-holder DI. Hidden View IDs persist under a namespaced Messenger account-settings JSON key. Messenger owns client construction/storage. | No settings screen. OS keychain/device security guarantees require platform review; smoke fixture deliberately replaces secret handlers. |
| Verification and account setup | Started SDK Main VM observes requests and owns setup/verification router stacks. | App does not render those stacks. No working recovery/verification interaction is established. |
| Notifications | SDK Main starts its permission-request observer. | No Comcord permission/delivery/click flow was found or tested. Dependency defaults are not a working product feature. |
| Media | Avatar loading uses base client's bounded thumbnail API. | Timeline models carry only body/name/avatar/time; no attachment presentation or playback flow. |
| Timeline | `TrixnityRoomTimeline` uses base `client.room.getTimeline`, initializes around last event, loads before/after, maps `RoomMessageEventContent.body` and sender data. | This is a body projection, including bodies from non-text room messages. It omits dedicated edit/reaction/redaction/undecryptable/outbox/receipt/thread/media presentation. Do not infer underlying client lacks those capabilities. |

Repository source paths above are under `composeApp/src/commonMain/kotlin/dev/phillipslabs/comcord/` unless stated otherwise. Entrypoints are in platform source sets and `androidApp/src/main`.

## Ownership today

Compose's `ActiveAccountScreen` creates `AccountStore` with `rememberCoroutineScope()` under `key(accountSource)`. The caller owns and cancels that scope when the Account UI leaves. `AccountStore` owns selection, timeline UI state, retry and load/error policy. Its `collectLatest` creates a room coroutine scope and cancels observation/pending paging when selection changes. `MatrixSource.timeline(roomId)` supplies the SDK-free `RoomTimeline` interface. The adapter owns the base SDK Timeline instance and SDK-to-Comcord mapping. Compose owns `LazyListState`; scroll anchoring and restoration are therefore separate from store data ownership.

All of this is in `composeApp/commonMain`. A future room-scoped timeline store can be a class/package within that module; lifecycle ownership does not require introducing another Gradle module.

## Validation already present versus this research

Seven common timeline tests cover selection, paging, incoming events, late updates/DM switching, cancellation of pending history, leaving rooms and retry. The JVM smoke test uses isolated Synapse and Messenger storage, authenticates via base password API, explicitly starts sync (no Main VM in the fixture), checks directory/timeline/settings persistence and logout. It does not validate Comcord's rendered login, host lifecycle, restart restoration, verification UI or platform notifications. PR 19 reports prior `buildDev` and smoke success; those are historical author evidence, not rerun results in this research session.

No Gradle build or runtime experiment was run for this documentation exercise. Existing user untracked files were left untouched.

## Decision structure

1. UI contract: whether shared feature Compose APIs consume Comcord models. Independent of backend selection.
2. Backend by capability: retain current selective integration (A), adapt more Messenger VMs (B), or own application orchestration over base client (C). A and B retain the same SDK stack; C retains Matrix machinery supplied by the base client.
3. State/lifetime: AccountStore selection authority; account-scoped versus room-scoped presentation sessions; persistence across room/account switches; foreground/background sync authority. Decide before assigning more lifecycle work to any VM or store.

Provisional inference: current code provides no evidence strong enough to justify wholesale Messenger removal. It also provides no evidence that blanket avoidance of Messenger feature VMs minimizes work. Judge reuse by concrete behavior and lifecycle ownership, not custom layout.

## Existing product scope from tickets

Parent [2](https://github.com/daphil19/comcord/issues/2) prioritizes desktop while retaining the shared shell on web/Android/iOS. [7](https://github.com/daphil19/comcord/issues/7) requires optimistic sending, failure/retry and per-room/per-Account drafts; [8](https://github.com/daphil19/comcord/issues/8) requires unread/mention aggregation and marking an opened Channel read. [14](https://github.com/daphil19/comcord/issues/14) and [15](https://github.com/daphil19/comcord/issues/15) require encryption, undecryptable placeholders, cross-signing verification and recovery-key/passphrase backup restore. These are requirements, not current integration claims.

Issue 2 currently excludes threads/reactions/edits/replies/rich media beyond basic display unless trivial SDK reuse, plus push/platform notification integration. They remain relevant ownership costs when comparing dependencies, but must not be silently promoted to v1 requirements. It specifies account-owned shell stores keeping inactive account state; the current UI-scoped store does not yet implement that future lifetime. The architecture decision can reaffirm or revise this explicitly.
