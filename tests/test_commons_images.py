import unittest
from scripts import enrich_commons_images as images, render_news

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
            "image_source_url": "https://commons.wikimedia.org/wiki/File:Story.jpg",
            "image_caption": "صورة توثيقية — تصوير: Photographer A",
        }
        page = render_news.render(item, [item], set())
        self.assertIn("../assets/news-images/story.jpg", page)
        self.assertIn("Photographer A", page)
        self.assertIn("creativecommons.org/licenses/by/4.0/", page)
        self.assertIn("commons.wikimedia.org/wiki/File:Story.jpg", page)

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
