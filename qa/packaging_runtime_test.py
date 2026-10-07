"""Emulator-only v4 SAF packaging checks; never changes the supplied input.

Runs against the APKs already built by the caller. The only remote documents
created or removed by this script have the explicit QA V4 names below.
"""
from contextlib import contextmanager
from pathlib import Path
from xml.etree import ElementTree as ET
import argparse
import hashlib
import json
import re
import struct
import tempfile
import time
import zipfile

from archive_checks import check_new
from smoke_test import dismiss_saved, pull
from ui_driver import Driver
from user_theme_runtime_test import open_theme

ROOT = Path(__file__).resolve().parents[1]
MAX_ARCHIVE = 256 * 1024 * 1024
MAX_MEMBER = 128 * 1024 * 1024
CHUNK = 65536
PACKAGES = {"com.android.settings", "dev.magicthemer", "dev.magicthemer.fixture", "com.example.future"}
REMOTE_NAMES = ("QA V4 Base.hnt", "QA V4 Untouched.hnt", "QA V4 Merged-edited.hnt", "QA V4 Fresh.hnt")
PACKAGE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+")


def sha256(path):
    size = Path(path).stat().st_size
    assert 0 < size <= MAX_ARCHIVE, "Archive exceeds the bounded QA input size"
    value = hashlib.sha256()
    total = 0
    with Path(path).open("rb") as source:
        while chunk := source.read(CHUNK):
            total += len(chunk)
            assert total <= MAX_ARCHIVE
            value.update(chunk)
    assert total == size
    return value.hexdigest()


def range_hash(path, start, length):
    assert 0 <= start and 0 <= length <= MAX_ARCHIVE
    value = hashlib.sha256()
    with Path(path).open("rb") as source:
        source.seek(start)
        remaining = length
        while remaining:
            chunk = source.read(min(CHUNK, remaining))
            assert chunk, "Truncated raw ZIP record"
            value.update(chunk)
            remaining -= len(chunk)
    return value.hexdigest()


def raw_records(archive):
    """Hash complete physical local records, including any data descriptors."""
    entries = sorted(archive.infolist(), key=lambda item: item.header_offset)
    assert not entries or entries[0].header_offset == 0
    records = {}
    for index, info in enumerate(entries):
        end = entries[index + 1].header_offset if index + 1 < len(entries) else archive.start_dir
        assert end >= info.header_offset
        records[info.filename] = range_hash(archive.filename, info.header_offset, end - info.header_offset)
    return records


def assert_known_header(archive, info):
    """Modified/new records must carry matching CRC/sizes, without bit 3."""
    with Path(archive.filename).open("rb") as source:
        source.seek(info.header_offset)
        header = source.read(30)
    assert len(header) == 30
    signature, version, flags, method, _, _, crc, packed, size, name_len, extra_len = struct.unpack("<IHHHHHIIIHH", header)
    assert signature == 0x04034B50, info.filename
    assert flags == info.flag_bits and not flags & 8, info.filename
    assert method == info.compress_type, info.filename
    assert crc == info.CRC and packed == info.compress_size and size == info.file_size, info.filename
    assert 0 < name_len <= 512 and extra_len <= 65535 and version <= 20, info.filename
    return {"flags": flags, "crc": crc, "compressed": packed, "uncompressed": size}


@contextmanager
def inner(outer, output):
    """Extract only the extensionless inner ZIP to a bounded temporary file."""
    info = outer.getinfo("icons")
    assert info.file_size <= MAX_MEMBER
    with tempfile.TemporaryDirectory(prefix="v4-icons-", dir=output) as directory:
        path = Path(directory) / "icons.zip"
        total = 0
        with outer.open(info) as source, path.open("wb") as destination:
            while chunk := source.read(CHUNK):
                total += len(chunk)
                assert total <= MAX_MEMBER
                destination.write(chunk)
        assert total == info.file_size
        with zipfile.ZipFile(path) as icons:
            yield icons


def package_of(name):
    if name.startswith("dynamic_icons/") or not name.endswith(".png"):
        return None
    package = name.rsplit("/", 1)[-1][:-4]
    return package if PACKAGE.fullmatch(package) else None


