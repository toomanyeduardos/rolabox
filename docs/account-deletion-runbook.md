# Deleting an account by email

How to handle a request to delete an account that arrives at `rolabox.app@gmail.com` and not
through the app. The promise, on the [deletion page](../site/delete-account/index.html), is
**done within 30 days, with a reply when it's done**. The in-app deletion and the order of its steps
are [ADR-037](adr/037-deleting-an-account.md). This runbook follows the same order by hand.

Do this in the **production** Firebase project, from the Firebase console, or with the Firebase CLI
signed in as an owner.

## 1. Check who's asking

1. Firebase console → **Authentication → Users**, and search for the email address the request came
   from.
2. **The sender must be the account's email.** If the request came from another address, reply asking
   them to write from the account's email, and stop. Don't delete an account because someone named it.
3. **No account with that email:** reply that there's no Rolabox account for that address, and stop.
4. Copy the account's **User UID**.

## 2. Delete the synced data

Everything the account has in Firestore lives under `users/{uid}`: `settings/preferences` and the
`podcastFollows` collection (ADR-037, "What each `Syncer` does").

With the CLI (recommended, deletes everything under the user at once):

```bash
firebase firestore:delete "users/<uid>" --recursive --project <production-project-id>
```

Or in the console: **Firestore Database → `users` → `<uid>`**, then delete the `podcastFollows` and
`settings` collections (⋮ → Delete collection). Check that `users/<uid>` has nothing left.

If the app ever syncs a new kind of data, its `Syncer`'s `deleteRemote` says where it lives. Check
that this step still covers it.

## 3. Delete the account

**Authentication → Users →** the account's row **→ ⋮ → Delete account.**

Delete the data before the account. Once the account is gone, the UID is harder to find again.

## 4. Reply

Reply to the request: the account and its synced data are deleted. Crash reports aren't linked to an
account and expire after 90 days. Data still on their phones stays there until they clear
Rolabox's storage or uninstall it.

Keep the request thread in the mailbox as the record. Don't copy emails or UIDs into this repo,
Trello or anywhere public.
