"""Rebuild launcher artwork from the preserved mascot trace.

Requires Python fonttools and shapely (authoring only; no app dependencies).
Run from the repository root: python qa/prepare_launcher_icon.py
"""
from pathlib import Path
import hashlib
import json
import math
import re
import xml.etree.ElementTree as ET

from fontTools.pens.basePen import BasePen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.svgLib.path import parse_path
from shapely.geometry import LineString, Polygon
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "artwork" / "launcher"
SOURCE = ART / "magic-themer-mascot-original.svg"
DRAWABLE = ROOT / "app/src/main/res/drawable"
SCALE = 0.125
OFFSET = 22
BACKGROUND = "#1C474D"
ANDROID = "http://schemas.android.com/apk/res/android"
SOURCE_SHA256 = "ef3fd27f5ff4bc910b214d0595627c0ecdf205f9457434365d8726ce097602e5"


def number(value):
    return f"{value:.1f}".rstrip("0").rstrip(".") if abs(value) >= 0.05 else "0"


class OutlinePen(BasePen):
    """Sample the original cubic curves for silhouette booleans and safe-zone QA."""

    def __init__(self):
        super().__init__(None)
        self.rings = []
        self.points = []

    def _moveTo(self, point):
        self.points = [point]

    def _lineTo(self, point):
        self.points.append(point)

    def _curveToOne(self, p1, p2, p3):
        p0 = self.points[-1]
        for step in range(1, 25):
            t = step / 24
            u = 1 - t
            self.points.append(tuple(
                u**3 * p0[j] + 3 * u*u*t * p1[j] + 3 * u*t*t * p2[j] + t**3 * p3[j]
                for j in (0, 1)
            ))

    def _closePath(self):
        self.rings.append(self.points)
        self.points = []


def translation(path):
    return tuple(map(float, re.findall(r"-?\d+(?:\.\d+)?", path.get("transform"))))


def outline(path):
    pen = OutlinePen()
    dx, dy = translation(path)
    parse_path(path.get("d"), TransformPen(pen, (1, 0, 0, 1, dx, dy)))
    return pen.rings


def native_path(path):
    pen = SVGPathPen(None, ntos=number)
    dx, dy = translation(path)
    parse_path(path.get("d"), TransformPen(
        pen, (SCALE, 0, 0, SCALE, OFFSET + dx*SCALE, OFFSET + dy*SCALE)
    ))
    return pen.getCommands()


def svg(paths, *, background=False, monochrome=False):
    content = '<rect width="108" height="108" fill="' + BACKGROUND + '"/>' if background else ""
    for fill, data in paths:
        content += f'<path fill="{fill}" d="{data}"' + (' fill-rule="evenodd"' if monochrome else '') + '/>'
    # The standalone icon previews the normal 72dp launcher viewport. Layer
    # exports retain the full 108dp canvas required for adaptive motion/masking.
    viewbox = "18 18 72 72" if background else "0 0 108 108"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" '
            'width="108" height="108" role="img" aria-label="Magic Themer mascot">'
            '<title>Magic Themer mascot</title>' + content + '</svg>\n')


def vector(paths, *, monochrome=False):
    content = '\n'.join(
        f'    <path android:fillColor="{fill}" android:pathData="{data}"'
        + (' android:fillType="evenOdd"' if monochrome else '') + ' />'
        for fill, data in paths
    )
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<vector xmlns:android="{ANDROID}" android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            + content + '\n</vector>\n')


def main():
    # Layer selection below is specific to this supplied trace, not arbitrary SVGs.
    source_hash = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    if source_hash != SOURCE_SHA256:
        raise ValueError("Source trace changed; review layer selection before rebuilding")
    paths = ET.parse(SOURCE).getroot().findall('.//{*}path')
    # The first 85 layers contain the broad illustration, eye highlights, tile,
    # shading and three stars. Later layers are tiny tracing fragments.
    # Drop the flat canvas (0), baked corner mask (3), and corner-edge fleck (46).
    kept = [i for i in range(85) if i not in (0, 3, 46)]
    color = [(paths[i].get('fill'), native_path(paths[i])) for i in kept]
    radius = max(math.hypot(OFFSET + x*SCALE - 54, OFFSET + y*SCALE - 54)
                 for i in kept for ring in outline(paths[i]) for x, y in ring)
    assert radius < 33, f"Artwork exceeds the 66dp safe circle: {radius}"

    def shape(index):
        return unary_union([Polygon(ring).buffer(0) for ring in outline(paths[index])])

    # Preserve the character and stars; eyes and four colored tiles become holes.
    silhouette = unary_union([shape(i) for i in (1, 2, 19, 47, 55, 63)])
    holes = unary_union([shape(i) for i in (10, 16, 11, 12, 13, 14)])
    mouth = LineString([(246, 272), (252, 276), (260, 277), (269, 274)]).buffer(2.5)
    silhouette = silhouette.difference(holes.union(mouth))
    silhouette = silhouette.union(unary_union([shape(i) for i in (30, 49)]))
    silhouette = silhouette.simplify(0.6, preserve_topology=True)
    mono_data = ""
    polygons = list(silhouette.geoms) if silhouette.geom_type == 'MultiPolygon' else [silhouette]
    for polygon in polygons:
        for ring in [polygon.exterior, *polygon.interiors]:
            coords = [(OFFSET + x*SCALE, OFFSET + y*SCALE) for x, y in list(ring.coords)[:-1]]
            mono_data += 'M' + 'L'.join(f'{number(x)},{number(y)}' for x, y in coords) + 'Z'
    monochrome = [("#FFFFFF", mono_data)]

    (ART / 'magic-themer-icon.svg').write_text(svg(color, background=True), encoding='utf-8')
    (ART / 'magic-themer-foreground.svg').write_text(svg(color), encoding='utf-8')
    (ART / 'magic-themer-monochrome.svg').write_text(svg(monochrome, monochrome=True), encoding='utf-8')
    (DRAWABLE / 'ic_launcher_foreground.xml').write_text(vector(color), encoding='utf-8')
    (DRAWABLE / 'ic_launcher_monochrome.xml').write_text(vector(monochrome, monochrome=True), encoding='utf-8')
    print(json.dumps({
        'source_sha256': source_hash,
        'original_paths': len(paths), 'optimized_paths': len(color),
        'original_bytes': SOURCE.stat().st_size,
        'optimized_svg_bytes': (ART / 'magic-themer-icon.svg').stat().st_size,
        'maximum_foreground_radius_dp': round(radius, 3), 'safe_radius_dp': 33,
        'monochrome_valid': silhouette.is_valid,
    }, indent=2))


if __name__ == '__main__':
    main()
