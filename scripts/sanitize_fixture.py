#!/usr/bin/env python3
"""Turns a raw answer of the TIDAL service into a test fixture that keeps the shape and holds no real text.

The answer of the service is the only way to know the real shape of its data: the names of the fields, which ones can be missing, the kinds of module and
the order of the items. A fixture that is written by hand drifts from it. This script keeps every key, every number, every true or false and every value that
names a kind (a type, a layout, a quality), and it replaces the text that names things (titles, names, addresses, pictures) with the same made-up text
each time that text occurs. A list of items is cut to a few items, and the total that the service names stays, so that a test can see a list that is not whole.

usage: sanitize_fixture.py RAW.json OUT.json [--items N]
"""
import argparse
import json
import re
import sys

# The keys whose text names something. The value is the kind of made-up text.
NAMING = {
    "title": "title", "name": "name", "artistName": "name", "version": "version", "copyright": "copyright", "description": "text", "text": "text",
    "summary": "text", "subTitle": "text", "shortSubtitle": "text", "source": "text", "url": "url", "link": "url", "cover": "image", "picture": "image",
    "image": "image", "imageId": "image", "squareImage": "image", "selectedAlbumCoverFallback": "image", "vibrantColor": "color", "isrc": "code", "upc": "code",
    "copy": "text", "caption": "text", "subtitle": "text", "label": "text", "handle": "name", "videoCover": "image", "preTitle": "text",
}
# The keys whose text names a kind. Their values stay, because a test needs the real kinds.
KEEP_TEXT = {"type", "layout", "audioQuality", "audioModes", "accessType", "assetPresentation", "manifestMimeType", "role", "category", "streamStartDate", "releaseDate",
             "lastUpdated", "created", "dateAdded", "dataApiPath", "apiPath", "id", "uuid", "mixes", "key", "keyScale", "width", "viewAll", "deviceType", "locale"}


class Sanitizer:
    def __init__(self, items: int):
        self.items = items
        self.seen: dict[tuple[str, str], str] = {}

    def made_up(self, kind: str, original: str) -> str:
        key = (kind, original)
        if key not in self.seen:
            number = sum(1 for k in self.seen if k[0] == kind) + 1
            self.seen[key] = {
                "image": f"00000000-0000-4000-8000-{number:012d}",
                "url": f"https://example.invalid/{number}",
                "color": "#808080",
                "code": f"EX{number:010d}",
            }.get(kind, f"Example {kind} {number}")
        return self.seen[key]

    @staticmethod
    def is_module(value: dict) -> bool:
        """A module of a page. Its title is a label of the service, such as Top Tracks, and not the name of something in the catalogue."""
        return "pagedList" in value or bool(re.fullmatch(r"[A-Z]+(_[A-Z]+)+", str(value.get("type", ""))))

    def walk(self, value, key: str = ""):
        if isinstance(value, dict):
            result = {}
            for k, v in value.items():
                if k == "title" and isinstance(v, str) and self.is_module(value):
                    # A label such as "More Albums by Name" names an artist after the word by.
                    result[k] = re.sub(r" by .+$", " by Example artist", v)
                else:
                    result[k] = self.walk(v, k)
            return result
        if isinstance(value, list):
            # A list of items is cut. A list of kinds, such as audio modes, stays whole.
            if key in ("items", "rows", "modules", "artists", "roles") and len(value) > self.items and key in ("items", "artists"):
                value = value[:self.items]
            return [self.walk(v, key) for v in value]
        if isinstance(value, str) and key in NAMING and key not in KEEP_TEXT and value not in ("", "null"):
            return self.made_up(NAMING[key], value)
        return value


def sanitize(raw, items: int = 3):
    return Sanitizer(items).walk(raw)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("raw")
    parser.add_argument("out")
    parser.add_argument("--items", type=int, default=3)
    args = parser.parse_args(argv)
    with open(args.raw, encoding="utf-8") as handle:
        raw = json.load(handle)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(sanitize(raw, args.items), handle, indent=1, ensure_ascii=False)
        handle.write("\n")


if __name__ == "__main__":
    main(sys.argv[1:])
