# ADR-020: Modules grouped by product area, with the device assembling its own screens

- **Status:** Accepted
- **Date:** 2026-10-03
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-001](001-layered-architecture.md) (where cross-area use cases live, and the
  wording of rule 1), [ADR-003](003-module-boundaries.md) (the module table and rules 1 to 10 and
  16 to 19), [ADR-009](009-ui-bound-sdks.md) (the wording of rules 1 and 4),
  [ADR-012](012-navigation.md) (rules 1 and 4, and an exception to rule 5),
  [ADR-014](014-area-ui-modules.md) (rules 1 to 3), [ADR-018](018-device-navigation.md) (rules 1,
  5 and 12, in the parts that name `:app`) and [ADR-019](019-device-host-module.md) (rules 2 and 4)
- **Revised 2026-10-06:** The migration is done ([37.07b](https://trello.com/c/NjGx7cY4)). The first
  step held: keys, an entry contract and a section's row compile and run in a JVM `:api`, the row
  with the Compose compiler applied to that module. Rules 1 to 8 are `[enforced]` (Rules,
  Conformance, Migration). The decision is unchanged.
- **Revised 2026-10-06:** The music library's data is a part of the device, `:device:library:{api,
  impl,testing}` (The layout, Left open). It was open; the device screens need it before the real
  library is designed, so it starts as a hard-coded implementation, in memory and with no database
  (ADR-002 isn't met yet, and the code says so). The decision is unchanged.
- **Revised 2026-10-06:** The music screens are a part of the device, `:device:music:{api,impl}`
  (The layout). It is the first part with device screens: it declares its keys and entry contract in
  its `:api`, and `:device:ui:impl` injects the contract, maps its exits to keys and pushes them. The
  decision is unchanged.
- **Revised 2026-10-06:** A section's row is observed, `observeRow(): Flow<SettingsRow>`, and a row
  may open nothing (Settings). Both came with 37.11: the account row depends on whether the user is
  signed in, and has no screen to open once they are. Rules 16 and 17 are unchanged. The decision is
  unchanged.
- **Revised 2026-10-07:** The music screens are `:device:music:ui:{api,impl}`, not
  `:device:music:{api,impl}` (The layout), so a part's screens are `:ui` the way `:auth:ui` and
  `:device:ui` already are, and the playback screens can be `:device:playback:ui` (38.01). Packages
  follow the new paths. The decision is unchanged.

## Context

[ADR-003](003-module-boundaries.md) sorts modules into `:feature:*` and `:core:*`. Features never
depend on each other, and only `:app` depends on them. That rule has a consequence that only showed
once the device existed: **anything that knows about more than one feature has to live in `:app`.**

[ADR-019](019-device-host-module.md) followed that rule. It moved the device's mechanism into
`:core:device` and left the assembly in `:app`: the main menu, what each exit opens, and the
handlers of the playback buttons. It considered "a device module that depends on the features" and
rejected it as a second composition root. With the code in place, the cost of that choice is clear:

- **`:app` grows with every device screen.** Each new row, exit and handler is code in `:app`. It
  is meant to be a thin wrapper.
- **The device can't be separated from `:app`.** What the device shows is decided outside it, so
  the device isn't a unit that can be read, tested or shipped on its own.
- **An area's code is spread over three places.** Authentication is `:core:auth:*`,
  `:feature:account` and part of `:core:domain`. Nothing in the layout says they belong together.
- **`:core` isn't common.** `:core:sync:impl` depends on `:core:auth:api`, and `:core:domain`
  depends on auth and user data. A module named for what everything shares depends on a product
  area.

Rolabox has two product areas. **Auth** decides whether the user may reach the device, by signing
in or by choosing offline mode ([ADR-008](008-one-app-with-offline-mode.md)). **The device** is the
music player, and it starts once auth has granted access, in whatever way. Each should be a unit
with a public surface, and neither should need `:app` to know what it contains.

## Decision

We will group modules **by product area**, let each area **assemble its own parts**, and keep
`:app` as a thin composition root.

### The layout

```
:app
:auth:data:{api,impl,testing}     the account: repository, models, errors, use cases
:auth:ui:{api,impl}               sign-in, account creation, password reset
:auth:settings:impl               the account section of the device's settings
:device:host                      the device's mechanism: screen stack, wheel routing (ADR-019)
:device:ui:{api,impl}             the device's assembly: its entries, the main menu, the exits
:device:settings:{api,impl}       the settings list and its slots
:device:library:{api,impl,testing}  the music library's data: artists, albums, songs
:device:music:ui:{api,impl}       the music screens: Music, Artists, albums, songs
:device:playback:…                open, see "Left open"
:common:util                      dispatchers, scopes, exception-to-error helpers
:common:designsystem              the visual language (ADR-015)
:common:testing                   Hilt test runner, MainDispatcherRule
:common:storage:{api,impl,testing}
:common:sync:{api,impl,testing}
:common:userdata:{api,impl,testing}
```

An area is made of **parts**. A part is split the same way everywhere:

- **`:api`** is the part's public surface, and a JVM module. For a data part it holds interfaces,
  models and error types, and the interfaces of its use cases
  ([ADR-021](021-data-flow-through-layers.md)). For a part with screens it holds the keys of its
  screens, its **entry contract** (below), and the plain types `:app` provides to it, such as
  `SignInConfig`.
- **`:impl`** implements the `:api` and binds it with Hilt. For a part with screens, it holds the
  screens and their ViewModels.
- **`:testing`** holds fakes of the `:api`.

A part has no empty module. It has a `:testing` only when there is something to fake, and an `:api`
only when another module needs something from it. `:auth:settings` is an `:impl` alone: all it does
is contribute a section, through a contract the device owns.

Four modules aren't split, because they have nothing to hide behind an interface: `:common:util`,
`:common:designsystem`, `:common:testing` and `:device:host`.

### What moved

| Before | After |
| --- | --- |
| `:core:common` | `:common:util` |
| `:core:designsystem` | `:common:designsystem` |
| `:core:testing` | `:common:testing` |
| `:core:storage:*`, `:core:sync:*`, `:core:userdata:*` | `:common:storage:*`, `:common:sync:*`, `:common:userdata:*` |
| `:core:auth:{api,impl,testing}` | `:auth:data:{api,impl,testing}` |
| `:core:auth:ui`, `:feature:account` | `:auth:ui:impl`, with keys, entry contract and `SignInConfig` in `:auth:ui:api` |
| `:core:domain` | Gone. Its use cases move to `:auth:data` |
| `:core:device` | `:device:host` |
| `:app`'s `device` package (main menu, device route) | `:device:ui:impl` |
| `:feature:settings` (empty) | `:device:settings:impl` |

A rule of another ADR that names a module in the left column applies to the module on the right.
"Feature module" in those rules means the `:impl` of a part with screens.

### The device host stays one module

[ADR-019](019-device-host-module.md)'s host is unchanged except for who uses it. `:device:host`
still owns the screen stack, the routing of wheel events and the contract a device screen uses to
receive turn and center. It still names no screen, depends on no area, and takes what it needs as
parameters.

It isn't folded into `:device:ui:api`. The screen contract is Compose code that uses the design
system's `WheelEvent`, so it can't live in a JVM `:api`. And because the host knows no screen, a
part whose screens are drawn in the display can depend on it without a cycle.

What changes is the caller. `:device:ui:impl` gives the host its start key, its entries and the
handlers of the playback buttons, and pushes on its stack. `:app` no longer does.

### Data and screens are separate parts

An area's screens and its data source are never in the same module. `:auth:ui:impl` depends on
`:auth:data:api`, and never sees Firebase ([ADR-001](001-layered-architecture.md)).

[ADR-009](009-ui-bound-sdks.md) is unchanged in what it decides: an SDK step that needs an Activity
lives with the UI, and only its result crosses into the data `:api`. Its "never from an `:impl`"
now reads "never from a data part's `:impl`": the step lives in the `:impl` of the part with
screens, `:auth:ui:impl`.

### Who may depend on whom

| Module | May depend on |
| --- | --- |
| `:app` | Every `:impl`, so Hilt can assemble the graph. In code, only `:auth:data:api`, `:auth:ui:api`, `:device:ui:api` and `:common` |
| An `:api` | Other `:api` modules it is allowed to see (below), `:common:util` |
| An `:impl` | `:api` modules it is allowed to see, `:common:util`, and `:common:designsystem` when it has screens |
| A `:device` `:impl` with device screens | The above, and `:device:host` |
| A `:testing` | Its own `:api`, and the `:api` modules whose types its fakes use |
| `:device:host` | `:common:designsystem`, `:common:util` |
| `:common:*` | `:common:*` only |

Which `:api` modules a module is allowed to see:

- **`:common` depends on nothing outside `:common`.** Dependencies point from the product areas
  down into `:common`, never back. When a common module needs something from a product area, it
  declares an interface in its own `:api` and the area implements it. Sync needs "the user to sync
  for, or nobody": `:common:sync:api` declares that, and `:auth:data:impl` provides it, including
  the offline-mode check of ADR-008.
- **The device never depends on auth.** No `:device` module names an `:auth` module.
- **Auth depends on the device in one place.** `:auth:settings:impl` depends on
  `:device:settings:api`, to contribute the account section. No other `:auth` module depends on
  `:device`.
- **A parent sees its children's `:api`.** `:device:ui:impl` depends on `:device:settings:api` and
  on the `:api` of the other parts of the device.
- **Parts with screens don't name their siblings' keys.** A part's exits are mapped to keys by its
  parent. The one exception is the generic exit (see "Leaving the display").
- **Data `:api` modules are seen by whoever needs the data.** Depending on a type's owner is how
  types are shared (ADR-003, rule 12).
- **Only `:app` depends on `:impl` modules,** as before. Project modules are still declared with
  `implementation`, and every module declares what its code uses (ADR-003, rule 15).

### Use cases

There is no shared domain module. A use case belongs to the area that owns its outcome: its
interface is in that area's data `:api`, which depends on the `:api` modules it combines, and its
implementation in the data `:impl` ([ADR-021](021-data-flow-through-layers.md)). Sign-in, sign-up
and the start destination move to `:auth:data`. The start-destination use case answers whether
access has been granted, and `:app` turns the answer into a key, so auth doesn't name the device.

### What `:app` does

- It is the Hilt composition root, and lists every `:impl` in its build file.
- It keeps the app's shell: the `Application`, the activity, startup and the splash.
- It owns the app stack ([ADR-012](012-navigation.md)) and decides its start.
- It connects the two areas, which don't know each other: when auth reports that access is
  granted, `:app` replaces the auth flow with the device.
- It pushes the full screens the device asks for (see "Leaving the display").
- It provides the configuration only it knows, such as `SignInConfig`.

It declares no navigation key, no entry, no menu and no list of features.

### Entry contracts

A part with screens declares an entry contract in its `:api`: an interface that adds the part's
entries and takes their exits as lambdas ([ADR-012](012-navigation.md), rule 5). Its `:impl`
implements and binds it.

- **Entries are added top down.** A contract adds its own entries and those of its children, which
  it gets by constructor injection. `:app` injects the contracts of `:auth:ui` and `:device:ui`,
  and never names what is under them.
- **A contract keeps the two stacks apart** (ADR-018, rule 2): the entries for the app stack, and
  the entries for the screen stack inside the display. `:app` adds the first set, and
  `:device:ui:impl` gives the second to the host.

### The device assembles itself

`:device:ui:impl` owns the device's entry, the **main menu**, the mapping from a device screen's
exits to keys, and what the playback buttons do, by calling the `:api` of the device's own parts.
It pushes on the screen stack, and the host pops it.

The main menu is a fixed list declared in `:device:ui:impl`. Whether a row is in the product is
that list. Whether the user sees it, such as hiding Podcasts, is a preference read at runtime. The
two are different mechanisms, and the main menu doesn't take contributions.

### Settings

Settings is a row of the main menu whether or not anything else exists. `:device:settings` owns the
settings list and its own rows, such as the theme.

`:device:settings:api` declares an ordered list of **slots**, and the contract of a **section**: the
slot it fills, its row, and the entries behind it. A part contributes a section through a Hilt set,
which `:device:settings:impl` declares so that it may be empty. `:auth:settings:impl` fills the
account slot. A slot nobody fills isn't shown, and a slot has at most one section.

So the order of the settings list is in one file, in the device, and the device knows that an
account slot exists without knowing who fills it. Adding a new kind of section means adding a slot
in `:device:settings:api`.

Settings and everything under it stay full screens on the app stack (ADR-018, rule 11). So a
contributed section never needs `:device:host`.

### Leaving the display

One exit is generic: **"open this key as a full screen."** The device's entry contract takes it as
a lambda, `:app` implements it by pushing on the app stack, and it is passed down unchanged. It is
used by the main menu's Settings row and by contributed settings sections, whose exits the device
can't know. A section opens only keys of its own area, so the account section opens sign-in with a
key from `:auth:ui:api`. No other screen uses it.

It passes a key where ADR-012's rule 5 asks for an identifier. This is a narrow navigator, accepted
so that the device doesn't have to know auth, and it is to be looked at again once it has been
used.

### Tests of the assembly

A contribution that is missing doesn't fail the build. So:

- `:device:settings` tests its slots with fake sections: the order, an empty slot, two sections in
  one slot.
- `:app` tests the real graph: which sections are present, and which are not.

### Left open

This ADR doesn't decide, and its module list isn't complete for:

- **Playback:** its parts. It will need a data part and screens, and possibly a part for Now
  Playing.
- **Where Room's database, entities and DAOs live,** now that `:device:library` exists. Its
  `:impl` serves hard-coded data in memory until the real library is designed, and is replaced
  then.
- **`:device:data`,** which doesn't exist until the device has data of its own.
- **Search and podcasts.**

### Migration

The code was moved in these steps ([37.07b](https://trello.com/c/NjGx7cY4)). Each step leaves the
build green, and brings the checks for the modules it moves: `ModuleRules.kt` drops the ADR-003
rule and gains the rule below in the same change.

1. **Prove the JVM `:api`.** On `:auth:ui:api`, declare keys and an entry contract with Navigation
   3's JVM variant. Also check that a contract can carry a row, as a settings section must. If
   either fails, this ADR is revised before anything else moves.
2. **Move `:common` and auth,** with no change in behaviour. Invert sync's dependency on auth, and
   move `:core:domain`'s use cases.
3. **Move the device.** Rename the host, move the main menu and the pushes from `:app` into
   `:device:ui:impl`, and add `:device:settings` and `:auth:settings`.
4. **Update what names the old modules:** the other ADRs' text and conformance notes, as dated
   revisions; `README.md`'s module table, rules and graph; the convention plugins for features and
   `:ui` modules ([ADR-004](004-convention-plugins.md)); the design system and screenshot checks
   and the detekt messages that name `:core:designsystem`; and the screenshot goldens.

The Music menu is built after step 3, so the first feature with device screens is written once.

## Alternatives considered

- **Keep ADR-019: `:app` assembles the device.** No migration. But `:app` grows with every device
  screen, and the device can't be taken out of it.
- **Keep `:feature` and `:core`, and split each feature into `:api` and `:impl`.** ADR-003 listed
  this as a later option. It lets features see each other's keys, but an area's code stays spread
  over `:core`, `:feature` and `:core:domain`, and the layout still doesn't say which features form
  the device.
- **One `:impl` per area, holding its screens and its data source.** Fewer modules, and `:auth`
  would be three. But screens would compile against Firebase, which ADR-001 exists to prevent.
- **Fold the host into `:device:ui`,** with the screen contract in its `:api`. One part fewer. But
  the contract is Compose code built on the design system, so that `:api` couldn't be a JVM module,
  and ADR-019's tested mechanism would be mixed with the assembly it was separated from.
- **Let a `:common` `:impl` depend on a product area's `:api`.** No interface to add for sync. But
  "common" would then depend on what uses it, and the rule would need an exception that the build
  check has to know about.
- **`:device:settings` depends on an `:auth:settings:api`.** Explicit, with no Hilt set. But the
  device would know auth exists, and couldn't be built without it.
- **Each section declares a weight for its position.** Sections can be added without touching the
  device. But the order would be decided outside the device and spread over the contributors, and
  no file would show the whole list.
- **The main menu takes contributions too.** One mechanism for both lists. But the device would no
  longer decide what the device shows, which is the reason for this ADR.
- **An injected navigator instead of exits as lambdas.** Less wiring, and no generic exit to
  explain. But any screen could then navigate anywhere, and a part's `:api` would no longer list
  how its screens are left. Kept as the fallback if lambdas become a problem.
- **Move the checks last, in one step.** Simpler to describe. But the modules already moved would
  be unchecked until the end, when rules decay fastest.
- **Revise ADR-003 and ADR-019 in place.** ADR-000 allows it before 1.0. But ADR-019 rejected this
  model for stated reasons, and the record of why that was reversed is worth more than a clean file.

## Consequences

- An area can be read, tested and moved as a unit, and its build files show what it needs from
  outside.
- `:app` stops growing with the product. A new device screen changes no file in `:app` except its
  build file's list of `:impl` modules.
- The device can be built and shown without auth. Auth can be built without the device, except for
  `:auth:settings`.
- `:common` can be trusted to be common, and one build rule says so.
- The decision whether sync may run moves out of sync. Sync obeys what auth provides, and the tests
  of ADR-008's rule 6 are split between the two.
- There are more modules, around thirty, and paths up to four levels deep. The convention plugins
  keep each build file short, but configuration time and the IDE's project view both grow.
- Every package changes, since packages mirror module paths (ADR-003, rule 14), and the screenshot
  goldens move with them.
- A missing contribution is found by a test, not by the compiler.
- There is a second kind of exit, the generic one, and its limit to settings is a convention.
- `:api` modules of parts with screens put Navigation 3's runtime, and with it the Compose runtime,
  on a JVM classpath.
- Until step 4 of the migration, other ADRs name modules that no longer exist, and are read through
  the table in "What moved".
- ADR-014's `:core:<area>:ui` module type is gone. Its rules 4 and 5 still apply to SDK-step code,
  which now lives inside the `:impl` of a part with screens.

## Rules

1. `[enforced]` `:common:*` modules depend only on `:common:*` modules.
2. `[enforced]` No `:device` module depends on an `:auth` module.
3. `[enforced]` The only `:auth` module that depends on a `:device` module is `:auth:settings:impl`,
   and it depends only on `:device:settings:api`.
4. `[enforced]` Only `:app` depends on `:impl` modules.
5. `[enforced]` An `:impl` depends only on `:api` modules, `:common:util` and `:common:designsystem`,
   and a `:device` `:impl` also on `:device:host` (plus testing modules from test configurations).
6. `[enforced]` An `:api` is a JVM module with no Android dependencies, and depends only on other
   `:api` modules and `:common:util`.
7. `[enforced]` Testing modules are only used from test configurations.
8. `[enforced]` `:device:host` depends only on `:common:designsystem` and `:common:util`, and only
   `:device` `:impl` modules depend on it.
9. `[convention]` A part is split into `:api` and `:impl`. It has a `:testing` only when it has a
   fake, and an `:api` only when another module needs something from it. `:common:util`,
   `:common:designsystem`, `:common:testing` and `:device:host` are single modules.
10. `[convention]` An area's screens and its data source are in different parts. A part with
    screens reaches data through a data part's `:api`, and a data part never depends on
    `:common:designsystem`.
11. `[convention]` A part with screens declares its keys and its entry contract in its `:api`. The
    contract takes every exit as a lambda, adds the entries of the part's children, and keeps the
    entries of the app stack apart from those of the screen stack.
12. `[convention]` A part with screens names another part's keys only as its parent, to map that
    part's exits. It never names a sibling's keys, except through the generic exit.
13. `[convention]` `:app` declares no navigation key, entry, menu or list of features. In code it
    uses only `:auth:data:api`, `:auth:ui:api`, `:device:ui:api` and `:common` modules.
14. `[convention]` `:app` owns the app stack, and only `:app` pushes, pops or replaces its entries.
    On the screen stack inside the display, only `:device:ui:impl` pushes, and the host pops.
15. `[convention]` The main menu is a fixed list in `:device:ui:impl`. Hiding a row is a
    preference, not a contribution.
16. `[convention]` The order of the settings list is the list of slots in `:device:settings:api`.
    A section is contributed through a Hilt set and fills one slot, and a slot has at most one
    section.
17. `[convention]` The generic "open this key as a full screen" exit is used only by the main
    menu's Settings row and by contributed settings sections, and a section opens only keys of its
    own area.
18. `[convention]` When a `:common` module needs something from a product area, it declares the
    interface in its own `:api`, and the area implements it.
19. `[convention]` A use case belongs to the data part of the area that owns its outcome
    (ADR-021). There is no shared domain module.
20. `[convention]` `:app` has a test of the assembled graph for every contribution that should be
    present, and for every one that shouldn't.

**Conformance.** Rules 1 to 8 are checked by `ModuleRules.kt` when the build is configured, and
`ModuleRulesTest` in `build-logic` has a violating and a passing case for each. The slots of rule
16 are tested by `SettingsSectionsTest` in `:device:settings:impl`, and rule 20 is
`AssembledGraphTest` in `:app`'s instrumented tests, which checks that the settings list has the
account section and no other.
