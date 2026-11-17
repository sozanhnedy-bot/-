import unittest
from scripts import enrich_commons_images as images

class CommonsImageTests(unittest.TestCase):
    def test_only_explicit_reuse_licenses_are_accepted(self):
        self.assertEqual(images.license_status("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"), "cc-by-sa")
        self.assertEqual(images.license_status("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/"), "cc0")
        self.assertEqual(images.license_status("All rights reserved", "https://example.com/license"), "")

    def test_candidate_requires_creator_credit_and_relevant_words(self):
        story = {"title": "الرئيس الفرنسي يزور القاهرة"}
        meta = lambda value: {"value": value}
        page = {"title": "File:French president Cairo.jpg", "imageinfo": [{
            "mime": "image/jpeg", "size": 1000, "url": "https://upload.wikimedia.org/example.jpg",
            "extmetadata": {
                "LicenseShortName": meta("CC BY 4.0"),
                "LicenseUrl": meta("https://creativecommons.org/licenses/by/4.0/"),
                "Artist": meta("Photographer A"),
                "ImageDescription": meta("الرئيس الفرنسي في القاهرة"),
            }
        }]}
        self.assertIsNotNone(images.candidate_metadata(page, images.tokens(story["title"])))
        del page["imageinfo"][0]["extmetadata"]["Artist"]
        self.assertIsNone(images.candidate_metadata(page, images.tokens(story["title"])))

    def test_unrelated_file_is_rejected(self):
        page = {"title": "File:Random landscape.jpg", "imageinfo": [{
            "mime": "image/jpeg", "size": 1000, "url": "https://upload.wikimedia.org/example.jpg",
            "extmetadata": {
                "LicenseShortName": {"value": "CC BY 4.0"},
                "LicenseUrl": {"value": "https://creativecommons.org/licenses/by/4.0/"},
                "Artist": {"value": "Photographer"},
                "ImageDescription": {"value": "Beautiful mountain landscape"},
            }
        }]}
        self.assertIsNone(images.candidate_metadata(page, images.tokens("الرئيس الفرنسي يزور القاهرة")))

if __name__ == "__main__":
    unittest.main()
