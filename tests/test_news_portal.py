import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import build_site, publish_social, render_articles, render_news


class SocialPublisherTests(unittest.TestCase):
    def test_short_code_expands_when_prefix_collides(self):
        first = "abcdef12" + "0" * 12
        second = "abcdef34" + "0" * 12
        self.assertEqual(publish_social.short_code(first, [first, second]), "abcdef12")

    def test_report_social_preview_uses_brand_cover_and_internal_url(self):
        report = {"id": "fb839c2fe70d9e789a03", "title": "عنوان تقرير", "social_caption": "نص للمشاركة", "image": "assets/story-finswimming.webp"}
        result = publish_social.report_content(report, [report["id"]], "2026-11-02")
        self.assertEqual(result["url"], "https://sozanhnedy-bot.github.io/-/r/fb839c.html")
        self.assertEqual(result["image"], "assets/brand-cover.jpg")
        self.assertIn("World News", result["caption"])
        self.assertIn("غلاف تعريفي للموقع", result["caption"])
        page = render_articles.render(report, "fb839c")
        self.assertIn("assets/brand-cover.jpg", page)
        self.assertIn("../assets/story-finswimming.webp", page)

    def test_social_selection_never_falls_back_to_topics(self):
        self.assertIsNone(publish_social.choose_content({"posted_report_ids": []}, [], "2026-11-02"))

    def test_x_text_keeps_internal_url_and_stays_bounded(self):
        content = {"caption": "عنوان طويل " * 100, "url": "https://sozanhnedy-bot.github.io/-/r/abcdef.html"}
        result = publish_social.social_text(content)
        self.assertTrue(result.endswith(content["url"]))
        self.assertLessEqual(len(result), 276)

    def test_x_post_uploads_brand_cover_then_creates_post(self):
        content = {"caption": "خبر للمراجعة", "url": "https://sozanhnedy-bot.github.io/-/r/abcdef.html", "image": "assets/brand-cover.jpg"}
        with patch("scripts.publish_social.request_json", side_effect=[{"data": {"id": "media-1"}}, {"data": {"id": "post-1"}}]) as request:
            post_id = publish_social.post_to_x(content, "test-token")
        self.assertEqual(post_id, "post-1")
        self.assertEqual(request.call_count, 2)
        self.assertEqual(request.call_args.args[0], "https://api.x.com/2/tweets")
        self.assertEqual(request.call_args.args[1]["media"]["media_ids"], ["media-1"])

    def test_disabled_social_job_does_not_call_network(self):
        with patch.dict("os.environ", {"SOCIAL_AUTOPUBLISH": "false"}), patch("scripts.publish_social.request_json") as request:
            self.assertEqual(publish_social.main(), 0)
        request.assert_not_called()


class ReportRenderingTests(unittest.TestCase):
    def test_youtube_embed_is_click_to_load(self):
        report = {"id": "fb839c2fe70d9e789a03", "title": "عنوان", "dek": "مقدمة", "sections": [], "youtube_id": "abcdefghijk"}
        page = render_articles.render(report, "fb839c")
        article_js = (Path(__file__).resolve().parents[1] / "article.js").read_text(encoding="utf-8")
        self.assertIn('data-youtube-id="abcdefghijk"', page)
        self.assertIn("data-load-youtube", page)
        self.assertNotIn("<iframe", page)
        self.assertIn("youtube-nocookie.com/embed/", article_js)

    def test_report_source_names_are_plain_text_not_external_links(self):
        report = {"id": "fb839c2fe70d9e789a03", "title": "عنوان", "dek": "مقدمة", "sections": [], "sources": ["وكالة"], "source_links": [{"name": "وكالة", "url": "https://publisher.example"}, {"name": "سيئ", "url": "javascript:alert(1)"}]}
        page = render_articles.render(report, "fb839c")
        self.assertIn("وكالة", page)
        self.assertNotIn("https://publisher.example", page)
        self.assertNotIn("javascript:", page)
        self.assertNotIn("target=\"_blank\"", page)

    def test_full_report_can_show_real_related_internal_news(self):
        report = {"id": "fb839c2fe70d9e789a03", "title": "تقرير", "sections": []}
        page = render_articles.render(report, "fb839c", {"region": "egypt"}, [{"id": "a" * 20, "title": "خبر من مصر"}])
        self.assertIn("أخبار ذات صلة", page)
        self.assertIn("../news/" + "a" * 20 + ".html", page)
        self.assertNotIn("https://publisher.example", page)


