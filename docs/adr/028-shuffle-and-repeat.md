# ADR-028: Shuffle and repeat are two settings that the queue follows, in Media3's own modes

- **Status:** Accepted
- **Date:** 2026-11-04
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-11-06:** Shuffle orders only the songs that can be played (rule 5, Decision). A protected song never
  enters the queue, and a song that fails leaves it ([ADR-023](023-playback-engine.md)), so a shuffled queue's order,
  its rounds and its count hold only songs that play. Shuffle Songs over a library of iTunes purchases had counted
  73 protected songs that never played, and skipped them one by one.

## Context

[ADR-018](018-device-navigation.md) decided the queue: playing a row queues its whole list, in the
order it is shown, and starts from that row. ⏮ restarts the item past 3 s and goes back one at or
below it, ⏭ goes forward one, and both do nothing at the ends of the queue (rule 17). It left shuffle
and repeat to the playback ADR, and [ADR-023](023-playback-engine.md) left them to their own
decision (playback/07). This is that decision.

The device is drawn after the iPod, and the epic of real music asks that "Shuffle Songs comes with
shuffle and repeat". The design already has the rows: Shuffle Songs on the main menu, and Shuffle and
Repeat in Settings, with their values on the right.

What the decision has to fit:

- **The queue is Media3's playlist** (ADR-023). The wheel, the media notification, the lock screen and
  the headset's buttons all end up as commands on one session, so each of them already follows the
  order the player keeps. An order kept anywhere else would have to be taught to each of them.
- **Media3 has both modes.** A `Player` has a repeat mode (`OFF`, `ONE`, `ALL`) and a shuffle mode,
  with a `ShuffleOrder` that the app can give. With `REPEAT_MODE_ONE`, ⏮ and ⏭ move as with `OFF`, and
  only the end of an item repeats it. With `REPEAT_MODE_ALL`, they wrap. Media3 keeps one shuffled order
  and doesn't make a new one when it wraps.
- **The device's settings are preferences** ([ADR-011](011-preferences-sync.md), rule 11):
  `:common:userdata` stores them and syncs them, and no `:device` module stores a setting.
- **Listening data stays on the phone** (planning, 2026-10-27). The queue and the position are
  restored after the app is killed, from the playback area's local sub-part, and never synced
  (ADR-023, rule 11). What is stored today is one point, the list's source with an index into it,
  since the list's order could be read again from the library.
- **A song that can't be played is skipped** (ADR-023), and the queue stops when nothing left can be
  played. Until now the queue doesn't wrap, so that search always ends.
- **Libraries are large.** The whole library is one list, Songs, and Shuffle Songs plays all of it.

## Decision

We will make **Shuffle** (Off, Songs, Albums) and **Repeat** (Off, One, All) two of the device's
settings, and the queue will follow them through **Media3's own repeat mode and shuffle order**, with
an order that Rolabox makes. The main menu's **Shuffle Songs** plays the whole library in a random
order, whatever the setting says. The order is listening data, and is stored on the phone with the
point that is restored.

### The two settings

| Setting | Values | Default | Stored as (ADR-011) |
| --- | --- | --- | --- |
| Shuffle | Off, Songs, Albums | Off | `shuffle`: `OFF`, `SONGS`, `ALBUMS` |
| Repeat | Off, One, All | Off | `repeat`: `OFF`, `ONE`, `ALL` |

- **They are preferences,** in `UserData`, stored by `:common:userdata` and synced as fields of
  `users/{uid}/settings/preferences` like Clicker, with their rules tests (ADR-011, rules 6, 7 and 11).
  They are the user's choice, not this installation's, so they sync (ADR-011, rule 10).
- **Each is a row of the device's Settings,** whose value steps in place on center (ADR-018, Full
  screens): Off, Songs, Albums, Off, and Off, One, All, Off.
- **They change only there.** The media notification, the lock screen, a Bluetooth device and any other
  controller of the session are not offered shuffle or repeat (below), so a setting never changes from
  outside the device.
- **Playback reads them itself.** `:device:playback:impl` observes them through `UserDataRepository`,
  so the settings screen only stores them, as it does Clicker, and a value that arrives by sync applies
  as one set on the phone does.

### Shuffle: the order the queue plays in

