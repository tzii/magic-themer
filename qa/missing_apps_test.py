"""Exercise missing-artwork classification and one-tap suggestions on an emulator."""
from pathlib import Path
import argparse
import json
import re

from ui_driver import Driver

ROOT = Path(__file__).resolve().parents[1]


def count(ui, prefix):
    node = ui.await_text(prefix + " (", contains=True)
    return int(re.search(r"\((\d+)\)", node.get("text", "")).group(1)), node


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    out = ROOT / "qa/output/v3"
    ui = Driver(args.serial, out, args.adb)
    ui.adb("shell", "pm", "clear", "dev.magicthemer")
    ui.start()
    ui.choose_pack()
    ui.tap(ui.await_text("Magic Themer QA Pack", contains=True))
    before, chip = count(ui, "Needs icon")
    assert before > 0
    ui.tap(chip)
    ui.edit(ui.await_text("Search apps or packages"), "com.android.vending")
    row = ui.await_app("com.android.vending")
    assert "Needs icon" in row.get("content-desc", "")
    assert "artwork unavailable" in row.get("content-desc", "")
    ui.screenshot("09-needs-icon.png")
    ui.tap(row)
    ui.await_text("Suggested icons (", contains=True)
    suggestion = ui.await_text("Play Store Alternative", contains=True)
    assert "Matches app name" in suggestion.get("content-desc", "") or "Known app name" in suggestion.get("content-desc", "")
    ui.screenshot("10-suggestions.png")
    ui.tap(suggestion)
    after, _ = count(ui, "Needs icon")
    assert after == before - 1, (before, after)
    ui.await_text("No apps match")
    _, customized = count(ui, "Customized")
    ui.tap(customized)
    assert "Customized" in ui.await_app("com.android.vending").get("content-desc", "")
    ui.screenshot("11-customized.png")
    ui.start()
    _, persisted_chip = count(ui, "Customized")
    assert persisted_chip.get("checked") == "true"
    node = ui.await_app("com.android.vending")
    assert "Customized" in node.get("content-desc", "")
    saved = ui.adb("shell", "run-as", "dev.magicthemer", "cat", "shared_prefs/builder.xml")
    assert "play_store_alternative" in saved
    ui.tap(node)
    ui.tap(ui.await_text("Reset"))
    reset_count, chip = count(ui, "Needs icon")
    assert reset_count == before
    ui.tap(chip)
    ui.edit(ui.await_text("Search apps or packages"), "com.android.vending")
    assert "Needs icon" in ui.await_app("com.android.vending").get("content-desc", "")
    assert "dev.magicthemer" not in ui.adb("logcat", "-d", "-b", "crash")
    report = {"missing_before": before, "missing_after_assignment": after,
              "invalid_mapped_resource_marked_missing": True, "one_tap_suggestion": True,
              "assignment_and_filter_survive_restart": True, "reset_restores_missing": True}
    (out / "missing-apps-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("PASS: missing resource, filtered count, ranked suggestion, restart and reset", flush=True)


if __name__ == "__main__":
    main()
