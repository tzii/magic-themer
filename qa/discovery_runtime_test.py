"""Emulator-only check of SAF publication, actual ZIP bytes and fallback help.

Uses the caller-built APK and synthetic QA icon pack. Only the main app's data
and the explicitly named QA output are reset. No HONOR refresh is simulated.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import time

from archive_checks import check_new
from packaging_runtime_test import edit_theme_name, filename_field
from smoke_test import pull
from ui_driver import Driver


ROOT = Path(__file__).resolve().parents[1]
FILENAME = "QA_V6_Discovery.hnt"
PACKAGES = ["com.android.settings", "dev.magicthemer", "dev.magicthemer.fixture", "com.example.future"]
EXPECTED_ENTRIES = {
    "preview/preview_icons_0.jpg", "preview/preview_unlock_0.jpg", "preview/icon_small.jpg", "preview/",
    "unlock/theme.xml", "unlock/", "description.xml", "icons",
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    out = ROOT / "qa/output/v6"
    ui = Driver(args.serial, out, args.adb)
    assert not ui.adb("shell", "pm", "path", "com.hihonor.android.thememanager", check=False).strip()
    assert not ui.adb("shell", "pm", "path", "com.huawei.android.thememanager", check=False).strip()
    ui.adb("install", "-r", str(args.apk))
    ui.adb("install", "-r", str(ROOT / "qa/iconpack/build/outputs/apk/debug/iconpack-debug.apk"))
    ui.adb("shell", "pm", "clear", "dev.magicthemer")
    ui.adb("shell", "rm", "-f", "/sdcard/Download/" + FILENAME)
    ui.start()
    ui.tap(ui.await_text("Choose pack"))
    ui.tap(ui.await_text("Magic Themer QA Pack", contains=True))
    ui.await_text("7 icons ready to browse.")
    edit_theme_name(ui, "Magic Themer QA Pack", "QA V6 Discovery")
    ui.tap(ui.await_enabled("Save theme"))
    ui.await_text("Save", timeout=60)
    ui.downloads()
    ui.edit(filename_field(ui), FILENAME)

    watcher = subprocess.Popen(
        [args.adb, "-s", args.serial, "shell", "timeout", "60", "toybox", "inotifyd", "-", "/sdcard/Download:mnwy"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    try:
        time.sleep(0.4)
        assert watcher.poll() is None, "Could not start the file-arrival observer"
        ui.tap(ui.await_text("Save"))
        ui.await_text("Theme saved", timeout=60)
        dialog = "\n".join(node.get("text", "") for node in ui.state())
        assert "App info" in dialog and "Force stop" in dialog
        ui.screenshot("01-verified-save.png")
        path = pull(ui, out, FILENAME)
        value = hashlib.sha256(path.read_bytes()).hexdigest()
        assert f"File: {FILENAME}" in dialog
        assert f"{path.stat().st_size} bytes verified" in dialog and f"SHA-256: {value}" in dialog
        time.sleep(0.3)
    finally:
        watcher.terminate()
        events_text, observer_errors = watcher.communicate(timeout=5)
        (out / "publication-events.txt").write_text(events_text, encoding="utf-8")

    events = [line.split(None, 2) for line in events_text.splitlines() if len(line.split(None, 2)) == 3]
    # This Android Toybox build emits m for the source and y for the target
    # of a rename (the event trace preserves both sides for inspection).
    final_arrivals = [index for index, (mask, _, name) in enumerate(events) if name == FILENAME and "y" in mask]
    assert final_arrivals, f"No final rename arrival observed: {events_text!r}; {observer_errors}"
    final_index = final_arrivals[-1]
    temporary_writes = [index for index, (mask, _, name) in enumerate(events)
                        if name.startswith(FILENAME + ".") and name.endswith(".partial") and "w" in mask]
    assert temporary_writes and max(temporary_writes) < final_index, "Final file arrived before staged writes closed"
    assert not any(name == FILENAME and "w" in mask for mask, _, name in events[final_index + 1:])
    leftovers = [name for name in ui.adb("shell", "ls", "-A", "/sdcard/Download").splitlines()
                 if FILENAME in name and name.endswith(".partial")]
    assert not leftovers, f"Staging document was left behind: {leftovers}"
    contents = check_new(path, PACKAGES, "QA V6 Discovery")
    assert set(contents["entries"]) == EXPECTED_ENTRIES

    # A generic emulator has no HONOR/Huawei Themes. Both entry points must
    # fall back cleanly without trying to launch an invented importer action.
    ui.tap(ui.await_text("Open Themes"))
    ui.await_text("Open HONOR Themes manually")
    ui.tap(ui.await_text("Close"))
    ui.tap(ui.await_text("More options"))
    ui.tap(ui.await_text("Apply help"))
    ui.await_text("Apply your theme")
    ui.screenshot("02-apply-guide.png")
    ui.tap(ui.await_text("App info"))
    ui.await_text("Open HONOR Themes manually")
    ui.tap(ui.await_text("Close"))
    report = {"contents": contents, "saved_dialog_matches_bytes": True,
              "staged_close_before_final_move": True, "staging_leftovers": leftovers,
              "events": events, "missing_themes_fallback": True, "honor_discovery_verified": False}
    (out / "discovery-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("PASS: verified A-layout archive is published by rename after writing; save/help fallbacks work", flush=True)


if __name__ == "__main__":
    main()
