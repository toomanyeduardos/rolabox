# ADR-009: SDK steps that need an Activity live in the UI, and only their results cross the `:api`

- **Status:** Accepted
- **Date:** 2026-09-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-28:** SDKs that bring Play services no longer have to stay out of an offline
  app, because [ADR-008](008-one-app-with-offline-mode.md) now builds one app with Firebase and Play
  services always in it. Instead, a UI step that reaches the network, such as Google sign-in, runs
  only when the user starts it, and turns offline mode off first (Decision, rule 5). The
  `cloudImplementation` requirement is gone.
- **Revised 2026-09-29:** The UI step now lives in the area's own `:core:<area>:ui` module
  ([ADR-014](014-area-ui-modules.md)), not in a feature, so that Sign in and Create account (and later
  other features) share it. That was the "shared UI module" alternative, and it is now adopted.
  The step's results are provider-neutral (Decision, and rules 1 and 3). Rule 4 is now a plain
  configuration type owned by the `:ui`, and rule 5 is followed, so it is no longer `[planned]`.
  The decision to keep the SDK step out of the `:api` and the `:impl` is unchanged.

## Context

Some SDKs can only do their work from an `Activity`, because they show system UI: Credential
Manager shows the account bottom sheet for Google sign-in, and later candidates include Play
Billing's purchase flow, in-app reviews, in-app updates, and Activity Result APIs such as the photo
picker or permission requests.

That clashes with the module layout in [ADR-003](003-module-boundaries.md):

- An area's `:api` is pure JVM, so its functions can't take a `Context` or an `Activity`.
- An area's `:impl` is bound as a singleton in the Hilt graph ([ADR-005](005-hilt-dependency-injection.md)).
  It can't hold an `Activity` without leaking it, and it has no way to know which `Activity` is
  showing.

The first case is signing in with Google through Credential Manager: the bottom sheet returns a
Google ID token, and Firebase Auth then exchanges the token for a session. Only the first step
needs an `Activity`.

## Decision

We will **split each such flow into a UI step and a data step**, one per layer
([ADR-001](001-layered-architecture.md)):

1. **The UI step runs in the UI layer.** The area's `:ui` module ([ADR-014](014-area-ui-modules.md)),
   a feature module, or `:app` for app-wide flows, calls the SDK from a composable or `Activity`,
   where an `Activity` is available. It turns the SDK's result into a plain value that the area's
   `:api` defines, such as a token.
2. **The data step goes through the `:api`.** The ViewModel passes that value to the area's
   repository, and the `:impl` does the rest (for sign-in, the Firebase exchange). SDK types never
   appear in `:api` signatures.

For Google sign-in:

```
:core:auth:ui                         :core:auth:api            :core:auth:impl
Credential Manager bottom sheet ──▶  SignInCredential   ──▶   FirebaseAuth.signInWithCredential
  (GoogleIdTokenCredential)            .GoogleIdToken(token)
```

- **Errors of the UI step aren't the area's errors.** "The user closed the sheet" or "no account to
  pick" are outcomes of the UI step. The `:ui` reports them as results of the step, the feature
  handles them as UI state, and they aren't cases of the area's error type. The area's error type covers the data step only (for sign-in, `AuthError`,
  [ADR-007](007-error-handling.md)).
- **Configuration only `:app` knows is provided by `:app` through Hilt.** For example, Credential
  Manager needs the Web client ID, which is generated from `google-services.json` as a resource in
  `:app`. `:app` provides it in a plain configuration type owned by the `:ui`, which the feature's
  ViewModel injects and passes on.
- **UI steps that reach the network run only when the user starts them, and never in offline
  mode.** Google sign-in through Credential Manager is the first network call of a sign-in, so
  offline mode is turned off before it, by a use case that the feature calls
  ([ADR-008](008-one-app-with-offline-mode.md), rule 7).
  SDKs that bring Play services are ordinary dependencies, since there's only one app.
- **Non-UI calls of the same SDK stay in the `:impl`.** Clearing Credential Manager's saved state on
  sign-out needs only the application context, so `:core:auth:impl` does it.

## Alternatives considered

- **An `Activity` provider in the `:impl`.** The `:impl` tracks the current `Activity` through
  lifecycle callbacks and runs the whole flow itself, so the feature only calls `signIn()`. Less code
  in the feature, but it's hidden global state that's easy to leak, it breaks when no `Activity` is
  resumed, and it hides a UI interaction inside the data layer.
- **Passing the `Context` through the `:api` as `Any`.** Keeps one call, but defeats the type system,
  and the `:api` stops being honest about being pure JVM.
- **A shared UI module per area (`:core:auth:ui`) that owns the UI step.** Features depend on it
  instead of the SDK, so a second screen that signs in doesn't repeat the code. It was set aside at
  first, because it was a module type that ADR-003 didn't have. It's adopted in
  [ADR-014](014-area-ui-modules.md), which added the module type.
- **Doing the whole flow in the feature, Firebase included.** The shortest path, but the feature would
  depend on Firebase, which is what [ADR-001](001-layered-architecture.md) exists to prevent.

## Consequences

- The area's `:ui` module depends on the UI SDK (for sign-in, `androidx.credentials` and `googleid`),
  so its build file shows that it shows that UI, and the features that use it don't. ADR-003's rules
  cover project dependencies only, so this doesn't conflict with them.
- The repository stays testable with plain values: tests pass a fake token, with no `Activity`.
- Each flow has two steps, and two places to handle its errors. That's more code than one `signIn()`
  call, and it's the price of keeping the layers honest.
- A second feature that needs the same UI step uses the area's `:ui` module and doesn't repeat it.

## Rules

1. `[convention]` SDK calls that need an `Activity` or show system UI are made from the UI layer (an
   area's `:ui` module, a feature module or `:app`), never from an `:impl` or behind an `:api`.
2. `[convention]` Only a plain value produced by the UI step crosses into the `:api`, as a type the
   `:api` defines. SDK types never appear in `:api` signatures.
3. `[convention]` Errors from the UI step (such as the user cancelling) are results of the step,
   shown by the feature, and aren't cases of the area's error type.
4. `[convention]` Configuration the UI step needs from `:app` (such as the Web client ID) is provided
   by `:app` through Hilt, in a plain type owned by the `:ui`.
5. `[convention]` A UI step that reaches the network runs only when the user starts it, and turns
   offline mode off first (ADR-008, rule 7), through `LeaveOfflineModeUseCase`.
