# Changelog

## 0.7.0

First public release, under the GNU GPL v3.

- **Original icons** in **Apps** (and in **Settings**) shows each app's own icon instead of the icon from the theme applied on the phone, so you can see which apps a custom theme already covers. HONOR swaps in the theme icon when an app's icon drawable is loaded; Magic Themer reads the launcher icon resource directly instead. Confirmed on a Magic V5 with a custom theme applied.
- The toolbar and pack card scroll away with the lists and the tabs stay pinned, so far more apps fit on screen. App rows are more compact: the status badge and package name share a line. **Choose pack** and **Open .hnt** appear until a source is chosen; afterwards the pack card changes the pack and **Open theme file** is in the menu.
- New **Settings**: show original icons, a designer credit for new themes, and version, license and source information.
- The **Theme** tab is grouped into **Details**, **Icons** and **File**, with shorter explanations.
- New themes are named after the selected pack until you type your own name; switching packs renames them too.
- **Designer** defaults to the credit set in **Settings**, else the phone's name from Settings → About phone, else its model. Imported themes keep their designer.
- Release builds are shrunk with R8 (about 1.6 MB) and omit Google's dependency metadata block.

## 0.6.0

- Optimized launcher icon with a dedicated monochrome version; the original SVG is kept in `artwork/launcher/`.
- New exports use the tested wallpaper-free layout. Imported theme modules are still preserved.
- When the storage provider supports renaming, saves write under a temporary name, verify the bytes, then rename to the chosen `.hnt`/`.hwt` name and verify again. Providers without rename support keep the verified direct save.
- The save dialog and apply guide explain the Themes force-stop workaround. **App info** opens the system screen to force-stop Themes.

## 0.5.0

- Exports gained folder records, real timestamps and a member order matching known-working themes. A blank description is omitted.
- Choosing a pack replaces an untouched default theme name with the pack's name.
- Apps without matching artwork get suggestions for their kind (music, video, games, transport, maps, photos, chat, browser, mail, money, shopping, food, news and reading, notes, files, health, weather, travel and tools). The kind comes from the app's name and package words, its declared Android category, and whether it plays video or audio. Name and mapping matches always rank first.
- **HONOR can't theme** marks builds whose icon resources are named differently from their installed package (for example DroidDeck's renamed variants); HONOR never applies a theme icon to them.
- Search ranks best matches first and tolerates a small typo, joined or split words and initials. Artwork is also found by the apps a pack maps it to. Wide and unfolded screens show more columns.

## 0.4.0

- Saving an imported theme without edits produces a byte-identical copy. Metadata edits replace only the requested XML values.
- Unchanged ZIP members keep their original compressed bytes, headers, attributes and comments. Changed members carry complete local CRC and size fields without data descriptors.
- Editing icons in an imported theme keeps the author's previews.
- Saves are read back and checked against the working export's length and SHA-256 before success is reported. The save dialog shows the actual filename and warns about an unexpected extension.

## 0.3.0

- New exports use `HwTheme` version 12 metadata, an empty unlock declaration and 720×1280 previews.
- **All / Needs icon / Customized** filters with counts and status badges.
- Ranked offline icon suggestions with thumbnails and match reasons.
- Imports support larger themes and HONOR's flat launcher aliases such as `:CAMERA.png`.
- **Repair theme compatibility** for older minimal Magic Themer exports.
- Active edits and pending saves use private working storage, so Android cache eviction cannot discard them.

## 0.2.0 and earlier

- Icon pack discovery through common launcher intents; appfilter and drawable.xml parsing from XML, raw resources or assets.
- Bitmap, vector and adaptive drawables rendered as 192 px PNGs.
- `.hnt` and `.hwt` export through Android's file picker; editing of ordinary existing theme ZIPs as a new copy.
