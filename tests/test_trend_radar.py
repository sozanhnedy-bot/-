import unittest
from datetime import datetime, timezone

from scripts.trend_radar import build_radar, normalize_topic, score_trend


class TrendRadarTests(unittest.TestCase):
    def test_normalizes_titles_for_deduplication(self):
        self.assertEqual(normalize_topic("  عاجل!!! خبر جديد  "), "عاجل خبر جديد")

    def test_rank_and_corroboration_increase_score(self):
        item = {"trend_rank": 1, "published_at": "2026-11-06T10:00:00Z"}
        self.assertGreater(score_trend(item, 1, 2), score_trend(item, 10, 0))

    def test_radar_is_ranked_and_contains_disclaimer(self):
        trends = [
            {"title": "قصة محلية مهمة", "region": "مصر", "trend_rank": 1, "source_name": "Google Trends"},
            {"title": "موضوع آخر", "region": "العالم", "trend_rank": 4, "source_name": "Google Trends"},
        ]
        news = [{"title": "قصة محلية مهمة في القاهرة"}]
        radar = build_radar(trends, news, datetime(2026, 11, 6, tzinfo=timezone.utc))
        self.assertEqual(len(radar["items"]), 2)
        self.assertIn("ليست", radar["disclaimer"])
        self.assertEqual(radar["items"][0]["title"], "قصة محلية مهمة")


if __name__ == "__main__":
    unittest.main()
