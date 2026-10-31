# Finding something to watch, and putting it on

The aim: any Claude (a session, or the voice assistant) can look through everything available to
Jared, recommend from what he actually watches rather than from each service's algorithm, and put it
on the bedroom TV. This is an evolving feature; open work is in TODO.md § Watching.

Three data MCP tools (data-layer) and one TV op:

| Tool | What it gives | Where it runs |
|---|---|---|
| `youtube` public ops | `search`, `video` (full description, chapters), `related`, `channel` (newest uploads) | anywhere: parsed from the JSON YouTube embeds in its pages |
| `youtube` account ops | `history`, `home` (his recommendations), `subscriptions`, `subscription_feed`, `watch_later`, `liked`, `playlists`, `playlist` | the Pi only, through `home_requests` |
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

## YouTube, his account

`data-layer/src/youtubeAccount.ts`. YouTube's public Data API has had no watch history or
recommendations since 2016, so this uses the site's own internal API (`youtubei/v1/browse`, browse
ids `FEhistory`, `FEwhat_to_watch`, `FEchannels`, `FEsubscriptions`, `VLWL`, `VLLL`,
`FEplaylist_aggregation`, `VL<playlist>`). It is signed in with the cookies of the Pi's automation
browser (pi-browser repo; logged in as his Google account). Cookies are read live over its
DevTools port (`Storage.getCookies`) for each request, never stored, and sent only to youtube.com.
Requests carry the `SAPISIDHASH` header the site computes. The browser keeps the cookies fresh.

The bedroom TV's YouTube is signed in to the same account, so its plays are in `history`, with how
much of each was watched (`watched N%`). Measured 2026-10-30: 400 history rows back to mid-October
took 4.2s; each page is about 200 videos, followed by continuation tokens up to 20 pages.

Any machine asks through `home_requests` (data-layer README § The Pi's home-network jobs). If the Pi
browser is signed out of Google, every account op fails with "not signed in": sign it back in over
VNC (pi-browser README).

## The catalog

`data-layer/src/catalog.ts`, from JustWatch's GraphQL API (`apis.justwatch.com/graphql`, the API its
own site uses, with no key). It is unofficial, so a schema change breaks it here. For each title it
gives IMDb score and votes, runtime, rating, genres, cast, seasons and episodes, and every offer:
included in a subscription, free, free with ads, rent or buy with price.

**Deep links.** JustWatch returns `deeplinkURL(platform: ANDROID_TV)`, an `intent://…#Intent;…;end`
URI. Where one lacks the app's package (Apple TV, Disney+), `SERVICES` in catalog.ts adds it. The
companion app parses it with `Intent.parseUri` (`open`), and `bin/tv open '<intent:…>'` passes it to
`am start` whole.

**His services** are app_settings `streaming_services`, a JSON array of catalog.ts `SERVICES` keys
(`["netflix", "amazonprime", …]`). `popular` and `added` default to them, and every offer on one is
marked "(his)". **Not set yet**: TODO.md § Blocked on Jared.

## What opens on the bedroom TV's own apps (tested 2026-10-30)

| Service | Deep link | Signed in on the TV | Result |
|---|---|---|---|
| YouTube | watch URL | yes | plays at once |
| Prime Video | catalog `intent://watch.amazon.com/…` | yes | the title page, "Play S1 E1"; when Prime was not already running, its "Who's watching?" profile picker first (below) |
| Apple TV | catalog `intent://tv.apple.com/…` | **no Apple TV+ on it** (signed in or not, unchecked) | the episode page, offering "Accept Free Trial" |
| Netflix | catalog `intent://www.netflix.com/…` | **no** | Google's "Choose an account to continue to Netflix" sign-in sheet |
| Disney+ | catalog link | **no** | the same sign-in sheet |
| Hulu, Max, others | | not tried | |

**Prime's profile picker.** With two profiles on the account, a Prime deep link that cold-starts the
app stops on "Who's watching?" and `now` reports nothing playing. The second profile ("j") was removed
2026-10-31 (amazon.com/gp/video/profiles → Edit profile, from the Pi browser), leaving only "jared";
whether the TV app still shows the picker with one profile is untested. The website does still show it.
It does not time out on its own (seen 2026-10-31: it sat there until pressed). `key select` (OK,
DPAD_CENTER) picks "jared" and Prime carries on into the link; with the link's `time=0` removed, a
movie resumes where it was left (Heat, 2026-10-31, resumed at 8:40). Nothing on the `open` path presses
it yet: the picker has no accessibility tree (`bin/tv screen` is empty in Prime), so detecting it means
a screenshot or inferring it from "Prime foreground, nothing playing" a few seconds after the open.

A sign-in sheet is backed out of with `back`. Signing in is Jared's to do: credentials are never
typed or chosen by Claude.

## The Apple TV box on HDMI 1

The bedroom also has an Apple TV ("Bedroom tv", <apple-tv-ip> on 2026-10-30, `_companion-link._tcp`).
His streaming subscriptions may well live there rather than on the TV's apps. `pyatv` sees it
(`uvx --from pyatv atvscript scan`) with the Companion, AirPlay and RAOP protocols. Controlling it
(launching an app, opening a `tv.apple.com` or Netflix URL, play/pause, what is playing) needs
**one-time Companion pairing, which shows a 4-digit PIN on the Apple TV screen**. That PIN cannot be
read remotely: the Google TV's `screencap` returns nothing on an HDMI input (protected content), so
Jared has to read it out once. TODO.md.

An earlier pyatv controller exists on the Pi (`~/tv-dashboard/atv_control.py`, March 2026, with the
`tv-server` unit inactive). Its credentials in `~/.pyatv.conf` are for the **Living Room** Apple TV,
not the bedroom one.
