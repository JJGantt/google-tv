# Ideas and next work

Each entry says what is known about whether it can be done cleanly; the ones since built say so.

## Session sidebar over whatever is playing

A vertical strip down the right of the screen showing the active session — prompts,
replies, tool lines — drawn by the companion app as an overlay (`SYSTEM_ALERT_WINDOW`, already
granted) hosting a WebView of a narrow hub page. The overlay is non-focusable, so the remote keeps
driving the app underneath.

- **It covers the picture; it cannot push it aside.** This TV has no picture-in-picture and no
  freeform windows (`pm list features` has neither), so nothing can resize another app. Forcing
  freeform on through developer settings is the kind of workaround not worth carrying.
- **Squeezing the video over is possible only when our app is the one playing it**: a full-screen
  app of ours laying out a YouTube embed beside the sidebar, or `android.media.tv.TvView` for an
  HDMI input (whether a third-party app may tune the TCL passthrough inputs is unverified).
  Netflix-style apps cannot be embedded.
- **Built** (CAPABILITIES.md § Sidebar): `/tv-sidebar` on the hub, `SidebarService` in the app.

## TV control for the voice orchestrator

**Built** (VOICE.md). The reasoning below is why it is shaped that way.

The orchestrator loads no skills or CLAUDE.md; its instructions are its system prompt and its tool
descriptions. And voice turns usually run on Fly, which cannot reach the TV on the LAN. So:

- TV tools in the orchestrator's tool set, with the how-to-look-at-the-screen guidance (below) in
  their descriptions.
- Commands reach the TV through Supabase: the companion app subscribes to a commands table and
  executes on the TV itself (no Pi in the path). It can already call `tcl_tv_display`; it cannot
  inject keys into other apps without an accessibility service.
- A `tv` skill for Claude Code sessions, pointing at this repo.

## Answering "what / who is on screen"

One frame of moving video is not evidence. Take several screenshots across 20-30 s, read on-screen
captions, get the playback position (`dumpsys media_session`) and the video's title/description,
and look the episode up (guest lists, credits). People are identified from that context, never from
their faces.
