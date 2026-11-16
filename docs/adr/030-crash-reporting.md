# ADR-030: Crashlytics reports crashes, ANRs and named non-fatal errors from every user, with nothing that identifies them

- **Status:** Accepted
- **Date:** 2026-11-16
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox has no crash reporting. Release builds are minified with R8, so a stack trace only reads
back through that release's `mapping.txt`. Play Console's Android vitals deobfuscates crashes from
Play installs, since the mapping travels in the AAB. But it has no non-fatal errors, and only
covers Play installs.

Firebase Crashlytics fills that gap. Unlike Auth and Firestore, it sends data without our code calling
it:

- **On every launch,** it fetches its settings from Firebase. Firebase Sessions, which Crashlytics
  depends on, sends a session event with the Firebase installation ID and the device's details. That
  is how Crashlytics counts crash-free users.
- **After a crash or an ANR,** it sends the report the next time the app starts.

[ADR-008](008-one-app-with-an-optional-account.md) allows none of this for most users. Rule 6 says
the app makes no Firebase request while the user is signed out or in no-account mode, and rule 9 says
a Firebase product that sends data by itself needs an ADR saying how it stays silent in no-account
mode. Applying rule 6 as written would mean reports only from signed-in users, so none from the Sign
in screen and none from anyone who chose to use the app without an account.

No-account mode is about the account: no account is created, nothing of the user's is stored in the
cloud, and nothing is synced (ADR-008, "Every other request is made with or without an account"). A
crash report holds none of that. It holds a stack trace, the device's model and state, and two random
per-install IDs that reset when the app is reinstalled. Firebase also sees the device's IP address
when the report arrives.

Non-fatal errors are different. Crashlytics keeps each recorded exception's message and its cause
chain. The errors worth recording carry personal data in those messages: a Firestore exception's
message can hold the document path, which holds the uid, and a file error can hold a path, which
holds song, album and artist names. [ADR-029](029-sync-failures-are-logged.md) logs those messages to
logcat, which never leaves the phone. Crashlytics would send them.

The device's minimum SDK is 29. Crashlytics reads ANRs from `ApplicationExitInfo`, which exists from
Android 11 (API 30).

## Decision

We will use **Firebase Crashlytics for every user**, signed in or not, in no-account mode or not. It
has no setting to turn it off. It sends nothing that identifies the user or their library.

**What's reported.**

| What | How |
| --- | --- |
| Crashes (uncaught exceptions) | Automatically, with every thread's stack trace. |
| ANRs | Automatically, on Android 11 and later. |
| Non-fatal errors | Only those our code reports, through `NonFatalReport`, as listed below. |

There are no native crashes to report, since Rolabox has no native code of its own, so the NDK library
isn't added. Freezes shorter than an ANR, and slow frames, are not Crashlytics' to report. Performance
monitoring is its own decision (ticket 122).

**What's never sent.** No user ID (`setUserId`), no custom keys, no Crashlytics log lines, and no
exception message from an SDK or from our own code's exceptions. No Google Analytics, so no
breadcrumbs. What a report holds is what Crashlytics collects by default: the stack trace, the device's
model, OS version, free memory and disk, orientation, whether it's rooted, whether the app was in the
foreground, the app's version, and the Crashlytics and Firebase installation IDs.

**Non-fatal errors go through one interface.** `NonFatalReport`, a one-function interface in
`:common:util`, takes a `kind` and the exception that caused it:

- **`kind` is built from names, never from data.** It names the typed error and, where there is one,
  the backend's code: `"RemoteError.Rejected PERMISSION_DENIED"`, `"Unplayable.EngineFailed
  ERROR_CODE_DECODER_INIT_FAILED"`, `"StorageError.Corrupted"`. It never holds a field's value, a URI,
  a path, an ID or an exception's message. A data class's `toString()` holds its fields, so it isn't
  used as a `kind`.
- **The cause's stack trace is kept, its text isn't.** The implementation records a `ReportedError`
  whose message is the `kind`, and whose stack trace is the cause's, copied. The cause itself isn't
  attached, so its message and its own causes never leave the phone. Crashlytics groups non-fatals by
  stack trace, so the place that failed is still what tells two reports apart.
- **Where there's no exception** (tags that make no sense), the stack trace is the caller's.

**The non-fatal errors reported.** Each is reported where the exception becomes a typed error
([ADR-007](007-error-handling.md)), next to the logcat line ADR-029 or the area already writes there.

