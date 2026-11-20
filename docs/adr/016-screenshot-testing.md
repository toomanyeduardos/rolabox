# ADR-016: Screenshot tests with Paparazzi, run on the JVM with goldens in Git LFS

- **Status:** Accepted
- **Date:** 2026-09-30
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-10-06:** Module names updated for the layout of
  [ADR-020](020-modules-by-product-area.md), now that the code has moved (37.07b). The harness is in
  the test fixtures of `:common:designsystem`, and the composite plugin that Paparazzi is not part
  of is `rolabox.android.screens` ([ADR-004](004-convention-plugins.md)). The goldens were renamed
  with their modules' packages, and none was recorded again. The decision is unchanged.
- **Revised 2026-11-11:** The link to [ADR-008](008-one-app-with-an-optional-account.md) follows its
  new file name. Context's "nothing talks to Firebase" is about CI having no Firebase project, not
  about the account choice that ADR-008 renamed and redefined the same day, so it still holds. The
  decision is unchanged.
- **Revised 2026-11-20:** Previews live in the file that creates what they show (rule 10). Five
  modules had gathered their previews into separate `*Previews.kt` files, while the rest of the app
  kept them next to their composables. No decision chose the separate files: the first one came with
  the music screens, whose files held only a route, and later modules copied it. One way is now the
  rule, checked by the `PreviewOutsideItsFile` detekt rule. Each preview writes out its own frame, so
  the device modules' `DevicePreview` and the design system's `DeviceListPreview` are gone. The
  previews moved with no change to what they show, so their goldens were recorded again under new
  names with the same pixels. "Where they live" now says that a golden is named after the file of its
  preview, which is what Paparazzi has always done.

## Context

Rolabox's look is its product: brushed metal, an LCD status bar, a recessed field, a skin and an
accent that can change ([ADR-015](015-design-system-owns-visual-language.md)). Nothing in the build
notices when that look breaks. A unit test can't see that a color went wrong in dark theme, that text
at a large font size is cut off, or that a change to a shared component moved every screen that uses
it. Today `@PreviewLightDark` previews are the only check, and someone has to open them.

Some facts shape the choice of tool:

- Every screen is already split into a ViewModel-backed route and a stateless screen that takes its
  state, and the screens have `@PreviewLightDark` previews. The inputs for screenshots exist.
- CI runs on `ubuntu-latest` with no emulator ([ADR-008](008-one-app-with-an-optional-account.md):
  nothing talks to Firebase, so nothing needs a device), and the developer also works on macOS.
- Tests should sit with the code they test, in each module, like the unit tests.
- The Android toolchain moves fast: Rolabox is on AGP 9.4, Kotlin 2.4 and JDK 25.

A screenshot test is worth having only if it is fast enough to run on every change, and stable
enough that a failure means something changed.

## Decision

