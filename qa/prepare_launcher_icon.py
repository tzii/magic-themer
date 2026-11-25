"""Build the Magic Themer launcher artwork from the vector design in this file.

The mascot is drawn from simple shapes and gradients, so one set of geometry
produces the SVG artwork, the website mascot and the Android VectorDrawables.
Authoring needs Python fonttools and shapely; the app gets no new dependencies.
Run from the repository root: python qa/prepare_launcher_icon.py
Add --out DIR to write every output into DIR instead of the repository.
"""
from dataclasses import dataclass
from fnmatch import fnmatch
from pathlib import Path
import argparse
import json
import math

from fontTools.pens.basePen import BasePen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.svgLib.path import parse_path
from shapely.geometry import LineString, Point, Polygon
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "artwork" / "launcher"
DRAWABLE = ROOT / "app/src/main/res/drawable"
DOCS_IMAGES = ROOT / "docs" / "images"
BACKGROUND = "#1C474D"
CENTER = (54, 54)
SAFE_RADIUS = 33   # Android's adaptive icon safe circle has a 66dp diameter.
ART_RADIUS = 32    # Keep 1dp in reserve for launcher masks that crop tighter.
MONO_GAP = 1.1     # Transparent gap that separates overlapping parts in the themed icon.
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"


# ---------------------------------------------------------------- geometry --

IDENTITY = (1, 0, 0, 1, 0, 0)


def mul(*matrices):
    """Compose affine matrices; the rightmost one applies first."""
    result = IDENTITY
    for m in matrices:
        a, b, c, d, e, f = result
        A, B, C, D, E, F = m
        result = (a*A + c*B, b*A + d*B, a*C + c*D, b*C + d*D, a*E + c*F + e, b*E + d*F + f)
    return result


def move(x, y):
    return (1, 0, 0, 1, x, y)


def turn(degrees, cx=0, cy=0):
    r = math.radians(degrees)
    return mul(move(cx, cy), (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0), move(-cx, -cy))


def apply(m, x, y):
    return (m[0]*x + m[2]*y + m[4], m[1]*x + m[3]*y + m[5])


def scale_of(m):
    return math.sqrt(abs(m[0]*m[3] - m[1]*m[2]))