def verify_merge(original, saved, output):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(saved) as after:
        assert after.testzip() is None
        metadata = ET.fromstring(after.read("description.xml"))
        assert metadata.findtext("title") == "QA V4 Merged"
        old_records, new_records = raw_records(before), raw_records(after)
        preserved = set(old_records) - {"description.xml", "icons"}
        assert all(new_records[name] == old_records[name] for name in preserved), "Unrelated outer local record changed"
        previews = {name for name in preserved if name.startswith("preview/")}
        assert previews and all(new_records[name] == old_records[name] for name in previews)
        outer_headers = {name: assert_known_header(after, after.getinfo(name)) for name in ("description.xml", "icons")}
        with inner(before, output) as old_icons, inner(after, output) as new_icons:
            assert new_icons.testzip() is None
            old_raw, new_raw = raw_records(old_icons), raw_records(new_icons)
            unchanged = {name for name in old_raw if package_of(name) not in PACKAGES}
            assert all(new_raw[name] == old_raw[name] for name in unchanged), "Untouched inner local record changed"
            assert set(old_raw) <= set(new_raw), "Merge removed existing icon records"
            inner_headers = {}
            for package in sorted(PACKAGES):
                matches = [entry for entry in new_icons.infolist() if package_of(entry.filename) == package]
                assert matches, f"Whole-pack merge omitted {package}"
                for entry in matches:
                    inner_headers[entry.filename] = assert_known_header(new_icons, entry)
            altered = [name for name in new_raw if name not in old_raw or new_raw[name] != old_raw[name]]
            assert altered and all(package_of(name) in PACKAGES for name in altered)
        return {"sha256": sha256(saved), "preserved_outer_raw_records": len(preserved),
                "preserved_preview_raw_records": len(previews), "preserved_inner_raw_records": len(unchanged),
                "changed_inner_records": sorted(altered), "outer_headers": outer_headers, "inner_headers": inner_headers}


def filename_field(ui):
    nodes = ui.state()
    candidates = [node for node in nodes if node.get("class", "").endswith("EditText")
                  and node.get("enabled") == "true" and node.get("bounds") != "[0,0][0,0]"]
    named = [node for node in candidates if any(word in node.get("resource-id", "").lower() for word in ("filename", "file_name"))]
    if len(named) == 1:
        return named[0]
    assert len(candidates) == 1, "Ambiguous filename input; refuse to type into an unobserved field"
    return candidates[0]


def save_as(ui, output, filename, screenshot=None):
    ui.await_text("Save", timeout=180)
    ui.downloads()
    ui.edit(filename_field(ui), filename)
    ui.tap(ui.await_text("Save"))
    ui.await_text("Theme saved", timeout=180)
    nodes = ui.state()
    dialog = "\n".join(node.get("text", "") for node in nodes)
    path = pull(ui, output, filename)
    value, size = sha256(path), path.stat().st_size
    assert f"File: {filename}" in dialog, "Save dialog did not report the actual provider filename"
    assert f"{size} bytes verified" in dialog, "Save dialog byte count differs from pulled file"
    assert f"SHA-256: {value}" in dialog, "Save dialog hash differs from pulled file"
    assert "unexpected extension" not in dialog.lower()
    if screenshot:
        ui.screenshot(screenshot)
    dismiss_saved(ui)
    return path, {"filename": filename, "bytes": size, "sha256": value, "dialog_matches_saved_file": True}


