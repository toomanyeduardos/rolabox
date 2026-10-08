# ADR-019: The device is a host module, `:core:device`, that receives its screens from `:app`

- **Status:** Accepted. Rules 2 and 4 superseded by [ADR-020](020-modules-by-product-area.md)
- **Date:** 2026-10-01
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-10-06:** Module names updated for the layout of
  [ADR-020](020-modules-by-product-area.md), now that the code has moved (37.07b). The host is
  `:device:host`, and it is unchanged except for who uses it. The body names it and
  `:common:designsystem` and `:common:util`; the title keeps the name the decision was made with.
  Rules 2 and 4, and "What `:app` gives it", are superseded by ADR-020. The two rules now point at
  the rules that replaced them, and that section is left as it was written: `:device:ui:impl` gives
  the host its start key, entries and playback handlers. Conformance now points at ADR-020's rule 8.
  Where the text says "feature", read the `:impl` of a part with screens.
- **Revised 2026-10-08:** The contract lets the top screen take MENU while it is in a mode of its
  own, so the mode ends before the host pops (What `:device:host` owns, rules 3 and 6). Scrub on Now
  Playing needed a cancel ([ADR-018](018-device-navigation.md), rule 16), and without this the
  screen would have taken an input outside the host (38.05). Hold MENU, the system's back and the
  playback buttons still never reach a screen.

## Context

[ADR-018](018-device-navigation.md) put everything about the device that isn't a visual part in
`:app`: the device destination, the screen stack and its `NavDisplay`, the routing of wheel events,
the main menu, and the mapping from exits to keys. Building the screen stack showed that this is
two different kinds of code:

- **A mechanism that names no feature.** The stack, its transitions, back inside the display, the
  routing of each wheel event, and the way a screen receives the events meant for it. None of it
  knows that Music or Podcasts exist.
- **The assembly of this product.** Which screens exist, what each exit opens, and the main menu,
  whose rows are the list of features.

Keeping the mechanism in `:app` has two costs:

- **A feature's device screen can't receive wheel events.** The place where a screen registers for
  turn and center is `internal` to `:app`, and no module may depend on `:app`. The first feature
  with device screens can't be written.
- **The device can't be used without `:app`.** `:app` is meant to be the wrapper around the
  features ([ADR-003](003-module-boundaries.md)), and the device is something it shows, like the
  auth flow. A second app, or a second device in this one, would have to copy the mechanism.

[ADR-012](012-navigation.md) says only `:app` pushes, pops or replaces entries. That rule exists so
that features don't navigate. It didn't foresee a stack whose "back" is the same on every screen
and is decided by no feature.

## Decision

We will move the device's mechanism into a **host module, `:device:host`**, and `:app` will give it
the screens.

### What `:device:host` owns

- **The assembled device:** the design system's body, display and wheel put together as one
  screen, with the battery level in the display's header.
- **The screen stack:** its state, saved across process death, and the `NavDisplay` inside the
  display, with its transitions.
- **Back inside the display.** The host pops on MENU, goes to the first screen on hold MENU, and
  handles the system's back, as ADR-018 describes. On the first screen it leaves back to whoever
  shows the device.
- **The routing of wheel events** (ADR-018, rule 5): turn and center go to the top screen, MENU and
  its hold are the host's own, and ⏮, ⏭, ⏯ and their holds are passed to handlers. While the top
  screen asks for MENU, to leave a mode of its own, MENU goes to it and the host doesn't pop.
- **The contract its screens use:** how a device screen receives turn and center, how it takes MENU
  while it is in a mode, and the state holder for a list's highlight. A screen asks for "the events
  meant for me". It doesn't name its key, and it never sees the stack or the router.

### What `:app` gives it

- **The start key and the entries.** `:app` collects the features' device entries, as ADR-012
  describes, and hands them to the host. The main menu is one of them, and it stays in `:app`: its
  rows are the list of features, which only `:app` knows.
- **What each exit opens.** A screen reports an exit, and `:app` turns it into a key and pushes it
  on the host's stack. The host never decides what is pushed.
- **What the playback buttons do.** The host calls handlers for ⏮, ⏭, ⏯ and their holds, and `:app`
  connects them to the playback area. So the host never depends on an area.
- **What the display's header says:** its title.

```
:app ── start key, entries, exits → keys, playback handlers ──▶ :device:host (host)
                                                                    │ turn, center
:feature:music ── device screens, using the host's contract ◀──────┘
:common:designsystem ── body, display, rows, wheel, WheelEvent (used by all three)
```

### Who changes the stack

ADR-012's rule becomes: **`:app` decides every push, and the host pops.** Features still don't
touch navigation state. A pop needs no knowledge of the screens, and it is the same on every one of
them, which is why the host can do it.

### Core, not feature

Features build on the device: their screens use its contract. A feature can't depend on another
feature ([ADR-003](003-module-boundaries.md), rule 1), so the device can't be one. It is the
platform the features' device screens sit on, not a peer of theirs.

### One utility module

