# ADR-018: Device navigation: a linear screen stack driven by the wheel

- **Status:** Accepted
- **Date:** 2026-10-01
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-10-01:** The screen stack, its `NavDisplay` and the routing of wheel events moved
  from `:app` to a device host module, `:core:device` ([ADR-019](019-device-host-module.md)). `:app`
  gives the host its screens, decides every push and handles the playback buttons, and the host
  pops (Decision, Consequences, rules 1, 3, 5 and 12). Building the screen stack showed that this
  mechanism names no feature, and that a feature's device screens couldn't reach it inside `:app`.
  What each control does, and every other rule, is unchanged.

## Context

The signed-in app is drawn as a music player device: an aluminum body with a small **display** at
the top and a **wheel** below it. The display is the only part that changes. The designs are in
`player-designs/device`.

This gives the app a navigation inside its navigation:

- **The display shows one screen at a time, from a tree.** The main menu lists Music, Podcasts,
  Audiobooks, Shuffle Songs and Settings. Music opens Artists, an artist opens its albums, an album
  opens its songs, and a song opens Now Playing. Going deeper slides the new screen in from the
  right, and going back slides it out.
- **The display is not touched.** Every interaction goes through the wheel: turning it, its center
  button, and the MENU, ⏮, ⏭ and ⏯ buttons on its ring.
- **The same control means different things.** Turning the wheel moves the highlight on a list, and
  changes the volume on Now Playing.
- **Playback outlives the screens.** Music keeps playing while the user browses Podcasts, and a
  record in the display's header spins while it does.

[ADR-012](012-navigation.md) chose Navigation 3 and gave `:app` the navigation state, but it was
written for an app with a navigation bar: one back stack per top-level section, list-detail layouts
on larger screens, and screens that are tapped. None of those exist on the device. It also left
"persistent UI outside the destinations" undecided, and on the device that is the whole body and
the wheel.

ADR-012 does not say who owns the screen stack inside the display, how a screen that can't be
tapped receives input, what each wheel control does and who decides it, or how the system's back
relates to MENU. [ADR-017](017-accessibility.md) requires text to follow the system font scale,
and the display is small.

**Target state.** The device isn't built yet. This ADR is written before its first ticket, so the
rules below apply to code as it is written, and none of them has a build check yet.

## Decision

We will build the signed-in app as **one device destination** in `:app`'s navigation, with a
**screen stack** inside its display that only the wheel changes.

### Two stacks, with `:app` deciding what is on both

- **The app stack** is ADR-012's: the auth flow, the device, and any **full screen** opened from
  the device. The device is a single entry of it.
- **The screen stack** is what the display shows. It is a second Navigation 3 back stack with its
  own `NavDisplay`, placed inside the display. It starts at the main menu, and it is linear: there
  are no sections and no tabs. It is run by the **device host**, `:core:device`
  ([ADR-019](019-device-host-module.md)), which `:app` gives the screens to.

Everything ADR-012 says about a back stack holds for the screen stack: its keys are serializable
and carry identifiers, features declare the keys and entries of their own screens, only `:app`
pushes, and saved state and ViewModels follow the entry. The host pops it, on MENU and on back. A
feature provides its **device screens** and its **full screens** as separate sets of entries, and
`:app` adds each to the stack it belongs to.

**The highlight is the entry's saved state.** Each list remembers its highlighted row for as long
as its entry is on the stack, so MENU returns to the row the user left, also after process death.

**The main menu belongs to `:app`,** as a destination no feature owns (ADR-012). It gains a
**Now Playing** row while an item is loaded.

### The wheel is an input device with a fixed vocabulary

The wheel reports events, and knows nothing about what is on the display:

| Event | Meaning |
| --- | --- |
| Turn | The wheel was turned a number of steps, clockwise or counter-clockwise |
| Center | The center button was pressed |
| MENU | The MENU button was pressed |
| Hold MENU | The MENU button was pressed and held |
| ⏮, ⏭ | The previous or the next button was pressed |
| Hold ⏮, hold ⏭ | The previous or the next button is being held, until it is released |
| ⏯ | The play/pause button was pressed |

