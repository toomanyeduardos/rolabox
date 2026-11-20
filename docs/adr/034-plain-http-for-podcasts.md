# ADR-034: Plain HTTP is allowed for podcast feeds, images and audio, tried as HTTPS first, and HTTPS is forced for every service Rolabox talks to

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Android blocks plain HTTP for an app that targets API 28 or later, unless the app allows it, and
Rolabox doesn't. Every request of the app goes through OkHttp or Media3's `DefaultHttpDataSource`
(`HttpURLConnection`), and both ask the platform's network security config before they open a
plain connection, and fail when it says no.

Podcasts are read from hosts that Rolabox doesn't choose. The feed URL is the directory's, and the
image and enclosure URLs are whatever the feed says ([ADR-031](031-podcasts.md), Feeds). Many hosts
still give `http://` URLs, for the feed, its images or its audio, so those shows can't be read, have
no artwork, or don't play. Hosts are unknown and endless, so they can't be listed. ADR-031 didn't
decide this. It was found while building playback/09.

The services the app itself talks to are few, known, and all HTTPS:

| Service | Hosts | Decided in |
| --- | --- | --- |
| Firebase Authentication, Firestore, Installations, Crashlytics | `*.googleapis.com`, `firebase-settings.crashlytics.com` | [ADR-008](008-one-app-with-an-optional-account.md), [ADR-030](030-crash-reporting.md) |
| Sign in with Google | `*.google.com`, `*.googleapis.com` | [ADR-013](013-account-linking.md) |
| The podcast directory | `itunes.apple.com` | [ADR-031](031-podcasts.md) |
| MusicBrainz | `musicbrainz.org` | [ADR-027](027-artwork-from-the-internet.md) |
| The Cover Art Archive, whose images redirect to the Internet Archive | `coverartarchive.org`, `*.archive.org` | [ADR-027](027-artwork-from-the-internet.md) |

What a podcast request sends: the URL, the client's default headers, and for a feed the `ETag` and
`Last-Modified` the same host gave last time. No account, cookie or token, and nothing about the
user ([ADR-008](008-one-app-with-an-optional-account.md), rule 6, makes the same request whatever
the account). A plain request can be read and changed on the way: someone on the network sees which
show and episode is fetched, and can swap the feed, the image or the audio.

For reference, iOS blocks HTTP by default too, and Apple gives apps an exception for media
(`NSAllowsArbitraryLoadsForMedia`) because of how podcasts and video are hosted.

## Decision

We will **allow plain HTTP by default** in `:app`'s network security config, **force HTTPS for the
domains of every service the app itself talks to**, and **try every `http://` podcast URL as
`https://` first**, falling back to the URL as given only when HTTPS doesn't work.

### The network security config

`:app`'s manifest names `@xml/network_security_config`, and nothing sets `usesCleartextTraffic`.

- **The base config allows cleartext,** and trusts the system's certificates, as the default does.
  It is what podcast hosts get.
- **One `domain-config` forbids it** for each service domain, with `includeSubdomains="true"`, so a
  host an SDK adds under the same domain is covered without a change here:

  | Domain | For |
  | --- | --- |
  | `googleapis.com` | Firebase Authentication, Firestore, Installations, Crashlytics' reports, Sign in with Google |
  | `google.com` | Sign in with Google |
  | `crashlytics.com` | Crashlytics' settings |
  | `apple.com` | The podcast directory |
  | `musicbrainz.org` | MusicBrainz |
  | `coverartarchive.org` | The Cover Art Archive |
  | `archive.org` | The Cover Art Archive's images |

- **A new service** joins the table, the config and the config's test in the change that makes its
  first request, and this ADR gets a dated revision.
- **`archive.org` is HTTPS-only although it also hosts podcasts.** An `http://` enclosure there is
  tried as HTTPS first (below), which the Internet Archive serves, so it plays. Only an Internet
  Archive host that doesn't serve HTTPS would fail, and the covers' redirect is worth more than it.
- **Domains no service uses aren't listed.** `firebaseio.com` is the Realtime Database's, which
  Rolabox doesn't use. Listing a domain is a promise the test keeps, not a guess.

### HTTPS first, for podcast URLs

An `http://` URL of a feed, an image or an enclosure, on the default port, is asked for as
`https://` with the same host, path and query. The URL as given is asked for only when that fails:

- **It fails** when the connection or the TLS handshake fails, or when it answers with neither a
  success (2xx) nor 304. A host may serve another site on HTTPS, and a 404 there says nothing about
  the feed.
- **A cancelled request isn't retried.** A search typed past, or a player that stopped, ends there.
- **The first request is upgraded, and redirects are followed as the server sends them.** A
  tracking redirect to an `http://` host is followed in plain HTTP. Rewriting each hop would mean
  following redirects by hand in both clients.