| Error | Where | Not reported |
| --- | --- | --- |
| Stored data that can't be parsed: `StorageError.Corrupted`, from DataStore or the library's Room database (the index and the artwork records) | `:common:storage:impl`, `:device:library:local:impl` | `StorageError.Unavailable`: an I/O error that the next read may not have |
| A sync the backend turned down: `RemoteError.Rejected`, with the Firestore code | Each sync `:network:impl` (today `:common:userdata:network:impl`) | `RemoteError.Unavailable`: no connection, a timeout or contention, which every offline device has |
| A song the engine couldn't play: `Unplayable.Damaged`, `Unsupported`, `Unreadable` and `EngineFailed`, with Media3's error code name; and `PlayerUnavailable`, when our own session refuses our controller | `:device:playback:impl` | `Unplayable.Missing` (the file was deleted) and `Unplayable.Protected` (DRM, as an `.m4p` is): the phone's state, not a bug |
| Indexing the library: MediaStore's query failing, a file that can't be read, and tags that make no sense (`LibraryError.TagsMalformed`, which our own tag parser returns) | `:device:library:local:impl` | A `SecurityException` (the permission went away) and a `FileNotFoundException` (the file did). `ArtistNotFound`, `AlbumNotFound`, `GenreNotFound`: an item deleted while a screen showed it |

Errors that are expected outcomes, such as no connection, a deleted file or a wrong password, are not
reported. A report means something is broken: a bug, a setup mistake, or a file or a phone that
Rolabox doesn't handle. Adding a kind of report follows the rules below and is reviewed. It doesn't
need a revision of this ADR.

**Where Crashlytics lives.**

- **`:common:crashreporting:impl`** holds the implementations of `NonFatalReport`, one per build type,
  and is the only module that depends on the Crashlytics SDK. It has no `:api`, since the interface it implements is in
  `:common:util` ([ADR-020](020-modules-by-product-area.md), rule 9). `:app` depends on it with
  `implementation`, like every other `:impl`.
- **`:common:testing`** holds `FakeNonFatalReport`, which keeps what was reported so that a test can
  check that an error was reported, and that an expected one wasn't.
- **The Crashlytics Gradle plugin** is applied by `rolabox.android.application.firebase`, next to the
  google-services plugin (ADR-008, rule 3).

**Debug builds don't start Crashlytics.** Turning collection off isn't enough. With it off,
Crashlytics still saves each crash on the phone, waiting to be told to send or delete it, and a
release build installed over a debug one, as on a developer's phone, sends them all on its first
start. So debug builds leave Crashlytics out entirely:

- `:app`'s debug manifest removes Crashlytics' registrars from Firebase's component discovery, so
  Firebase never creates it. No exception handler is installed, and nothing is saved.
- `:common:crashreporting:impl` binds `NonFatalReport` to logcat in `src/debug`, and to Crashlytics in
  `src/release`, so nothing in a debug build asks for Crashlytics.
- `rolabox.android.application.firebase` still sets the `firebase_crashlytics_collection_enabled`
  manifest flag from the build type, off in `debug` and on in `release`, as a second guard. Nothing
  else in the app turns collection on or off.

**Stack traces are readable.**

- The release build keeps the `SourceFile` and `LineNumberTable` attributes, so frames have line
  numbers after R8.
- The release build uploads its mapping file to Crashlytics, with the Crashlytics Gradle plugin's
  `mappingFileUploadEnabled`, so every release's reports are deobfuscated with no manual step.
- The upload needs a real Firebase app. With the placeholder config (ADR-008, rule 4), which is what
  CI and forks build with, the upload is turned off and the build logs that it was skipped. A release
  built with the placeholder can't be read in Crashlytics, and can't be shipped anyway, since Play
  rejects its debug signature.

## Alternatives considered

- **Reports only from signed-in users (ADR-008 rule 6 as written).** No change to ADR-008. But most of
  the crashes would go unseen: those on the Sign in screen, those before the user chooses, and every
  crash of someone using the app without an account. And it would tie a crash report to having an
  account, which is the opposite of what the report should hold.
- **A setting to turn it off, on by default.** The user gets a say, and some apps do this. But a crash
  report holds nothing about the user or their library, and the setting is one more preference to
  store, sync or not sync, and test. If what's sent ever includes something personal, this decision
  changes with it.
- **A setting that starts off (opt-in).** The strictest, but almost nobody turns on crash reporting,
  so there would be almost no reports.
- **Record every `Either.Left` as a non-fatal error.** Nothing to choose. But most typed errors are
  expected outcomes, like no connection or a deleted file, and they would bury the reports that mean
  something is broken. Crashlytics also keeps only the last eight non-fatals of a session.
- **Record the SDK's exception itself.** The most detail, and no `ReportedError`. But its message and
  its causes can hold the uid, a document path, a file path or a song's name, and every reporting site
  would have to be checked for that. Stripping the text in one place makes that the implementation's
  job.
- **`kind` from the typed error's `toString()`.** No string to write at each site. But a data class's
  `toString()` holds its fields, such as `TagsMalformed`'s URI, so it can't be trusted to hold no
  data.
- **Crashlytics in `:app`, with no new module.** One module fewer. But `:app` would depend on a
  backend SDK directly, and the rule "only one module calls Crashlytics" couldn't be checked by module,
  the way [ADR-022](022-data-source-sub-parts.md) checks Firestore.
