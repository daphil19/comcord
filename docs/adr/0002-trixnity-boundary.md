# ADR 0002: Trixnity Boundary

## Status

Accepted

## Decision

Comcord will keep `trixnity-messenger` as the Matrix integration backbone, but shared UI should depend on Comcord-owned presentation models and stores wherever practical.

## Context

The current UI directly consumes `RootViewModel`, `AddMatrixAccountViewModel`, `PasswordLoginViewModel`, and `MatrixClients`. That is acceptable for the bootstrap slice, but it makes later UX work harder because SDK contracts become UI contracts.

## Consequences

- startup may still use SDK primitives directly while the shared architecture is being split out
- room selection, shell state, and later timeline models should move behind Comcord-owned abstractions
- new feature modules should avoid leaking SDK types into composable APIs unless there is a clear short-term need
