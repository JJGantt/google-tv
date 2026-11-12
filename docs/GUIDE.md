# TV Guide

A screen in the companion app (the `Guide` activity, "TV Guide" in the TV's app list): titled shelves of
thumbnails, one row each, like a streaming app's home screen. Selecting a card opens it in its app.
Open it with `bin/tv launch guide`, or the data MCP's `tv_guide` `show` (by voice too).

## Where the shelves live

Supabase, so the voice orchestrator on Fly can change them (data-layer 00171, `tvGuide.ts`):

- `tv_shelves`: `id` (`sh_…`), `title`, `position`.
- `tv_shelf_items`: `id` (`pk_…`), `shelf_id`, `position`, `title`, `url`, `package`, `image`, `note`
  (00175), `description` (00180).
- `tv_guide_history` (00172, renamed in 00174): the whole screen as it stood after every change, for `history`,
  `history_view` and `restore`. Never pruned.

Shelves are not hard-coded: any number, each with its own title. The data MCP's `tv_guide` tool is
the only writer. Its description is the orchestrator's whole instruction set: change only what is
asked for, `view` to see everything, add / remove / replace / move items, create / rename / move /
delete shelves, and the history ops. Item `title` is the plain title; the service is shown by the
badge, never in the title.

An item's `note` is a short annotation (a score, a runtime, a tag), set for any number of items in one
`annotate` call or on items in `add` / `replace`. The screen draws it as cyan text on an opaque black pill at the
bottom left of the thumbnail, opposite the badge. Any length is stored; a long note wraps upward to four lines, which
reach the badge, and a longer one ends in an ellipsis. No note draws nothing.

An item's `description` is a short plot summary, set on items in `add` / `replace` or for any number of
items in one `describe` call. The tool's instructions have the orchestrator write one for every item it adds
(a short paragraph condensed from the catalog or YouTube summary), while a note is only ever added on request.
The screen draws it in grey under the title with no line limit: each card is as tall as its text, and a row
is as tall as its tallest card, with cards aligned to the top.

What an item opens is worked out from its URL (`resolveItem`): an `intent:` deep link from the
`catalog` tool names its own package; a YouTube watch URL opens in the YouTube app, and its thumbnail
is derived (`i.ytimg.com/vi/<id>/hqdefault.jpg`). A catalog title's thumbnail is the `Image:` line
`catalog title` prints (a JustWatch backdrop).

## How the screen gets them

The screen reads the hub's `GET /api/tv/guide` (status-hub `server/routes/tv.js`) with the hub token
each time it comes to the front. After every write the tool inserts a `guide_refresh` tv_command,
which TvLink passes to the screen if it is in front (`Guide.shown`), so an open screen redraws within
a second or two and keeps focus on the same card. `guide_refresh` never brings the screen up.

Thumbnails and badges load straight from their URLs with no disk cache. The badge is the service's
site icon from Google's favicon service (`SITES` in `Guide.java` maps package to site), with a white
rim traced around its shape. A package missing from `SITES` gets no badge. The apps' own launcher
icons are full square tiles and too heavy for a badge.

## On the phone

The hub's phone page has the same screen as its right-hand pane (status-hub `cards/guide.js`): the
same shelves, badges and notes, redrawn when the tool's `guide_refresh` reaches the hub, which passes
it to the phone as a `guide` event on the session stream. A tap plays the item on the TV.

## Limits

Netflix is not signed in on the TV (WATCHING.md), so a Netflix item stops at its sign-in screen.
Opening a Prime item from the TV Guide does not get the Pi's press past "Who's watching?", which only
follows a voice `open` or `launch`.
