# Releasing Rolabox

How a release is built, signed and uploaded to Google Play, and how the upload key is kept. The
decisions behind these steps are in [ADR-035](adr/035-release-signing-and-versioning.md). Releases
are built and uploaded by hand for now; building and uploading from CI is launch/16.

## The keys

- **App signing key:** generated and held by Google through Play App Signing. Nobody outside Google
  ever has it, so it can't be lost.
- **Upload key:** held by the maintainer, signs every bundle uploaded to Play. Used for nothing but
  Rolabox.
- **Debug key:** signs debug builds, and release builds when no upload key is configured. Play
  rejects anything it signs.

## One-time setup

### 1. Create the upload key

Choose a folder outside the repo for the keystore, and an alias for the key. Neither is written in
the repo (ADR-035, rule 3): the commands below use `<keystore>` for the keystore's path and
`<alias>` for the alias, and you note your real ones in the password manager entry (step 2).

`keytool` asks for one password: use a strong, generated one.

```bash
keytool -genkeypair -v -keystore <keystore> -storetype PKCS12 -alias <alias> -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Rolabox"
```

- RSA 4096, PKCS12, valid for 30 years (ADR-035).
- With PKCS12, the key password is the keystore password: `keytool` doesn't ask for a second one.
  Use the same value for both properties below.
- Never put the keystore inside the repo. `.gitignore` ignores `*.jks`, `*.keystore` and `*.p12`,
  and CI fails if one is tracked, but keeping it outside the checkout is what keeps it safe.

### 2. Back it up

Right after creating it, before the first upload:

- **The password:** in the password manager, in one entry named "Rolabox upload key" with the
  alias, the keystore's path, and a note of where the keystore copies are. With PKCS12 the key password is the keystore
  password.
- **The keystore file:** one copy in private cloud storage, and one on offline storage kept apart
  from the machine that builds releases. The file is useless without the password, which is kept
  apart from it.

Check that the saved password opens the keystore, while a typo is still cheap to fix:

```bash
keytool -list -keystore <keystore> -alias <alias>
```

It should list your alias as a `PrivateKeyEntry`. Run `chmod 600` on the keystore so only you
can read it. If a copy changes (a new key after a reset), update all of them.

### 3. Tell Gradle where it is

Add these to `~/.gradle/gradle.properties`, the one in your home folder, never the repo's:

```properties
rolaboxUploadStoreFile=<keystore, as an absolute path>
rolaboxUploadStorePassword=<password>
rolaboxUploadKeyAlias=<alias>
rolaboxUploadKeyPassword=<password>
```

The path must be absolute: Gradle doesn't expand `~`. Both passwords are the same one, since
PKCS12 has only one. Run `chmod 600 ~/.gradle/gradle.properties` so only you can read it.

Set all four or none. With none, release builds are signed with the debug key and the build warns
that Play will reject them. With only some, the build fails and names the missing ones. On CI the
same values go in as `ORG_GRADLE_PROJECT_rolaboxUpload*` environment variables (launch/16).

### 4. Enrol in Play App Signing (with launch/10)

Create the app in Play Console with these settings:

- **Name:** Rolabox
- **Package:** `com.eduardoflores.rolabox`, set by the first bundle you upload
- **Default language:** English (United States), en-US
- **App or game:** App
- **Price:** Free
- **Category:** Music & Audio

The developer account's public name and contact email are the ones in the privacy policy.

Upload the first bundle to **internal testing**. When Play asks about app signing, keep the
default: **let Google generate and manage the app signing key**. Don't upload one. The first
bundle you upload registers your upload key. The App content forms are in "Play Console
declarations" below. Play asks for the foreground service declarations only after a bundle that
uses them is uploaded.

Internal testing isn't reviewed. So until a closed test release has been sent for review and
approved, internal testers see the app as "com.eduardoflores.rolabox (unreviewed)", with no icon or
description, and "Send app for review" stays grayed out. Play didn't generate a pre-launch report
for the internal upload either. It runs on the first closed test upload.

### 4b. Set up the closed test (with launch/11)

The developer account is personal and older than November 2023, so Play's rule of 12 testers for
14 days before production doesn't apply. The closed test is there for the review, the pre-launch
report, and real testers. Console paths go after the app's URL, `…/app/<app id>/`.

