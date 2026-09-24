# ADR-004: Share build configuration through convention plugins

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox has around a dozen modules, and the number will grow with every feature. Most of them need
the same setup: SDK levels, Java version, Compose, Hilt with KSP, ktlint and detekt, and the
module dependency rules from [ADR-003](003-module-boundaries.md). Configuration copied into every
build file drifts: one module misses a lint rule, another compiles against a different SDK.

Gradle offers several ways to share build logic. It recommends convention plugins in an included
build over `buildSrc` and over configuring subprojects from the root build file.

## Decision

We will put all shared build configuration in **convention plugins** inside the `build-logic`
included build (`build-logic/convention`), written as Kotlin classes and registered under
`rolabox.*` plugin IDs.

- **Base plugins** set up one kind of module: `rolabox.android.application`,
  `rolabox.android.library` and `rolabox.jvm.library`. Each one also applies the module rules
  (`enforceModuleRules()`) and static analysis (`applyStaticAnalysis()`), so no module can opt out
  of them.
- **Additive plugins** add one capability: `rolabox.android.compose`, `rolabox.hilt`.
- **Composite plugins** describe a module type: `rolabox.android.feature` applies a library,
  Compose and Hilt, plus the dependencies every feature needs.
- Shared constants (SDK levels, Java version) live in `ProjectConfig`, and dependency versions live
  only in the version catalog (`gradle/libs.versions.toml`).

A module's build file then states what the module *is*, plus its namespace and any dependencies of
its own:

```kotlin
plugins {
    id("rolabox.android.feature")
}

android {
    namespace = "com.eduardoflores.rolabox.feature.settings"
}
```

## Alternatives considered

- **Copying configuration into each module.** No indirection, but it drifts, and every new
  cross-cutting setting means editing every module.
- **`subprojects {}` / `allprojects {}` blocks in the root build file.** Short, but it couples every
  project to the root. It also works against Gradle's project isolation and configuration cache, and
  it applies configuration by position in the build instead of by what a module is.
- **`buildSrc`.** Same plugin code, but any change to `buildSrc` invalidates the build cache for the
  whole project, and Gradle now recommends an included build instead.
- **Precompiled script plugins (`*.gradle.kts` files in `build-logic`).** Less boilerplate than
  plugin classes, but we preferred plain Kotlin classes, which are easier to refactor, share helper
  functions (such as `ModuleRules.kt`) naturally, and read like the rest of the codebase.

## Consequences

- Module build files stay a few lines long, and a new module gets the whole standard setup by
  applying one plugin.
- Cross-cutting rules (module rules, ktlint, detekt) are applied in one place and can't be
  forgotten, which is what makes the `[enforced]` rules in ADR-003 possible.
- There's more indirection: to know how a module is configured, you read the plugins it applies.
- Inside `build-logic`, catalog entries are looked up by string (`libs.findLibrary("…")`), so a
  typo fails when the build is configured instead of when the plugin compiles.
- Changes to `build-logic` affect every module, so they need careful review.

## Rules

1. `[convention]` Every module applies exactly one base plugin (`rolabox.android.application`,
   `rolabox.android.library` or `rolabox.jvm.library`), directly or through a composite plugin.
2. `[convention]` Configuration needed by more than one module goes into a convention plugin, not
   into module build files.
3. `[convention]` Module build files contain only plugins, the namespace, dependencies, and
   settings unique to that module.
4. `[convention]` Dependency versions are declared only in `gradle/libs.versions.toml`.
5. `[convention]` Plugins are Kotlin classes in `build-logic/convention`, registered in its
   `build.gradle.kts` with a `rolabox.*` ID.
