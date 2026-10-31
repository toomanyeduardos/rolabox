# ADR-025: Parts run work at process start through a set in `:common:util`, which `:app` runs

- **Status:** Accepted
- **Date:** 2026-10-30
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

[ADR-024](024-library-index.md) scans the library at each start of the app's process. Something has
to call the library then, and only `Application.onCreate` runs at that moment. `RolaboxApplication`
is in `:app`, and [ADR-020](020-modules-by-product-area.md)'s rule 13 lets `:app`'s code use only
`:auth:data:api`, `:auth:ui:api`, `:device:ui:api` and `:common`. So it can't name
`LibraryRepository`, in `:device:library:api`. `SyncRepository` is called from there because it is
in `:common`. The library is the first part outside `:common` to need this, and it won't be the
last: playback/05 restores the queue after a process dies.

## Decision

We will declare `ProcessStartTask`, a one-function interface, in `:common:util`, with an empty Hilt
set of them. A part adds its task to the set from its `:impl`. `RolaboxApplication` injects the set,
and calls each task in `onCreate`, knowing nothing of what is in it.

A task runs on the main thread before anything is shown. So it only starts work in the application
scope ([ADR-006](006-async-api-shape.md), rule 6), and never waits for it. The order of the set
isn't defined, so no task relies on another having run.

## Alternatives considered

- **`:app` calls `LibraryRepository` directly.** The simplest, and like the call to sync. But it
  breaks ADR-020's rule 13, which the build enforces, and `:app` would grow by a dependency for each
  part that needs it.
- **The device's ViewModel calls it.** Nothing new, and library/04 needs that call site anyway. But a
  process started by the media service, or one waiting on sign-in, wouldn't scan, which isn't what
  ADR-024 says.
- **The repository starts when Hilt creates it.** `:app` gains nothing. But when it runs depends on
  who injects it first, and a side effect of injection is hard to see or test.
- **`androidx.startup`.** The same result, with an `Initializer` in each part's manifest and no code
  in `:app`. But it is a new dependency, and its initializers sit outside Hilt's graph, so they would
  have to reach into it through an entry point.

## Consequences

- A part can run work at process start without `:app` knowing of it, and ADR-020's rule 13 holds.
- What runs at start isn't visible in `RolaboxApplication`. Searching for `ProcessStartTask` finds it.
- Everything a task injects is created during `onCreate`, so its dependencies have to be cheap to
  create. The library's are: Room opens its database at the first query.
- Tests that replace a part's Hilt module drop its task with it, so they don't start its work.

## Rules

1. `[convention]` Work a part outside `:common` does at each start of the process is a
   `ProcessStartTask` added to the set from its `:impl`. `:app` only runs the set.
2. `[convention]` A task doesn't block and doesn't wait: it starts work in the application scope. It
   doesn't rely on another task having run.

**Conformance.** Both rules are reviewed. That `:app` can't call a part directly is ADR-020's
rule 13, which `ModuleRules.kt` enforces.
