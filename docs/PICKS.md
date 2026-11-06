# Picks

A screen in the companion app (`Picks` activity, "Picks" in the TV's app list) showing a grid of
thumbnails and titles chosen by a session. Selecting one opens it in the app it belongs to.

    bin/tv picks <file.json>   # replace the list, then open the screen
    bin/tv picks               # open the screen
    bin/tv launch picks        # the same, through the launcher entry

The file is a JSON array; `picks/sample.json` is the shape:

    [{"title": "...", "image": "<thumbnail URL>", "url": "<what to open>", "package": "<app, optional>"}]

- **YouTube**: `url` a watch URL, `package` `com.google.android.youtube.tv`, `image`
  `https://i.ytimg.com/vi/<id>/hqdefault.jpg`. Ids come from the `youtube` tool (WATCHING.md).
- **Netflix, Prime and the rest**: `url` the catalog's `open:` deep link as is (it names its own
  package), `package` omitted. The catalog tool prints no images; JustWatch's GraphQL API
  (`node(id:"ts…") { ... on MovieOrShow { content(country:US, language:"en") { backdrops { backdropUrl } } } }`)
  gives `/backdrop/<n>/{profile}/<slug>.{format}`, served as
  `https://images.justwatch.com/backdrop/<n>/s640/<slug>.jpg`.

Netflix is not signed in on the TV (WATCHING.md), so a Netflix pick stops at its sign-in screen.
Opening a Prime pick from this screen does not get the Pi's press past "Who's watching?", which only
follows a voice `open`.

`bin/tv picks <file>` goes over adb: `PicksReceiver` takes the array base64-encoded (no shell
quoting), refuses anything that is not a JSON array, and stores it as `picks.json` in the app's files
dir. The screen reads that file each time it comes up and loads thumbnails straight from their URLs,
with no cache. Voice has no way to set the list yet.
