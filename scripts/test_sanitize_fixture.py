import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sanitize_fixture import sanitize  # noqa: E402


class SanitizeFixtureTest(unittest.TestCase):
    def test_names_and_titles_are_replaced_and_the_same_text_gets_the_same_replacement(self):
        raw = {"items": [{"title": "Real Song", "artists": [{"name": "Real Artist"}]}, {"title": "Real Song", "artists": [{"name": "Other Artist"}]}]}
        clean = sanitize(raw)
        text = json.dumps(clean)
        self.assertNotIn("Real", text)
        self.assertNotIn("Other", text)
        self.assertEqual(clean["items"][0]["title"], clean["items"][1]["title"])
        self.assertNotEqual(clean["items"][0]["artists"][0]["name"], clean["items"][1]["artists"][0]["name"])

    def test_numbers_flags_kinds_and_dates_stay(self):
        raw = {"id": 42, "duration": 183, "explicit": True, "ai": False, "audioQuality": "LOSSLESS", "type": "ALBUM_LIST", "releaseDate": "2002-02-26", "trackNumber": 3}
        self.assertEqual(raw, sanitize(raw))

    def test_a_list_of_items_is_cut_and_the_total_stays(self):
        raw = {"pagedList": {"totalNumberOfItems": 1049, "items": [{"id": n} for n in range(10)]}}
        clean = sanitize(raw, items=3)
        self.assertEqual(3, len(clean["pagedList"]["items"]))
        self.assertEqual(1049, clean["pagedList"]["totalNumberOfItems"])

    def test_the_title_of_a_module_is_a_label_of_the_service_and_stays(self):
        raw = {"type": "TRACK_LIST", "title": "Top Tracks", "pagedList": {"items": [{"id": 1, "title": "Real Song"}]}}
        clean = sanitize(raw)
        self.assertEqual("Top Tracks", clean["title"])
        self.assertNotIn("Real Song", json.dumps(clean))

    def test_a_label_that_names_an_artist_after_the_word_by_loses_the_name(self):
        raw = {"type": "ALBUM_LIST", "title": "More Albums by Real Artist", "pagedList": {"items": []}}
        self.assertEqual("More Albums by Example artist", sanitize(raw)["title"])

    def test_addresses_and_pictures_are_replaced_and_the_paths_of_the_service_stay(self):
        raw = {"url": "https://tidal.com/browse/track/1", "cover": "ab-cd-ef", "apiPath": "pages/contributor?artistId=7", "dataApiPath": "pages/data/x?artistId=7"}
        clean = sanitize(raw)
        self.assertNotIn("tidal.com", clean["url"])
        self.assertNotEqual("ab-cd-ef", clean["cover"])
        self.assertEqual(raw["apiPath"], clean["apiPath"])
        self.assertEqual(raw["dataApiPath"], clean["dataApiPath"])

    def test_empty_text_and_null_stay(self):
        raw = {"title": "", "version": None, "description": "null"}
        self.assertEqual(raw, sanitize(raw))


if __name__ == "__main__":
    unittest.main()
