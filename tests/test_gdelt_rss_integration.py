import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from scripts import update_news


class GDELTRSSIntegrationTests(unittest.TestCase):
    @patch("scripts.gdelt_discovery_preview.fetch_category")
    def test_gdelt_candidates_adapt_to_rss_schema_without_images_or_fake_publish_dates(self, fetch):
        fetch.return_value = [{
            "title": "A sufficiently detailed international news headline",
            "url": "https://news.example.com/world/story?utm_source=gdelt",
            "source_domain": "news.example.com",
            "seen_at": "2026-11-17T09:00:00Z",
        }]
        items = update_news.fetch_gdelt_items("world", timespan="24h", maxrecords=10)
        self.assertEqual(len(items), 1)
        item = items[0]
        self.assertEqual(item["source_name"], "news.example.com")
        self.assertEqual(item["source_url"], "https://news.example.com")
        self.assertEqual(item["category"], "world")
        self.assertIsNone(item["published_at"])
        self.assertEqual(item["discovered_at"], "2026-11-17T09:00:00Z")
        self.assertEqual(item["discovery_source"], "GDELT DOC 2.0")
        self.assertEqual(item["image_url"], "")
        self.assertEqual(item["excerpt"], "")
        fetch.assert_called_once()

    def test_gdelt_failure_does_not_block_successful_rss_feed(self):
        now = datetime(2026, 11, 17, 12, 0, tzinfo=timezone.utc)
        rss_feed = {
            "name": "RSS source",
            "region": "world",
            "category": "world",
            "url": "https://feeds.example.com/rss",
            "enabled": True,
        }
        gdelt_feed = {
            "name": "GDELT:world",
            "region": "world",
            "category": "world",
            "url": "gdelt://world",
            "enabled": True,
        }
        rss_item = {
            "title": "A detailed RSS headline about an important event",
            "excerpt": "This is a substantive publisher excerpt with enough factual detail to qualify as a source excerpt.",
            "url": "https://publisher.example.com/story/1",
            "source_name": "Publisher",
            "source_url": "https://publisher.example.com",
            "region": "world",
            "category": "world",
            "published_at": "2026-11-17T11:00:00Z",
        }

        def fetcher(url, region, name, source_url=""):
            if url.startswith("gdelt://"):
                raise RuntimeError("simulated GDELT outage")
            return [rss_item]

        data, changed, warnings = update_news.build_update(
            {"schema_version": 1, "items": []},
            [rss_feed, gdelt_feed],
            fetcher,
            update_news.summarize_rss_excerpt,
            now=now,
            max_per_run=5,
        )
        self.assertTrue(changed)
        self.assertEqual(len(data["items"]), 1)
        self.assertEqual(data["items"][0]["url"], rss_item["url"])
        self.assertTrue(any("GDELT:world" in warning for warning in warnings))

    def test_same_publisher_story_from_rss_and_gdelt_is_not_added_twice(self):
        now = datetime(2026, 11, 17, 12, 0, tzinfo=timezone.utc)
        rss_feed = {"name": "Publisher RSS", "region": "world", "category": "world",
                    "url": "https://publisher.example.com/rss", "enabled": True}
        gdelt_feed = {"name": "GDELT:world", "region": "world", "category": "world",
                      "url": "gdelt://world", "enabled": True}
        shared_title = "International leaders announce a new agreement after talks"
        rss_item = {
            "title": shared_title,
            "excerpt": "The publisher reports additional details about the agreement and the talks that led to it.",
            "url": "https://publisher.example.com/story",
            "source_name": "Publisher",
            "source_url": "https://publisher.example.com",
            "region": "world",
            "category": "world",
            "published_at": "2026-11-17T11:00:00Z",
        }
        gdelt_item = {
            "title": shared_title,
            "excerpt": "",
            "url": "https://publisher.example.com/story?utm_source=gdelt",
            "source_name": "publisher.example.com",
            "source_url": "https://publisher.example.com",
            "region": "world",
            "category": "world",
            "published_at": None,
            "discovery_source": "GDELT DOC 2.0",
            "discovered_at": "2026-11-17T11:05:00Z",
        }

        def fetcher(url, region, name, source_url=""):
            return [gdelt_item] if url.startswith("gdelt://") else [rss_item]

        data, changed, warnings = update_news.build_update(
            {"schema_version": 1, "items": []},
            [rss_feed, gdelt_feed],
            fetcher,
            update_news.summarize_rss_excerpt,
            now=now,
            max_per_run=5,
        )
        self.assertTrue(changed)
        self.assertEqual(len(data["items"]), 1)
        self.assertEqual(data["items"][0]["url"], rss_item["url"])
        self.assertEqual(data["items"][0]["discovery_source"], "GDELT DOC 2.0")
        self.assertEqual(data["items"][0]["discovered_at"], "2026-11-17T11:05:00Z")


if __name__ == "__main__":
    unittest.main()