These say what the user did to the wheel, not what it does. What each event does is decided by the
host, `:app` and the screen, in the table under "What each control does".

A turn arrives as steps, not as an angle. The wheel turns the angle into steps and applies the
**acceleration** for long lists, so every screen scrolls the same way and none of them does that
math.

The device host receives every event and decides where it goes:

- **Turn and center go to the screen on top of the screen stack,** and to no other.
- **MENU and its hold are handled by the host,** the same way on every screen.
- **⏮, ⏭ and their holds are handled by `:app`,** the same way on every screen. The host passes
  them to handlers that `:app` provides, so it doesn't depend on the playback area.
- **⏯ goes to the top screen first.** If the highlighted row can be played, the screen plays it.
  On any other row, and on Now Playing, `:app` handles it, the same way everywhere.

A device screen has no tap, click, drag or scroll handling of its own. It still **reports its exits
as lambdas with identifiers** (ADR-012, rule 5): center on an artist calls
`onArtistClick(artistId)`, and `:app` turns it into a key and pushes it. What changes is what
triggers the exit, not how it is reported.

### What each control does

An item is **loaded** from the moment it starts until its queue ends. A paused item is loaded.

| Control | On a list | On Now Playing |
| --- | --- | --- |
| Turn | Moves the highlight | Changes the volume. In scrub mode, moves the position |
| Center | Opens the highlighted row, or plays it | Enters scrub mode |
| MENU | Goes back one screen. Does nothing on the main menu | Goes back one screen |
| Hold MENU | Goes to the main menu | Goes to the main menu |
| ⏮ ⏭ | Previous or next item if one is loaded. Otherwise nothing | Previous or next item |
| Hold ⏮ ⏭ | Seeks in the loaded item. Otherwise nothing | Seeks |
| ⏯ | On a playable row, plays it. On any other row, plays or pauses the loaded item | Plays or pauses |

- **What the wheel does depends on the screen, not on playback.** Turning changes the volume only
  on Now Playing. That is what lets the user browse while something plays.
- **What ⏮ and ⏭ do depends on playback, not on the screen.** They never move in the tree.
- **⏯ on a playable row does what center does:** it plays that row in place of what was loaded,
  and Now Playing is pushed. A playable row is the last level of the tree, such as a song or an
  episode. On every other row ⏯ plays or pauses the loaded item, and does nothing when nothing is
  loaded. So on a list of songs ⏯ never pauses: pausing is done from a list that isn't playable,
  or from Now Playing.
- **Scrub is a mode of the Now Playing screen, not a destination.** Center enters it, turning moves
  the position, and it ends on its own after a few seconds without a turn. It is the screen's own
  state, and the screen stack doesn't change.
- **Playing a row starts its list from that row.** Song 3 of 10 is followed by songs 4
  to 10. `:app` then pushes Now Playing.
- **Now Playing is only ever the top of the screen stack.** It is pushed by playing something, or by
  the main menu's Now Playing row. When the queue ends, nothing is loaded: `:app` pops Now Playing
  if it is showing, and the main menu's row goes away.
- **The volume is the system's media volume.** The wheel and the hardware keys change the same
  value, and each shows the other's change.

### The system's back

Inside the tree, the system's back does what MENU does. On Now Playing in scrub mode, it leaves
scrub first. **On the main menu it leaves the app,** where MENU does nothing: Android expects back
to leave from an app's first screen, and predictive back previews the home screen on that gesture.
Playback continues after leaving.

### Playback state lives above the screens

What is loaded, whether it is playing, its position and its queue belong to the playback area, and
are exposed as `Flow`s ([ADR-006](006-async-api-shape.md)). `:app` observes them for the header's
record, the Now Playing row and the ⏮ ⏭ ⏯ buttons, and the Now Playing screen observes them for its
content. No entry's ViewModel owns playback state, since every entry can be popped while the music
continues. The playback engine, its service and the media session are left to their own ADR.

