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
- **Revised 2026-10-08:** The host also pops when `:device:ui:impl` asks it to take a screen off
  the top, and only if that screen is on top (What `:device:host` owns, Who changes the stack, rule
  3). [ADR-018](018-device-navigation.md) pops Now Playing when the queue ends, and the end of the
  queue is playback state, which the host can't see (rule 5). Wiring Now Playing (38.06) needed it.
  The host still does the pop and still decides no push.
- **Revised 2026-10-10:** The host no longer handles the system's back (What `:device:host` owns,
  Who changes the stack, rule 3). On every device screen back leaves the app
  ([ADR-018](018-device-navigation.md), rule 9), so the `NavDisplay` inside the display has no back
  handling, and back reaches whoever shows the device from any depth. The host still pops on MENU,
  goes to the first screen on hold MENU, and pops the screen it is asked to.
- **Revised 2026-10-12:** The header shows the title of the screen on top of the screen stack, and
  the screen gives it through the host's contract. `:device:ui:impl` no longer gives the host a
  title, and gives it the state of the header's record (What `:device:host` owns, What `:app` gives
  it, The header, Alternatives, Consequences, rules 3 and 6, new rule 9, Conformance). A review of
  the project on 2026-10-11 found that the header said "Rolabox" on every screen and never showed
  the record ([ADR-018](018-device-navigation.md), rule 19). The title is given by the screen, and
  not by its entry, because some titles are loaded: an artist's name. Rule 9 is `[enforced]` for
  who draws the display, by a new check, `DeviceDisplayOutsideHost`. The contract and the record
  come with 39.03.
- **Revised 2026-10-12:** The contract lets the top screen take ⏯ while its highlighted row can be
  played, and ⏯ reaches the handlers of the playback buttons only when it doesn't (What
  `:device:host` owns, rule 6). [ADR-018](018-device-navigation.md)'s rule 5 has ⏯ play the
  highlighted row, and the highlight is the screen's own state, so the handler of the playback
  buttons can't know which row it is. Building ⏯ (39.02) needed it. It is the same shape as MENU
  for a mode. ⏮, ⏭, their holds, hold MENU and the system's back still never reach a screen.
- **Revised 2026-10-12:** The contract for the title, and the record, are in the code (Conformance).
  39.03 built them: a screen gives its title with `ScreenTitle`, and `:device:ui:impl` gives the host
  whether an item is loaded and whether it is playing. Their unit tests are `ScreenTitlesTest`, in
  `:device:host`. No rule changes.
- **Revised 2026-10-12:** The contract lets the top screen be told of the playback buttons while it
  is in a mode of its own (What `:device:host` owns, rule 6, Conformance).
  [ADR-018](018-device-navigation.md)'s rule 16 has ⏮, ⏭, ⏯ and the holds end scrub before they do
  what they do, also when they change nothing in the playback state, so the screen can't learn of
  them from that state. Building ⏮ (39.04) needed it. The screen is told and doesn't take the
  button: the handlers receive every one, as before, and the screen isn't told which one it was.

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
  screen.
- **The display's header:** the title of the top screen, the battery level and the record. No
  screen draws a display or a header of its own.
- **The screen stack:** its state, saved across process death, and the `NavDisplay` inside the
  display, with its transitions.
- **Back inside the display.** The host pops on MENU and goes to the first screen on hold MENU. It
  doesn't handle the system's back: on every screen, at any depth, back is left to whoever shows
  the device, and ADR-018 says what it does. The host also pops a screen that whoever assembles the
  device names, if that screen is on top: Now Playing when the queue ends.
- **The routing of wheel events** (ADR-018, rule 5): turn and center go to the top screen, MENU and
  its hold are the host's own, and ⏮, ⏭, ⏯ and their holds are passed to handlers. While the top
  screen asks for MENU, to leave a mode of its own, MENU goes to it and the host doesn't pop. While
  the top screen asks for ⏯, to play its highlighted row, ⏯ goes to it and not to the handlers.
  While the top screen asks to be told of the playback buttons, to leave a mode of its own, it is
  told before the handlers, and the handlers still receive the button.
- **The contract its screens use:** how a device screen receives turn and center, how it takes MENU
  while it is in a mode and ⏯ while its highlighted row can be played, how it is told of a playback
  button while it is in a mode, how it gives the header its title, and the state holder for a list's
  highlight. A screen asks for "the events meant for me" and says "this is my title". It doesn't
  name its key, and it never sees the stack or the router.

### What `:app` gives it

- **The start key and the entries.** `:app` collects the features' device entries, as ADR-012
  describes, and hands them to the host. The main menu is one of them, and it stays in `:app`: its
  rows are the list of features, which only `:app` knows.
- **What each exit opens.** A screen reports an exit, and `:app` turns it into a key and pushes it
  on the host's stack. The host never decides what is pushed.
- **What the playback buttons do.** The host calls handlers for ⏮, ⏭, ⏯ and their holds, and `:app`
  connects them to the playback area. So the host never depends on an area.
- **The state of the header's record:** whether an item is loaded, and whether it is playing. The
  title is no longer given here: each screen gives its own (see "The header").

```
:app ── start key, entries, exits → keys, playback handlers ──▶ :device:host (host)
                                                                    │ turn, center
:feature:music ── device screens, using the host's contract ◀──────┘
:common:designsystem ── body, display, rows, wheel, WheelEvent (used by all three)
```

### The header

