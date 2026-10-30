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
  waits up to 15 s. Its description is the orchestrator's whole instruction set for the TV: the
  orchestrator loads no skills, so anything it must know about the TV goes there.
- **The hub** (status-hub `server.js` § THE BEDROOM TV) pushes each insert to every connected TV app
  and, with none connected, answers the row at once with "the TV is not connected". A TV that connects
  late is replayed only commands under 30 s old.
- **`TvLink`** (the companion app) holds the stream open, reconnecting with backoff from 1 s doubling
  to a minute, and authenticates with the hub's device token (the one `bin/tv sidebar on` pushes).
  Started at boot by `BootReceiver`, and by `bin/tv link`.

## What it can do

`brightness`, `contrast`, `night`, `volume`, `open` (a URL in an app — a specific YouTube video),
`launch`, `sidebar`, `overlay`, `now`. Verified end to end on 2026-10-30 for `brightness` (read) and
`now` (app, title, position); the rest run the same code paths `bin/tv` proved, from inside the app.

`now` reads media sessions, which Android allows to an enabled notification listener:

    adb shell cmd notification allow_listener com.jaredgantt.tvcompanion/.Listener

## What it cannot do, and why

An app cannot turn the TV off or on (`goToSleep` is a system permission), switch HDMI inputs
(unverified, untried), press keys inside another app, or take a screenshot. Those stay with `bin/tv`
over adb, so the orchestrator hands them to a session, which has the `tv` skill.