The **list** is still what ADR-018 says: the list the row was selected from, in the order it is shown.
Shuffle decides the **play order**, the order in which the queue goes through it.

- **Off:** the play order is the list's order, from the selected row, with the rows before it before
  it (ADR-018, rule 17).
- **Songs:** the selected row plays first, and then every other song of the list once, in a random
  order. Every song plays once in a round, and none twice.
- **Albums:** the selected row's album plays first, from that row to the end of the album, and then
  the list's other albums in a random order, each whole and in its own order (disc, then track, as the
  album's list shows it). The songs of the first album that come before the selected row play last, so
  each song still plays once in a round. A list with one album, such as an album's own list, plays in
  its order, from the selected row, as with Off.
- **⏮ goes back through the play order,** which is the order the songs were played in: at or below
  3 s it starts the song that played before this one. ⏭ starts the next one in the play order. The
  ends that ADR-018 names, "the first item" and "the last", are the ends of the play order.

### Repeat: what happens at the ends

- **Off:** after the last song of the play order, the queue ends, and nothing is loaded (ADR-018).
- **One:** when the loaded song ends, it starts again at 0:00, and that is a start of an item (ADR-018,
  rule 10). ⏮ and ⏭ move through the queue as with Off, and do nothing at its ends as with Off: Repeat
  One repeats only what ends on its own.
- **All:** after the last song, the first starts. ⏭ on the last song starts the first, and ⏮ on the
  first song, at or below 3 s, starts the last.
- **All, with Shuffle on:** each round has a new order. When the last song of a round ends, or ⏭ is
  pressed on it, the next round is a new random order of the whole list, by the same Shuffle value, and
  it never starts with the song that just played. ⏮ on the first song of a round goes to the last song
  of that round's order, not to the song that played before it.

### Changing a setting while a queue plays

Both apply at once, and the loaded song is never interrupted: it keeps playing, or stays paused, where
it is.

- **Shuffle turned on, or from one value to the other:** the loaded song becomes the first of a new play
  order, and the rest of the list follows it by the new value, as if the song had just been selected.
  ⏮ at its start then restarts it, since nothing is before it.
- **Shuffle turned off:** the play order is the list's order again, at the loaded song: the song after
  it in the list is next, and the one before it is previous.
- **Repeat:** the next end, or the next ⏮ or ⏭ at an end, follows the new value. The order doesn't
  change.

### Shuffle Songs on the main menu

- **It plays the whole library, every song of every album, in a random order,** starting from a random
  song. It is the list Songs, shuffled as Shuffle Songs would shuffle it, and Now Playing is pushed
  (ADR-018).
- **It doesn't read or change the Shuffle setting.** Its queue is shuffled because of how it was
  started, not because of the setting, so turning Shuffle off while it plays leaves it shuffled, and
  turning it to Albums leaves it by songs.
- **Repeat applies to it** as to any queue. With Repeat All, each round is a new random order of the
  library.
- **The row plays on center.** It isn't a song, so it isn't a playable row in ADR-018's sense, and ⏯ on
  it plays or pauses what is loaded, as on any other row that isn't a song.
- **It is a play of its own in the `:api`,** next to `play(source, from)`: a call that shuffles a source
  and starts from a random item. Its name and signature are playback/08's.

### In the engine

- **The repeat mode is the player's.** The setting maps to `REPEAT_MODE_OFF`, `ONE` or `ALL`. Media3
  already moves ⏮ and ⏭ as with `OFF` under `ONE`, wraps them under `ALL`, and keeps ⏮'s 3 s threshold
  (ADR-023, rule 7), so the wheel and the media buttons share all of it.
- **The shuffle order is the player's, and Rolabox makes it.** With Shuffle on, the player's shuffle
  mode is on, with a `ShuffleOrder` of the `:impl` that follows the rules above, Songs or Albums. The
  playlist itself stays in the list's order. With Shuffle off, shuffle mode is off.
- **The new order of each round is the singleton's.** Media3 wraps a shuffled playlist into the same
  order, so the singleton gives the player a new `ShuffleOrder` when a round ends, before the next
  round's first song starts.
- **A skip follows the play order and the repeat mode.** A song that can't be played is skipped to the
  nearest one in the play order (ADR-023). With Repeat All the search wraps, and it still stops: when
  every song of the queue has failed, nothing is loaded and `unplayableQueue` says so. With Repeat One
  a song that fails is skipped as with Off, never started again.
