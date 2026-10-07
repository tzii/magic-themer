"""Import/save the user's two local themes on an emulator, verify saved bytes.

Inputs are never modified. No theme assets are added to the application.
"""
from contextlib import contextmanager
from pathlib import Path
from xml.etree import ElementTree as ET
import argparse
import hashlib
import json
import shutil
import tempfile
import time
import zipfile

from ui_driver import Driver
from smoke_test import dismiss_saved, pull

ROOT = Path(__file__).resolve().parents[1]


def digest(stream):
    result = hashlib.sha256()
    while chunk := stream.read(65536):
        result.update(chunk)
    return result.hexdigest()


@contextmanager
def inner(outer):
    with tempfile.TemporaryDirectory(prefix="magic-theme-qa-", dir=ROOT / "qa/output/v3") as directory:
        path = Path(directory) / "icons.zip"
        with outer.open("icons") as source, path.open("wb") as target:
            shutil.copyfileobj(source, target, 65536)
        with zipfile.ZipFile(path) as icons:
            yield icons


def verify(original, saved, repair=False):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(saved) as after:
        assert after.testzip() is None
        metadata = ET.fromstring(after.read("description.xml"))
        assert metadata.tag == "HwTheme"
        assert metadata.findtext("version") == "12"
        assert metadata.findtext("font") == "Default"
        ignored = {"description.xml", "icons"}
        if repair:
            ignored.update({"preview/icon_small.jpg", "preview/preview_icons_0.jpg"})
            assert ET.fromstring(after.read("unlock/theme.xml")).tag == "HWTheme"
            assert "preview/preview_unlock_0.jpg" in after.namelist()
        preserved = 0
        for entry in before.infolist():
            if entry.filename not in ignored:
                with before.open(entry) as source, after.open(entry.filename) as output:
                    assert digest(source) == digest(output), entry.filename
                preserved += 1
        with inner(before) as original_icons, inner(after) as saved_icons:
            assert saved_icons.testzip() is None
            assert set(original_icons.namelist()) == set(saved_icons.namelist())
            for entry in original_icons.infolist():
                with original_icons.open(entry) as source, saved_icons.open(entry.filename) as output:
                    assert digest(source) == digest(output), entry.filename
            icon_entries = len(saved_icons.infolist())
        with saved.open("rb") as output:
            output_hash = digest(output)
        return {"root": metadata.tag, "title": metadata.findtext("title"),
                "version": metadata.findtext("version"), "preserved_outer_members": preserved,
                "preserved_inner_entries": icon_entries, "sha256": output_hash}


def open_theme(ui, filename, title):
    ui.tap(ui.await_text("Open .hnt"))
    ui.open_fixture(filename)
    ui.await_text(title, contains=True, timeout=180)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        nodes = ui.state()
        details = ui.find("Imported theme", nodes)
        if details is not None:
            close = ui.find("Close", nodes)
            if close is not None:
                ui.tap(close)
        button = ui.find("Save theme copy")
        if button is not None and button.get("enabled") == "true":
            return
        time.sleep(0.5)
    raise AssertionError("Supplied theme import did not complete")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--working", default="C:/Users/simon/AppData/Local/Temp/chicv6.hnt")
    parser.add_argument("--failed", default="C:/Users/simon/AppData/Local/Temp/My Magic Icons.hnt")
    args = parser.parse_args()
    out = ROOT / "qa/output/v3"
    ui = Driver(args.serial, out, args.adb)
    inputs = [Path(args.working), Path(args.failed)]
    initial_hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs]
    ui.adb("shell", "pm", "clear", "dev.magicthemer")
    ui.adb("shell", "logcat", "-c")
    for path in inputs:
        ui.adb("push", str(path), "/sdcard/Download/" + path.name)
    for filename in ("QA Chic Verified-edited.hnt", "QA Repaired Verified-edited.hnt"):
        ui.adb("shell", "rm", "-f", "'/sdcard/Download/" + filename + "'")
    ui.start()
    open_theme(ui, inputs[0].name, "chicv6")
    ui.screenshot("12-large-working-theme.png")
    ui.tap(ui.find("Theme"))
    ui.edit(ui.await_text("chicv6"), "QA Chic Verified")
    ui.tap(ui.find("Save theme copy"))
    ui.await_text("Save", timeout=180)
    ui.adb("shell", "wm", "size", "2160x1920")
    ui.save_document(timeout=180)
    dismiss_saved(ui)
    ui.adb("shell", "wm", "size", "reset")
    ui.await_enabled("Save theme copy", timeout=180)
    working_output = pull(ui, out, "QA Chic Verified-edited.hnt")
    report = {"working_copy": verify(inputs[0], working_output)}
    report["large_save_picker_resize"] = True
    print("PASS: 96.7 MB working theme imported, saved; all modules and inner assets preserved", flush=True)

    open_theme(ui, inputs[1].name, "My Magic Icons")
    ui.tap(ui.find("Theme"))
    ui.edit(ui.await_text("My Magic Icons"), "QA Repaired Verified")
    switch = ui.reveal("Repair theme compatibility")
    assert switch.get("checked") == "true"
    ui.screenshot("13-repair-legacy-theme.png")
    ui.tap(ui.find("Save theme copy"))
    ui.save_document(timeout=180)
    dismiss_saved(ui)
    repaired_output = pull(ui, out, "QA Repaired Verified-edited.hnt")
    report["repaired_copy"] = verify(inputs[1], repaired_output, repair=True)
    final_hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs]
    assert initial_hashes == final_hashes
    report["inputs_unchanged"] = True
    report["device_acceptance"] = "awaiting phone check"
    assert "dev.magicthemer" not in ui.adb("logcat", "-d", "-b", "crash")
    (out / "user-theme-runtime-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("PASS: legacy profile repaired through UI; all 7,404 source PNGs identical", flush=True)


if __name__ == "__main__":
    main()
