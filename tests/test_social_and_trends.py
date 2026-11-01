import hashlib
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from scripts import publish_social, render_articles, render_news, render_trends, update_threads_signals, update_trends


class SocialPublisherTests(unittest.TestCase):
    def test_short_code_expands_when_prefix_collides(self):
        first = "abcdef12" + "0" * 12
        second = "abcdef34" + "0" * 12
        self.assertEqual(publish_social.short_code(first, [first, second]), "abcdef12")

    def test_report_uses_internal_short_url_and_compact_image(self):
        report = {
            "id": "fb839c2fe70d9e789a03",
            "title": "عنوان تقرير",
            "social_caption": "نص للمشاركة",
            "image": "assets/story-finswimming.webp",
            "image_caption": "صورة توضيحية وليست من موقع الحدث.",
        }
        result = publish_social.report_content(report, [report["id"]], "2026-10-31")
        self.assertEqual(result["url"], "https://sozanhnedy-bot.github.io/-/r/fb839c.html")
        self.assertEqual(result["image"], "assets/story-finswimming-social.jpg")
        self.assertIn("ثريدز وثائقي", result["caption"])
        self.assertIn("الصورة توضيحية", result["caption"])

    def test_trend_digest_is_labeled_as_rising_topics_not_verified_events(self):
        result = publish_social.trend_digest({"items": [
            {"title": "موضوع أ", "score": 12, "source": "Google Trends"},
            {"title": "موضوع ب", "score": 9, "source": "X Trends"},
        ]}, "2026-10-31")
        self.assertIsNotNone(result)
        self.assertIn("ليس تأكيدًا مستقلًا", result["caption"])
        self.assertIn("ثريدز وثائقي", result["caption"])
        self.assertNotIn("Google Trends", result["caption"])
        self.assertNotIn("X Trends", result["caption"])
        self.assertEqual(result["image"], "assets/brand-cover.jpg")
        self.assertEqual(result["url"], "https://sozanhnedy-bot.github.io/-/")

    def test_x_text_keeps_the_internal_url_and_stays_bounded(self):
        content = {"caption": "عنوان طويل " * 100, "url": "https://sozanhnedy-bot.github.io/-/r/abcdef.html"}
        result = publish_social.social_text(content)
        self.assertTrue(result.endswith(content["url"]))
        self.assertLessEqual(len(result), 276)

    def test_x_post_uploads_image_then_creates_post(self):
        content = {"caption": "خبر للمراجعة", "url": "https://sozanhnedy-bot.github.io/-/r/abcdef.html", "image": "assets/story-yemen-diplomacy-social.jpg"}
        with patch("scripts.publish_social.request_json", side_effect=[{"data": {"id": "media-1"}}, {"data": {"id": "post-1"}}]) as request:
            post_id = publish_social.post_to_x(content, "test-token")
        self.assertEqual(post_id, "post-1")
        self.assertEqual(request.call_count, 2)
        self.assertEqual(request.call_args.args[0], "https://api.x.com/2/tweets")
        self.assertEqual(request.call_args.args[1]["media"]["media_ids"], ["media-1"])

    def test_disabled_scheduler_does_not_call_network(self):
        with patch.dict("os.environ", {"SOCIAL_AUTOPUBLISH": "false"}), patch("scripts.publish_social.request_json") as request:
            self.assertEqual(publish_social.main(), 0)
        request.assert_not_called()

    def test_youtube_embed_is_click_to_load(self):
        report = {"id": "fb839c2fe70d9e789a03", "title": "عنوان", "dek": "مقدمة", "sections": [], "youtube_id": "abcdefghijk"}
        html = render_articles.render(report, "fb839c")
        article_js = (Path(__file__).resolve().parents[1] / "article.js").read_text(encoding="utf-8")
        self.assertIn('data-youtube-id="abcdefghijk"', html)
        self.assertIn("data-load-youtube", html)
        self.assertNotIn("<iframe", html)
        self.assertIn("youtube-nocookie.com/embed/", article_js)

    def test_rss_brief_never_links_to_the_source(self):
        item = {
            "id": "a" * 20,
            "title": "عنوان <script>alert(1)</script>",
            "summary": "ملخص آمن",
            "source_name": "مصدر تجريبي",
            "url": "https://publisher.example/story",
            "source_url": "https://publisher.example",
        }
        page = render_news.render(item)
        self.assertIn("مصدر تجريبي", page)
        self.assertNotIn("publisher.example", page)
        self.assertNotIn("<script>", page)
        self.assertIn('href="../"', page)
        self.assertNotIn('href="https://', page)

    def test_build_renders_only_news_without_full_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            news_path, reports_path = root / "news.json", root / "reports.json"
            news_path.write_text(json.dumps({"items": [
                {"id": "full-report", "title": "مكتمل"},
                {"id": "rss-brief", "title": "موجز", "summary": "تفاصيل موجزة", "source_name": "مصدر"},
            ]}, ensure_ascii=False), encoding="utf-8")
            reports_path.write_text(json.dumps({"items": [{"id": "full-report"}]}), encoding="utf-8")
            output = root / "site"
            with patch.object(render_news, "NEWS_PATH", news_path), patch.object(render_news, "REPORTS_PATH", reports_path):
                self.assertEqual(render_news.build(output), 1)
            self.assertTrue((output / "news" / "rss-brief.html").is_file())
            self.assertFalse((output / "news" / "full-report.html").exists())


