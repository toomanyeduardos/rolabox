# ADR-031: Podcasts: Apple's directory, feeds read on the phone, downloads in playback's Media3 cache, and follows synced per show

- **Status:** Accepted
- **Date:** 2026-11-16
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-002](002-offline-first-data-flow.md) (rule 1, for a directory's results and
  the feed of a show opened from search), [ADR-022](022-data-source-sub-parts.md) (rule 2, in the
  same part), [ADR-018](018-device-navigation.md) (rule 17, for episodes) and
  [ADR-026](026-artwork.md) (rules 1 and 6, for episodes)
- **Revised 2026-11-17:** `PodcastDirectory.show` takes the show a search found, not its id ("The
  directory and a show from search"). With only the id, opening a show needs a `lookup` request to
  the directory for its feed URL before the feed is read, and each such request counts against
  the directory's limit of about 20 a minute, which searches already share.
- **Revised 2026-11-18:** Mobile Data follows its frames, 35 to 37 of `player-designs/device`, added
  for podcasts/07 ("Mobile data", "Stream while downloading", "Errors", rules 9 and 15). The setting
  is Use Mobile Data, in a Podcasts list of the device's settings, not a row of the settings list.
  The Episode screen shows that an episode needs Wi-Fi in place of Play, and opens that list, so
  `play` failing with `MobileDataOff` is no longer how the user learns it. An episode that runs out
  of bytes waits for the network, still playing, shows why on Now Playing, and goes on by itself
  when the network allows it: the design's WAITING FOR WI-FI, and the same for no connection, which
  the ADR had paused. The free-space check uses only the 50 MB when the feed gives no size.
  DOWNLOADING and WAITING FOR WI-FI are in the screen's top corner, as frames 28 and 37 draw them,
  not in place of the bar's right label, as this ADR first said.
- **Revised 2026-11-19:** Search's screens, for podcasts/08 ("Search"). A show and an episode
  opened from search have keys of their own, with the directory id and the feed URL, and for an
  episode its GUID and enclosure URL, so a screen can read them from the feed after process death.
  A result already in the library opens from the library, but a show with Follow Off, which would
  hide most of its feed there. Turning Follow On from search adds the show with every episode of
  the feed just read, through a new `PodcastsRepository.follow`. Search comes back by itself when
  the connection does, through `PodcastDirectory.observeConnected`, which a network data source of
  the part's own answers. Rule 4 says what "never written" meant: the answers aren't kept as a
  cache, and only what the user plays or follows joins the library, as this ADR already said. The
  Android back of frame 29 only hides the keyboard, as ADR-032 (rule 4) says.
- **Revised 2026-11-19:** Episodes that leave their feed, and the follow check's details, for
  podcasts/09 ("The database", "Follow checks"). An episode no longer in its feed was to stay
  listed, but publishers drop older episodes from the public feed to put them behind a paywall, as
  The Daily does after about three weekdays, so the list would fill with episodes that can't be
  played. Such an episode keeps its row, with when it left the feed, and is listed only while it has
  a download. The read of one show's feed when its Follow is turned On is unique work of its own,
  since `KEEP` on the check's name would drop it while a check runs. A feed answered with no
  episodes is skipped as unreadable, so a broken feed doesn't hide a whole show, and a show added
  from a synced follow needs the feed's title.
- **Revised 2026-11-19:** Syncing follows, for podcasts/10 ("Syncing follows", rule 14). Every follow a pull turns
  On has its feed read, through its own unique work (`checkShow`), not only one whose show the library doesn't
  have: a check already running drops a second `checkAll` under `KEEP`, and a show turned back On on another phone
  lists its new episodes as if followed here. A follow whose show is in the library keeps the show's feed URL when
  a remote follow is applied: a feed's move keeps its `updatedAt`, so on the tie the cloud's older URL would win and
  the next check would move it back again. Firestore comes from `:common:firestore`
  ([ADR-033](033-shared-firestore-module.md)). Rule 14 is `[enforced]`, by the rules' emulator tests.
- **Revised 2026-11-19:** Plain HTTP, for podcasts/11 ("Feeds", "Downloads", "Artwork"). Many hosts give `http://`
  URLs for the feed, its images or its audio, which Android blocked, so those shows couldn't be read, had no artwork
  or didn't play. This ADR didn't decide it. [ADR-034](034-plain-http-for-podcasts.md) does: plain HTTP is allowed
  for podcast hosts, each `http://` URL is tried as HTTPS first, and HTTPS is forced for the services.
- **Revised 2026-11-19:** Refused audio, for podcasts/12 ("Search", "Errors"). Some publishers keep paid episodes
  in their public feed, and their server refuses the audio, so Play from search added the show and the episode,
  the download failed, and an empty show was left in Podcasts. An episode opened from search now has its audio
  checked with `PodcastDirectory.checkAudio` (a request for its first kilobyte) when its Episode screen opens, and
  a 401, 403 or 410 is the new `PodcastError.AudioRefused`: the screen shows "Not available" in place of Play, and
  nothing is added. Only episodes from search are checked: checking every episode of the library would slow its
  screens. While episodes need Wi-Fi the check isn't made, so rule 9 holds. The player's 401, 403 or 410 is the
  new `PlaybackError.Unplayable.Refused`, which says "This episode isn't available" and isn't reported.

## Context

Podcasts is on the main menu and does nothing. The design session of 2026-11-11
([Podcasts: browse, play, search and follow](https://trello.com/c/oEVSfgcJ)) decided what the user
sees, in frames 09 and 22 to 34 of `player-designs/device`:

- **Podcasts → Owner → Show → Episode,** always, with Search as the first row of Podcasts.
- **Search** finds shows by name or creator and episodes by title or description, in a podcast
  directory, as the user types on the phone's keyboard. It doesn't search what is on the phone.
- **Playing an episode downloads it and plays while it downloads.** Its show joins Podcasts with
  Follow Off and that one episode. Nothing else downloads on its own.
- **Each episode keeps its position,** and is unplayed, partly played or played: a full orange dot,
  a half dot, no dot.
- **Follow On lists a show's new episodes** each time the app checks, on open with a connection.
  Follow Off lists only the episodes that were downloaded. Follows sync between the user's phones.
  Positions, played state and downloads stay on the phone.
- **On Now Playing,** an episode shows its title, its show and its owner, and its artwork. ⏮ and ⏭
  skip back 15 s and forward 30 s, a hold scrubs, and while the episode downloads the bar's lighter
  part is what's downloaded. When it ends it's marked played and playback stops.

None of the architecture behind it is decided. [ADR-020](020-modules-by-product-area.md) lists
"search and podcasts" as left open, [ADR-023](023-playback-engine.md) left where an episode's
position is stored, and [ADR-024](024-library-index.md) said that podcasts get a database of their
own. What the decision has to fit:

- **Podcasts are Rolabox's own files.** Music is the user's files, found through MediaStore and
  never copied. An episode is fetched from the internet and kept by the app, so it goes with the app.
- **Media3 is playback's only** ([ADR-023](023-playback-engine.md), rule 9, `[enforced]`).
- **The local database is the single source of truth** ([ADR-002](002-offline-first-data-flow.md)),
  and a part's repositories read and write only its local side
  ([ADR-022](022-data-source-sub-parts.md), rule 2). A search of a directory is a question asked of
  the network, and its answer isn't the user's data.
- **No image library** ([ADR-026](026-artwork.md), rule 4), and OkHttp and kotlinx-serialization only
  in `:network:impl` modules ([ADR-027](027-artwork-from-the-internet.md), rule 7).
- **No-account mode makes no Firebase request, and any other request is made as when signed in**
  ([ADR-008](008-one-app-with-an-optional-account.md), rule 6). The directory, the feeds, the
  episodes and their images are all such requests.
- **Episodes probably shouldn't be shuffled** ([ADR-028](028-shuffle-and-repeat.md), What was not
  decided).

**Target state.** This ADR is ahead of the code. Rule 13 is `[enforced]` from now on, and holds
trivially until podcasts/03 adds the parser. The tickets that build the rest are listed under each
section, and the planned rules name theirs.

## Decision

We will search **Apple's iTunes Search API**, read each show's **RSS feed on the phone** with the
platform's pull parser, keep the podcasts in a **database of their own**, download episodes with
**Media3's `DownloadManager` in the playback area**, into the same cache the player reads, and
**sync follows per show with last-write-wins**.

### The directory: Apple's iTunes Search API

The design asks for four fields. Both free directories were tried against them on 2026-11-16:

| Field | iTunes Search API | Podcast Index |
| --- | --- | --- |
| Show name | Yes: `entity=podcast` | Yes: `search/byterm` (title, author, owner) |
| Creator | Yes: `entity=podcast` matches `artistName` | Yes: `search/byterm` |
| Episode title | Yes: `entity=podcastEpisode` | Only through `search/byperson`, which is meant for people |
| Episode description | Yes: `entity=podcastEpisode` | Only through `search/byperson` |
| Key | None | A key and a secret, shipped in the APK, and a SHA-1 header with the time |
| Limits | About 20 requests a minute, "subject to change" | Not published |
| Terms | Written for promoting Apple's store; nothing on podcasts in particular | Open data, with terms of service |

So **the directory covers all four fields,** and the product needs no fallback. "tiberius
gracchus" finds *Unpopular Reforms (Gracchi III)* of *Emperors of Rome*, whose title doesn't hold
either word: only its description does.

- **Two requests per search,** made at once: `media=podcast&entity=podcast` for Shows, and
  `media=podcast&entity=podcastEpisode` for Episodes, each with `limit=25` and the phone's country.
  Shows match their name and their creator, and episodes their title and their description, which is
  the design's grouping.
- **What is sent** is the text typed and the country. Nothing about the user or the account.
- **Within the limit.** About 20 requests a minute is about 10 searches. A search is sent once
  typing has paused for 400 ms and the text has at least 2 characters. A newer text cancels the
  request of an older one, and the answers to the last 20 texts are kept in memory, so deleting a
  letter doesn't ask again. A refusal for the rate (HTTP 403 or 429) is
  `PodcastError.DirectoryBusy`, with its own message.
- **What is used from an answer.** A show: `collectionId`, `collectionName`, `artistName` and
  `feedUrl`. A show with no `feedUrl` is dropped, since it can't be played. An episode: also
  `trackName`, `episodeGuid`, `episodeUrl`, `releaseDate`, `trackTimeMillis` and `description`,
  which is plain text. An answer's artwork URLs are not used: search results have no images.
- **"Creator" in a result is the directory's `artistName`.** "Owner" in the library is the feed's
  (below). They are almost always the same name, and search results come before any feed is read.
- **A show's identity is its directory id,** `collectionId`, as `itunes-<id>`. Every show enters
  the library through the directory, by search or by sync. The feed URL is a field that can change
  (below).
- **The directory is replaceable.** It is one interface of `:device:podcasts:network:api`, and its
  answers are the part's own models. Podcast Index is the fallback if Apple's terms or limits change,
  at the cost of the key and of episode search through `search/byperson`.

### Feeds: read on the phone, with the platform's pull parser

A show's episodes come from its RSS feed, not from the directory.

- **OkHttp fetches the feed** in `:device:podcasts:network:impl`, following redirects. A permanent
  redirect (301 or 308), or `itunes:new-feed-url` in the feed, replaces the stored feed URL.
- **An `http://` feed is read,** as HTTPS first ([ADR-034](034-plain-http-for-podcasts.md)), and so
  are its images and its episodes' audio.
- **It asks only for what changed.** The `ETag` and `Last-Modified` of the last answer are stored
  with the show and sent back, and a 304 is "nothing new".
- **The parser is `XmlPullParser`,** from `android.util.Xml`, namespace-aware. It is the platform's,
  so there is no dependency, and it streams, so a feed of thousands of items is read without holding
  it. It reads RSS 2.0 with the iTunes namespace, and ignores the rest. Atom isn't read: podcast
  feeds are RSS. Its tests run on the JVM with Robolectric, against recorded feeds.
- **What is kept of a show:** its title; its owner, from `itunes:author`, then `itunes:owner`'s
  `itunes:name`, then the directory's `artistName`, then none ("Unknown" is never stored as text,
  as in [ADR-024](024-library-index.md), rule 12); its image URL (`itunes:image`, then `image/url`);
  the feed URL, `ETag` and `Last-Modified`.
- **What is kept of an episode:** its GUID; its title; its date (`pubDate`); its length
  (`itunes:duration`, in seconds, `MM:SS` or `HH:MM:SS`, or none until the player knows it); its
  description; its enclosure's URL, size in bytes and type; and its own image URL (`itunes:image`),
  when it has one.
- **An episode is identified by its show and its GUID,** and by its enclosure URL when it has no
  GUID. A GUID that changes makes a new episode, as in every podcast player.
- **An item with no audio enclosure is skipped.** Rolabox plays audio, so a video item isn't listed.
- **The description is plain text.** It is taken from `description`, then `itunes:summary`, then
  `content:encoded`, and its HTML is turned into text with paragraph breaks
  (`HtmlCompat.fromHtml`), in the `:network:impl`. Links and images are dropped: the display can't
  open them.
- **A feed that isn't RSS, or that ends early,** is `PodcastError.FeedUnreadable`. Items read before
  the error are not kept: a feed is applied whole or not at all.

### The layout

Podcasts is a part of the device, with a local and a network sub-part
([ADR-022](022-data-source-sub-parts.md)), and a part with screens:

```
:device:podcasts:api            shows, owners, episodes, their ids and errors; the repositories,
                                the directory, positions, and artwork as pixels
:device:podcasts:impl           their implementations, the follow check and the artwork download in
                                WorkManager, the Syncer of follows, its process-start task
:device:podcasts:testing        fakes of the three :api modules
:device:podcasts:local:api      the database and the artwork files, as interfaces
:device:podcasts:local:impl     the Room database, and the artwork files in filesDir/podcasts/artwork/
:device:podcasts:network:api    the directory, the feeds, the images and the follows in Firestore, as interfaces
:device:podcasts:network:impl   the directory and the images over OkHttp and kotlinx-serialization, the
                                feeds with XmlPullParser, the follows over Firestore
:device:podcasts:ui:api         the keys and entry contract of Podcasts, Owner, Show, Episode and Search
:device:podcasts:ui:impl        those screens and their ViewModels
```

- **Search belongs to podcasts.** It searches the directory and nothing else, and searching the
  phone is a non-goal, so there is nothing for a shared search part to share. Searching the library
  is decided when it comes.
- **The directory and the follows share one `:network:api`,** as different interfaces. The one of
  the follows is called only by the `Syncer` ([ADR-022](022-data-source-sub-parts.md), rule 6, read
  per interface). The others are called by the part's `:impl`.
- **How the parts see each other:**

  | Module | Depends on, of podcasts and playback |
  | --- | --- |
  | `:device:playback:api` | `:device:podcasts:api`, for `EpisodeId` in `PlaybackItemId.Episode` |
  | `:device:playback:impl` | `:device:podcasts:api`, for an episode's URI, title, show and owner, and its position |
  | `:device:podcasts:impl` | `:device:playback:api`, for the state of each download |
  | `:device:podcasts:ui:impl` | `:device:podcasts:api` and `:device:playback:api`, to play |

  `:device:podcasts:api` never depends on `:device:playback:api`. That is the one direction that
  would close a cycle, and Gradle refuses one.
- **Playing from search is two calls from the ViewModel:** the podcasts repository adds the episode
  and its show, then `PlaybackController.play`. It isn't a use case: no result combines the two
  ([ADR-021](021-data-flow-through-layers.md)), and a use case in `:device:podcasts:api` would need
  `:device:playback:api`.

The main menu's Podcasts row maps the part's exit to its key, as Music's does
([ADR-020](020-modules-by-product-area.md)). podcasts/02 adds the data modules, podcasts/05 the
screens.

### The directory and a show from search are read from the network

Search and a show opened from search (frame 32) are answers of the directory and of a feed, asked
for while the user waits, and not kept. So they are an exception to
[ADR-002](002-offline-first-data-flow.md)'s rule 1 and [ADR-022](022-data-source-sub-parts.md)'s
rule 2, and the only one in the part:

- **`PodcastDirectory`, in `:device:podcasts:api`, reads the network on each call:** `search(text)`
  and `show(show)`, the show with every episode of its feed. It isn't a repository, and its answers
  are never written to the database.
- **`show` takes the show a search found,** with its feed URL, not its id. A show is only opened
  from search, so the caller has it, and the feed is the only request: opening a show asks nothing
  of the directory, and doesn't count against its limit.
- **Everything the library shows comes from the database:** Podcasts, an owner, a show in the
  library, an episode, its position and its dots. A show from search becomes the library's only when
  one of its episodes is played or it is followed.
- **No connection** is `PodcastError.NoConnection`, and search shows its message (frame 33). No
  results is an empty answer, not an error, and search shows its own message for it.

### Search

Search is the device screen of `PodcastSearchKey` (frames 29 to 34), with the field of ADR-032 at
the top of its list. podcasts/08 builds it.

- **As the user types,** the ViewModel sends the text once typing has paused for 400 ms and it has
  at least 2 characters, and cancels the search of an older text (rule 1). Shows come first, with
  their creator on the right in the row's quieter value, then Episodes, under section labels.
- **No connection** keeps the text and shows frame 33. `PodcastDirectory.observeConnected` says
  when the phone has a network that reaches the internet, and the search of the text is sent again
  when it does. It is answered by `ConnectionNetworkDataSource`, in `:device:podcasts:network:api`,
  from `ConnectivityManager`'s default network in its `:impl`, as playback reads it for downloads.
- **The directory busy** (`PodcastError.DirectoryBusy`) keeps the text and says "Too many
  searches", "Wait a moment, then search again." It isn't sent again by itself, which would spend
  more of the limit: the next change of the text, or the search key, sends it.
- **A show opened from search** (frame 32) is `PodcastSearchShowKey`, with the show's directory id,
  title, creator and feed URL: what `PodcastDirectory.show` needs, and the header's title before the
  feed is read. Follow, then every episode of the feed, newest first, with the download arrow and
  no dots. A feed that can't be read says "Can't open this show", "Its feed couldn't be read. Try
  again later.", and no connection says frame 33's words.
- **An episode found by search** is `PodcastSearchEpisodeKey`, with its show's fields and its GUID
  and enclosure URL. Its Episode screen (frame 26, with Play) reads the show's feed and finds the
  episode there by GUID, else by enclosure URL, so that playing it adds the enclosure's size and
  type, which the directory doesn't give. The keys carry the identities the directory and the feed
  give a show and an episode, and not the episode's text, which can be long.
- **Already in the library:** center on an episode result that is in the library opens its own
  Episode screen, and on a show with Follow On, its own Show screen, with their dots and downloads.
  A show in the library with Follow Off opens frame 32 from the feed, since its own screen lists
  only what was downloaded: Follow then reads Off from the library, and the episodes the library has
  show their dots and downloads, and open their own Episode screen.
- **Follow On from search** is `PodcastsRepository.follow(feed)`: in one transaction it adds the
  show, unless it is there, with the feed's owner, every episode of the feed it doesn't have, and the
  follow On. The feed was just read, so there is nothing for the follow check to read at once. Off
  is `setFollowed`, as on a show of the library.
- **Playing from search** is `addEpisode` then `play`, as before.
- **Audio the publisher refuses.** Some publishers keep paid episodes in their public feed and refuse
  their audio. When an episode found by search has been found in its feed, its Episode screen calls
  `PodcastDirectory.checkAudio` with the feed's enclosure URL, before it offers Play. It is a `GET`
  with `Range: bytes=0-1023` to the publisher's server, through `AudioNetworkDataSource` in
  `:device:podcasts:network:api`, with the podcasts' client and redirects followed. It isn't
  Apple's, so it doesn't count against the directory's limit, and it isn't a download: nothing of
  the body is read or kept (rule 8). A 401, 403 or 410 is `PodcastError.AudioRefused`, and the
  screen's one row is "Not available", on which center does nothing, so nothing is added. Any other
  answer (a 206, a 200 from a server that ignores the range, a 404, a 5xx), no connection, or no
  answer within 10 seconds offers Play as before: the player says what it finds. The screen shows
  nothing until the check answers, so a press can't add the episode first. While episodes need
  Wi-Fi the screen shows Needs Wi-Fi and the check waits for the network to allow it (rule 9).
  There is no paywall tag in RSS to read instead: the server's answer is the signal. Episodes of
  the library aren't checked, since a screen of many would make as many requests: playback's
  `Unplayable.Refused` says it for them.

### The database

`:device:podcasts:local:impl` keeps a Room database of its own, `podcasts.db`
([ADR-024](024-library-index.md), rule 5).

| Table | Holds | Kind |
| --- | --- | --- |
| Shows | The database's own id; the directory id; the feed URL; the title; the owner and its grouping key; the image URL; `ETag`, `Last-Modified` and when the feed was last read; when the show joined | The user's data |
| Episodes | The database's own id; the show; the GUID; the title; the date; the length; the description; the enclosure's URL, size and type; the image URL; when it was listed; when it left the feed | The user's data |
| Progress | The episode; its position; whether it is played; when it was last played | The user's data |
| Follows | The directory id; the feed URL; whether it is followed; `updatedAt` | The user's data, synced |

- **Every table is the user's data.** None can be rebuilt from what is on the phone, so each change
  has a migration tested against the exported schema ([ADR-024](024-library-index.md), rule 10).
- **Owners aren't a table.** An owner is the grouping key of its shows, ignoring case and
  whitespace, like an artist's, and the Owner screen lists the shows that share it.
- **Progress is apart from Episodes,** so reading a feed again rewrites what the feed says and never
  touches a position or a played state.
- **Follows are apart from Shows,** since a follow can arrive by sync before its show is in the
  library, and an unfollow is kept as the record that wins over an older follow (Syncing follows).
- **Download state and the audio are not in this database.** They are playback's (Downloads), so
  there is one record of what is downloaded, and the episode rows read it.
- **Nothing is deleted.** Deleting episodes and shows is a non-goal. An episode that leaves its
  feed keeps its row, with when it left, `leftFeedAt`, set when a check reads the feed (a 200, not
  a 304) and the episode isn't in it. One that comes back has `leftFeedAt` cleared. Publishers drop
  older episodes from the public feed to put them behind a paywall, so an episode that left can
  only be played from its download.
- **What a show lists:** with Follow On, every episode its feed has listed since it was followed or
  added, newest first, but those that left the feed. With Follow Off, only the episodes with a
  download, done or in progress. An episode that left the feed is listed only with a download, done
  or in progress, whatever Follow says. The rows of the episodes not listed stay, and show again
  when Follow is turned back On or the episode comes back to the feed.

`EpisodeId` and `ShowId` are the database's own ids, as `SongId` is the index's
([ADR-024](024-library-index.md), rule 8). podcasts/02 builds the database.

### Played, partly played and unplayed

- **An episode is played once its position reaches 90% of its length.** The end of most episodes is
  ads and credits, and a share of the length works for an episode of 5 minutes and one of 3 hours,
  where a fixed time wouldn't. An episode that ends is played too.
- **Partly played** is a position past 0:00 that isn't played. **Unplayed** is never started.
- **Played stays played.** Playing it again doesn't clear it, and its position is still stored.
- **The rule is the podcasts part's.** Playback stores the position and the length (below), and
  `:device:podcasts:impl` decides played, so the 90% is in one place.
- **The length** is the feed's, and the player's once the episode has played, which replaces it.
  With no length, an episode is played only when it ends.
- **The Episode screen offers Play** for an unplayed or played episode, from 0:00, and **Resume** and
  **Play from Start** for one partly played.

### Positions: the podcasts part keeps them, playback reads and writes them

This is what [ADR-023](023-playback-engine.md) left open.

- **`EpisodeProgressRepository`, in `:device:podcasts:api`,** gives an episode's position and
  stores a position with the length the player knows. Playback reaches the podcasts' database only
  through it ([ADR-021](021-data-flow-through-layers.md)), and no table is shared.
- **Playback reads the position when an episode starts,** and stores it when ADR-023 stores its
  restore point: on each pause, every few seconds while playing, going to the background, when the
  service stops, and when the episode ends.
- **The restore point is unchanged.** It holds the episode's `PlaybackItemId`, and the position the
  episode resumes from is the podcasts', so the two never disagree.

playback/09 builds it.

### Downloads: Media3's `DownloadManager`, in the playback area

- **`:device:playback:impl` downloads episodes,** with Media3's `DownloadManager` and a
  `DownloadService`, into a `SimpleCache` that the player reads. Media3 is playback's only
  ([ADR-023](023-playback-engine.md), rule 9, unchanged), and the download and the player have to
  share one cache, which one module can hold.
- **Playing an episode downloads it.** `play` with an episode that isn't downloaded adds its
  download first. Nothing else starts one, and nothing downloads on its own.
- **The files are app-specific storage,** in `filesDir/episodes/`, so they go with the app, and the
  system never clears them as it can clear the cache directory. The cache never evicts: deleting is
  a non-goal.
- **The cache key of an episode is its id,** not its URL. Enclosure URLs go through tracking
  redirects that change from one request to the next, and one episode must stay one download.
- **Media3 keeps its own record** of each download and of the cache, in its own SQLite database.
  It is the engine's state, kept by the engine, and not one of Rolabox's tables
  ([ADR-024](024-library-index.md), rule 5 is about Rolabox's relational data).
- **HTTP is Media3's own** (`DefaultHttpDataSource`), for the download and for the stream, so
  OkHttp stays in `:network:impl` modules ([ADR-027](027-artwork-from-the-internet.md), rule 7).
  An `http://` enclosure is asked for as HTTPS first, by a data source over it
  ([ADR-034](034-plain-http-for-podcasts.md), rule 4).
- **The service is a foreground service of type `dataSync`** while a download runs, declared with
  its permission in `:device:playback:impl`'s manifest. Its notification shows the progress where
  notifications are allowed. `POST_NOTIFICATIONS` is still never asked for
  ([ADR-023](023-playback-engine.md), rule 8): the media notification already shows the episode
  that is downloading, since a download only starts by playing it.
- **After the process dies,** Media3's record keeps the bytes so far and the download, and the
  `DownloadManager` resumes it, with a range request, when it is created again. A
  `ProcessStartTask` of `:device:playback:impl` creates it ([ADR-025](025-process-start-tasks.md)).
  There is no `Scheduler`: starting a foreground service from the background is restricted from
  Android 12, and the next open of the app is soon enough for an episode the user chose to play.
- **A download that fails** is retried by Media3, then left failed. Its row shows the download
  arrow again, and playing it adds the download again.
- **Storage full.** Before a download is added, playback compares the free space of `filesDir`
  with the enclosure's size from the feed, plus 50 MB, and `play` returns
  `PlaybackError.StorageFull` instead of starting. A feed that gives no size is checked against the
  50 MB alone. A download that fails later with the disk full is failed like any other.
- **`ItemDownloads`, in `:device:playback:api`,** reports each download's state by `PlaybackItemId`:
  waiting, downloading with its fraction, done or failed. `:device:podcasts:impl` turns it into the
  episode's own download state in `:device:podcasts:api`, for the rows' percentage and arrow and for
  what a show with Follow Off lists. Media3's types never leave the `:impl`.

podcasts/07 builds it.

### Mobile data: a setting

- **A setting, Use Mobile Data, decides whether episodes use a metered network.** On, a download
  runs on any connection. Off, it waits for an unmetered one, which is the `DownloadManager`'s
  requirement, and the player's own stream refuses a metered network too, so no byte of an episode
  is fetched over mobile data. An episode then plays as far as it is downloaded, and waits for Wi-Fi
  there (Stream while downloading). Search, a show opened from search, the follow check and artwork
  always use any network: they aren't episodes.
- **It reads as On when never set.** A download only starts because the user pressed Play.
- **It is a preference that doesn't sync,** `episodesOnMobileData` in `:common:userdata`
  ([ADR-011](011-preferences-sync.md), rules 10 and 11). It is about this phone's data plan, not a
  taste: a tablet on Wi-Fi and a phone on a plan want different values.
- **Its row is in a Podcasts list of the device's settings** (frame 35). The settings list gains a
  Podcasts row after Main Menu, which opens a list with one row, Use Mobile Data, toggled in place by
  center as the main menu's switches are. The list gives later podcast settings a home. It is a
  device screen of `:device:settings:impl`, with its key in `:device:settings:api`, as Main Menu's
  is ([ADR-020](020-modules-by-product-area.md), rule 17), and the row is always shown.
- **Playback says when an episode needs Wi-Fi.** `:device:playback:api` reports whether episodes
  need Wi-Fi now: Use Mobile Data is Off and the phone's network is metered. `:device:playback:impl`
  reads the network with `ConnectivityManager`'s default network callback, since it is also what
  refuses the stream, so the rule is in one place. With no network at all, an episode doesn't need
  Wi-Fi: it needs a connection, which is `NoConnection`.
- **The Episode screen shows it in place of Play** (frame 36). For an episode with no download,
  while episodes need Wi-Fi, the screen's one row is "Needs Wi-Fi. Select to allow mobile data." in
  place of Play. Center on it is an exit of `:device:podcasts:ui:api`'s contract, which
  `:device:ui:impl`, the parent of both, maps to the key of the Podcasts list of settings
  ([ADR-020](020-modules-by-product-area.md), rule 12), so MENU comes back to the episode. Play
  returns as soon as Wi-Fi or the setting allows it. An episode partly downloaded keeps Play or
  Resume, and plays as far as it is downloaded.
- **`play` still returns `PlaybackError.MobileDataOff`** for an episode with no download while
  episodes need Wi-Fi, for a press that arrives before the screen has changed, and the toast says so
  ([ADR-018](018-device-navigation.md), rule 18).

### Stream while downloading

- **The player reads an episode through a `CacheDataSource`** over the same `SimpleCache`, with the
  episode's cache key. What is downloaded is read from the cache. What isn't yet, the player fetches
  from the network itself, without writing it: only the download writes the cache. So a seek ahead of
  the download plays at once, and a region the two fetch at the same time is fetched twice, which is
  accepted.
- **An episode that is downloaded plays with no connection.** One that isn't, and runs out of bytes,
  waits there, and isn't skipped: a queue of one has nowhere to skip to, and the file isn't broken.
- **It waits for a reason, reported as state.** `PlaybackState` gains what the loaded item waits
  for: `PlaybackError.NoConnection` with no network, `PlaybackError.MobileDataOff` with Use Mobile
  Data Off on a metered network, and `null` otherwise. Now Playing shows WAITING FOR WI-FI for the
  second in the screen's top corner, where it shows DOWNLOADING (frame 37), and NO CONNECTION for
  the first.
- **While it waits it is still playing,** as the design draws it: `isPlaying` stays `true` and the
  position stays. It goes on by itself as soon as the network allows it, an unmetered one for
  `MobileDataOff` and any for `NoConnection`, and so does the download. ⏯ pauses it, and a paused
  episode doesn't go on by itself.
- **`PlaybackState` gains the download of the loaded item,** from `ItemDownloads`. Now Playing draws
  the downloaded fraction as the lighter part of the bar, and DOWNLOADING with the percentage in the
  screen's top corner, where SCRUB and VOLUME are shown, until it is done (frame 28). The bar's
  labels stay the time elapsed and the time left. The fraction is of bytes, which is close to the
  fraction of time for the constant bit rates podcasts use. Media3 doesn't push a download's
  progress, so it is read on playback's `Tick` while a download runs.

podcasts/07 builds it, with Now Playing's bar.

### Follow checks

- **On open, with a connection.** A `ProcessStartTask` of `:device:podcasts:impl` requests unique
  WorkManager work, `podcast-follow-check`, with a network constraint and `KEEP`, so it runs once a
  connection exists. The app coming back to the foreground requests it again when the last check is
  more than an hour old. There is no periodic work.
- **Turning Follow On reads that show's feed at once,** with the same worker, as unique work of its
  own, `podcast-follow-check-<directoryId>`, with a network constraint and `KEEP`. Under the
  check's name, `KEEP` would drop it while a check runs. Following from search doesn't: the feed was
  just read (Search).
- **The work reads the feed of each followed show,** with `ETag` and `Last-Modified`, adds the
  episodes whose GUID is new, and updates what changed of the others. It never touches progress, and
  never downloads an episode. A feed read whole (a 200) also sets `leftFeedAt` on the episodes it no
  longer has, and clears it on those it has again (The database). A 304 changes no episode.
- **What the work writes of a show is one transaction,** and only while its follow is still On, so
  a show unfollowed while the work ran isn't changed, and isn't added. A feed that moved (a 301 or
  308, or `itunes:new-feed-url`) replaces the feed URL of the show and of its follow together, and
  leaves the follow's `updatedAt` as it is: the move isn't the user's choice, and mustn't win over
  their newer Follow on another phone.
- **A followed show that isn't in the library yet,** because its follow arrived by sync, is added
  from its feed, with the feed's title and owner. A feed with no title doesn't add it: it is skipped
  as unreadable, since the follow carries no title of its own.
- **A feed that can't be read is skipped** until the next check. It is the publisher's failure, so
  it is logged and not reported to Crashlytics ([ADR-030](030-crash-reporting.md), rule 6). A feed
  read whole with no episode is one that can't be read: it would hide every episode of the show
  without a download, and a publisher's broken feed is likelier than a show that emptied itself.

podcasts/09 builds it.

### Syncing follows

- **One document per show,** `users/{uid}/podcastFollows/{showKey}`, where `showKey` is
  `itunes-<collectionId>`, with `feedUrl`, `followed` and `updatedAt`
  ([ADR-010](010-firestore-security-rules.md), rule 4).
- **Last-write-wins per show** ([ADR-011](011-preferences-sync.md), rule 2). Unfollowing writes
  `followed: false` with a new `updatedAt`, and that record is the tombstone: it is kept, locally and
  in Firestore, so an older follow from another phone can't bring the show back. Follows are never
  deleted. A user follows tens of shows, not thousands.
- **The `Syncer` of follows is in `:device:podcasts:impl`,** added to the set of
  `:common:sync:impl` ([ADR-022](022-data-source-sub-parts.md), rule 5). It runs only for a signed-in
  user who hasn't chosen no-account mode ([ADR-011](011-preferences-sync.md), rule 8). After a pull,
  each follow it turned On has that show's feed read, as unique work of its own (Follow checks), so a
  show the library doesn't have is added, and one turned back On lists its new episodes.
- **The cloud's feed URL is for a phone without the show.** Applying a remote follow to a show in the
  library keeps the show's own feed URL, which this phone's checks keep where the feed is now. A move
  keeps the follow's `updatedAt` (Follow checks), so it isn't pushed as the user's choice.
- **The model both sides speak** is `Follow` and `SyncedFollows`, in `:device:podcasts:api`, as
  `SyncedPreferences` is in `:common:userdata:api` ([ADR-022](022-data-source-sub-parts.md), rule 7).
  The Firestore mapping and transaction are in `:device:podcasts:network:impl`, with Firestore from
  `:common:firestore` ([ADR-033](033-shared-firestore-module.md)).
- **Follows made without an account are kept, and merged on sign-in.** Each was stored with its
  `updatedAt`, so the first sync after signing in resolves it against the account's, show by show,
  like any other change. Nothing is overwritten wholesale in either direction.
- **The security rules** allow that path for its owner only, with `hasOnly` on the three fields, a
  `showKey` of the form `itunes-` and digits, a `feedUrl` that is an `https` or `http` URL of at most
  2,048 characters, a boolean, and `updatedAt` checked as ADR-011's rule 6 says. Each case has its
  emulator test ([ADR-010](010-firestore-security-rules.md), rule 3).
- **What doesn't sync:** positions, played state, downloads, the Mobile Data setting, and the shows
  and episodes themselves. A show joins another phone's library only by being followed.

podcasts/10 builds it, with the rules and their tests.

### Episodes in playback

- **`PlaybackItemId.Episode(val id: EpisodeId)`** joins `Song`, and `PlaybackSource.Episode(id)` is
  the source of a queue of one episode ([ADR-023](023-playback-engine.md), The playable item).
- **An episode's `PlaybackItem`** has the episode's title, its show as `artist`, its owner as
  `album`, and its length. Now Playing shows them in that order (frame 27). Its URI is the enclosure
  URL, from `:device:podcasts:api` ([ADR-023](023-playback-engine.md), rule 5), and the player reads
  it through the cache (above).
- **Playing an episode queues that episode alone,** not the list it was selected from. Nothing
  plays after it, so when it ends the queue ends, playback stops and Now Playing is popped, as
  [ADR-018](018-device-navigation.md)'s rule 8 says of any queue. This is the exception to rule 17's
  first sentence.
- **⏮ skips back 15 s and ⏭ skips forward 30 s,** with Media3's `seekBack` and `seekForward`, whose
  increments are set in the player's builder, as the restart threshold of songs is. ⏭ in the last
  30 s goes to the end, and so ends the episode. A hold scrubs as it does for a song. This replaces
  rule 17's ⏮ and ⏭ for an episode, and ⏮'s 3 s restart doesn't apply.
- **Every way in does the same** ([ADR-023](023-playback-engine.md), rule 3). The session's player
  is wrapped in a `ForwardingPlayer` that turns previous and next into skip back and forward while an
  episode is loaded, so the notification, the lock screen and a headset skip by seconds too, and the
  notification shows those buttons.
- **No shuffle and no repeat.** A queue of one has nothing to shuffle, and an episode that repeats
  would never be played. While an episode is loaded the player's repeat mode is off, whatever the
  settings say, and the header shows neither icon ([ADR-028](028-shuffle-and-repeat.md)).
- **Speed** is designed in by ADR-023 and isn't built in this round.

playback/09 builds it.

### Artwork: the feed's image, downloaded by the podcasts part

- **An episode's artwork is its own image, then its show's, then none.** Search results have none.
- **The podcasts part downloads it,** in WorkManager work of `:device:podcasts:impl`,
  `podcast-artwork-download`, with a network constraint, one image at a time, through
  `:device:podcasts:network:api` over OkHttp, as the library's covers are
  ([ADR-027](027-artwork-from-the-internet.md)). It runs when a show joins the library and when an
  episode with a new image is listed.
- **It is stored decoded down to one size,** the size of Now Playing's cover, as a file in
  `filesDir/podcasts/artwork/` named for the show or the episode and its image URL. Feed images are
  often 3,000 pixels square, and only one size is ever drawn. A file that is missing, or whose name
  doesn't match the URL any more, is downloaded again. There is no table.
- **The Online Artwork setting stops it too.** It is the user's choice about images fetched from the
  internet ([ADR-027](027-artwork-from-the-internet.md), rule 3), whoever fetches them.
- **`PodcastArtworkRepository`, in `:device:podcasts:api`, gives an episode's artwork as pixels,**
  as `ArtworkRepository` does for a song ([ADR-026](026-artwork.md)). A podcasts model holds no
  image and no image URL. Now Playing and playback's artwork watcher use the one that matches the
  loaded item's `PlaybackItemId`, so Now Playing, the notification and the lock screen still show the
  same image. An episode's `PlaybackItem.artwork` is `null`. This is the change to ADR-026's rules 1
  and 6.
- **No image library,** as ADR-026's rule 4 says. `BitmapFactory` decodes with a sample size.

playback/09 builds it, with Now Playing (frame 27).

### Errors

Every call that can fail returns an `Either` ([ADR-007](007-error-handling.md)).

- **`PodcastError`, in `:device:podcasts:api`:** `NoConnection`; `DirectoryBusy`, for the rate;
  `DirectoryFailed`, for any other answer the directory can't give; `FeedUnreadable`, for a feed that
  can't be fetched or read; `AudioRefused`, for an episode whose server refuses its audio with a
  401, 403 or 410 (Search); and `Storage`, wrapping the database's error.
- **No results is not an error.** It is an empty answer.
- **`PlaybackError` gains what playing an episode can end in:** `StorageFull` and `MobileDataOff`,
  returned by `play`, and `NoConnection` and `MobileDataOff`, reported as state while it waits. A
  download that fails is a state of `ItemDownloads`, not an error of a call.
- **`PlaybackError.Unplayable.Refused`:** the server answered 401, 403 or 410 while the player
  streamed an item or read it through the download's cache. Now Playing says "This episode isn't
  available". A download that fails for it stays `ItemDownload.Failed`, with no reason: the player,
  which reads the same bytes, is what tells the user.
- **What is reported to Crashlytics** ([ADR-030](030-crash-reporting.md), rule 6): a database
  failure. Not a feed that can't be read, a directory that refuses, audio the publisher refuses, no
  connection or a full disk, which are expected outcomes.

### What was not decided

- **Searching the library,** and with it a shared search part.
- **Deleting episodes and shows,** and with it evicting the cache.
- **Playback speed,** designed in by ADR-023.
- **Playing the next episode when one ends,** and downloading new episodes on their own.
- **Syncing positions and played state.** They would sync per episode, keyed by show and GUID, the
  day they do.

## Alternatives considered

**The directory.**

- **Podcast Index.** Open data, terms written for podcast apps, and an API for podcasts only. But it
  searches episodes only through `search/byperson`, made for people's names, a key and a secret
  would ship in the APK where anyone can read them, and each request signs the time with SHA-1, so a
  phone with a wrong clock fails. Kept as the fallback.
- **Both, Apple's for search and Podcast Index for feeds.** But the feed is the publisher's own, and
  reading it directly needs neither.
- **A server of Rolabox's own that indexes feeds.** Full control of search. But it is a backend to
  run and pay for, which nothing else needs, and ADR-008 keeps Firebase for accounts only.
- **Titles only for episodes,** as the ticket allowed if no free directory searched descriptions.
  Apple's does, so the design is met.
- **Open a show from search by its id,** as `show(id)`. A key of one string for the Show screen,
  and a show could be opened from an id alone. But the feed URL then comes from a `lookup` request to
  the directory, one more request per show opened, against a limit that searches already use.

**Feeds.**

- **A parser library,** such as RSS-Parser. Less code. But a dependency for something the platform
  does, and libraries read the feed into memory whole, where the pull parser streams it.
- **Episodes from the directory, not the feed.** One source, already JSON. But the directory lags
  the feed, caps an answer at 200 items, and isn't the show's publisher.
- **Keep the HTML of descriptions, and render it.** The display shows one font, and can't open a
  link.

**Downloads.**

- **WorkManager writing a plain file, and the player reading it.** Plain `.mp3` files. But playing a
  file while another process writes it means a data source of our own that waits for bytes, and the
  fraction, the resume after a kill and the retries would be written by hand. Media3 does all of
  them.
- **The download in the podcasts part, with Media3 allowed there.** The episodes' part would own its
  downloads. But a `SimpleCache` may be opened once per folder in a process, so the player and the
  download would still have to share one instance across two `:impl` modules, which only a third
  module could hold, and ADR-023's rule 9 would have to change for it.
- **Stream only, and download nothing.** The simplest. But the design downloads what is played, and
  an episode would need a connection every time.
- **A `Scheduler` to resume downloads in the background.** Downloads would finish without the app
  opening. But starting a foreground service from the background is restricted from Android 12, and
  the gain is small for an episode the user is listening to.

**Played, positions and follows.**

- **Played in the last 30 s, or only at the end.** A fixed time is too much of a short episode and
  too little of a long one, and "at the end" leaves every episode with ads unplayed.
- **The position in playback's own store.** Playback already stores one point. But the dots and
  Resume belong to the podcasts' screens, and a position per episode is relational data, which
  belongs in the podcasts' database.
- **Follows as a set merged by id, with separate tombstones.** But a follow is a switch per show,
  On or Off, and Off with its time is already the tombstone.
- **One Firestore document holding every follow.** One read. But a map with keys of the user's
  choosing can't have a `hasOnly` allowlist ([ADR-010](010-firestore-security-rules.md), rule 3),
  and every change would rewrite the whole list.
- **Periodic follow checks.** New episodes would be listed while the app is closed. But the design
  checks on open, and a check while closed would only be seen on open anyway.

**Artwork.**

- **Draw the image straight from its URL with an image library.** ADR-026 rules it out, and the
  cover would need a connection each time.
- **Generalize the library's `ArtworkRef` to name an episode.** One repository for artwork. But the
  library's `:api` would know podcasts, and the library and podcasts don't depend on each other.

## Consequences

- Search covers all four fields, with no key, from one directory whose terms don't name podcasts
  and whose limit is about 10 searches a minute. A user typing quickly can see the busy message.
- Podcasts is nine modules, and the app over fifty. `:app` lists four more `:impl` modules.
- Playback grows: a second service, a cache, a download record, a `ForwardingPlayer`, a new kind of
  item and a dependency on the podcasts' `:api`. Its `:impl` is the only one that knows Media3, as
  before.
- The podcasts part and playback each depend on the other's `:api`, in one direction each.
- Downloads are cache files named by Media3, not `.mp3` files. They can't be opened outside the app,
  and they go with it.
- A download stopped by a kill waits for the next open of the app.
- Two exceptions to "the database is the source of truth": the directory, and a show opened from
  search. Both are the podcasts part's, and named.
- A new setting, Use Mobile Data, with a list of its own in the device's settings (frame 35), and a
  non-synced field in ADR-011's table.
- Playback watches the phone's network, and the Episode screen has an exit into the device's
  settings, which the device maps.
- An episode waiting for the network looks like it plays, with the reason shown, until the network
  comes back or the user pauses it.
- The follows add a collection to Firestore and its rules, the first synced data of a device part.
- The 90% rule means an episode can be played with its last minutes unheard, on purpose.
- The fraction on Now Playing's bar is of bytes, and is off for an episode with a variable bit rate.

## Rules

1. `[convention]` The podcast directory is Apple's iTunes Search API, called only from
   `:device:podcasts:network:impl`, with only the text typed and the country. A search is sent after
   400 ms without typing, with at least 2 characters.
2. `[convention]` A show's episodes come from its RSS feed, read on the phone. A show is identified
   by its directory id, and an episode by its show and its GUID.
3. `[convention]` The podcasts area is `:device:podcasts:{api,impl,testing}`, its sub-parts
   `:device:podcasts:local:{api,impl}` and `:device:podcasts:network:{api,impl}`, and its screens
   `:device:podcasts:ui:{api,impl}`. Search is part of it.
4. `[convention]` Only `PodcastDirectory` reads the network to answer a screen: search, a show
   opened from search, and the check of an episode's audio opened from search. Its answers are never kept in the database as a cache: a show and its
   episodes join the library only when the user plays one of them or follows the show. Everything
   else a screen shows of podcasts is read from the database.
5. `[convention]` The podcasts' relational data is in their own Room database, every table of it is
   the user's data, and download state and audio are not in it.
6. `[convention]` An episode is played once its position reaches 90% of its length, or it ends. The
   podcasts part decides it.
7. `[convention]` An episode's position is stored by playback through `EpisodeProgressRepository`,
   and read through it when the episode starts.
8. `[convention]` Episodes are downloaded only by `:device:playback:impl`, with Media3's
   `DownloadManager`, into app-specific storage, keyed by the episode's id, and only when the episode
   is played. The player reads them through the same cache.
9. `[convention]` With Use Mobile Data off, no byte of an episode is fetched over a metered network.
   The setting doesn't sync, and is in the Podcasts list of the device's settings.
10. `[convention]` Follows sync one document per show, last-write-wins on `updatedAt`, and an
    unfollow is kept as `followed: false`. Positions, played state and downloads don't sync.
11. `[convention]` Playing an episode queues it alone. ⏮ and ⏭ skip back 15 s and forward 30 s on
    every way in, and an episode is never shuffled or repeated.
12. `[convention]` An episode's artwork is downloaded by the podcasts part, stored at one size, and
    crosses an `:api` as pixels only from `PodcastArtworkRepository`.
13. `[enforced]` Only `:device:podcasts:network:impl` uses the XML pull parser (`org.xmlpull.v1`
    and `android.util.Xml`).
14. `[enforced]` The security rules of `users/{uid}/podcastFollows/{showKey}` allow its owner only,
    with `hasOnly` on `feedUrl`, `followed` and `updatedAt`, and have emulator tests for each case.
15. `[convention]` An episode that runs out of bytes waits, still playing, with `NoConnection` or
    `MobileDataOff` as state, and goes on by itself when the network allows it. ⏯ pauses it, and a
    paused episode never goes on by itself.

**Conformance.** Rule 13 is the detekt rule `FeedParserOutsidePodcastsNetwork` in
`build-logic/detekt-rules`, with a violating and a passing case, which reports an import of
`org.xmlpull.v1` or `android.util.Xml`. That it runs everywhere but
`:device:podcasts:network:impl` is its `excludes` in `config/detekt/detekt.yml`. What this ADR
relies on is already enforced elsewhere: Media3 only in `:device:playback:impl`
([ADR-023](023-playback-engine.md), rule 9), so rule 8's "only playback" holds by the build; Room
only in `:local:impl` modules ([ADR-024](024-library-index.md), rule 4); OkHttp and
kotlinx-serialization only in `:network:impl` modules ([ADR-027](027-artwork-from-the-internet.md),
rule 7); Firestore only in `:network:impl` modules and `:common:firestore`
([ADR-033](033-shared-firestore-module.md), rule 3); the layout of rule 3 by ADR-020's and ADR-022's checks on module paths; and a cycle between
the two `:api` modules by Gradle. The rest are reviewed, and covered by tests as they are built:
rule 6 by the tests of `EpisodeProgressRepository` in `:device:podcasts:impl`; rules 2 and 4 by the
tests of `:device:podcasts:network:impl` against recorded answers and feeds, with no network;
rules 7, 8, 9, 11 and 15 by the real player's tests in `:device:playback:impl`; rule 10 by `FollowsSyncerTest` in
`:device:podcasts:impl` and the contract tests of the follows in `:device:podcasts:local:impl`. Rule 14 is
`firebase/test/firestore.rules.test.mjs`, which the `firestore-rules` job in CI runs against the emulator.
