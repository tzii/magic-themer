# Validation record

Completed 7 October 2026 for **0.3.0**, application ID `dev.magicthemer`. This is a debug-signed prototype. The user confirmed that v0.2 was absent from HONOR Themes and the supplied Peafowl theme worked on **MagicOS 10.0.0.168 EU**. Acceptance of the repaired file and new v0.3 exports on the physical V5 remains unverified.

The earlier [v0.2 validation record](VALIDATION-0.2.md) is retained as history.

The `dist/` APKs, checksums and personal repaired themes described here are local development artifacts. They are ignored by Git and are not included in a repository clone. Supplied theme files and generated QA evidence are also not tracked; build fresh APKs and provide your own local theme inputs when reproducing these checks.

## Build and artifact

Toolchain: Android Studio JBR 25.0.2, Gradle 9.4.0, AGP 9.1.0, Kotlin runtime override 2.3.21, compile/target SDK 36, minimum SDK 26, Material Components 1.13.0 and RecyclerView 1.4.0.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:MAGIC_THEMER_COMPATIBILITY_THEME = "$env:TEMP\chicv6.hnt"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :qa:iconpack:assembleDebug --offline --console=plain
```

- **63 unit tests passed**, zero failures/errors/skips with the supplied-file environment variable: 8 appfilter parser, 6 drawable catalogue, 14 suggestions, 3 mapping selection, 15 archive writer and 15 theme editor tests.
- The optional supplied-theme test opens and saves the actual working file, checks preserved module digests and reopens the result. Without that environment variable, this one test is skipped.
- **Lint passed: zero errors, 47 warnings.** Warnings concern English text construction, resource lookup, pinned SDK/dependencies, backup guidance, adapter notifications and optional Kotlin/style improvements. No quality gates were disabled.
- Final APK signature verified using APK Signature Scheme v2. Package version is `0.3.0`, version code `3`.
- No internet, broad storage or all-package visibility permission. AndroidX declares/uses the private signature-protected `dev.magicthemer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

Artifact: `dist/Magic-Themer-0.3.0-debug.apk` — **6,612,535 bytes**.

SHA-256: `438288dbb0a5109d04a125a208bfecc30278c2713224a0fe1e56bebecf2dad03`. Older APKs and all release checksums remain in `dist/`.

## Supplied archive comparison and personal repair

The rejected file has `HnTheme` version `8.0.0`, four outer entries and 7,404 package icons. The working file has `HwTheme` version `12`, 143 outer entries, an empty unlock declaration, three 720×1280 previews and 7,438 inner entries, including HONOR aliases and dynamic/configuration assets.

The working file's combined expanded outer/inner data is **169,469,602 bytes**, beyond the previous 128 MiB aggregate limit. Its flat colon aliases also failed the old path validator. v0.3 supports those exact alias names, raises file-backed limits and adopts the working metadata/preview profile for new exports. These comparisons identify concrete differences; they do not prove which field made the old file invisible or that a minimal export will be accepted.

`qa/compare_user_themes.py` created `dist/My Magic Icons Fixed.hnt` from the working base. All **7,404** incoming package PNGs match the failed export exactly. **40** remaining inner entries and **141** unrelated outer entries retain their original contents. Title/title-cn change; original author/designer credits remain. Both input SHA-256 values remain unchanged. ZIP CRCs pass.

Repaired artifact: **81,595,730 bytes**, SHA-256 `5c71e089b6e6e913f9e00899b25584bbfad1741ed9a4c50ed9db445b442185b2`. This is a personal user-data transformation, not artwork bundled in the APK. Apply Customize → Icons; applying the entire file also uses the base's wallpaper/system modules.

## Android 16 checks

Used the headless `Medium_Phone_API_36.1` emulator, serial `emulator-5556`, and an original synthetic QA icon pack. The fixture includes vectors, a 768px raster, alternatives, one deliberately missing mapped resource and an absent app mapping.

Conversion/editor checks completed:

