# Magic Themer

A free, offline Android prototype that creates icon themes from installed icon packs and edits supported local theme files. Target: **HONOR Magic V5, MagicOS 10.0.0.168 EU**.

On 25 October, the user confirmed that the v0.5 export **My Magic Icons** and all three import controls appeared in HONOR Themes after restarting the phone. Use that v0.5 export as the working baseline. Test A also appeared without wallpaper or a full base theme, so those modules are not required for listing in this test. Immediate discovery, actual launcher rendering and earlier edited-theme repairs remain separate checks; see [device results](docs/RESEARCH.md#25-october-follow-up-all-controls-listed-after-restart).

## What v0.5 adds

- New exports include a generated wallpaper module, `preview/`, `unlock/` and `wallpaper/` folder records, real timestamps and Peafowl's member order. A blank description is omitted. The v0.5 layout is now listed on the V5 after restart; the wallpaper-free A control is also listed. Keep the current exporter while checking icon application. Choosing **Customize → Icons** applies just the icons.
- Choosing a pack replaces an untouched default theme name with the pack's name. Existing sessions retain their saved title; the user's v0.5 export named `My Magic Icons` is listed successfully, so that name alone is not a discovery blocker.
- Apps with no matching artwork get suggestions for their kind: music, video, games, transport, maps, photos, chat, browser, mail, money, shopping, food, news and reading, notes, files, health, weather, travel and tools. The kind comes from the app's name and package words (including fused names such as Busitalia), its declared Android category, and whether it plays video or audio. Generic artwork such as `music` or `bus` comes first, then icons of well-known apps of that kind. Name and mapping matches always rank above these.

## What v0.4 adds

- Saving a working imported theme without edits produces a byte-identical copy when legacy conversion is off. Metadata edits replace only requested XML values; a blank missing description is not added.
- Unchanged ZIP members retain their original compressed bytes, headers, attributes and comments. Changed and new members carry complete local CRC/size fields without streaming data descriptors.
- Editing icons in an imported theme keeps the author's previews. The six additions in the supplied edited `chicv6` are retained in the new personal repair.
- The minimal legacy conversion also recognizes copies with customized designer credit, and normalizes legacy ZIP headers in both layers.
- System file-picker saves are read back and checked against the working export's length and SHA-256 before success is reported. The save dialog shows the actual filename and warns about an unexpected extension.

## What v0.3 adds

- New exports use the working file's `HwTheme` version 12 metadata, empty unlock declaration and 720×1280 preview images.
- **Needs icon** shows apps without usable theme artwork. **All / Needs icon / Customized** filters include counts and separate status badges.
- Missing apps offer ranked, offline icon suggestions with thumbnails and match reasons. Tap one to assign it; the full searchable catalogue remains available.
- Imports support larger working themes and HONOR's flat launcher aliases such as `:CAMERA.png`, while retaining archive validation and file-backed processing.
- Older minimal Magic Themer exports offer **Repair theme compatibility**, enabled by default, when saved as a copy.
- Active edits and pending saves use private working storage, so Android cache eviction cannot discard a large theme during editing. Existing cached sessions migrate when their files remain available.

Existing features:

- Finds installed icon packs through common launcher intent signatures.
- Reads appfilter mappings and drawable.xml artwork catalogues from XML resources, raw resources or assets.
- Renders bitmap, vector and adaptive drawables as transparent 192 px PNGs.
- Uses Material 3 components with light/dark themes and visual pack, app and icon browsing.
- Reports installed-app coverage and lets you reassign app icons from a searchable artwork gallery.
- Search ranks the best matches first and tolerates a small typo, joined or split words ("droid deck") and initials ("yt"). Artwork is also found by the apps a pack maps it to. While typing, the header and save bar make room for results, and dragging the results hides the keyboard. Wide and unfolded screens show more columns.
- Saves manual assignments locally per pack.
- Defaults to the full mapped pack, including apps absent from this phone. Installed-app-only export remains available.
- Creates experimental `.hnt` and `.hwt` theme archives and saves through Android's system file picker.
- Opens supported existing theme ZIPs, edits title/designer/description and static app icons, and saves a new copy while preserving unrelated module contents.
- Opens HONOR Themes and explains the import/apply flow.

The full mapped pack means **one selected icon per app package in appfilter.xml**, including uninstalled apps. Alternative artwork from drawable.xml is available for manual assignments; it does not become an extra theme icon until assigned to an app. A pack with only drawable.xml can be browsed and assigned manually.

No ads, accounts, analytics, network permission, subscriptions or broad storage permissions. Install any purchased icon packs normally from Google Play before using them here. The app does not download or unlock paid packs.

## Try the APK

Build the development APK using the instructions below. The output is `app/build/outputs/apk/debug/app-debug.apk`, signed with your local Android debug key. Generated APKs, checksums and personal theme files in `dist/` are not tracked in Git. See [validation](docs/VALIDATION.md) for tests and Android 16 emulator checks.

Local-only diagnostics are `dist/Chic V6 Header Test.hnt` (the untouched working theme with only its matching titles changed) and `dist/Chic V6 Fixed.hnt` (that working base with the six app-icon additions from the supplied edited file). They are not included in this repository. Original author/designer credits and previews are preserved. Apply **Customize → Icons** in HONOR Themes to select the repaired icons; applying the entire theme also uses the base's wallpaper/system modules. No supplied third-party artwork is bundled with the APK.

1. Install Magic Themer and a compatible icon pack.
2. Tap **Choose pack**. In **Apps**, use **Needs icon** to find gaps, tap an app and choose a suggested icon or search all artwork. Browse **Pack icons** for alternatives. In **Theme**, leave **Whole icon pack** on, or turn it off for an installed-app-only first test.
3. Edit the theme name and keep **HONOR (.hnt)** selected for the first test. Tap **Save theme** and save to internal storage `HONOR/Themes` if that folder is available in the file picker. Otherwise save to Downloads and copy there with the phone's Files app.
4. Open HONOR Themes → Me → My downloads, then Customize → Icons. Select the exported theme's icons.
5. If it is absent, close and reopen HONOR Themes, then restart the phone and check again. The user's v0.5 export and all three controls appeared after a restart. Keep the same file and record where it was saved; consult [device testing](docs/DEVICE_TESTING.md) if it is still missing.

The user applies the theme in HONOR Themes. No public direct-apply API was located during this research. On some builds the importer may behave differently or be restricted.

## Edit an existing theme

Tap **Open .hnt** and choose a local file you can legitimately use. The editor supports ordinary ZIP themes containing `description.xml` with `HnTheme` or `HwTheme` metadata and an extensionless `icons` ZIP. Change **Theme name**, **Designer** or **Description**, then replace static package PNG icons using an installed pack. **Save theme copy** proposes a separate `Title-edited.hnt` document; the original selected document is not overwritten.

Selecting a pack does not change an imported theme by itself. Assign individual icons in **Apps**, or tap **Apply whole pack to theme** in **Theme**. This merges the pack and keeps original icons for uncovered apps. **Replace existing icon set** is an explicit option to remove the old static package icons when applying a pack. **Use original** removes an app's custom theme icon; **Reset** restores the imported icon or current pack default. For a separately identifiable copy in HONOR Themes, change **Theme name** before saving; the proposed `-edited` filename does not change the internal theme title.

Unchanged member contents, including existing wallpaper, font, system and lock-screen modules, are copied through. The editor cannot author those modules or reproduce their proprietary behavior. ZIP compression and edited metadata formatting may change; a saved copy still needs validation in HONOR Themes. Encrypted, signed or other unsupported theme formats are not promised.

To convert an older minimal export, open it, leave **Repair theme compatibility** enabled in **Theme**, then save a copy. This option is restricted to the exact supported minimal legacy layout, including copies with edited designer credit. Other imported themes retain their existing metadata profile. When recovering edits from a rejected rewritten full theme, start with its untouched working original; raw preservation cannot restore packaging already lost in an earlier save.

## Build

Open the project folder in Android Studio. Install Android SDK Platform 36 and Build Tools 36.0.0. Use a compatible JDK for Gradle 9.4; JDK 17–26 are supported by that Gradle release. The implementation uses AGP 9.1.0's built-in Kotlin support with a documented Kotlin 2.3.21 runtime override, Material Components 1.13.0 and RecyclerView 1.4.0. See [validation](docs/VALIDATION.md) for build results. This Kotlin/toolchain combination extends beyond JetBrains' fully tested compatibility matrix.

Windows PowerShell, with Android Studio installed in its standard location:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. SDK paths in `local.properties` are machine-specific and ignored. Wrapper distribution and JAR hashes were checked against [Gradle's published checksums](https://gradle.org/release-checksums/).

The separate `:qa:iconpack` module is an original synthetic icon pack for emulator tests. It is never bundled with the main APK. Build it with `:qa:iconpack:assembleDebug`; see its [README](qa/iconpack/README.md).

## Limits and next milestones

- Listing of the v0.5 export and A/B/C controls is confirmed on the target V5 after restart. Immediate discovery, actual launcher icon rendering and earlier edited-theme repairs still need verification.
- HONOR themes an app only when its icon's resource package matches its installed package. Builds renamed after compilation, such as DroidDeck's AnTuTu, PUBG and Ludashi variants, keep their own icon under any theme. **Apps** marks them **HONOR can't theme**; see [research](docs/RESEARCH.md#8-october-icons-the-launcher-leaves-unthemed).
- Only explicitly mapped or manually assigned icons are exported. Pack fallback masks, backgrounds and scale recipes are not applied to unmapped apps.
- Package-level HONOR filenames mean one exported icon per app package. Multiple launcher activities cannot all have independent themed icons in this profile.
- Calendars are a snapshot of the current day. Clock hands and launcher-specific monochrome tinting are not dynamic.
- The gallery combines drawable.xml artwork and mapped icons. Drawables absent from both metadata files are not enumerated automatically.
- Work profiles and cloned apps need separate investigation. New wallpapers, fonts, lock screens and system UI modules cannot be authored here; imported modules are preserved.
- Import and export enforce icon, entry and byte limits. Import allows 256 MiB archives, 128 MiB members and 512 MiB aggregate expanded outer/inner data. New rendered icons retain a 64 MiB total PNG budget. Editing supports classic single-disk ZIP framing; ZIP64, encrypted, signed, prefixed or trailing-data archives are rejected when saving. Oversized or malformed archives are rejected.
- Source research does not establish redistribution rights for third-party artwork. Keep pack-derived themes for your own use unless the creator allows sharing.

Next milestone: use the v0.5 **My Magic Icons** export, select it through **Customize → Icons**, and verify mapped and manually assigned icons on both V5 displays. Preserve A/B/C and the Peafowl original locally as diagnostic references; they need not remain on the phone once application is confirmed. Test A is a candidate for simplifying the exporter later, after its icon application is checked. Full system and lock-screen authoring requires separate research.

Research, primary sources and assumptions: [RESEARCH.md](docs/RESEARCH.md).
