"""Check the final APK creates a new whole-pack theme from durable staging."""
from pathlib import Path
import argparse
import json
from archive_checks import check_new
from ui_driver import Driver
from smoke_test import dismiss_saved, pull

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    out = ROOT / "qa/output/v3"
    ui = Driver(args.serial, out, args.adb)
    ui.adb("shell", "pm", "clear", "com.tzii.magicthemer")
    ui.adb("shell", "rm", "-f", "'/sdcard/Download/QA Final Pack.hnt'")
    ui.start()
    ui.choose_pack()
    ui.tap(ui.await_text("Magic Themer QA Pack", contains=True))
    ui.await_text("7 icons ready to browse.")
    ui.tap(ui.find("Theme"))
    ui.edit(ui.await_text("Magic Themer QA Pack"), "QA Final Pack")
    ui.tap(ui.find("Save theme"))
    ui.await_text("Save", timeout=60)
    files = ui.adb("shell", "run-as", "com.tzii.magicthemer", "ls", "files/theme-work")
    assert "export-" in files
    ui.save_document()
    dismiss_saved(ui)
    path = pull(ui, out, "QA Final Pack.hnt")
    report = check_new(path,
        ["com.android.settings", "com.tzii.magicthemer", "dev.magicthemer.fixture", "com.example.future"], "QA Final Pack")
    assert "com.tzii.magicthemer" not in ui.adb("logcat", "-d", "-b", "crash")
    (out / "final-new-export-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("PASS: final APK stages a new theme privately and saves the whole mapped pack", flush=True)


if __name__ == "__main__":
    main()