We will test how UI looks with **screenshot tests written with [Paparazzi](https://github.com/cashapp/paparazzi)**,
which renders Compose on the JVM with layoutlib, the engine behind Android Studio's preview. They
need no emulator, no device and no Robolectric, and run as ordinary unit tests.

**Applied through a convention plugin.** `rolabox.android.paparazzi` (in `build-logic`,
[ADR-004](004-convention-plugins.md)) adds Paparazzi and its dependencies to a module. It is **opt-in**,
applied next to `rolabox.android.library` and `rolabox.android.compose`, and is not part of
`rolabox.android.screens`, so only modules with UI worth snapshotting pay for
it. Two checks keep it from being forgotten or bypassed:

- a module that declares Paparazzi itself, without applying the plugin, fails configuration
  (`ModuleRules.kt`, like the dependency rules of [ADR-003](003-module-boundaries.md));
- a module that has `@Preview` composables in its sources, but doesn't apply the plugin or has no
  screenshot test (a `*SnapshotTest` in `src/test`), fails the `checkScreenshotTests` task, which
  runs before every build (`preBuild`) and in `check`, like ADR-015's resource check.

**What is snapshotted: every `@Preview`.** A screenshot test doesn't list screens by hand. It scans
the module for `@Preview` composables, including `private` ones and those that come from
`@PreviewLightDark`, using
[ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner), and renders
each one. A new preview gets a snapshot with no new test, and a preview without a recorded golden
fails the build. What makes something snapshot-able is therefore what makes it previewable: a
**stateless** composable that takes its state and callbacks. The ViewModel-backed route needs Hilt and
Firebase, which the JVM has no graph for, and isn't snapshotted.

**What a snapshot covers.** One device configuration, a Pixel 6 in portrait, and three renders per
preview:

| Render | Why |
| --- | --- |
| Light, font scale 1.0 | The default. |
| Dark, font scale 1.0 | Theme coverage. |
| Light, font scale 1.5 | Large text: truncation, overflow and layout that only break when text grows. |

Dark at 1.5x is left out: the layout problems of large text don't depend on the theme, and it would
add a third more goldens for little. Tablets and landscape aren't covered yet. Changing the matrix is
a revision of this ADR.

**Where they live.** In the module's own `src/test`. Each module has a `PreviewSnapshotTest` of a few
lines that names the packages to scan and extends `PreviewSnapshot`. The harness that does the work
(scanning, the device, theme and font scale, the background `showBackground` asks for) lives once, in
the **test fixtures of `:common:designsystem`**, because preview tooling is the design system's
([ADR-015](015-design-system-owns-visual-language.md)). The plugin adds those fixtures to every module
that applies it. Goldens are in `src/test/snapshots/` next to the test, named after the file of the
preview, the preview and the render (`SignInScreen_SignInScreenPreview_Dark.png`), so adding a
preview doesn't rename the others.

**Where previews live.** A preview is in the file that creates what it shows: a screen's previews
in the screen's file, next to its route and the composables only it uses, and a component's in the
component's file. There are no files of previews alone. The code and how it looks are then read and
changed together, and a golden's name points at the file to open. Each preview writes out its own
frame, with no preview wrapper: a device screen's the `RolaboxTheme` and the `DeviceScreen` it sits
in, and a design-system component's the `RolaboxTheme` and whatever holds it, such as a
`DeviceDisplay`. The sample data a preview needs is `private` in the same file. When two files need
the same sample, each keeps its own copy, since previews of different screens shouldn't change
together.

**Goldens are in Git LFS.** `.gitattributes` tracks `**/src/test/snapshots/**/*.png`. Every preview
produces three images, and they grow with the app; LFS keeps them out of the clone history and its
size. Each contributor installs git-lfs once (`git lfs install`), and CI checks out with `lfs: true`.

**How goldens change.** A change that alters how UI looks runs `./gradlew recordPaparazziDebug`, and
commits the new images in the same pull request as the code. Reviewers read the image diffs like any
other change. CI never records: it only verifies. `./gradlew cleanRecordPaparazziDebug` also removes
the goldens of previews that no longer exist.

**In CI.** A `screenshots` job, separate from `build`, runs `./gradlew verifyPaparazziDebug` on every
pull request and push to `main`. When it fails, it uploads the actual and diff images
(`build/paparazzi/failures`) and the report as the `screenshot-diffs` artifact. The `build` job passes
`-Prolabox.skipScreenshotTests`, so the snapshots aren't run twice. Locally, the same tests run in
`./gradlew check` and `unitTest`, because they are unit tests.

**A pre-release, for now.** The Paparazzi 2.0 line is still in alpha (2.0.0-alpha05.1 at the time of
writing). It is the first release built for AGP 9, which Rolabox needs, and we ran it with AGP 9.4.1 and
Kotlin 2.4.20, which are newer than the ones it was built against. We will **move to the stable
2.0 release when it ships**.

## Alternatives considered

- **Roborazzi.** Also runs on the JVM, on top of Robolectric, which runs real Android framework code.
  It can click, scroll and capture the result, and it renders what Robolectric renders. We didn't
  pick it because Rolabox needs static renders of stateless composables, not interaction, and
  Robolectric adds a slower startup, an SDK download and another test runtime for that. It remains
  the fallback if Paparazzi falls behind a future AGP.
- **Emulator-based screenshot tests** (instrumented tests with a golden comparison, or a tool such as
  Shot or Dropshots). They render on a real Android build, so they can catch what layoutlib can't.
  But CI would need an emulator: minutes to boot, flakiness, and images that differ between API
  levels, device images and GPU settings. That is too slow and too unstable to run on every pull
  request, and it would split the tests out of the module's unit-test flow.
- **Google's Compose Preview Screenshot Testing plugin.** First-party and also based on previews.
  But it has its own `screenshotTest` source set instead of `src/test`, and its release cycle is
  tied to AGP's. We chose the tool that keeps the tests where the unit tests are.
- **Write a test per screen by hand.** No scanner dependency. But every new preview needs a matching
  test that somebody has to remember, and the ones that are forgotten are the new ones. We accept
  a third-party library to make coverage follow the previews.
- **Keep goldens in plain git.** No setup, and nothing to install. But image files can't be merged
  or compressed by git, so each change to a golden adds its full size to every clone, for good.
- **Put the harness in `:common:testing`.** Keeps test code together. But it would be a dependency of
  every module that uses `:common:testing`, including the ones with no UI, and it would make Paparazzi
  something modules get without applying the plugin, which is what the first check rejects.
- **Keep a module's previews in a `*Previews.kt` file of their own.** The screen files stay short,
  and samples shared by several screens are written once. But a screen's look is then in another
  file than its code, the goldens are named after a file that isn't the screen's, and the app had
  both layouts with no rule to choose. A long screen file is the cost we accept.
- **A shared preview wrapper (a `DevicePreview { … }` per module, one in `:device:host`, or the
  design system's `DeviceListPreview`).** Saves repeating the theme and the frame in each preview.
  But it is one more indirection to read past, and one in `:device:host` would put preview code in
  its API. `MainMenu.kt` already wrote the frame out, and that is the way kept: the design system's
  `DeviceListPreview`, which three files shared, was written out in its previews too.
- **Bundle the plugin into `rolabox.android.screens`.** A new module
  couldn't forget it. But every such module would carry Paparazzi, including one with no previews,
  and it contradicts applying it only where it is needed. The `@Preview` check closes the same gap
  without the cost.
- **Test more than previews (full screens with a ViewModel, dialogs, states reached by clicking).**
  More coverage. But it needs the Hilt graph and a fake for each dependency, and screenshot tests are
  the wrong tool for behavior, which unit tests already cover.

## Consequences

- A change to how UI looks shows up in the pull request as an image diff, and a change that looks
  different by accident fails the build.
- Every `@Preview` costs three goldens, about 15 MB in total for the 21 previews that exist
  today. Previews should stay small, since a preview that fills the screen costs the most.
- A contributor has to install git-lfs. Without it, a commit stores the full image in git history
  instead of an LFS pointer, and a clone gets pointer text files instead of images, so every golden
  looks corrupt. The README explains the setup.
- LFS has a storage and bandwidth quota. Every CI run downloads the goldens, and so does every
  clone, so the free quota is worth watching as the goldens grow.
- layoutlib isn't a real device: fonts, blur and some shaders can differ from what a phone draws. A
  passing snapshot shows the layout and colors are as recorded, not that they look right on every
  device.
- Upgrading Paparazzi, Compose, the Compose BOM or a font can change pixels, and the goldens need
  recording again in a pull request of their own, so the diff is only the upgrade's.
- Goldens are recorded on macOS and verified on Linux in CI, and they matched on the first CI run.
  Rendering differences between operating systems are still possible after a toolchain upgrade. If
  one shows up, the `maxPercentDifference` threshold is the escape hatch, and recording on the CI
  platform the fallback.
- Previews now have a second job. A preview that needs a ViewModel, or that changes from run to run
  (a clock, an animation, a random value), makes its snapshot fail or flake, and has to be fixed in
  the preview.
- There is a dependency on ComposablePreviewScanner, a small third-party library, and on a
  pre-release of Paparazzi until 2.0 ships.
- Screenshot tests don't cover behavior, the routes with ViewModels, or devices and orientations
  other than a portrait phone.

## Rules

1. `[enforced]` Paparazzi comes only from the `rolabox.android.paparazzi` plugin. A module that
   declares Paparazzi without applying it fails configuration.
2. `[enforced]` A module with `@Preview` composables applies `rolabox.android.paparazzi` and has a
   `*SnapshotTest` in its `src/test`. The `checkScreenshotTests` task fails otherwise, before every
   build and in `check`.
3. `[convention]` A preview calls a stateless composable with explicit state and callbacks. It
   doesn't need a ViewModel, Hilt, Firebase or anything that changes from run to run. The previews
   are the screenshot test: a screen that changes how it looks changes its previews.
4. `[convention]` A module's screenshot test is `PreviewSnapshotTest` in its own `src/test`, it
   extends `PreviewSnapshot` from the design system's test fixtures, and its class name ends in
   `SnapshotTest`. Modules don't write their own harness.
5. `[convention]` Every preview is snapshotted as a Pixel 6 in portrait, in light, in dark and in light at
   font scale 1.5. A different matrix is a revision of this ADR.
6. `[enforced]` Goldens are verified by `verifyPaparazziDebug` in the CI `screenshots` job on every pull
   request, and by `check` locally. A missing or different golden fails the build.
7. `[convention]` Goldens live in `src/test/snapshots/`, are tracked by Git LFS, and are recorded only
   with `recordPaparazziDebug`, and committed in the same pull request as the change that caused
   them. CI never records.
8. `[enforced]` The `screenshots` job uploads the actual and diff images as the `screenshot-diffs`
   artifact when it fails.
9. `[convention]` Paparazzi stays on its latest 2.0 release. When 2.0 is released as stable, the
   pre-release is replaced by it, and the goldens are recorded again if they change.
10. `[enforced]` A preview is in the file that creates what it shows: a screen's previews in the
    screen's file, a component's in the component's file. No file holds only previews, and no file is
    named `*Previews.kt`. Each preview writes out its own frame (`RolaboxTheme`, and the
    `DeviceScreen` or `DeviceDisplay` it sits in) with no preview wrapper, and its sample data is
    `private` to its file.

**Conformance.** Rule 1 is checked by `ModuleRules.kt` and rule 2 by the `checkScreenshotTests`
task, both part of `./gradlew check`, and the second also runs before every build. Rule 10 is
checked by the `PreviewOutsideItsFile` detekt rule (`build-logic/detekt-rules`), which fails on a
file named `*Previews.kt`, and on a preview in a file that creates no screen or component: no
composable other than the previews, helpers named `…Preview`, and `private` camelCase helpers that
return a value. That a preview is in the file of the right screen, the frame and the `private`
samples are left to review. Rules 6 and 8 are the CI `screenshots` job: Paparazzi's tests fail on a
missing or different golden, and the workflow uploads the diffs. Rule 7 is partly held by
`.gitattributes`.
