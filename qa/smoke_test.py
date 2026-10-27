"""Material 3 conversion/editor smoke test. Only resets our apps on an emulator."""
from pathlib import Path
import argparse
import hashlib
import json
import time

from archive_checks import check_edit, check_new, read_theme
from build_theme_fixture import create
from ui_driver import Driver

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--editor-only", action="store_true", help="Resume editor checks after conversion passed")
    parser.add_argument("--output", default="qa/output/v3")
    args = parser.parse_args()
    out = ROOT / args.output
    ui = Driver(args.serial, out, args.adb)
    (out / "archive-report.json").unlink(missing_ok=True)
    ui.adb("shell", "cmd", "uimode", "night", "no")
    fixture = create()
    original_hash = hashlib.sha256(fixture.read_bytes()).hexdigest()
    if args.editor_only:
        report = {"whole": check_new(out / "QA Whole Pack.hnt",
                  ["com.android.settings", "dev.magicthemer", "dev.magicthemer.fixture", "com.example.future"], "QA Whole Pack"),
                  "installed": check_new(out / "QA Installed.hnt",
                  ["com.android.settings", "dev.magicthemer", "dev.magicthemer.fixture"], "QA Installed")}
        ui.start()
        ui.await_text("Magic Themer")
        editor_checks(ui, out, fixture, original_hash, report)
        return
    for name in ("QA Whole Pack.hnt", "QA Installed.hnt", "QA Edited-edited.hnt", "QA Merged-edited.hnt"):
        ui.adb("shell", "rm", "-f", "'/sdcard/Download/" + name + "'")
    ui.adb("push", str(fixture), "/sdcard/Download/QA Base.hnt")
    ui.adb("install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    ui.adb("uninstall", "dev.magicthemer.fixture", check=False)
    ui.adb("shell", "pm", "clear", "dev.magicthemer")
    ui.adb("logcat", "-c")
    ui.start()
    ui.await_text("Choose pack")
    assert ui.await_text("Save theme").get("enabled") == "false"
    ui.screenshot("01-empty.png")
    print("PASS: empty state and disabled export", flush=True)

    ui.adb("install", "-r", str(ROOT / "qa/iconpack/build/outputs/apk/debug/iconpack-debug.apk"))
    ui.start()
    ui.choose_pack()
    ui.tap(ui.await_text("Magic Themer QA Pack", contains=True))
    ui.await_text("Pack icons")
    time.sleep(1)
    ui.screenshot("02-apps.png")

    ui.tap(ui.find("Pack icons"))
    ui.await_text("Alternative tile", contains=True)
    ui.screenshot("03-full-catalogue.png")
    print("PASS: complete drawable catalogue includes unmapped alternative", flush=True)

    ui.tap(ui.find("Apps"))
    ui.edit(ui.await_text("Search apps or packages"), "Magic Themer")
    ui.tap(ui.await_app("dev.magicthemer"))
    ui.tap(ui.await_text("Alternative tile", contains=True))
    ui.start()
    ui.await_text("Pack icons")
    preferences = ui.adb("shell", "run-as", "dev.magicthemer", "ls", "shared_prefs")
    assert preferences.strip()
    saved = ui.adb("shell", "run-as", "dev.magicthemer", "cat", "shared_prefs/builder.xml")
    assert "alternative_tile" in saved
    print("PASS: visual assignment survives restart", flush=True)

    ui.tap(ui.find("Theme"))
    whole = ui.await_text("Whole icon pack", contains=True)
    assert whole.get("checked") == "true"
    ui.edit(ui.await_text("Magic Themer QA Pack"), "QA Whole Pack")
    ui.tap(ui.find("Save theme"))
    ui.save_document()
    dismiss_saved(ui)
    whole_path = pull(ui, out, "QA Whole Pack.hnt")
    report = {"whole": check_new(whole_path,
        ["com.android.settings", "dev.magicthemer", "dev.magicthemer.fixture", "com.example.future"], "QA Whole Pack")}
    print("PASS: whole pack exports absent-app mapping and skips missing drawable", flush=True)

    ui.tap(ui.find("Theme"))
    ui.tap(ui.await_text("Whole icon pack", contains=True))

    ui.edit(ui.await_text("QA Whole Pack"), "QA Installed")
    ui.tap(ui.find("Save theme"))
    ui.save_document()
    dismiss_saved(ui)
    installed = pull(ui, out, "QA Installed.hnt")
    report["installed"] = check_new(installed,
        ["com.android.settings", "dev.magicthemer", "dev.magicthemer.fixture"], "QA Installed")
    print("PASS: installed-only scope excludes absent-app mapping", flush=True)

    ui.tap(ui.await_text("Whole icon pack", contains=True))

    editor_checks(ui, out, fixture, original_hash, report)


def editor_checks(ui, out, fixture, original_hash, report):
    original_hash = ui.adb("shell", "sha256sum", "'/sdcard/Download/QA Base.hnt'").split()[0]
    for name in ("QA Edited-edited.hnt", "QA Merged-edited.hnt"):
        ui.adb("shell", "rm", "-f", "'/sdcard/Download/" + name + "'")
    ui.tap(ui.find("Theme"))
    if ui.await_text("Whole icon pack").get("checked") != "true":
        ui.tap(ui.find("Whole icon pack"))

    ui.open_theme_file()
    ui.open_fixture("QA Base.hnt")
    ui.await_text("Save theme copy", timeout=45)
    ui.tap(ui.find("Theme"))
    ui.edit(ui.await_text("QA Base"), "QA Edited")
    ui.tap(ui.find("Apps"))
    ui.edit(ui.await_text("Search apps or packages"), "Settings")
    ui.tap(ui.await_app("com.android.settings"))
    ui.tap(ui.await_text("Alternative tile", contains=True))
    ui.start()
    ui.await_text("Save theme copy", timeout=45)
    ui.tap(ui.find("Save theme copy"))
    ui.save_document()
    dismiss_saved(ui)
    edited = pull(ui, out, "QA Edited-edited.hnt")
    report["edited"] = check_edit(fixture, edited, "QA Edited", ["com.android.settings", "org.example.uninstalled"])
    print("PASS: edited theme copy preserves opaque modules and unknown metadata", flush=True)

    ui.tap(ui.find("Theme"))
    ui.tap(ui.reveal("Apply whole pack to theme"))
    ui.edit(ui.await_text("QA Edited"), "QA Merged")
    ui.tap(ui.find("Save theme copy"))
    ui.save_document()
    dismiss_saved(ui)
    merged = pull(ui, out, "QA Merged-edited.hnt")
    report["merged"] = check_edit(fixture, merged, "QA Merged",
        ["com.android.settings", "org.example.uninstalled", "dev.magicthemer", "dev.magicthemer.fixture", "com.example.future"])
    print("PASS: explicit whole-pack merge preserves unrelated existing icon", flush=True)
    remote_original = out / "QA Base pulled.hnt"
    ui.adb("pull", "/sdcard/Download/QA Base.hnt", str(remote_original))
    assert hashlib.sha256(remote_original.read_bytes()).hexdigest() == original_hash
    print("PASS: imported source archive remains byte-identical", flush=True)

    ui.adb("shell", "cmd", "uimode", "night", "yes")
    ui.adb("shell", "wm", "size", "2160x1920")
    time.sleep(2)
    ui.await_text("Save theme copy")
    ui.tap(ui.find("Pack icons"))
    time.sleep(1)
    ui.screenshot("04-wide-dark.png")
    ui.adb("shell", "wm", "size", "reset")
    ui.adb("shell", "cmd", "uimode", "night", "no")
    report["source_unchanged"] = True
    report["magic_os_applied"] = False
    (out / "archive-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    crashes = ui.adb("logcat", "-d", "-b", "crash")
    assert "dev.magicthemer" not in crashes, crashes
    print("PASS: wide dark mode and no app crash entries", flush=True)
    print("All emulator checks passed. HONOR Themes acceptance needs phone testing.", flush=True)


def pull(ui, out, filename):
    target = out / filename
    ui.adb("pull", "/sdcard/Download/" + filename, str(target))
    return target


def dismiss_saved(ui):
    # The explanatory apply dialog may offer Close or Done.
    ui.await_text("Theme saved", timeout=30)
    nodes = ui.state()
    for label in ("Close", "Done", "OK"):
        node = ui.find(label, nodes)
        if node is not None:
            ui.tap(node)
            return


if __name__ == "__main__":
    main()
