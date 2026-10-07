"""Assert actual saved theme contents after the Android document-picker flow."""
from pathlib import Path
from xml.etree import ElementTree as ET
import hashlib
import io
import struct
import zipfile


def read_theme(path):
    with zipfile.ZipFile(path) as outer:
        assert outer.testzip() is None
        members = {name: outer.read(name) for name in outer.namelist()}
    with zipfile.ZipFile(io.BytesIO(members["icons"])) as inner:
        assert inner.testzip() is None
        icons = {name: inner.read(name) for name in inner.namelist()}
    return members, icons, ET.fromstring(members["description.xml"])


def check_png(data, size=192):
    assert data.startswith(b"\x89PNG\r\n\x1a\n")
    assert struct.unpack(">II", data[16:24]) == (size, size)


def check_new(path, packages, title):
    outer, icons, metadata = read_theme(path)
    assert metadata.findtext("title") == title
    assert metadata.tag == "HwTheme"
    assert metadata.findtext("version") == "12"
    assert metadata.findtext("font") == "Default"
    assert ET.fromstring(outer["unlock/theme.xml"]).tag == "HWTheme"
    assert "preview/preview_unlock_0.jpg" in outer
    assert sorted(icons) == sorted(package + ".png" for package in packages)
    for data in icons.values():
        check_png(data)
    return {"entries": sorted(outer), "icons": sorted(icons), "title": title,
            "sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest()}


def check_edit(original, edited, title, expected_packages):
    before, old_icons, original_xml = read_theme(original)
    after, icons, metadata = read_theme(edited)
    for name, data in before.items():
        if name not in ("icons", "description.xml", "preview/icon_small.jpg", "preview/preview_icons_0.jpg"):
            assert after[name] == data, f"Changed unrelated module: {name}"
    assert metadata.tag == original_xml.tag
    assert metadata.attrib == original_xml.attrib
    assert metadata.findtext("version") == original_xml.findtext("version")
    assert ET.tostring(metadata.find("customNode")) == ET.tostring(original_xml.find("customNode"))
    assert metadata.findtext("title") == title
    for name in ("config.xml", "dynamic/assets.bin"):
        assert icons[name] == old_icons[name], f"Changed icon configuration: {name}"
    assert icons["org.example.uninstalled.png"] == old_icons["org.example.uninstalled.png"]
    assert icons["com.android.settings.png"] != old_icons["com.android.settings.png"]
    assert sorted(name[:-4] for name in icons if name.endswith(".png")) == sorted(expected_packages)
    for name, data in icons.items():
        if name.endswith(".png"):
            check_png(data)
    return {"preserved_modules": sorted(name for name in before if name not in ("icons", "description.xml")),
            "icons": sorted(icons), "root": metadata.tag, "version": metadata.findtext("version"),
            "sha256": hashlib.sha256(Path(edited).read_bytes()).hexdigest()}
