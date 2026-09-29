# Architecture Decision Records

Why Rolabox is built the way it is. Each record captures one decision: its context, the
alternatives considered, the trade-offs accepted, and the rules that follow from it. How ADRs are
written, reviewed and replaced is itself a decision, recorded in ADR-000.

New ADR: copy [`template.md`](template.md), take the next number, and add it to this table.

| ADR | Title | Status |
| --- | --- | --- |
| [000](000-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [001](001-layered-architecture.md) | Layered architecture with a domain layer that owns the repository interfaces | Accepted |
| [002](002-offline-first-data-flow.md) | Offline-first data flow with the local database as the single source of truth | Accepted |
| [003](003-module-boundaries.md) | Module boundaries and dependency rules | Accepted |
| [004](004-convention-plugins.md) | Share build configuration through convention plugins | Accepted |
| [005](005-hilt-dependency-injection.md) | Use Hilt for dependency injection | Accepted |
| [006](006-async-api-shape.md) | Async API shape: `Flow` for observed state, `suspend` for single operations | Accepted |
| [007](007-error-handling.md) | Typed errors with Arrow `Either` for every fallible operation | Accepted |
| [008](008-one-app-with-offline-mode.md) | One app with Firebase built in, and offline mode as a user choice | Accepted |
| [009](009-ui-bound-sdks.md) | SDK steps that need an Activity live in the UI, and only their results cross the `:api` | Accepted |
| [010](010-firestore-security-rules.md) | Firestore for synced data, with per-user security rules tested against the emulator | Accepted |
| [011](011-preferences-sync.md) | Sync preferences through Firestore, field by field, with last-write-wins | Accepted |
| [012](012-navigation.md) | Navigation with Jetpack Navigation 3, with the back stacks owned by `:app` | Accepted |
| [013](013-account-linking.md) | One account per email, with Google and password sign-in linked automatically | Accepted |
| [014](014-area-ui-modules.md) | Area UI modules for SDK steps and shared UI, `:core:<area>:ui` | Accepted |
