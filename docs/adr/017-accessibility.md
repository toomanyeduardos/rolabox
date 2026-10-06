# ADR-017: Accessibility is an architectural rule, starting with large text

- **Status:** Accepted
- **Date:** 2026-10-01
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-10-02:** Rule 9 now covers the device host, `:core:device`
  ([ADR-019](019-device-host-module.md)), besides the design system and the area `:ui` modules.
  ADR-019 added a third kind of module whose composables show text to other modules, and rule 9
  named only the first two, so rule 3 didn't see a fixed height around the host's composables.
- **Revised 2026-10-06:** Module names updated for the layout of
  [ADR-020](020-modules-by-product-area.md), now that the code has moved (37.07b). Rule 9 names the
  `:impl` of a part with screens where it named an area `:ui` module, which no longer exists, and
  the device host is `:device:host`. The decision is unchanged.

## Context

Rolabox had no recorded decision on accessibility. Nothing in the build treated a screen that a
person can't read or operate as a defect.

Large text is where that showed first. Android lets a user scale the system font, and a user who
sets it to 1.5x got clipped labels, a status display whose two texts ran into each other, and an
error badge whose "!" was cut in half. The causes were ordinary layout code:

- containers of text with a fixed height: the metal keys (52 dp), the LCD (46 dp), the recessed and
  read-only fields (50 dp) and the Google button (48 dp);
- a single line with an ellipsis for the app's own text, in the top bar;
- rows that put two texts side by side and assume both fit;
- sizes in `dp` that are meant to match a text, such as the record in the wordmark, which stay the
  same when the text grows.

[ADR-016](016-screenshot-testing.md) already renders every `@Preview` at font scale 1.5. But a
golden only proves that a render didn't change: a clipped layout that was recorded once keeps
passing. That render was also only visible as a golden, not in Android Studio while a screen is
being built, and there was no written rule for what "fits at 1.5x" means, so approving a 1.5x golden
was a guess.

[ADR-015](015-design-system-owns-visual-language.md) keeps text styles in the design system, in `sp`,
so the sizes themselves already follow the font scale. What was missing is the layout around them.

Accessibility is wider than large text: screen readers, contrast, touch targets and motion each
need a decision of their own. Deciding them all at once would delay the one that is broken today.

## Decision

We will treat accessibility as an architectural rule: a screen or shared component that a person
can't read or operate with an accessibility setting on is a defect, and the build catches as much
of it as a build can.

**This ADR grows by section.** It starts with large text. Screen readers (TalkBack and semantics),
color contrast, touch target size and reduced motion are each added later as a dated revision of
this ADR, as [ADR-000](000-record-architecture-decisions.md) allows, with their own rules and
enforcement. Until a section exists, that topic has no rule here.

### Large text

**The bar is font scale 1.5,** the scale ADR-016 snapshots. Every screen and shared component is
fully usable at 1.5x: all text can be read in full, and every control can be reached and operated.
Android goes up to 2.0x. The bar is 1.5x because it is what the screenshot tests cover, and raising
it is a revision of ADR-016 and of this ADR together.

**Text follows the system font scale.** Text sizes are in `sp` and come from the design system's
text styles (ADR-015). No code overrides or caps the font scale, which in Compose means building a
`Density(…)` to provide in place of the system's. A size in `dp` that has to match a text (a badge
around a glyph, the record in the wordmark) is multiplied by the font scale that is in force, which
it reads and doesn't change.

**No text is lost.** At 1.5x no text is clipped by its container, overlaps another element, or is
cut by the edge of the screen. An ellipsis is allowed only for user or remote content of unbounded
length, such as an email address or a track title. The app's own labels, titles, buttons and error
messages are never cut short: they wrap.

**Containers grow with their text.** A container of text has a minimum height
(`heightIn(min = …)`) in place of a fixed one, so it looks the same at 1.0x and grows when its text
does.

**A row that doesn't fit reflows vertically.** When a horizontal arrangement doesn't fit the width
at 1.5x, it is laid out vertically, stacked or wrapped onto more lines, in the same reading order.
Compose's `FlowRow` does this for a row of items. Shrinking the text, clipping it, cutting it with an
ellipsis, or scrolling the screen sideways are not accepted fixes. Where the row fits, the layout at
1.0x doesn't change.

