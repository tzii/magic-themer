# MagicOS icon-theme feasibility research

Research date: 7 October 2026. Target supplied by user: HONOR Magic V5, MagicOS **10.0.0.168**, EU.

## Conclusion

An independent local icon-pack-to-theme converter is feasible using ordinary Android APIs. The engineering path is: discover an installed pack → read its public resources and component mappings → choose one drawable per target package → rasterize icons → package a candidate HONOR theme → let the user import and apply its icons in HONOR Themes.

The user supplied a rejected Magic Themer export and a working Peafowl theme from the target phone. Their comparison now provides a working archive profile, while acceptance of the corrected minimal export still needs a phone test. No target V5 was connected during development. A successful emulator export must not be described as successful MagicOS application.

## Evidence and confidence

| Area | Finding | Confidence / source |
| --- | --- | --- |
| Target OS | The user's device runs 10.0.0.168 EU. HONOR's official MagicOS pages list the V5 among supported devices. | User confirmation; [HONOR MagicOS](https://www.honor.com/uk/magic-os/) |
| Competitor | Peafowl advertises MagicOS theme creation and importing icon packs. Its Play listing declares ads and in-app purchases. This provides feasibility evidence, not a specification we can copy. | [Peafowl publisher's Google Play listing](https://play.google.com/store/apps/details?id=h7.hamzio.emuithemeotg) |
| Local import | HONOR support documents `.hwt`/`.hnt` files in `HONOR/Themes`, My downloads, and separate icon customization. These instructions are not specific certification for 10.0.0.168 EU. | [HONOR support: third-party themes](https://www.honor.com/ru/support/content/ru-ru15829525/) |
| Icon format | The widely supported ADW format maps Android components to drawable names in appfilter.xml. The file can live under res/xml, res/raw or assets. | [Kvaesitso developer documentation](https://kvaesitso.mm20.de/docs/developer-guide/integrations/icon-packs) |
| Resource access | PackageManager can obtain an installed app's Resources; loading APK executable code is unnecessary. | [Android PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager#getResourcesForApplication(java.lang.String)) |
| Package discovery | Android 11+ filters package queries; declare matching launcher/icon-pack intents in `<queries>`. Broad QUERY_ALL_PACKAGES is unnecessary for the prototype. | [Android package visibility](https://developer.android.com/training/package-visibility/declaring) |
| Storage | The user can choose an export file with ACTION_CREATE_DOCUMENT without general storage permission. Android/data and Android/obb are restricted through SAF on Android 11+. | [Android shared document storage](https://developer.android.com/training/data-storage/shared/documents-files) |
| Theme container | An authored Huawei-to-HONOR converter uses an outer ZIP, an extensionless inner icons ZIP, package-named PNGs and description.xml metadata. This is evidence of existing practice, not an official guarantee of the generated minimal profile. | [Converter repository](https://github.com/buergerr/Huawei-To-Honor-Theme-Converter) |
| Official theme production | HONOR publishes theme component standards, HNT submission requirements, image dimensions and template tooling. English documentation includes older specifications, so OS number must not be treated as a theme schema version. | [HONOR Developers theme standards](https://developer.honor.com/en/doc/guides/100794) |

## Android implementation

The app queries explicit icon-pack action/category signatures plus normal launcher activities. It reads pack resources using `getResourcesForApplication` and never requests code-loading flags. It resolves each drawable by resource name and draws it to a transparent bitmap. This accommodates vector and adaptive resources without extracting or redistributing the pack's APK.

The parser normalizes short Android activity names and preserves mappings for apps absent from the phone. When a pack has several entries for one package, the current launch activity takes precedence; otherwise a representative mapping is selected. Users can override that choice locally. v0.2 defaults to the full mapped pack, including uninstalled apps; installed-app-only export remains an option. The exporter reports missing drawable resources instead of emitting blank replacements.

v0.2 also reads drawable.xml catalogues and merges their artwork names with mapped drawable names. Alternatives are selectable for manual assignments; they are not fabricated app/package mappings. Catalogue-only packs can be browsed and assigned manually. This is the supported catalogue, not exhaustive reflection over every drawable resource in an APK.

Background work avoids rendering the entire pack for a preview. Visual lists use recycled cells and bounded on-demand thumbnails. Export allocates one rendered bitmap at a time, recycles it after PNG encoding and enforces a total byte budget. Text XML rejects DTD/entity declarations, and exported names are validated. Tests inspect real generated archives rather than only checking string assembly. Actual v0.2 test results belong in [VALIDATION.md](VALIDATION.md).

## Candidate archive profile

The writer creates an outer theme ZIP containing `description.xml`, an extensionless `icons` ZIP, an empty `unlock/theme.xml` declaration and generated preview JPEGs. The inner ZIP contains `com.example.app.png` files at its root. New themes do not include wallpaper, font or system UI artwork; the editor preserves those modules from imported files.

v0.3 uses `HwTheme` version `12` for both extension choices, with title/title-cn, author/designer and Default font declarations. The `.hwt` option changes the extension, not the metadata profile. Ordinary imported themes preserve their original profile. A narrow opt-in repair upgrades recognizable older minimal Magic Themer `HnTheme` version `8.0.0` exports.

The app now generates 720×1280 JPEGs under `preview/icon_small.jpg`, `preview/preview_icons_0.jpg` and `preview/preview_unlock_0.jpg`, matching the dimensions and paths in the working sample. The official [standard example ZIP](https://contentplatform-drcn.hihonorcdn.com/developerPlatform/HONOR_Themes_standard_case/HONOR_Themes_standard_case.zip) was located during initial research, but its download stalled; its contents were not inspected or included.

### Supplied working and rejected themes

The user's `My Magic Icons.hnt` contains four outer entries, 7,404 flat package icons, `HnTheme` version `8.0.0` and two differently sized previews. The user's accepted `chicv6.hnt` contains 143 outer entries, `HwTheme` version `12`, an empty unlock declaration, three 720×1280 previews and additional wallpaper/system modules. Its 7,438 inner entries include flat launcher aliases such as `:CAMERA.png`, dynamic calendar assets and an icon mask. The ordinary nested ZIP layout and package PNG naming match in both files.

These differences support correcting the metadata/previews and using the working file as an editing base. They do **not** prove which individual field made the old export invisible, or that a minimal file will be accepted without the extra system modules. ZIP header flags also differ; no evidence establishes that they are significant.

The working file is 96,706,683 bytes. Its combined expanded outer and inner members total 169,469,602 bytes, exceeding the old 128 MiB aggregate budget. Its valid flat colon aliases were also rejected by the old path validator. v0.3 increases file-backed limits and allows the exact `:[A-Z_]+.png` root alias form; other colon paths, drive paths and traversal remain rejected.

`qa/compare_user_themes.py` creates a personal repaired copy from the supplied working base. It replaces 7,404 matching/additional package PNGs from the failed export, preserves all other inner entries and every unrelated outer module, changes title/title-cn and preserves author/designer credits. Streamed digest/content checks confirm preservation and unchanged source files. The repaired output is user data, not an APK asset. Reports remain locally under ignored `qa/output/v3/`.

No third-party theme artwork, Peafowl assets or implementation code is included. The QA pack consists of original simple vector tiles.

## v0.2 existing-theme editing

The editor opens an app-owned private working copy of an ordinary local ZIP theme. Active input, inner ZIPs and pending saves use `filesDir/theme-work`, rather than evictable Android cache storage. The real 96.7 MB sample exposed source-file eviction on a nearly full emulator; small fixtures had not exercised this failure. Existing cache-based sessions migrate when their files still exist. The editor requires supported `HnTheme` or `HwTheme` description.xml metadata and an extensionless icons ZIP. It recognizes static PNGs whose filenames represent Android packages, including package icons inside nested directories. Existing packages with several matching paths have all those paths updated when replaced.

Editable fields are title, designer and description (`briefInfo`/`briefinfo`). Static package icons can be replaced, added or removed; the writer also supports replacing the entire static package icon set. Unrelated metadata nodes and unchanged archive-member contents are preserved. New output is saved separately; the source selected by the user is not edited in place.

The UI keeps choosing a pack separate from applying it to an imported theme. Individual assignments change selected apps; **Apply whole pack to theme** merges the chosen mappings and retains uncovered imported icons by default. **Replace existing icon set** opts into removing the original static package PNG set. **Use original** removes an app's custom icon, while **Reset** returns to the imported icon or pack default.

Preservation concerns member contents, not byte-for-byte identity of the outer ZIP or original XML formatting. Theme signatures, encrypted formats and importer-specific constraints are not validated by this editor. Preserving an existing module does not imply the app can author its system, wallpaper, font or lock-screen behavior. Output still requires testing on MagicOS 10.0.0.168 EU.

The implementation validates archive paths, duplicate members, CRCs, metadata structure and byte budgets without extracting paths to shared storage. v0.3 allows 256 MiB input archives, 512 MiB aggregate expanded data, 128 MiB individual members, 1 MiB metadata and 512 KiB imported package PNGs. These checks bound local processing; they are not a claim of HONOR format certification.

## v0.3 missing apps and suggestions

Resource names are checked when pack metadata loads, without decoding every icon. Apps with no mapping, missing artwork, intentionally removed icons or observed decode failures appear under **Needs icon**. Valid retained imported icons remain themed when a failed replacement is skipped. **Customized** identifies usable manual assignments. These labels describe the editor's pending export, not the phone's currently applied theme.

Suggestions use a background-built offline index of pack mappings, launcher activities, artwork names, app labels, exact known app aliases and meaningful package words. Queries return at most 12 ranked candidates with reasons, excluding known missing resources. They require the user to tap an icon; there is no automatic assignment or network request. Searchable full-pack artwork remains available when the index finds no close match.

## Applying and storage

The app exports through Android's system file picker. The user should choose `HONOR/Themes` or copy there with the phone's Files app, then select the icons in HONOR Themes. The app can open the Themes launcher activity, but no documented public third-party direct-apply API was found.

Some community reports describe a Themes app's private Android/data download directory. That path is not a valid general-purpose export destination for a normal modern Android app. This prototype does not promise access to it. If the EU importer only scans private files and offers no supported import route, that is a device compatibility blocker to document rather than conceal.

## Build decisions

The scaffold uses the installed SDK 36, Gradle 9.4.0 and AGP 9.1.0. v0.2 uses native Material Components views with Material 3 DayNight styling, AppCompat and RecyclerView, rather than Compose. Material Components 1.13.0 and RecyclerView 1.4.0 are pinned cached dependencies. A Kotlin 2.3.21 buildscript runtime override uses a complete cached compiler after the default compiler download stalled. Google documents this override mechanism; this version combination goes beyond JetBrains' fully tested matrix. The underlying toolchain built v0.1, while v0.2 validation is recorded separately. It is deliberately pinned rather than claiming these are the newest available versions. The official AGP release index listed 9.4.0 during the original research.

Relevant build references: [AGP 9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes), [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [KGP runtime override](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime_dependency_on_kotlin_gradle_plugin), [JetBrains tested matrix](https://kotlinlang.org/docs/gradle-configure-project.html), [Android 16 SDK setup](https://developer.android.com/about/versions/16/setup-sdk), [Gradle compatibility](https://docs.gradle.org/9.4.0/userguide/compatibility.html).

## Scope after the device test

1. Confirm `.hnt` or `.hwt` acceptance, listing, persistence after reboot and actual icon changes on both V5 displays.
2. Add compatibility profiles only from working archive evidence; keep the original working theme available for comparison.
3. Validate full mapped-pack export, drawable.xml alternatives and existing-theme edits on the phone; retain original/input files for comparison.
4. Implement unmapped-app fallback composition and tint controls as separate verified features.
5. Research HONOR lock-screen and system component authoring before offering complete theme authoring.
6. Produce a signed release APK, accessibility/foldable checks and distribution documentation once target-device behavior is established.
