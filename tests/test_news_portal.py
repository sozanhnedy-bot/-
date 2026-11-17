import json
import tempfile
from datetime import datetime, timezone
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import build_site, publish_social, render_articles, render_news, render_seo


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

    def test_original_report_includes_news_article_structured_data(self):
        report = {
            "id": "a" * 20,
            "title": "عنوان تقرير أصلي",
            "dek": "مقدمة التقرير",
            "published_at": "2026-11-17T10:00:00Z",
            "sections": [{"heading": "تفاصيل", "paragraphs": ["نص أصلي"]}],
        }
        page = render_articles.render(report, "aaaaaa")
        self.assertIn('type="application/ld+json"', page)
        self.assertIn('"@type": "NewsArticle"', page)
        self.assertIn('"datePublished": "2026-11-17T10:00:00Z"', page)
        self.assertIn('"name": "World News"', page)
        self.assertIn('"@id": "https://sozanhnedy-bot.github.io/-/reports/' + "a" * 20 + '.html"', page)

    def test_full_report_can_show_real_related_internal_news(self):
        report = {"id": "fb839c2fe70d9e789a03", "title": "تقرير", "sections": []}
        page = render_articles.render(report, "fb839c", {"region": "egypt"}, [{"id": "a" * 20, "title": "خبر من مصر", "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.", "source_name": "مصدر", "source_url": "https://publisher.example"}])
        self.assertIn("أخبار ذات صلة", page)
        self.assertIn("../news/" + "a" * 20 + ".html", page)
        self.assertNotIn("https://publisher.example", page)


