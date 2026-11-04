# Turning the TV on and off remotely

Voice can turn the bedroom TV on and off (`bedroom_tv` op `power`). It can also put something on
while the TV is off: `open` and `launch` wake it first. Measured 2026-10-30: from standby to the
video playing took 4.1s.

## How it works

The TV's companion app cannot wake the TV (`PowerManager.wakeUp` needs a signature permission), and it
is not running while the TV is off. The hub runs on Fly and cannot reach the home network. So the
wake comes from the Pi:

1. A `tv_commands` row is inserted (`power`, or `open` / `launch`).
2. **The Pi** (data-layer `tvHome.ts`, inside data-http, `HOME_AGENT=1`) is subscribed to the same
   inserts. It does the whole of `power` with `bin/tv power on|off` and answers the row itself.
   For `open` / `launch` it runs `bin/tv power on` too (harmless when the TV is on). If the TV still
   reports off afterwards, it answers the row `the TV is off and did not wake`, so the caller hears
   that in seconds instead of waiting out its 45s timeout and going to investigate.
3. **The hub** ignores `power` rows. When the TV is not connected, it holds `open` / `launch` rows
   instead of failing them; every other op answers `the TV is off (its app is not connected)`.
4. The screen comes on. `TvLink` gets `ACTION_SCREEN_ON` (`ScreenOn.java`), cuts its reconnect
   backoff short and reconnects. The hub replays unanswered rows from the last 60s (`TV_REPLAY_MS`)
   to the newly connected app, which does the command.

`bin/tv power on` sends Wake-on-LAN once a second for up to 5s until `mWakefulness=Awake`, then falls
back to adb's `KEYCODE_WAKEUP`. One packet is not enough: an `open` on 2026-10-30, after about two hours
in standby, sent a single packet and the TV took 55s to come on. `power off` is adb's `KEYCODE_SLEEP`.

## It depends on Network standby

Settings > System > Power > Standby mode has these options (read from the decompiled `TvSettings.apk`,
`SystemManagerWorkForTROM3.getStandMode`):

| Mode | What it keeps | How it is stored |
|---|---|---|
| Hibernated | nothing ("pausing all activities and services when screen is off") | the default when nothing below is set |
| Doze | maintenance windows for updates | `DOZE_PRO_STATE` |
| Speaker standby | a connected speaker | sound manager |
| **Network standby** | Doze, plus the internet connection and casting requests | `settings put system tcl_sys_network_standby_enabled 1` |
| Screenless | audio with the panel off | secure `KEY_SCREENLESS_SERVICE_VALUE` = 1 |

**It must be Network standby (`tcl_sys_network_standby_enabled=1`).** In any other mode the Wi-Fi is
off in standby and no wake method can reach the TV. `bin/tv power off` checks it before every sleep
and puts it back if it has been changed (saying so on stderr), so a different mode chosen in the TV's
menu lasts only until the next remote power-off; a power-off with the TV's own remote after such a
change still leaves it unreachable. Network standby uses somewhat more power while the TV is off.

## Wake methods, measured

Each test turned the TV off over adb, waited 60s or more in standby, then tried one method
(`tools/wake-probe.py`):

| Method | Woke it | Time to on | Notes |
|---|---|---|---|
| **Wake-on-LAN** to the wlan0 MAC | yes, also from the Pi | 0.6s | no pairing, no session; **the one used** |
| adb `KEYCODE_WAKEUP` | yes | 2.0s | needs the adb pairing alive; adbd still answers in network standby. The fallback |
| Cast (start YouTube's receiver) | yes | 6.8s | slowest, and launches YouTube as a side effect |
| Android TV Remote v2 | not tried | | needs a pairing code on screen; nothing it adds |

The longest standby tested was a few minutes. Whether Wake-on-LAN still works after hours in
standby (the overnight case) is not yet measured: TODO.md.

## Inactivity auto-off

The TV turns itself off after **4 hours with no remote input**, even while video plays, so falling
asleep with it on does not leave it on all night. This is Android's attentive timeout, secure
`attentive_timeout` in ms (14400000), and it is the same setting as the menu's Settings > System >
Power & Energy > Energy saver "turn off" choice (Never / 4 / 6 / 8 hours, read from the decompiled
`TvSettings.apk`). The framework shows a 30s warning first (`mAttentiveWarningDurationConfig`).

`sleep_timeout` (about 12h here) is NOT this: it only counts while nothing holds a wake lock, and a
playing video holds one, so it never fires during playback. `-1` in `attentive_timeout` means never.

Not yet verified: that it fires overnight, and whether adb-driven actions (`open` from voice) count
as input and restart the 4 hours.

## Sleep timer

`bin/tv sleep <minutes>` turns the TV off that many minutes from now (`bedroom_tv` op `sleep`, run by
data-layer `tvHome.ts`); `sleep off` cancels, bare `sleep` prints when it fires or `none`. A new one
replaces the pending one. It is a one-shot transient systemd timer, `bedroom-tv-sleep.timer`, in the
Pi's user manager, so it survives data-http restarting (every `pull-repos` update restarts it); it
does not survive a reboot. systemd only, so the Pi's, not the Mac's. When it fires it runs
`bin/tv sleep fire`, which does `power off` and checks the TV reports off, reporting to the ops
responder as `bedroom-tv-sleep` if it does not. The fire runs whatever `bin/tv` is checked out at that
moment.

The TV has its own sleep timer in TCL's settings (global/system `tcl_app_power_off_time_delay` and
`tcl_app_power_off_time_expire`, `-1`/`0` when unset). Whether writing those arms it, and whether it
counts down during playback, is untested (TODO.md), so the Pi's timer is the one used.

## The app's link in standby

While the TV is off, `TvLink`'s stream to the hub is down, even in Network standby. A `now` sent
while the TV was off answered "not connected". Its retries keep backing off towards a minute, so the
screen-on receiver is what makes a wake fast.