The header shows **the title of the screen on top of the screen stack**
([ADR-018](018-device-navigation.md), rule 19). The screen gives it through the contract, the same
way it asks for its events: it states its title while it is composed, and the host shows the title
of whichever screen is on top. A screen under the top one keeps its title for when MENU returns to
it.

The screen gives it, and not its entry or its key, because a title can be loaded. The list of an
artist's albums is titled with the artist's name, which its ViewModel reads from the library, and a
key carries only the identifier ([ADR-012](012-navigation.md)). A fixed title, such as "Artists", is
given the same way, from the screen's string resources. Until the top screen has given a title, the
header shows none.

The **record** is playback state, which the host can't see (rule 5). Whoever assembles the device
gives the host two values, whether an item is loaded and whether it is playing, and the host shows
the record spinning, still or not at all.

### Who changes the stack

ADR-012's rule becomes: **`:app` decides every push, and the host pops.** Features still don't
touch navigation state. A pop needs no knowledge of the screens, and it is the same on every one of
them, which is why the host can do it. It pops for the wheel only, never for the system's back.

One pop starts outside the host: when the queue ends, Now Playing can't stay (ADR-018). The host
doesn't know playback, so `:device:ui:impl` names the screen and the host pops it if it is on top.
That is the only pop that is asked for, and it still goes back one screen and nowhere else.

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
- **The entry gives the title,** as metadata of its `NavEntry`. Nothing to register, and the host
  reads it without the screen being composed. But the metadata is built from the key alone, before
  the screen has loaded anything, so an artist's name would have to travel in the key, where ADR-012
  wants an identifier, or the title would be a second thing the entry has to observe.
- **`:device:ui:impl` gives the title, from the key on top.** It already knows every key, and the
  host keeps one parameter. But it would have to read the library to name an artist, which is the
  screen's ViewModel's job, and a new screen would be titled in a module that isn't its own.
- **Each screen draws its own header.** The screen has everything it needs. But the header would
  slide with every transition, the battery and the record would be repeated on every screen, and
  each screen would be a place to get them wrong.
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
- Every device screen has one more thing to do: give its title. One that doesn't has an empty
  header, which shows in its golden and nowhere else.
- A preview of a screen in the device gives the title to the host's stateless device directly,
  since nothing runs the contract in a preview.
- ADR-015's rule that an area-agnostic composable lives in the design system has an exception, and
  where a new device composable goes is decided by whether it is a part or the mechanism.

## Rules

1. `[enforced]` `:device:host` depends only on `:common:designsystem` and `:common:util` (plus testing
   modules from test configurations).
2. Superseded by [ADR-020](020-modules-by-product-area.md), rule 8: only `:device` `:impl` modules
   depend on the host.
3. `[convention]` `:device:host` owns the assembled device with the display's header, the screen
   stack and its `NavDisplay`, back inside the display, and the routing of wheel events. It pops, and it never decides what is
   pushed. MENU pops unless the top screen takes it through the contract (rule 6). The one pop it
   is asked for is a named screen, popped only if it is on top: Now Playing when the queue ends.
   It doesn't handle the system's back, and its `NavDisplay` has no back handling.
4. Superseded by [ADR-020](020-modules-by-product-area.md), rules 14 and 15: `:device:ui:impl` gives
   the host its start key, its entries and the handlers of the playback buttons, pushes on the
   screen stack, and declares the main menu.
5. `[convention]` `:device:host` has no navigation destination, no ViewModel and no Hilt module,
   and depends on no area. What it needs from the product arrives as a parameter.
6. `[convention]` A device screen receives turn and center through `:device:host`'s contract, and
   MENU only while it is in a mode of its own, to leave it, and ⏯ only while its highlighted row can
   be played, to play it. While it is in a mode of its own it is told that a playback button was
   pressed, to leave the mode: it isn't told which, and the button still goes to the handlers. It
   takes no other input: not hold MENU, ⏮, ⏭, their holds or the system's back. It gives its title through the same contract. It
   doesn't name its key, and it never sees the stack or the router.
7. `[convention]` The device's parts and their look stay in `:common:designsystem` (ADR-015).
   `:device:host` assembles them, and defines no color, text style or shape.
8. `[convention]` `:device:host` is one module. It isn't split into `:api`, `:impl` and `:testing`.
9. `[enforced]` Only `:device:host` draws the display and its header. The header shows the title
   that the top screen gives through the contract, and every device screen gives one. The state of
   the record arrives as a parameter (rule 5). Enforced for who draws the display: that every
   screen gives a title is checked in review.

**Conformance.** Rule 1 is checked by `ModuleRules.kt` when the build is configured, as
[ADR-020](020-modules-by-product-area.md)'s rule 8, which also replaces rule 2: only `:device` `:impl`
modules depend on the host. Rule 9 is checked by `DeviceDisplayOutsideHost`, a Rolabox detekt rule
that reports a call to the design system's `DeviceDisplay` or `DeviceHeader` anywhere but
`:device:host` and `:common:designsystem`, which declares and previews them. It runs in `detekt`, part
of `./gradlew check` and of the CI `build` job, and its unit tests have a violating and a passing
case. An exception is a `@Suppress` that names the rule. The contract for the title, and the record,
are behavior, with no syntax for a build check to read: they are covered by `ScreenTitlesTest`, the
unit tests of `:device:host` that 39.03 brought. The routing of rules 3 and 6, with who is told of a
playback button and when, is covered by `WheelEventRouterTest`. That every screen gives a title, and the other
rules, are reviewed.
