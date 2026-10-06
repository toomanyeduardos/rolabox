# ADR-001: Layered architecture with a domain layer that owns the repository interfaces

- **Status:** Accepted. Where cross-area use cases live, and the wording of rule 1, superseded by
  [ADR-020](020-modules-by-product-area.md). Use cases as concrete classes in an `:api` superseded
  by [ADR-021](021-data-flow-through-layers.md)
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-26:** The domain layer is now split by area: each area's `:core:<area>:api`
  module holds its repository interfaces, models and error types, instead of one `:core:domain`
  holding them all (rules 1 to 3, Decision, Consequences). There is no shared `:core:model`: each
  type lives in the `:api` of the area that owns it. The data layer is each area's `:impl`, which
  depends only on `:api` modules and the `:core:common` utilities, so shared storage is an area too
  (`:core:storage`). Use cases are pure logic and live in an `:api` (one area) or `:core:domain`
  (several). This makes each capability self-contained, and each module's dependencies show which
  capabilities it uses. The layering is unchanged. See [ADR-003](003-module-boundaries.md) for the
  module layout.
- **Revised 2026-09-28:** Added rule 8, which names use cases with a `UseCase` suffix so they are
  easy to find and tell apart from repositories. The first cross-area use case,
  `ResolveStartDestinationUseCase`, was named this way. The decision is unchanged.
- **Revised 2026-10-06:** Module names updated for the layout of
  [ADR-020](020-modules-by-product-area.md), now that the code has moved (37.07b). The diagram and
  the layers name parts instead of `:core:<area>` and `:feature:*`. `:core:domain` is gone: a use
  case is an interface in the data `:api` of the area that owns its outcome, implemented in that
  part's `:impl` (Decision). Rules 1 and 2 are worded as the ADR-020 rules that the build now checks
  for them. Where the text says "feature", read the `:impl` of a part with screens. The three
  layers, and when a use case is created, are unchanged.

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

We will use three layers. Dependencies point inward, toward the domain. The domain layer is split
by area (auth, user data, and later the library, playlists, and so on): each area's `:api` module is
that area's domain layer ([ADR-003](003-module-boundaries.md)).

```
UI (:impl of a part with screens)  ──▶  domain (a data part's :api)  ◀──  data (that part's :impl)
                                              │
                                              ▼
                                         :common:util
```

**Domain layer (a data part's `:api`, pure Kotlin/JVM).**
- Each area's `:api` module owns the **repository interfaces** that features and use cases
  consume, plus the models and error types those interfaces use.
- It depends only on `:common:util` and other areas' `:api` modules, such as `:common:storage:api`
  for storage errors.
- It has no Android dependencies, so everything in it is tested on the JVM.
- A use case's interface is in the data `:api` of the area that owns its outcome, which depends
  on the `:api` modules it combines. Its implementation is in that part's `:impl`
  ([ADR-020](020-modules-by-product-area.md), [ADR-021](021-data-flow-through-layers.md)). There is
  no shared domain module.

**Data layer (a data part's `:impl`).**
- Each area's `:impl` module **implements** its `:api` interfaces (dependency inversion).
- An `:impl` reaches other areas, including storage, only through their `:api` modules. For
  example, `:common:userdata:impl` stores preferences through `PreferencesStore` from
  `:common:storage:api`, and only `:common:storage:impl` knows about DataStore.
- Storage and transport types (Room entities, DTOs, DataStore keys) stay internal to the data layer
  and are mapped to `:api` types at its boundary.

**UI layer (the `:impl` of a part with screens).**
- Compose screens and ViewModels. They depend on the `:api` modules of the parts they use, never on
  `:impl` modules.
- ViewModels expose UI state as a `StateFlow` and receive user actions as function calls
  (unidirectional data flow). The shape of a ViewModel is decided in
  [ADR-021](021-data-flow-through-layers.md).

**Use cases are optional.** A ViewModel may inject a repository interface directly. Create a use
case only when at least one of these is true:

1. it combines data from **more than one repository**;
2. the same logic is needed by **more than one ViewModel**;
3. it holds a **business rule** that isn't presentation logic (for example, shuffle and queue
   rules, or matching remote favorites to local tracks).

Use cases are **pure logic**: they combine repository interfaces or apply rules to what they
return, and do no I/O of their own. Their interfaces are in the domain layer, in an `:api`, and
their implementations in the same part's `:impl` ([ADR-021](021-data-flow-through-layers.md)), tested
on the JVM with fakes.

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
  mistake, and each area's fakes fit naturally in its part's `:testing` module.
- Business rules live in a JVM module, and their tests run without Robolectric or a device.
- Every repository interface lives in its area's `:api` module, away from its implementation,
  which adds some navigation cost when reading code.
- "When to create a use case" is a judgment call guided by the three criteria above, so it needs
  attention in review.
- Aggregating in a use case is for crossing repository boundaries. When the data lives in the same
  local database, a single query in the data layer is preferred over several repository calls
  joined in memory.

## Rules

1. `[enforced]` Only `:app` depends on `:impl` modules: a part with screens depends on `:api`
   modules. An `:impl` depends only on `:api` modules, `:common:util` and `:common:designsystem`
   ([ADR-020](020-modules-by-product-area.md), rules 4 and 5).
2. `[enforced]` Every `:api` module is a JVM module with no Android dependencies, and depends only
   on `:common:util` and other `:api` modules ([ADR-020](020-modules-by-product-area.md), rule 6).
3. `[convention]` Every repository interface that a feature or use case consumes is declared in its
   area's `:api` module, and implemented in that area's `:impl` module.
4. `[convention]` Room entities, DTOs and other storage types never appear in a public signature
   outside the data layer.
5. `[convention]` A use case exists only if it meets at least one of the three criteria in
   Decision. Use cases that only pass a call through are not created. Use cases are pure logic, with
   no I/O of their own.
6. `[convention]` When a use case exists for an operation, ViewModels use it instead of calling the
   repository directly.
7. `[convention]` A ViewModel that needs data from more than one repository gets it through a use
   case.
8. `[convention]` A use case's class name ends in `UseCase` and starts with a verb, such as
   `ResolveStartDestinationUseCase`.
