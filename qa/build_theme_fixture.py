"""Generate our own small HNT archive to verify non-icon modules survive editing."""
from pathlib import Path
import io
import struct
import zipfile
import zlib

OUT = Path(__file__).resolve().parent / "output" / "v2"


def png(red, green, blue, size=192):
    def chunk(name, payload):
        return struct.pack(">I", len(payload)) + name + payload + struct.pack(">I", zlib.crc32(name + payload))
    rows = (b"\0" + bytes((red, green, blue, 255)) * size) * size
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def create():
    OUT.mkdir(parents=True, exist_ok=True)
    nested = io.BytesIO()
    with zipfile.ZipFile(nested, "w", zipfile.ZIP_DEFLATED) as icons:
        icons.writestr("com.android.settings.png", png(35, 95, 200))
        icons.writestr("org.example.uninstalled.png", png(200, 80, 40))
        icons.writestr("config.xml", b'<icons><scale value="0.85" /></icons>')
        icons.writestr("dynamic/assets.bin", b"Original dynamic icon assets\x00\x01")
    fixture = OUT / "QA Base.hnt"
    with zipfile.ZipFile(fixture, "w", zipfile.ZIP_DEFLATED) as theme:
        theme.writestr("description.xml", '''<?xml version="1.0" encoding="UTF-8"?>
<HnTheme custom="keep-me">
  <title>QA Base</title><designer>Base Author</designer><version>6.0.0</version>
  <briefInfo>Original fixture description</briefInfo><customNode key="original">preserve this</customNode>
</HnTheme>''')
        theme.writestr("icons", nested.getvalue())
        # These are deliberately opaque test bytes, never claimed as a usable device theme.
        theme.writestr("wallpaper/home.jpg", b"original wallpaper test data\x00\x01")
        theme.writestr("fonts/Original.ttf", b"original font test data\x02\x03")
        theme.writestr("com.android.systemui", b"original system module test data\x04\x05")
        theme.writestr("custom/notes.txt", b"Unknown files must be preserved.")
    return fixture


if __name__ == "__main__":
    print(create())