- **Sentry, or another crash reporter.** Richer non-fatal handling, and some can be self-hosted. But
  Firebase is already in the app (ADR-008), the mapping upload and ANR reporting come with
  Crashlytics, and a second backend means a second account, quota and privacy entry.
- **Android vitals only.** Nothing to add. But it has no non-fatal errors, and covers Play installs
  only.

## Consequences

- Crashes, ANRs on Android 11 and later, and the listed non-fatal errors from every release user
  reach Crashlytics, readable through each release's mapping file.
- **ANRs on Android 10 aren't reported.** Android vitals still shows them for Play installs.
- **A user in no-account mode sends crash reports and a session event on every launch.** ADR-008's
  promise for no-account mode is about the account, and that still holds: nothing of the account, the
  library or the preferences is sent. ADR-008 is revised to name Crashlytics as an exception to rule 6.
- **The privacy policy and Play's Data safety form must declare it:** crash logs and diagnostics,
  collected from every user, not optional, not shared, and the installation IDs as device or other
  IDs. The About screen ticket covers where the policy is linked.
- **Under GDPR, the installation IDs and the IP address count as personal data,** even though they
  don't identify anyone by name. Collecting them without consent relies on crash reporting being
  needed to keep the app working. That's a legal judgment, not a technical one, and it should be
  checked before release in the EU.
- **A report says less than logcat.** Without the exception's message, a non-fatal says what failed
  and where, not the SDK's reason. ADR-029's logcat lines still hold the reason, for a device at hand.
- Every place that reports a non-fatal error injects `NonFatalReport`, and its tests use the fake.
  `SyncRunner` is unaffected: sync failures are reported where Firestore's exception is converted, as
  ADR-029 logs them.
- Debug builds and instrumented tests send nothing and save nothing, so testing never shows up in
  Crashlytics, even after a release build is installed over a debug one. Checking that a report
  arrives takes a release build.
- **Debug and release differ in one binding.** A bug in `CrashlyticsNonFatalReport` only shows in a
  release build, and a debug build's non-fatal reports go to logcat, under the `NonFatalReport` tag.
- Crashlytics and Firebase Sessions add to the APK and to the work done at process start.

## Rules

1. `[convention]` Crashlytics is used for every user, whatever their account state, with no setting
   to turn it off. ADR-008's rule 6 names it as an exception.
2. `[enforced]` Only `:common:crashreporting:impl` depends on the Crashlytics SDK, and
   `ModuleRules.kt` fails the build otherwise. Nothing else calls Crashlytics.
3. `[convention]` Nothing sets a Crashlytics user ID, custom key or log line, and the Google Analytics
   SDK isn't added.
4. `[convention]` Non-fatal errors are reported only through `NonFatalReport`. Its `kind` names the
   typed error and the backend's code, and never holds a field's value, a URI, a path, an ID or an
   exception's message.
5. `[enforced]` The recorded `ReportedError` holds the `kind` as its message and the cause's stack
   trace, and neither the cause's message nor its causes. `ReportedErrorTest` checks this.
6. `[convention]` A non-fatal error is reported where the exception becomes a typed error, and only
   when it means something is broken. Expected outcomes, such as no connection, a deleted file or a
   wrong password, are not reported. Each site's tests check, with `FakeNonFatalReport`, what's
   reported and what isn't.
7. `[enforced]` Debug builds don't start Crashlytics: `:app`'s debug manifest removes its registrars,
   and debug binds `NonFatalReport` to logcat. Its collection flag is off in `debug` and on in
   `release`, set only by `rolabox.android.application.firebase`. `NoCrashlyticsInDebugTest` in
   `:app`'s instrumented tests and `CrashReportingTest` in `build-logic` check it.
8. `[convention]` The release build keeps `SourceFile` and `LineNumberTable`
   (`app/src/main/keepRules/crash-reporting.keep`), and uploads its mapping file whenever it's built
   with a real Firebase config. With the placeholder, the upload is off and the build says so.
   `CrashReportingTest` checks when the upload is on.
9. `[convention]` The Crashlytics Gradle plugin is applied only through
   `rolabox.android.application.firebase`.

**Conformance.** Rule 2 is checked by `ModuleRules.kt` when the build is configured, with a case in
`ModuleRulesTest`. Rule 5 is covered by `ReportedErrorTest` in `:common:crashreporting:impl`, and
rules 7 and 8 by `CrashReportingTest` in `build-logic`. Rule 7 is also covered by
`NoCrashlyticsInDebugTest`, an instrumented test of the debug build, which `./gradlew check` doesn't
run. Rule 6 is covered at each reporting site, with
`FakeNonFatalReport`: `DataStorePreferencesStoreTest`, `FirestoreErrorsTest`,
`LibraryErrorReportsTest` and `UnplayableFilesTest`. Rules 1, 3, 4 and 9 are reviewed. That a report
arrives, readable, from a minified release build is checked by hand, with a forced crash, before
each release that changes this setup.
