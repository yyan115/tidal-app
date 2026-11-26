#!/usr/bin/env python3
"""Checks the form of CHANGELOG.md, so that a line that breaks the rules of the project is found before it reaches a release.

The file is in the Keep a Changelog form. The rules of the project for its text:
- The list of what is not released yet is the first section, named Unreleased.
- A section of a version holds only the headings Added, Changed, Deprecated, Removed, Fixed and Security.
- A line is a short phrase that names the change. It starts with a capital, it has no full stop at the end, and it has no dash and no semicolon.
- A line of Unreleased does not set the new against the old. Words such as "no longer", "instead of" and "used to" are the sign of that.

Only Unreleased is checked for the last two rules, because the lines of released versions are history.

usage: check_changelog.py [CHANGELOG.md]
"""
import pathlib
import re
import sys

HEADINGS = {"Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"}
CONTRAST = re.compile(r"\b(no longer|instead of|used to|rather than|anymore|previously|formerly)\b", re.IGNORECASE)


def problems(text: str) -> list[str]:
    found: list[str] = []
    lines = text.splitlines()
    versions = [(number, line) for number, line in enumerate(lines, 1) if line.startswith("## ")]
    if not versions or not re.fullmatch(r"## \[Unreleased\]", versions[0][1].strip()):
        found.append("The first version section must be '## [Unreleased]'.")
    section = ""
    unreleased = False
    for number, line in enumerate(lines, 1):
        if line.startswith("## "):
            section = line
            unreleased = line.strip() == "## [Unreleased]"
        elif line.startswith("### "):
            if line[4:].strip() not in HEADINGS:
                found.append(f"Line {number}: '{line.strip()}' is not a heading of Keep a Changelog.")
        elif line.startswith("- "):
            entry = line[2:].strip()
            if not entry:
                found.append(f"Line {number}: an empty entry.")
                continue
            if not section:
                found.append(f"Line {number}: an entry outside a version section.")
            if re.search(r"[—–;]", entry):
                found.append(f"Line {number}: a dash or a semicolon in '{entry}'.")
            if unreleased:
                if not entry[0].isupper() and not entry[0].isdigit():
                    found.append(f"Line {number}: an entry starts with a capital letter, '{entry}'.")
                if entry.endswith("."):
                    found.append(f"Line {number}: an entry has no full stop at the end, '{entry}'.")
                if CONTRAST.search(entry):
                    found.append(f"Line {number}: an entry does not set the new against the old, '{entry}'.")
    return found


def main(argv: list[str]) -> int:
    path = pathlib.Path(argv[0] if argv else "CHANGELOG.md")
    found = problems(path.read_text(encoding="utf-8"))
    for line in found:
        print(line)
    if found:
        return 1
    print("The changelog follows the rules of the project.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
