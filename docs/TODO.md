# Open work

Everything raised and not yet done, so none of it is lost. Designs and feasibility notes for the
bigger items are in [IDEAS.md](IDEAS.md); what is already proven is in
[CAPABILITIES.md](CAPABILITIES.md).

## Blocked on the owner

- **Which streaming services are subscribed**, to set app_settings `streaming_services` (WATCHING.md §
  The catalog). Until then the catalog marks nothing as owned and `popular` / `added` need `services`.
- **Signing in to Netflix and Disney+ on the TV's own apps** (or deciding those are watched on the
  Apple TV box). Their links stop at a sign-in sheet today. Credentials are entered by the owner.
- **Apple TV's Play press**, verified by watching one Apple TV `open` now that Apple TV+ is signed in
  (WATCHING.md § Apple TV's title page).
- **The Apple TV box's pairing PIN**, read off the screen once, so pyatv can control it
  (WATCHING.md § The Apple TV box).
- **The Pi browser signed in to Netflix (and other services)**, so their watch history can be read
  the way YouTube's is. One-time, over VNC.

## Visible tests (need the TV on and someone awake in the bedroom)

- TCL's own sleep timer (`tcl_app_power_off_time_delay` / `_expire`, POWER.md § Sleep timer): whether
  writing it arms it and whether it counts during playback. If both, it could replace the Pi's timer.
- An overlay over the Apple TV (HDMI 1) picture: `OverlayService` is built and permitted, never shown.
- **Wake after a long standby** (POWER.md): Wake-on-LAN is measured after minutes in standby, not
  hours. The first real morning use will show it; if it fails, the adb fallback in `bin/tv power on`
  and the voice answer will say so.
- **Voice, spoken**: "put X on the TV" with the TV off, "turn the TV off", and a catalog pick, said
  to the voice assistant rather than run by script.
- `bin/tv input hdmi1`: switches to a TCL input (foreground `com.tcl.tv`); that it is HDMI 1 / the
  Apple TV is not confirmed, because screenshots of an HDMI input come back empty.
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
- **TV Guide: open a title's details page, not playback, and show the release year** (asked for, then put
  on hold). Measured on the TV: a Prime `watch.amazon.com/detail?asin=<ASIN>&territory=US` link lands
  on the title page with Prime already running, while the catalog's `watch?asin=…&time=0` link
  restarts Prime into the profile picker and plays. Apple TV movie links already stop on the title
  page; it is the Pi's follow-up to the phone pane's `/api/tv/guide/open` (tvHome.ts `inspect` with
  `play: true`) that presses Play, and a guide open would need to skip it. Unverified because those
  apps are signed out: Netflix `/watch/<id>` → `/title/<id>` (screenshots are blank in Netflix),
  Disney+ `/play/<id>` → `/browse/entity-<id>`. `tv.apple.com/us/show/<id>` with no slug did not
  open Apple TV. Hulu's catalog link is a `PLAY_CONTENT` intent and Max's a `/video/watch/` URL, with
  no details form derivable from either. The year is in catalog `title`'s first line but is not stored
  on `tv_shelf_items`.
- **Voice: inputs.** Power is done (POWER.md); switching inputs can ride the same Pi-side path
  (`tvHome.ts` running `bin/tv input`).

## Watching (WATCHING.md)

- **Own recommendations**: a taste profile from YouTube history (what is finished vs abandoned,
  `watched N%`), subscriptions and channels, used to rank the `home` feed, `subscription_feed` and
  catalog picks instead of taking YouTube's order. Today a model does this ad hoc from the tools.
- **The Apple TV box** under control with pyatv once paired: open titles there, read what is
  playing, Up Next if it is reachable.
- **Netflix / Prime watch history** through the Pi browser, the way YouTube's is read, once it is
  signed in to each.
- **Hulu, Max and other deep links** on the TV, untested.
- **Search history and comments** on YouTube: not built; nothing has asked for them yet.
- **Accessibility service** in the companion app, for acting inside other apps without adb.
- **Screensaver** on this TV (DreamService showing the wall; the full-screen wall itself is `Hub`, CAPABILITIES.md).
- **The rest of the picture menu** through `tcl_tv_display`: picture mode, colour, HDR,
  blue-light level. Codes are in TCL-DISPLAY-SERVICE.md; brightness, contrast and dynamic
  contrast are wired (`bin/tv night` sets all three). Contrast can go below 60 if 60 is still too bright at night.

## Decided against

- **Shrinking another app to make room**: no picture-in-picture or freeform windows on this TV.
- **"Screen off, sound on"** (TCL's Audio only quick setting): the picture stays on.
- **Auto TV-off at sleep onset from the watch**: status-hub docs/TODO.md § Dropped.
