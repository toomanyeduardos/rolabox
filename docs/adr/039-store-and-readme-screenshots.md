# ADR-039: Store and README screenshots are rendered by Paparazzi at the device's resolution, on demand, apart from the goldens

- **Status:** Accepted
- **Date:** 2026-11-22
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Google Play's listing needs 2 to 8 phone screenshots, each side between 320 and 3840 px and at most
2:1. Four or more at 1080 px or more make the app eligible for Play's recommendations (launch/13). The
README needs screenshots of the device as well, and until now it showed only the auth screens
(launch/14).

The screenshot tests of [ADR-016](016-screenshot-testing.md) already render every screen, but they
can't be uploaded as they are:

- **They are too small.** Paparazzi scales every image so its longest side is at most 1000 px, and the
  harness shrinks the frame to its content (`RenderingMode.SHRINK`). A Pixel 6 golden is 450 × 1000.
- **Their content is written for tests.** Samples show edge cases (long titles, failures, empty
  lists), and one preview put a developer's own email address in the sign-in screen.
- **Their matrix is fixed.** ADR-016 rule 5 renders every preview as a Pixel 6 in light, dark and
  light at 1.5x, which is right for catching regressions and wrong for a store.

What the screenshots show is constrained too:

- **The Play listing can't show other people's work.** A real album cover or an artist's name in a
  store listing risks rejection under Play's intellectual property and metadata policies. Music
  services that show real artists have licences that Rolabox doesn't.
- **The repo is public and Apache-2.0.** Committing a real cover, even as a test fixture, would
  redistribute it under a licence nobody can grant.
- **The README should look like real use:** a real album and a real podcast episode, which the
  maintainer doesn't necessarily own as files, so a capture on a phone isn't possible.

## Decision

We will render store and README screenshots **with Paparazzi, at the device's full resolution, in a
run of their own**, which is never part of `check` and whose output is never committed.

- **The harness is the design system's,** next to the preview harness, in the test fixtures of
  `:common:designsystem` (`screenshot/StoreScreenshot.kt`), for the reasons ADR-016 put the other
  one there. It renders a Pixel 6 in portrait at **1080 × 2400** (`useDeviceResolution`,
  `RenderingMode.NORMAL`), each case **in light and in dark**, at font scale 1.0. A snapshot handler
  of its own writes each image as a PNG; nothing is compared against a golden.
- **Cases are code, in the module that owns the screen.** A module with store or README shots has a
  `StoreScreenshotTest` in its `src/test`, which extends `StoreScreenshot`. Each case calls the same
  stateless composable the previews call, inside the `RolaboxTheme` and `DeviceScreen` it sits in,
  with sample data `private` to the test. Each case belongs to one of two sets, `play` or `readme`.
- **They run only when asked.** `./gradlew storeScreenshots` runs the `StoreScreenshotTest`s of every
  module with the Paparazzi plugin, and nothing else. Every other test run excludes them, so `check`,
  `recordPaparazziDebug` and `verifyPaparazziDebug` are as ADR-016 describes. The images go to
  `build/store-screenshots/play/` and `build/store-screenshots/readme/`.
- **The Play set is made up.** Invented artists, albums, songs, shows and episodes, like the preview
  samples, and covers generated in code (`colorMixCover`), so nothing in it belongs to anyone else.
- **The README set may use real names and real art, but the art never enters the repo.** Names of
  albums, songs, shows and episodes are written in the test. Art is read at render time from
  `.screenshots-local/`, a folder at the root of the repo that `.gitignore` excludes
  (`localArtwork`). A case whose file is missing is skipped, not failed, so the run works on a fresh
  clone. `docs/releasing.md` lists each file and where it comes from.
- **README images with real art are hosted outside the repo,** as GitHub attachments that the README
  links to. README images without real art are copied to `docs/images/readme/`, which is tracked by
  Git LFS, and committed.

## Alternatives considered

- **Raise ADR-016's matrix to full resolution.** One harness, and the goldens would be the store
  images. But every golden of every preview would be about 2.4 times larger, in LFS, for an image
  that only a handful of screens need. Store content and test content also want different samples:
  a test wants the long title that breaks the layout, and a store wants the one that looks good.
- **Capture on a phone by hand.** What a user sees, exactly, including the system bars. But it isn't
  reproducible (a library, a clock and a battery level that change), it can't show music or episodes
  the maintainer doesn't have, and every release would redo it by hand.
- **Commit the real art as a fixture.** Reproducible on any clone. But it would redistribute
  copyrighted covers under Apache-2.0, which the repo can't do.
- **A separate Gradle test task that copies the unit test task's setup.** It would keep the store
  run out of the test task completely. But Paparazzi's Gradle plugin configures the unit test task
  (layoutlib, resources, system properties), and copying that is fragile across Paparazzi releases.
  A filter on the existing task does the same job.
- **Render the store images from the existing previews, found by name.** No new sample data. But
  the previews' samples are chosen for tests and some name real things, and the README cases need
  data that previews must not have (art read from a file breaks ADR-016 rule 3).

## Consequences

- The store and README images are rebuilt with one command, the same way every time, and change
  when the screens do.
- A store image is only as true as layoutlib: fonts, blur and shaders can differ from a phone, as
  ADR-016 says of the goldens. The images show no status or navigation bar.
- Nothing checks that a store case still looks right. A case can break silently until the next time
  someone looks at the images; the composables themselves are still covered by the goldens.
- The README set needs its art downloaded once per machine, and its images uploaded to GitHub by
  hand when they change.
- Each module with cases carries a little more test code, which changes when a screen's state does.

## Rules

1. `[convention]` Store and README screenshots come only from the design system's `StoreScreenshot`
   harness, at 1080 × 2400, in light and in dark. Modules don't write their own.
2. `[enforced]` A module's store and README cases are a `StoreScreenshotTest` in its `src/test`. They
   run only under `./gradlew storeScreenshots`, and every other test run excludes them
   (`ScreenshotTests.kt` in `build-logic`).
3. `[enforced]` The images are written to `build/store-screenshots/`, which is never committed
   (`build/` is in `.gitignore`). The only images from it that are committed are README images with
   no real art, copied to `docs/images/readme/` and tracked by Git LFS.
4. `[convention]` The `play` set uses only made-up names and generated art.
5. `[convention]` Real art is read only from `.screenshots-local/`, through `localArtwork`, and is
   never committed. `.gitignore` excludes the folder.
6. `[convention]` A README image that contains real art is hosted outside the repo and linked from
   the README.

**Conformance.** Rule 2 is the test filter that `rolabox.android.paparazzi` applies: without the
`storeScreenshots` task, `*StoreScreenshotTest` is excluded, and with it, only those run. The harness
also skips its cases unless the store run set its system property. Rule 3 is the harness, which writes
nowhere else, and `.gitignore`. Rules 1 and 4 to 6 are reviewed; `.gitignore` holds rule 5 unless a
file is added by force.
