# google-tv

Programmatic control of the **Bedroom TV**: a TCL Google TV (model "Smart TV Pro", product G08,
Android TV 14, MediaTek MT9653 chassis). Not the Living Room Vizio, and not the TV the Pi's HDMI
kiosk drives.

Everything goes through **wireless adb** (Developer options → Wireless debugging), which gives a
shell on the TV with the same powers as a developer's cable. The firmware is not modified and the
TV is not rooted; neither is possible (locked bootloader) or needed.

| doc | what it covers |
|---|---|
| [docs/CAPABILITIES.md](docs/CAPABILITIES.md) | what can be controlled, each item marked verified or not, and the limits |
| [docs/SETUP.md](docs/SETUP.md) | pairing a machine, discovery, what breaks the connection |
| [docs/TCL-DISPLAY-SERVICE.md](docs/TCL-DISPLAY-SERVICE.md) | the vendor binder service behind brightness and the rest of the picture menu |
| [docs/IDEAS.md](docs/IDEAS.md) | ideas and the reasoning behind the built ones: session sidebar overlay, orchestrator TV tools, how to answer "who is on screen" |
| [docs/VOICE.md](docs/VOICE.md) | how voice reaches the TV: tv_commands, the hub relay, the app's link |
| [docs/POWER.md](docs/POWER.md) | turning the TV on and off remotely: Network standby, the measured wake methods, the Pi's part |
| [docs/WATCHING.md](docs/WATCHING.md) | finding something to watch and putting it on: YouTube (public and the signed-in account), the cross-service catalog, deep links, the Apple TV box |
| [docs/TODO.md](docs/TODO.md) | every open item: blocked, visible tests still owed, not started, decided against |

## `bin/tv`

One script, stdlib Python, runs the same on the Mac and the Pi (both are paired, and each keeps its
own `~/.config/google-tv/`, docs/SETUP.md).

    bin/tv brightness [0-100]     # TCL "Brightness" = panel backlight; prints the value the TV reports
    bin/tv contrast [0-100]
    bin/tv dynamic-contrast [0-2] # 0 off, 1 low, 2 high
    bin/tv night                  # brightness 0, contrast 60, dynamic contrast off: watching in bed
    bin/tv volume [0-100]
    bin/tv power [on|off]
    bin/tv wake                   # Wake-on-LAN only
    bin/tv now                    # foreground app + what is playing
    bin/tv open <url> [app]       # e.g. a YouTube watch URL, opened in the YouTube app
    bin/tv launch <app>           # youtube, netflix, prime, disney, hulu, max, tubi, appletv, ...
    bin/tv input hdmi1..hdmi4
    bin/tv key <KEY>...           # dpad_up, enter, back, home, media_play_pause, ...
    bin/tv text "<string>"        # types into the focused field
    bin/tv screen                 # on-screen text with tap coordinates; * marks the focused element
    bin/tv screenshot [path]
    bin/tv sidebar on [0-1]|off   # the focused session (or the orchestrator, by voice), see-through, down the right (width as a fraction)
    bin/tv overlay "<text>" [s]   # one line in the top-right corner for s seconds
    bin/tv link                   # restart the companion app's connection to the hub (docs/VOICE.md)
    bin/tv shell <cmd...>

Every setter prints the value read back from the TV afterwards, never the value requested.

The TV is the bedroom TV: anything that lights the screen or makes sound wakes whoever is asleep.
Test visible changes only when that is wanted.
