import unittest
from scripts import enrich_commons_images as images, render_news

class CommonsImageTests(unittest.TestCase):
    def test_only_explicit_reuse_licenses_are_accepted(self):
        self.assertEqual(images.license_status("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"), "cc-by-sa")
        self.assertEqual(images.license_status("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/"), "cc0")
        self.assertEqual(images.license_status("All rights reserved", "https://example.com/license"), "")
        self.assertEqual(images.license_status("CC BY 4.0", "https://example.com/fake-license"), "")
        self.assertEqual(images.license_status("CC BY 4.0", "http://creativecommons.org/licenses/by/4.0/"), "")

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

    def test_candidate_rejects_a_different_person_with_the_same_surname(self):
        meta = lambda value: {"value": value}
        page = {"title": "File:Melania_Trump_2011.jpg", "imageinfo": [{
            "mime": "image/jpeg", "size": 1000, "url": "https://upload.wikimedia.org/example.jpg",
            "extmetadata": {
                "LicenseShortName": meta("CC BY 4.0"),
                "LicenseUrl": meta("https://creativecommons.org/licenses/by/4.0/"),
                "Artist": meta("Photographer A"),
                "ImageDescription": meta("Melania Trump at an event"),
            }
        }]}
        story = "Donald Trump meets world leaders"
        self.assertIsNone(images.candidate_metadata(page, images.tokens(story), images.tokens("Donald Trump")))

    def test_existing_image_subject_must_match_story(self):
        item = {
            "title": "Donald Trump meets world leaders",
            "image_alt": "Melania Trump at an event",
            "image_source_url": "https://commons.wikimedia.org/wiki/File:Melania_Trump_2011.jpg",
        }
        self.assertFalse(images.image_matches_story(item))
        item["image_alt"] = "Donald Trump at a summit"
        item["image_source_url"] = "https://commons.wikimedia.org/wiki/File:Donald_Trump_summit.jpg"
        self.assertTrue(images.image_matches_story(item))

    def test_local_commons_image_requires_rights_metadata(self):
        item = {"image_url": "assets/news-images/story.jpg", "image_rights_status": "cc-by", "image_credit": "Photographer", "image_license_url": "https://creativecommons.org/licenses/by/4.0/"}
        self.assertEqual(render_news.safe_story_image(item), "assets/news-images/story.jpg")
        item["image_license_url"] = "http://example.com/license"
        self.assertEqual(render_news.safe_story_image(item), "")

    def test_article_renders_local_image_with_credit_and_license_links(self):
        item = {
            "id": "a" * 20, "title": "الرئيس الفرنسي يزور القاهرة",
            "summary": "تفاصيل موثقة عن زيارة الرئيس الفرنسي إلى القاهرة خلال اليوم.",
            "excerpt": "تفاصيل موثقة عن زيارة الرئيس الفرنسي إلى القاهرة خلال اليوم.",
            "summary_type": "source_excerpt", "source_name": "وكالة أخبار",
            "source_url": "https://publisher.example/news", "image_url": "assets/news-images/story.jpg",
            "image_rights_status": "cc-by", "image_credit": "Photographer A",
            "image_license_url": "https://creativecommons.org/licenses/by/4.0/",
            "image_source_url": "https://commons.wikimedia.org/wiki/File:French_president_Cairo.jpg",
            "image_alt": "الرئيس الفرنسي في القاهرة",
            "image_caption": "صورة توثيقية — تصوير: Photographer A",
        }
        page = render_news.render(item, [item], set())
        self.assertIn("../assets/news-images/story.jpg", page)
        self.assertIn("Photographer A", page)
        self.assertIn("creativecommons.org/licenses/by/4.0/", page)
        self.assertIn("commons.wikimedia.org/wiki/File:Story.jpg", page)

    def test_subject_queries_remove_publisher_suffix_and_find_person(self):
        queries = images.search_queries("Trump meets world leaders in Philippines - live updates - BBC")
        self.assertIn("Trump", queries)
        self.assertNotIn("BBC", queries)

    def test_attempt_cap_is_exposed(self):
        import inspect
        self.assertIn("--max-attempts", inspect.getsource(images.main))

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
