# Validation record

## 25 October 2026: user-reported v0.5 device listing

The user reports that a fresh v0.5 **My Magic Icons** export and all A/B/C import controls were initially absent, then appeared after restarting the target V5. Their HONOR Themes → My downloads → Icons screenshot shows all four plus Peafowl's original; My Magic Icons has the selection checkmark. Actual launcher icon changes are not shown and remain to be checked.

Inspection of the local controls confirms that A has no wallpaper or full base modules, B adds wallpaper, and C retains the full base. Listing therefore does not require wallpaper or the full base in this comparison. Delayed discovery is a supported explanation, but the specific indexing mechanism and the outcome for older rejected files remain unverified. Keep the current v0.5 export as the working baseline and retain the controls locally.

This follow-up changes documentation only. It does not rerun or supersede the historical v0.4 build/runtime checks below.

The existing local v0.5 JVM reports from 25 October contain 88 tests: 86 passed, zero failures/errors, and two optional supplied-theme tests skipped. Those two supplied-file checks were not run for v0.5.

## Historical v0.4 validation

Completed 7 October 2026 for **0.4.0**, application ID `dev.magicthemer`. This debug-signed prototype targets **Magic V5 / MagicOS 10.0.0.168 EU**. The supplied untouched Peafowl theme worked on the phone; two actual app-edited outputs did not appear. Acceptance of v0.4 controls and exports remains a phone test.

Earlier records: [v0.3](VALIDATION-0.3.md), [v0.2](VALIDATION-0.2.md). APKs, personal themes and generated evidence in `dist/` and `qa/output/` are local-only and ignored by Git. No supplied artwork is included in the APK or source repository.

## Build checks

The toolchain remains Android Studio JBR 25.0.2, Gradle 9.4.0, AGP 9.1.0, Kotlin runtime override 2.3.21, SDK 36, minimum SDK 26, Material Components 1.13.0 and RecyclerView 1.4.0.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:MAGIC_THEMER_COMPATIBILITY_THEME = 'C:\Users\simon\AppData\Local\Temp\chicv6 (1).hnt'
$env:MAGIC_THEMER_EDITED_THEME = 'C:\Users\simon\AppData\Local\Temp\chicv6-edited.hnt'
$env:MAGIC_THEMER_DIAGNOSTIC_OUTPUT = "$PWD\dist"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain
```

- **73 JVM tests passed**, zero failures/errors/skips with both supplied-file variables: 8 appfilter, 6 catalogue, 14 suggestions, 3 mapping selection, 15 new-theme writer, 19 editor and 8 raw ZIP tests.
- The two optional supplied-file tests are skipped when their inputs are not provided. They require no third-party assets in the repository. The diagnostic-output variable is optional; when present, personal controls are copied from the same writer used by the APK.
- **Lint: zero errors, 47 warnings.** Existing text/resource lookup, pinned dependency, backup, notification and style warnings remain. No quality gates were disabled.
- APK version `0.4.0`, version code `4`; APK Signature Scheme v2 verifies.
- The only declared permission is AndroidX's private signature-protected `dev.magicthemer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no internet, broad storage or all-package permission.

APK: `dist/Magic-Themer-0.4.0-debug.apk`, **6,695,340 bytes**. SHA-256: `ca2f68cf4ce44041c542ffd816230c17d2d45d16c475241752ba6221b817adcf`.

## Actual phone exports and causal limits

The newly supplied `chicv6 (1).hnt` is byte-identical to the earlier working base: SHA-256 `1c75536b8b96cd62a59c805a415c9394ef616f2390fa274c22780cda82af37dd`.

`chicv6-edited.hnt` has SHA-256 `9cacf6712e640f94c23e5d25b555520cc304ddbdc56393d0ffd5eda7ccfd478b`. It preserves outer names/order but converts all 143 records to DEFLATED, streaming descriptors, zero local CRC/sizes and DOS creator metadata without Unix attributes. It adds `<briefInfo/>`, changes two previews and rebuilds the nested archive. All 7,438 original inner contents remain; exactly six package icons were added.

`ChicIcons-edited.hnt` has SHA-256 `951443bd84ef9a3876ccff0a8c1c9d66bbc4d84af54b089b8a2f68f1a2075632`. It retains the four-entry minimal `HnTheme` 8.0.0 profile with designer `Simo`. The former designer-equals-Magic-Themer rule prevented this customized copy from receiving the legacy conversion option.

These are observed differences, not proof of the HONOR importer's rejection rule. v0.4 raw-copies unrelated local records and compressed payloads; updated records have populated local CRC/sizes without descriptors. No-edit saves of the working original are byte-identical. Metadata patching retains XML formatting and avoids inserting an absent blank field. Working-theme icon edits keep imported preview bytes. Explicit minimal legacy conversion normalizes descriptor framing in both layers while retaining compressed PNG payloads.

## Personal controls

Generated and verified through the JVM APK writer, with originals unchanged:

| File | Bytes | SHA-256 | Preservation |
| --- | ---: | --- | --- |
| `dist/Chic V6 Header Test.hnt` | 96,706,696 | `c7f54cabc5706a12629896a2a82a0521f917f260086d14cc70cd43a2ca130553` | Only matching titles change; 142 outer records and the entire inner ZIP remain byte-exact. |
| `dist/Chic V6 Fixed.hnt` | 96,770,110 | `74f47967149832ba7184fede4b7a808d8d3ab09c60db53c324cb778d5b8fcf8e` | 141 outer records and all 7,438 original inner records remain byte-exact; six new package PNGs are retained, for 7,444 entries. |

Both outputs retain original credits, previews, modules, directory methods/attributes and 143 outer entries. All local CRC/size fields match the central directory; neither contains data descriptors. Verification of raw compressed records is stronger than comparing only extracted contents. Local evidence: `qa/output/v4/actual-phone-exports.json`, `actual-icon-changes.json` and `personal-controls-report.json`.

## Android runtime checks

The focused emulator driver `qa/packaging_runtime_test.py` passed on Android 16 / API 36.1:

- A 96.7 MB no-edit working-theme copy saved through the system document picker is byte-identical to the original. Actual filename, byte count and SHA-256 in the save dialog match the pulled file.
- Applying the four-mapping synthetic pack to the real working theme retains 141 unrelated outer raw records, every original preview and the unrelated inner records. Modified outer and inner local headers contain correct CRC/sizes without descriptors. No original icon records are removed during merging.
- A fresh whole-pack export contains all four mappings, including the uninstalled app, with complete local headers in both ZIP layers.
- The host original and the emulator's imported original remain unchanged. No app crash entries occurred. The verification dialog was inspected in an actual screenshot.

The first attempt hit a System UI startup ANR and a UI automation collision during recovery. Only named earlier QA documents were cleared to recover emulator space, and the emulator was rebooted. The final run used one UI driver and passed all checks; no application changes or weakened assertions were needed for that rerun.

The driver operates only on an emulator, resets this app's data and uses named QA documents. Run with `--adb` and `--working` overrides; its report and screenshots are ignored under `qa/output/v4/`.

HONOR Themes discovery, actual launcher icon changes on the physical V5, work profiles/clones, proprietary theme modules and production signing remain outside these checks. See [device testing](DEVICE_TESTING.md) for the title-only versus six-icon comparison.