**Every screen scrolls vertically** when its content is taller than the viewport, so that with the
keyboard closed every control can be reached on the Pixel 6 configuration at 1.5x.

**The large-font render is in the IDE.** `@PreviewLightDark` declares three previews: light, dark,
and light at font scale 1.5. Android Studio shows the three for every preview that uses it. The
screenshot harness renders a preview at the font scale it declares, and still adds the 1.5x render
to a light preview that declares none (a bare `@Preview`), so every preview has the three renders
of ADR-016 and none is rendered twice. The goldens keep their names.

**A third party's branding rules come first,** as [ADR-000](000-record-architecture-decisions.md)
rule 5 says for every ADR. Google's rules for the Sign in with Google button fix its proportions and
a one-line label, and allow scaling the whole button. So the Google button scales as one piece with
the font scale, "G", gaps and height together, and its label doesn't wrap.

**Enforcement.** Two detekt checks fail the build:

- `FixedHeightAroundText`, a rule of Rolabox's own, rejects `.height(…)`, `.size(…)`,
  `.requiredHeight(…)` and `.requiredSize(…)` in the modifier of a composable call that shows text:
  a text composable itself, or a call whose content has one inside. Spacers, rules, icons and other
  containers without text pass. It lives in `build-logic/detekt-rules`, the project's first module of
  detekt rules, with unit tests, and every module gets it from the `rolabox.detekt` plugin
  ([ADR-004](004-convention-plugins.md)). It reads syntax only, so it knows a text composable by its
  name, from a list in `config/detekt/detekt.yml`.
- detekt's `ForbiddenMethodCall` rejects a call to `Density(…)`.

Whether an ellipsis is on a label or on an email address, and whether a row fits, are things a
static check can't tell. Those rules are checked in review, against the 1.5x goldens.

### What was not decided

Tablets, landscape and display-size scaling aren't covered, because ADR-016 doesn't snapshot them
yet. Third-party UI, such as Google's account picker and system dialogs, isn't Rolabox's to lay out.
The Google sign-in button is, within Google's branding rules.

## Alternatives considered

- **Leave it to the goldens.** ADR-016 already renders 1.5x, so no new rule is needed. But a golden
  records whatever the layout does, including a clipped label, and nobody had a written definition
  to review it against. The fixed heights this ADR removes all had passing goldens.
- **Cap the font scale** (for example at 1.3x) so the designed layouts always fit. The least work,
  and what many apps do. But it overrides a setting the user chose because they need it, which is
  the opposite of the goal.
- **Shrink or auto-size text to fit its container.** Keeps every layout as designed. But it makes
  the text small again for exactly the users who asked for it to be large.
- **A separate `@PreviewLargeFont` annotation, applied where it matters.** Fewer previews in the
  IDE. But it is opt-in, and the previews that forget it are the new ones. Folding it into
  `@PreviewLightDark` means no preview can forget it.
- **Rename `@PreviewLightDark`** now that it shows three renders. More accurate. But ADR-015 and
  ADR-016 refer to it by name, and the name would have to change in both for no change in behavior.
- **A Gradle task that scans sources with a regular expression,** like `checkTextStyling`. No new
  module. But a modifier chain spans lines and a container's text is in a nested lambda, so a regex
  would miss most cases or flag every spacer.
- **A detekt rule with type resolution,** which would recognize any composable that shows text
  without a list of names. More precise. But it only runs in the per-variant tasks, it is slower,
  and "shows text" still has no type to resolve. A list of names in the configuration is simple to
  read and to extend.
- **Put the detekt rules in a module of the main build.** A normal project dependency. But the rules
  are build tooling, which ADR-004 keeps in `build-logic`, and the main build's modules are for the
  app's code. It remains the fallback if a future detekt can't load rules from an included build.
- **Enforce the bar at 2.0x.** Covers every user. But ADR-016 doesn't render 2.0x, so the rule would
  have no golden to be reviewed against, and Android 14's non-linear scaling changes what 2.0x means.
- **Wrap the Google button's label and let the button grow taller,** like Rolabox's own keys. It
  loses no text at any width. But Google's branding rules show a one-line label with fixed
  proportions, and don't allow uses they don't cover. Scaling the whole button is what they allow.
- **One ADR per accessibility topic.** Smaller records. But they share their context, their bar
  and their enforcement, and a reader would have to collect "what does Rolabox do for accessibility"
  from several files.

## Consequences

