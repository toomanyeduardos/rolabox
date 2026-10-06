# ADR-021: Every layer is reached through an abstraction, from the screen to the data source

- **Status:** Accepted
- **Date:** 2026-10-05
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-001](001-layered-architecture.md) (use cases as concrete classes in an
  `:api`, and the shape of ViewModels it left open) and [ADR-003](003-module-boundaries.md) (rule
  11, and the "pure-logic use cases" exception of "Where things live")

## Context

[ADR-001](001-layered-architecture.md) put repositories behind interfaces: the interface is in an
`:api`, the implementation in an `:impl`, and nothing but `:app` sees the implementation. Two
kinds of class were left outside that pattern:

- **Use cases are concrete classes in an `:api`.** ADR-001 argued that pure logic has nothing to
  hide, and [ADR-003](003-module-boundaries.md) rejected an interface for them as twice the files.
  So an `:api` holds interfaces for one kind of thing and implementations for another, and a
  ViewModel that uses a use case compiles against its logic and its constructor.
- **Composables take the concrete ViewModel.** `CreateAccountScreen` asks for
  `CreateAccountViewModel`. ADR-001 said ViewModels expose a `StateFlow` and left the rest "to a
  future ADR".

[ADR-020](020-modules-by-product-area.md) makes `:api` and `:impl` the shape of every part of the
app. With that, the two exceptions stand out: the path from a screen to a data source crosses
several boundaries, and only some of them are abstractions. Rolabox is also a showcase of its
architecture, so the path should follow one rule that can be stated in one sentence.

## Decision

We will make **every step from the screen to the data source depend on an abstraction, never on
the class that implements it.**

```
Screen composable           stateless: takes state and lambdas
      ▲ state   │ actions
Route composable ─────────▶ abstract SignInViewModel ◀──── DefaultSignInViewModel
                                                                  │ injects
                            SignInUseCase, AuthRepository  ◀──────┘
                            (interfaces, in :auth:data:api)
                                      ▲ implemented by
                            DefaultSignInUseCase, FirebaseAuthRepository
                            (in :auth:data:impl)
                                      │ inject
                            PreferencesStore (interface, in :common:storage:api)
                                      ▲ implemented by
                            DataStorePreferencesStore (in :common:storage:impl)
```

### Composable to ViewModel

- **Each screen's ViewModel is an abstract class** that extends `ViewModel`. It declares the UI
  state as a `StateFlow` and one function per user action, and nothing else: no constructor
  parameters and no logic.
- **The concrete ViewModel extends it,** is annotated with `@HiltViewModel`, and injects what it
  needs. It is named `Default…ViewModel`.
- **Composables are typed with the abstract class.** The route composable takes it as a parameter,
  collects its state, and calls the stateless screen composable with state and lambdas. That
  second composable is unchanged, and is still what previews and screenshot tests call
  ([ADR-016](016-screenshot-testing.md), rule 3).
- **The concrete class is named in one place:** the part's entry, where `hiltViewModel` creates it
  and passes it to the route. Hilt creates ViewModels by their concrete class, so one place has to
  name it. No composable function does.
- **Both classes live in the `:impl` of the part with screens.** The abstract class needs
  `androidx.lifecycle`, so it can't be in a JVM `:api`, and no other module uses it.

### ViewModel to domain

A ViewModel injects only interfaces from `:api` modules: a repository or a use case. When a use
case is needed is unchanged: ADR-001's three criteria still decide it, and a use case that only
passes a call through is still not created.

### Use cases

- **A use case is an interface in an `:api`,** with the models and errors it uses. It is public.
- **Its implementation is a class in the `:impl` of the same part,** `internal`, bound with
  `@Binds` ([ADR-005](005-hilt-dependency-injection.md), rule 2), and named `Default…UseCase`.
- **It is still pure logic.** It combines or applies rules to interfaces from `:api` modules and
  does no I/O of its own. Moving to an `:impl` doesn't let it call an SDK.
- **It has a fake** in the part's `:testing` module, like a repository.

So an `:api` now holds only interfaces, models and error types, with no exception.

### Domain to data, and below

Unchanged from ADR-001, and stated here so the chain is in one place:

- A repository's interface is in its part's `:api`, and its implementation in that part's `:impl`.
- An implementation reaches the next layer (storage, sync, the network) only through that layer's
  `:api`, and that layer's `:impl` implements it.
