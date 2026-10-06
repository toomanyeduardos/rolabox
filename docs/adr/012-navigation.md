# ADR-012: Navigation with Jetpack Navigation 3, with the back stacks owned by `:app`

- **Status:** Accepted. Rules 1 and 4 superseded by [ADR-020](020-modules-by-product-area.md), which
  also adds an exception to rule 5
- **Date:** 2026-09-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-28:** [ADR-008](008-one-app-with-offline-mode.md) replaced the offline and cloud
  flavors with one app, so every build has the same destinations. Entries no longer depend on the
  flavor: all of them are in `src/main`, and whether the user starts on Sign in is decided at
  runtime by the start destination (Context, Decision, rule 8).
- **Revised 2026-09-28:** The flavors are gone (`m1/flavor-removal`), so rule 8 holds, and it's a
  convention now. The decision is unchanged.
- **Revised 2026-10-01:** The signed-in app is a device with a display and a wheel
  ([ADR-018](018-device-navigation.md)), not an app with a navigation bar. The top-level sections,
  their back stacks and the Material navigation bar behavior were removed, and so were the
  side-by-side layouts for larger screens (Context, Decision, Consequences, rules 3 and 7). The app
  stack now holds the auth flow, the device and the full screens opened from it. The library, the
  ownership of navigation state by `:app`, and the rules for keys, entries and exits are unchanged,
  and ADR-018 applies them to the stack inside the display.
- **Revised 2026-10-01:** The stack inside the device's display is run by the device host,
  `:core:device` ([ADR-019](019-device-host-module.md)). The host pops it, on MENU and on back, and
  `:app` still decides every push (Decision, Consequences, rule 1). Before this, only `:app` could
  change any navigation state, which kept a mechanism that names no feature inside `:app`. Features
  still don't touch navigation state.

## Context

Rolabox needs one navigation model for the whole app:

- **A device inside the app.** The signed-in app is a music player device, whose display shows a
  tree of screens that is walked with a wheel ([ADR-018](018-device-navigation.md)). Some screens,
  such as the account's forms, are ordinary screens shown over it.
- **Links across features.** A search result opens an artist, an artist opens an album, an album
  opens a song, and a podcast opens an episode. These screens live in different feature modules.
- **Flows that end somewhere else.** Sign in leads to Create account and to Forgot password, and a
  successful sign-in or sign-up leaves the auth screens for the device with no way back to them.
- **One layout.** The device looks the same on every screen size, in portrait.

[ADR-003](003-module-boundaries.md) already settles part of this: features never depend on each
other, and navigation between features is wired in `:app`. It doesn't say which navigation library
to use, who owns the back stacks, or how one feature opens another's screen. There is one app
([ADR-008](008-one-app-with-offline-mode.md)), so every build has the account screens, and a user
in offline mode simply never opens them.

Navigation 3 is stable and is Google's recommended navigation library for Compose apps. It works
differently from Navigation Compose: a back stack is a plain list of keys held in Compose state,
which the app owns and changes directly, and `NavDisplay` shows what the list resolves to. There is
no `NavController` and no `NavHost` graph. Layouts that show more than one entry at once, such as
list-detail, are *scene strategies* applied to the same back stack.

## Decision

We will use **Jetpack Navigation 3**, following its own patterns, and `:app` will own all
navigation state.

- **Destinations are `NavKey`s.** Each destination is a `@Serializable` class or object that
  implements `NavKey`. Back stacks are saved across process death, so keys must be serializable and
  carry identifiers (an artist id), never models (an `Artist`).
- **`:app` owns the navigation state.** The app stack holds the auth flow, the device, and the
  full screens opened from the device. The device is one entry, and the stack of screens inside its
  display is a second back stack (ADR-018). `:app` decides what is on both: every push and every
  replacement is made by `:app`, in code that responds to the exits a screen reports. On the app
  stack `:app` also pops. On the stack inside the display, popping is done by the device host
  (ADR-019), since MENU and back do the same on every screen. Back at the first screen of the app
  stack leaves the app.
- **Keys are declared where the screen lives.** A feature declares the keys of its own screens and
  provides their entries as one function, which `:app` adds to the `entryProvider`. This is
  Navigation 3's modularization pattern. Destinations that no feature owns are declared in `:app`.
- **Screens report exits as lambdas, with identifiers.** A screen composable takes plain lambdas
  for the ways out of it: `onBack`, `onCreateAccountClick`, or, across features,
  `onArtistClick(artistId)` and `onEpisodeClick(episodeId)`. A feature's entries function takes
  those exits as parameters, and `:app` turns each one into the other feature's key and pushes it.
  So Search can open an artist without knowing the artist feature exists, and features stay
  independent (ADR-003).