class InternalNewsBriefTests(unittest.TestCase):
    def test_rss_page_has_source_date_and_plain_attribution_without_external_navigation(self):
        item = {
            "id": "a" * 20,
            "title": "عنوان <script>alert(1)</script> — وكالة المثال",
            "summary": "موجز آمن",
            "source_name": "وكالة المثال",
            "url": "https://news.google.com/rss/redirect?id=secret",
            "source_url": "https://publisher.example",
            "region": "world",
            "published_at": "2026-11-02T11:30:00Z",
            "excerpt": "تفاصيل إضافية من موجز الناشر تشرح الخبر بوضوح.",
            "image_url": "https://images.example/story.jpg",
        }
        page = render_news.render(item, [{"id": "b" * 20, "title": "خبر مرتبط", "region": "world"}])
        self.assertIn("وكالة المثال", page)
        self.assertIn("2026-11-02 11:30 UTC", page)
        self.assertNotIn("https://publisher.example", page)
        self.assertNotIn('href="https://', page)
        self.assertIn("https://images.example/story.jpg", page)
        self.assertIn("تفاصيل الخبر", page)
        self.assertIn("تفاصيل إضافية من موجز الناشر", page)
        self.assertNotIn("تعرض هذه الصفحة المعلومات المتاحة في موجز الناشر", page)
        self.assertNotIn("عن هذه المادة", page)
        self.assertNotIn("news.google.com", page)
        self.assertNotIn("<script>", page)
        self.assertIn("./" + "b" * 20 + ".html", page)

    def test_rss_page_without_valid_source_link_never_redirects(self):
        item = {"id": "c" * 20, "title": "خبر", "summary": "موجز", "source_name": "ناشر", "url": "https://publisher.example/story", "source_url": "javascript:alert(1)"}
        page = render_news.render(item)
        self.assertIn("الناشر: ناشر", page)
        self.assertNotIn('href="https://publisher.example', page)
        self.assertNotIn("javascript:", page)
        self.assertIn("../assets/news-illustrations/world.jpg", page)
        self.assertIn("رسم توضيحي لقسم العالم؛ لا يصوّر الحدث نفسه", page)
        self.assertNotIn("عن هذه المادة", page)
        self.assertNotIn("تعرض هذه الصفحة المعلومات المتاحة في موجز الناشر", page)

    def test_build_renders_rss_news_only_without_duplicates_for_full_reports(self):
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


class BuildAndBrowserTests(unittest.TestCase):
    def test_clean_site_build_omits_browser_tasks_and_trend_pages(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "site"
            output.mkdir()
            (output / "task-chat.html").write_text("stale", encoding="utf-8")
            (output / "trends").mkdir()
            (output / "trends" / "stale.html").write_text("stale", encoding="utf-8")
            build_site.build(output)
            for name in ["index.html", "archive.html", "section.html", "videos.html", "archive.js", "section.js", "videos.js"]:
                self.assertTrue((output / name).is_file(), name)
            self.assertFalse((output / "task-chat.html").exists())
            self.assertFalse((output / "trends").exists())
            self.assertTrue((output / "assets" / "news-illustrations" / "world.jpg").is_file())
            self.assertTrue((output / "news").is_dir())
            self.assertTrue((output / "reports").is_dir())

    def test_homepage_has_no_browser_task_link_or_trend_monitor(self):
        root = Path(__file__).resolve().parents[1]
        page = (root / "index.html").read_text(encoding="utf-8").casefold()
        app = (root / "app.js").read_text(encoding="utf-8").casefold()
        self.assertNotIn("task-chat", page)
        self.assertNotIn("trend", page)
        self.assertNotIn("google trends", app)
        self.assertIn("home_window_ms = 3 * 24 * 60 * 60 * 1000", app)
        self.assertIn("recentrecords(allrecords())", app)
        self.assertIn("archive.html", page)
        self.assertIn("world news", page)


if __name__ == "__main__":
    unittest.main()
