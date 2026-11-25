# Magic Themer launcher artwork

The mascot is a little mint ghost holding a four-colour app tile. Since v4 it is
drawn from simple vector shapes and gradients defined in
`qa/prepare_launcher_icon.py`, so one design produces every launcher asset.

- `magic-themer-icon.svg`: full-colour standalone icon showing the central 72dp
  launcher viewport. Square edges let the consuming platform choose its own mask.
- `magic-themer-foreground.svg`: transparent adaptive foreground, 108 x 108 canvas.
- `magic-themer-monochrome.svg`: transparent themed-icon layer, 108 x 108 canvas.
- `magic-themer-mascot-original.svg`: the original user-supplied trace
  (SHA-256 `ef3fd27f5ff4bc910b214d0595627c0ecdf205f9457434365d8726ce097602e5`).
  Up to v0.7 the launcher icon was built from it. It is kept as the identity
  reference and is no longer used by the build.

The same script also writes `docs/images/mascot.svg` (the website mascot,
without the glow and stars) and the Android resources
`app/src/main/res/drawable/ic_launcher_foreground.xml` and
`ic_launcher_monochrome.xml`. Gradients become inline `aapt:attr` gradients in
the VectorDrawable, which minSdk 26 supports.

Background colour: `#1C474D` (`@color/launcher_background`). A faint mint aura
in the foreground fades to fully transparent before the mask edge. Everything
else stays within 32dp of the canvas centre (currently 31.285dp), inside
Android's 33dp safe circle. The script fails if a shape crosses that limit.

The monochrome layer is built with shapely from the same shapes, following the
`MONOCHROME` recipe in the script. Leaves, tail, body, card, paws and head are
layered with 1.1dp transparent gaps between them. The eyes, mouth and four tiles
are cut out, and the eye highlights are kept. Its one path uses even-odd filling
so Android can tint it without losing the character's features.

## Rebuilding

Run `python qa/prepare_launcher_icon.py` from the repository root. Authoring
dependencies are `fonttools` and `shapely`; the app has no new dependencies.
Edit `design()` and the constants above it to change the artwork. Add
`--out DIR` to write every output into `DIR` for experiments instead of the
repository.

`docs/images/icon-256.png` (README and website) is a 256px render of
`magic-themer-icon.svg`, rendered in a browser at 1024px and downscaled. It
reuses the alpha mask of the previous PNG so its rounded corners keep the same
shape.

## Design history

- v0.6–v0.7: optimized trace of the original illustration.
- v3: hand-built vector redraw. It removed the tall ribbon on the right side,
  gave the mascot a bigger chibi head with a light 7° tilt and bigger glossy
  eyes, plus blush and an open smile. It also lowered the card so the chin no
  longer covers the tiles.
- v4 (current): Gemini 4 Neon refined v3. The head and tuft became one seamless
  silhouette, the paws became mitten shapes hugging the card, and the blush
  became airbrushed gradients. The eyes and tiles are brighter, and the low tail
  tapers into a soft wisp.

## Verification — 26 November 2026

- `:app:assembleDebug :app:lintDebug --offline` succeeded. Lint reported 0 errors
  and 56 warnings. The only launcher one is `VectorPath` (long path data) on the
  monochrome silhouette; it is a rendering-cost hint, and launchers cache icons.
- Loaded the compiled APK resources on the Android API 36 emulator without
  installing it. Rendered the native AdaptiveIconDrawable at 48px and 256px in
  colour and themed monochrome. The native renders match the SVG artwork,
  gradients included. Evidence is in `qa/output/launcher-icon-v4/` (ignored).
- Inspected the SVG artwork at 36, 48, 96 and 640px with circular and
  rounded-square masks, plus the website mascot on its halo.
- The HONOR launcher's appearance has not been checked on the physical phone.