class InternalTrendPageTests(unittest.TestCase):
    def test_trend_page_is_internal_and_hides_provider_and_publisher_labels(self):
        item = {
            "id": "a" * 18,
            "title": "موضوع رائج",
            "region": "egypt",
            "region_label": "X · مصر",
            "source": "X Trends",
            "news_items": [{"title": "عنوان مرتبط", "source": "Publisher Confidential", "snippet": "مقتطف <script>"}],
        }
        page = render_trends.render(item)
        self.assertIn("موضوع رائج", page)
        self.assertIn("عنوان مرتبط", page)
        self.assertIn("مقتطف &lt;script&gt;", page)
        self.assertNotIn("X Trends", page)
        self.assertNotIn("Publisher Confidential", page)
        self.assertNotIn("href=\"https://publisher.example", page)
        self.assertIn('href="../"', page)

    def test_build_creates_internal_route_for_each_valid_trend(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "trending.json"
            source.write_text(json.dumps({"items": [
                {"id": "b" * 18, "title": "ترند أول"},
                {"id": "../unsafe", "title": "مسار غير صالح"},
            ]}, ensure_ascii=False), encoding="utf-8")
            output = root / "site"
            with patch.object(render_trends, "TRENDS_PATH", source):
                self.assertEqual(render_trends.build(output), 1)
            self.assertTrue((output / "trends" / f"{'b' * 18}.html").is_file())
            self.assertFalse((output / "trends" / ".." / "unsafe.html").exists())
        app_js = (Path(__file__).resolve().parents[1] / "app.js").read_text(encoding="utf-8")
        self.assertIn("function trendUrl(id)", app_js)
        self.assertIn("title.href = destination", app_js)
        self.assertIn("const rawRegion", app_js)
        self.assertIn("replace(/^(?:X|Google Trends)", app_js)
        self.assertNotIn("trend.source", app_js)


class XTrendTests(unittest.TestCase):
    def test_new_topic_alert_remains_active_for_thirty_minutes(self):
        now = datetime(2026, 10, 31, 20, 0, tzinfo=timezone.utc)
        topic = "#خبر_جديد"
        digest = hashlib.sha256(f"x:23424802:{topic.casefold()}".encode("utf-8")).hexdigest()[:18]
        previous = [{"id": digest, "title": topic, "published_at": (now - timedelta(minutes=15)).isoformat().replace("+00:00", "Z")}]
        payload = json.dumps({"data": [{"trend_name": topic}]}).encode("utf-8")

        class Response:
            def __enter__(self):
                return self
            def __exit__(self, *_):
                return False
            def read(self, *_):
                return payload

        with patch("scripts.update_trends.urllib.request.urlopen", return_value=Response()):
            result = update_trends.fetch_x_trends("token", "23424802", now, previous)
        self.assertEqual(len(result), 1)
        self.assertTrue(result[0]["alert"])
        self.assertEqual(result[0]["source"], "X Trends")

    def test_bad_woeid_is_rejected_before_network_call(self):
        with patch("scripts.update_trends.urllib.request.urlopen") as request:
            with self.assertRaises(ValueError):
                update_trends.fetch_x_trends("token", "../unsafe", datetime.now(timezone.utc), [])
        request.assert_not_called()


class ThreadsSignalTests(unittest.TestCase):
    def test_unchanged_aggregate_count_does_not_rewrite_snapshot(self):
        measured_at = "2026-10-31T19:00:00Z"
        previous = {
            "schema_version": 1,
            "updated_at": measured_at,
            "items": [{
                "trend_id": "topic-1",
                "title": "موضوع",
                "region": "world",
                "region_label": "",
                "recent_public_posts": 2,
                "measured_at": measured_at,
                "source": "Threads keyword search",
            }],
        }
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "signals.json"
            trends = Path(tmp) / "trends.json"
            output.write_text(json.dumps(previous, ensure_ascii=False), encoding="utf-8")
            trends.write_text(json.dumps({"items": [{"id": "topic-1", "title": "موضوع"}]}), encoding="utf-8")
            original = output.read_text(encoding="utf-8")
            with patch.object(update_threads_signals, "OUTPUT", output), patch.object(update_threads_signals, "TRENDS", trends), patch.dict("os.environ", {"THREADS_USER_ACCESS_TOKEN": "test-token"}), patch("scripts.update_threads_signals.search", return_value=[{}, {}]):
                self.assertEqual(update_threads_signals.main(), 0)
            self.assertEqual(output.read_text(encoding="utf-8"), original)


if __name__ == "__main__":
    unittest.main()
