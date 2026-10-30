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
| [docs/IDEAS.md](docs/IDEAS.md) | planned work: session sidebar overlay, orchestrator TV tools, how to answer "who is on screen" |

## `bin/tv`

One script, stdlib Python, runs the same on the Mac and the Pi (both are paired).

    bin/tv brightness [0-100]     # TCL "Brightness" = panel backlight; prints the value the TV reports
    bin/tv volume [0-100]
    bin/tv power [on|off]
    bin/tv now                    # foreground app + what is playing
    bin/tv open <url> [app]       # e.g. a YouTube watch URL, opened in the YouTube app
    bin/tv launch <app>           # youtube, netflix, prime, disney, hulu, max, tubi, appletv, ...
    bin/tv input hdmi1..hdmi4
    bin/tv key <KEY>...           # dpad_up, enter, back, home, media_play_pause, ...
    bin/tv text "<string>"        # types into the focused field
    bin/tv screen                 # on-screen text with tap coordinates; * marks the focused element
    bin/tv screenshot [path]
    bin/tv shell <cmd...>

Every setter prints the value read back from the TV afterwards, never the value requested.

The TV is the bedroom TV: anything that lights the screen or makes sound wakes whoever is asleep.
Test visible changes only when that is wanted.
