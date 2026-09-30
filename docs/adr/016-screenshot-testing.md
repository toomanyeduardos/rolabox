# ADR-016: Screenshot tests with Paparazzi, run on the JVM with goldens in Git LFS

- **Status:** Accepted
- **Date:** 2026-09-30
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox's look is its product: brushed metal, an LCD status bar, a recessed field, a skin and an
accent that can change ([ADR-015](015-design-system-owns-visual-language.md)). Nothing in the build
notices when that look breaks. A unit test can't see that a color went wrong in dark theme, that text
at a large font size is cut off, or that a change to a shared component moved every screen that uses
it. Today `@PreviewLightDark` previews are the only check, and someone has to open them.

Some facts shape the choice of tool:

- Every screen is already split into a ViewModel-backed route and a stateless screen that takes its
  state, and the screens have `@PreviewLightDark` previews. The inputs for screenshots exist.
- CI runs on `ubuntu-latest` with no emulator ([ADR-008](008-one-app-with-offline-mode.md): nothing
  talks to Firebase, so nothing needs a device), and the developer also works on macOS.
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
`rolabox.android.ui` or `rolabox.android.feature`, so only modules with UI worth snapshotting pay for
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
the **test fixtures of `:core:designsystem`**, because preview tooling is the design system's
([ADR-015](015-design-system-owns-visual-language.md)). The plugin adds those fixtures to every module
that applies it. Goldens are in `src/test/snapshots/` next to the test, named after the composable and
the render (`SignInScreen_SignInScreenPreview_Dark.png`), so adding a preview doesn't rename the
others.

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
- **Put the harness in `:core:testing`.** Keeps test code together. But it would be a dependency of
  every module that uses `:core:testing`, including the ones with no UI, and it would make Paparazzi
  something modules get without applying the plugin, which is what the first check rejects.
- **Bundle the plugin into `rolabox.android.ui` and `rolabox.android.feature`.** A new module
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
- Goldens are recorded on macOS and verified on Linux in CI. Earlier experience says they match, but
  rendering differences between operating systems are possible. If a CI run shows them, the
  `maxPercentDifference` threshold is the escape hatch, and recording on the CI platform the fallback.
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

**Conformance.** Rule 1 is checked by `ModuleRules.kt` and rule 2 by the `checkScreenshotTests`
task, both part of `./gradlew check`, and the second also runs before every build. Rules 6 and 8 are
the CI `screenshots` job: Paparazzi's tests fail on a missing or different golden, and the workflow
uploads the diffs. Rule 7 is partly held by `.gitattributes`.
