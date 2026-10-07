"""Validate an actual v0.2 cached edit session after installing v0.3."""
from pathlib import Path
import argparse
import json

from archive_checks import read_theme
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
    ui.adb("shell", "pm", "clear", "dev.magicthemer")
    ui.adb("install", "-r", "-d", str(ROOT / "dist/Magic-Themer-0.2.0-debug.apk"))
    ui.adb("push", str(out / "QA Base pulled.hnt"), "/sdcard/Download/QA Base.hnt")
    ui.adb("shell", "rm", "-f", "'/sdcard/Download/QA Migrated Draft-edited.hnt'")
    ui.start()
    ui.tap(ui.await_text("Open .hnt"))
    ui.open_fixture("QA Base.hnt")
    ui.await_enabled("Save theme copy", timeout=45)
    ui.tap(ui.find("Theme"))
    ui.edit(ui.await_text("QA Base"), "QA Migrated Draft")
    ui.adb("shell", "am", "force-stop", "dev.magicthemer")
    old_files = ui.adb("shell", "run-as", "dev.magicthemer", "ls", "cache")
    assert "imported-" in old_files
    ui.adb("install", "-r", str(ROOT / "dist/Magic-Themer-0.3.0-debug.apk"))
    ui.start()
    ui.await_enabled("Save theme copy", timeout=45)
    ui.await_text("QA Migrated Draft")
    migrated_files = ui.adb("shell", "run-as", "dev.magicthemer", "ls", "files/theme-work")
    assert "imported-" in migrated_files and "theme-icons-" in migrated_files
    ui.tap(ui.find("Save theme copy"))
    ui.save_document()
    dismiss_saved(ui)
    saved = pull(ui, out, "QA Migrated Draft-edited.hnt")
    before, old_icons, old_xml = read_theme(out / "QA Base pulled.hnt")
    after, icons, xml = read_theme(saved)
    assert old_icons == icons
    for name in before:
        if name not in ("icons", "description.xml"):
            assert before[name] == after[name], name
    assert xml.tag == old_xml.tag and xml.findtext("version") == old_xml.findtext("version")
    assert xml.findtext("title") == "QA Migrated Draft"
    report = {"upgraded_from": "0.2.0", "active_source_migrated": True,
              "draft_metadata_preserved": True, "copy_save_preserved_modules_and_icons": True}
    (out / "migration-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("PASS: v0.2 cached session migrates; draft and saved copy are preserved", flush=True)


if __name__ == "__main__":
    main()
