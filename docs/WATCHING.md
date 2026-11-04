# Finding something to watch, and putting it on

The aim: any Claude (a session, or the voice assistant) can look through everything available on the
account, recommend from actual watch history rather than from each service's algorithm, and put it
on the bedroom TV. This is an evolving feature; open work is in TODO.md § Watching.

Three data MCP tools (data-layer) and one TV op:

| Tool | What it gives | Where it runs |
|---|---|---|
| `youtube` public ops | `search`, `video` (full description, chapters), `related`, `channel` (newest uploads) | anywhere: parsed from the JSON YouTube embeds in its pages |
| `youtube` account ops | `history`, `home` (account recommendations), `subscriptions`, `subscription_feed`, `watch_later`, `liked`, `playlists`, `playlist` | the Pi only, through `home_requests` |
| `catalog` | `search`, `title`, `episodes`, `popular`, `added` across Netflix, Prime Video, Apple TV+, Disney+, Hulu, Max and the rest, each offer with an Android TV deep link | anywhere: JustWatch's GraphQL API |
| `bedroom_tv` `open` | plays a YouTube URL, or opens a catalog deep link in its app | the TV's app (VOICE.md) |

## YouTube, public

`data-layer/src/youtube.ts`. The watch, results and channel pages carry `ytInitialPlayerResponse` and
`ytInitialData` as JSON in a `<script>`. WebFetch strips scripts and truncates the 1.5MB page, so it
never sees a description or a search result. That is why this tool exists. No API key, and it works
from Fly as well as the home network.

**Transcripts are not available.** The caption URLs in the player response return an empty body, and
`youtubei/v1/get_transcript` answers FAILED_PRECONDITION. Both want a proof-of-origin token that only
YouTube's own player mints. Chapters and the description are the content signal there is.

## YouTube, the signed-in account

`data-layer/src/youtubeAccount.ts`, on the Pi only, reached through `home_requests`; how it signs in is
described there. The bedroom TV's YouTube is on the same account, so its plays are in `history` with how
much of each was watched. "not signed in" from every account op means the Pi browser needs signing back
in (pi-browser README).

## The catalog

`data-layer/src/catalog.ts`, from JustWatch's GraphQL API (`apis.justwatch.com/graphql`, the API its
own site uses, with no key). It is unofficial, so a schema change breaks it here. For each title it
gives IMDb score and votes, runtime, rating, genres, cast, seasons and episodes, and every offer:
included in a subscription, free, free with ads, rent or buy with price.

**Deep links.** JustWatch returns `deeplinkURL(platform: ANDROID_TV)`, an `intent://…#Intent;…;end`
URI. Where one lacks the app's package (Apple TV, Disney+), `SERVICES` in catalog.ts adds it. The
companion app parses it with `Intent.parseUri` (`open`), and `bin/tv open '<intent:…>'` passes it to
`am start` whole.

**Subscribed services** are app_settings `streaming_services`, a JSON array of catalog.ts `SERVICES` keys
(`["netflix", "amazonprime", …]`). `popular` and `added` default to them, and every offer on one is
marked "(his)". **Not set yet**: TODO.md § Blocked on the owner.

## What opens on the bedroom TV's own apps (tested 2026-10-30)

| Service | Deep link | Signed in on the TV | Result |
|---|---|---|---|
| YouTube | watch URL | yes | plays at once |
| Prime Video | catalog `intent://watch.amazon.com/…` | yes | the title page, "Play S1 E1"; when Prime was not already running, its "Who's watching?" profile picker first (below) |
| Apple TV | catalog `intent://tv.apple.com/…` | **no Apple TV+ on it** (signed in or not, unchecked) | the episode page, offering "Accept Free Trial" |
| Netflix | catalog `intent://www.netflix.com/…` | **no** | Google's "Choose an account to continue to Netflix" sign-in sheet |
| Disney+ | catalog link | **no** | the same sign-in sheet |
| Hulu, Max, others | | not tried | |

**Prime's profile picker.** A Prime deep link that cold-starts the app stops on "Who's watching?",
even with one profile on the account, and `now` reports nothing playing. It does not time out on its
own. `key select` (OK, DPAD_CENTER) picks the highlighted profile, the last one used, and Prime carries
on into the link; with the link's `time=0` removed, a movie resumes where it was left.

The Pi presses it: every `open` or `launch` that lands in Prime runs `bin/tv profile prime` once the TV
is awake (data-layer `tvHome.ts`). The picker has no accessibility tree (`bin/tv screen` is empty in
Prime) and logs nothing, so `profile` reads the screen: a screenshot through `tesseract` (installed on
the Pi, `tesseract-ocr`; about 35 MB and a quarter of a second a look), up to 20 s, pressing OK only
when the text says "Who's watching?", and stopping early once Prime shows a page of other text. It
then reads the screen again and fails if the picker is still there; a failure, or a run where every
capture was blank (DRM playback captures as solid black), is logged as an error by data-http. OK is
never pressed on anything but the picker, because on a rental's title page the focused button is the
purchase.

A sign-in sheet is backed out of with `back`. Signing in is done by the owner: credentials are never
typed or chosen by Claude.

## The Apple TV box on HDMI 1

The bedroom also has an Apple TV ("Bedroom tv", `_companion-link._tcp`).
Streaming subscriptions may live there rather than on the TV's apps. `pyatv` sees it
(`uvx --from pyatv atvscript scan`) with the Companion, AirPlay and RAOP protocols. Controlling it
(launching an app, opening a `tv.apple.com` or Netflix URL, play/pause, what is playing) needs
**one-time Companion pairing, which shows a 4-digit PIN on the Apple TV screen**. That PIN cannot be
read remotely: the Google TV's `screencap` returns nothing on an HDMI input (protected content), so
someone in the room has to read it out once. TODO.md.

An earlier pyatv controller exists on the Pi (`~/tv-dashboard/atv_control.py`, March 2026, with the
`tv-server` unit inactive). Its credentials in `~/.pyatv.conf` are for the **Living Room** Apple TV,
not the bedroom one.
