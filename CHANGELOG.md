# Changelog

## 0.8.0 (unreleased)

- **Streamed exports up to 120 MiB**: streams new whole-pack exports straight to disk (raising the icon budget from 64 MiB to 120 MiB) and deflates icon entries in memory for faster exports on 8,000+ icon packs.
- **Edited theme naming & previous customizations**: prompts with an incremented theme name (`Name 2`, `Name 3`, …) when saving edits to an imported theme whose internal title is unchanged (since MagicOS Themes hides duplicate-title copies), and highlights previously customized apps under **Customized** when reopening a theme.
- **Style unmatched apps like the pack** (experimental, opt-in in **Theme**, needs phone check): parses `<iconback>`, `<iconmask>`, `<iconupon>`, and `<scale>` from `appfilter.xml` and exports HONOR's `icon_mask.png` (alpha-inverted from Android's `DST_OUT` mask), `icon_background_*.png`, and `icon_border.png` so HONOR can style uncovered apps.
- **Live calendar icon** (experimental, opt-in in **Theme**, needs phone check): reconstructs a clean `dynamic_icons/com.android.calendar/calendar_background.png` and extracts the pack's date text color into `com.hihonor.android.launcher` from the pack's `<calendar>` day drawables.
- **Translations & Italian UI**: moved app UI text into Android string resources (`values/strings.xml`) and added an Italian translation (`values-it/strings.xml`). Low-level archive/parser diagnostics remain in English.
- **Share icon requests**: share the **Needs icon** list as a formatted icon request (app names, `ComponentInfo`, Play Store links, and `appfilter.xml` entries) from the **Needs icon** filter or menu.
- **Store metadata & issue templates**: added Fastlane metadata (`fastlane/metadata/android/`) in English and Italian for IzzyOnDroid / Obtainium, plus GitHub issue forms for bug and device compatibility reports.
- **Toolchain & Android 17**: updated to Gradle 9.9.2, Android Gradle Plugin 9.5.3, Kotlin 2.4.21, Material Components 1.14.0, and `compileSdk` / `targetSdk` 37.
- Redrawn, cuter launcher icon: a bigger round head with glossy eyes, blush and a happy smile, and tiny paws hugging the palette card. The tall ribbon on the right is gone. The themed (monochrome) icon, the README icon and the website mascot match.

## 0.7.1

- Android application ID is now `com.tzii.magicthemer`, matching the developer naming used by ThystTV. The visible name remains Magic Themer, and the permanent release signing key is unchanged.
- Android installs this as a separate app from v0.7.0 and earlier (`dev.magicthemer`). Export any themes you want to keep from the old app, then open them in the new app. Saved settings and assignments do not transfer automatically.

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
