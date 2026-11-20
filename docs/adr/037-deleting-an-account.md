# ADR-037: Deleting an account: the signed-in user re-authenticates, each `Syncer` deletes its data in the cloud, then the account, then Android erases the app's data

- **Status:** Proposed
- **Date:** 2026-11-20
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-022](022-data-source-sub-parts.md) (rule 5, for deleting an account: a
  `Syncer`'s delete runs in the foreground, not as WorkManager work) and
  [ADR-031](031-podcasts.md) ("Follows are never deleted", in Syncing follows, for deleting an
  account: sync still never deletes a follow)

## Context

Rolabox is going onto Google Play (launch/00). Play's account deletion policy says an app that lets
users create an account must also let them delete it, from inside the app and from a web link
(launch/08 covers the link). Rolabox had no way to do either, which blocked the launch.

What an account holds:

- **The Firebase Auth user** ([ADR-008](008-one-app-with-an-optional-account.md),
  [ADR-013](013-account-linking.md)): a password, Google, or both linked.
- **Two kinds of synced data under `users/{uid}`** ([ADR-010](010-firestore-security-rules.md),
  rule 4): the preferences, one document at `settings/preferences`
  ([ADR-011](011-preferences-sync.md)), and the podcast follows, one document per show at
  `podcastFollows/{showKey}` ([ADR-031](031-podcasts.md)). The app never writes `users/{uid}`
  itself, so only the subcollections exist.
- **Nothing in Crashlytics:** it holds nothing that identifies the user
  ([ADR-030](030-crash-reporting.md)).

What makes it harder than calling `delete()`:

- **Deleting a document doesn't delete its subcollections,** and the client SDKs have no recursive
  delete. Each kind of synced data has to be deleted by whoever knows its paths. Each path is known
  only to the `:network:impl` of the part that owns the data ([ADR-022](022-data-source-sub-parts.md)),
  and auth can't depend on `:device:podcasts` ([ADR-020](020-modules-by-product-area.md), rules 2
  and 3).
- **The rules refuse the data once the Auth user is gone,** so the data has to go first.
- **Firebase refuses to delete an account that hasn't signed in recently**
  (`requires-recent-login`). If that comes after the data is gone, the account stays and its data
  doesn't.
- **An offline Firestore write doesn't fail.** It's queued and sent later, so "offline deletes
  nothing" doesn't happen by itself.
- **Sync writes back.** A sync running while the data is deleted, or one triggered after, writes the
  phone's data to the account again.
- **Signing up again merges the phone's data.** Preferences and follows made without an account
  merge into the account on the first sync after signing in (ADR-011, ADR-031). Kept on the phone,
  the deleted account's choices would be pushed into the new account straight away.
- **The phone holds much more than what syncs.** The podcasts' database (shows, episodes,
  positions, played state, follows), downloaded episodes in Media3's cache, artwork, the library
  index, the preferences' file, and what the SDKs keep: Firebase Auth's user, Firestore's cache,
  WorkManager's queue and Crashlytics' reports. Each part stores its own, and more will come.

## Decision

We will delete an account from the app, on the Spark plan, with no server code. The signed-in user
re-authenticates first, then each `Syncer` deletes its own data in the cloud, then the Auth user is
deleted, then Android erases everything the app keeps on the phone, which closes the app. The next
launch is a fresh install. It all happens on one screen, while the user stays signed in until the
Auth user is deleted.

**What re-authenticating means.** The user doesn't sign out and back in. While signed in, they
enter their credential again on the Delete account screen: their password, or Google's account
picker. Firebase Auth checks it against the account that is already signed in
(`FirebaseUser.reauthenticate`), which proves to Firebase that the account's owner is the one asking
for the deletion, and refreshes when they last signed in. Firebase requires that: it refuses to
delete an account whose last sign-in isn't recent (`requires-recent-login`), so a phone left
signed in for weeks can't delete an account with one tap. Re-authenticating also needs the network,
so it proves the phone is online before anything is deleted. A credential of another account is
refused, so it can't be used to delete this one.

**Where.** Settings → Account opens the Sign out screen while signed in (ADR-020, rule 16: one row
per section). That screen gets a **Delete account** link at its foot, which opens the Delete
account screen in auth's area. That screen says what is deleted, the phone's data included, and
that the app closes. It asks the user to re-authenticate with what the account has (a password
field, a Google button, or both for a linked account), and has the destructive confirm. Signing out
alone erases nothing: the user can sign back in and carry on.

**The order,** in `DeleteAccountUseCase` (`:auth:data:impl`, since its outcome is auth's: ADR-020,
rule 19):

