# ADR-026: Artwork is a reference in the library, loaded from MediaStore's thumbnail of the song, with no image library

- **Status:** Accepted
- **Date:** 2026-11-03
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Now Playing draws `DeviceCoverArtPlaceholder` for every song, and the media notification and the
lock screen show no picture. playback/06 shows the real artwork, which ADR-023 left out ("What was
not decided") and [ADR-024](024-library-index.md) left to it: the index records whether a file has
an embedded picture, and "how it is loaded and shown is playback/06's".

Artwork comes from two places. The file's own tags hold a picture: ID3's `APIC` frame in an MP3, the
`covr` item in an M4A. And many collections keep one image per album next to the files, as
`cover.jpg` or `folder.jpg`. ADR-024 said that image needs the images permission. On API 33 and up
that is `READ_MEDIA_IMAGES`, a second prompt, which Google Play allows only to apps that need the
user's photos as a core feature. A music player doesn't.

The system already finds both. `ContentResolver.loadThumbnail` on an audio item's `content://` URI
returns MediaStore's thumbnail of it, which the media provider makes: the embedded picture, read
with `MediaMetadataRetriever`, and when there is none, the best `.jpg` or `.png` in the file's
folder. The provider reads the folder itself, so the audio permission is all the app needs.

Images are new to the app, and what crosses an `:api` can't be one: an `:api` is a JVM module
([ADR-020](020-modules-by-product-area.md), rule 6), the playback state has no URI
([ADR-023](023-playback-engine.md), rule 4), and every layer is reached through an abstraction
([ADR-021](021-data-flow-through-layers.md)). Three places show the artwork: Now Playing, the
notification and the lock screen, and the last two are drawn by Media3 from a `Bitmap`. One image
is on screen at a time, since artwork in the lists is out of this phase.

## Decision

### Where the artwork is: a reference, not an image

- **`:device:library:api` gains `ArtworkRef`,** which names the song whose picture it is. It holds
  no image and no URI. `Album` gains `artwork: ArtworkRef`, and `PlaybackItem` gains
  `artwork: ArtworkRef?`, `null` for a kind of item that can't have one. A song's reference is its
  own.
- **An album's reference is one of its songs:** the first, in track order, whose tags have a
  picture, or the first song when none has. The image in the folder is the same for all of them.
  The index picks it in the album's query, so no table changes.
- **Whether there is artwork is known only once it loads.** The index knows about the embedded
  picture, not about the folder. So every song has a reference, and "none" is the answer of a load.

### Where it comes from: MediaStore's thumbnail of the song

- **`:device:library:local:api` gains `ArtworkLocalDataSource`,** which `:device:library:local:impl`
  implements with `ContentResolver.loadThumbnail` on the song's URI, at one size, large enough for
  Now Playing's cover on a phone and for the notification.
- **The order is the system's:** the picture embedded in the tags; then the best `.jpg` or `.png`
  in the song's folder, where the system prefers names with `albumart` and then any `.jpg`, which
  `cover.jpg` and `folder.jpg` are; then none. The system doesn't look in `Download` or in a
  top-level folder. Rolabox doesn't re-rank what it returns.
- **No image permission is asked for.** ADR-024's line that `cover.jpg` needs it is revised.
- **A load that finds nothing is not an error.** The provider throws `IOException` when there is no
  artwork, and when the file is gone, and both are "none": a file that is gone has no cover either.
  A song no longer in the index has none. A file the system won't open, for want of the permission,
  is `LibraryError.FileUnreadable`.

### How it is loaded: in the library, with no image library

- **`:device:library:api` gains `ArtworkRepository`,** with one `suspend` call
  ([ADR-006](006-async-api-shape.md)) that takes an `ArtworkRef` and returns
  `Either<LibraryError, ArtworkImage?>`, `null` for none. `ArtworkImage` is the width, the height
  and the ARGB pixels, so the `:api` stays JVM. The fake is in `:device:library:testing`.
- **`:device:library:impl` keeps the last few images in memory,** in an LRU of a few entries: the
  song playing and the ones around it. An entry is for one version of the file, so a file that
  changed loads again.
- **Each consumer turns the pixels into its own type:** Compose's `ImageBitmap` on Now Playing, a
  `Bitmap` for Media3.

### Who shows it

- **Now Playing's ViewModel injects `ArtworkRepository`,** and loads the artwork of the item playing
  when the item changes. Its UI state holds the image, `null` while it loads and when there is none.
  A change of song clears it first, so the old cover never stands for the new song.
- **The design system draws it:** `DeviceCoverArt` takes the image, and draws it cropped to a square
  in the frame the placeholder already has, or the placeholder when there is no image
  ([ADR-015](015-design-system-owns-visual-language.md)). Its size comes from the screen as before,
  so at large font scales the cover still shrinks last ([ADR-018](018-device-navigation.md), Large
  text).
- **The notification and the lock screen ask the same repository.** `:device:playback:impl` sets each
  item's `MediaMetadata.artworkUri` to a Rolabox URI that names the song, and gives the session a
  `BitmapLoader` that reads that URI through `ArtworkRepository`. Media3 hands the platform the
  `Bitmap` it loaded, and the platform's session also gets the URI, which only Rolabox can read: with
  no artwork, the system's media controls try it, log a warning and show their own background.

## Alternatives considered

- **Rolabox reads the folder itself, with `READ_MEDIA_IMAGES`.** Rolabox would decide the names and
  the order, and the tests could check them. But it is a second permission prompt, Play's policy
  doesn't allow it for a music player, and the device's permission row (library/04) would grow a
  second case.
- **Embedded pictures only, read by Rolabox's own parser.** The parser already finds `APIC` and
  `covr`, and could keep their bytes instead of skipping them. Everything would be tested against
  the fixtures. But the albums that only have a `cover.jpg` get no artwork, which the ticket asks
  for.
- **Coil.** The usual image library for Compose: caches in memory and on disk, decodes at the size of
  the view, follows the lifecycle. But it is a new dependency for one image at a time, the
  notification still needs a Media3 `BitmapLoader`, and loading the thumbnail would be a custom
  fetcher that has to live in `:local:impl` anyway, behind an `:api` that can't name Coil's types.
  It is the first option to look at again when the lists show artwork.
- **Pass a `content://` URI of the image to the screens.** Coil or the notification could load it as
  it is. But the URI is the song's audio URI, which ADR-023's rule 4 keeps out of the playback
  state, and the screens would reach the system without the library in between.
- **Store the artwork in the index.** A table of thumbnails, or a flag for "has a folder image" from
  a load at scan time. The lists would know ahead which songs have none. But it is a decode for every
  song during a scan, for a list that doesn't show artwork yet, and the provider already keeps its
  thumbnails.

## Consequences

- The artwork of `cover.jpg` and `folder.jpg` works with the permission the app already has.
- The order isn't Rolabox's. When a folder has both `cover.jpg` and `folder.jpg`, the system picks.
  A folder named `Download`, or the top of the storage, gives nothing. A manufacturer's media
  provider could behave differently, so playback/06 checks it by hand on API 29 and on API 36 with
  the fixtures that `pushAudioFixtures` puts on the device and an album folder with a `cover.jpg`.
- Unit tests can't run the provider: Robolectric doesn't make thumbnails. They cover Rolabox's part:
  the album's choice of song, the cache, "none" and the errors, against library/02's fixtures and a
  fake data source.
- The image is no sharper than the system's thumbnail. That is enough for a phone's display.
- Every song has a reference, so the lists can't hide the artwork of songs that have none without a
  load. That is for the ticket that adds artwork to the lists.
- Pixels cross the `:api` as an `IntArray`, a copy of each image. An image of the size Now Playing
  needs is a few megabytes, and the cache holds a few.

## Rules

1. `[convention]` Artwork crosses an `:api` as an `ArtworkRef`, and as pixels only from
   `ArtworkRepository`. No model of the library or of playback holds an image, or the URI of one.
2. `[convention]` Artwork comes from MediaStore's thumbnail of the song: the picture embedded in its
   tags, then an image in its folder, then none. Rolabox asks for no image permission.
3. `[enforced]` Only `:device:library:local:impl` calls `loadThumbnail`.
4. `[enforced]` No image-loading library is a dependency: Coil (`io.coil-kt`, `io.coil-kt.coil3`), Glide
   (`com.github.bumptech.glide`), Picasso (`com.squareup.picasso`) or Fresco (`com.facebook.fresco`).
5. `[convention]` The design system draws the artwork and its placeholder, in one frame. Artwork that
   is loading or missing is the placeholder.
6. `[convention]` Now Playing, the notification and the lock screen get the artwork through
   `ArtworkRepository`, so they show the same image.

**Conformance.** Rule 3 is the detekt rule `MediaStoreOutsideLibrary` in `build-logic/detekt-rules`,
which also reports a call of `loadThumbnail`, with a violating and a passing case, and runs everywhere
but `:device:library:local:impl` as ADR-024's rule 2 does. Rule 4 is checked by `ModuleRules.kt` when the
build is configured, on those groups, with a violating and a passing case in `ModuleRulesTest`. The
rest are reviewed. Rule 2 is the provider's behavior, checked by hand (Consequences), and the album's
choice of song by the tests of `:device:library:impl`. Rules 5 and 6 are covered by the goldens of
`DeviceCoverArt` and Now Playing, and by `NowPlayingViewModelTest`.
