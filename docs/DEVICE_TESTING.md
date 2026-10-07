# Magic V5 compatibility test

Target: MagicOS 10.0.0.168 EU. The user confirmed that the v0.2 export was absent while the supplied Peafowl theme worked. v0.3 corrects the profile and supports that larger working file. Repaired/new outputs still need physical device validation. Expected APK: `dist/Magic-Themer-0.3.0-debug.apk`.

The named APK and personal `.hnt` files under `dist/` are local-only artifacts ignored by Git; a repository clone does not contain them. Build the APK using the README instructions and supply your own local theme files for the following comparisons.

## Check the repaired file first

Copy `dist/My Magic Icons Fixed.hnt` into the same folder and use the same import route that accepts `chicv6.hnt`. Look for **My Magic Icons Fixed**. Select **Customize → Icons** and check several changed apps. This personal copy includes the working base's wallpaper/system modules, so selecting icons separately avoids applying the entire base theme. The two supplied source files remain unchanged.

Compare that result with a new v0.3 export. If the working-base copy is accepted but the minimal export is absent, preserve both files: additional modules or packaging details still need investigation.

To generate a personal working-base copy from files on another machine, use `python qa/compare_user_themes.py --working path/to/working.hnt --failed path/to/export.hnt --output qa/output/local/repaired.hnt --report-dir qa/output/local`. Both inputs remain unchanged; choose a new output path and use that file in the phone check. These files are not bundled with the APK or committed to the repository.

## First test

1. Keep a note of your current theme so you can restore it using HONOR Themes.
2. Install the development APK and one icon pack you already own. Pick a pack with obvious static icons for Settings and several everyday apps.
3. Open Magic Themer, select the pack and record its name, mapped-app count, artwork count and missing-resource warnings. Check that pack images, app icons and gallery thumbnails match the selected pack.
4. Full mapped-pack export is the default and includes apps not installed here. In **Theme**, turn **Whole icon pack** off for this small first test. Export **HONOR (.hnt)** with a short ASCII title, such as `Magic Test 01`.
5. Save into the shared internal-storage `HONOR/Themes` folder. If the picker cannot reach that folder, save to Downloads and use the phone's Files app to copy it there.
6. Open HONOR Themes → Me → My downloads. Look for `Magic Test 01`. Reopen Themes, then reboot if the theme is absent.
7. Use Customize → Icons to apply the exported icons. Inspect Settings and at least three pack-mapped apps. Confirm your wallpaper and lock screen behave as expected.
8. Check both outer and inner displays, app drawer if enabled, and persistence after reboot.
9. Restore your prior icons after the test. If a corrected `.hnt` is absent, record that result and compare it with the repaired working-base file before changing extensions.

Menu wording may differ. Do not use a private Android/data path as proof that normal app storage access works. If the local import route is unavailable, record what the current Themes app actually offers.

## Full pack and visual picker

After one small export works:

1. Restore full mapped-pack mode and export again. Compare its icon count with the installed-app-only export. It should include mapped uninstalled apps, with one icon per package.
2. Browse and search **Pack icons**. Select a drawable.xml alternative for an installed app and check the selected image in **Apps** before exporting.
3. Apply that export in HONOR Themes and confirm the manually assigned icon changes. Extra gallery artwork should not appear as fictitious app icons.
4. Rotate, fold/unfold, switch between light/dark mode and scroll the visual lists. Check readability, selection persistence and that recycled thumbnails never display another app's icon.

Missing drawable resources should be reported and skipped. A gallery entry without a package mapping is artwork available for assignment, not an automatically exported theme entry.

In **Apps**, check **Needs icon** and its count. An unmapped or missing-resource app should appear, with its original icon and a status badge. Tap it, review **Suggested icons** and choose one thumbnail. The app should leave Needs icon and appear in **Customized**; **Reset** should restore its prior state. Verify the assignment survives restart and is used after export/application. These filters describe pending theme coverage, not a scan of the theme already applied by HONOR Themes.

## Edit a known-working local theme

1. Keep the original `.hnt` file and current theme available for restoration. Use a local theme you already own.
2. Tap **Open .hnt**. Record the theme's title, designer, description, static icon count and any import warnings. Selecting a pack should leave its existing icons unchanged until you assign an icon or explicitly apply the pack.
3. Change metadata and a small number of app icons. Tap **Save theme copy** and save with a distinct name. Verify that the original file remains unchanged.
4. Import the copy through the same HONOR Themes route and inspect its listing and selected app icons.
5. If the original contains wallpaper, font, system or lock-screen modules, check that their behavior matches the original. Their contents are preserved by the editor, but acceptance of the rewritten archive remains a device question.
6. After the small edit succeeds, test **Apply whole pack to theme** with **Replace existing icon set** off. Original icons for uncovered apps should remain. Test replacement with that switch on separately, and record which original static icons are removed.

The editor requires ordinary ZIP archives with supported description.xml metadata and an extensionless icons ZIP. It edits metadata and static package PNGs; it cannot design new system or lock-screen modules. Preserve both input and output when reporting a rejection.

## Evidence to return

- Exact OS build/region and HONOR Themes version.
- Icon pack package/name and exported extension.
- Whether the flow created a new theme or edited an imported copy; export mode and any manual icon assignments.
- Where the file was saved; whether Themes listed it.
- Whether Customize → Icons accepted it, and which mapped apps changed.
- Any error text, automatic reversion or difference between folded/unfolded screens.
- The generated theme file and a known-working icon-only theme if you can obtain one legitimately. Do not share a paid pack's artwork without its creator's permission.

The next implementation change should follow this evidence: importer issue, metadata issue, archive layout issue or icon filename issue. Changing several parts at once would make a successful result harder to explain.

## Known prototype limits

New icon exports contain static mapped or manually assigned icons. Pack fallback masks/backgrounds, animated clocks and changing calendar dates are not recreated. Calendar snapshots need regeneration when the date changes. One exported icon is available per package; cloned apps and work profiles need separate investigation. Existing unrelated modules are preserved when editing a supported theme, but are not authored or interpreted by this app.

The APK uses a local debug signature. A public release requires release signing and target-device validation. Check [VALIDATION.md](VALIDATION.md) for what was actually tested in this workspace.
