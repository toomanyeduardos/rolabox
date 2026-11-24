# ADR-041: The Play icon and feature graphic are rendered by the store run, the icon from the launcher icon in `:app`

- **Status:** Accepted
- **Date:** 2026-11-23
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** ADR-039 rule 1, in part: the store run also renders images at sizes other than 1080 × 2400

## Context

Besides its screenshots, the Play listing needs two graphics (launch/12):

- **A 512 × 512 icon,** a 32-bit PNG. Play masks it to its own rounded square, so it is the full
  square, with no shadow and no mask of its own. It should be the launcher icon, so the listing and
  the home screen match.
- **A 1024 × 500 feature graphic,** shown above the listing in some places, on Play's light or dark
  background.

[ADR-039](039-store-and-readme-screenshots.md) renders the screenshots with Paparazzi, from the
app's own composables, in a run of their own. Its rule 1 fixes that run at 1080 × 2400, which neither
graphic is.

The launcher icon is two vector layers in `:app` (`ic_launcher_background`, `ic_launcher_foreground`),
where [ADR-015](015-design-system-owns-visual-language.md) rule 9 keeps it, because the system reads it
before the app's theme exists. Paparazzi 2.0 renders in application modules as well as libraries.

## Decision

We will render **both graphics in the store run, `./gradlew storeScreenshots`**, next to the
screenshots, with the same harness.

- **The harness takes other sizes for Play's graphics.** `StoreScreenshot.shootGraphic(name, width,
  height)` renders one image at exactly `width` × `height` pixels, in one theme, to
  `build/store-screenshots/play/<name>.png`. Screenshots keep using `shoot`, at 1080 × 2400, in light
  and in dark.
- **The icon is rendered from the launcher icon's own layers,** in a `StoreScreenshotTest` in `:app`,
  which applies `rolabox.android.paparazzi` for it. The layers fill the 512 × 512 square edge to edge,
  as Android Studio's Play Store icon export does, so the disc keeps its place in the safe zone. The
  icon is never drawn a second time anywhere else.
- **The feature graphic is built from the design system's parts** (brushed metal, the wordmark) and
  the device as it is on the phone, in the `StoreScreenshotTest` of `:device:ui:impl`, which already
  renders the main menu. It carries its own background, so it reads on Play's light and dark themes,
  and no text beyond the wordmark.

## Alternatives considered

- **Export the icon from Android Studio's Image Asset wizard, and draw the feature graphic by hand.**
  The usual way, and no code. But both would be files made by hand, out of date the first time the
  icon or the device's look changes, and the feature graphic would be a second drawing of the
  design system that nothing checks against it.
- **Draw the icon again in the design system,** from `ds_ic_vinyl` and the aluminum colors. No need
  for Paparazzi in `:app`. But it would be a copy of the launcher icon, which could drift from it
  without anyone noticing.
- **Move the launcher icon's layers into the design system.** One source, and the store case could
  live in a library. But it reopens ADR-015 rule 9 for a store image, and the adaptive icon would
  still be in `:app`.

## Consequences

- `./gradlew storeScreenshots` makes everything the listing needs, the same way each time, and the
  icon changes when the launcher icon does.
- `:app` applies Paparazzi, for this one test. It has no previews, so ADR-016's goldens don't change.
- As with the screenshots, nothing checks that the graphics still look right; someone looks at them
  before uploading.

## Rules

1. `[convention]` The Play icon and feature graphic come from the `StoreScreenshot` harness, through
   `shootGraphic`, at Play's sizes (512 × 512 and 1024 × 500), into `build/store-screenshots/play/`.
   They are never committed, like the screenshots (ADR-039 rule 3).
2. `[convention]` The icon is rendered from `:app`'s launcher icon layers, and drawn nowhere else.
3. `[convention]` The feature graphic uses only the design system's parts and the device's own
   composables, with made-up content, as the Play screenshots do (ADR-039 rule 4).

**Conformance.** The store run's test filter (ADR-039 rule 2) also covers `:app`'s
`StoreScreenshotTest`. The rules here are reviewed.