- **Only songs that can be played are shuffled.** The queue holds no protected song (ADR-023), so the order is made
  of songs that play. A song that fails leaves the queue and its place in the order, and the round goes on without
  it: the songs after it keep their order, and the next round doesn't have it.
- **The state shows the play order.** `PlaybackState.queue` is the list in the order it plays, and
  `queueIndex` is where the loaded song is in it, so Now Playing's "3 of 10" counts what has played.
  The settings' values are in the state too, for Now Playing's icons.
- **No controller but Rolabox's own can set them.** The session takes away
  `COMMAND_SET_SHUFFLE_MODE` and `COMMAND_SET_REPEAT_MODE` from every controller that connects, except
  the singleton's `MediaController`, and adds no shuffle or repeat button to the notification. The
  system's controls show nothing of either.

### Now Playing

- **An icon for Shuffle and one for Repeat show while they are on,** with a different one for Repeat
  One. Shuffle Songs' queue shows the Shuffle icon, since it plays shuffled, whatever the setting says.
- The icons are drawn by the design system ([ADR-015](015-design-system-owns-visual-language.md)) and
  added to `player-designs/device` before playback/08 builds them.

### What is restored

The point that ADR-023 restores after the app is killed gains the order, so a shuffled queue comes
back as it was. It stays on the phone and is never synced (ADR-023, rule 11).

- **A shuffled queue stores its play order,** as the `PlaybackItemId`s of the round it is in, and
  whether it was started by Shuffle Songs. A queue in the list's order stores no order, as now, since
  the library gives it again.
- **The order is stored when it changes,** on a play, a change of Shuffle and a new round, and not
  every few seconds as the position is: the whole library's order is too large to write that often.
- **The point's index is into the play order** when there is one, and into the list's order when there
  isn't.
- **A song that is gone leaves the stored order** as it leaves the queue (ADR-023), and the point gives
  way to the song now at its place, as it does today.
- **An order that can't be read, or that doesn't hold the stored song, is dropped:** the queue comes
  back with a new order by the same rules, from the stored song.
- **The settings are not part of the point.** They are preferences, read as they are when the queue
  comes back. A queue stored shuffled and restored with Shuffle off is in the list's order, at the
  stored song, as if Shuffle had been turned off while it played.

### What was not decided

- **Shuffling playlists, podcasts and audiobooks.** They come later, and are lists like any other, so
  they follow these rules unless their own ADR says otherwise. An episode probably shouldn't be
  shuffled.
- **Shuffle and repeat on the system's controls,** Android Auto's among them. Left out for now (above),
  and decided when a controller outside the app needs them.
- **Weighting the random order,** by ratings or by what was played recently. Every order is uniform.

## Alternatives considered

- **Revise ADR-018 or ADR-023 with these rules instead.** No new record. But ADR-018 is the device's
  navigation and ADR-023 the engine, and this decision crosses both, the settings of ADR-011 and the
  restore: written into either, half of it would be about the other's subject, and each of them already
  leaves shuffle and repeat to a decision of its own.
- **Reorder the playlist in the singleton, and leave Media3's shuffle off.** The order is just the
  playlist, with nothing to keep in step. But changing Shuffle mid-queue would move every media item of
  the playlist, and turning it off would need the list's order kept beside it anyway. Media3's shuffle
  order is made for this, and it is what the session reports to the system.
- **Media3's `DefaultShuffleOrder`.** No code of ours. But it has no Albums, doesn't put the selected
  song first, and doesn't change between rounds.
- **A random seed instead of the stored order.** A number instead of a list. But the order depends on the
  list too, and a song added or gone since makes the same seed give another order.
- **Not keeping the shuffled order after the app is killed.** Nothing new to store. But the restored
  queue would play the songs of this round again, and what comes next would change behind the user.
- **Changing Shuffle applies to the next play only.** The queue never changes under the user. But a
  setting that seems to do nothing until a row is played is harder to understand, and the iPod applied
  it at once.
- **⏮ on the first song under Repeat All restarts it, as with Off.** One meaning for ⏮ on the first
  song. But ⏭ wraps under Repeat All, and ⏮ is its mirror: it is what Media3 and the iPod both do.
