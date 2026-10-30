# Open work

Everything raised and not yet done, so none of it is lost. Designs and feasibility notes for the
bigger items are in [IDEAS.md](IDEAS.md); what is already proven is in
[CAPABILITIES.md](CAPABILITIES.md).

## Blocked on Jared

- **Go on orchestrator TV control** (IDEAS.md): TV tools in the voice worker's tool set, reaching
  the TV through Supabase and the companion app rather than the Pi. Proposed 2026-10-30, no answer yet.

## Visible tests (need the TV on and someone awake in the bedroom)

- An overlay over the Apple TV (HDMI 1) picture: `OverlayService` is built and permitted, never shown.
- `bin/tv power off` then `on`: does the adb connection survive standby, and does wake work over adb?
  If not, the Android TV remote protocol (ports 6466/6467, needs a one-time on-screen code) is the
  other way to wake it.
- `bin/tv input hdmi1`: HW15 = HDMI 1 is assumed, not checked.
- A real reboot: does wireless debugging come back by itself, and does `BootReceiver` fire on this
  TCL build (it may hold boot broadcasts back from non-whitelisted apps)?
- `bin/tv launch <app>` and deep links into apps other than YouTube.

## Not started

- **`tv` skill for Claude Code sessions**, pointing at this repo and carrying the on-screen lookup
  method (IDEAS.md § Answering "what / who is on screen").
- **Sidebar, next steps**: a voice/watch toggle for it (today it is `bin/tv sidebar on|off` only);
  check it over the Apple TV input; copy the hub token to the Pi (`~/.config/google-tv/hub-token`)
  so the Pi can show it.
- **Side by side for real**: an app of ours that plays a YouTube embed (or an HDMI input via
  `TvView`, unverified) beside a panel, since nothing can resize another app on this TV.
- **Companion app listening to Supabase** for commands, which the orchestrator tools need.
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
