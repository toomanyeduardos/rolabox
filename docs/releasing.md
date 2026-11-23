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

When the app is created in Play Console, keep the default: **let Google generate and manage the app
signing key**. Don't upload one. The first bundle you upload registers your upload key.

### 5. Register the key fingerprints in Firebase (with launch/02)

Sign in with Google checks the SHA-1 of the key that signed the installed APK. In the Firebase
project of the Play build, add the SHA-1 and SHA-256 of:

- **The app signing key:** copy them from Play Console, under the app's App signing page.
- **The upload key,** so a locally built signed release can sign in during the smoke test:

```bash
keytool -list -v -keystore <keystore> -alias <alias> | grep -E "SHA1:|SHA256:"
```

They go in Firebase console → Project settings → the Android app → **Add fingerprint**. The
fingerprints are public, not secrets. After adding them, download `google-services.json` again and
replace `app/google-services.json`: each fingerprint adds an Android OAuth client to it.

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
2. **Commit and tag it** `v<versionName>`, for example `v1.0.0`.
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
