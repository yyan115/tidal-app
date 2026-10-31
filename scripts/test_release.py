import datetime
import pathlib
import tempfile
import unittest

import release

CHANGELOG = """# Changelog

## [Unreleased]

### Added
- Sleep timer.

## [0.2.0] - 2026-10-31

### Changed
- Redesign.
"""


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.directory.name)
        (self.root / "VERSION").write_text("0.2.0\n")
        (self.root / "CHANGELOG.md").write_text(CHANGELOG)

    def tearDown(self):
        self.directory.cleanup()

    def test_version_code_matches_the_gradle_formula(self):
        self.assertEqual(1_000_000, release.version_code("1.0.0"))
        self.assertEqual(2_003_004, release.version_code("2.3.4"))

    def test_rejects_malformed_versions(self):
        for text in ("1.0", "v1.0.0", "1.0.0-rc1", "01.0.0", "1.0.1000", ""):
            with self.assertRaises(release.ReleaseError, msg=text):
                release.parse_version(text)

    def test_prepare_moves_unreleased_entries_under_the_new_version(self):
        release.prepare(self.root, "1.0.0", datetime.date(2026, 11, 2))
        text = (self.root / "CHANGELOG.md").read_text()
        self.assertIn("## [Unreleased]\n\n## [1.0.0] - 2026-11-02\n\n### Added\n- Sleep timer.\n\n## [0.2.0]", text)
        self.assertEqual("1.0.0\n", (self.root / "VERSION").read_text())
        fastlane = self.root / "fastlane/metadata/android/en-US/changelogs/1000000.txt"
        self.assertEqual("- Sleep timer.\n", fastlane.read_text())

    def test_prepare_refuses_an_empty_unreleased_section(self):
        (self.root / "CHANGELOG.md").write_text("# Changelog\n\n## [Unreleased]\n\n## [0.2.0] - 2026-10-31\n\n- Redesign.\n")
        with self.assertRaises(release.ReleaseError):
            release.prepare(self.root, "0.3.0", datetime.date(2026, 11, 2))
        self.assertEqual("0.2.0\n", (self.root / "VERSION").read_text())

    def test_prepare_refuses_versions_that_are_not_newer(self):
        for text in ("0.2.0", "0.1.9"):
            with self.assertRaises(release.ReleaseError, msg=text):
                release.prepare(self.root, text, datetime.date(2026, 11, 2))

    def test_entry_returns_only_the_requested_section(self):
        self.assertEqual("### Changed\n- Redesign.\n", release.entry(CHANGELOG, "0.2.0"))
        with self.assertRaises(release.ReleaseError):
            release.entry(CHANGELOG, "9.9.9")

    def test_fastlane_text_stays_within_the_size_limit(self):
        long_entry = "\n".join(f"- Change number {number} with some descriptive text." for number in range(60))
        text = release.plain_text(long_entry)
        self.assertLessEqual(len(text.encode("utf-8")), release.FASTLANE_LIMIT)
        self.assertTrue(text.startswith("- Change number 0"))


if __name__ == "__main__":
    unittest.main()
