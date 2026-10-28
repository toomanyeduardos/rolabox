# ADR-023: Playback with Media3, in a session service that the playback area owns

- **Status:** Accepted
- **Date:** 2026-10-28
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Nothing plays. The device browses an in-memory sample library, and `PlaybackController` and
`PlaybackState` are implemented by `InMemoryPlayer`, a clock in place of the audio
([ADR-018](018-device-navigation.md), Playback state). ADR-018 and
[ADR-020](020-modules-by-product-area.md) both leave "the engine, its service and the media
session" to a playback ADR, and every ticket of the phase that plays real files
([Real music](https://trello.com/c/5rwZH0WU)) needs it. This is that ADR.

What the decision has to fit:

- **The files are the phone's own music,** found through MediaStore and kept in Rolabox's own index
  (library/01). Rolabox never copies them, and no music ships in the app.
- **Playback outlives the screens and the app's window.** Music goes on with the screen off and the
  app in the background, with the media notification, the lock screen, and headset and Bluetooth
  buttons. On Android that takes a foreground service of type `mediaPlayback` and a media session.
- **The device already defines the behavior.** ADR-018's control table, rule 10 (playing and paused,
  and each start of an item) and rule 17 (the queue, and ⏮'s 3 s restart) are written against
  `PlaybackState` and `PlaybackController`, and the screens and their tests use only those
  interfaces. The engine has to meet them, not change them.
- **A foreground service is new in the module layout.** ADR-020 gives `:app` no feature code and
  only `:app` depends on an `:impl`. A service is declared in a manifest, and that has to be placed.
- **Podcasts come later and reuse the player.** An episode keeps its own position, skips by seconds,
  plays at another speed, and is either streamed or a downloaded file. Songs are the only kind now.
- **After the app is killed, the queue and the position come back, paused** (an epic goal,
  playback/05). Listening data stays on the phone and is not synced.
- **ADR-018 already says the volume is the system's media volume.** `InMemoryPlayer` keeps a volume
  of its own from 0 to 100, and the wheel changes it by 1 per step.

**Target state.** This ADR is ahead of the code. Rule 9 is `[enforced]` from now on, and holds
trivially until playback/02 adds Media3. playback/02 builds the player, the service and the state
mapping, playback/03 the background behavior, playback/04 the unplayable files and playback/05 the
restore. Until playback/02, `InMemoryPlayer` is what ADR-018 describes.

## Decision

We will play with **Media3**: an `ExoPlayer` inside a `MediaSessionService`, both in
`:device:playback:impl`. `PlaybackController` and `PlaybackState` stay the only way the device
reaches playback, and their implementation talks to the session through a Media3
`MediaController`.

### The engine is Media3

ExoPlayer plays the queue, and Media3's session library gives the media session, the notification,
the lock screen, the media buttons and the foreground service from one `Player`. That is most of
what the phase asks for, maintained by the platform's own team, and it plays MP3, M4A/AAC, FLAC,
OGG/Opus and WAV from a `content://` URI, a file or a stream. The alternatives, and why they lost,
are under Alternatives considered.

### Where it lives

```
:device:playback:api        PlaybackState, PlaybackController, the playable item, PlaybackError
:device:playback:impl       the MediaSessionService with its ExoPlayer and MediaSession, the
                            implementation of the two interfaces, the mapping to and from Media3,
                            and the manifest that declares the service and its permissions
:device:playback:testing    FakePlayback, for tests and previews
```

- **The service is the playback area's.** `:device:playback:impl` declares it, with
  `foregroundServiceType="mediaPlayback"` and the permissions it needs, in its own
  `AndroidManifest.xml`, which the build merges into the app's. `:app` already lists the `:impl`,
  and adds nothing: no manifest entry, no class, no dependency. ADR-020's rules 4, 5 and 13 hold as
  they are written, and there is no new module.
- **The service is a Hilt entry point** (`@AndroidEntryPoint`, [ADR-005](005-hilt-dependency-injection.md)).
  It builds the player and the session in `onCreate` and releases both in `onDestroy`.
- **No Media3 type crosses the `:api`.** The `:api` stays a JVM module (ADR-020, rule 6). Media3's
  states, items and exceptions are mapped at the `:impl`'s boundary
  ([ADR-021](021-data-flow-through-layers.md)), as an SDK's types are under
  [ADR-022](022-data-source-sub-parts.md).

### The service, the session and the controller

```
wheel → :device:ui:impl, Now Playing → PlaybackController ─┐
                                                           ├→ MediaController → MediaSession → ExoPlayer
notification, lock screen, headset, Bluetooth ─────────────┘        (in the MediaSessionService)
```

- **The service owns the player and the session.** Nothing else holds the `ExoPlayer`.
- **`PlaybackController` and `PlaybackState` are one `@Singleton` in the `:impl`, as now,** and it
  holds a `MediaController` connected to the session. Every action is a command on that controller,
  and the state is built from its events. Connecting starts the service, and the actions wait for the
  connection.
- **So every way in reaches the same `Player`.** The wheel, the notification, the lock screen and the
  media buttons all end up as the same commands on one session, and none of them has a path of its
  own. ⏮ on a Bluetooth headset restarts the song past 3 s exactly as the wheel's ⏮ does, because the
  threshold is the player's (below).
- **The queue is Media3's playlist.** `play` resolves the list through the library and gives the
  whole queue to the player, so the next song is prepared before the current one ends and albums play
  without gaps (MP3 with its encoder's gapless header, and AAC).
- **The singleton keeps what Media3 doesn't:** the `PlaybackItem`s of the queue, the count of starts,
  and the hold of ⏮ or ⏭. It runs in the application scope, since playback outlives the screens
  ([ADR-006](006-async-api-shape.md), rule 6). Media3 is called on the main thread, as it requires,
  and nothing blocks.

### The playable item

The queue is made of **playable items**, and a song is one kind. Songs are the only kind this phase
ships.

```kotlin
sealed interface PlaybackItemId {
    data class Song(val id: SongId) : PlaybackItemId
    // Episode(val id: EpisodeId) comes with podcasts.
}

data class PlaybackItem(
    val id: PlaybackItemId,
    val title: String,
    val artist: String,      // for an episode, its show
    val album: String,       // for an episode, its show again, or empty
    val duration: Duration,
)
```

- **`PlaybackItem` replaces `PlaybackSong`,** and `currentSong` becomes `currentItem`.
  `play(source, from: SongId)` becomes `play(source, from: PlaybackItemId)`, and `PlaybackSource`
  gains a source for each new kind, as an episode's show. playback/02 renames them.
- **The item is what Now Playing shows.** Where its audio is doesn't belong to the `:api`, which
  screens see.
- **The audio's location comes from the data part that owns the item, as a URI.** For a song, the
  library's `:api` gives each `Song` a URI: a `content://` URI from MediaStore. Playback hands it to
  Media3 and never queries MediaStore, the index or the file system itself, and doesn't need the
  storage permission. A file the user picks through a folder later is also a `content://` URI, and an
  episode is an `https://` URL or a downloaded file, with no change to playback.
- **What podcasts add is designed in now and built with them:** a speed (Media3's playback
  parameters), skipping by seconds (`seekTo` from the position), and a position kept per item, read
  when the item starts and stored as it plays. A song starts at 0:00, so it has no stored position
  (the player the device is drawn after remembered one only for podcasts and audiobooks).

### From the engine's state to `PlaybackState`

| `PlaybackState` | From Media3 |
| --- | --- |
| `queue`, `queueIndex`, `currentItem` | The singleton's list of items, and the player's current media item index |
| `isPlaying` | `playWhenReady`. A song that is buffering is still playing, and nothing loaded is `false` |
| `position` | `currentPosition`, read on the clock below |
| `startCount` | Counted by the singleton on each `onMediaItemTransition`, and on a restart by ⏮ (a seek to 0:00 of the same item) |
| `volume` | The device volume, as a percentage of its maximum |
| nothing loaded | The player is idle with no media items. When the queue ends (`STATE_ENDED`), the singleton clears the items, so the queue ends as ADR-018 says |

- **The clock of 39.02 stays, and reads the engine instead of counting.** Media3 doesn't push its
  position, so while an item plays or is being sought the singleton reads `currentPosition` every
  `Tick` (250 ms). The observable contract of `position` doesn't change, and nothing ticks while
  paused.
- **⏮ is Media3's `seekToPrevious`, with `maxSeekToPreviousPosition` set to 3 s.** It goes to the
  previous item at or below 3 s, restarts it past 3 s, and restarts the first item at any position,
  which is ADR-018's rule 17. The threshold stays the playback area's, in the player's builder.
- **⏭ is `seekToNext`,** which does nothing on the last item, as rule 17 says.
- **A hold of ⏮ or ⏭ is the singleton's,** since Media3 has no scan: it seeks the player every
  `Tick` by `SEEK_SPEED` times the time that passed, stops at 0:00 and at the duration, and never
  passes the end of the item, as ADR-018 says. The player stays as it was, so a playing item plays on
  from where the hold left it.
- **Errors follow [ADR-007](007-error-handling.md).** An action that can fail returns
  `Either<PlaybackError, …>`, as `play` does now. A failure the engine reports while it plays, which
  no call returned (a file that is gone or can't be decoded), is a `PlaybackError` that the state
  reports as it happens, and the singleton maps Media3's `PlaybackException` codes to it.
  `PlaybackException` never leaves the `:impl`. playback/04 builds this: the cases, skipping to the
  next item, and how the state reports them.

### Volume is the phone's media volume

ADR-018 decided it: there is one volume, as on the player the device is drawn after, and it is the
phone's music stream.

- **The wheel and the phone's volume keys move the same value,** and Now Playing's bar follows both.
- **One step of the wheel is one step of the phone's volume.** Phones have 15 to 25 steps, so a
  step on a 0 to 100 scale would often not move the volume at all. `setVolume(level)` becomes
  `changeVolume(steps)`, which moves it by whole steps and stops at the ends. `volume` stays a
  percentage from 0 to 100, for the bar. playback/02 changes the `:api` and Now Playing.
- **It's done through Media3's device volume** (`increaseDeviceVolume`, `decreaseDeviceVolume` and
  `deviceVolume`), without the flag that shows the system's panel: the device draws its own bar.
- **The player's own volume stays at full.** It is never a second volume.

### Audio focus, noise and buttons

- **The player handles audio focus** with audio attributes for music. A call or another app taking
  focus for a while pauses it, and it resumes when the focus returns. Another app taking focus for
  good pauses it, and nothing resumes it. Short sounds, as navigation prompts, duck it.
- **Headphones unplugged or a Bluetooth device disconnected pauses** (Media3's
  `handleAudioBecomingNoisy`), and nothing resumes it when they come back.
- **Headset and Bluetooth buttons** reach the session, and so the same commands the wheel uses: play
  and pause, next, previous.

### Permissions and the service's lifetime

- **`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`** are declared by
  `:device:playback:impl`, with the service.
- **`POST_NOTIFICATIONS` is never asked for.** From Android 13 on, notifications that belong to a
  media session are exempt from it, so the media notification shows without it, and playback works
  either way. Asking would be a prompt that changes nothing the user can see.
- **The storage permission is the library's,** since playback only opens the URIs the library gives.
- **The service runs while there is something to play.** Media3 makes it a foreground service while
  the player plays, with the media notification. When the app is dismissed from recents, a playing
  item plays on, and a paused one, or nothing loaded, stops the service and its notification.
- **What playback/05 restores is the playback area's own data,** stored on the phone and never
  synced: the queue's source, the current item and its position. It is a local sub-part of playback,
  `:device:playback:local:{api,impl}` (ADR-022), on `:common:storage`. A restored queue is loaded
  paused, and nothing starts on its own. Media3's playback resumption (`onPlaybackResumption`) reads
  the same data when the system's controls or a Bluetooth button ask to play after the app was
  killed.

### Fakes and tests

- **`FakePlayback` stays in `:device:playback:testing`,** for the screens' tests and their previews.
  It implements the same two interfaces, so it follows each change of the `:api`.
- **`InMemoryPlayer` is deleted** when playback/02 binds the real implementation. Its tests become
  the real player's, since the behavior they describe is the same.
- **The real player is tested on the JVM, with Robolectric and Media3's test utilities.** A real
  `ExoPlayer` with Media3's fake clock plays the generated tones of library/02, from test sources
  only, and the tests check the state mapping, every action, the restart threshold, the holds and
  the end of the queue. Robolectric is added to the stack for these tests.
- **The service is tested on a device:** an instrumented test in `:device:playback:impl` connects a
  `MediaController` to it and checks that it plays, goes to the foreground and stops. The background,
  the buttons and audio focus are checked by hand on a device (playback/03).

### What was not decided

- **Shuffle and repeat,** which ADR-018 left here. playback/07 decides them.
- **Android Auto, Wear OS and casting.** A `MediaLibraryService` would be needed for Android Auto's
  browsing, and it is out of this phase.
- **Artwork** in the notification and on Now Playing (playback/06).
- **Where a podcast episode's position is stored,** once the library's database exists (library/01).

## Alternatives considered

- **Android's `MediaPlayer`.** In the platform, with no dependency. But the queue, gapless playback,
  the media session, the notification and the foreground service would all be written by hand around
  it, its errors are integer codes, and it can't be tested on the JVM: Robolectric's shadow plays
  nothing.
- **ExoPlayer with the legacy `MediaSessionCompat` and `MediaBrowserServiceCompat`.** What most apps
  used until Media3. But the legacy session is deprecated in favor of Media3's, and the notification
  and the connector between the two would be ours to keep.
- **A native engine** (FFmpeg, or VLC for Android). More formats. But a large native library for
  formats the phase doesn't test, licensing to manage, and the session and service still to write.
- **The service in `:app`.** Where Android's templates put it, and next to the app's manifest. But
  `:app` would hold feature code and depend on Media3, against ADR-020's rule 13, and the service
  would be away from the player it serves.
- **A playback module of its own for the service,** as `:device:playback:service`. The service would
  be apart from the state. But it shares the player with the implementation of the state, and would
  need a way into `:impl`'s internals: it would be a new kind of module for no boundary a reader can
  use.
- **One `ExoPlayer` singleton in Hilt, used by both the service and `PlaybackController`, without a
  `MediaController`.** Synchronous, with no connection to wait for. But the wheel would reach the
  player directly and the media buttons through the session, two paths that can drift apart, and the
  player's life would be the process's, not the service's.
- **The player's own volume for the wheel.** The wheel would move smoothly from 0 to 100. But the
  phone's keys would move another volume, two volumes would multiply, and a quiet song at full wheel
  could not be told from a quiet phone. The player the device is drawn after had one volume.
- **`setVolume(level)` on the phone's volume, unchanged.** No change to the `:api`. But with 15 steps
  a wheel step of 1 rounds back to where it was, and the wheel would seem dead.
- **Ask for `POST_NOTIFICATIONS` on the first play.** What playback/03 expected. But media
  notifications are exempt, so the prompt would change nothing.
- **Playback finds the file itself, from a `SongId`.** One field fewer in the library. But two parts
  would know MediaStore and need its permission, and a folder picked later or a podcast would change
  playback.
- **Keep `PlaybackSong` and add an episode type later.** No rename now. But `play(from: SongId)` and
  `currentSong` would be renamed then anyway, with more code on top of them.

## Consequences

- Real playback, the notification, the lock screen and the media buttons come from one library and
  one `Player`, and every way in behaves the same.
- `:app` doesn't change, and the playback area holds everything about playing: the service, the
  manifest entries, the engine and its mapping.
- The playback `:impl` is no longer a plain class with a clock: it connects to a service, and its
  actions are asynchronous until the connection is made. Its tests need Robolectric and run slower.
- Robolectric and Media3 are new in the stack. Media3 adds to the APK's size.
- The `:api` changes in playback/02: `PlaybackItem`, `currentItem`, `play(from: PlaybackItemId)` and
  `changeVolume(steps)`. Now Playing, the device's handlers and `FakePlayback` follow it.
- The library's `Song` gains a URI before the library's index exists. library/01 has to keep it, and
  playback/02's debug stand-in library gives the URIs of the tones on the device.
- The volume moves in the phone's steps, coarser than the 0 to 100 of the bar, and different from
  phone to phone.
- No notification permission prompt, ever, for playback.
- The engine's failures while playing arrive as state, not as the result of a call, so the device
  needs a way to show them that isn't a toast after an action (playback/04).

## Rules

1. `[convention]` Playback uses Media3: an `ExoPlayer` and a `MediaSession`, in a
   `MediaSessionService`.
2. `[convention]` The service, its manifest entries and its permissions are in
   `:device:playback:impl`. `:app` declares nothing for playback.
3. `[convention]` `PlaybackController` and `PlaybackState` are the only way the rest of the app
   reaches playback, and their implementation reaches the player only through a `MediaController`
   connected to the session. Only the service holds the `ExoPlayer`.
4. `[convention]` The queue is made of `PlaybackItem`s, identified by a `PlaybackItemId` with one
   case per kind of item. The `:api` has no URI, path or Media3 type.
5. `[convention]` The location of an item's audio is a URI given by the `:api` of the data part that
   owns the item. Playback never queries MediaStore or the file system to find it.
6. `[convention]` The volume is the phone's media volume, changed in its own steps, and shown as a
   percentage. The player's own volume stays at full.
7. `[convention]` ⏮'s restart threshold is the player's `maxSeekToPreviousPosition`, so the wheel
   and the media buttons share it (ADR-018, rule 17).
8. `[convention]` `POST_NOTIFICATIONS` is not requested for playback.
9. `[enforced]` Media3 (`androidx.media3`) is a dependency of `:device:playback:impl` only.
10. `[convention]` The engine's errors are mapped to `PlaybackError` in the `:impl`, and
    `PlaybackException` never leaves it (ADR-007).
11. `[convention]` What playback stores to restore after the app is killed is in
    `:device:playback:local`, on the phone, and is not synced.

**Conformance.** Rule 9 is checked by `ModuleRules.kt` when the build is configured, on the
`androidx.media3` group, with a violating and a passing case in `ModuleRulesTest` in `build-logic`.
Its message names ADR-023 rule 9. Since only that module has Media3 on its classpath, nothing else can
import it, and the `:api` can't name a Media3 type. The rest are reviewed. Rules 3, 6, 7 and 10 are
behavior, covered by the real player's tests in `:device:playback:impl` (playback/02 and
playback/04), and rule 2 by the instrumented test of the service.
