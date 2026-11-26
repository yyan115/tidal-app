import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from check_changelog import problems  # noqa: E402

GOOD = """# Changelog

## [Unreleased]

### Added

- Pull to refresh in the Library

### Fixed

- Repeated tracks in the Library

## [0.8.1] - 2026-11-24

### Changed

- 24-bit songs are checked before they play, and a phone that fails the check uses 16-bit.
"""


class CheckChangelogTest(unittest.TestCase):
    def test_a_good_changelog_has_no_problem(self):
        self.assertEqual([], problems(GOOD))

    def test_the_changelog_of_the_repository_follows_the_rules(self):
        text = (pathlib.Path(__file__).resolve().parent.parent / "CHANGELOG.md").read_text(encoding="utf-8")
        self.assertEqual([], problems(text))

    def test_released_lines_are_history_and_only_the_dash_and_semicolon_rule_applies_to_them(self):
        self.assertEqual([], problems(GOOD.replace("check uses 16-bit.", "check used to use 16-bit.")))
        self.assertEqual(1, len(problems(GOOD.replace("check uses 16-bit.", "check uses 16-bit — really."))))

    def test_unreleased_must_come_first(self):
        self.assertTrue(problems(GOOD.replace("## [Unreleased]", "## [0.9.0] - 2026-12-01")))

    def test_a_line_that_sets_the_new_against_the_old_is_found(self):
        for words in ("Refresh button no longer shown", "Pull to refresh instead of a button", "Menu that used to open"):
            self.assertTrue(problems(GOOD.replace("Pull to refresh in the Library", words)), words)

    def test_a_full_stop_a_small_letter_a_dash_and_a_semicolon_are_found(self):
        for line in ("Pull to refresh.", "pull to refresh", "Pull to refresh — fast", "Pull; refresh"):
            self.assertTrue(problems(GOOD.replace("Pull to refresh in the Library", line)), line)

    def test_an_unknown_heading_is_found(self):
        self.assertTrue(problems(GOOD.replace("### Added", "### Improved")))


if __name__ == "__main__":
    unittest.main()