1. Empty state disables export. Pack discovery, compiled metadata, actual thumbnails, searchable artwork and manual selection work.
2. Whole-pack export includes four package icons, including the absent app; installed-only export includes three. Missing resources are skipped. New metadata is HwTheme 12 with an empty unlock declaration and three verified 720×1280 JPEGs.
3. Manual assignment survives restart. Edited ordinary themes preserve opaque modules, unknown metadata, dynamic/configuration assets and uncovered icons; the source stays byte-identical.
4. Explicit pack merging and saving across a screen-size change preserve the session and expected contents. Light portrait and dark wide layouts were visually inspected. Wide mode is a resized emulator, not a physical foldable test.
5. Needs icon correctly classifies the missing Play Store resource. Choosing a ranked thumbnail reduces the missing count **19 → 18**, adds Customized, persists both assignment/filter across restart and restores the original missing count after Reset.
6. Suggestion reasons were inspected and adjusted to wrap readably. Index construction runs on the background worker; queries are bounded and never auto-assign.

The real-file test exposed Android cache eviction on the nearly full emulator: active source/inner files disappeared before save. Active imports, inner ZIPs, staging and pending outputs now use private `files/theme-work`. Migration permits only owned cache files, copy fallbacks are bounded, and cleanup protects active/fresh files. Subsequent checks on the final APK passed:

1. **96.7 MB chicv6.hnt** imports and saves edited metadata while preserving all **7,438 inner entries** and **141 unrelated outer entries**. The large save picker survives a screen-size change.
2. The actual older Magic Themer file imports, displays Repair theme compatibility enabled, and saves HwTheme 12 with new previews/unlock declaration. All **7,404 original PNGs remain identical**.
3. Both supplied external inputs remain unchanged and no Magic Themer crash entries appear.
4. A real edit session created by the v0.2 APK migrates during an in-place v0.3 update. Draft metadata survives and the saved copy preserves icons/modules.
5. A final new whole-pack export stages under private working storage, saves through DocumentsUI and passes ZIP/metadata/PNG content checks.

The QA driver was corrected to wait for enabled controls, restore search after restart, reveal offscreen files and handle the older-theme explanation. The running legacy-file test's explanation was dismissed using its observed Close button; archive verification completed normally. The final migration and new-export checks were automatic.

## Reproduction and limits

After building both APKs and starting the emulator:

```powershell
python qa/smoke_test.py --serial emulator-5556
python qa/rotation_test.py --serial emulator-5556
python qa/missing_apps_test.py --serial emulator-5556
python qa/user_theme_runtime_test.py --serial emulator-5556
python qa/migration_test.py --serial emulator-5556
python qa/new_export_test.py --serial emulator-5556
```

Use `--adb` for an explicit ADB path. The drivers operate only on emulator serials, reset this app/fixture or named QA documents, and keep local evidence under ignored `qa/output/v3/`: screenshots, exported archives, comparison/runtime/migration reports and UI dumps. The real-file driver requires the user's supplied files at its default paths, or `--working` and `--failed` overrides. The migration check uses the retained v0.2 APK and the smoke test's QA fixture output.

The optional local repair script also accepts `--working` and `--failed`, with `--output` and `--report-dir` to select separate destinations. For example: `python qa/compare_user_themes.py --working path/to/working.hnt --failed path/to/export.hnt --output qa/output/local/repaired.hnt --report-dir qa/output/local`. It creates destination directories, preserves both inputs and rejects destinations that would overwrite either input. The recorded personal repair is not distributed with the source repository.

Not established: HONOR Themes listing/application of repaired or new v0.3 files on the physical V5; minimal-profile acceptance without full modules; encrypted/signed themes; production signing/distribution; broad OEM, work-profile and cloned-app behavior; fallback composition or live dynamic icons. Needs icon describes pending static theme coverage, not a scan of the device's currently applied theme.

Use [DEVICE_TESTING.md](DEVICE_TESTING.md) for the phone check, comparing the repaired working-base file with a new minimal export.
