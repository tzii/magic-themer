# Magic Themer

Turn the icon packs you already have into HONOR / MagicOS icon themes. Free, offline and open source: no ads, accounts, analytics or network access.

HONOR Themes doesn't support third-party icon packs. Magic Themer reads an installed pack, lets you fill gaps and pick alternatives app by app, and saves a `.hnt` theme you apply in HONOR Themes → **Customize → Icons**. It can also edit the icons and details of an existing local theme, always saving a new copy.

Tested on an **HONOR Magic V5, MagicOS 10** (EU). Other HONOR and Huawei devices may work but are untested.

## Features

- Finds installed icon packs that use the common launcher formats (Nova, ADW, Lawnchair, Smart Launcher and others).
- Exports the whole pack, including apps you haven't installed yet, or only your installed apps.
- **Needs icon** lists apps the pack doesn't cover, with suggestions based on the app's name, category and whether it plays music or video.
- **Original icons** shows each app's own icon instead of the one from your current theme, so you can see what a theme really covers.
- Search across apps and artwork that tolerates typos, split words and initials.
- Opens existing ordinary `.hnt` themes to change icons, name, designer and description, keeping their wallpapers, fonts and other modules.
- Flags apps HONOR can't theme (builds renamed after compilation).
- Light and dark themes; adapts to foldables, tablets and landscape.

## Install

Download the APK from [Releases](https://github.com/tzii/magic-themer/releases) and install it. Install any icon packs from Google Play as usual. Magic Themer doesn't download or unlock paid packs.

If upgrading from an earlier debug preview, export any theme you want to keep, then uninstall the preview before installing the public release. The signing keys differ, and uninstalling clears the app's saved assignments and settings. Future public releases use the same permanent signing key.

## Make a theme

1. Tap **Choose pack**.
2. In **Apps**, use **Needs icon** to find gaps. Tap an app to pick a suggested icon or search the pack. **Pack icons** browses all of the pack's artwork, including alternatives.
3. In **Theme**, check the name and designer. **Whole icon pack** includes apps you haven't installed; turn it off to export installed apps only.
4. Tap **Save theme** and save into internal storage → `HONOR/Themes` (or Downloads, then move it there with Files).
5. Open HONOR Themes → **Me → My downloads**, then **Customize → Icons** and pick your theme.

If the theme doesn't show up, tap **App info** in the save dialog, choose **Force stop**, then reopen Themes.

To edit an existing theme, use **Open .hnt** (or **Open theme file** in the menu). Selecting a pack doesn't change an imported theme until you assign icons or tap **Apply whole pack to theme**. Saving always creates a new file; the original is never overwritten.

## Limitations

- Icons are static 192 px PNGs. Dynamic calendars and clocks, pack masks, backgrounds and scaling for unmapped apps aren't reproduced.
- HONOR themes one icon per app package, so apps with several launcher activities share one icon.
- New wallpapers, fonts, lock screens and system UI can't be created; imported ones are preserved.
- Encrypted, signed or otherwise unusual theme formats aren't supported.
- Icon artwork belongs to its creators. Keep themes made from packs for personal use unless the pack's author allows sharing.

## Build

Requirements: Android SDK Platform 36 and Build Tools 36.0.0, and JDK 17 or newer (Android Studio's bundled JBR works).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS or Linux use `./gradlew` with the same tasks. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For a signed release build, copy `keystore.properties.example` to `keystore.properties`, fill it in, and run `:app:assembleRelease`. Without that file the release APK is left unsigned. Keystores and `keystore.properties` are ignored by Git.

See [publishing on GitHub](docs/RELEASING.md) for signing, verification and release steps.

`:qa:iconpack` is an original synthetic icon pack used for emulator tests; it isn't part of the app. See [validation](docs/VALIDATION.md) for the test procedure and [research](docs/RESEARCH.md) for how HONOR's theme format and engine behave.

## License

Magic Themer is free software under the [GNU General Public License v3.0](LICENSE). See the [changelog](CHANGELOG.md) for version history.
