# Validation record

## 27 October 2026: v0.7.1 application ID

At the user's request, the installed application ID changed to `com.tzii.magicthemer`, following ThystTV's developer naming. The visible name remains Magic Themer. Version is **0.7.1 / code 8**. The internal Kotlin namespace stays unchanged; the built APK's manifest and compiled resource package both use the new application ID.

- Debug/release assembly, JVM unit tests, release lint and the updated synthetic QA pack build passed. JVM reports: **86 passed**, zero failures/errors, two optional supplied-theme tests skipped. Release lint: zero errors, 57 warnings.
- APK Signature Scheme v2 verifies. The RSA-4096 certificate SHA-256 remains `8f4e78877186c72e128376d60e231aa108ba8009ad59a44b534fc29216b9e460`, matching v0.7.0 and the owner's existing key backup.
- Current QA scripts and the synthetic pack target the new application ID. The historical v0.2→v0.3 migration check explicitly retains the old ID. Release notes and installation instructions explain separate installation and exporting themes from the old app.
- The actual production-signed APK passed the phone-emulator check: new-ID installation and launch, matching icon resources (the app is themeable), original-icon and custom-assignment persistence across restart, whole-pack export containing `com.tzii.magicthemer.png`, and theme import retaining the original credits. Both application IDs remained installed, and the old app's saved preferences file stayed byte-identical. No crash entries occurred. Evidence is ignored under `qa/output/v7.1/`.
- Release artifact: `dist/Magic-Themer-0.7.1.apk`, **1,663,001 bytes**, SHA-256 `9025ae5d67d32d7514a896ef6c39c86dcd8e9a8ea771d2a6d358926438c5492f`.

## 27 October 2026: phone confirmation and public-release preparation

After receiving the polished v0.7 debug APK, the user reported that it works fine on the target phone. This closes the outstanding general phone check for that build; it is not a separate test of every imported theme or device model.

The user then authorized permanent release signing and GitHub publication. The public branch history was checked separately from local T3 checkpoint refs: no personal `.hnt`/`.hwt` fixtures, APKs, keystores, signing properties or machine-local SDK configuration were found in the branch history being published. Local checkpoint refs and ignored working files are not pushed.

- A permanent RSA-4096 signing key was generated with a cryptographically random 256-bit password. The local keystore, signing configuration and self-contained password-manager recovery note are ignored by Git. The recovery note's encoded keystore was checked byte-for-byte against the actual key file.
- Signed `:app:assembleRelease` and `:app:lintRelease` passed. Release lint reports zero errors and 57 warnings. `apksigner verify --verbose --print-certs` confirms APK Signature Scheme v2 and the permanent RSA-4096 signer. Public certificate SHA-256: `8f4e78877186c72e128376d60e231aa108ba8009ad59a44b534fc29216b9e460`.
- Every extracted ZIP entry in the signed APK is byte-identical to the previously tested optimized unsigned build. Signing therefore changes the APK signature block without changing the tested application payload; the earlier minified runtime checks still apply.
- Public installation artifact: `dist/Magic-Themer-0.7.0.apk`, **1,662,957 bytes**, SHA-256 `cf91ca75a78d81c0d329020c96770b92bda324bb6f281500c9392f5118024aa5`. The companion `.apk.sha256` file contains this checksum.

## 26 October 2026: v0.7 polish and GitHub preparation

The user confirmed that **Original icons** bypasses the applied custom theme on their HONOR Magic V5 / MagicOS 10. They also reported that the v0.6 export appeared without force-stopping Themes on that attempt. These are phone observations; emulator checks do not establish HONOR indexing behavior.

- Debug and unsigned, R8-minified release assembly passed, together with `:app:testDebugUnitTest`, `:app:lintDebug` and the synthetic QA pack build. The JVM reports contain **86 passes**, zero failures/errors and two optional supplied-theme tests skipped. Lint reports zero errors and 58 warnings.
- The Android 16 / API 36.1 phone emulator passed the conversion/editor smoke checks: empty state and disabled export, complete artwork catalogue, assignment persistence, whole-pack and installed-only exports, imported metadata/icon edits, whole-pack merge, unchanged source archive, wide dark layout and no app crash entries. Saved ZIPs and screenshots are retained under ignored `qa/output/v7/`.
- A copy of the minified release APK, signed with the local debug key solely for emulator QA, passed the collapsing-header/pinned-tabs checks, Settings changes and persistence after restart, default pack title, custom title persistence, whole-pack export and import with the original designer preserved. The regular debug APK was restored afterwards. `polish-report.json` and the inspected portrait/Settings screenshots preserve the results.
- The driver now handles **Choose pack** versus **Change**, reveals the collapsing toolbar before opening its menu, and expects a new theme to take its pack's name. The shorter empty-result summary is reflected in its QA check.
- The first editor run was interrupted when the emulator disconnected. After restarting, reusing a filename deleted directly through ADB encountered a Downloads provider path failure; the app rejected the save. The same editor assertions then passed using fresh `QA Edited Retry` / `QA Merged Retry` filenames. Failure logs and provider diagnostics remain local; no archive assertions were removed.
- Publication preparation includes GPL-3.0, a changelog, signing configuration/example and [release instructions](RELEASING.md). The tracked-file check found no APKs, personal theme files, keystores or signing properties, and no hard-coded user-directory paths in the text sources checked. GitHub publication remains with the owner.

