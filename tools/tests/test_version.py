import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("app_version", Path(__file__).parents[1] / "version.py")
version = importlib.util.module_from_spec(spec)
spec.loader.exec_module(version)


class VersionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.version_file = self.root / "version.properties"
        self.log_file = self.root / "CHANGELOG.md"
        self.version_file.write_text("versionName=1.2.9\nversionCode=42\n", encoding="utf-8")
        self.log_file.write_text("# 更新日志\n\n## 1.2.9 (42)\n\n- 原有记录。\n", encoding="utf-8")

    def test_bumps_reset_only_lower_components_and_preserve_history(self):
        for part, expected in (("patch", "1.2.10"), ("minor", "1.3.0"), ("major", "2.0.0")):
            with self.subTest(part=part):
                self.version_file.write_text("versionName=1.2.9\nversionCode=42\n", encoding="utf-8")
                self.assertEqual((expected, 43), version.bump(self.root, part, "更新说明"))
                self.assertEqual((expected, 43), version.read_version(self.version_file.read_text()))
                log = self.log_file.read_text(encoding="utf-8")
                self.assertIn(f"## {expected} (43)", log)
                self.assertIn("- 更新说明", log)
                self.assertIn("- 原有记录。", log)
                self.assertFalse(self.version_file.read_bytes().startswith(b"\xef\xbb\xbf"))

    def test_invalid_inputs_leave_both_files_unchanged(self):
        for content, message in (
            ("versionName=01.2.3\nversionCode=42\n", "说明"),
            ("versionName=1.2.3\nversionCode=0\n", "说明"),
            ("versionName=1.2.3\nversionCode=2100000000\n", "说明"),
            ("versionName=1.2.3\nversionCode=42\nversionCode=43\n", "说明"),
            ("versionName=1.2.3\n", "说明"),
            ("versionName=1.2.3\nversionCode=42\n", "  "),
            ("versionName=1.2.3\nversionCode=42\n", "说明\n## fake"),
        ):
            with self.subTest(content=content, message=message):
                self.version_file.write_text(content, encoding="utf-8")
                before = (self.version_file.read_bytes(), self.log_file.read_bytes())
                with self.assertRaises(ValueError):
                    version.bump(self.root, "patch", message)
                self.assertEqual(before, (self.version_file.read_bytes(), self.log_file.read_bytes()))

    def test_invalid_log_does_not_bump(self):
        self.log_file.write_text("invalid", encoding="utf-8")
        before = self.version_file.read_bytes()
        with self.assertRaises(ValueError):
            version.bump(self.root, "patch", "说明")
        self.assertEqual(before, self.version_file.read_bytes())

    def test_failed_log_write_restores_version(self):
        original_replace = version.replace_text
        before = (self.version_file.read_bytes(), self.log_file.read_bytes())

        def replace(path, content):
            if path == self.log_file:
                raise OSError("simulated write failure")
            original_replace(path, content)

        with patch.object(version, "replace_text", side_effect=replace):
            with self.assertRaises(OSError):
                version.bump(self.root, "patch", "说明")
        self.assertEqual(before, (self.version_file.read_bytes(), self.log_file.read_bytes()))


if __name__ == "__main__":
    unittest.main()
