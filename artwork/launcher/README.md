# Magic Themer launcher artwork

- `magic-themer-mascot-original.svg`: unmodified user-supplied trace. SHA-256:
  `ef3fd27f5ff4bc910b214d0595627c0ecdf205f9457434365d8726ce097602e5`.
- `magic-themer-icon.svg`: optimized, full-color standalone icon. Square edges
  intentionally allow the consuming platform to choose its own mask.
- `magic-themer-foreground.svg`: transparent adaptive foreground, 108 x 108 canvas.
- `magic-themer-monochrome.svg`: transparent themed-icon layer, 108 x 108 canvas.

The optimized color artwork keeps 82 broad illustration layers from the 394-path
trace, including the eyes, highlights, four-color tile, shading and three stars.
It removes tiny tracing fragments, the old corner mask and a background-edge
fleck. Coordinates are rounded to 0.1dp on the adaptive canvas. The standalone
SVG is 56,307 bytes versus the original's 163,129 bytes (65% smaller).

The foreground scales the 512-unit source by 0.125 and translates it by (22, 22).
Its farthest sampled curve point is 31.101dp from the canvas center, within the
33dp radius safe circle. The standalone SVG shows the normal central 72dp
launcher viewport; the adaptive layer files retain the complete 108dp canvas.
Background color: `#1C474D`. No rounded corners are baked into the new artwork.

The separate monochrome silhouette uses transparent eyes, mouth and four tile
cutouts, with eye highlights retained. Its one path uses even-odd filling so
Android can tint it without losing the character's features.

## Rebuilding

Run `python qa/prepare_launcher_icon.py` from the repository root. Authoring
dependencies are `fonttools` and `shapely`; the app has no new dependencies.
This regenerates the three optimized SVGs and the native Android foreground and
monochrome VectorDrawable resources from the preserved original. The script
checks the original hash and foreground safe radius before writing outputs.
Both adaptive launcher XML resources reference the dedicated monochrome layer.

## Verification — 25 October 2026

- `:app:assembleDebug :app:lintDebug --offline --console=plain` succeeded.
  Lint reported 0 errors and 54 warnings elsewhere in the app.
- Inspected the SVG artwork at 48px and 144px with circular and rounded-square
  masks, plus the monochrome rendering.
- Loaded the compiled APK resources directly on the Android API 36 emulator
  without installing it or changing the active app. Rendered the native
  AdaptiveIconDrawable at 48px and 256px in color and themed monochrome; inspected
  both native 256px outputs. Evidence is in `qa/output/launcher-icon/` (ignored).
- Target HONOR launcher appearance has not been checked on the physical phone.