class InternalNewsBriefTests(unittest.TestCase):
    def test_title_only_rss_item_is_not_publicly_rendered(self):
        item = {
            "id": "d" * 20,
            "title": "عنوان فقط",
            "summary": "",
            "summary_type": "source_notice",
            "excerpt": "عنوان فقط",
            "source_name": "ناشر",
        }
        self.assertFalse(render_news.has_public_details(item))

    def test_public_details_require_minimum_distinct_content(self):
        base = {"title": "اجتماع إقليمي بشأن تطورات جديدة", "source_name": "وكالة المثال", "source_url": "https://publisher.example"}
        self.assertFalse(render_news.has_public_details({
            **base, "summary": "اجتماع إقليمي بشأن تطورات جديدة - وكالة المثال", "excerpt": "",
        }))
        self.assertFalse(render_news.has_public_details({**base, "summary": "تفاصيل موجزة"}))
        self.assertTrue(render_news.has_public_details({
            **base,
            "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.",
        }))
        self.assertFalse(render_news.has_public_details({
            **base,
            "summary": "",
            "article_content": " ".join(["نص منقول من المقال الأصلي" for _ in range(35)]),
        }))

    def test_rss_brief_pages_are_noindex_and_not_in_sitemaps(self):
        item = {
            "id": "d" * 20,
            "title": "عنوان موجز",
            "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.",
            "source_name": "مصدر",
            "source_url": "https://publisher.example",
        }
        page = render_news.render(item)
        self.assertIn('name="robots" content="noindex,follow"', page)
        sitemap = render_seo.render_sitemap({"items": [item]}, {"items": []})
        self.assertNotIn("/news/" + "d" * 20 + ".html", sitemap)

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
            "image_rights_status": "cc-by",
            "image_credit": "وكالة المثال",
            "image_license_url": "https://publisher.example/license",
        }
        page = render_news.render(item, [{"id": "b" * 20, "title": "خبر مرتبط", "region": "world", "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.", "source_name": "مصدر", "source_url": "https://publisher.example"}])
        self.assertIn("وكالة المثال", page)
        self.assertIn("2026-11-02 11:30 UTC", page)
        self.assertIn("https://publisher.example", page)
        self.assertIn('rel="nofollow noopener noreferrer"', page)
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
        self.assertNotIn("news-illustrations/world.jpg", page)
        self.assertNotIn("رسم توضيحي لقسم العالم", page)
        self.assertNotIn("عن هذه المادة", page)
        self.assertNotIn("تعرض هذه الصفحة المعلومات المتاحة في موجز الناشر", page)

    def test_full_publisher_article_text_is_never_rendered(self):
        item = {
            "id": "e" * 20,
            "title": "خبر مفصل",
            "source_name": "ناشر",
            "article_content": "بداية المادة " + ("تفصيل من نص الناشر. " * 100),
        }
        page = render_news.render(item)
        self.assertNotIn("بداية المادة", page)
        self.assertNotIn("تفصيل من نص الناشر", page)
        self.assertNotIn("مقتطف محدود من نص أتاحه الناشر", page)

    def test_rss_page_hides_images_without_explicit_reuse_rights(self):
        item = {"id": "f" * 20, "title": "خبر", "source_name": "ناشر",
                "image_url": "https://images.example/story.jpg"}
        page = render_news.render(item)
        self.assertNotIn("https://images.example/story.jpg", page)
        item.update({"image_rights_status": "cc-by", "image_credit": "وكالة المثال",
                     "image_license_url": "https://publisher.example/license"})
        page = render_news.render(item)
        self.assertIn("https://images.example/story.jpg", page)
        item["image_license_url"] = "javascript:alert(1)"
        page = render_news.render(item)
        self.assertNotIn("https://images.example/story.jpg", page)

    def test_rss_page_hides_google_placeholders_and_publisher_brand_images(self):
        item = {"id": "d" * 20, "title": "خبر", "source_name": "ناشر", "region": "world",
                "image_url": "https://lh3.googleusercontent.com/news-photo=s0-w300"}
        page = render_news.render(item)
        self.assertNotIn("lh3.googleusercontent.com", page)
        self.assertNotIn("news-illustrations/world.jpg", page)
        item["image_url"] = "https://www.ewtnnews.com/EWTNNewsLogo.png"
        page = render_news.render(item)
        self.assertNotIn("EWTNNewsLogo.png", page)
        self.assertNotIn("news-illustrations/world.jpg", page)

    def test_rss_page_hides_thumbnail_reused_across_many_stories(self):
        image = "https://images.example/generic-thumbnail.jpg"
        items = [
            {"id": chr(ord("d") + index) * 20, "title": f"خبر {index}", "source_name": "ناشر", "region": "world", "image_url": image}
            for index in range(3)
        ]
        page = render_news.render(items[0], items)
        self.assertNotIn(image, page)
        self.assertNotIn("news-illustrations/world.jpg", page)

    def test_build_renders_rss_news_only_without_duplicates_for_full_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            news_path, reports_path = root / "news.json", root / "reports.json"
            news_path.write_text(json.dumps({"items": [
                {"id": "full-report", "title": "مكتمل"},
                {"id": "rss-brief", "title": "موجز", "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.", "source_name": "مصدر", "source_url": "https://publisher.example"},
            ]}, ensure_ascii=False), encoding="utf-8")
            reports_path.write_text(json.dumps({"items": [{"id": "full-report"}]}), encoding="utf-8")
            output = root / "site"
            with patch.object(render_news, "NEWS_PATH", news_path), patch.object(render_news, "REPORTS_PATH", reports_path):
                self.assertEqual(render_news.build(output), 1)
            self.assertTrue((output / "news" / "rss-brief.html").is_file())
            self.assertFalse((output / "news" / "full-report.html").exists())

    def test_build_skips_title_only_rss_pages(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            news_path, reports_path = root / "news.json", root / "reports.json"
            news_path.write_text(json.dumps({"items": [{
                "id": "weak-item", "title": "عنوان فقط", "summary": "",
                "summary_type": "source_notice", "excerpt": "عنوان فقط", "source_name": "مصدر"
            }]}), encoding="utf-8")
            reports_path.write_text(json.dumps({"items": []}), encoding="utf-8")
            output = root / "site"
            with patch.object(render_news, "NEWS_PATH", news_path), patch.object(render_news, "REPORTS_PATH", reports_path):
                self.assertEqual(render_news.build(output), 0)
            self.assertFalse((output / "news" / "weak-item.html").exists())


class BuildAndBrowserTests(unittest.TestCase):
    def test_seo_documents_include_public_routes_and_exclude_weak_news_pages(self):
        news = {"updated_at": "2026-11-15T00:00:00Z", "items": [
            {"id": "a" * 20, "title": "خبر غني", "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.", "source_name": "مصدر", "source_url": "https://publisher.example", "published_at": "2026-11-15T00:00:00Z"},
            {"id": "b" * 20, "title": "عنوان فقط", "summary": "", "summary_type": "source_notice", "excerpt": "عنوان فقط", "source_name": "مصدر"},
        ]}
        reports = {"items": [{"id": "c" * 20, "title": "تقرير", "published_at": "2026-11-14T00:00:00Z", "sections": [{"heading": "قسم", "paragraphs": ["نص"]}]}]}
        sitemap = render_seo.render_sitemap(news, reports)
        self.assertNotIn("/news/" + "a" * 20 + ".html", sitemap)
        self.assertIn("https://sozanhnedy-bot.github.io/-/reports/" + "c" * 20 + ".html", sitemap)
        self.assertNotIn("/news/" + "b" * 20 + ".html", sitemap)
        self.assertNotIn("archive.html", sitemap)
        self.assertNotIn("section.html", sitemap)
        self.assertNotIn("videos.html", sitemap)
        self.assertNotIn("section.html?category=", sitemap)
        self.assertIn("Sitemap: https://sozanhnedy-bot.github.io/-/sitemap.xml", render_seo.render_robots())

    def test_news_sitemap_only_lists_recent_original_reports(self):
        now = datetime(2026, 11, 17, 12, 0, tzinfo=timezone.utc)
        reports = {"items": [
            {"id": "a" * 20, "title": "تقرير حديث", "sections": [{"heading": "قسم"}]},
            {"id": "b" * 20, "title": "تقرير قديم", "sections": [{"heading": "قسم"}]},
            {"id": "c" * 20, "title": "مسودة بلا أقسام"},
        ]}
        news = {"items": [
            {"id": "a" * 20, "published_at": "2026-11-17T10:00:00Z"},
            {"id": "b" * 20, "published_at": "2026-11-14T10:00:00Z"},
            {"id": "c" * 20, "published_at": "2026-11-17T11:00:00Z"},
        ]}
        sitemap = render_seo.render_news_sitemap(reports, now, news)
        self.assertIn("xmlns:news=", sitemap)
        self.assertIn("/reports/" + "a" * 20 + ".html", sitemap)
        self.assertIn("تقرير حديث", sitemap)
        self.assertNotIn("b" * 20, sitemap)
        self.assertNotIn("c" * 20, sitemap)

    def test_report_sitemap_lastmod_uses_linked_news_timestamp(self):
        news = {"items": [{"id": "e" * 20, "published_at": "2026-11-17T09:15:00Z"}]}
        reports = {"items": [{"id": "e" * 20, "title": "تقرير", "sections": [{"heading": "قسم"}]}]}
        sitemap = render_seo.render_sitemap(news, reports)
        self.assertIn("<lastmod>2026-11-17</lastmod>", sitemap)

    def test_sitemap_rejects_invalid_content_ids(self):
        news = {"items": [
            {"id": "../bad", "title": "خبر", "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.", "source_name": "مصدر", "source_url": "https://publisher.example"},
        ]}
        reports = {"items": [
            {"id": "../bad", "title": "تقرير", "sections": [{"heading": "قسم", "paragraphs": ["نص"]}]},
        ]}
        sitemap = render_seo.render_sitemap(news, reports)
        self.assertNotIn("../bad", sitemap)
        self.assertNotIn("/news/../", sitemap)
        self.assertNotIn("/reports/../", sitemap)

    def test_public_seo_metadata_is_present_on_static_and_generated_pages(self):
        root = Path(__file__).resolve().parents[1]
        for filename in ("index.html", "archive.html", "section.html", "videos.html"):
            page = (root / filename).read_text(encoding="utf-8")
            self.assertIn('meta property="og:url"', page)
            self.assertIn('link rel="canonical"', page)
        item = {"id": "f" * 20, "title": "خبر غني", "summary": "ناقش الوزراء خلال الاجتماع آليات التعاون والموعد المتوقع للخطوات المقبلة وفق بيان رسمي صدر اليوم.", "source_name": "مصدر", "published_at": "2026-11-15T00:00:00Z"}
        page = render_news.render(item)
        self.assertIn('meta property="og:url"', page)
        self.assertIn('meta name="twitter:title"', page)
        self.assertIn('rel="canonical"', page)

    def test_clean_site_build_omits_browser_tasks_and_trend_pages(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "site"
            output.mkdir()
            (output / "task-chat.html").write_text("stale", encoding="utf-8")
            (output / "trends").mkdir()
            (output / "trends" / "stale.html").write_text("stale", encoding="utf-8")
            build_site.build(output)
            for name in ["index.html", "archive.html", "section.html", "videos.html", "photos.html", "about.html", "privacy.html", "contact.html", "editorial-policy.html", "archive.js", "section.js", "videos.js", "photos.js"]:
                self.assertTrue((output / name).is_file(), name)
            self.assertFalse((output / "task-chat.html").exists())
            self.assertFalse((output / "trends").exists())
            self.assertTrue((output / "assets" / "news-illustrations" / "world.jpg").is_file())
            self.assertTrue((output / "news").is_dir())
            self.assertTrue((output / "reports").is_dir())
            self.assertTrue((output / "sitemap.xml").is_file())
            self.assertTrue((output / "news-sitemap.xml").is_file())
            self.assertIn("news-sitemap.xml", (output / "robots.txt").read_text(encoding="utf-8"))
            self.assertIn('name="robots" content="noindex,follow"', (output / "photos.html").read_text(encoding="utf-8"))
            self.assertTrue((output / "robots.txt").is_file())
            self.assertIn("Alaaloka878787@GMAIL.COM", (output / "contact.html").read_text(encoding="utf-8"))

    def test_public_site_data_strips_full_publisher_article_text(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "site"
            build_site.build(output)
            public_news = json.loads((output / "data" / "news.json").read_text(encoding="utf-8"))
            self.assertTrue(all("article_content" not in item for item in public_news.get("items", [])))
            self.assertTrue(all(len(item.get("excerpt", "")) <= 300 for item in public_news.get("items", [])))
            self.assertTrue(all(len(item.get("summary", "")) <= 300 for item in public_news.get("items", []) if item.get("summary_type") == "source_excerpt"))
            latest_path = output / "data" / "news-latest.json"
            self.assertTrue(latest_path.is_file())
            latest_news = json.loads(latest_path.read_text(encoding="utf-8"))
            self.assertLessEqual(len(latest_news.get("items", [])), len(public_news.get("items", [])))
            cutoff = datetime.now(timezone.utc).timestamp() - 72 * 60 * 60
            for item in latest_news.get("items", []):
                published = item.get("published_at") or item.get("created_at")
                self.assertGreaterEqual(datetime.fromisoformat(published.replace("Z", "+00:00")).timestamp(), cutoff)
                self.assertNotIn("article_content", item)
            homepage_script = (Path(__file__).resolve().parents[1] / "app.js").read_text(encoding="utf-8")
            self.assertIn('./data/news-latest.json', homepage_script)
            self.assertNotIn('fetchJson("./data/news.json")', homepage_script)
            worker = (Path(__file__).resolve().parents[1] / "service-worker.js").read_text(encoding="utf-8")
            self.assertIn('"data/news-latest.json"', worker)
            self.assertIn('const CACHE_NAME = "world-news-v19-entity-match-guard";', worker)

    def test_verified_image_filter_bumps_app_and_service_worker_cache_versions(self):
        root = Path(__file__).resolve().parents[1]
        page = (root / "index.html").read_text(encoding="utf-8")
        worker = (root / "service-worker.js").read_text(encoding="utf-8")
        self.assertIn("app.js?v=20261117-entity-match-guard", page)
        self.assertIn('const CACHE_NAME = "world-news-v18-image-fallback-fix";', worker)

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

    def test_section_and_archive_apply_the_public_quality_gate(self):
        root = Path(__file__).resolve().parents[1]
        for filename in ("section.js", "archive.js"):
            script = (root / filename).read_text(encoding="utf-8")
            self.assertIn("hasSubstantiveDetail", script)
            self.assertIn("summary_type", script)
            self.assertNotIn("article_content", script)


if __name__ == "__main__":
    unittest.main()
