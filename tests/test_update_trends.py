import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from scripts.update_trends import parse_date, safe_https_url, child_text
import xml.etree.ElementTree as ET


class TrendsRadarTests(unittest.TestCase):
    def test_parse_date_normalizes_to_utc(self):
        value = parse_date("Fri, 06 Nov 2026 20:00:00 +0200")
        self.assertEqual(value, datetime(2026, 11, 6, 18, 0, tzinfo=timezone.utc))

    def test_rejects_non_https_and_credentials(self):
        self.assertEqual(safe_https_url("http://example.com/a"), "")
        self.assertEqual(safe_https_url("https://user:pass@example.com/a"), "")
        self.assertEqual(safe_https_url("https://example.com/a"), "https://example.com/a")

    def test_namespace_local_names_are_read(self):
        node = ET.fromstring(
            '<item xmlns:ht="https://example.com/ht">'
            '<ht:approx_traffic>20K+</ht:approx_traffic></item>'
        )
        self.assertEqual(child_text(node, "approx_traffic"), "20K+")


if __name__ == "__main__":
    unittest.main()