1. **Re-authenticate**, with the password or a fresh Google ID token from the screen's sign-in step
   ([ADR-009](009-ui-bound-sdks.md)). Offline, or with the wrong credential, this fails and nothing
   is deleted. Because the sign-in is now recent, Firebase doesn't refuse the account's delete in
   step 3.
2. **Delete the data in the cloud:** `SyncRepository.deleteRemoteData(uid)`. First it pauses sync
   for that user and waits for a sync that's running to finish. Then it runs every `Syncer`'s
   `deleteRemote(uid)`.
3. **Delete the Auth user.** Firebase signs out by itself, so there is no user to sync for.
4. **Erase the app's data:** `ActivityManager.clearApplicationUserData()`, what Settings → Apps →
   Clear storage does. Android deletes every database, preference, file, cache and download of the
   app, and what the SDKs keep, then closes the app. Nothing has to list them, so a store added later
   is erased too. The music files are the user's own, outside the app, and aren't touched.

**A failure in steps 2 or 3** returns the error. The user stays signed in, and sync stays paused, so
the phone doesn't write the data back into the account, which still exists. Retrying runs every
step again, and each step can be repeated safely. The pause is lifted when the user leaves the
Delete account screen (`DeleteAccountUseCase.cancel`), and it names its user, so it never stops
another account's sync. It lives in memory, so a process that dies with it set starts
without it. In that case, sync writes the phone's data back into the account, which isn't deleted,
and nothing is lost.

**Android refusing step 4** (it returns `false`, which happens only under a device policy) doesn't
fail the deletion, because the account is gone. It's logged
([ADR-029](029-sync-failures-are-logged.md)), sync resumes, for nobody as the user is signed out,
and the user goes to Sign in with the phone's data still there. A later sign-up would then push the
phone's settings and follows into the new account.

**What each `Syncer` does.** `Syncer` gains `deleteRemote(userId)`. A kind of data that syncs can't
be added without saying how it's deleted in the cloud, because it doesn't compile otherwise. The
phone's side needs nothing from the parts: step 4 erases it all.

- **Preferences.** Deletes `users/{uid}/settings/preferences`.
- **Follows.** Lists `users/{uid}/podcastFollows` from the server and deletes each document, in
  batches of up to 500.

**Offline in the middle.** The cloud side lists from the server, never from the cache, and gives
each write a time limit. Past it, the write is `RemoteError.Unavailable`, the user is told, and the
account is kept. If Firestore sends the queued delete later, that's still what the user asked for,
and retrying finishes the job.

**Errors** (ADR-007): `DeleteAccountError` is `Auth(AuthError)` or `Remote(RemoteError)`.
`AuthError` gains `RequiresRecentLogin`, for when Firebase refuses even after step 1. The screen
then asks the user to re-authenticate again.

**Security rules.** The owner may delete `settings/preferences` and `podcastFollows/{showKey}`,
with emulator tests showing that the owner can and another user can't. Sync never deletes either,
which `FollowsSyncer`'s and `PreferencesSyncer`'s tests check: ADR-031's tombstones and ADR-011's
rule 6 hold for every write that isn't an account being deleted.

**Credential Manager** isn't cleared when an account is deleted, as it isn't on sign-out yet.
launch/07 clears it on both.

## Alternatives considered

- **A Cloud Function triggered when the Auth user is deleted,** calling the Admin SDK's
  `recursiveDelete(users/{uid})` with retries, or Firebase's Delete User Data extension, which does
  the same. The app would make one call, every subcollection would go including ones added later,
  and an account deleted from the console for the web route (launch/08) would be cleaned up too. But
  Cloud Functions need the Blaze plan: a billing account with a card, a spend cap to set, and
  Artifact Registry storage for each deploy. It would also be the first server code in a project
  that keeps Firebase to accounts and sync (ADR-008). With two kinds of synced data, the app's way
  is about three round trips. This is worth revisiting if the synced data, or deleting from the
  console, grows.
- **Delete the Auth user first, then the data.** The rules would refuse the data, since it belongs
  to a user who no longer exists.
- **Re-authenticate only when Firebase answers `requires-recent-login`.** It saves a step, but the data
  is gone before Firebase refuses. It also needs another way to tell that the phone is offline
  before anything is deleted.
- **A separate set of erasers, apart from `Syncer`.** It would keep `Syncer` about syncing, but a
  part could add a `Syncer` and forget its eraser. Every kind of data that syncs is exactly what
  has to be deleted, so they are one contract.