### Full screens

Screens that need touch or the keyboard are not device screens: the auth flow, Settings and the
account. A full screen is an ordinary Material screen, like Sign in: it fills the window, it is
operated by touch, and the device's body, display and wheel are not drawn. It is an entry on the
app stack, pushed on top of the device.

**Settings is the way out of the device.** The main menu's Settings row opens Settings as a full
screen, and everything under it stays outside the device. A row on the display opens a full screen
by reporting an exit, like any other, and back returns to the device with its screen stack as it
was. A screen is one or the other: a device screen has no touch handling, and a full screen doesn't
use the wheel.

### Where the code lives

- **`:core:designsystem`** has the device's body, the display's frame and header, the list rows and
  their highlight, the wheel, and the wheel's event vocabulary. None of them knows about an area
  ([ADR-015](015-design-system-owns-visual-language.md)).
- **`:core:device`** has the assembled device, the screen stack and its `NavDisplay`, the routing
  of wheel events, and the contract a device screen uses to receive turn and center
  ([ADR-019](019-device-host-module.md)).
- **`:app`** has the device destination, the main menu, the entries it gives to the host, the
  mapping from exits to keys, and the handlers of the playback buttons.
- **Features** have their device screens and ViewModels. They use the design system's rows and
  receive turn and center events through the host's contract. They don't know the wheel's shape, or
  that other features exist ([ADR-003](003-module-boundaries.md)).

### One layout, in portrait

The app is locked to portrait, for every screen, since it has one activity. The device has one
layout, and there are no side-by-side scenes.

### Large text

**Text on the display follows the system font scale**
([ADR-017](017-accessibility.md)). Rows grow with their text, so fewer of them fit, and the list
still reaches every row with the wheel. Now Playing can't scroll, since the wheel is its volume, so
its layout has to fit the display at 1.5x by giving space to the text first and to the cover art
last.

**The wheel doesn't scale:** its MENU label and its icons keep their size, as parts of a control
whose shape is fixed. That is an exception to ADR-017's rule 2, and it is marked where it is made,
as ADR-017's rule 10 requires.

### What was not decided

- **Text input on the device,** which search needs. A letter picker driven by the wheel and a full
  screen with the system keyboard are both open, and it will be decided with the first search
  ticket, as a revision of this ADR.
- **Screen readers.** A display that isn't touched and a wheel that is a custom gesture both need a
  TalkBack design. It comes as a section of ADR-017, and may add rules here.
- **Queue rules** beyond "from the selected row to the end": shuffle, repeat, and what ⏮ does in the
  middle of an item. They belong to the playback ADR.

## Alternatives considered

- **One stack: device screens as entries of the app stack.** No second `NavDisplay`. But the body
  and the wheel would be drawn by every entry or around the whole `NavDisplay`, so the auth and
  Settings screens would need a way out of them, and a transition between two device screens would
  move the whole device instead of the display's content.
- **Keep ADR-012's sections,** with Music, Podcasts and Audiobooks each keeping a stack. It would
  remember where the user was in each. But the device has no control to switch sections with, and
  the design is a tree walked with MENU.
- **Features provide data and `:app` draws every screen.** A feature would return rows and what
  each one opens, and no feature would have a composable on the device. It guarantees that every
  list looks and behaves the same. But Now Playing isn't a list, rows differ by area (an unplayed
  dot on a podcast), and features would stop owning their screens, which ADR-003 gives them.
- **A touchable display, with the wheel as a second way in.** Easier to use and to make accessible.
  But two input paths per screen means two behaviors to design and test for each one, and the
  device stops being the product's idea.
- **The wheel's meaning decided by playback** (turning changes the volume whenever something
  plays). Simpler to state. But the user then can't browse while music plays.
- **Every event goes to the top screen, which forwards what it doesn't use.** One path for input.
  But MENU and the playback buttons must behave the same everywhere, and each screen would be a
  place to get that wrong.
- **Back does nothing on the main menu, like MENU.** One meaning for "back". But the app would trap
  the system's gesture, and the user would have no way to leave except Home.
