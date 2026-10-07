# Validation record

Completed 7 October 2026 for **0.2.0**, application ID `dev.magicthemer`. This is a debug-signed prototype. HONOR Themes acceptance on MagicOS 10.0.0.168 EU remains unverified.

## Build and release artifact

Toolchain: Android Studio JBR 25.0.2, Gradle 9.4.0, AGP 9.1.0, Kotlin runtime override 2.3.21, compile/target SDK 36, minimum SDK 26. Material Components 1.13.0 and RecyclerView 1.4.0 provide the native Material 3 UI.

The first offline compilation needed additional AndroidX artifacts. They were resolved from the configured repositories; subsequent builds completed offline. Android-unavailable XML constants and an ambiguous ConcurrentHashMap key lookup were corrected. API-27 theme overrides now inherit the shared Material 3 base in light and dark modes. No quality gates were disabled.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :qa:iconpack:assembleDebug --offline --console=plain
```

- **42 unit tests passed**, zero failures/errors: 8 appfilter parser, 6 drawable catalogue, 3 mapping selection, 15 archive writer and 10 theme editor tests.
- **Lint passed: zero errors, 39 warnings.** Remaining warnings concern English text construction, targeted resource lookup, pinned dependencies/SDK, backup guidance, adapter refresh and optional Kotlin/style improvements. This is not a zero-warning production release.
- Final APK signature verified with APK Signature Scheme v2.
- No internet, broad storage or all-package visibility permission. AndroidX adds a private signature-protected `dev.magicthemer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` declaration/use.
- Gradle wrapper distribution and JAR hashes were verified during the original project setup.

Artifact: `dist/Magic-Themer-0.2.0-debug.apk` — **6,614,674 bytes**.

SHA-256: `4b4bfe41ce83d2ffebc2472373507abc6b79e4c5dc4a4e342b78a79fb7acee26`. Both APK versions and checksums remain in `dist/`.

## Android 16 runtime checks

Used the headless `Medium_Phone_API_36.1` emulator, serial `emulator-5556`. The QA pack is original test artwork: vectors, a 768px raster drawable, an unmapped alternative and one deliberately absent drawable. A mapped future app is not installed.

Conversion checks:

1. Empty state disables new-theme export.
2. Installed pack discovery and compiled XML parsing succeed.
3. Visual pack picker, app rows, icon picker and complete drawable gallery render actual icons. The unmapped alternative appears and can be assigned.
4. A manual assignment persists through app restart.
5. Whole-pack export includes four package PNGs, including the absent app; installed-only export includes three. Both skip the deliberately missing resource.
6. Saves complete through Android DocumentsUI. Outer/inner ZIP CRCs and metadata are valid, and all exported PNGs are 192×192.

Existing-theme checks:

1. An ordinary HNT fixture opens through the document picker. Selecting its replacement pack alone does not apply all pack mappings.
2. Edited metadata and a manually replaced Settings icon persist across restart and save to a separate copy.
3. Unrelated wallpaper, font, system and unknown file contents remain identical. Inner configuration/dynamic assets and an uncovered existing app icon remain identical.
4. Unknown XML attributes/nodes, the HnTheme root and original format version `6.0.0` survive editing.
5. Explicit whole-pack merging adds absent-app mappings while retaining the uncovered original icon.
6. The imported external source remains **byte-for-byte identical** before and after saves.
7. Saving with the system picker open across a screen-size change retains the pending export and edit session; the resulting archive passes the same preservation assertions.

Unit tests additionally cover replace-all/removal, alias replacement, package-named PNGs in `dynamic_icons/`, custom metadata, completed ZIP size overhead, unsafe/duplicate paths, DTD/deep XML rejection, oversized members and temporary-file cleanup. Replace-all preservation is unit-tested; the emulator flow exercises merging.

Light portrait UI at 1080×2400 and dark wide UI at 2160×1920 were visually inspected. The wide layout is a resized emulator, not a physical foldable test. No Magic Themer crash entries appeared. System-picker navigation and duplicate-label selectors in the QA driver were corrected; the conversion and editor checks were completed in separate phases.

Reproduce after building both APKs:

```powershell
python qa/smoke_test.py --serial emulator-5556
python qa/rotation_test.py --serial emulator-5556
```

The drivers only operate on emulator serials and reset the two QA apps or named QA documents. The editor-only resume option checks previously saved conversion outputs before continuing. Local evidence is ignored under `qa/output/v2/`: screenshots, exported themes, archive-report.json, rotation-report.json and UI dumps. The QA HNT contains opaque synthetic module bytes; it is a preservation fixture, not a usable phone theme.

## Not established

- Discovery, import or application by HONOR Themes on the actual Magic V5, including acceptance of the minimal HnTheme 8.0.0 profile or `.hwt` behavior.
- Compatibility with encrypted/signed themes or icon modules outside the supported ordinary ZIP structure.
- Paid/unusual icon-pack dashboards, work profiles, cloned apps, fallback composition or live dynamic icons.
- Production signing, Play distribution or broad device compatibility.

Use [DEVICE_TESTING.md](DEVICE_TESTING.md) to validate both a generated theme and an edited known-working HNT on the phone. The original 0.1 APK and its earlier emulator evidence remain available.