def number(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


K = 0.5523  # Cubic Bezier constant for quarter ellipses.


def ellipse(cx, cy, rx, ry):
    return (f"M{cx + rx} {cy}C{cx + rx} {cy + K*ry} {cx + K*rx} {cy + ry} {cx} {cy + ry}"
            f"C{cx - K*rx} {cy + ry} {cx - rx} {cy + K*ry} {cx - rx} {cy}"
            f"C{cx - rx} {cy - K*ry} {cx - K*rx} {cy - ry} {cx} {cy - ry}"
            f"C{cx + K*rx} {cy - ry} {cx + rx} {cy - K*ry} {cx + rx} {cy}Z")


def rounded_rect(x, y, w, h, r):
    return (f"M{x + r} {y}H{x + w - r}A{r} {r} 0 0 1 {x + w} {y + r}V{y + h - r}"
            f"A{r} {r} 0 0 1 {x + w - r} {y + h}H{x + r}A{r} {r} 0 0 1 {x} {y + h - r}"
            f"V{y + r}A{r} {r} 0 0 1 {x + r} {y}Z")


def sparkle(cx, cy, r, waist=0.16):
    """Four-point star with soft concave sides."""
    w = r * waist
    return (f"M{cx} {cy - r}C{cx + w} {cy - w} {cx + w} {cy - w} {cx + r} {cy}"
            f"C{cx + w} {cy + w} {cx + w} {cy + w} {cx} {cy + r}"
            f"C{cx - w} {cy + w} {cx - w} {cy + w} {cx - r} {cy}"
            f"C{cx - w} {cy - w} {cx - w} {cy - w} {cx} {cy - r}Z")


def split_cubic(p, t):
    def lerp(a, b):
        return (a[0] + (b[0] - a[0])*t, a[1] + (b[1] - a[1])*t)
    a, b, c = lerp(p[0], p[1]), lerp(p[1], p[2]), lerp(p[2], p[3])
    d, e = lerp(a, b), lerp(b, c)
    f = lerp(d, e)
    return (p[0], a, d, f), (f, e, c, p[3])


def cubic_segment(p, t0, t1):
    head, _ = split_cubic(p, t1)
    _, middle = split_cubic(head, t0 / t1)
    return middle


def pts(*points):
    return " ".join(f"{x} {y}" for x, y in points)


# ------------------------------------------------------------------ paint --

@dataclass
class Linear:
    x1: float
    y1: float
    x2: float
    y2: float
    stops: tuple

    def placed(self, m):
        (x1, y1), (x2, y2) = apply(m, self.x1, self.y1), apply(m, self.x2, self.y2)
        return Linear(x1, y1, x2, y2, self.stops)


@dataclass
class Radial:
    cx: float
    cy: float
    r: float
    stops: tuple

    def placed(self, m):
        cx, cy = apply(m, self.cx, self.cy)
        return Radial(cx, cy, self.r * scale_of(m), self.stops)


@dataclass
class Shape:
    name: str
    d: str
    fill: object = None          # "#RRGGBB", Linear, Radial or None
    alpha: float = 1.0
    stroke: str = None
    width: float = 0.0
    m: tuple = IDENTITY
    mascot: bool = True          # False keeps a shape out of the website mascot.

    def path(self):
        pen = SVGPathPen(None, ntos=number)
        parse_path(self.d, TransformPen(pen, self.m))
        return pen.getCommands()

    def paint(self):
        return self.fill.placed(self.m) if isinstance(self.fill, (Linear, Radial)) else self.fill


def stops(*items):
    return tuple((s[0], s[1], s[2] if len(s) > 2 else 1.0) for s in items)


# ----------------------------------------------------------------- design --

SHIFT = move(0.9, -0.5)          # Centers the mascot within the 66dp safe circle.
HEAD = (52.6, 45.9)
HEAD_TILT = 7
CARD = (54.4, 71.0)
CARD_TILT = -7

MINT_BODY = stops((0, "#F5FFEB"), (0.58, "#D8F5D6"), (1, "#A0DBC3"))
EYE = stops((0, "#092028"), (0.60, "#123A45"), (1, "#1C5560"))
EYE_GLOW = stops((0, "#3CC2B0"), (0.55, "#74EED6"), (1, "#B4FFEA"))
BLUSH = "#FF7A94"
INK = "#1B3F46"


def design():
    shapes = []

    def add(name, d, fill=None, m=IDENTITY, **kwargs):
        shapes.append(Shape(name, d, fill, m=mul(SHIFT, m), **kwargs))

    head_m = mul(move(*HEAD), turn(HEAD_TILT))
    card_m = mul(move(*CARD), turn(CARD_TILT))

    # A seamless soft aura that lifts the pale mascot off the dark teal background.
    shapes.append(Shape("glow", ellipse(54, 54, 38, 38), Radial(54, 54, 38, stops(
        (0, "#9CFCE0", 0.34), (0.42, "#6FE3C6", 0.16), (0.76, "#4BC9B0", 0.04),
        (1, "#4BC9B0", 0))), mascot=False))

    # Soft botanical leaf wings tucked behind the left shoulder.
    add("leaf-back", "M41.4 60.2C36.4 58.0 30.8 58.6 28.4 61.8C27.6 62.9 28.1 64.1 29.4 64.4"
                     "C33.4 65.4 38.4 64.2 41.4 60.2Z",
        Linear(28.5, 62.5, 41, 60.5, stops((0, "#A8E5E0"), (1, "#4E98A1"))))
    add("leaf-front", "M42.2 57.6C39.2 50.2 33.2 45.8 27.0 46.2C25.5 46.3 25.2 47.7 25.8 49.0"
                      "C28.0 54.6 34.0 59.8 42.2 57.6Z",
        Linear(26, 47, 41.5, 57, stops((0, "#C6FFE3"), (1, "#3BC293"))))
    add("leaf-vein", "M40.4 56.5Q34.2 52.4 28.6 48.8", stroke="#EAFFF5", width=0.85, alpha=0.85)

    # Ghostly tail: smooth continuous sweep from under the body, tapering into a soft upturned wisp curl.
    add("tail", "M38.8 71.8C41.8 81.4 55.2 84.4 66.2 81.2C73.4 79.1 78.2 73.8 78.6 68.4"
                "C78.7 66.7 77.4 65.9 76.4 67.1C74.4 69.5 70.2 74.8 62.0 76.8"
                "C53.4 78.7 44.6 76.6 38.8 71.8Z",
        Linear(42, 78, 78, 67.5, stops((0, "#D0F8E0"), (0.42, "#66DBBE"), (1, "#29A197"))))
    add("tail-shine", "M50.2 81.0Q62.2 81.9 72.0 76.9", stroke="#EEFFF3", width=0.78, alpha=0.52)

    # Plump little ghost body flowing seamlessly into the tail; the card nestles against its tummy.
    add("body", "M40.4 55.0C36.6 59.2 35.4 64.8 36.6 70.0C37.9 75.8 43.4 79.6 50.8 80.2"
                "C57.8 80.8 64.0 78.0 66.4 72.8C68.2 68.8 67.6 62.8 64.2 57.0Z",
        Radial(45, 62, 23, MINT_BODY))

    # Palette card with four vibrant app tiles.
    add("card-shadow", rounded_rect(-10.6, -9.9, 21.2, 19.8, 5.3), "#0B3236", alpha=0.30,
        m=mul(move(0.8, 1.5), card_m))
    add("card-rim", rounded_rect(-10.6, -9.9, 21.2, 19.8, 5.3),
        Linear(-10, -10, 10, 10, stops((0, "#E4FAFA"), (1, "#7FBFC8"))), m=card_m)
    add("card-face", rounded_rect(-9.4, -8.7, 18.8, 17.4, 4.2),
        Linear(-6, -9, 6, 9, stops((0, "#FFFFFF"), (1, "#EAF7F5"))), m=card_m)
    tiles = (("mint", -7.8, -7.1, "#7AF7BE", "#2EC28A"), ("coral", 0.5, -7.1, "#FFB29A", "#ED7058"),
             ("sky", -7.8, 0.4, "#92D9FF", "#43A1EC"), ("lilac", 0.5, 0.4, "#B0BCFF", "#667EF5"))
    for name, x, y, light, dark in tiles:
        add(f"tile-{name}", rounded_rect(x, y, 7.3, 6.7, 2.1),
            Linear(x, y, x + 7.3, y + 6.7, stops((0, light), (1, dark))), m=card_m)
        add(f"tile-shine-{name}", rounded_rect(x + 1.2, y + 0.8, 4.9, 1.35, 0.68), "#FFFFFF",
            alpha=0.45, m=card_m)

    # Stubby mitten-like paws hugging the card instead of detached round balls.
    add("paw-left",
        "M-12.8 -0.6C-10.1 -1.1 -7.2 -0.1 -7.0 2.1C-6.8 4.4 -9.4 5.3 -12.2 4.5"
        "C-13.6 4.1 -14.0 0.1 -12.8 -0.6Z",
        Linear(-12.6, 0.2, -7.2, 4.6, stops((0, "#E2F7DC"), (0.48, "#F5FFEC"), (1, "#BCE8D2"))),
        m=card_m)
    add("paw-edge-left", "M-11.2 4.5C-8.9 5.1 -7.0 4.0 -7.0 2.1C-7.0 0.7 -8.1 -0.2 -9.6 -0.6",
        stroke="#8EC8B5", width=0.55, alpha=0.82, m=card_m)

    add("paw-right",
        "M11.6 -3.6C9.4 -4.1 7.5 -2.6 7.5 -0.6C7.5 1.4 9.4 2.4 11.6 1.6"
        "C12.9 1.1 12.9 -3.1 11.6 -3.6Z",
        Linear(8.2, -2.6, 12.4, 1.4, stops((0, "#F6FFEC"), (0.55, "#DAF4D7"), (1, "#A8DAC4"))),
        m=card_m)
    add("paw-edge-right", "M11.0 1.8C9.0 2.2 7.5 1.0 7.5 -0.6C7.5 -2.2 8.7 -3.5 10.6 -3.8",
        stroke="#8EC8B5", width=0.55, alpha=0.82, m=card_m)

    # Head with a seamless, soft cream-wisp tuft on top.
    add("chin-shadow", ellipse(0.4, 15.0, 10.2, 2.3),
        Radial(0.4, 15.0, 10.2, stops((0, "#124447", 0.30), (0.68, "#124447", 0.13), (1, "#124447", 0))),
        m=head_m)
    add("head",
        "M7.8 -13.2C14.8 -10.0 18.4 -4.8 18.4 1.3C18.4 9.9 10.9 14.9 0 14.9"
        "C-10.9 14.9 -18.4 9.9 -18.4 1.3C-18.4 -6.4 -13.5 -12.6 -6.2 -14.8"
        "C-1.4 -16.4 2.6 -18.6 6.8 -20.8C8.8 -21.8 10.3 -20.7 9.9 -18.5"
        "C9.5 -16.4 8.8 -14.7 7.8 -13.2Z",
        Radial(-5.8, -7.8, 29, stops((0, "#FAFFF3"), (0.45, "#E7F9DC"), (0.80, "#C1ECD5"),
                                     (1, "#9ED8C1"))), m=head_m)
    add("tuft-crease", "M3.6 -14.0Q5.9 -14.2 7.8 -13.2", stroke="#96D2BC", width=0.62, alpha=0.45, m=head_m)
    add("tuft-shine", "M0.0 -15.6Q3.7 -17.3 7.2 -19.3", stroke="#FFFFFF", width=0.85, alpha=0.55, m=head_m)
    add("head-shine", ellipse(-8.4, -8.5, 4.4, 2.1), "#FFFFFF", alpha=0.55,
        m=mul(head_m, turn(-28, -8.4, -8.5)))

    # Velvety rosy cheek blush with smooth radial falloff.
    for side, cx in (("left", -12.2), ("right", 12.2)):
        add(f"blush-{side}", ellipse(cx, 7.8, 3.7, 3.7),
            Radial(cx, 7.8, 3.7, stops((0, BLUSH, 0.72), (0.46, "#FF8EA2", 0.40),
                                       (0.80, "#FFA0B0", 0.10), (1, "#FFA0B0", 0))), m=head_m)

    # Big glossy jewel-like chibi eyes with a concave lower iris cup cradling the dark pupil.
    for side, cx in (("left", -7.3), ("right", 7.3)):
        cy, rx, ry = 1.7, 4.8, 5.2
        add(f"eye-{side}", ellipse(cx, cy, rx, ry), Linear(cx, cy - ry, cx, cy + ry, EYE), m=head_m)
        irx, iry = 0.83 * rx, 0.83 * ry
        gx, gy = irx * math.cos(math.radians(16)), 0.30 + iry * math.sin(math.radians(16))
        add(f"eye-glow-{side}",
            f"M{cx + gx} {cy + gy}A{irx} {iry} 0 0 1 {cx - gx} {cy + gy}"
            f"Q{cx} {cy + 2.65} {cx + gx} {cy + gy}Z",
            Linear(cx, cy + 1.2, cx, cy + 4.8, EYE_GLOW), alpha=0.98, m=head_m)
        add(f"eye-shine-{side}", ellipse(cx - 1.5, cy - 1.75, 1.7, 2.0), "#FFFFFF",
            m=mul(head_m, turn(-16, cx - 1.5, cy - 1.75)))
        add(f"eye-sparkle-{side}", ellipse(cx + 1.9, cy + 1.35, 0.82, 0.82), "#FFFFFF",
            alpha=0.95, m=head_m)

    # Bubbly happy open smile with softly rounded corners and a bright coral-pink tongue.
    top = 6.9
    lip = ((-2.5, top + 0.3), (-2.3, top + 3.9), (2.3, top + 3.9), (2.5, top + 0.3))
    add("mouth",
        f"M{pts(lip[0])}C{pts(*lip[1:])}"
        f"C2.5 {top - 0.05} 1.4 {top + 0.25} 0 {top + 0.28}"
        f"C-1.4 {top + 0.25} -2.5 {top - 0.05} {pts(lip[0])}Z",
        INK, m=head_m)
    tongue = cubic_segment(lip, 0.21, 0.79)
    add("tongue", f"M{pts(tongue[0])}C{pts(*tongue[1:])}Q0 {top + 1.15} {pts(tongue[0])}Z",
        Linear(0, top + 1.4, 0, top + 3.1, stops((0, "#FF9EB0"), (1, "#FF6C85"))), m=head_m)

    # Sparkles: placed on the canvas, outside the mascot.
    shapes.append(Shape("star-big", sparkle(74.4, 34.8, 4.3, waist=0.20),
                        Radial(74.4, 34.8, 3.2, stops((0, "#FFFFFF"), (1, "#FFE6A8")))))
    for name, x, y, r in (("star-left", 32.6, 35.0, 2.6), ("star-right", 80.2, 50.8, 2.1),
                          ("star-low", 30.8, 70.2, 2.2)):
        shapes.append(Shape(name, sparkle(x, y, r, waist=0.20), "#ECFFF2"))
    return shapes


# Painter's recipe for the single-colour themed icon: "fill" adds, "cut" removes,
# "over" adds a shape in front of what is already there with a transparent gap.
MONOCHROME = (
    ("fill", "leaf-*"), ("fill", "tail"), ("over", "body"),
    ("over", "card-rim"), ("cut", "tile-mint"), ("cut", "tile-coral"),
    ("cut", "tile-sky"), ("cut", "tile-lilac"), ("over", "paw-left"), ("over", "paw-right"),
    ("over", "head"), ("cut", "eye-left|eye-right"), ("fill", "eye-shine-*"),
    ("cut", "mouth"), ("fill", "star-*"),
)


# ---------------------------------------------------------------- outputs --

class OutlinePen(BasePen):
    """Sample curves into polylines for safe-zone checks and silhouette booleans."""

    def __init__(self):
        super().__init__(None)
        self.rings, self.lines, self.points = [], [], []

    def _moveTo(self, point):
        self.points = [point]

    def _lineTo(self, point):
        self.points.append(point)

    def _curveToOne(self, p1, p2, p3):
        p0 = self.points[-1]
        for step in range(1, 25):
            t = step / 24
            u = 1 - t
            self.points.append(tuple(u**3*p0[j] + 3*u*u*t*p1[j] + 3*u*t*t*p2[j] + t**3*p3[j]
                                     for j in (0, 1)))

    def _closePath(self):
        self.rings.append(self.points)

    def _endPath(self):
        self.lines.append(self.points)


def outline(shape):
    pen = OutlinePen()
    parse_path(shape.d, TransformPen(pen, shape.m))
    return pen


def geometry(shape):
    pen = outline(shape)
    parts = [Polygon(ring).buffer(0) for ring in pen.rings]
    parts += [LineString(line).buffer(shape.width / 2) for line in pen.lines]
    return unary_union(parts)


def monochrome_path(shapes):
    named = {s.name: s for s in shapes}
    result = Polygon()
    for op, patterns in MONOCHROME:
        picked = [geometry(s) for n, s in named.items()
                  if any(fnmatch(n, p) for p in patterns.split("|"))]
        assert picked, f"Monochrome recipe matched nothing: {patterns}"
        part = unary_union(picked)
        if op == "fill":
            result = result.union(part)
        elif op == "cut":
            result = result.difference(part)
        else:
            result = result.difference(part.buffer(MONO_GAP)).union(part)
    result = result.buffer(0).simplify(0.05, preserve_topology=True)
    polygons = list(result.geoms) if result.geom_type == "MultiPolygon" else [result]
    data = ""
    for polygon in polygons:
        for ring in (polygon.exterior, *polygon.interiors):
            coords = list(ring.coords)[:-1]
            data += "M" + "L".join(f"{number(x)} {number(y)}" for x, y in coords) + "Z"
    return data, result


def hex_alpha(color, alpha):
    return f"#{round(alpha * 255):02X}{color[1:].upper()}"


def svg_document(shapes, viewbox, *, background=None, monochrome=None):
    defs, body = [], []
    if background:
        body.append(f'<rect x="0" y="0" width="108" height="108" fill="{background}"/>')
    for shape in shapes:
        paint = shape.paint()
        attrs = f'd="{shape.path()}"'
        if shape.stroke:
            attrs += (f' fill="none" stroke="{shape.stroke}" stroke-width="{number(shape.width * scale_of(shape.m))}"'
                      ' stroke-linecap="round"')
            if shape.alpha < 1:
                attrs += f' stroke-opacity="{number(shape.alpha)}"'
        else:
            if isinstance(paint, (Linear, Radial)):
                gid = f"g{len(defs)}"
                items = "".join(
                    f'<stop offset="{number(o)}" stop-color="{c}"'
                    + (f' stop-opacity="{number(a)}"' if a < 1 else "") + "/>"
                    for o, c, a in paint.stops)
                if isinstance(paint, Linear):
                    defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                                f'x1="{number(paint.x1)}" y1="{number(paint.y1)}" '
                                f'x2="{number(paint.x2)}" y2="{number(paint.y2)}">{items}</linearGradient>')
                else:
                    defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                                f'cx="{number(paint.cx)}" cy="{number(paint.cy)}" r="{number(paint.r)}">'
                                f'{items}</radialGradient>')
                attrs += f' fill="url(#{gid})"'
            else:
                attrs += f' fill="{paint}"'
            if shape.alpha < 1:
                attrs += f' fill-opacity="{number(shape.alpha)}"'
        body.append(f"<path {attrs}/>")
    if monochrome:
        body.append(f'<path fill="#FFFFFF" fill-rule="evenodd" d="{monochrome}"/>')
    content = (f"<defs>{''.join(defs)}</defs>" if defs else "") + "\n".join(body)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" width="512" height="512" '
            'role="img" aria-label="Magic Themer mascot"><title>Magic Themer mascot</title>\n'
            + content + "\n</svg>\n")


