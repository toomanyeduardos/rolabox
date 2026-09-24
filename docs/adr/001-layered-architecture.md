# ADR-001: Layered architecture with a domain layer that owns the repository interfaces

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox will grow several data sources: the device's music library, local user data (favorites,
play history, preferences), and an optional remote backend. Screens will increasingly combine
them. For example, an artist page may show songs, albums and other related content that come from
different repositories.

We want three things from the structure:

- UI code never sees storage details (Room entities, network DTOs, DataStore keys).
- Business rules can be tested on the JVM, without Android.
- The boundary between layers is a module dependency the build can check, not a naming convention.

Google's [guide to app architecture](https://developer.android.com/topic/architecture) describes a
UI layer, a data layer and an *optional* domain layer. Now in Android follows it: features depend on
the data layer directly, and use cases exist only where they add something.

## Decision

We will use three layers. Dependencies point inward, toward the domain.

```
UI (:feature:*)  ──▶  domain (:core:domain)  ◀──  data (:core:data and other data modules)
                              │
                              ▼
                        :core:model
```

**Domain layer (`:core:domain`, pure Kotlin/JVM).**
- It owns the **repository interfaces** that features and use cases consume, plus any use cases.
- It depends only on `:core:model` and `:core:common`.
- It has no Android dependencies, so everything in it is tested on the JVM.

**Data layer (`:core:data` and the modules behind it, such as `:core:datastore` and `:core:auth`).**
- It **implements** the domain's repository interfaces (dependency inversion).
- Storage and transport types (Room entities, DTOs) stay internal to the data layer and are
  mapped to `:core:model` types at its boundary.

**UI layer (`:feature:*`).**
- Compose screens and ViewModels. Features depend on `:core:domain`, never on data-layer modules.
- ViewModels expose UI state as a `StateFlow` and receive user actions as function calls
  (unidirectional data flow). How UI state is shaped in detail is left to a future ADR.

**Use cases are optional.** A ViewModel may inject a repository interface directly. Create a use
case only when at least one of these is true:

1. it combines data from **more than one repository**;
2. the same logic is needed by **more than one ViewModel**;
3. it holds a **business rule** that isn't presentation logic (for example, shuffle and queue
   rules, or matching remote favorites to local tracks).

A use case that only passes a call through to one repository is not created. When a use case
exists for an operation, ViewModels go through it and don't call the repository directly for that
operation, so the logic never ends up in two places.

## Alternatives considered

- **Optional domain layer, features depend on the data layer (Google's guidance, Now in Android).**
  Simpler, with one fewer module, and a good fit for many apps this size. We rejected it because
  repository interfaces would sit next to their implementations. Features would then compile
  against the data module, so nothing stops a Room or DataStore type from reaching the UI, and the
  boundary depends on discipline instead of the build.
- **Required domain layer, every ViewModel call goes through a use case (strict Clean
  Architecture).** One uniform rule, but it produces many use cases that only pass calls through.
  They add files and indirection without adding behavior, and reviewers learn to skim them, so the
  meaningful ones get skimmed too.
- **Aggregating in the ViewModel.** Letting ViewModels combine several repositories themselves is
  the shortest path, but the combining logic gets copied to every screen that needs it, and it
  can't be tested without the presentation layer.

## Consequences

- Features compile only against interfaces and models, so the UI can't reach storage types by
  mistake, and fakes for every repository fit naturally in `:core:testing`.
- Business rules live in a JVM module, and their tests run without Robolectric or a device.
- Every repository interface lives in one module, away from its implementation, which adds some
  navigation cost when reading code.
- "When to create a use case" is a judgment call guided by the three criteria above, so it needs
  attention in review.
- Aggregating in a use case is for crossing repository boundaries. When the data lives in the same
  local database, a single query in the data layer is preferred over several repository calls
  joined in memory.

## Rules

1. `[enforced]` Feature modules depend on `:core:domain` and never on data-layer modules
   (`:core:data`, `:core:datastore`, `:core:auth`, `:core:sync`, and future ones such as
   `:core:database`).
2. `[enforced]` `:core:domain` is a JVM module with no Android dependencies, and depends only on
   `:core:model` and `:core:common`.
3. `[convention]` Every repository interface that a feature or use case consumes is declared in
   `:core:domain`, and implemented in the data layer.
4. `[convention]` Room entities, DTOs and other storage types never appear in a public signature
   outside the data layer.
5. `[convention]` A use case exists only if it meets at least one of the three criteria in
   Decision. Use cases that only pass a call through are not created.
6. `[convention]` When a use case exists for an operation, ViewModels use it instead of calling the
   repository directly.
7. `[convention]` A ViewModel that needs data from more than one repository gets it through a use
   case.