- **Keep everything on the phone.** Deleting the account would delete what is in the cloud, as
  Play asks, and the user could go on without an account. But the next sign-up pushes the deleted
  account's settings and follows into the new account, and the user expects deleting the account to
  leave nothing of it behind.
- **Reset only what syncs:** the synced preferences to their defaults and every local follow, with a
  `resetLocal()` on each `Syncer`. It stops the push into a new account and keeps the podcasts and
  downloads. But the phone keeps everything else of the deleted account's, and it's a second contract
  every `Syncer` has to keep.
- **Each part erases its own storage,** then the app goes to Sign in without closing. Smoother, but
  every database, folder, cache and SDK's store must be listed, and kept listed as parts are added,
  and playback and running downloads must be stopped first. A store that's forgotten survives the
  deletion. Android's erase is complete by construction.
- **Erase the phone on signing out too.** Signing out to switch accounts would lose every download
  and position. Signing out is leaving, not deleting.
- **Keep sync paused in storage across a process restart,** until the deletion finishes or is
  abandoned. It would also cover the process dying mid-deletion, at the cost of a stored state that
  could stop sync for good if never cleared. Being written back into an account that still exists
  loses nothing.

## Consequences

- Users can delete their account from the app, which Play requires.
- A kind of data that syncs must implement its delete in the cloud, with tests, to compile.
- Deleting is a foreground operation of `SyncRepository`, alongside the WorkManager sync it pauses.
- The rules allow the owner to delete their synced documents, so a bug in the app could delete
  them. Only the two `Syncer`s' `deleteRemote` call the delete.
- After deleting, the phone is a fresh install: the app closes, and the next launch is a first run,
  starting at Sign in, with the audio permission prompt and a library scan. The user's music files
  are untouched.
- The user sees the app close rather than a screen that says it's done. The Delete account screen
  says so before they confirm.
- An account deleted from the Firebase console (the web route, launch/08) leaves its documents in
  Firestore, to be deleted by hand from the console along with it.
- A future subcollection under `users/{uid}` that isn't a `Syncer`'s data has no delete, and needs a
  decision here first.

## Rules

1. `[enforced]` Every `Syncer` implements `deleteRemote(userId)`, which deletes all its data in the
   cloud for that user. The interface requires it.
2. `[convention]` Deleting an account follows the order of Decision: re-authenticate, delete the data
   in the cloud with sync paused, delete the Auth user, then erase the app's data. Nothing on the
   phone is erased before the account is deleted. `DeleteAccountUseCase` is the only caller of
   `SyncRepository.deleteRemoteData` and of the erase.
3. `[convention]` A `:network:api` deletes synced data only from its `Syncer`'s `deleteRemote`. Sync
   itself never deletes a synced document.
4. `[convention]` The cloud side of a delete lists from the server and gives each write a time limit,
   so that offline isn't reported as done.
5. `[enforced]` The security rules allow deleting a document under `users/{uid}` to its owner only.
   The rules tests check each kind, for the owner and for another user.
6. `[convention]` All the user's data in the cloud is a `Syncer`'s data under `users/{uid}`.
   Anything else stored there needs a revision of this decision saying how it's deleted.
7. `[convention]` The phone's data is erased by Android (`clearApplicationUserData`), never by a list
   of stores in the app. Signing out erases nothing.

**Conformance.** Rule 1 is checked by the compiler, and by each `Syncer`'s tests:
`PreferencesSyncerTest` in `:common:userdata:impl` and `FollowsSyncerTest` in `:device:podcasts:impl`.
Rule 2 is `DeleteAccountUseCaseTest` in `:auth:data:impl`, which covers the order, a failed
re-authentication deleting nothing, a failed cloud delete or account delete keeping the account,
the pause and the phone's data, the erase coming only after the account is deleted, and Android
refusing to erase still succeeding. `DeleteAccountViewModelTest` in `:auth:ui:impl` covers which ways to
re-authenticate the screen offers. The pause is covered by `SyncedDataDeletionTest`, `SyncRunnerTest`
and `SyncTriggersTest` in `:common:sync:impl`. Rule 3 is reviewed, and the `Syncer`s' tests check
that `sync` deletes nothing. Rule 4 is `ServerTimeoutTest` in each `:network:impl`, which the
deletes go through, and is otherwise reviewed: listing from the server needs Firestore itself. Rule 5 is
`firebase/test/firestore.rules.test.mjs`, run in CI against the emulator. Rules 6 and 7 are
reviewed. That Android's erase closes the app and the next launch is a first run is checked by hand
on a device.
