# Voice control

The voice orchestrator (and anything else with the data MCP) drives this TV through the data MCP's
`bedroom_tv` tool. Voice turns usually run on Fly, which cannot reach a TV on the home network, so
nothing here talks to the TV directly; Supabase and the hub carry it:

    bedroom_tv tool ──insert──▶ tv_commands ──realtime INSERT──▶ hub ──SSE /api/tv/stream──▶ TvLink (on the TV)
          ▲                                                                                     │
          └──realtime UPDATE── tv_commands.result ◀── hub /api/tv/result ◀──────────────────────┘

- **`tv_commands`** (data-layer 00135): one row per command, `op` + `args`, answered with `result`
  and `done_at`. Kept as the record.
- **The tool** (`data-layer/src/bedroomTv.ts`) subscribes to its row's update before inserting it and
  waits up to 15 s (45 s for `power`, `open` and `launch`, which may wait for a wake). Its description is the orchestrator's whole instruction set for the TV: the
  orchestrator loads no skills, so anything it must know about the TV goes there.
- **The hub** (status-hub `server.js` § THE BEDROOM TV) pushes each insert to every connected TV app
  and, with none connected, answers the row at once with "the TV is off", except `open` and `launch`,
  which it holds while the Pi wakes the TV, and `power`, which it leaves to the Pi (POWER.md). A TV
  that connects late is replayed only commands under 60 s old.
- **`TvLink`** (the companion app) holds the stream open, reconnecting with backoff from 1 s doubling
  to a minute (cut short when the screen comes on), and authenticates with the hub's device token (the one `bin/tv sidebar on` pushes).
  Started at boot by `BootReceiver`, and by `bin/tv link`.
  A hub deploy closes the stream (`link: java.io.EOFException` in logcat, then `link: connected`
  about 13 s later), and commands in that gap answer `the TV is off, or its app is not connected`
  while the TV is on; `bin/tv shell dumpsys power | grep mWakefulness` tells the two apart.

## What it can do

`power` (done by the Pi, not the app: POWER.md), `brightness`, `contrast`, `night`, `volume`,
`open` (a YouTube URL in the YouTube app, or a catalog `intent:` deep link: WATCHING.md),
`launch`, `sidebar`, `overlay`, `now`, `media`, `input`, `key`, `sleep` (the Pi's: POWER.md § Sleep timer), and `guide_refresh` (sent by the `tv_guide` tool after a write: an open TV Guide redraws, GUIDE.md). Verified end to end on 2026-10-30 for `brightness` (read) and
`now` (app, title, position); the rest run the same code paths `bin/tv` proved, from inside the app.

`now` reads media sessions, which Android allows to an enabled notification listener:

    adb shell cmd notification allow_listener com.jaredgantt.tvcompanion/.Listener

`open` and `launch` hold a screen wake lock for 30 s around starting the app (`holdAwake`). Starting
an app is not user activity, and on an HDMI input the TV keeps the screen on itself, so the 10-minute
screensaver timer can be long expired: without the lock the screensaver starts about two seconds after
the app does, pausing YouTube before the video plays, and pressing a key afterwards uncovers YouTube's
home screen. The lock is released with `ON_AFTER_RELEASE`, which restarts the idle timer.

**Choosing what to put on** is the `youtube` and `catalog` tools: WATCHING.md.

`media` is `play` and `pause` on whatever is playing, through its media session, the same grant `now`
uses. It answers with `now`. With nothing playing or paused it answers `nothing is playing`. There is
no skip or seek: a session's position is its app's last report plus the time since, so an app that
says it is playing when it is not turns a seek into a jump to nowhere. Anything beyond play and pause
is a session's job.

**Whether something is really playing** is not the app's answer. A media session reports only what
its app claims, and Apple TV's says PLAYING over its title page's trailer and over a stopped film. So
data-layer `bedroomTv.ts` follows `now` and `media` with a `check` row, which the Pi answers
(`bin/tv check`): whether the foreground app is sending sound (an audio output thread holding its
track wrote in the last second; mute and quiet scenes still write), and for Apple TV whether its title
page is on screen. `judge()` merges the two: `playing` is the sound, and a session that says playing
with no sound becomes `not playing` with its position dropped.

**Every `open` is followed up on the Pi**, whoever wrote the row: the `bedroom_tv` tool or the phone's
TV Guide (the hub's `/api/tv/guide/open`). The Pi sees every insert, so this is the one place it is
done (tvHome.ts `followUp`): the app staying in front (`bin/tv front`, WATCHING.md), Prime's profile picker, Apple TV's Play button (WATCHING.md), then up to
15 s for sound. It writes the row's `outcome` and `checked_at` (data-layer 00176), separate from
`result`, the app's own answer. The tool waits for both and answers `playing: true`, or `ok: false`
with what is on screen; another app in front of the one opened is `ok: false` even while it plays. An open that is not playing afterwards is logged as an error on the Pi.

**A replayed command is not done twice.** The hub replays unanswered rows when the stream reconnects;
the app remembers its last 50 commands and answers a repeat with what it did the first time. Answers
retry for about 15 s before the app gives up and logs it.

`input` (`hdmi` 1-4) opens TCL's passthrough input the way `bin/tv input` does. The PlayStation 5 is
HDMI 2 (CEC reports it on port 2). It wakes the TV first, like `open`.

## What it cannot do, and why

An app cannot turn the TV off or on (`goToSleep` is a system permission), so `power` is the Pi's
(POWER.md). An app cannot inject key presses into another app either, so `key` (back, home, the
arrows, select) is the Pi's too: data-layer `tvHome.ts` runs `bin/tv key` over adb and answers the
row, and the hub leaves `power`, `key`, `sleep` and `check` rows alone. The Pi also presses OK on Prime Video's "Who's
watching?" picker after an `open` or `launch` into Prime (WATCHING.md § Prime's profile picker). While the TV is off the app is not running:
`open`, `launch` and `input` wait for the Pi to wake it, `key` and setting a `sleep` answer `the TV is off`, and anything
else answers `the TV is off, or its app is not connected to the hub` (the hub cannot tell which). Screenshots stay with `bin/tv` over adb, so
the orchestrator hands them to a session, which has the `tv` skill.
