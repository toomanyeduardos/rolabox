# ADR-013: One account per email, with Google and password sign-in linked automatically

- **Status:** Accepted
- **Date:** 2026-09-29
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

A user can reach Rolabox through two providers: an email and password, and Google
([ADR-009](009-ui-bound-sdks.md)). Both can carry the same email address. Someone who created an
account with a password and later taps "Continue with Google" with that address must either land in
the account they already have or get a second, empty one.

Firebase Authentication decides this with a project setting, "User account linking", under
Authentication → Settings:

- **Link accounts that use the same email** (the default, "one account per email"): one account per
  email address. Signing in with a second provider adds it to the existing account.
- **Create multiple accounts for each identity provider**: every provider creates its own account.

The Google sign-in of Rolabox is the only social provider, and the synced data
([ADR-010](010-firestore-security-rules.md), [ADR-011](011-preferences-sync.md)) is stored under the
Firebase user ID. So the choice decides whether one person can end up with two data sets.

## Decision

We will keep Firebase's **one account per email** setting, and let Google sign-in link to an
existing password account **without asking for the password**.

- Google returns an email that it has verified. When it matches an existing account, Firebase signs
  the user into that account and adds Google as a provider. The user ID, and so the synced data,
  stay the same. The app doesn't detect or handle the collision, and `AuthError` has no case for it.
- Create account and Sign in both offer Google, and both end the same way for an existing email:
  signed in, with the same data. Creating an account with a password for an email that Google
  created stays `AuthError.EmailAlreadyInUse`, and the screen points the user to Google.
- Firebase only trusts a provider's email if it is verified. If the password account's email was
  never verified, Firebase removes the password login when Google links to it, since anyone could
  have registered that address without owning it. The user keeps the account through Google. Rolabox
  doesn't verify emails at sign-up yet, so this can happen. We accept it: the alternative is a
  password that an unverified stranger might hold.

## Alternatives considered

- **Ask for the password, then link.** Catch the collision, ask for the password, and link the
  Google credential with the signed-in user. Nothing is removed from the account, and the user
  decides. It needs a new error case, a dialog, and `linkWithCredential` in the repository, all for
  a case that Google's verified email already makes safe. We'll reconsider if a provider whose
  emails aren't verified is added.
- **Multiple accounts per email.** The simplest to build, and there is nothing to link. But the same
  person gets two accounts with two separate libraries and preferences, depending on how they signed
  in that day. That is the worst outcome for an offline-first app that syncs per user.

## Consequences

- One person has one account and one data set, whichever sign-in they use.
- There is no linking UI to build, test or explain.
- An unverified password login can be replaced by Google's without a warning. It should be revisited
  when email verification or password reset is built.
- The behavior depends on a console setting, not on code. A project with the setting changed breaks
  the decision silently, so rule 2 asks for it to be checked.

## Rules

1. `[convention]` Firebase's "User account linking" is set to "Link accounts that use the same
   email", in every Firebase project the app uses.
2. `[convention]` Account collisions between providers aren't handled in code: `AuthError` has no
   case for them, and Google sign-in calls `AuthRepository.signIn` as it is.
3. `[convention]` A provider whose emails aren't verified doesn't sign in without a new ADR, since
   this decision relies on Google's email being verified.
