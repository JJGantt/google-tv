# Capabilities

**Verified** means it was done against this TV and the result read back. **Unverified** means it
is standard Android behaviour that has not been exercised on this TV yet. Do not promote an item
to verified without doing it.

## Control from outside (adb shell)

| capability | how | status |
|---|---|---|
| Read brightness | `tcl_tv_display` tx 101 → the menu's Settings → Display → Picture → Brightness | verified: read 0, menu showed 0 |
| Set brightness | `tcl_tv_display` tx 100 `(applyType=0, value, act=2)` | verified: set 30 via binder, reopened menu showed 30; saved, not just applied |
| Android `screen_brightness` | `settings put system screen_brightness` | **does nothing to the panel.** Android's own value moves; TCL's Brightness does not. Do not use it |
| Rest of the picture menu | other `tcl_tv_display` calls (picture mode, contrast, colour, HDR, …) | unverified; codes are in TCL-DISPLAY-SERVICE.md |
| Read volume | `cmd media_session volume --stream 3 --get` (range 0–100) | verified: read 5 |
| Set volume | same, `--set N` | unverified |
| Power state | `dumpsys power` → `mWakefulness` | verified (read "Awake") |
| Sleep / wake | `KEYCODE_SLEEP` / `KEYCODE_WAKEUP` | unverified. Whether the adb connection survives standby is the open question |
| Open a specific YouTube video | `am start -a VIEW -d https://www.youtube.com/watch?v=<id> com.google.android.youtube.tv` | verified: video started playing, media session reported its title |
| Deep links into other apps | same pattern with each app's URL | unverified per app |
| Launch an app | `monkey -p <pkg> -c LEANBACK_LAUNCHER 1` | unverified |
| Switch HDMI input | `content://android.media.tv/passthrough/com.tcl.tvinput%2F.TvPassThroughService%2FHW1<n>` | unverified. HW15–HW18 exist; HW15 = HDMI 1 is an assumption |
| What is playing | `dumpsys media_session` | verified: package, PLAYING, title |
| Foreground app | `dumpsys window` → `mCurrentFocus` | verified |
| Remote buttons | `input keyevent` | verified (D-pad, Enter, Back, Settings all drove the menus) |
| Read the screen as text | `uiautomator dump` | verified for Settings and the home screen. The YouTube player exposes no text |
| Screenshot | `screencap -p` | verified for Android UI. DRM video (Netflix etc.) is expected to capture black |
| Pair another machine with no one at the TV | drive Developer options → Wireless debugging → Pair from an already-paired machine and read the code off the screen | verified: that is how the Pi was paired |

Installed apps with a launcher entry: YouTube, YouTube Music, YouTube TV, Netflix, Prime Video,
Disney+, Hulu, Max, Tubi, Apple TV, Instagram, TCL TV+, TCL browser, Play Store.

## A companion app on the TV (planned)

Writing and installing an app needs no developer account; that is only for publishing to the
Play Store. Build on the Mac, `adb install`.

An app has **less** access than the adb shell: it cannot inject key presses into other apps, and
whether it may call `tcl_tv_display` is unverified (TCL may restrict it to system callers). What
an app adds is that it lives on the TV:

- **Keeps adb alive.** Granted `WRITE_SECURE_SETTINGS` once from adb, it can set
  `adb_wifi_enabled=1` itself at boot, so a reboot that turns wireless debugging off heals
  without anyone at the TV. Unverified.
- **Overlays** over whatever is on screen, via `SYSTEM_ALERT_WINDOW` (grantable with
  `appops set`). HDMI video is a hardware plane under Android's UI, and TCL's own settings
  panel draws over the Apple TV picture, so overlays over HDMI are expected to work. Unverified.
- **Accessibility service** (enabled once in settings, or from adb via
  `enabled_accessibility_services`) can read and act inside other apps' UIs.
- **Listening to Supabase directly**, so commands reach the TV with no Pi in the path.
- A **screensaver** (DreamService) or a full-screen hub view.

## Limits

- No firmware changes, no root.
- Wireless debugging may switch off after a reboot or a network change; unmeasured.
- The adb port changes each time wireless debugging is enabled; `bin/tv` rediscovers it over mDNS.