1. **`closed-testing`** → **Manage track** on "Closed testing – Alpha".
2. **Countries / regions:** United States.
3. **Testers:** an email list named "Rolabox closed test", with each tester's Google account. Its
   **feedback email** is the contact email in the privacy policy. The opt-in link on this tab only
   works once a release on the track is approved.
4. **Create new release** and upload the bundle there, rather than adding an earlier one from the
   library. A saved release can't be edited: create a new one, which replaces the draft.
5. **`publishing`** → **Send changes for review.** The first review covers everything at once: the
   store listing, App content, countries and the release. Google says up to 7 days; 1.0.1's took under an hour. Check **Submission activity** on that page for its status. With managed
   publishing off, an approved release goes live by itself.

What Play reported on the first closed release (1.0.0, 2026-11-23):

- **"Required hardware feature declarations are restricting your app's availability":
  `android.hardware.screen.portrait`.** This blocks sending for review. The portrait lock (ADR-018)
  makes Android assume a portrait screen is required, so the manifest declares it with
  `android:required="false"`. Fixed in 1.0.1. The lock itself stays.
- **"This App Bundle contains native code, and you've not uploaded debug symbols."** Only a warning,
  so ignore it. The only native libraries are AndroidX's (`libandroidx.graphics.path.so` and
  `libdatastore_shared_counter.so`), which ship stripped, so there are no symbols to upload.

### 5. Register the key fingerprints in Firebase (with launch/02)

Sign in with Google checks the SHA-1 of the key that signed the installed APK. In the Firebase
project of the Play build, add the SHA-1 and SHA-256 of:

- **The app signing key:** copy them from Play Console, under the app's App signing page.
- **The upload key,** so a locally built signed release can sign in during the smoke test:

```bash
keytool -list -v -keystore <keystore> -alias <alias> | grep -E "SHA1:|SHA256:"
```

They go in Firebase console → Project settings → the Android app → **Add fingerprint**. You can
also add them from `firebase/`:

```bash
npx firebase apps:list ANDROID --project <project-id>
```

```bash
npx firebase apps:android:sha:create <app-id> <sha> --project <project-id>
```

```bash
npx firebase apps:android:sha:list <app-id> --project <project-id>
```

The list should show the SHA-1 and SHA-256 of both keys, so at least four. If your debug key is
registered for development sign-in, it's two more. The fingerprints are public, not secrets.

Sign in with Google checks them on Google's side, so a bundle already on Play starts working once
they're added. You don't need to rebuild it. Still, download `google-services.json` again and
replace `app/google-services.json` before the next build: each fingerprint adds an Android OAuth
client to it.

Check the fingerprint against the app Play actually installed, because the one shown in Play
Console may not be the key that signs the APKs. Play can sign with a newer key in a rotation
lineage, as it did for 1.0.0. Install from Play, connect the phone, then pull the APK and print its
certificate:

```bash
adb pull "$(adb shell pm path com.eduardoflores.rolabox | head -1 | sed 's/package://' | tr -d '\r')" base.apk
```

```bash
apksigner verify --print-certs base.apk | grep -E "SHA-1|SHA-256"
```

If its SHA-1 isn't in the Firebase list, add its SHA-1 and SHA-256 too. Without it, Sign in with
Google fails with `[28444] Developer console is not set up correctly` in logcat, which the app
shows as "No account found on this device".

The Play build uses the same Firebase project as development (ADR-008), and the release build reads
the real `app/google-services.json` from your checkout, as a debug build does.

### 6. Deploy the Firestore rules (with launch/02)

The production project runs the rules in the repo, never ones edited in the console (ADR-010). From
`firebase/`, after `npm ci` and `npx firebase login`:

```bash
npm run deploy:rules -- --project <project-id>
```

It runs the emulator tests first and deploys only if they pass. Then check what's deployed:

```bash
npm run rules:check -- --project <project-id>
```

It prints a diff and fails if the deployed rules aren't `firebase/firestore.rules`. Run the deploy
again whenever the rules change.

## Each release

1. **Bump the version** in the repo's `gradle.properties`:
   `rolabox.versionName=MAJOR.MINOR.PATCH`. The build derives `versionCode` as
   `MAJOR * 10000 + MINOR * 100 + PATCH`. Every upload needs a version no earlier upload had, even
   a rebuild of the same code. `MINOR` and `PATCH` go up to 99.