- **Repeat One also catches ⏭,** so it restarts the song. Repeat One would mean "this song only". But
  the user would have no way to move on without changing the setting, and the iPod moved on.
- **Shuffle and repeat as buttons on the notification and the lock screen.** Phones show them, and
  Bluetooth devices can set them. But a setting would change from outside the device, and the device's
  Settings is the one place a setting changes. They can be added later, as these same settings.
- **Shuffle Songs turns the Shuffle setting on.** One idea of shuffle instead of two. But the user's
  setting would change because of a row of the main menu, and it would stay on for the next album.
- **The settings on the phone only, not synced.** They would be like listening data. But they are the
  user's choice of how to listen, like Clicker, not what was listened to, and ADR-011 syncs every
  choice.

## Consequences

- The wheel, the media buttons, the notification and the lock screen all follow one order and one
  repeat mode, since all of them are the player's.
- ADR-018's rule 17 now has the play order and the repeat mode in it: its ends are the play order's,
  and they wrap under Repeat All.
- The skip of ADR-023 can wrap, so its search needs to stop on a queue where everything has failed
  even when it wraps.
- `:device:playback:impl` depends on `:common:userdata:api`, to read the settings.
- `PlaybackState.queue` is in play order, so the order the user sees is never the list's when Shuffle
  is on. A screen that wants the list's order reads it from the library.
- The stored point grows with the order of a shuffled queue, up to the whole library for Shuffle Songs,
  and is written when the order changes. The point and the order are two writes, so a kill between them
  can leave an order that doesn't hold the song, which gives a new order.
- Under Repeat All with Shuffle on, ⏮ on the first song of a round goes to the last of that round, not
  to the song played before it, which was in the round before.
- Shuffle and repeat can't be changed from the notification, the lock screen or a car, only from the
  device's Settings.
- A setting synced from another device changes the order of a queue playing here.

## Rules

1. `[convention]` Shuffle (`OFF`, `SONGS`, `ALBUMS`) and Repeat (`OFF`, `ONE`, `ALL`) are device
   settings, stored and synced by `:common:userdata` (ADR-011, rule 11), Off by default.
   `:device:playback:impl` reads them through `UserDataRepository`.
2. `[convention]` The list is ADR-018's. Shuffle decides the play order: Songs plays the selected row
   first and every other song once in a random order, and Albums plays the selected row's album from
   that row, then the other albums whole in a random order, then the first album's songs before the row.
3. `[convention]` ⏮ and ⏭ move through the play order, and its ends are ADR-018's first and last.
   Repeat One starts the song again only when it ends. Repeat All wraps at both ends, and with Shuffle
   on each round has a new order that doesn't start with the song that just played.
4. `[convention]` A change of Shuffle or Repeat applies to the queue that plays, at once, without
   interrupting the loaded song, except that a queue started by Shuffle Songs stays shuffled by songs.
5. `[convention]` Shuffle Songs plays every song of the library that can be played (no protected song, ADR-023) in a
   random order from a random one of them, and doesn't read or change the Shuffle setting. Repeat applies to it.
6. `[convention]` The repeat mode and the shuffle order are the player's: Media3's repeat mode, and a
   `ShuffleOrder` of `:device:playback:impl` with the player's shuffle mode on. The playlist stays in
   the list's order.
7. `[convention]` `PlaybackState.queue` and `queueIndex` are in play order, and the state says whether
   the queue is shuffled and which repeat mode applies.
8. `[convention]` Only Rolabox's own `MediaController` may set the shuffle and repeat modes. The session
   offers them to no other controller, and the notification has no button for them.
9. `[convention]` A shuffled queue's play order is stored with the restored point in
   `:device:playback:local`, on the phone, and is not synced (ADR-023, rule 11).

**Conformance.** All the rules are reviewed: they are behavior, with no syntax for a build check to
read. playback/08 covers them with unit tests: rules 2 to 7 and 9 in `:device:playback:impl`, with a
real `ExoPlayer` on the JVM as ADR-023 tests it and a random source the tests fix; rule 8 by a test
that connects a second controller to the session and finds the commands missing; rule 1 by the
ViewModel tests of `:device:settings:impl` and the rules tests of the two new fields.
