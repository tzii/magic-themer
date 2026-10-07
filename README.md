# Magic Themer

A free, offline Android prototype that creates icon themes from installed icon packs and edits supported local theme files. Target: **HONOR Magic V5, MagicOS 10.0.0.168 EU**.

The user confirmed that the v0.2 export was absent from HONOR Themes while `chicv6.hnt` from Peafowl worked on this phone. v0.3 corrects the archive profile from that comparison. The repaired file and new exports still need a phone check; APK/emulator checks cannot establish HONOR Themes acceptance.

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
- Saves manual assignments locally per pack.
- Defaults to the full mapped pack, including apps absent from this phone. Installed-app-only export remains available.
- Creates experimental `.hnt` and `.hwt` theme archives and saves through Android's system file picker.
- Opens supported existing theme ZIPs, edits title/designer/description and static app icons, and saves a new copy while preserving unrelated module contents.
- Opens HONOR Themes and explains the import/apply flow.

The full mapped pack means **one selected icon per app package in appfilter.xml**, including uninstalled apps. Alternative artwork from drawable.xml is available for manual assignments; it does not become an extra theme icon until assigned to an app. A pack with only drawable.xml can be browsed and assigned manually.

No ads, accounts, analytics, network permission, subscriptions or broad storage permissions. Install any purchased icon packs normally from Google Play before using them here. The app does not download or unlock paid packs.

## Try the APK

Build the development APK using the instructions below. The output is `app/build/outputs/apk/debug/app-debug.apk`, signed with your local Android debug key. Generated APKs, checksums and personal theme files in `dist/` are not tracked in Git. See [validation](docs/VALIDATION.md) for tests and Android 16 emulator checks.

The local personal repair, `dist/My Magic Icons Fixed.hnt`, combines all 7,404 package icons from the failed export with the supplied working theme's original modules, aliases and remaining artwork. It is not included in this repository. Original author/designer credits are preserved. Apply **Customize → Icons** in HONOR Themes to select its icons; applying the entire theme also uses its existing wallpaper/system modules. No supplied third-party artwork is bundled with the APK.

1. Install Magic Themer and a compatible icon pack.
2. Tap **Choose pack**. In **Apps**, use **Needs icon** to find gaps, tap an app and choose a suggested icon or search all artwork. Browse **Pack icons** for alternatives. In **Theme**, leave **Whole icon pack** on, or turn it off for an installed-app-only first test.
3. Edit the theme name and keep **HONOR (.hnt)** selected for the first test. Tap **Save theme** and save to internal storage `HONOR/Themes` if that folder is available in the file picker. Otherwise save to Downloads and copy there with the phone's Files app.
4. Open HONOR Themes → Me → My downloads, then Customize → Icons. Select the exported theme's icons.
5. If it is absent, compare with the repaired file and consult [device testing](docs/DEVICE_TESTING.md). Keep the output and record where it was saved.

The user applies the theme in HONOR Themes. No public direct-apply API was located during this research. On some builds the importer may behave differently or be restricted.

## Edit an existing theme

Tap **Open .hnt** and choose a local file you can legitimately use. The editor supports ordinary ZIP themes containing `description.xml` with `HnTheme` or `HwTheme` metadata and an extensionless `icons` ZIP. Change **Theme name**, **Designer** or **Description**, then replace static package PNG icons using an installed pack. **Save theme copy** proposes a separate `Title-edited.hnt` document; the original selected document is not overwritten.

Selecting a pack does not change an imported theme by itself. Assign individual icons in **Apps**, or tap **Apply whole pack to theme** in **Theme**. This merges the pack and keeps original icons for uncovered apps. **Replace existing icon set** is an explicit option to remove the old static package icons when applying a pack. **Use original** removes an app's custom theme icon; **Reset** restores the imported icon or current pack default.

Unchanged member contents, including existing wallpaper, font, system and lock-screen modules, are copied through. The editor cannot author those modules or reproduce their proprietary behavior. ZIP compression and edited metadata formatting may change; a saved copy still needs validation in HONOR Themes. Encrypted, signed or other unsupported theme formats are not promised.

To repair an older minimal export, open it, leave **Repair theme compatibility** enabled in **Theme**, then save a copy. This option is restricted to recognizable older Magic Themer files. Other imported themes retain their existing metadata profile.

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

- MagicOS archive acceptance, automatic discovery and launcher icon rendering need verification on the exact EU phone/build.
- Only explicitly mapped or manually assigned icons are exported. Pack fallback masks, backgrounds and scale recipes are not applied to unmapped apps.
- Package-level HONOR filenames mean one exported icon per app package. Multiple launcher activities cannot all have independent themed icons in this profile.
- Calendars are a snapshot of the current day. Clock hands and launcher-specific monochrome tinting are not dynamic.
- The gallery combines drawable.xml artwork and mapped icons. Drawables absent from both metadata files are not enumerated automatically.
- Work profiles and cloned apps need separate investigation. New wallpapers, fonts, lock screens and system UI modules cannot be authored here; imported modules are preserved.
- Import and export enforce icon, entry and byte limits. Import allows 256 MiB archives, 128 MiB members and 512 MiB aggregate expanded outer/inner data. New rendered icons retain a 64 MiB total PNG budget. Oversized or malformed archives are rejected.
- Source research does not establish redistribution rights for third-party artwork. Keep pack-derived themes for your own use unless the creator allows sharing.

Next milestone: verify the repaired file, corrected new export and edited working-theme copy on the V5. Full system and lock-screen authoring requires separate research after that check.

Research, primary sources and assumptions: [RESEARCH.md](docs/RESEARCH.md).