2. **Commit and tag it** `v<versionName>`, for example `v1.0.0`, and push the tag: pushing the
   branch doesn't push tags.

   ```bash
   git tag v1.0.1
   ```

   ```bash
   git push origin v1.0.1
   ```
3. **Build the bundle** from that commit, with the signing values from step 3 set:

   ```bash
   ./gradlew :app:bundleRelease
   ```

   The bundle is `app/build/outputs/bundle/release/app-release.aab`. Its R8 mapping file is inside
   it, so Play Console deobfuscates crashes, and the build uploads it to Crashlytics too
   (ADR-030). If the build printed "This release is signed with the debug key", the signing values
   aren't set; if it printed "Firebase config: the placeholder", `app/google-services.json` is
   missing, so sign-in won't work and Crashlytics gets no mapping file. Don't upload either.
4. **Check the signature:**

   ```bash
   jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
   ```

   It should say `jar verified.` and be signed by your upload key's name, not `CN=Android Debug`.
5. **Smoke-test** a minified build on a device (launch/15).
6. **Check the production rules:** `npm run rules:check -- --project <project-id>` from
   `firebase/` passes (setup step 6).
7. **Upload the `.aab`** to the Play Console track. Never upload an APK.
8. **Check the build from Play:** install it from the track, and check that Google sign-in,
   email/password sign-in and preference sync work. Google sign-in failing only here means the
   app signing key's fingerprints are missing from Firebase (setup step 5).

## Play Console declarations

