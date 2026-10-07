"""Inspect user-provided themes as data and create a personal repair from a working base.

Neither input is modified. No supplied artwork is bundled with the app.
"""
from pathlib import Path
from collections import Counter
from xml.etree import ElementTree as ET
import argparse
import hashlib
import json
import re
import shutil
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FAILED = Path(r"C:\Users\simon\AppData\Local\Temp\My Magic Icons.hnt")
BASE = Path(r"C:\Users\simon\AppData\Local\Temp\chicv6.hnt")
OUT = ROOT / "qa/output/v3"
PKG = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+\.png\Z")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def extract_icons(path, target):
    with zipfile.ZipFile(path) as theme, theme.open("icons") as stream, target.open("wb") as output:
        shutil.copyfileobj(stream, output, 256 * 1024)


def inspect(path, nested):
    with zipfile.ZipFile(path) as theme, zipfile.ZipFile(nested) as icons:
        entries = icons.infolist()
        return {
            "file": path.name, "bytes": path.stat().st_size, "sha256": digest(path),
            "metadata": theme.read("description.xml").decode("utf-8"),
            "outer_entries": len(theme.infolist()), "inner_entries": len(entries),
            "outer_expanded_bytes": sum(e.file_size for e in theme.infolist()),
            "inner_expanded_bytes": sum(e.file_size for e in entries),
            "static_package_icons": sum(bool(PKG.fullmatch(e.filename)) for e in entries),
            "aliases": [e.filename for e in entries if e.filename.startswith(":")],
            "non_package_samples": [e.filename for e in entries if not PKG.fullmatch(e.filename)][:30],
            "outer_methods": dict(Counter(e.compress_type for e in theme.infolist())),
            "inner_methods": dict(Counter(e.compress_type for e in entries)),
        }


def copy_entry(source, entry, destination):
    # Write member bytes through streams; do not extract archive-supplied paths.
    info = zipfile.ZipInfo(entry.filename, entry.date_time)
    info.compress_type = zipfile.ZIP_DEFLATED if not entry.is_dir() else zipfile.ZIP_STORED
    info.external_attr = entry.external_attr
    info.comment = entry.comment
    with source.open(entry) as incoming, destination.open(info, "w") as outgoing:
        shutil.copyfileobj(incoming, outgoing, 256 * 1024)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--failed", type=Path, default=FAILED, help="Theme whose package icons should replace the working base's icons")
    parser.add_argument("--working", type=Path, default=BASE, help="Known-working theme whose other modules are preserved")
    parser.add_argument("--output", type=Path, default=ROOT / "dist/My Magic Icons Fixed.hnt", help="New repaired theme path")
    parser.add_argument("--report-dir", type=Path, default=OUT, help="Directory for the comparison report and temporary ZIPs")
    args = parser.parse_args()
    failed, base = args.failed.resolve(), args.working.resolve()
    fixed, report_dir = args.output.resolve(), args.report_dir.resolve()
    report_file = report_dir / "supplied-theme-comparison.json"
    if fixed == report_file or (fixed.exists() and report_file.exists() and fixed.samefile(report_file)):
        parser.error("The repaired output must use a different path from the comparison report.")
    for destination in (fixed, report_file):
        if destination in (failed, base) or (destination.exists() and any(
            source.exists() and destination.samefile(source) for source in (failed, base)
        )):
            parser.error("The output and report must not overwrite either input theme.")
    hashes = {str(p): digest(p) for p in (failed, base)}
    fixed.parent.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="compare-", dir=report_dir) as work:
        work = Path(work)
        failed_icons, base_icons = work / "failed-icons.zip", work / "base-icons.zip"
        extract_icons(failed, failed_icons)
        extract_icons(base, base_icons)
        report = {"failed": inspect(failed, failed_icons), "working": inspect(base, base_icons)}
        merged_icons = work / "merged-icons.zip"
        with zipfile.ZipFile(failed_icons) as incoming, zipfile.ZipFile(base_icons) as original:
            incoming_names = {e.filename for e in incoming.infolist() if PKG.fullmatch(e.filename)}
            with zipfile.ZipFile(merged_icons, "w", zipfile.ZIP_DEFLATED) as merged:
                for entry in original.infolist():
                    if entry.filename not in incoming_names:
                        copy_entry(original, entry, merged)
                for entry in incoming.infolist():
                    if entry.filename in incoming_names:
                        copy_entry(incoming, entry, merged)
            report["replaced_package_icons"] = len(incoming_names)
            report["preserved_icon_entries"] = len(original.infolist()) - len(incoming_names.intersection(original.namelist()))

        with zipfile.ZipFile(base) as original, zipfile.ZipFile(fixed, "w", zipfile.ZIP_DEFLATED) as destination:
            xml = original.read("description.xml")
            if b"<!DOCTYPE" in xml.upper() or b"<!ENTITY" in xml.upper():
                raise ValueError("External entities are not supported")
            metadata = ET.fromstring(xml)
            for tag in ("title", "title-cn"):
                element = metadata.find(tag)
                if element is None:
                    element = ET.SubElement(metadata, tag)
                element.text = "My Magic Icons Fixed"
            description = ET.tostring(metadata, encoding="utf-8", xml_declaration=True)
            for entry in original.infolist():
                if entry.filename == "description.xml":
                    destination.writestr(entry.filename, description)
                elif entry.filename == "icons":
                    with destination.open("icons", "w") as output, merged_icons.open("rb") as stream:
                        shutil.copyfileobj(stream, output, 256 * 1024)
                else:
                    copy_entry(original, entry, destination)

        # Compare actual contents, not only names or compressed sizes.
        with zipfile.ZipFile(base) as original, zipfile.ZipFile(fixed) as repaired:
            assert original.namelist() == repaired.namelist()
            assert repaired.testzip() is None
            for name in original.namelist():
                if name not in ("description.xml", "icons"):
                    with original.open(name) as before, repaired.open(name) as after:
                        assert hashlib.file_digest(before, "sha256").digest() == hashlib.file_digest(after, "sha256").digest(), name
            extract_icons(fixed, work / "verification-icons.zip")
        with zipfile.ZipFile(work / "verification-icons.zip") as final_icons, zipfile.ZipFile(failed_icons) as incoming, zipfile.ZipFile(base_icons) as original:
            assert final_icons.testzip() is None
            for name in incoming_names:
                assert final_icons.read(name) == incoming.read(name), name
            for entry in original.infolist():
                if entry.filename not in incoming_names:
                    assert final_icons.read(entry.filename) == original.read(entry), entry.filename
        assert all(digest(Path(path)) == checksum for path, checksum in hashes.items())
        report["repair"] = {"file": str(fixed), "bytes": fixed.stat().st_size, "sha256": digest(fixed),
                            "input_files_unchanged": True, "outer_modules_preserved": True, "device_acceptance": "awaiting phone check"}
        report_file.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps({"failed": report["failed"], "working": report["working"], "repair": report["repair"],
                          "replaced_package_icons": report["replaced_package_icons"]}, indent=2))


if __name__ == "__main__":
    main()