def vector_document(shapes=(), *, monochrome=None):
    lines = []
    for shape in shapes:
        paint = shape.paint()
        if shape.stroke:
            lines.append(f'    <path android:pathData="{shape.path()}"\n'
                         f'        android:strokeColor="{shape.stroke}"'
                         f' android:strokeWidth="{number(shape.width * scale_of(shape.m))}"'
                         ' android:strokeLineCap="round"'
                         + (f' android:strokeAlpha="{number(shape.alpha)}"' if shape.alpha < 1 else "")
                         + " />")
            continue
        alpha = f' android:fillAlpha="{number(shape.alpha)}"' if shape.alpha < 1 else ""
        if isinstance(paint, (Linear, Radial)):
            if isinstance(paint, Linear):
                gradient = (f'android:type="linear" android:startX="{number(paint.x1)}"'
                            f' android:startY="{number(paint.y1)}" android:endX="{number(paint.x2)}"'
                            f' android:endY="{number(paint.y2)}"')
            else:
                gradient = (f'android:type="radial" android:centerX="{number(paint.cx)}"'
                            f' android:centerY="{number(paint.cy)}" android:gradientRadius="{number(paint.r)}"')
            items = "\n".join(f'                <item android:offset="{number(o)}"'
                              f' android:color="{hex_alpha(c, a)}" />' for o, c, a in paint.stops)
            lines.append(f'    <path android:pathData="{shape.path()}"{alpha}>\n'
                         '        <aapt:attr name="android:fillColor">\n'
                         f'            <gradient {gradient}>\n{items}\n'
                         '            </gradient>\n        </aapt:attr>\n    </path>')
        else:
            lines.append(f'    <path android:pathData="{shape.path()}"\n'
                         f'        android:fillColor="{paint}"{alpha} />')
    if monochrome:
        lines.append(f'    <path android:fillColor="#FFFFFF" android:fillType="evenOdd"\n'
                     f'        android:pathData="{monochrome}" />')
    namespace = f' xmlns:aapt="{AAPT}"' if any(isinstance(s.fill, (Linear, Radial)) for s in shapes) else ""
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by qa/prepare_launcher_icon.py. Edit the design there. -->\n'
            f'<vector xmlns:android="{ANDROID}"{namespace}\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            + "\n".join(lines) + "\n</vector>\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, help="write all outputs here instead of the repository")
    args = parser.parse_args()
    shapes = design()
    radius = 0.0
    for shape in shapes:
        if shape.name == "glow":
            continue
        pen = outline(shape)
        for ring in pen.rings + pen.lines:
            for x, y in ring:
                radius = max(radius, math.hypot(x - CENTER[0], y - CENTER[1]) + shape.width / 2)
    assert radius < ART_RADIUS, f"Artwork reaches {radius:.2f}dp from the center"

    mono_data, mono_shape = monochrome_path(shapes)
    mascot = [s for s in shapes if s.mascot and not s.name.startswith("star")]

    outputs = {
        ART / "magic-themer-icon.svg": svg_document(shapes, "18 18 72 72", background=BACKGROUND),
        ART / "magic-themer-foreground.svg": svg_document(shapes, "0 0 108 108"),
        ART / "magic-themer-monochrome.svg": svg_document([], "0 0 108 108", monochrome=mono_data),
        DOCS_IMAGES / "mascot.svg": svg_document(mascot, "20 18 68 68"),
        DRAWABLE / "ic_launcher_foreground.xml": vector_document(shapes),
        DRAWABLE / "ic_launcher_monochrome.xml": vector_document(monochrome=mono_data),
    }
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        outputs = {args.out / path.name: text for path, text in outputs.items()}
    for path, text in outputs.items():
        path.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps({
        "color_shapes": len(shapes),
        "maximum_foreground_radius_dp": round(radius, 3),
        "art_radius_budget_dp": ART_RADIUS, "safe_radius_dp": SAFE_RADIUS,
        "monochrome_valid": mono_shape.is_valid,
        "bytes": {p.name: p.stat().st_size for p in outputs},
    }, indent=2))


if __name__ == "__main__":
    main()
