# ADR-015: The design system is the only home of Rolabox's visual language

- **Status:** Accepted
- **Date:** 2026-09-30
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

[ADR-003](003-module-boundaries.md) created `:core:designsystem` as a utility module for "theme and
shared composables", and limits what it may depend on (rule 9). It doesn't say what must live there,
or what other modules may not define for themselves. Without that, the same color, text size or
button slowly gets defined again in each feature, and the app stops looking like one app.

The first screens showed what that looks like:

- Text styles were set per call site (a size here, a weight there) until they were moved into
  `RolaboxTypography` and `RolaboxTextStyles`, and the font into `:core:designsystem`.
- Components written for Sign in (the recessed field, the "or" divider, the links) were first built
  in `:feature:account`, then moved to the design system when their second user showed up.
- `:app` still had the colors of the project template, which nothing used.
- The Google sign-in button needs colors, a shape and a label that Google's branding rules set, and
  that must not follow Rolabox's theme.

Android also merges the resources of every module into one namespace. A resource that another
module defines with the same name silently replaces the design system's, with no error.

Strings are a special case. Some are the same everywhere ("OK", "Cancel", "Back"). Others only look
the same in English: "Sign in" is a title, a button and a link in `:feature:account`, and
translators often need a different word for each.

## Decision

We will keep **Rolabox's visual language only in `:core:designsystem`**, and other modules will use
it without defining their own.

**What the design system owns:**

- **Tokens:** colors (the palette, the color scheme, the metal colors), fonts and text styles,
  shapes, and spacing values that recur.
- **Brand assets:** the logo, the wordmark, the record (`ds_ic_vinyl`), textures, and icons that
  aren't specific to one area.
- **Shared composables:** any component that knows nothing about an area (fields, dividers, links,
  top bars, loading states). It goes in the design system **when it is first written**, not when a
  second feature needs it, as [ADR-014](014-area-ui-modules.md) decided for area UI.
- **Preview tooling,** such as `@PreviewLightDark` and the type specimen.
- **Strings that mean the same thing in every context,** such as the labels of dialog buttons ("OK",
  "Cancel", "Retry") and accessibility labels of shared components ("Back").

**How other modules use it.** Features, `:ui` modules and `:app` read tokens through the theme:
`MaterialTheme.colorScheme`, `MaterialTheme.shapes`, `RolaboxType.styles` and `RolaboxMetal.colors`.
The raw palette, the fonts and the token sets behind those accessors are `internal` to the design
system, so a skin or an accent can replace them without touching a call site. Other modules don't
create colors, fonts, text styles or shapes, and don't declare color, font or style resources.

**Spacing is the exception.** Colors, fonts, text styles and shapes must come from the design
system. Spacing values (padding, gaps, sizes) should, but a one-off `dp` value in a layout is
allowed. When the same value shows up in more than one module, it becomes a token. Spacing tokens
are added one at a time, when a value recurs, and not as a scale made up in advance.

**Third-party branding stays with the area.** A third party's visual identity (Google's button
colors, its pill shape, its "G" artwork) isn't Rolabox's, and must not follow Rolabox's theme. It
lives in the area's `:ui` module, in the provider's `internal` subpackage
([ADR-014](014-area-ui-modules.md)). Each place that defines one of these values carries a
`@Suppress` that names this ADR, so every exception is visible in review.

**Shared strings.** A string is shared only when **both its meaning and its role** are the same in
every place it appears: the label of a dialog's confirm button, "Back" on a navigation icon. A string
that only has the same English text as another one ("Sign in" as a title and "Sign in" as a button)
isn't shared: each feature defines its own, so it can be translated on its own. A shared string
lives in the design system, with the `ds_` prefix.

**Resource names.** Every resource of the design system starts with `ds_` (`ds_metal_body`,
`ds_ic_vinyl`, `ds_hanken_grotesk_bold`). Since Android merges resources into one namespace, the
prefix makes it very unlikely that another module replaces one by accident, and shows where a
resource comes from where it is used.

**What `:app` keeps.** A few resources have to be in `:app`, because the manifest or the system
reads them before any Compose theme exists:

- the launcher icon (`mipmap/`, `ic_launcher_*`);
- the system splash: its theme, the animated icon (`avd_vinyl_spin`, `ic_splash_vinyl`) and the
  branding image, which are drawn at the sizes the system requires;
- the window themes (`Theme.Rolabox`, `Theme.Rolabox.Splash`);
- `app_name`.

These use the design system's values wherever one exists: the splash background is `ds_metal_body`,
in the window theme and in `RolaboxSplash`. They don't define colors or fonts of their own.

**Enforcement.** Two checks, added to the convention plugins
([ADR-004](004-convention-plugins.md)), fail the build:

- `checkDesignSystemResources` runs before every Android module's build (`preBuild`) and in
  `check`. Outside the design system, it rejects `res/font*` folders, `<color>` resources, and
  `<style>` resources (except in `:app`). In the design system, it rejects any resource without the
  `ds_` prefix.