- **What is stored doesn't change.** The feed, image and enclosure URLs stay as the feed gave them,
  and each request is upgraded again. The publisher didn't move anything, and a host that drops
  HTTPS later still works. A feed's own redirects still move it ([ADR-031](031-podcasts.md),
  Feeds).
- **A URL with a port of its own isn't upgraded.** HTTPS on the same port is almost never served.

It is done once per client:

- **OkHttp, in `:device:podcasts:network:impl`:** an application interceptor of the podcasts'
  client, so the directory, the feeds and the images go through it. The directory's URL is already
  HTTPS, and passes as it is.
- **Media3, in `:device:playback:impl`:** a data source over `DefaultHttpDataSource`, as the
  upstream of the episodes' cache, so the download and the stream both go through it
  ([ADR-031](031-podcasts.md), Downloads, Stream while downloading). Media3 stays playback's only
  ([ADR-023](023-playback-engine.md), rule 9).

### Only the podcasts area writes `http://`

A plain URL in Rolabox's own code is a mistake everywhere but the podcasts area, whose tests and
previews may name one. Feeds' `http://` URLs arrive as data, not as literals, so playback needs none
either.

### What was not decided

- **Remembering hosts that don't serve HTTPS.** Each request to one pays a failed attempt, up to
  the connect timeout (15 s for OkHttp, 8 s for Media3) for a host that drops the packets. If that
  shows, a short memory of such hosts can be added to both clients.
- **Upgrading redirects** (above).

## Alternatives considered

- **Allow cleartext app-wide** (`usesCleartextTraffic="true"`). One line, and what open-source
  players such as AntennaPod do. But a future `http://` mistake anywhere in the app, in a service
  that sends a token, would go out unencrypted with nothing to stop it.
- **Allow cleartext by default and force HTTPS for the services, with no upgrade.** Simpler: no
  code in either client. But every `http://` podcast would go out in plain text, even from the many
  hosts that serve HTTPS too and only kept an old URL in the feed.
- **Refuse `http://` podcasts,** and say they can't be played. The safest. But the shows just look
  broken, and the user can't do anything about it.
- **List the podcast hosts that may use HTTP, and keep the default blocked.** The safest config.
  But hosts are unknown and endless, and a show on an unlisted host would look broken.
- **Rewrite the stored URL to HTTPS once it works.** One attempt per URL instead of two for hosts
  that don't serve HTTPS. But it changes the user's data for a guess, and syncs a feed URL the
  publisher never gave ([ADR-031](031-podcasts.md), Syncing follows).

## Consequences

- Shows whose feed, images or audio are on `http://` are read, have artwork and play.
- Most of them are fetched over HTTPS anyway, by the upgrade.
- What stays plain can be seen and swapped on the network: which show and episode, and the bytes.
  Nothing secret is in it, and it is accepted, as Apple accepts it for media.
- A host that doesn't serve HTTPS costs a failed attempt per request, and up to a connect timeout
  for one that drops the packets.
- The services can never be reached in plain text, by any client in the process, whatever URL the
  code builds.
- A new service is a change to the config and its test, with this ADR.
- Two small pieces of code, one per client, do the same upgrade.

## Rules

1. `[enforced]` `:app`'s network security config allows cleartext in its base config, and forbids
   it, with `includeSubdomains`, for exactly the domains of this ADR's table. The manifest names it,
   and doesn't set `usesCleartextTraffic`.
2. `[convention]` A new service the app talks to joins the table, the config and its test in the
   change that makes its first request.
3. `[enforced]` A string literal that starts with `http://` is written only in the podcasts area
   (`:device:podcasts:*`) and in tests.
4. `[convention]` An `http://` URL of a podcast feed, image or enclosure, on the default port, is
   asked for as `https://` first, and as given only when the HTTPS request fails to connect or
   answers with neither a 2xx nor 304. A cancelled request isn't retried. The podcasts' OkHttp client
   and the episodes' Media3 upstream each do it, and the stored URL never changes for it.
5. `[convention]` A podcast request sends the URL, the client's default headers, and a feed's own
   validators: no account, cookie, token or anything about the user.

**Conformance.** Rule 1 is `NetworkSecurityConfigTest` in `:app`'s unit tests, which parses the
config and the manifest, with a violating and a passing case. Rule 3 is the detekt rule
`CleartextUrlOutsidePodcasts` in `build-logic/detekt-rules`, with a violating and a passing case;
that it doesn't run on the podcasts area or on tests is its `excludes` in
`config/detekt/detekt.yml`. Rule 4 is covered by `HttpsFirstInterceptorTest` in
`:device:podcasts:network:impl` and `HttpsFirstDataSourceTest` in `:device:playback:impl`. Rule 5
holds by the clients as they are built: the podcasts' OkHttp client has no cookie jar and no
interceptor that adds a header, and `DefaultHttpDataSource` is given none. A show with an `http://`
feed and enclosure is checked by hand on a device.
