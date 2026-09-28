# ADR-003: Module boundaries and dependency rules

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-26:** Every capability is now an area split into `:core:<area>:api`, `:impl` and
  `:testing`, instead of one shared `:core:domain` and `:core:data` plus standalone data modules.
  `:api` modules hold only interfaces and models, and `:impl` modules depend only on `:api` modules
  and the `:core:common` utilities. Storage and sync became areas like the others, and there is no
  shared `:core:model`. The module table, guidelines, alternatives and rules were rewritten, and
  decisions were added on where fakes, use cases and shared types live. With the old layout, every
  feature could see every repository interface, so a module's dependencies didn't show which
  capabilities it used. An area's code was also spread across shared modules, so it couldn't be
  moved or reasoned about on its own.
- **Revised 2026-09-28:** A `:testing` module may also depend on the `:api` modules whose types its
  fakes use, not only its own `:api` (rule 13, "Where things live"). A fake implements its `:api`'s
  interfaces, so it uses every type in their signatures, and rule 15 requires declaring those
  modules. Rule 13's goal, never putting an implementation on a test classpath, is unchanged, since
  `:api` modules have none.

## Context

Rolabox is split into Gradle modules (see the [module table and generated graph](../../README.md#modules)).
Modules only help if the dependencies between them follow rules. Otherwise the graph slowly
becomes a tangle: build times grow, features become coupled to each other, and layer boundaries
([ADR-001](001-layered-architecture.md)) exist only on paper.

Gradle already rejects dependency cycles. Everything else has to be decided by us. The convention
plugins ([ADR-004](004-convention-plugins.md)) check the rules when the build is configured,
through `ModuleRules.kt`.

The app's data and behavior fall into **areas** (capabilities): authentication, user data
(preferences), local storage, sync, and later the music library, playlists and play history. We
want each area to be self-contained and portable, and each module's dependencies to show exactly
which areas it uses.

## Decision

We will organize `:core` modules **by area**, and split every area into a public API and a hidden
implementation:

- **`:core:<area>:api`** is the area's public surface: interfaces, models and error types. Anything
  another module needs from the area is exposed here, and nowhere else.
- **`:core:<area>:impl`** implements the `:api`, and binds it with Hilt. It depends only on `:api`
  modules: its own, and those of the areas it uses.
- **`:core:<area>:testing`** holds fakes of the `:api`.

Dependencies point from `:impl` to `:api`, never the other way. So an area `:bbb` that needs data
from `:aaa` depends on `:aaa:api` only, and never sees how `:aaa` is implemented:

```
:bbb:impl ──▶ :bbb:api ──▶ :aaa:api ◀── :aaa:impl
                                            ▲
:app ───────────────────────────────────────┘ (and every other :impl)
```

`:app` is the only module that depends on `:impl` modules. It has to be: Hilt assembles the whole
graph in the `@HiltAndroidApp` module, so `:app` must see every implementation and its bindings.

| Module type | Role | May depend on |
| --- | --- | --- |
| `:app` | Composition root: `Application`, navigation between features, the Hilt graph, and the `@TestInstallIn` modules that swap production bindings in tests | Anything, but testing modules only from test configurations |
| `:feature:*` | One user-facing area: screens and ViewModels | `:core:<area>:api`, `:core:domain`, `:core:common`, `:core:designsystem` |
| `:core:<area>:api` | Interfaces, models, error types, and pure-logic use cases for this area (pure JVM) | Other `:core:<area>:api`, `:core:common` |
| `:core:<area>:impl` | Implementations of the `:api` and their Hilt modules, with backend-specific code in flavor source sets ([ADR-008](008-offline-and-cloud-flavors.md)) | `:core:<area>:api` (its own and others), `:core:common` |
| `:core:<area>:testing` | Fakes of the area's `:api` (pure JVM) | Its own `:api` |
| `:core:domain` (not yet created) | Pure-logic use cases that combine more than one area (pure JVM) | `:core:<area>:api`, `:core:common` |
| `:core:common` | Utility: dispatchers, exception-to-error helpers (pure JVM) | Nothing |
| `:core:designsystem` | Utility: theme and shared composables | `:core:common` |
| `:core:testing` | Utility: Hilt test runner, `MainDispatcherRule` | Anything except `:impl` modules |

The current areas are `auth`, `storage`, `sync` and `userdata`.

**Where things live.** These follow from the goal that an area can be moved or read on its own:

- **`:api` modules hold only interfaces and models.** That includes the area's error types. The
  one exception is **pure-logic use cases** ([ADR-001](001-layered-architecture.md)): a use case
  that only combines or applies rules to its `:api`'s interfaces, with no I/O or framework code of
  its own, may live in the `:api`. It can be tested on the JVM with the area's fakes.
- **Every type lives in the `:api` of the area that owns it.** The owner is the area that defines
  what the type means. `AuthUser` is in `:core:auth:api`, `UserData` in `:core:userdata:api`, and
  `StorageError` in `:core:storage:api`.
- **Types are shared through `:api` dependencies, not a shared model module.** When a second area
  needs a type, it depends on the owner's `:api`. For example, playlists will depend on
  `:core:library:api` for `TrackId`. That dependency is real, and it shows in the build file. A
  type with no owning area is a generic utility, and belongs in `:core:common`.
- **Storage is an area.** `:core:storage:api` exposes `PreferencesStore` (key-value settings) and
  `StorageError`, and `:core:storage:impl` implements them with DataStore. Areas own their keys and
  map stored values to their own models, so `:core:userdata:impl` doesn't know DataStore exists.
  How Room fits (where the database class, entities and DAOs live) is left to its own ADR.
- **Use cases that combine areas go in `:core:domain`.** It's created with the first such use case,
  and the build rules for it already exist. A use case for one area lives in that area's `:api`.
- **Fakes live in each area's `:testing` module.** A module's test dependencies then name the
  same areas as its main dependencies, and an area carries its fakes with it. `:testing` modules
  depend only on `:api` modules (their own, and those whose types their fakes use), so using a
  fake never puts an implementation on a test classpath.
- **`@TestInstallIn` modules live in `:app`'s androidTest sources.** Replacing a production Hilt
  module means referencing it, and only `:app` may see `:impl` modules. `:app` is also the only
  module that assembles a production Hilt graph, so it's the only place where there is something to
  replace.
- **Utility modules aren't split.** `:core:common`, `:core:designsystem` and `:core:testing` have no
  implementation to hide, so they have no `:api`/`:impl` pair. They stay generic: anything that
  belongs to an area goes in that area.
- **Packages mirror module paths**: `com.eduardoflores.rolabox.core.auth.api`,
  `….core.auth.impl`, `….core.auth.testing`. The same package is never split across modules.

Guidelines that go with the table:

- **Features are independent.** A feature never depends on another feature. Navigation between
  features is wired in `:app`. If two features need the same code, it moves to a `:core` module.
- **Features declare the `:api` modules they use.** The `rolabox.android.feature` convention plugin
  adds only `:core:designsystem`, so a feature's build file lists the areas it depends on.
- **Use `implementation` by default.** `api` is used only when a module's public signatures expose
  another module's types (for example, `:core:userdata:api` returns `StorageError` from
  `:core:storage:api`). It's never a way to hand consumers dependencies they'd otherwise declare.
- **Every module declares what it uses directly,** even when another dependency's `api` already puts
  it on the classpath. `:app` declares `:core:userdata:api` for its ViewModel, not only
  `:core:userdata:impl`. So a build file lists every area the module's code touches, which is the
  point of this layout. Hilt doesn't need an `:impl` to re-export its `:api`: it collects bindings
  from `:app`'s whole classpath.
- **Split modules when there's a reason.** A new area gets its `:api`, `:impl` and `:testing`
  modules when it has an interface of its own. A new feature module is created per user-facing
  area, not per screen.

## Alternatives considered

- **A single `:app` module with packages instead of modules.** The fastest start, but package
  boundaries aren't enforced by the compiler. It also gives up build parallelism and the
  independence of features.
- **Modules by layer only (`:ui`, `:domain`, `:data`).** Enforces layers, but every feature ends up
  in the same UI module, so features are still coupled and every UI change rebuilds all of them.
- **One shared `:core:domain` and `:core:data` for every area.** This was the original layout.
  Fewer modules, but a feature that needs one repository sees all of them, and an area's code is
  spread across shared modules, so it can't be moved or reviewed on its own.
- **An area module without an API split (`:core:auth` holding interface and implementation).**
  Half the modules, but features would compile against the implementation and its dependencies
  (Firebase in cloud), which is what [ADR-001](001-layered-architecture.md) exists to prevent.
- **Shared data-layer modules that `:impl` modules call directly (`:core:datastore`).** No interface
  to write, but every `:impl` would know which storage library backs it, storage exceptions would be
  converted in every area, and the rule "`:impl` depends only on `:api`" would need exceptions.
- **A shared `:core:model` for types used by more than one area.** Common in Android projects, and
  it's where this layout started. But almost every type has an owning area, so the module either
  stays nearly empty or collects types whose owner nobody decided. It also hides which areas depend
  on each other, because they all depend on the shared module instead.
- **Use cases only as an interface in `:api` and a class in `:impl`.** Keeps `:api` free of any
  logic, but doubles the files for every use case, and pure logic gains nothing from being hidden
  behind Hilt.
- **All fakes in `:core:testing`.** One module to find them in, but every test would see every
  area's fakes, and `:core:testing` would need every `:impl` module for its `@TestInstallIn`
  modules, putting implementations on feature test classpaths.
- **Creating `:core:domain` now, empty.** Ready for the first cross-area use case, but an empty
  module is a place for code to drift into without meeting the criteria in ADR-001.
- **Splitting each feature into `api` and `impl` modules.** Lets features navigate to each other
  without depending on each other's implementation. It's useful at larger scale, but it doubles the
  number of feature modules, and wiring navigation in `:app` is enough for now. A future ADR can
  adopt it.
- **Rules documented but not enforced.** Cheaper to set up, but rules nobody checks decay quietly,
  one convenient dependency at a time.

## Consequences

- Breaking an `[enforced]` rule fails the build at configuration time, before anything compiles,
  with an error naming the rule.
- Features can be built, tested and changed on their own, and can be worked on in parallel.
- A module's build file lists exactly which areas it uses, and an area (its `:api`, `:impl` and
  `:testing` modules) can be read, tested or moved as a unit.
