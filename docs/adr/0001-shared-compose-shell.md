# ADR 0001: Shared Compose Shell

## Status

Accepted

## Decision

Comcord will keep the primary application shell in shared Compose Multiplatform code.

## Context

The existing project already ships Android, iOS, JVM desktop, and web targets from one `composeApp` module. The current shell in `commonMain` is still small enough to refactor before platform-specific behavior leaks into feature code.

## Consequences

- shared navigation, theme, and shell layout live in `commonMain`
- platform source sets stay focused on lifecycle, notifications, media, deep links, and host integration
- future exceptions require an ADR rather than ad hoc UI forks
