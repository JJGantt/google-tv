# Open work

Everything raised and not yet done, so none of it is lost. Designs and feasibility notes for the
bigger items are in [IDEAS.md](IDEAS.md); what is already proven is in
[CAPABILITIES.md](CAPABILITIES.md).

## Blocked on Jared

- A go-ahead to switch the TV off and on a few times for the wake tests (above).

## Visible tests (need the TV on and someone awake in the bedroom)

- An overlay over the Apple TV (HDMI 1) picture: `OverlayService` is built and permitted, never shown.
- **Waking the TV remotely** (POWER.md): network standby is now on; run `tools/wake-probe.py` with
  `cast`, `wol` and `adb` to find which wake works, then build the Pi-side wake so voice can put
  something on with the TV off. Needs Jared's go-ahead, because each run turns the screen off and on.
- `bin/tv input hdmi1`: HW15 = HDMI 1 is assumed, not checked.
- A real reboot: does wireless debugging come back by itself, and does `BootReceiver` fire on this
  TCL build (it may hold boot broadcasts back from non-whitelisted apps)?
- `bin/tv launch <app>` and deep links into apps other than YouTube.

## Not started

- **Sidebar, next steps**: a voice/watch toggle for it (today it is `bin/tv sidebar on|off` only);
  check it over the Apple TV input; copy the hub token to the Pi (`~/.config/google-tv/hub-token`)
  so the Pi can show it.
- **Side by side for real**: an app of ours that plays a YouTube embed (or an HDMI input via
  `TvView`, unverified) beside a panel, since nothing can resize another app on this TV.
- **Voice control, first spoken test** (VOICE.md): only `brightness` (read) and `now` were run end to
  end, by writing rows directly. Say a few commands to the voice assistant; check `volume` set goes
  through the app's AudioManager stepping the way `bin/tv volume` does through keys.
- **Voice: power and inputs.** The app cannot do either. Waking is in POWER.md and comes first.
  Turning off and inputs can ride on the same Pi-side path once it exists.
- **Accessibility service** in the companion app, for acting inside other apps without adb.
- **Screensaver / hub view** on this TV (DreamService or a full-screen page).
- **The rest of the picture menu** through `tcl_tv_display`: picture mode, colour, HDR,
  blue-light level. Codes are in TCL-DISPLAY-SERVICE.md; brightness, contrast and dynamic
  contrast are wired (`bin/tv night` sets all three). Contrast can go below 60 if 60 is still too bright at night.

## Decided against

- **Shrinking another app to make room**: no picture-in-picture or freeform windows on this TV.
- **"Screen off, sound on"** (TCL's Audio only quick setting) for falling asleep: he wants the
  picture kept.
- **Auto TV-off at sleep onset from the watch**: status-hub docs/TODO.md § Dropped.