def edit_theme_name(ui, previous, value):
    ui.tap(ui.await_text("Theme"))
    for _ in range(8):
        nodes = ui.state()
        field = next((node for node in nodes if node.get("class", "").endswith("EditText")
                      and node.get("text") == previous and node.get("bounds") != "[0,0][0,0]"), None)
        if field is not None:
            ui.edit(field, value)
            return
        scroll = next((node for node in nodes if node.get("scrollable") == "true"
                       and node.get("class", "").endswith("ScrollView")), None)
        assert scroll is not None, "Theme form scroll container missing"
        left, top, right, bottom = ui.bounds(scroll)
        x = (left + right) // 2
        ui.adb("shell", "input", "swipe", str(x), str(top + (bottom - top) // 4),
               str(x), str(top + (bottom - top) * 3 // 4), "300")
        time.sleep(0.3)
    raise AssertionError("Theme name input could not be revealed")


def fresh_theme(ui, output):
    ui.tap(ui.await_text("More options"))
    ui.tap(ui.await_text("New theme"))
    ui.await_enabled("Save theme", timeout=180)
    edit_theme_name(ui, "My Magic Icons", "QA V4 Fresh")
    whole = ui.reveal("Whole icon pack")
    assert whole.get("checked") == "true"
    ui.tap(ui.await_enabled("Save theme"))
    path, verified = save_as(ui, output, "QA V4 Fresh.hnt")
    contents = check_new(path, sorted(PACKAGES), "QA V4 Fresh")
    with zipfile.ZipFile(path) as outer:
        outer_headers = {info.filename: assert_known_header(outer, info) for info in outer.infolist()}
        with inner(outer, output) as icons:
            inner_headers = {info.filename: assert_known_header(icons, info) for info in icons.infolist()}
    return {"verification": verified, "contents": contents, "outer_headers": outer_headers, "inner_headers": inner_headers}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--working", default="C:/Users/simon/AppData/Local/Temp/chicv6 (1).hnt")
    parser.add_argument("--output", default="qa/output/v4")
    args = parser.parse_args()
    output = ROOT / args.output
    ui = Driver(args.serial, output, args.adb)
    assert ui.adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Refuse to reset a non-emulator device"
    working = Path(args.working).resolve()
    original_hash = sha256(working)
    with zipfile.ZipFile(working) as original:
        original_title = ET.fromstring(original.read("description.xml")).findtext("title")
    assert original_title
    ui.adb("install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    ui.adb("install", "-r", str(ROOT / "qa/iconpack/build/outputs/apk/debug/iconpack-debug.apk"))
    ui.adb("shell", "pm", "clear", "dev.magicthemer")
    ui.adb("shell", "logcat", "-c")
    for name in REMOTE_NAMES:
        ui.adb("shell", "rm", "-f", "'/sdcard/Download/" + name + "'")
    ui.adb("push", str(working), "/sdcard/Download/QA V4 Base.hnt")
    ui.start()
    open_theme(ui, "QA V4 Base.hnt", original_title)
    ui.tap(ui.await_enabled("Save theme copy", timeout=180))
    untouched, untouched_verification = save_as(ui, output, "QA V4 Untouched.hnt", "01-save-verification-dialog.png")
    assert sha256(untouched) == original_hash, "No-op SAF export was not byte-identical to the original"
    report = {"no_op": {"byte_identical": True, "verification": untouched_verification}}
    print("PASS: real SAF no-op copy byte-identical; reported name, bytes and SHA-256 match", flush=True)
    # Evidence is already retained on the host; avoid keeping several large
    # copies in the emulator's smaller data partition during the next export.
    ui.adb("shell", "rm", "-f", "'/sdcard/Download/QA V4 Untouched.hnt'")

    ui.tap(ui.await_text("Choose pack"))
    ui.tap(ui.await_text("Magic Themer QA Pack", contains=True))
    ui.await_enabled("Save theme copy", timeout=180)
    edit_theme_name(ui, original_title, "QA V4 Merged")
    assert ui.reveal("Whole icon pack").get("checked") == "true"
    ui.tap(ui.reveal("Apply whole pack to theme"))
    assert ui.reveal("Replace existing icon set").get("checked") == "false"
    ui.tap(ui.await_enabled("Save theme copy", timeout=180))
    merged, merged_verification = save_as(ui, output, "QA V4 Merged-edited.hnt")
    report["merged"] = verify_merge(working, merged, output)
    report["merged"]["verification"] = merged_verification
    print("PASS: imported icon merge preserves unrelated raw records and previews; modified headers have CRC/sizes and no descriptors", flush=True)
    ui.adb("shell", "rm", "-f", "'/sdcard/Download/QA V4 Merged-edited.hnt'")

    report["fresh"] = fresh_theme(ui, output)
    print("PASS: fresh whole-pack export includes four mappings; both ZIP layers have complete local headers", flush=True)
    assert sha256(working) == original_hash, "Supplied host input changed"
    remote_hash = ui.adb("shell", "sha256sum", "'/sdcard/Download/QA V4 Base.hnt'").split()[0]
    assert remote_hash == original_hash, "Imported emulator input changed"
    crashes = ui.adb("logcat", "-d", "-b", "crash")
    assert "dev.magicthemer" not in crashes, crashes
    report.update({"input_sha256": original_hash, "inputs_unchanged": True, "app_crash_entries": False,
                   "device_acceptance": "awaiting phone check"})
    (output / "packaging-runtime-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("All v4 emulator packaging checks passed; HONOR Themes acceptance needs phone testing.", flush=True)


if __name__ == "__main__":
    main()
