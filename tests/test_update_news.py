import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from scripts.update_news import RSS_NO_DETAILS, ai_summarization_enabled, build_update, clean_text, newest_items, parse_feed, summarize_rss_excerpt


NOW = datetime(2026, 10, 31, 0, 0, tzinfo=timezone.utc)


class NewsParsingTests(unittest.TestCase):
    def test_parse_rss_removes_markup_and_keeps_source(self):
        xml = """<?xml version='1.0' encoding='UTF-8'?>
        <rss version='2.0'><channel><item>
          <title>خبر مهم</title>
          <link>https://example.com/news/1</link>
          <description><![CDATA[<p>نص <b>مهم</b> &amp; تفاصيل</p>]]></description>
          <pubDate>Sat, 31 Oct 2026 00:00:00 GMT</pubDate>
          <source url='https://example.com'>وكالة المثال</source>
          <enclosure url='https://images.example/photo.jpg' type='image/jpeg'/>
        </item></channel></rss>""".encode("utf-8")
        items = parse_feed(xml, "egypt", "اختبار", now=NOW)
        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["title"], "خبر مهم")
        self.assertEqual(items[0]["excerpt"], "نص مهم & تفاصيل")
        self.assertEqual(items[0]["source_name"], "وكالة المثال")
        self.assertEqual(items[0]["region"], "egypt")
        self.assertEqual(items[0]["image_url"], "https://images.example/photo.jpg")

    def test_parse_rss_preserves_full_article_paragraphs(self):
        xml = """<rss><channel><item>
          <title>تقرير مفصل</title><link>https://example.com/news/full</link>
          <description><![CDATA[<p>الفقرة الأولى بتفاصيل الحدث.</p><p>الفقرة الثانية وتطورات جديدة.</p>]]></description>
          <content:encoded xmlns:content="http://purl.org/rss/1.0/modules/content/"><![CDATA[<h2>الخلفية</h2><p>هذه خلفية موسعة للخبر.</p><p>وهذه تفاصيل إضافية مهمة.</p>]]></content:encoded>
          <pubDate>Sat, 31 Oct 2026 00:00:00 GMT</pubDate>
        </item></channel></rss>""".encode("utf-8")
        items = parse_feed(xml, "world", "اختبار", now=NOW)
        self.assertIn("الخلفية", items[0]["article_content"])
        self.assertIn("\n", items[0]["article_content"])
        self.assertIn("تفاصيل إضافية", items[0]["article_content"])

    def test_parse_rss_extracts_lead_image_from_description_html(self):
        xml = """<rss><channel><item>
          <title>خبر بصورة</title><link>https://example.com/news/photo</link>
          <description><![CDATA[<p>تفاصيل الخبر</p><img src="https://cdn.example.com/images/story.webp" alt="صورة الخبر">]]></description>
          <pubDate>Sat, 31 Oct 2026 00:00:00 GMT</pubDate>
        </item></channel></rss>""".encode("utf-8")
        items = parse_feed(xml, "world", "اختبار", now=NOW)
        self.assertEqual(items[0]["image_url"], "https://cdn.example.com/images/story.webp")

    def test_parse_atom_extracts_image_enclosure_link(self):
        xml = """<feed xmlns="http://www.w3.org/2005/Atom"><entry>
          <title>خبر ذري</title><link href="https://example.com/news/atom"/>
          <link rel="enclosure" type="image/jpeg" href="https://cdn.example.com/atom.jpg"/>
          <updated>2026-10-31T00:00:00Z</updated>
        </entry></feed>""".encode("utf-8")
        items = parse_feed(xml, "world", "اختبار", now=NOW)
        self.assertEqual(items[0]["image_url"], "https://cdn.example.com/atom.jpg")

    def test_parse_rss_rejects_google_placeholders_and_publisher_brand_images(self):
        xml = """<rss><channel><item>
          <title>خبر بصورة</title><link>https://example.com/news/photo</link>
          <description><![CDATA[<img src="https://lh3.googleusercontent.com/news-photo=s0-w300">]]></description>
          <pubDate>Sat, 31 Oct 2026 00:00:00 GMT</pubDate>
        </item></channel></rss>""".encode("utf-8")
        items = parse_feed(xml, "world", "اختبار", now=NOW)
        from scripts.update_news import _safe_image_url
        self.assertEqual(items[0]["image_url"], "")
        for url in (
            "https://lh3.googleusercontent.com/news-photo=s0-w300",
            "https://www.ewtnnews.com/EWTNNewsLogo.png",
            "https://techreviewafrica.com/assets/images/seo/633592b1b61361664455345.jpeg",
            "https://www.aljazeera.net/images/aljazeera-arabic-social-preview-v2.jpg",
        ):
            self.assertEqual(_safe_image_url(url, "image"), "", url)

    def test_clean_text_unescapes_entities_and_collapses_whitespace(self):
        self.assertEqual(clean_text("<p>أخبار&nbsp; &amp; تفاصيل</p>"), "أخبار & تفاصيل")

    def test_rejects_dtd_and_unsafe_links(self):
        with self.assertRaises(ValueError):
            parse_feed(b"<!DOCTYPE x [<!ENTITY e 'boom'>]><rss/>", "world", "feed", now=NOW)
        xml = b"<rss><channel><item><title>X</title><link>javascript:alert(1)</link></item></channel></rss>"
        self.assertEqual(parse_feed(xml, "world", "feed", now=NOW), [])

    def test_skips_entries_older_than_seven_days(self):
        xml = """<rss><channel><item><title>قديم</title><link>https://example.com/old</link>
        <description>مقتطف</description><pubDate>Tue, 20 Oct 2026 00:00:00 GMT</pubDate>
        </item></channel></rss>""".encode("utf-8")
        self.assertEqual(parse_feed(xml, "world", "feed", now=NOW), [])