- **A hand-rolled stack for the display,** since it is a simple list of screens. No nested
  `NavDisplay`. But saved state, a ViewModel per entry and transitions would be rebuilt by hand,
  which ADR-012 already rejected for the app.
- **Revise ADR-012 to include all of this.** One navigation ADR. But the wheel's vocabulary and the
  control table are a decision of their own, and ADR-012 would double in size.

## Consequences

- The whole browsing experience is one mechanism: a new level of the tree is a key, an entry and an
  exit, with no navigation code of its own.
- The control table is in one place, and the host and `:app` apply the parts that are the same
  everywhere, so a new screen can't change what MENU or ⏭ does.
- The screen stack and the routing of events are ordinary state and code, tested on the JVM in
  `:core:device`, without any feature.
- `:app` grows: the main menu and the playback buttons are there, not in a feature.
- Two nested `NavDisplay`s, each with its own back handling, are not a common setup, and how they
  share the system's back and predictive back will be worked out on first use.
- A device screen can't be operated by touch at all. Until the screen reader section exists, the
  device is not usable with TalkBack, and that is a known gap, not an accepted end state.
- At large font scales the display shows few rows, and Now Playing has little room. The design has
  to be checked at 1.5x for every screen ([ADR-016](016-screenshot-testing.md)).
- The display's size comes from the device's layout and not from its text, which ADR-017's check on
  fixed heights around text is likely to flag. It is laid out without a fixed height, or it is an
  exception marked as ADR-017's rule 10 requires.
- Tablets, foldables and landscape get the same portrait device. Android ignores an app's
  orientation lock on large screens, so there the device has to be drawn at its own proportions in
  whatever window it gets.
- The sign-in screens are locked to portrait too.
- Search can't be built on the device until text input is decided.

## Rules

1. `[convention]` The signed-in app is one device destination on `:app`'s app stack. What the
   display shows is a second, linear back stack, the screen stack, that starts at the main menu. The
   device host runs it and pops it, and `:app` decides every push (ADR-019).
2. `[convention]` Device screens follow ADR-012 for keys, entries, exits and state. A feature
   provides its device screens and its full screens as separate sets of entries.
3. `[convention]` A device screen has no tap, click, drag or scroll handling. Its only inputs are
   the wheel events the device host gives it.
4. `[convention]` The wheel reports events from a fixed vocabulary and knows nothing about the
   screens. A turn is a number of steps, with acceleration already applied.
5. `[convention]` Turn and center go to the top screen only. MENU and its hold are handled by the
   device host, and ⏮, ⏭ and their holds by `:app`, and no screen changes what they do. ⏯ plays the
   highlighted row when that row can be played, and is handled by `:app` everywhere else.
6. `[convention]` What turn and center do depends on the screen. What ⏮ and ⏭ do depends on
   whether an item is loaded. The table in Decision is the reference for every control.
7. `[convention]` A list's highlight is saved state of its entry, and is restored when the user
   comes back to it.
8. `[convention]` Now Playing is only ever the top of the screen stack. Its modes, such as scrub,
   are state of the screen and not destinations.
9. `[convention]` The system's back does what MENU does, except on the main menu, where it leaves
   the app.
10. `[convention]` Playback state belongs to the playback area and is observed as `Flow`s. No
    entry's ViewModel owns it.
11. `[convention]` A screen that needs touch or the keyboard is a full screen on the app stack, not
    a device screen. No screen mixes the two. Settings and everything under it are full screens,
    opened from the main menu.
12. `[convention]` The device's body, display, rows and wheel, and the wheel's event vocabulary,
    live in `:core:designsystem`. The screen stack and the routing of events live in `:core:device`
    (ADR-019). The main menu and the mapping from exits to keys live in `:app`.
13. `[convention]` The app is locked to portrait, and the device has one layout.
14. `[convention]` Text on the display follows the system font scale (ADR-017). The wheel's label
    and icons don't, as an exception marked under ADR-017's rule 10.
