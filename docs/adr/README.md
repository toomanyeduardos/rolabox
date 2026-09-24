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
| [008](008-offline-and-cloud-flavors.md) | Offline and cloud build flavors, with Firebase only in cloud | Accepted |