- An `:impl` can be tested with the fakes of the areas it uses. `:core:userdata:impl` is tested with
  `FakePreferencesStore`, with no DataStore.
- There are more modules: three per area. Adding an area means creating three build files, which
  the convention plugins keep to a few lines each.
- A shared library (DataStore, later Room) needs an interface in front of it, which is extra code
  compared with calling it directly. For Room, that interface is the open question for its ADR.
- Several modules share the names `api`, `impl` and `testing`. Gradle paths and packages keep them
  apart, but IDE project views and build output show the short names.
- Cross-feature navigation needs deliberate wiring in `:app`, which is extra work compared with one
  feature calling another directly.
- The [module graph in the README](../../README.md#module-graph) is generated from the build
  (`./gradlew moduleGraph`), so it stays the accurate picture of the actual structure. This ADR
  describes the rules, not the current graph.

## Rules

1. `[enforced]` Feature modules never depend on other feature modules.
2. `[enforced]` Only `:app` depends on `:feature:*` modules.
3. `[enforced]` Only `:app` depends on `:impl` modules.
4. `[enforced]` Feature modules depend only on `:api` modules, `:core:domain`, `:core:common` and
   `:core:designsystem` (plus testing modules from test configurations).
5. `[enforced]` `:api` modules, `:core:common` and `:core:domain` are JVM modules with no Android
   dependencies.
6. `[enforced]` `:api` modules depend only on `:core:common` and other `:api` modules.
7. `[enforced]` `:impl` modules depend only on `:api` modules and `:core:common` (plus testing
   modules from test configurations).
8. `[enforced]` `:core:domain` depends only on `:api` modules and `:core:common`.
9. `[enforced]` `:core:designsystem` depends only on `:core:common`.
10. `[enforced]` Testing modules (`:core:testing` and every `:core:<area>:testing`) are only used
    from test configurations (`testImplementation`, `androidTestImplementation`).
11. `[convention]` `:api` modules contain only interfaces, models, error types and pure-logic use
    cases.
12. `[convention]` Every type lives in the `:api` of the area that owns it, and other areas reach it
    by depending on that `:api`. There is no shared model module.
13. `[convention]` Fakes live in their area's `:testing` module, which depends only on that area's
    `:api` and the `:api` modules whose types it uses (never an `:impl`). `@TestInstallIn` modules
    live in `:app`'s test sources.
14. `[convention]` Packages mirror module paths, and no package is split across modules.
15. `[convention]` Dependencies use `implementation` unless the module's public signatures expose
    the other module's types. A module declares every project module whose types it uses directly,
    even when it would get them through another dependency's `api`.

**Conformance.** Rules 1 to 10 are checked by `ModuleRules.kt` when the build is configured. Rule 11
could be checked with a Konsist test later.
