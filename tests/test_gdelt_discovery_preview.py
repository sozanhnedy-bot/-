import unittest

from scripts.gdelt_discovery_preview import (
    merge_candidates,
    normalize_article,
    parse_seen_date,
    safe_article_url,
)


class GDELTDiscoveryPreviewTests(unittest.TestCase):
    def test_rejects_non_https_or_credential_urls(self):
        self.assertEqual(safe_article_url("http://example.com/story"), "")
        self.assertEqual(safe_article_url("https://user:pass@example.com/story"), "")
        self.assertEqual(safe_article_url("javascript:alert(1)"), "")

    def test_removes_tracking_parameters_and_fragment(self):
        self.assertEqual(
            safe_article_url("https://example.com/story?utm_source=test&id=7#top"),
            "https://example.com/story?id=7",
        )

    def test_normalizes_candidate_without_copying_image_url(self):
        item = normalize_article({
            "title": "  Example <b>world</b> news headline  ",
            "url": "https://news.example/story?utm_campaign=x",
            "domain": "news.example",
            "seendate": "20261117T090000Z",
            "language": "English",
            "sourcecountry": "US",
            "socialimage": "https://images.example/photo.jpg",
        }, "world")
        self.assertIsNotNone(item)
        self.assertEqual(item["title"], "Example world news headline")
        self.assertEqual(item["url"], "https://news.example/story")
        self.assertEqual(item["seen_at"], "2026-11-17T09:00:00Z")
        self.assertEqual(item["categories"], ["world"])
        self.assertNotIn("socialimage", item)
        self.assertNotIn("image_url", item)

    def test_rejects_missing_title_or_unsafe_url(self):
        self.assertIsNone(normalize_article({"title": "Too short", "url": "https://news.example/a"}, "world"))
        self.assertIsNone(normalize_article({"title": "A long enough headline here", "url": "http://news.example/a"}, "world"))

    def test_deduplicates_and_combines_categories(self):
        first = {
            "title": "Same story",
            "url": "https://news.example/story",
            "source_domain": "news.example",
            "seen_at": "2026-11-17T08:00:00Z",
            "language": "English",
            "source_country": "US",
            "categories": ["world"],
            "discovery_source": "GDELT DOC 2.0",
        }
        second = {**first, "seen_at": "2026-11-17T09:00:00Z", "categories": ["economy"]}
        result = merge_candidates([first, second])
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["categories"], ["economy", "world"])
        self.assertEqual(result[0]["seen_at"], "2026-11-17T09:00:00Z")

    def test_parses_invalid_seen_date_as_empty(self):
        self.assertEqual(parse_seen_date("not-a-date"), "")


if __name__ == "__main__":
    unittest.main()
