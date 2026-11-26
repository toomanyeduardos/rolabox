# ADR-045: The stored queue is loaded when the app comes to the front, not when its process starts

- **Status:** Accepted
- **Date:** 2026-11-25
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-023](023-playback-engine.md) (in part: "It is restored at each start of the process" and "A
  restored queue is prepared … also after a process started by background work" in Permissions and the service's
  lifetime, and the matching line in Consequences)

## Context

[ADR-023](023-playback-engine.md) restores the stored queue, paused and prepared, at each start of the process and
each time the app comes back. It accepted the cost in its Consequences: "A restored queue shows a paused media
notification after a reboot or background work, with the app never opened."

In the field that cost reads as a bug ([Trello #171](https://trello.com/c/ECRAousy)). On a Samsung S24 with
Android 17, hours after Rolabox was dismissed from Recents, its media notification came up with the last song,
paused. Nothing was playing, and it went away later on its own. The user had done nothing to start it.

It was reproduced on an emulator with no code changes. The steps: play a song, pause it, dismiss the app from
Recents, kill the process, then reboot. After the reboot the process started "for service `PlaybackService`" while
the Bluetooth stack's AVRCP scan of browsable players was running. That scan binds every app that declares a
`MediaBrowserService`. Playback's process start task then loaded the queue, and Media3 posted the notification for the
paused player. The app's screen was never opened.

That scan is only one of the ways the process can start in the background. Others are System UI's media
resumption, which also binds the exported service, WorkManager's jobs (the follow check, covers, episode
downloads), a media button, and anything else that binds the service. The user starts none of these, so the
notification shows up at random. And it looks like the app is running when it isn't.

## Decision

We will load the stored queue **only when the app comes to the front**, at the process lifecycle's start, and
**not at the start of the process**.

- **A process started in the background leaves the player empty.** Media3 posts no notification for a player with
  nothing loaded, so a bind, a job or a receiver shows nothing.
- **The task stays a `ProcessStartTask`** ([ADR-025](025-process-start-tasks.md)). At process start it only
  starts observing the process lifecycle, which has to be done on the main thread. It loads on each start of that
  lifecycle and stores on each stop, as before.
- **A play with nothing loaded is unchanged.** When the system's controls or a media button ask to play after the
  app was killed, Media3's `onPlaybackResumption` reads the stored point, as ADR-023 already decided.
- **Everything else in ADR-023's restore holds:** what is stored and when, a stored song that is gone, what the user
  played in the meantime staying, and a restored queue being prepared once it is loaded.

## Alternatives considered

- **Keep restoring at process start.** The paused song is in the shade and on the lock screen after a reboot,
  ready to resume with one press. But it comes up at random times, looks like the app is running, and was
  reported as a bug.
- **Restore at process start, but hide the notification until the app is opened.** Keeps the player ready. But
  Media3's service posts the notification for any loaded player, so hiding it means overriding Media3's own
  notification updates. And Bluetooth and the lock screen would still see a loaded session the user didn't ask for.
- **Stop exporting the `MediaBrowserService` filter.** Stops the Bluetooth and System UI binds. But WorkManager's
  jobs and media buttons still start the process. It would also break the system's media resumption, which ADR-023
  relies on to resume after the app was killed.

## Consequences

- No media notification appears unless the user opened the app or pressed play.
- After a reboot, the paused song isn't in the shade or on the lock screen until the app is opened. A media button
  or the system's controls still resume it.
- A process started in the background no longer opens the stored point at start. It is read when the app comes to
  the front, or when a play asks for it.

## Rules

1. `[convention]` The stored queue is loaded into the player when the process lifecycle starts, that is when the
   app comes to the front, and never at the start of the process. This replaces "It is restored at each start of
   the process" in ADR-023.
2. `[convention]` With nothing loaded, a play from the system's controls or a media button gets the stored point
   through Media3's `onPlaybackResumption` (unchanged from ADR-023).

**Conformance.** Rule 1 is covered by `ResumeWhereYouLeftOffTest`. The process start alone loads nothing, and the
lifecycle's start loads the stored point. Rule 2 is covered by the existing resumption tests.
