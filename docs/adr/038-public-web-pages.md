# ADR-038: Rolabox's public web pages are static files in `site/`, deployed to GitHub Pages, and the privacy policy changes with the code it describes

- **Status:** Proposed
- **Date:** 2026-11-21
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Google Play needs two public URLs before Rolabox can be published (launch/08):

- **A privacy policy,** for any app that handles personal or sensitive data. Rolabox reads the music
  library (`READ_MEDIA_AUDIO`), sends crash reports from every user ([ADR-030](030-crash-reporting.md)),
  stores an email and syncs data with an account ([ADR-008](008-one-app-with-an-optional-account.md)),
  and talks to MusicBrainz, the Cover Art Archive, Apple's directory and podcast hosts
  ([ADR-027](027-artwork-from-the-internet.md), [ADR-031](031-podcasts.md)).
- **An account deletion page,** for any app that lets users create an account. It has to name the app
  and the developer, give the steps, and offer a way to ask for deletion without the app
  ([ADR-037](037-deleting-an-account.md)).

The pages have to load without sign-in, stay at the same URL, and say exactly what the Data safety
form says (launch/09). Rolabox has no server and no domain, and runs Firebase on the Spark plan with
no server code. The repo is public on GitHub, and the app's MusicBrainz `User-Agent` already names it.

What the policy says depends on the code: a new host, a new `Syncer`, a new backed-up file or a new
SDK changes what leaves the phone. A policy kept somewhere else drifts from the code without anyone
noticing, and Play treats a policy that doesn't match the app as a policy violation.

## Decision

We will keep Rolabox's public pages as **hand-written static HTML in `site/`** in this repo, and
**deploy them to GitHub Pages** with a GitHub Actions workflow, `pages.yml`. They are served at
`https://toomanyeduardos.github.io/rolabox/`.

| Page | Path | Linked from |
| --- | --- | --- |
| Landing | `site/index.html` | — |
| Privacy policy | `site/privacy/index.html` → `/privacy/` | Play Console's privacy policy field; the app's About (launch/06) |
| Account deletion | `site/delete-account/index.html` → `/delete-account/` | Play Console's account deletion field; the privacy policy |
| Stylesheet | `site/style.css` | Both pages |

- **No generator.** Two pages don't need Jekyll or a static site generator, and plain HTML has no
  build to break. GitHub Pages serves the uploaded artifact as it is.
- **No scripts, fonts, trackers or cookies,** and nothing loaded from another site. A privacy policy
  that tracks its readers would contradict itself. Links to other sites are plain links.
- **Readable at any text size.** Sizes are in `rem`, the layout reflows at 200% zoom, and the colors
  follow `prefers-color-scheme`, in the spirit of [ADR-017](017-accessibility.md).
- **The workflow** checks on every PR that touches `site/`: the links between the pages resolve,
  checked offline with lychee, and there are no scripts or third-party resources. On a push to `main`,
  it checks, then uploads `site/` and deploys it. The repo's Pages source is "GitHub Actions".
- **The policy changes in the same PR as the code it describes.** A PR that adds a host the app talks
  to, a `Syncer`, a backed-up file ([ADR-036](036-android-backup.md)), a permission or an SDK that
  collects data updates `site/privacy/` too, and its effective date. The PR template asks.
- **The public contact** is `rolabox.app@gmail.com`, an address used only for Rolabox. Requests to
  delete an account by email are handled by hand, within 30 days, as `docs/account-deletion-runbook.md`
  says.

## Alternatives considered

- **Firebase Hosting** (`rolabox.web.app`). A neutral domain on the Firebase project the app already
  has, at no cost on the Spark plan. But it deploys with the Firebase CLI by hand, as the rules do,
  or needs a service account secret in CI, and the domain belongs to the Firebase project, so moving
  projects moves the URL that's in Play Console.
- **GitHub Pages from `/docs` on `main`.** No workflow. But `docs/` holds the ADRs and the release
  notes, which would be published too, and Jekyll would render them.
- **A separate repo or a hosted page builder (Google Sites, Notion).** Keeps the app repo to code.
  But the policy would no longer be reviewed in the PR that changes what it describes, which is the
  drift this decision exists to stop.
- **One page, with deletion as a section** (`/privacy/#delete-account`). One file fewer. But Play's
  deletion field wants a page about deleting an account, and a reviewer landing halfway down a long
  policy is more likely to reject it.
- **A privacy policy generator.** Fast, but its templates claim things Rolabox doesn't do (analytics,
  advertising IDs, cookies), and anything it claims that the app doesn't do is a mismatch Play can
  reject.
- **A custom domain.** Most portable. But it costs money every year, and must be renewed or the
  policy's URL breaks. GitHub Pages can add one later, and Play Console's URLs change with it.

## Consequences

- The policy and the deletion page are versioned, reviewed and dated like code. Their history is
  public, which the policy points to.
- The URL includes the maintainer's GitHub username, and moves if the repo is renamed or moved.
  Play Console's two fields would need updating then.
- A PR that changes what leaves the phone has one more file to edit. Nothing but review checks that
  the text is right: a build can check that links resolve, not that a sentence is true.
- A deletion request by email is manual work, with a 30-day promise to keep.

## Rules

1. `[enforced]` Rolabox's public pages are static files in `site/`, and only `pages.yml` deploys them.
2. `[enforced]` Every link between the pages, and to their stylesheet, resolves. `pages.yml` fails
   otherwise.
3. `[enforced]` The pages have no `<script>` or `<iframe>`, and load no stylesheet, image or media
   from another site. `pages.yml` fails otherwise.
4. `[convention]` A change that adds a host the app talks to, a `Syncer`, a backed-up file, a
   permission or an SDK that collects data updates `site/privacy/` and its effective date in the same
   PR.
5. `[convention]` What the privacy policy says matches the Data safety answers in Play Console. A
   change to one is made to the other.
6. `[convention]` The deletion page names Rolabox and its developer, gives the in-app steps, and gives
   a way to ask for deletion without the app.

**Conformance.** Rules 2 and 3 are the `check` job of `pages.yml`, on every PR that touches `site/`.
Rule 1 is the workflow being the only one with `pages: write`. Rule 4 is the checkbox in
`.github/pull_request_template.md`. Rules 4 to 6 are otherwise reviewed.
