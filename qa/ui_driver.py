"""Small emulator-only driver using UI nodes observed through uiautomator."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
import subprocess
import time


class Driver:
    def __init__(self, serial, output, adb="adb"):
        if not serial.startswith("emulator-"):
            raise ValueError("This QA driver only operates on an Android emulator.")
        self.serial = serial
        self.output = Path(output)
        self.output.mkdir(parents=True, exist_ok=True)
        self.binary = adb

    def adb(self, *parts, check=True):
        result = subprocess.run([self.binary, "-s", self.serial, *parts], capture_output=True, check=check)
        return result.stdout.decode("utf-8", errors="replace")

    def state(self):
        self.adb("shell", "uiautomator", "dump", "/sdcard/magic-themer-ui.xml")
        xml = self.adb("shell", "cat", "/sdcard/magic-themer-ui.xml")
        (self.output / "last-ui.xml").write_text(xml, encoding="utf-8")
        return list(ET.fromstring(xml).iter("node"))

    def find(self, text, nodes=None, contains=False):
        for node in nodes if nodes is not None else self.state():
            for key in ("text", "content-desc"):
                value = node.get(key, "").lower()
                if (text.lower() in value) if contains else (text.lower() == value):
                    return node
        return None

    def await_text(self, text, contains=False, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            node = self.find(text, contains=contains)
            if node is not None:
                return node
            time.sleep(0.4)
        raise AssertionError(f"UI did not show {text!r}; see {self.output / 'last-ui.xml'}")

    def await_app(self, package, timeout=30):
        pattern = re.compile(r", " + re.escape(package) + r"(?:,|$)")
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            node = next((n for n in self.state() if pattern.search(n.get("content-desc", ""))), None)
            if node is not None:
                return node
            time.sleep(0.4)
        raise AssertionError(f"UI did not show app {package!r}; see last-ui.xml")

    def await_enabled(self, text, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            node = self.find(text)
            if node is not None and node.get("enabled") == "true":
                return node
            time.sleep(0.4)
        raise AssertionError(f"UI control {text!r} did not become enabled; see last-ui.xml")

    def tap(self, node):
        assert node is not None, "Expected UI node missing; see last-ui.xml"
        x1, y1, x2, y2 = self.bounds(node)
        assert x2 > x1 and y2 > y1, "Expected UI node has no visible bounds"
        self.adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.4)

    @staticmethod
    def bounds(node):
        coords = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
        assert len(coords) == 4
        return coords

    def reveal(self, text, contains=False):
        for _ in range(8):
            nodes = self.state()
            node = self.find(text, nodes, contains)
            if node is not None and node.get("bounds") != "[0,0][0,0]":
                return node
            scroll = next((n for n in nodes if n.get("scrollable") == "true"), None)
            assert scroll is not None, f"No scroll container for {text}"
            x1, y1, x2, y2 = self.bounds(scroll)
            x = (x1 + x2) // 2
            self.adb("shell", "input", "swipe", str(x), str(y1 + (y2 - y1) * 3 // 4),
                     str(x), str(y1 + (y2 - y1) // 4), "300")
            time.sleep(0.3)
        raise AssertionError(f"Could not reveal {text}")

    def edit(self, node, value):
        assert node is not None
        if not node.get("class", "").endswith("EditText"):
            text = node.get("text", "")
            node = next((n for n in self.state() if n.get("class", "").endswith("EditText") and n.get("text") == text), None)
        assert node is not None, "Text input missing; refuse to type into a label"
        self.tap(node)
        self.adb("shell", "input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A")
        self.adb("shell", "input", "text", value.replace(" ", "%s"))
        self.adb("shell", "input", "keyevent", "KEYCODE_BACK")

    def screenshot(self, name):
        self.adb("shell", "screencap", "-p", "/sdcard/magic-themer-shot.png")
        self.adb("pull", "/sdcard/magic-themer-shot.png", str(self.output / name))

    def start(self):
        self.adb("shell", "am", "force-stop", "dev.magicthemer")
        self.adb("shell", "am", "start", "-f", "0x10008000", "-n", "dev.magicthemer/.MainActivity")
        self.await_enabled("Choose pack", timeout=180)

    def downloads(self):
        nodes = self.state()
        root = self.find("Show roots", nodes)
        if root is None:
            root = self.find("Navigate up", nodes)
        if root is not None:
            self.tap(root)
            self.await_text("Downloads")
            target = next((n for n in self.state() if n.get("text") == "Downloads" and n.get("resource-id") == "android:id/title"), None)
            self.tap(target)

    def open_fixture(self, filename):
        self.await_text("Show roots")
        node = self.find(filename)
        if node is not None:
            self.tap(node)
            return
        self.tap(self.find("Show roots"))
        self.await_text("sdk_gphone64_x86_64")
        root = next((n for n in self.state() if n.get("text") == "sdk_gphone64_x86_64" and n.get("resource-id") == "android:id/title"), None)
        self.tap(root)
        self.await_text("Files on sdk_gphone64_x86_64")
        self.tap(self.await_text("Download"))
        self.tap(self.reveal(filename))

    def save_document(self, timeout=60):
        self.await_text("Save", timeout=timeout)
        self.downloads()
        self.tap(self.await_text("Save"))
