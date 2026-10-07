"""Continue a completed editor smoke session; rotate while a save picker is open."""
from pathlib import Path
import argparse
import json
import time
from ui_driver import Driver
from archive_checks import check_edit

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5556")
parser.add_argument("--adb", default="adb")
parser.add_argument("--output", default="qa/output/v3")
args = parser.parse_args()
out = ROOT / args.output
ui = Driver(args.serial, out, args.adb)
ui.adb("shell", "rm", "-f", "'/sdcard/Download/QA Rotation-edited.hnt'")
ui.await_text("Save theme copy")
ui.screenshot("05-gallery-light.png")
ui.tap(ui.find("Choose pack"))
ui.await_text("Choose icon pack")
ui.screenshot("06-pack-picker.png")
ui.tap(ui.find("Close"))
ui.tap(ui.find("Apps"))
ui.edit(ui.await_text("Search apps or packages"), "Settings")
ui.tap(ui.await_app("com.android.settings"))
ui.await_text("Alternative tile", contains=True)
ui.screenshot("07-icon-picker.png")
ui.tap(ui.find("Close"))
ui.tap(ui.find("Theme"))
ui.edit(ui.await_text("QA Merged"), "QA Rotation")
ui.tap(ui.find("Save theme copy"))
ui.await_text("Save", timeout=60)
ui.adb("shell", "wm", "size", "2160x1920")
time.sleep(1)
ui.save_document()
ui.await_text("Theme saved", timeout=45)
ui.tap(ui.find("Done"))
target = out / "QA Rotation-edited.hnt"
ui.adb("pull", "/sdcard/Download/QA Rotation-edited.hnt", str(target))
report = check_edit(out / "QA Base pulled.hnt", target, "QA Rotation",
    ["com.android.settings", "org.example.uninstalled", "dev.magicthemer", "dev.magicthemer.fixture", "com.example.future"])
ui.adb("shell", "wm", "size", "reset")
ui.await_text("Save theme copy")
ui.tap(ui.find("Theme"))
ui.await_text("QA Rotation")
ui.screenshot("08-editor-light.png")
(out / "rotation-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
assert "dev.magicthemer" not in ui.adb("logcat", "-d", "-b", "crash")
print("PASS: save picker rotation preserves pending export and edit session", flush=True)
