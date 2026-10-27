# ADR 0005: Messenger-First Matrix Workflows

## Status

Accepted. Supersedes [ADR 0002](0002-trixnity-boundary.md).
Implementation and experiments are separate work and have not started.

## Decision

Comcord will retain `trixnity-messenger` and use its existing view models and
workflows as the default for standard Matrix behavior. Feature composables may
consume Messenger view models directly. Comcord-owned copies of their state,
interfaces, and field names are not required merely to hide SDK types.

Comcord continues to own its community projections, Account switching policy,
navigation, and visible Server, Category, Channel, and DM vocabulary. Where
Comcord has no explicit product requirement, Messenger behavior is the default.
Overrides require a concrete user need. A custom layout alone does not justify
reimplementing an SDK workflow.

Messenger must earn its place through substantial reuse of complete workflows,
particularly timeline/composer, authentication, verification, and recovery.
Retaining it primarily for bootstrap infrastructure while duplicating its
view-model layer is not the intended architecture. AGPL is acceptable when that
reuse delivers value; permissive licensing is not a hard requirement.

Broader adoption is gated on the timeline experiment below. If integration
requires substantial duplicate orchestration, reopen direct `trixnity-client`
integration rather than preserving Messenger by default.

## Context and evidence

ADR 0002 protected product flexibility by introducing Comcord-owned stores and
presentation models. Ticket #6 and PR #19 followed that direction: `AccountStore`
owns selection and timeline presentation state, and an adapter maps the base
client Timeline into Comcord models. Messenger still supplies account/client
construction, restoration, authentication routing, settings, and storage glue.

Reassessment separates three decisions: the types consumed by Compose, the
backend supplying behavior, and the lifetime of state. Comcord vocabulary does
not require Comcord-owned types for every SDK feature. Messenger's timeline VM
wraps the same base Timeline while adding outbox handling, read/viewport behavior
and element lifecycles; its factory accepts a room and callbacks. Reuse with
Comcord navigation is plausible, but not yet demonstrated.

The inspected baseline is Messenger 4.10.3 with Trixnity client 5.9.2. Messenger
is AGPLv3, while base Trixnity is Apache-2.0; Comcord's current LICENSE is already
AGPLv3. The license preference is therefore a deliberate product choice, not an
assumption that the dependency is cost-free.

See the [checkout inventory](../research/2026-10-26-comcord-boundary-checkout.md)
and [pinned official-source research](../research/2026-10-26-trixnity-boundary.md)
for capability inventory, source citations, version identity and limitations.
Available dependency functionality is not proof of a working Comcord integration.

## State and lifecycle ownership

- Account-level Comcord state owns the Active Account, selected Server/View and
  room, and retained navigation preferences. Existing requirements for per-room,
  per-Account drafts and restoration remain; reuse SDK persistence where it fits.
- A room-scoped timeline module within `composeApp/commonMain` owns live timeline
  behavior, normally using Messenger's timeline/composer VMs. Account selection
  creates, activates or disposes that room scope. Do not maintain a second copy
  of messages, paging, errors or send state in `AccountStore` solely for wrapping
  the SDK. AccountStore may expose the selected session without owning its internals.
- Use one selection authority and explicit SDK lifecycle binding. Leaving a room
  must stop obsolete work unless a deliberate cache policy retains it. Keeping
  drafts/navigation does not imply keeping live timelines. Exact scroll/history
  retention and class/package structure remain implementation decisions; no new
  Gradle module is required.
- All signed-in Accounts may sync while the application is foregrounded. Account
  switching changes the visible UI without stopping other Accounts' sync.
  Background execution and push delivery are separate platform decisions.

## Alternatives and consequences

**A: Retain current selective use.** This minimizes immediate disruption and can
be appropriate for features whose semantics differ substantially. It retains
valuable account/storage infrastructure, but risks maintaining two orchestration
models and duplicating Messenger behavior incrementally. It is not the default
direction for standard Matrix workflows.

**B: Reuse more Messenger VMs. Chosen.** Direct feature consumption avoids
mechanical DTO mapping and gains existing workflows. Custom Comcord projections
and policy still warrant owned models and tests. The cost is closer coupling to
SDK VM contracts, lifecycle contexts and callbacks. Upgrades need integration
verification; less owned code does not guarantee easier upgrades.

**C: Build on trixnity-client.** This remains a credible alternative if the reuse
gate fails. The client already supplies protocol APIs, sync, repositories, crypto,
verification/key services, outbox and media machinery. Comcord would own more
account orchestration, platform storage/secret configuration, login/recovery UX,
notification integration and feature presentation. Existing account/device/data
compatibility must be demonstrated rather than assuming either forced logout
or automatic compatibility.

Custom shell behavior remains testable with fakes. SDK-owned timeline/workflow
behavior should be verified through integration scenarios, not duplicated in a
Comcord store solely to preserve the previous testing pattern. This revises the
blanket SDK-free presentation/source expectations in ticket #2 and the timeline
backend restriction in ticket #6. Update affected implementation guidance and
ticket plans when separately authorized implementation begins; historical ticket
text does not establish that the existing backend is architecturally preferable.

## Adoption gate and staged implementation outline

These are future work, not a migration performed by this ADR.

1. Build an isolated JVM timeline fixture using the actual Messenger VM in
   feature Compose, Comcord shell selection, one explicit room lifecycle and one
   viewport mode. Pass when initial loading, paging and incoming events work;
   switching A to B stops obsolete work; destruction leaves no observers; and
   selection has one authority with no duplicated timeline state. Source-level
   SDK types are permitted. If substantial competing navigation or orchestration
   is necessary, stop and reassess B against C.
2. Extend the passing fixture to sending/outbox reconciliation and undecryptable
   placeholders. Confirm useful behavior beyond today's body-only timeline, then
   validate affected Android, JS, Wasm and iOS runtime/lifecycle behavior before
   claiming cross-platform fit. Compilation alone does not satisfy this gate.
3. Adopt successful timeline/composer reuse incrementally, removing replaced
   Comcord behavior. Connect authentication, verification and recovery workflows
   end to end. Preserve explicit Comcord requirements and account state; document
   concrete exceptions rather than adding blanket wrappers. Keep changes scoped
   to separate implementation tickets and their relevant verification.
4. Before enabling notifications, validate two Accounts sharing a room: attribute
   alerts to the receiving Account, apply the agreed focus/suppression policy,
   and activate the correct Account and room on click. Notification delivery
   remains outside the current build round. Push/background delivery needs its
   own platform/service plan.

## Remaining uncertainties

- VM reuse and lifetime ergonomics are supported by source inspection but not
  proven by a Comcord fixture. The acceptance gate may change the backend choice.
- Current host lifecycle binding is unverified. Published target support and
  prior compilation do not prove pause/resume, recovery or background behavior.
- Messenger's notification infrastructure processes multiple Accounts, but its
  built-in click URI lacks Account identity and Comcord's selection/focus policy
  is not integrated. The size of that extension remains unknown.
- Secret protection differs by platform; native/JVM defaults must not be
  generalized to Linux/web. Existing-store/key compatibility, full recovery flows
  and platform security guarantees require targeted validation as those features
  are implemented.
- Threads, reactions, edits, replies and rich media beyond basic display remain
  outside this build round unless trivial through SDK reuse. Future capability
  does not silently expand the current scope.
