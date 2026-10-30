# Turning the TV on remotely

The goal: a voice "put X on the TV" works when the TV is off. Today it does not. The hub answers
`the TV is not connected` (`tv_commands` row, 2026-10-30 13:54), because the companion app's link to
the hub (`TvLink`) is not running while the TV is in standby. Nothing on the TV can wake it from inside,
since `PowerManager.wakeUp` needs a signature permission. The wake has to come from the network.

## What standby keeps alive depends on TCL's standby mode

Settings > System > Power > Standby mode has these options (read from the decompiled `TvSettings.apk`,
`SystemManagerWorkForTROM3.getStandMode`):

| Mode | What it keeps | How it is stored |
|---|---|---|
| Hibernated | nothing ("pausing all activities and services when screen is off") | the default when nothing below is set |
| Doze | maintenance windows for updates | `DOZE_PRO_STATE` |
| Speaker standby | a connected speaker | sound manager |
| **Network standby** | Doze, plus the internet connection and **casting requests** | `settings put system tcl_sys_network_standby_enabled 1` |
| Screenless | audio with the panel off | secure `KEY_SCREENLESS_SERVICE_VALUE` = 1 |

The settings menu writes `tcl_sys_network_standby_enabled` and nothing else, so setting it over adb is
the same as choosing it in the menu. It was 0 and is now **1** (set 2026-10-30). Network standby uses
somewhat more power while the TV is off. That is the cost of being able to turn it on remotely.

## Ways to wake it, none tested yet

Each needs a machine on the home network, the Pi (always on) or the Mac. The hub on Fly cannot reach
the TV.

| Method | How | Expected |
|---|---|---|
| Cast | launch an app on "Bedroom TV" over the Cast protocol (`pychromecast`). Network standby exists to accept these | most likely to work |
| Wake-on-LAN | magic packet to wlan0 `<tv-mac>` | depends on TCL's Wi-Fi wake patterns |
| adb | `bin/tv power on` (`KEYCODE_WAKEUP`) | only if adbd still answers in standby |
| Android TV Remote v2 | `_androidtvremote2._tcp`, port 6466/6467, power key; needs a one-time pairing code shown on screen | what the Google Home app uses |

`tools/wake-probe.py <method>` turns the TV off, waits, tries one method and reports whether it came
back and how fast. It lights the screen, so run it only when someone in the bedroom has said it is fine.

When the TV comes back on, `TvLink` reconnects by itself. On 2026-10-30 a command sent about a minute
after the TV was switched on by hand went through (13:55:13).

## Once a method works

The wake belongs on the Pi, next to the TV on the home network:

1. The hub, finding the TV not connected, holds the command instead of failing it, and marks it as
   needing a wake.
2. Something on the Pi subscribed to `tv_commands` (realtime, no polling) wakes the TV with the
   method that worked.
3. `TvLink` reconnects, and the hub delivers the held command within its replay window.

Before building this, write down what it costs on the Pi (CLAUDE.md § The Pi is small). It may fit
inside a process that already runs there.