class NewsUpdateTests(unittest.TestCase):
    def setUp(self):
        self.feed = {"name": "اختبار", "region": "world", "category": "economy", "url": "https://news.google.com/rss", "enabled": True}
        self.entries = [
            {"title": "خبر 1", "excerpt": "مقتطف 1", "url": "https://news.google.com/a", "source_name": "المصدر", "source_url": "", "region": "world", "published_at": "2026-10-31T00:00:00Z"},
            {"title": "خبر 2", "excerpt": "مقتطف 2", "url": "https://news.google.com/b", "source_name": "المصدر", "source_url": "", "region": "world", "published_at": "2026-10-30T23:00:00Z"},
            {"title": "خبر موجود", "excerpt": "مقتطف", "url": "https://news.google.com/existing", "source_name": "المصدر", "source_url": "", "region": "world", "published_at": "2026-10-30T22:00:00Z"},
        ]

    def test_newest_items_are_sorted_before_the_per_feed_limit(self):
        items = [
            {"id": "older", "published_at": "2026-10-31T10:00:00Z"},
            {"id": "newest", "published_at": "2026-10-31T12:00:00Z"},
            {"id": "middle", "published_at": "2026-10-31T11:00:00Z"},
        ]
        self.assertEqual([item["id"] for item in newest_items(items, 2)], ["newest", "middle"])

    def test_existing_news_older_than_seven_days_are_removed(self):
        existing = {"schema_version": 1, "items": [
            {"id": "expired", "url": "https://news.google.com/expired", "published_at": "2026-10-23T23:59:59Z"},
            {"id": "week-old", "url": "https://news.google.com/week-old", "published_at": "2026-10-24T00:00:00Z"},
            {"id": "unknown-date", "url": "https://news.google.com/unknown", "published_at": "unknown"},
        ]}
        data, changed, _ = build_update(
            existing,
            [self.feed],
            lambda *_: [],
            lambda _item: "unused",
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual([item["id"] for item in data["items"]], ["week-old", "unknown-date"])

    def test_new_items_preserve_source_excerpt_and_photo(self):
        item = {
            **self.entries[0],
            "source_name": "وكالة المثال",
            "source_url": "https://publisher.example",
            "excerpt": "تفاصيل من موجز الناشر تتجاوز العنوان وتحتوي معلومات إضافية.",
            "image_url": "https://images.example/story.jpg",
        }
        feed = self.feed
        data, changed, _ = build_update(
            {"schema_version": 1, "items": []},
            [feed],
            lambda *_: [item],
            lambda _item: "ملخص من المصدر",
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual(data["items"][0]["excerpt"], item["excerpt"])
        self.assertEqual(data["items"][0]["image_url"], item["image_url"])

    def test_new_items_store_full_article_content(self):
        item = {**self.entries[0], "article_content": "الفقرة الأولى\nالفقرة الثانية"}
        data, changed, _ = build_update(
            {"schema_version": 1, "items": []},
            [self.feed],
            lambda *_: [item],
            lambda _item: "ملخص عربي",
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual(data["items"][0]["article_content"], item["article_content"])

    def test_duplicate_story_from_another_rss_query_backfills_excerpt_and_photo(self):
        existing_item = {
            "url": "https://news.google.com/rss/articles/source-story",
            "title": "تطورات دولية — وكالة المثال",
            "source_name": "وكالة المثال",
            "source_url": "https://publisher.example",
            "region": "world",
            "category": "world",
            "summary": RSS_NO_DETAILS,
            "summary_type": "source_notice",
            "summary_date": "2026-10-30",
        }
        incoming = {
            **self.entries[0],
            "title": "تطورات دولية",
            "url": "https://news.google.com/rss/articles/source-story-duplicate",
            "source_name": "وكالة المثال",
            "source_url": "https://publisher.example",
            "excerpt": "وصف خبري أطول بكثير من العنوان، يتضمن سياقًا أساسيًا من الناشر.",
            "image_url": "https://images.example/story.jpg",
        }
        feed = self.feed
        data, changed, _ = build_update(
            {"schema_version": 1, "updated_at": "previous", "items": [existing_item]},
            [feed],
            lambda *_: [incoming],
            lambda _item: self.fail("known story must not be summarized again"),
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual(len(data["items"]), 1)
        self.assertEqual(data["items"][0]["excerpt"], incoming["excerpt"])
        self.assertEqual(data["items"][0]["image_url"], incoming["image_url"])

    def test_deduplicates_and_respects_per_run_limit(self):
        existing = {"schema_version": 1, "updated_at": None, "items": [{"url": "https://news.google.com/existing", "summary_date": "2026-10-31"}]}
        summaries = []
        data, changed, warnings = build_update(
            existing,
            [self.feed],
            lambda *_: self.entries,
            lambda item: summaries.append(item["url"]) or "ملخص عربي محايد",
            now=NOW,
            max_per_run=1,
            max_per_day=30,
        )
        self.assertTrue(changed)
        self.assertEqual(len(summaries), 1)
        self.assertEqual(len(data["items"]), 2)
        self.assertEqual(data["items"][0]["category"], "economy")
        self.assertFalse(warnings)

    def test_specific_feed_category_wins_over_broad_world_feed(self):
        broad = {**self.feed, "category": "world"}
        item = {**self.entries[0]}
        data, changed, _ = build_update(
            {"schema_version": 1, "items": []},
            [broad, self.feed],
            lambda *_: [item],
            lambda _item: "ملخص عربي محايد",
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual(data["items"][0]["category"], "economy")

    def test_respects_daily_summary_cap(self):
        existing = {"schema_version": 1, "updated_at": None, "items": [{"url": "https://news.google.com/old", "summary_date": "2026-10-31"}]}
        data, changed, _ = build_update(
            existing,
            [self.feed],
            lambda *_: self.entries,
            lambda _item: "يجب ألا يُستدعى",
            now=NOW,
            max_per_run=3,
            max_per_day=1,
        )
        self.assertFalse(changed)
        self.assertEqual(data["items"], existing["items"])

    def test_title_only_rss_item_becomes_safe_brief_without_ai_call(self):
        item = {**self.entries[0], "excerpt": ""}
        data, changed, _ = build_update(
            {"schema_version": 1, "updated_at": None, "items": []},
            [self.feed],
            lambda *_: [item],
            lambda _item: self.fail("summarizer must not be called without an excerpt"),
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual(data["items"][0]["summary"], RSS_NO_DETAILS)
        self.assertEqual(data["items"][0]["summary_type"], "source_notice")

    def test_all_feed_failures_preserve_existing_data(self):
        existing = {"schema_version": 1, "updated_at": "previous", "items": [{"url": "https://example.com", "summary": "قديم"}]}

        def fail(*_args):
            raise TimeoutError("network")

        with self.assertRaises(RuntimeError):
            build_update(existing, [self.feed], fail, lambda _item: "unused", now=NOW)

    def test_no_new_items_does_not_change_timestamp(self):
        existing = {"schema_version": 1, "updated_at": "previous", "items": [{
            "url": self.entries[0]["url"], "region": "world", "category": "economy", "summary_date": "2026-10-30",
            "source_name": "المصدر", "source_url": "", "published_at": "2026-10-31T00:00:00Z",
            "excerpt": "مقتطف 1", "image_url": "",
        }]}
        data, changed, _ = build_update(
            existing,
            [self.feed],
            lambda *_: [self.entries[0]],
            lambda _item: "unused",
            now=NOW,
        )
        self.assertFalse(changed)
        self.assertEqual(data["updated_at"], "previous")

    def test_backfills_more_specific_category_for_existing_item(self):
        existing = {"schema_version": 1, "updated_at": "previous", "items": [{"url": self.entries[2]["url"], "region": "world"}]}
        data, changed, _ = build_update(
            existing,
            [self.feed],
            lambda *_: [self.entries[2]],
            lambda _item: self.fail("existing stories do not need new summaries"),
            now=NOW,
        )
        self.assertTrue(changed)
        self.assertEqual(data["items"][0]["category"], "economy")
        self.assertEqual(data["updated_at"], "2026-10-31T00:00:00Z")

    def test_ai_summaries_are_off_without_explicit_opt_in(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertFalse(ai_summarization_enabled())
        with patch.dict("os.environ", {"AI_SUMMARIZATION_ENABLED": "true"}, clear=True):
            self.assertTrue(ai_summarization_enabled())

    def test_rss_summary_uses_publisher_article_text_when_excerpt_is_title_only(self):
        title = "عنوان الخبر"
        article = "الفقرة الأولى من الخبر تشرح الحدث بتفاصيل أتاحها الناشر.\nالفقرة الثانية تضيف سياقًا وتطورات مؤكدة."
        item = {
            "title": title,
            "source_name": "المصدر",
            "excerpt": f"{title} - المصدر",
            "article_content": article,
        }
        self.assertEqual(summarize_rss_excerpt(item), article.replace("\n", " ")[:600])



class PublisherArticleExtractorTests(unittest.TestCase):
    def test_extracts_article_paragraphs_and_ignores_navigation_and_scripts(self):
        from scripts.update_news import extract_publisher_article
        page = """<html><body>
          <nav><p>قائمة التنقل لا يجب أن تظهر في التقرير.</p></nav>
          <article><h1>عنوان التقرير</h1>
            <p>هذه الفقرة الأولى من التقرير وتحتوي على تفاصيل الحدث ومعلوماته الأساسية.</p>
            <p>هذه الفقرة الثانية تشرح التطورات والسياق الذي نشره الموقع الأصلي.</p>
            <script>نص برمجي لا يجب أن يظهر</script>
          </article>
          <footer><p>روابط وتذييل الموقع لا تدخل في التقرير.</p></footer>
        </body></html>"""
        content = extract_publisher_article(page)
        self.assertIn("عنوان التقرير", content)
        self.assertIn("الفقرة الأولى", content)
        self.assertNotIn("قائمة التنقل", content)
        self.assertNotIn("نص برمجي", content)
        self.assertNotIn("روابط وتذييل", content)


class MetaImageExtractorTests(unittest.TestCase):
    def test_reads_open_graph_image_without_file_extension(self):
        from scripts.update_news import _MetaImageExtractor
        parser = _MetaImageExtractor()
        parser.feed('<html><meta property="og:image" content="https://cdn.example.com/photo?id=42"></html>')
        parser.close()
        self.assertEqual(parser.urls, ["https://cdn.example.com/photo?id=42"])


if __name__ == "__main__":
    unittest.main()
