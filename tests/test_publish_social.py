import os
import unittest
from unittest.mock import patch

from scripts import publish_social


CONTENT = {
    "key": "report:0123456789abcdef01234567",
    "report_id": "0123456789abcdef01234567",
    "caption": "World News | خبر للاختبار\nتفاصيل التقرير.",
    "url": "https://sozanhnedy-bot.github.io/-/r/012345.html",
    "image": "assets/brand-cover.jpg",
}


class SocialPublishingTests(unittest.TestCase):
    def test_instagram_caption_is_limited_to_2200_characters_and_keeps_url(self):
        content = {**CONTENT, "caption": "خبر " * 1000}
        caption = publish_social.instagram_caption(content)
        self.assertLessEqual(len(caption), 2200)
        self.assertTrue(caption.endswith(content["url"]))

    def test_instagram_publish_creates_container_then_publishes(self):
        with patch.object(
            publish_social,
            "request_json",
            side_effect=[{"id": "container-1"}, {"id": "published-2"}],
        ) as request:
            result = publish_social.post_to_instagram(
                CONTENT, "123456789", "test-token", "v27.0"
            )

        self.assertEqual(result, "published-2")
        self.assertEqual(request.call_count, 2)
        create_url, create_body = request.call_args_list[0].args
        self.assertTrue(create_url.endswith("/123456789/media"))
        self.assertEqual(
            create_body["image_url"],
            f"{publish_social.SITE_URL}/assets/brand-cover.jpg",
        )
        self.assertEqual(create_body["caption"], publish_social.instagram_caption(CONTENT))
        publish_url, publish_body = request.call_args_list[1].args
        self.assertTrue(publish_url.endswith("/123456789/media_publish"))
        self.assertEqual(publish_body, {"creation_id": "container-1"})

    def test_instagram_rejects_invalid_account_id_before_network_call(self):
        with patch.object(publish_social, "request_json") as request:
            with self.assertRaises(ValueError):
                publish_social.post_to_instagram(CONTENT, "not-an-id", "token", "v27.0")
        request.assert_not_called()

    def test_disabled_publisher_makes_no_external_calls(self):
        with patch.dict(os.environ, {"SOCIAL_AUTOPUBLISH": "false"}), patch.object(
            publish_social, "request_json"
        ) as request:
            self.assertEqual(publish_social.main(), 0)
        request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
