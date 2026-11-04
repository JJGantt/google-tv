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

## What it can do

`power` (done by the Pi, not the app: POWER.md), `brightness`, `contrast`, `night`, `volume`,
`open` (a YouTube URL in the YouTube app, or a catalog `intent:` deep link: WATCHING.md),
`launch`, `sidebar`, `overlay`, `now`, `media`, `input`, `key`, `sleep` (the Pi's: POWER.md § Sleep timer). Verified end to end on 2026-10-30 for `brightness` (read) and
`now` (app, title, position); the rest run the same code paths `bin/tv` proved, from inside the app.

`now` reads media sessions, which Android allows to an enabled notification listener:

    adb shell cmd notification allow_listener com.jaredgantt.tvcompanion/.Listener

`open` and `launch` hold a screen wake lock for 30 s around starting the app (`holdAwake`). Starting
an app is not user activity, and on an HDMI input the TV keeps the screen on itself, so the 10-minute
screensaver timer can be long expired: without the lock the screensaver starts about two seconds after
the app does, pausing YouTube before the video plays, and pressing a key afterwards uncovers YouTube's
home screen. The lock is released with `ON_AFTER_RELEASE`, which restarts the idle timer.

**Choosing what to put on** is the `youtube` and `catalog` tools: WATCHING.md.

`media` drives whatever is playing through its media session, the same grant `now` uses: `play`,
`pause`, `toggle`, `stop`, `next`, `previous`, and `forward` / `back` / `seek` by `seconds`. Skips are a
`seekTo` from the live position (the session's last reported position plus the time elapsed since),
because `fastForward` and `rewind` mean a different jump in every app. It answers with `now`. With
nothing playing or paused it answers `nothing is playing`.

`input` (`hdmi` 1-4) opens TCL's passthrough input the way `bin/tv input` does. The PlayStation 5 is
HDMI 2 (CEC reports it on port 2). It wakes the TV first, like `open`.

## What it cannot do, and why

An app cannot turn the TV off or on (`goToSleep` is a system permission), so `power` is the Pi's
(POWER.md). An app cannot inject key presses into another app either, so `key` (back, home, the
arrows, select) is the Pi's too: data-layer `tvHome.ts` runs `bin/tv key` over adb and answers the
row, and the hub leaves `power`, `key` and `sleep` rows alone. The Pi also presses OK on Prime Video's "Who's
watching?" picker after an `open` or `launch` into Prime (WATCHING.md § Prime's profile picker). While the TV is off the app is not running:
`open`, `launch` and `input` wait for the Pi to wake it, `key` and setting a `sleep` answer `the TV is off`, and anything
else answers `the TV is off (its app is not connected)`. Screenshots stay with `bin/tv` over adb, so
the orchestrator hands them to a session, which has the `tv` skill.
