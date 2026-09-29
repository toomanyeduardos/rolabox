# ADR-014: Area UI modules for SDK steps and shared UI, `:core:<area>:ui`

- **Status:** Accepted
- **Date:** 2026-09-29
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

[ADR-009](009-ui-bound-sdks.md) says an SDK step that needs an `Activity`, such as Google sign-in
through Credential Manager, runs in the UI layer, and only its result crosses the `:api`. It puts
that step in a feature module, and set aside a shared UI module for the day a second feature needs
the same step.

Building the first one showed two more forces:

- **The step isn't only UI code of one screen.** Sign in and Create account use the same step, the
  same branding button and the same handling of its outcomes. Re-authenticating before deleting an
  account, or linking a provider from Settings, would be a third and fourth user, in other features.
- **Providers change.** The `:api` already treats each provider as a case of `SignInCredential`,
  but the code around the step (results, errors, the flow, the button) was written with one
  provider's name. Adding or replacing a provider should touch the provider's own code, and
  nothing else.

Neither fits an `:api` (pure JVM, no Android), an `:impl` (no Activity, no UI), or a feature (it
would be copied, and features can't depend on each other, [ADR-003](003-module-boundaries.md)).

## Decision

We will give an area that has UI-bound code an optional fourth module, **`:core:<area>:ui`**. It is
an Android library with Compose, and it holds the parts of the area's UI that more than one
feature can use:

- the area's UI-bound SDK steps ([ADR-009](009-ui-bound-sdks.md));
- the area's shared composables: branded buttons, and dialogs and bottom sheets that don't belong to
  one screen;
- plain state holders (`remember…` functions) and plain classes that connect a step to the area's
  `:api` (and to `:core:domain`, when a rule spans areas).

**What makes something a feature, and not a `:ui`.** A **ViewModel** or a **navigation destination**
(a `NavKey` and its entry, [ADR-012](012-navigation.md)) makes it a feature. `:ui` modules have
neither, and no Hilt modules. So screens keep one owner: `:feature:*`. Dependencies are not
injected into a `:ui` either: a feature's ViewModel injects them and passes them in.

**Dependencies.** A `:ui` may depend on `:api` modules, `:core:domain`, `:core:common` and
`:core:designsystem`, plus testing modules from test configurations. It never depends on an
`:impl`, on a feature, or on another `:ui`. Features and `:app` may depend on `:ui` modules, and
nothing else may. Only areas that have such code get one: there is no empty `:ui`. It has no
`:testing` module either, since nothing in it is an interface that other modules would fake: its
tests use the fakes of the `:api` modules it uses.

**Steps are provider-neutral.** The UI step of every sign-in provider has the same shape, so the
code that uses it doesn't name a provider:

```
:feature:account                        :core:auth:ui                              :core:auth:api
ViewModel ── SignInFlow, SignInError ──▶ SignInStep ──▶ SignInStepResult.Credential ──▶ SignInCredential
Screen ───── SignInButton(provider) ──▶ per provider, internal:
                                          google/GoogleSignInStep, google/GoogleButton
```

- The types the feature sees are neutral: `SignInProvider`, `SignInStepResult`, `SignInFlow`,
  `SignInError`, `SignInButton`. `SignInStepResult.Credential` carries the `:api`'s
  `SignInCredential`, so the flow calls `AuthRepository.signIn(credential)` for every provider.
- What is specific to one provider (its SDK, its branding, its configuration) lives in its own
  subpackage and is `internal`, so no other module can depend on it.
- **Adding a provider** touches these places, and the compiler points at each of them:
  1. a case of `SignInCredential` in `:core:auth:api`;
  2. a branch of `toFirebaseCredential()` in `:core:auth:impl`;
  3. a value of `SignInProvider`, and its step and button, in `:core:auth:ui`;
  4. its configuration, in `SignInConfig`, provided by `:app`.
  The feature's ViewModels, screens and the flow don't change (only a screen that shows the new
  button does).
- **Configuration** that only `:app` knows (Google's Web client ID) is a plain `SignInConfig` type
  owned by `:core:auth:ui`, provided by `:app` through Hilt ([ADR-009](009-ui-bound-sdks.md), rule 4).

## Alternatives considered

- **Keep the step in the feature.** It's what ADR-009 chose first, and it needs no new module type.
  It works while one feature uses it. It stops working with the second: the step would be copied,
  or a feature would depend on another feature, which ADR-003 forbids.
- **Put it in `:app`.** `:app` is allowed to see everything and to hold UI steps
  ([ADR-009](009-ui-bound-sdks.md)), but features couldn't reach it without callbacks for every
  use, and `:app` would collect the UI code of every area.
- **One shared `:core:ui` for every area.** One module instead of one per area, but it would depend
  on every area's `:api`, so a feature would see all of them, which is the problem
  [ADR-003](003-module-boundaries.md) removed for `:core:domain` and `:core:model`.
- **Full screens and ViewModels in `:ui`.** Features would shrink to navigation wiring. It gives
  screens two owners and makes `:feature:account` empty, which contradicts ADR-003's module table.
- **Put the provider-neutral types in the `:api`.** They'd be pure JVM, but the result of a step
  is what the UI produced, and ADR-009 keeps errors of the UI step out of the area's `:api`.
- **Name the types after the one provider we have.** Less code today. Adding a second provider then
  means renaming or duplicating the result, the flow, the errors and the ViewModel state.

## Consequences

- The feature's build file no longer lists any sign-in SDK. `:feature:account` has ViewModels,
  screens and strings, and no provider code.
- A second user of the same step (a re-authentication sheet, a Settings screen) uses it without
  copying and without depending on another feature.
- There is one more module type and one more module for the auth area, and its build rules are more
  code to maintain.
- The `:ui` may hold a composable that is nearly a screen (a bottom sheet). The rule that a
  ViewModel or a destination makes it a feature is what keeps that line, and it is checked by review.
- If no second user ever appears, this module was earlier than it needed to be. That is the cost of
  deciding ahead of the second user, and it is accepted.

## Rules

1. `[enforced]` `:core:<area>:ui` modules depend only on `:api` modules, `:core:domain`,
   `:core:common` and `:core:designsystem` (plus testing modules from test configurations).
2. `[enforced]` Only `:feature:*` modules and `:app` depend on `:ui` modules.
3. `[convention]` A `:ui` module has no ViewModel, no navigation destination and no Hilt module.
   Code that has a ViewModel or a destination is a feature.
4. `[convention]` A `:ui` module exposes neutral types, and keeps what is specific to one provider
   in its own subpackage as `internal`. Adding a provider follows the checklist in Decision.
5. `[convention]` Configuration that `:app` provides for a `:ui` is a plain type owned by that
   `:ui`. A `:ui` doesn't inject anything: the feature's ViewModel does, and passes it in.
