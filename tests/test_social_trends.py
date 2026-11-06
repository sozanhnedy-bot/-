import unittest

from scripts.trend_radar import TrendLinkParser, build_radar, normalize_topic


class SocialTrendTests(unittest.TestCase):
    def test_normalize_topic(self):
        self.assertEqual(normalize_topic(" ترند!!! مصر "), "ترند مصر")

    def test_parse_trends24_topic_links(self):
        parser = TrendLinkParser()
        parser.feed('<html><a href="/topic/example/"><span>#ترند_مصر</span></a><a href="/about/">About</a></html>')
        self.assertEqual(parser.items, ["#ترند_مصر"])

    def test_same_topic_from_multiple_signals_is_grouped(self):
        radar = build_radar([
            {"title": "خبر عاجل عن مشروع جديد", "region": "مصر", "trend_rank": 2, "signal": "x_trends24_public", "source_name": "X"},
            {"title": "خبر عاجل عن مشروع جديد", "region": "مصر", "trend_rank": 4, "signal": "google_trends_rss", "source_name": "Google Trends"},
        ], [])
        self.assertEqual(len(radar["items"]), 1)
        self.assertEqual(radar["items"][0]["source_count"], 2)
        self.assertEqual(set(radar["items"][0]["platforms"]), {"x_trends24_public", "google_trends_rss"})

    def test_social_radar_preserves_source_and_media_rights_state(self):
        radar = build_radar([{
            "title": "قصة محلية",
            "region": "مصر",
            "trend_rank": 1,
            "source_name": "Trends24 (public X trends)",
            "source_url": "https://trends24.in/egypt/",
            "url": "https://trends24.in/egypt/",
            "signal": "x_trends24_public",
        }], [])
        self.assertEqual(radar["items"][0]["signal"], "x_trends24_public")
        self.assertEqual(radar["items"][0]["rights_status"], "not_assessed")
        self.assertIn("فيسبوك", radar["disclaimer"])


if __name__ == "__main__":
    unittest.main()
