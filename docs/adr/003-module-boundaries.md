# ADR-003: Module boundaries and dependency rules

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox is split into Gradle modules (see the [module table and generated graph](../../README.md#modules)).
Modules only help if the dependencies between them follow rules. Otherwise the graph slowly
becomes a tangle: build times grow, features become coupled to each other, and layer boundaries
([ADR-001](001-layered-architecture.md)) exist only on paper.

Gradle already rejects dependency cycles. Everything else has to be decided by us. The convention
plugins ([ADR-004](004-convention-plugins.md)) already check two rules when the build is
configured, through `ModuleRules.kt`.

## Decision

We will organize modules by **type**, give each type a fixed set of dependencies it's allowed to
have, and make the build check these rules wherever possible.

| Module type | Role | May depend on |
| --- | --- | --- |
| `:app` | Composition root: `Application`, navigation between features, final Hilt graph | Anything except `:core:testing` outside test configurations |
| `:feature:*` | One user-facing area: screens and ViewModels | `:core:domain`, `:core:model`, `:core:designsystem`, `:core:common` |
| `:core:domain` | Repository interfaces and use cases (pure JVM) | `:core:model`, `:core:common` |
| `:core:model` | Domain models (pure JVM) | Nothing |
| `:core:common` | Shared utilities: dispatchers, result types (pure JVM) | Nothing |
| Data layer: `:core:data`, `:core:database`, `:core:datastore`, `:core:auth`, `:core:sync` | Implementations of domain interfaces, storage, remote access | `:core:domain`, `:core:model`, `:core:common`, other data-layer modules |
| `:core:designsystem` | Theme and shared composables | No domain or data modules |
| `:core:testing` | Fakes, test runner, `@TestInstallIn` modules | Anything it needs to replace |

Guidelines that go with the table:

- **Features are independent.** A feature never depends on another feature. Navigation between
  features is wired in `:app`. If two features need the same code, it moves to a `:core` module.
- **`:app` is the only module that depends on features.** It's also where the data-layer
  implementations meet the Hilt graph ([ADR-005](005-hilt-dependency-injection.md)).
- **Use `implementation` by default.** `api` is used only when a module's public signatures expose
  another module's types (for example, a module that returns `:core:model` types).
- **Split modules when there's a reason.** A new `:core` module needs a clear responsibility that
  doesn't fit an existing module. A new feature module is created per user-facing area, not per
  screen.

## Alternatives considered

- **A single `:app` module with packages instead of modules.** The fastest start, but package
  boundaries aren't enforced by the compiler. It also gives up build parallelism and the
  independence of features.
- **Modules by layer only (`:ui`, `:domain`, `:data`).** Enforces layers, but every feature ends up
  in the same UI module, so features are still coupled and every UI change rebuilds all of them.
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
- Cross-feature navigation needs deliberate wiring in `:app`, which is extra work compared with one
  feature calling another directly.
- The [module graph in the README](../../README.md#module-graph) is generated from the build
  (`./gradlew moduleGraph`), so it stays the accurate picture of the actual structure. This ADR
  describes the rules, not the current graph.

## Rules

1. `[enforced]` Feature modules never depend on other feature modules.
2. `[enforced]` `:core:model` depends on no other module.
3. `[enforced]` `:core:*` modules never depend on `:feature:*` modules.
4. `[enforced]` Only `:app` depends on `:feature:*` modules.
5. `[enforced]` Feature modules never depend on data-layer modules (see ADR-001, rule 1).
6. `[enforced]` `:core:domain`, `:core:model` and `:core:common` are JVM modules with no Android
   dependencies.
7. `[enforced]` `:core:designsystem` never depends on `:core:domain` or data-layer modules.
8. `[enforced]` `:core:testing` is only used from test configurations (`testImplementation`,
   `androidTestImplementation`).
9. `[convention]` Dependencies use `implementation` unless the module's public signatures expose
   the other module's types.

**Conformance.** Rules 1 to 8 are checked by `ModuleRules.kt` when the build is configured.