- Storage and transport types stay inside the `:impl` that uses them, and are mapped to `:api`
  types at its boundary.

### What flows back

State flows up as `Flow`s and results ([ADR-006](006-async-api-shape.md)), errors as `Either`
([ADR-007](007-error-handling.md)). The ViewModel folds both into UI state, and neither appears in
a composable.

### Migration

The code doesn't match this ADR yet. The use cases move with
[ADR-020](020-modules-by-product-area.md)'s second migration step, and the ViewModels of the
account screens change in the same step, since both are being moved anyway.

## Alternatives considered

- **Keep concrete ViewModels, with stateless screen composables** (today). One class per screen,
  and previews already don't need a ViewModel. But the route composable is tied to one
  implementation and to Hilt, so the wiring between a ViewModel and its screen can only be tested
  with the real ViewModel and fakes behind it.
- **An interface for the ViewModel, instead of an abstract class.** Lighter, and it could live in a
  JVM module. But the route would hold something that isn't a `ViewModel`, so nothing in the type
  says it is scoped to the entry and survives configuration changes.
- **Put the abstract ViewModel in the part's `:api`.** Consistent with repositories. But that
  `:api` would need Android's lifecycle library, and no other module has a use for the class.
- **Keep use cases as concrete classes in an `:api`** (ADR-001). Half the files, and ViewModel
  tests run the real rule with fake repositories. But an `:api` then mixes contracts and logic,
  consumers compile against a constructor, and a change to a rule rebuilds every module that
  depends on the `:api`.
- **An interface only for use cases with more than one implementation.** No file is added without
  a reason. But there would be two kinds of use case, and the choice would be made again for each.
- **Use case implementations in a module of their own,** a domain `:impl` beside the data `:impl`.
  It keeps logic and SDK code apart by module. But it adds a module per part for a handful of
  classes, and the "pure logic" rule already keeps them apart.

## Consequences

- The path from a screen to a data source follows one rule, and an `:api` holds only contracts.
- A route can be tested with a fake ViewModel, and a ViewModel with fake use cases and
  repositories.
- A change inside a use case no longer rebuilds the modules that use it.
- There are more files: an abstract class per ViewModel, and an interface, a binding and a fake per
  use case.
- **ViewModel tests no longer run the real use case.** A part with screens can't see an `:impl`, so
  its tests use the fake. The rule itself is tested where it is implemented, and a fake that
  doesn't behave like the real one is a new way for tests to pass wrongly.
- Tests that cover a rule of another ADR move with the use case. ADR-008's rule 7 is covered by the
  sign-in and sign-up use case tests, which will be in `:auth:data:impl`.
- A use case implementation sits in the same module as SDK code, so "no I/O of its own" is held by
  review and by its constructor, which takes only `:api` interfaces.
- The entry is the one place that names a concrete ViewModel, which is a convention.

## Rules

Rules tagged `[planned]` are enforced with [37.07b](https://trello.com/c/NjGx7cY4).

1. `[planned]` An `:api` module declares no class whose name ends in `UseCase` or `Repository`:
   those are interfaces.
2. `[planned]` No composable function has a parameter or a `hiltViewModel` call typed with a
   `Default…ViewModel`. Only an entry names one.
3. `[convention]` Every screen's ViewModel is an abstract class that extends `ViewModel` and
   declares only its UI state, as a `StateFlow`, and its actions. The class that implements it is
   `Default…ViewModel`, and both live in the `:impl` of the part with screens.
4. `[convention]` A route composable takes the abstract ViewModel, and passes state and lambdas to
   a stateless screen composable. Previews and screenshot tests call the stateless one.
5. `[convention]` A ViewModel injects only interfaces from `:api` modules.
6. `[convention]` A use case is an interface in an `:api`, implemented by an `internal`
   `Default…UseCase` in the same part's `:impl`, bound with `@Binds`, with a fake in the part's
   `:testing`.
7. `[convention]` A use case implementation takes only `:api` interfaces in its constructor, and
   does no I/O of its own.
8. `[convention]` An `:api` module contains only interfaces, models and error types.
9. `[convention]` An `:impl` reaches another layer only through that layer's `:api`.

**Conformance.** Rules 1 and 2 will be checked by detekt. Today the use cases are classes in
`:core:domain`, and the account screens take their concrete ViewModels.