- detekt's `ForbiddenMethodCall` and `ForbiddenImport` rules reject, outside `:core:designsystem`,
  calls to `Color(…)`, `FontFamily(…)`, `Font(…)`, `TextStyle(…)`, `RoundedCornerShape(…)` and
  `CutCornerShape(…)`, and imports of `sp`, `em` and `FontWeight` (`config/detekt/detekt.yml`).

## Alternatives considered

- **Leave it to review.** No build code. But `:core:designsystem` already existed, and styles were
  still defined in features until they were moved by hand. A rule that nobody checks decays one
  convenient `Color(0x…)` at a time, which is why ADR-003 enforces its rules.
- **A separate `:core:strings` (or `:core:resources`) module for shared strings.** Keeps copy apart
  from visuals. But the shared strings are almost all labels of shared components, so they would be
  in one module and their component in another. It's also one more shared module for things to
  drift into, the problem ADR-003 had with `:core:model`.
- **Share every string with the same English text.** Fewer strings to write. But translations
  depend on the role of a string, not only on its text, and a shared string can't be changed for one
  screen without changing it for all of them.
- **Move a composable to the design system only when a second feature needs it.** Nothing is moved
  early. But the first user is then a feature, which builds the component with its own values, and
  moving it later means cleaning those up. ADR-014 already chose to decide ahead of the second user.
- **Require spacing tokens too.** The most consistent. But there is no designed spacing scale, the
  values in the screens today don't follow one, and making one up would put guesses into tokens.
- **Move the Google button's colors into the design system.** One place for every color. But they
  aren't Rolabox's: they must not change with an accent or a skin, and they would be public tokens
  that any feature could use for something else.
- **Use Android lint's `resourcePrefix` for the `ds_` prefix.** Built in. But it only warns, and the
  resource check already reads the same files.
- **Enforce the resource rules while configuring the build, like `ModuleRules.kt`.** Fails earlier.
  But reading resource files while configuring makes every resource change invalidate the
  configuration cache.

## Consequences

- A screen that needs a new color, text style or shape has to add it to the design system first.
  That's slower for a one-off, and it's the point: the token then exists for the next screen.
- Changing the app's look (a skin, an accent, a new font) touches only `:core:designsystem`.
- The design system grows with every shared component, and every change to it rebuilds every
  feature. Components that know about an area stay in that area's `:ui` to limit this.
- Features may still have one-off spacing values, so spacing can be inconsistent until tokens exist
  for the values that recur.
- Strings that look the same are defined more than once. That is intended, and translation needs it.
- Brand exceptions need a `@Suppress` with a reason, one per value, which is noisier than a
  file-level exception, but each one is visible.
- The resource check adds a small task to every Android module's build.
- The `ds_` prefix makes resource names longer, and renaming a resource means updating its references.

## Rules

1. `[convention]` Rolabox's tokens, brand assets, preview tooling and every composable that knows
   nothing about an area live in `:core:designsystem`. A composable goes there when it is first
   written.
2. `[enforced]` Colors and fonts are defined only in `:core:designsystem`: no `res/font` folder, no
   `<color>` resource, and no `Color(…)`, `FontFamily(…)` or `Font(…)` call anywhere else.
3. `[enforced]` Text styles and shapes are defined only in `:core:designsystem`: no `TextStyle(…)`,
   `RoundedCornerShape(…)` or `CutCornerShape(…)` call, and no import of `sp`, `em` or `FontWeight`,
   anywhere else. Only `:core:designsystem` and `:app` (for its window themes) declare `<style>`
   resources.
4. `[convention]` Spacing should come from the design system's tokens. A one-off `dp` value is
   allowed, and a value used in more than one module becomes a token.
5. `[enforced]` Other modules read tokens through the theme (`MaterialTheme`, `RolaboxType.styles`,
   `RolaboxMetal.colors`). The raw palette, fonts and token sets are `internal` to the design system.
6. `[convention]` A third party's branding lives in the area `:ui` that uses it, in the provider's
   `internal` subpackage. Each value that breaks rule 2 or 3 for it carries a `@Suppress` with a
   comment that names this rule.
7. `[enforced]` Every resource in `:core:designsystem` starts with `ds_`.
8. `[convention]` A string is shared, in the design system, only when its meaning and its role are
   the same everywhere it appears. A string that only has the same text as another stays in its
   feature.
9. `[convention]` `:app` holds only the resources the system reads before the app's theme exists:
   the launcher icon, the system splash and its assets, the window themes and `app_name`. They use
   the design system's values wherever one exists.

**Conformance.** Rules 2, 3 and 7 are checked by the `checkDesignSystemResources` task and by
detekt, both part of `./gradlew check`, and the resource check also runs before every build. Rule 5
is checked by the compiler, through `internal` visibility.