What Rolabox answers in Play Console → Monitor and improve → Policy and programs → **App
content** (or `app-content/overview` at the end of the app's console URL). The privacy policy
(`site/privacy/`) and the Data safety answers must say the same thing (ADR-038, rule 5). So a
change that updates the policy also updates this section and Play Console, in the same PR.

### Privacy policy and app access

- **Privacy policy URL:** `https://toomanyeduardos.github.io/rolabox/privacy/`
- **App access:** all functionality is available without special access. The account is optional,
  and anyone can create one in the app.

### Ads, IDs and app categories

| Declaration | Answer |
| --- | --- |
| Ads | The app contains no ads |
| Advertising ID | Not used. The merged release manifest has no `com.google.android.gms.permission.AD_ID` |
| Target audience | 18 and over. Not designed to appeal to children (the policy: not directed at children under 13) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health apps | None |

### Content rating

Questionnaire category: **All other app types**. No violence, sexual content, profanity, drugs or
gambling. Users can't interact with each other or exchange content, the app doesn't share the user's
location, and it sells nothing. Podcasts are shows the user picks from Apple's public directory,
played as they are published, and Rolabox hosts none of them.

### Foreground services

Each declaration has a video: a YouTube (unlisted) or Google Drive link that plays without signing
in.

| Service | Type | Play's use case | What the video shows |
| --- | --- | --- | --- |
| `PlaybackService` (`:device:playback:impl`, ADR-023) | `mediaPlayback` | Media playback | A song playing, the app sent to the background, the media notification with playback continuing, then pausing from the notification |
| `EpisodeDownloadService` (`:device:playback:impl`, ADR-031) | `dataSync` | Network transfer: download | A podcast episode starting to download, the app sent to the background, the download notification until it finishes |

Record each on a device, for at most 3 minutes, and stop with Ctrl-C:

```bash
adb shell screenrecord /sdcard/fgs-media-playback.mp4
```

```bash
adb pull /sdcard/fgs-media-playback.mp4
```

A new foreground service type needs a new declaration and video before the release that adds it.

### Permissions

`READ_MEDIA_AUDIO`, and `READ_EXTERNAL_STORAGE` up to API 32 (ADR-024), read the music library,
which is the app's core functionality. Play has no declaration form for either. The forms are only
for photos and videos, all files, SMS and call log, location and the like.

### Data safety

- **Collects or shares user data:** yes.
- **All user data is encrypted in transit:** yes. Firebase, Crashlytics, Apple, MusicBrainz and the
  Cover Art Archive are reached over HTTPS. The plain HTTP that ADR-034 allows reaches only podcast
  hosts, and carries no user data.
- **Account creation:** username and password, and OAuth (Google).
- **Users can ask for their data to be deleted:** yes. In the app, and at
  `https://toomanyeduardos.github.io/rolabox/delete-account/` (ADR-037, ADR-038).
- **Shared:** nothing. Firebase is Rolabox's service provider, which Play doesn't count as sharing.
  A podcast search goes to Apple only because the user searches, which is Play's exemption for a
  transfer the user starts and expects. So it's collected, but not shared.
- **Processed ephemerally:** yes for in-app search history, which goes to Apple only to get results
  and isn't stored. No for every other type.

| Data type | Collected | Required or optional | Purpose | The policy's row |
| --- | --- | --- | --- | --- |
| Personal info → Email address | Yes | Optional: only with an account | Account management | Email address … only if you create an account |
| Personal info → Name | Yes | Optional | Account management | Name (Google sign-in) |
| Personal info → User IDs | Yes | Optional | Account management | Account ID |
| Personal info → Other info | Yes | Optional | Account management | Profile photo link (Google sign-in) |
| App activity → Other actions | Yes | Optional | App functionality | Settings and the podcasts you follow, only while signed in |
| App activity → In-app search history | Yes | Optional | App functionality | The text of a podcast search, sent to Apple's iTunes Search API |
| App info and performance → Crash logs | Yes | Required | Analytics | Crash reports: stack trace |
| App info and performance → Diagnostics | Yes | Required | Analytics | Crash reports: device model, Android version, app version |
| Device or other IDs | Yes | Required | Analytics | Crash reports: installation IDs (ADR-030) |

**Not declared, and why.** The policy describes these, but they aren't user data in Data safety's
sense:

- **An album's title and artist, sent to MusicBrainz and the Cover Art Archive** when Online Artwork
  is on. This is catalog data about a published album, not about the user, and nothing links it
  to them.
- **Podcast feeds, images and audio, fetched from each show's host.** These are downloads of
  public content. The hosts see only the request and the IP address.
- **Android backup** goes to the user's own Google account, under Android's control, not Rolabox's.

## Store listing text

The text of Play Console → Grow users → Store presence → Main store listing (launch/12). Change it
here first, then paste it into Play, so the listing and the app change together. Play's limits are
30, 80 and 4000 characters. Don't claim a feature the release doesn't have, and don't repeat
keywords to rank.

Rolabox is drawn after a click-wheel player, so the listing says whose it isn't, as About → Legal
does in the app (card 96). The full description ends with that line. Apart from it, the name, short
description, full description, icon, feature graphic and screenshots don't use the word "iPod",
"Click Wheel", Apple's logo or any other Apple mark, and no other company is named, so nothing
suggests the app is anyone else's.

**App name** (29):

```text
Rolabox: Offline Music Player
```

**Short description** (79):

```text
A music player with a scroll wheel, for the music you own. Offline, no account.
```

**Full description** (1298):

```text
Rolabox plays the music you already have: the audio files on your phone. There's no catalog to browse, no subscription and no ads.

It looks like a hardware player, not a streaming app: a brushed aluminum body, a backlit LCD-style display, and a wheel you turn to move through lists and press to choose. Turn on the clicker and every step of the wheel clicks.

YOUR MUSIC
• Browse by artist, album, song or genre
• Shuffle all your songs from the main menu
• Shuffle and repeat, for a song or the whole queue
• Missing album covers can be downloaded from an open online archive, or turned off in Settings

PODCASTS
• Search for shows and follow them
• Episodes download to your phone before they play

WORKS WITHOUT AN ACCOUNT
Rolabox works fully without an account. If you add one, your settings and the shows you follow sync between your devices, and you can delete the account and its data from the app at any time.

MAKE IT YOURS
• Light or dark, or follow the system
• Choose which rows the main menu shows

OPEN SOURCE
Rolabox is open source, under the Apache 2.0 license: https://github.com/toomanyeduardos/rolabox

Rolabox is an independent app. It is not affiliated with, endorsed by or sponsored by Apple Inc. iPod is a trademark of Apple Inc., registered in the U.S. and other countries.
```

## Store icon and feature graphic

Play's 512 × 512 icon and 1024 × 500 feature graphic are rendered with the screenshots (ADR-041),
by `./gradlew storeScreenshots`, as `build/store-screenshots/play/icon-512.png` and
`feature-graphic.png`. The icon is the launcher icon's two layers, edge to edge; Play rounds the
corners itself. The feature graphic has its own aluminum background, so it reads on Play's light
and dark themes. Upload both in Main store listing → Graphics, and redo them when the launcher
icon or the device's look changes.

## Store and README screenshots

The Play listing's screenshots and the README's are rendered by Paparazzi on the JVM, at a Pixel 6's
1080 × 2400, in light and in dark (ADR-039). Redo them when a screen they show changes, and before a
release whose listing should show it.

1. **Get the README's art,** once per machine. The README set shows real music and a real podcast,
   whose art is never committed. It lives in `.screenshots-local/` at the root of the repo, which
   Git ignores. Without these files the README cases are skipped, and the Play set is still made.

   | File | What | Where it comes from |
   | --- | --- | --- |
   | `u2-best-of-1990-2000.jpg` | U2, *The Best of 1990-2000* | Cropped from a phone screenshot of Now Playing; ask the maintainer, or crop the cover from your own copy to a square |
   | `the-daily-2026-11-01.jpg` | *The Daily*, "Want to Enjoy Music More? Go For a Run." (1 Nov 2026) | The episode's `itunes:image` in the feed, `https://feeds.simplecast.com/54nAGcIl` |

   ```bash
   mkdir -p .screenshots-local
   curl -L -o .screenshots-local/the-daily-2026-11-01.jpg https://image.simplecastcdn.com/images/12d3db32-0c1e-4c49-b672-cf6fe68edf5d/c7ad2106-c464-4c75-9045-5849873dd050/640x640/3000241103_km_nyc_marathon_f_633966.jpg
   ```

2. **Render them:**

   ```bash
   ./gradlew storeScreenshots
   ```

   The images are in `build/store-screenshots/play/` and `build/store-screenshots/readme/`, each
   screen as `<name>_Light.png` and `<name>_Dark.png`.

3. **Upload the Play set** in Play Console → Grow users → Store presence → Main store listing →
   Phone screenshots. Play takes 2 to 8, each side between 320 and 3840 px, at most 2:1; four or
   more at 1080 px or more make the app eligible for Play's recommendations. There are 9 screens,
   so leave one out. The suggested 8, in this order, are `01` to `04` and `06` to `09`: main menu,
   artists, an album's songs, Now Playing, Settings, sign in, a show's episodes, and Now Playing an
   episode. Mix light and dark so the listing shows both, for example light for the odd numbers
   and dark for the even ones.

4. **Update the README's images,** if they changed:
   - Images with no real art (`play/01-main-menu`, `play/06-settings`,
     `readme/songs-u2-best-of` and `readme/show-the-daily`) are copied to `docs/images/readme/`
     and committed. Git LFS tracks them.
   - Images with real art (`readme/now-playing-u2` and `readme/now-playing-the-daily`) are never
     committed. Drag them into a comment on a GitHub issue or pull request, and paste the
     `https://github.com/user-attachments/assets/…` URLs that GitHub makes into the README's
     `<picture>` blocks.

**Adding a screen.** Add a `@Test` to the `StoreScreenshotTest` of the module that owns the screen,
or create one that extends `StoreScreenshot`, and call `shoot(StoreSet.Play or StoreSet.Readme,
"<name>") { … }` with the screen in its `RolaboxTheme` and `DeviceScreen`. Play cases use only
made-up names and `colorMixCover` art. README cases may use real names and `localArtwork("<file>")`,
with the file added to the table above.

## If the upload key is lost or leaked

The app signing key is Google's, so the app is safe: only the upload key has to be replaced.

1. Make a new upload key, as in step 1 of the setup.
2. Export its certificate:

   ```bash
   keytool -export -rfc -keystore <keystore> -alias <alias> -file <folder outside the repo>/upload_certificate.pem
   ```

3. In Play Console, open the app's App signing page and request an upload key reset, attaching
   `upload_certificate.pem`. The account owner has to make the request. Google takes a few
   days, and until the new key is active, no update can be uploaded.
4. Once it's active, update `~/.gradle/gradle.properties`, both backups, and the upload key's
   fingerprints in Firebase. Delete `upload_certificate.pem`.

A leaked key should be reset right away: until then, whoever has it and the passwords can upload a
bundle that Play accepts.
