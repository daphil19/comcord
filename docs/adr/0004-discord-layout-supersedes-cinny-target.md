# ADR 0004: Discord Layout Replaces the Cinny-Inspired Target

## Status

Accepted. Replaces the Cinny-inspired target from the earlier `comcord-vibed` branch, where the old layout note (`docs/ux/cinny-inspired-layout.md`) and roadmap plans are archived.

## Decision

Discord's structure and navigation are now the reference for Comcord's shell. Cinny's publicly observable behaviour is used only to fill gaps Discord has no answer for, mainly nested spaces. Structural choices that follow from this:

- The Rail lists Views (Direct Messages, All Rooms, Orphan Rooms), all visible by default and each hideable, followed by Servers.
- Inside a Server, uncategorized Channels come first with no header, then one Category per first-level subspace. Channels from deeper subspaces are folded into their Category.
- A space's `m.space.child` events decide which Channels and Categories it contains, as the Matrix spec intends. A room's own `m.space.parent` claim is not used to decide membership.
- Joinable Channels are listed in their Server. Opening one shows a Channel Preview with an explicit Join action instead of joining straight away. If the user isn't allowed to join, the Join action is shown disabled with a short explanation (for example, ask an admin for access); this is a low-priority edge case.
- The Member List groups people by Role, using each Channel's own power levels. Comcord doesn't flag differences from the Server space's power levels.
- The User Panel at the bottom-left holds the account switcher, presence, and settings.

## Context

The project used to target a Cinny-inspired look. The product goal is now explicitly "feels like Discord" for migrating communities. Cinny already handles Matrix-specific layouts (nested spaces, rail folders, categorized subspaces) well, so it stays a secondary reference. Clean-room rules still apply to both: nothing is copied from Discord or Cinny, whether code, assets, icons, or exact visual trade dress.

## Consequences

- The current projection in `ComcordRoomDirectory.kt`, which relies on `m.space.parent`, has to be replaced.
- Showing Joinable Channels requires the space hierarchy API, plus handling for rooms that can't be previewed or joined.
- Voice channels (via MatrixRTC / Element Call) are deliberately left out of the current build round, but they are a required future feature, not an optional one.
- Rail folders, and treating deeper subspaces as more than folded Categories, are possible later additions.