Artifacts:

- `dist/Magic-Themer-0.7.0-debug.apk`: **6,782,112 bytes**, SHA-256 `4a1a46ae5eb4fcc443e47065d1ff432f39948c12e2a0cd2d1bb62d17aaaea84e`.
- `dist/Magic-Themer-0.7.0-release-unsigned.apk`: **1,658,861 bytes**, SHA-256 `15ffca9feb964e7226bf6c932e67cdbdcd14495a655e311caa2e58561f34a349`. This is not an installable public release until signed with the owner's permanent key.

The final layout still needs a phone check. A permanent release keystore is not configured, and no GitHub visibility, tag or release changes were made.

## 25 October 2026: v0.6 build and save-publication checks

The final APK includes the new optimized adaptive launcher icon and its dedicated monochrome resource. Build isolation started from commit `94868108375013ee0a67983e6ef374682ce7d2bd`, overlaid this task's export/save changes and the user-authorized icon resources, and kept generated evidence under ignored `qa/output/v6/`. The original SVG remains unchanged.

- `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug` passed. The source tests contain **86 passes**, zero failures/errors and two optional supplied-theme tests skipped. After adding the icon resources, assembly and lint passed again: zero lint errors and 53 warnings.
- `qa/discovery_runtime_test.py` passed against the final APK on the Android 16 / API 36.1 phone emulator. It uses the original synthetic QA pack, resets only this app's emulator data and writes a named QA document in Downloads.
- The actual saved ZIP has A's eight outer entries, four package PNGs and no wallpaper/system modules. Both ZIP layers are readable. The dialog's filename, size and SHA-256 match the pulled document.
- A native file-event trace confirms that the temporary document's writable handle closes before the final `.hnt` rename arrival. The finalized document has no later writes, and no temporary document remains. `publication-events.txt`, `discovery-report.json` and screenshots preserve the evidence.
- The save dialog and apply guide expose the **App info** action. Both normal opening and app-info opening fall back safely when neither HONOR nor Huawei Themes is installed. No HONOR discovery behavior is simulated by this test.
- APK Signature Scheme v2 verification passed. The packaged APK contains the dedicated monochrome icon resource.

The first hidden-temporary-name experiment exposed Downloads provider normalization and an empty leftover file; normal temporary names avoid that behavior, and the final test asserts that no staging file remains. The longer app-info button label was clipped in the guide; the final shorter label is visible and actionable in the inspected screenshot. No validation assertions or quality gates were weakened.

APK: `dist/Magic-Themer-0.6.0-discovery-debug.apk`, **7,790,560 bytes**, version `0.6.0` / code `6`. SHA-256: `42e17b8559bdbe9eac89243e9511b64cd78bc8a1dbead5636c41691d9bc9167a`.

Reproduce after building the APK and synthetic QA pack:

```powershell
python qa/discovery_runtime_test.py --adb "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" --serial emulator-5556
```

The remaining target-phone check is immediate listing of a new uniquely titled v0.6 export saved directly into `Honor/Themes` while Themes is already running. The rename sequence is verified on the emulator; its effect on HONOR's indexing remains unverified. Providers without rename support retain direct saving and the manual refresh workaround.

## 25 October 2026: user-confirmed icon application

The user confirms that the v0.5 export applies correctly on both V5 displays, leaves wallpaper unchanged through icon customization, provides relevant recommendations across more apps and persists as expected. The wallpaper-free A control also applies icons. Launcher screenshots show applied artwork for A and the user's v0.5 export; they do not use identical scroll regions or necessarily identical assignments.

A later exported pack appeared after force-stopping HONOR Themes and reopening it. The user reports that Peafowl does not require this workaround, and clarified that its completed file is manually moved into `Honor/Themes`. Immediate discovery remains unresolved; archive application is working for the tested exports. v0.6 adopts A's wallpaper-free layout, publishes completed saves by rename where supported, and adds a Themes app-info shortcut. The effect on HONOR discovery still requires target-phone verification.

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
$env:MAGIC_THEMER_COMPATIBILITY_THEME = "$env:TEMP\chicv6 (1).hnt"
$env:MAGIC_THEMER_EDITED_THEME = "$env:TEMP\chicv6-edited.hnt"
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
