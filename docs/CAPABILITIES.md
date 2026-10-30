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
| Open a specific YouTube video | `am start -a VIEW -d https://www.youtube.com/watch?v=<id> com.google.android.youtube.tv` | verified twice, the second time through `bin/tv open`: playing, title read back from the media session |
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

## Companion app on the TV (`app/`)

Writing and installing an app needs no developer account; that is only for publishing to the
Play Store. `app/build.sh` builds it with the bare SDK tools (no Gradle), then:

    adb install -r app/build/tvcompanion.apk
    adb shell pm grant com.jaredgantt.tvcompanion android.permission.WRITE_SECURE_SETTINGS
    adb shell appops set com.jaredgantt.tvcompanion SYSTEM_ALERT_WINDOW allow

The two grants persist across reboots and reinstalls with `-r`; a full uninstall loses them.

An app cannot inject key presses into other apps; the adb shell remains the way to drive other
apps' UIs. Everything else checked so far, it can do itself:

| capability | status |
|---|---|
| Call `tcl_tv_display` (read and set brightness) as an ordinary app | verified: `getBacklight` returned 0; `setBacklight(0, 0, 2)` returned 1 (accepted). No hidden-API workaround needed |
| Write `adb_wifi_enabled=1` (WRITE_SECURE_SETTINGS) | verified when the receiver is triggered by hand |
| Re-enable wireless debugging on a real reboot | unverified. Needs a reboot; TCL may also hold back boot broadcasts for apps it has not whitelisted |
| Overlay permission | verified granted (`canDrawOverlays=true`) |
| Overlay visible over an HDMI input | unverified. HDMI video is a hardware plane under Android's UI, and TCL's own settings panel draws over the Apple TV picture, so it is expected to work |

Probes run invisibly: `am broadcast -n com.jaredgantt.tvcompanion/.ProbeReceiver` (results in
`logcat`, tag `TvCompanion`). The overlay is
`am start-foreground-service -n com.jaredgantt.tvcompanion/.OverlayService --es text "…" --ei seconds 5`.

Not built yet:
- **Accessibility service** (enabled once in settings, or from adb via
  `enabled_accessibility_services`) can read and act inside other apps' UIs.
- **Listening to Supabase directly**, so commands reach the TV with no Pi in the path.
- A **screensaver** (DreamService) or a full-screen hub view.

## Limits

- No firmware changes, no root.
- Wireless debugging may switch off after a reboot or a network change; unmeasured.
- The adb port changes each time wireless debugging is enabled; `bin/tv` rediscovers it over mDNS.