- A user with a larger system font gets every text in full, and controls that grow with their
  labels. Screens get taller, and more of them scroll.
- The design has to work at two sizes. A component whose look depends on a fixed height (the
  recessed field's well, a pill-shaped key) now has that height as a minimum, and is taller with
  larger text or a label that wraps.
- The top bar can't wrap its title inside Material's fixed bar height, so `RolaboxTopBar` scales the
  bar's height with the font scale. It is taller at 1.5x even when the title fits on one line.
- Every `@PreviewLightDark` preview shows three renders in Android Studio in place of two, which
  takes more room and more time to render.
- `FixedHeightAroundText` reads syntax. It doesn't follow a modifier that is built elsewhere and
  passed in, or a content lambda that is passed as a parameter, and it only knows the text
  composables in its list. A new component that shows text has to be added to the list by hand.
- `ForbiddenMethodCall` doesn't run on `:common:designsystem`, which ADR-015 excludes from it so it can
  define colors and shapes. There, not building a `Density` is checked in review.
- The Google button's label stays on one line. It fits at 1.5x on the Pixel 6 configuration, but on
  a narrower screen or at a larger scale it could be cut, and Google's rules leave no fix for that
  other than a shorter approved label.
- A justified fixed size needs a `@Suppress` with a comment, which is noise, and is the point: each
  exception is visible.
- The rules that matter most to a user, no lost text and reflow, are still checked by a person
  reading a golden. A check that needs no eye, such as a test that fails when a text node reports
  overflow at 1.5x, is a follow-up.
- `build-logic` is now included in the build twice, once for its plugins and once so its detekt
  rules can be resolved as a dependency, and it has a second project to build.
- Users at 2.0x, on tablets or in landscape aren't covered by any rule or golden yet.

## Rules

1. `[convention]` Accessibility rules live in this ADR, one section per topic. A new topic is a
   dated revision that brings its rules and their enforcement. The large-text bar is the font scale
   ADR-016 snapshots (1.5), and changing it is a revision of both ADRs.
2. `[enforced]` Text follows the system font scale. No code builds a `Density(…)` to override or cap
   it. Enforced everywhere except in `:common:designsystem`, where it is checked in review.
3. `[enforced]` A composable that shows text has no fixed height: no `.height(…)`, `.size(…)`,
   `.requiredHeight(…)` or `.requiredSize(…)` on it or on a container around it. It uses a minimum
   (`heightIn(min = …)`), or a size multiplied by the font scale.
4. `[convention]` At font scale 1.5 no text is clipped, overlaps another element or is cut by the
   screen edge. An ellipsis is only for user or remote content of unbounded length, never for the
   app's own labels, titles, buttons or error messages.
5. `[convention]` A horizontal arrangement that doesn't fit at 1.5x is laid out vertically, stacked
   or wrapped, in the same reading order. Text isn't shrunk and the screen doesn't scroll
   horizontally. Where the row fits, the layout at 1.0x is unchanged.
6. `[convention]` Every screen scrolls vertically when its content is taller than the viewport.
7. `[convention]` Previews use `@PreviewLightDark`, which shows light, dark and light at font scale
   1.5 in the IDE. The screenshot harness renders the font scale a preview declares, and adds the
   1.5x render to a light preview that declares none.
8. `[convention]` A pull request that adds or changes a 1.5x golden is reviewed against rules 4 to
   6, not only for whether the image changed.
9. `[convention]` A new composable that shows text, in the design system, in the `:impl` of a part
   with screens or in `:device:host`, is added to `textComposables` in `config/detekt/detekt.yml`, so rule 3
   sees the containers around it.
10. `[convention]` An exception to rule 2 or 3 carries a `@Suppress` with a comment that names the
    rule and says why.

11. `[convention]` The Sign in with Google button follows Google's branding rules first (ADR-000
    rule 5): it scales as one piece with the font scale, and its label stays on one line. A comment
    at each exception to this ADR names this rule.

**Conformance.** Rule 2 is checked by detekt's `ForbiddenMethodCall` in `detektMain` and
`detektTest`, and rule 3 by `FixedHeightAroundText` in `detekt`, all part of `./gradlew check` and
of the CI `build` job. The rule's own unit tests run in `./gradlew check` and `unitTest`. Rules 4 to
6 are reviewed against the 1.5x goldens that ADR-016's `screenshots` job verifies.