- **Leaving a flow replaces its stack.** When sign-in or sign-up succeeds, `:app` replaces the
  navigation state with the device, so back never returns to the auth screens. The auth flow is a
  single back stack of its own.
- **There are no adaptive layouts.** The device has one layout (ADR-018), so there are no
  side-by-side scenes, and a screen never checks the window size to decide how to lay itself out.
- **State follows the entry.** `NavDisplay`'s entry decorators keep saveable UI state and
  ViewModels per entry, and clear them when the entry is popped. ViewModels are created in the
  entries, so each screen gets its own, and two artist screens on the same stack don't share one.
- **Every build has the same entries.** All entries, the account screens included, are added from
  `src/main` (ADR-008). Which one the app opens on is decided at runtime by the start destination,
  not by the build.

## Alternatives considered

- **Navigation Compose (Navigation 2).** The more established library, with more third-party
  material, built-in deep links, and proven Hilt and testing integration. It supports multiple back
  stacks through `saveState`/`restoreState` on navigation calls, but that state lives inside the
  `NavController`, where it can't be read or tested directly, and list-detail layouts need a
  separate component outside the graph. It's built on the Fragment-era `NavController` and graph,
  and Google has published a migration guide from Nav2 to Nav3, so choosing Nav2 now would mean
  migrating later with a much larger graph.
- **A hand-rolled `when` over state.** No dependency, and enough for two destinations. But back
  handling, transitions, saved state, per-screen ViewModel scoping and multiple back stacks would
  all be rebuilt by hand, and every screen we add makes it worse.
- **All keys and entries in `:app`.** One place to read every destination, and features don't
  depend on the navigation library at all. But it separates each screen from its key, and with a
  full music library it moves dozens of feature-owned declarations into `:app`. The coupling it
  would avoid doesn't exist, because features already can't see each other's keys.
- **Keys in separate `:api` modules per feature**, so one feature can push another's key itself.
  This is the case ADR-003 considered and postponed. It removes the lambda-to-key mapping in `:app`,
  at the cost of twice the feature modules. We'll reconsider if that mapping becomes the main cost
  of adding a screen.
- **A third-party navigation library** (Voyager, Decompose). Mature multi-stack support, but a
  dependency outside the Jetpack set for a need the platform library now covers.

## Consequences

- Navigation state is ordinary state: `:app` reads it, changes it and tests it directly, including
  leaving a flow and back behavior, with no Android framework.
- Back handling, transitions, saved state, per-entry ViewModels and adaptive scenes come from the
  library.
- The navigation state that holds the app stack, the auth flow and the device's screen stack is our
  own code, and we maintain it. The screen stack's part is in `:core:device` (ADR-019).
- Every link between features goes through `:app`. That's one lambda per exit and one mapping in
  `:app`, which is more code than a feature pushing a key, and it's the price of feature
  independence.
- Deep links (a shared artist or episode link) aren't handled by the library as they are in
  Navigation Compose. When the first one is needed, `:app` will parse the link into keys and build
  the back stacks itself.
- Feature modules depend on the Navigation 3 runtime to declare keys and entries, and on
  `kotlinx-serialization` for the keys. Both are new dependencies in the version catalog.
- Navigation 3 is newer than Navigation Compose, so there is less community material, and some of
  its integrations (such as how Hilt scopes ViewModels to entries) will be worked out on first use.
- The start destination from `StartupViewModel` is applied when the navigation state is created. It
  doesn't drive the screen on later changes: after that, `:app` changes the state.
- Persistent UI outside the destinations, which is the device's body and wheel, is decided in
  ADR-018.

## Rules

1. `[convention]` `:app` owns the app stack and its `NavDisplay`, and decides what is on every
   stack: only `:app` pushes or replaces entries. Only `:app` pops the app stack. The stack inside
   the device's display is popped by the device host (ADR-019).
2. `[convention]` Destinations are `@Serializable` classes or objects that implement `NavKey`, and
   carry identifiers, not models.
3. `[convention]` The app stack holds the auth flow, the device and the full screens opened from
   it. There are no top-level sections. The stack inside the device's display follows ADR-018.
4. `[convention]` A feature declares only the keys of its own screens, and never refers to another
   feature's keys.
5. `[convention]` A screen composable receives its exits as lambdas, and exits that open another
   feature's screen pass identifiers. Screens don't touch navigation state.
6. `[convention]` A flow that ends in another part of the app (such as sign-in to the device)
   replaces its own back stack, so its screens can't be reached with back.
7. `[convention]` There are no side-by-side layouts. No screen changes how it is arranged based on
   window size (ADR-018).
8. `[convention]` Every entry is added from `src/main`, and no entry depends on the build
   configuration (ADR-008).