`:device:host` isn't split into `:api`, `:impl` and `:testing`. Those exist for areas, where an
interface hides an implementation. The host has nothing to hide: no Hilt binding, nothing to swap,
and what features use from it is concrete Compose code. It is a utility module, like
`:common:designsystem`.

It may depend on `:common:designsystem` and `:common:util` only, and only features and `:app` may
depend on it. It has no destination of its own, no ViewModel and no Hilt module, and it holds no
data: a screen's ViewModel gets its data from the areas, and the host never sees it.

### The boundary with the design system

The design system keeps the device's **parts** and everything about how they look: the body, the
display's frame and header, the rows, the wheel, the metal colors, and the wheel's event vocabulary
([ADR-015](015-design-system-owns-visual-language.md)). `:device:host` **assembles** those parts and
makes them work. It defines no color, text style or shape. Its composables know nothing about an
area, so ADR-015's rule 1 would place them in the design system, and that rule gets an exception
for the host.

## Alternatives considered

- **Keep the mechanism in `:app`** (ADR-018 as first written). No new module. But features can't
  reach the contract their screens need, and the device only exists inside this app.
- **Move only the screen-side contract to `:common:designsystem`,** and keep the stack and the
  routing in `:app`. The smallest change that lets a feature receive events. But the place where a
  screen registers and the place the router dispatches from are one mechanism, so part of the
  routing would live in the design system, which isn't visual language. The device would still be
  tied to `:app`.
- **`:feature:device` for the host, with the contract in `:common:designsystem`.** The host would be
  a feature that only `:app` uses, and it would declare the device's key and entry. It has the same
  split mechanism as the option above.
- **A device module that depends on the features,** with its own destinations and data. It could be
  shipped whole. But it is a second composition root: only `:app` may depend on features (ADR-003,
  rules 1 and 2), and the data belongs to the areas, not to the device. If the whole product ever
  has to be shipped as a module, `:app`'s assembly code moves into one, on top of this host.
- **Split it into `:device:host:api` and `:impl`.** Consistent with the areas. But an `:api` is
  pure JVM, and the contract is Compose code with one implementation.
- **`:device:host:ui`, as an area UI module** ([ADR-014](014-area-ui-modules.md)). The module type
  exists already. But a `:ui` belongs to an area with an `:api`, and the device has none.
- **Move the device's parts into `:device:host` too,** so the whole device is in one module. But
  the rows are used by every feature, and their look is Rolabox's visual language, which ADR-015
  keeps in one place.
- **The host depends on the playback `:api`** and handles ⏮, ⏭ and ⏯ itself. Fewer parameters. But
  a utility module would depend on an area, and the host couldn't be used without that area.

## Consequences

- A feature's device screen can be written: it depends on `:device:host` for its input and its
  highlight, and on nothing in `:app`.
- The device can be shown by another app, or twice in this one, with different screens.
- `:app` shrinks back to assembly: the entries, the exits, the main menu and the playback handlers.
- The stack and the routing are tested in `:device:host`, on the JVM, without any feature.
- There is one more module and one more module type, with its own build rules to maintain.
- ADR-012's "only `:app` changes navigation state" now has a second actor. The line between
  pushing and popping is a convention, checked in review.
- The host's parameters are its whole interface with `:app`. Each new thing the device needs from
  the product, such as the Now Playing row's state, is a parameter and not a dependency.
- Every change to `:device:host` rebuilds every feature that has device screens.
- ADR-015's rule that an area-agnostic composable lives in the design system has an exception, and
  where a new device composable goes is decided by whether it is a part or the mechanism.

## Rules

1. `[enforced]` `:device:host` depends only on `:common:designsystem` and `:common:util` (plus testing
   modules from test configurations).
2. Superseded by [ADR-020](020-modules-by-product-area.md), rule 8: only `:device` `:impl` modules
   depend on the host.
3. `[convention]` `:device:host` owns the assembled device, the screen stack and its `NavDisplay`,
   back inside the display, and the routing of wheel events. It pops, and it never decides what is
   pushed. MENU pops unless the top screen takes it through the contract (rule 6).
4. Superseded by [ADR-020](020-modules-by-product-area.md), rules 14 and 15: `:device:ui:impl` gives
   the host its start key, its entries and the handlers of the playback buttons, pushes on the
   screen stack, and declares the main menu.
5. `[convention]` `:device:host` has no navigation destination, no ViewModel and no Hilt module,
   and depends on no area. What it needs from the product arrives as a parameter.
6. `[convention]` A device screen receives turn and center through `:device:host`'s contract, and
   MENU only while it is in a mode of its own, to leave it. It takes no other input: not hold MENU,
   the playback buttons or the system's back. It doesn't name its key, and it never sees the stack
   or the router.
7. `[convention]` The device's parts and their look stay in `:common:designsystem` (ADR-015).
   `:device:host` assembles them, and defines no color, text style or shape.
8. `[convention]` `:device:host` is one module. It isn't split into `:api`, `:impl` and `:testing`.

**Conformance.** Rule 1 is checked by `ModuleRules.kt` when the build is configured, as
[ADR-020](020-modules-by-product-area.md)'s rule 8, which also replaces rule 2: only `:device` `:impl`
modules depend on the host.
