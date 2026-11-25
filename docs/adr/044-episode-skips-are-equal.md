# ADR-044: During an episode, ⏮ and ⏭ both skip 15 s

- **Status:** Accepted
- **Date:** 2026-11-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-031](031-podcasts.md) (in part: ⏭'s 30 s in rule 11, "Episodes in playback"
  and "Context"), and [ADR-018](018-device-navigation.md) (in part: ⏭'s 30 s for an episode in rule 17)

## Context

[ADR-031](031-podcasts.md) made ⏮ and ⏭ skip by seconds while an episode is loaded: back 15 s and
forward 30 s, with Media3's `seekBack` and `seekForward`, whose increments are set in the player's
builder. Every way in does the same, since the session's player turns previous and next into those
skips (rule 11). [ADR-018](018-device-navigation.md)'s rule 17 repeats the two amounts.

15 s back and 30 s forward is what many podcast apps do: forward skips an ad or a segment, and
back replays a missed sentence. In use, it was reported as a bug: the same wheel, pressed on
opposite sides, moves by different amounts, so ⏭ then ⏮ doesn't come back to where it was, and
the device gives no hint that the two differ. Rolabox has one wheel and no labels on its buttons,
so the user can't see the amounts as they can on an app's "15" and "30" icons.

## Decision

We will skip **15 s both ways** while an episode is loaded: ⏮ goes back 15 s and ⏭ goes forward
15 s.

- **How:** unchanged. Media3's seek-forward increment is set to 15 s in the player's builder, next
  to the seek-back increment, and the playback area keeps both in one place (`EpisodeSkips.kt`).
- **The end of an episode:** ⏭ in its last 15 s goes to the end, and so ends it, as ⏭ in the last
  30 s did. Media3 stops a seek at the duration.
- **Everything else in ADR-031's rule 11 holds:** an episode is queued alone, it is never shuffled
  or repeated, a hold scrubs as for a song, and the notification, the lock screen and a headset
  skip by the same 15 s as the wheel.

## Alternatives considered

- **Keep 15 s back and 30 s forward.** Common in podcast apps, and a long forward skip gets past
  an ad in fewer presses. But on a device with one unlabeled wheel the difference reads as a bug,
  which is how it was reported.
- **30 s both ways.** Fewer presses to skip ahead, but a missed sentence takes 30 s to replay, and
  that is the more common reason to press ⏮.
- **A setting for each amount.** Lets each user pick, but adds two rows to Settings for a choice
  most users never make, and the default still has to be decided.

## Consequences

- ⏭ then ⏮ comes back to the same place.
- Skipping a long ad takes twice as many presses of ⏭. A hold still scrubs faster.

## Rules

1. `[convention]` While an episode is loaded, ⏮ skips back 15 s and ⏭ skips forward 15 s, on every
   way in. This replaces "forward 30 s" in ADR-031's rule 11 and ADR-018's rule 17.

**Conformance.** Covered by `EpisodeSkipsTest`: previous and next move an episode by the player's
increments, and the two increments are the same 15 s.
