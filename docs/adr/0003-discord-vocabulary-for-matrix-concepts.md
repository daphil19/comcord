# ADR 0003: Discord Vocabulary for Matrix Concepts

## Status

Accepted

## Decision

The UI uses Discord's words, not Matrix's. A top-level joined space is a **Server**, a first-level subspace is a **Category**, and a non-DM room is a **Channel**. The Matrix host is always called the **Homeserver**, never just "server". `GLOSSARY.md` holds the canonical definitions.

## Context

Comcord exists to make Discord communities moving to Matrix feel at home, and familiar words matter as much as familiar layout. We considered "Space / Channel" and "Space / Room". Both avoid a clash with Matrix's own meaning of "server" (the homeserver, which shows up at login and in user IDs like `@alice:example.org`). We chose familiarity anyway and handle the clash with a strict wording rule.

## Consequences

- UI copy must say "homeserver" wherever it means the Matrix host: login, account details, error messages, user IDs.
- Code should use the glossary terms for Comcord-owned models. Matrix SDK types keep their own names at the integration boundary (see ADR 0002).
- The old `Guild*` names in `ComcordRoomDirectory.kt` should be renamed to the glossary terms when that code is reworked.
