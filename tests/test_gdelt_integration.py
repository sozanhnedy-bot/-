import unittest
from unittest.mock import patch

from scripts import update_news
from scripts.gdelt_discovery_preview import fetch_category as _unused


class GDELTAdapterTests(unittest.TestCase):
    def test_adapter_keeps_discovery_time_separate_and_never_imports_images(self):
        candidate = {
            "title": "A sufficiently descriptive international news headline",
            "url": "https://news.example/story?utm_source=gdelt",
            "seen_at": "2026-11-17T09:00:00Z",
            "socialimage": "https://images.example/story.jpg",
        }
        with patch("scripts.gdelt_discovery_preview.fetch_category", return_value=[candidate]):
            items = update_news.fetch_gdelt_items("world")

        self.assertEqual(len(items), 1)
        self.assertIsNone(items[0]["published_at"])
        self.assertEqual(items[0]["discovered_at"], candidate["seen_at"])
        self.assertEqual(items[0]["discovery_source"], "GDELT DOC 2.0")
        self.assertEqual(items[0]["image_url"], "")
        self.assertEqual(items[0]["url"], candidate["url"])

    def test_rejects_unknown_category_before_network_access(self):
        with self.assertRaises(ValueError):
            update_news.fetch_gdelt_items("not-a-category")


class GDELTAndRSSFailoverTests(unittest.TestCase):
    def test_rss_still_updates_when_gdelt_fails(self):
        feeds = [
            {"name": "GDELT:world", "region": "world", "category": "world",
             "url": "gdelt://world", "enabled": True},
            {"name": "RSS", "region": "world", "category": "world",
             "url": "https://feeds.example/rss", "enabled": True},
        ]
        rss_item = {
            "title": "خبر دولي مهم",
            "excerpt": "يتضمن هذا المقتطف تفاصيل إضافية كافية عن الحدث وسياقه وتطوراته الأخيرة.",
            "url": "https://publisher.example/story",
            "source_name": "Publisher",
            "source_url": "https://publisher.example",
            "region": "world",
            "category": "world",
            "published_at": "2026-11-17T08:00:00Z",
            "image_url": "",
        }

        def fetcher(url, *_args):
            if url.startswith("gdelt://"):
                raise RuntimeError("simulated GDELT outage")
            return [rss_item]

        data, changed, warnings = update_news.build_update(
            {"schema_version": 1, "items": []}, feeds, fetcher,
            update_news.summarize_rss_excerpt,
            max_per_run=10, now=update_news.datetime(2026, 11, 17, 10, tzinfo=update_news.timezone.utc),
        )
        self.assertTrue(changed)
        self.assertEqual(len(data["items"]), 1)
        self.assertEqual(data["items"][0]["url"], rss_item["url"])
        self.assertTrue(any("GDELT:world" in warning for warning in warnings))

    def test_gdelt_can_supply_candidates_when_rss_fails_without_claiming_publish_time(self):
        feeds = [
            {"name": "RSS", "region": "world", "category": "world",
             "url": "https://feeds.example/rss", "enabled": True},
            {"name": "GDELT:world", "region": "world", "category": "world",
             "url": "gdelt://world", "enabled": True},
        ]
        candidate = {
            "title": "A sufficiently descriptive international news headline",
            "excerpt": "",
            "url": "https://news.example/story",
            "source_name": "news.example",
            "source_url": "https://news.example",
            "region": "world",
            "category": "world",
            "image_url": "",
            "published_at": None,
            "discovery_source": "GDELT DOC 2.0",
            "discovered_at": "2026-11-17T09:00:00Z",
        }

        def fetcher(url, *_args):
            if url.startswith("gdelt://"):
                return [candidate]
            raise TimeoutError("simulated RSS outage")

        data, changed, warnings = update_news.build_update(
            {"schema_version": 1, "items": []}, feeds, fetcher,
            update_news.summarize_rss_excerpt,
            max_per_run=10, now=update_news.datetime(2026, 11, 17, 10, tzinfo=update_news.timezone.utc),
        )
        self.assertTrue(changed)
        self.assertEqual(len(data["items"]), 1)
        self.assertIsNone(data["items"][0]["published_at"])
        self.assertEqual(data["items"][0]["discovered_at"], candidate["discovered_at"])
        self.assertTrue(any("RSS" in warning for warning in warnings))


if __name__ == "__main__":
    unittest.main()
