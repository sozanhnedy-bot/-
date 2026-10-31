import unittest
from datetime import datetime, timezone

from scripts.update_news import build_update, clean_text, parse_feed


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
        </item></channel></rss>""".encode("utf-8")
        items = parse_feed(xml, "egypt", "اختبار", now=NOW)
        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["title"], "خبر مهم")
        self.assertEqual(items[0]["excerpt"], "نص مهم & تفاصيل")
        self.assertEqual(items[0]["source_name"], "وكالة المثال")
        self.assertEqual(items[0]["region"], "egypt")

    def test_clean_text_unescapes_entities_and_collapses_whitespace(self):
        self.assertEqual(clean_text("<p>أخبار&nbsp; &amp; تفاصيل</p>"), "أخبار & تفاصيل")

    def test_rejects_dtd_and_unsafe_links(self):
        with self.assertRaises(ValueError):
            parse_feed(b"<!DOCTYPE x [<!ENTITY e 'boom'>]><rss/>", "world", "feed", now=NOW)
        xml = b"<rss><channel><item><title>X</title><link>javascript:alert(1)</link></item></channel></rss>"
        self.assertEqual(parse_feed(xml, "world", "feed", now=NOW), [])

    def test_skips_entries_older_than_48_hours(self):
        xml = """<rss><channel><item><title>قديم</title><link>https://example.com/old</link>
        <description>مقتطف</description><pubDate>Mon, 26 Oct 2026 00:00:00 GMT</pubDate>
        </item></channel></rss>""".encode("utf-8")
        self.assertEqual(parse_feed(xml, "world", "feed", now=NOW), [])


class NewsUpdateTests(unittest.TestCase):
    def setUp(self):
        self.feed = {"name": "اختبار", "region": "world", "url": "https://news.google.com/rss", "enabled": True}
        self.entries = [
            {"title": "خبر 1", "excerpt": "مقتطف 1", "url": "https://news.google.com/a", "source_name": "المصدر", "source_url": "", "region": "world", "published_at": "2026-10-31T00:00:00Z"},
            {"title": "خبر 2", "excerpt": "مقتطف 2", "url": "https://news.google.com/b", "source_name": "المصدر", "source_url": "", "region": "world", "published_at": "2026-10-30T23:00:00Z"},
            {"title": "خبر موجود", "excerpt": "مقتطف", "url": "https://news.google.com/existing", "source_name": "المصدر", "source_url": "", "region": "world", "published_at": "2026-10-30T22:00:00Z"},
        ]

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
        self.assertFalse(warnings)

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

    def test_all_feed_failures_preserve_existing_data(self):
        existing = {"schema_version": 1, "updated_at": "previous", "items": [{"url": "https://example.com", "summary": "قديم"}]}

        def fail(*_args):
            raise TimeoutError("network")

        with self.assertRaises(RuntimeError):
            build_update(existing, [self.feed], fail, lambda _item: "unused", now=NOW)

    def test_no_new_items_does_not_change_timestamp(self):
        existing = {"schema_version": 1, "updated_at": "previous", "items": [{"url": self.entries[0]["url"], "summary_date": "2026-10-30"}]}
        data, changed, _ = build_update(
            existing,
            [self.feed],
            lambda *_: [self.entries[0]],
            lambda _item: "unused",
            now=NOW,
        )
        self.assertFalse(changed)
        self.assertEqual(data["updated_at"], "previous")


if __name__ == "__main__":
    unittest.main()
