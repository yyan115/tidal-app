#!/usr/bin/env python3
"""Release helpers used by the Release workflow.

    release.py prepare VERSION   Update VERSION, CHANGELOG.md and the F-Droid changelog.
    release.py notes VERSION     Print the changelog entry for VERSION.
"""

import argparse
import datetime
import pathlib
import re
import sys

VERSION_PATTERN = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
UNRELEASED = "## [Unreleased]"
FASTLANE_LIMIT = 500


class ReleaseError(Exception):
    pass


def parse_version(text: str) -> tuple[int, int, int]:
    match = VERSION_PATTERN.match(text.strip())
    if not match:
        raise ReleaseError(f"'{text}' is not a version in the form MAJOR.MINOR.PATCH.")
    parts = tuple(int(part) for part in match.groups())
    if any(part > 999 for part in parts):
        raise ReleaseError("Each version component must be 999 or lower.")
    return parts  # type: ignore[return-value]


def version_code(version: str) -> int:
    major, minor, patch = parse_version(version)
    return major * 1_000_000 + minor * 1_000 + patch


def split_unreleased(changelog: str) -> tuple[str, str, str]:
    """Return the text before the Unreleased body, the body, and the text after it."""
    start = changelog.find(UNRELEASED)
    if start < 0:
        raise ReleaseError(f"CHANGELOG.md has no '{UNRELEASED}' heading.")
    body_start = start + len(UNRELEASED)
    following = re.search(r"^## \[", changelog[body_start:], flags=re.MULTILINE)
    body_end = body_start + following.start() if following else len(changelog)
    return changelog[:body_start], changelog[body_start:body_end], changelog[body_end:]


def entry(changelog: str, version: str) -> str:
    parse_version(version)
    match = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n", changelog, flags=re.MULTILINE)
    if not match:
        raise ReleaseError(f"CHANGELOG.md has no entry for {version}.")
    following = re.search(r"^## \[", changelog[match.end():], flags=re.MULTILINE)
    end = match.end() + following.start() if following else len(changelog)
    return changelog[match.end():end].strip() + "\n"


def plain_text(notes: str) -> str:
    """Reduce a changelog entry to the plain text F-Droid shows, within its size limit."""
    lines = [line.rstrip() for line in notes.splitlines() if not line.startswith("#")]
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()
    text = text.replace("`", "")
    kept: list[str] = []
    size = 0
    for line in text.splitlines():
        size += len(line.encode("utf-8")) + 1
        if size > FASTLANE_LIMIT:
            break
        kept.append(line)
    return "\n".join(kept).strip() + "\n"


def prepare(root: pathlib.Path, version: str, today: datetime.date) -> None:
    new = parse_version(version)
    version_file = root / "VERSION"
    current = parse_version(version_file.read_text())
    if new <= current:
        raise ReleaseError(f"{version} is not newer than the current version {version_file.read_text().strip()}.")
    changelog_file = root / "CHANGELOG.md"
    before, body, after = split_unreleased(changelog_file.read_text())
    if not body.strip():
        raise ReleaseError("The Unreleased section of CHANGELOG.md is empty.")
    heading = f"## [{version}] - {today.isoformat()}"
    updated = f"{before}\n\n{heading}\n\n{body.strip()}\n\n{after.lstrip()}"
    changelog_file.write_text(updated.rstrip() + "\n")
    version_file.write_text(version + "\n")
    changelogs = root / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
    changelogs.mkdir(parents=True, exist_ok=True)
    (changelogs / f"{version_code(version)}.txt").write_text(plain_text(entry(updated, version)))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("prepare", "notes"):
        commands.add_parser(name).add_argument("version")
    args = parser.parse_args(argv)
    root = pathlib.Path(__file__).resolve().parent.parent
    try:
        if args.command == "prepare":
            prepare(root, args.version, datetime.date.today())
        else:
            sys.stdout.write(entry((root / "CHANGELOG.md").read_text(), args.version))
    except ReleaseError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
