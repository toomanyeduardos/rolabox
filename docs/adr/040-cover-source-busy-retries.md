# ADR-040: A `503` from the cover source is sent again in the same run, after its `Retry-After`, before the run ends

- **Status:** Accepted
- **Date:** 2026-11-23
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-027](027-artwork-from-the-internet.md) (in part: "The source" and "Retries",
  for a `503` only)

## Context

[ADR-027](027-artwork-from-the-internet.md) downloads missing covers in one WorkManager work, one
request a second, to MusicBrainz and the Cover Art Archive. It counts a `503` as a server error.
Under "Retries", a server error ends the run with `Result.retry()`, and WorkManager's exponential
backoff schedules the next run (30 s, then 1 min, 2 min, 4 min, and so on up to 5 h). The album
stays as it was.

On 2026-11-23, after a reinstall of 1.0.2, all 28 albums started with no cover. MusicBrainz was
shedding load for everyone: from another machine with Rolabox's `User-Agent`, 5 of 11 searches got
a `503` with `retry-after: 1` and `x-ratelimit-zone: global` (or `search-shed`), even 2 to 3 s
apart. Rolabox's own spacing was correct. Each run got at most one cover before its next request
got a `503`. The album playing got its cover on the fourth run, about 4 minutes in, and 21 albums
were still waiting, with the backoff doubling each time
([bug card](https://trello.com/c/NL3pN6bs)).

A `503` with `Retry-After: 1` doesn't mean the source is down. It means "ask again in a second".
ADR-027 treats it like an outage, and a whole run, with WorkManager's growing backoff, is too big a
step for a one-second wait.

## Decision

We will send a `503` again from inside the request, in the same run, before it ends the run.

- **What is sent again:** the same request, to either host, when the answer is `503`. No other
  status, and not a timeout or a lost connection: those still end the run as ADR-027 says.
- **How long it waits:** the `Retry-After` in whole seconds. If it is missing, is a date, or is
  less than a second, it waits 1 s, the spacing of ADR-027's rule 2. The retry still goes through
  the app's request spacing, so it is never sent less than a second after the request before it.
- **How many times:** up to 3 more times for one request. If the last one is also a `503`, the
  request is a server error, and the run ends with `Result.retry()` as before.
- **A long wait isn't waited for in the run:** a `Retry-After` over 10 s ends the run at once, and
  WorkManager's backoff does the waiting, with the network constraint and outside the process.
- **Nothing else in ADR-027 changes.** A `503` is never an attempt (ADR-027, rule 5), whether or
  not a retry later succeeds. The order of the albums, the setting check before each album, and the
  work's constraint and backoff stay as they are.

## Alternatives considered

- **Keep ADR-027 as it is.** Correct, and simple: every `5xx` is one case. But while MusicBrainz is
  busy, a fresh library fills over hours, a cover at a time, which is the bug this ADR fixes.
- **Shorter backoff in WorkManager** (linear, or a smaller start). It cuts the wait but keeps the
  cost: each `503` still throws away the run, restarts the worker, and reads the albums again, and
  WorkManager's minimum backoff is 10 s, longer than the 1 s the server asks for.
- **Slow down to one request every 2 or 3 s.** The `503`s were global load shedding, not Rolabox's
  rate: they came at 3 s apart too. It would slow every download on every normal day.
- **Retry every `5xx` and timeouts in the run too.** A `502` or a timeout says nothing about when to
  try again, and a source that is down would hold the worker for the length of every retry. A
  `503` with a short `Retry-After` is the one answer that says when.
- **Retry without a limit, as long as `Retry-After` is short.** A source that sheds load for minutes
  would keep the worker running with nothing to show. Three retries cover the short bursts seen.

## Consequences

- When MusicBrainz is busy, a run carries on through short bursts of `503`s, and covers come in at
  about the rate of a normal day, a little slower.
- One request can take up to about 4 s longer (3 retries of 1 s plus the spacing), or up to about
  40 s with the longest `Retry-After` waited for. The album wanted still goes next, after the
  request in flight.
- A source that keeps answering `503` costs 4 requests per run instead of 1 before the run ends.
  That is within what MusicBrainz asks, since each one waits as long as it was told.
- The source has two kinds of server error to tell apart, and its tests cover both.

## Rules

1. `[convention]` Only a `503` is sent again in the same run, the same request, at most 3 more
   times. Every other server error, a timeout and no network end the run with `Result.retry()`
   (ADR-027, Retries).
2. `[convention]` Each retry waits for the `Retry-After` in seconds, at least 1 s, and still goes
   through the app's request spacing (ADR-027, rule 2). A `Retry-After` over 10 s isn't waited for
   in the run: the request is a server error at once.
3. `[convention]` A `503` is never an attempt, whether or not a retry finds the cover (ADR-027,
   rule 5).

**Conformance.** Reviewed, and covered by `MusicBrainzCoverNetworkDataSourceTest`, against recorded
answers with no network: a `503` then a cover, the `Retry-After` waited for on the test clock, a
missing one waiting 1 s, the error after the last retry, a long `Retry-After` not waited for, a
`502` not sent again, and the archive's `503`.
