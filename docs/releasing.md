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
   it, so Play Console deobfuscates crashes. If the build printed "This release is signed with the
   debug key", the signing values aren't set: don't upload it.
4. **Check the signature:**

   ```bash
   jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
   ```

   It should say `jar verified.` and be signed by your upload key's name, not `CN=Android Debug`.
5. **Smoke-test** a minified build on a device (launch/15).
6. **Upload the `.aab`** to the Play Console track. Never upload an APK.

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
