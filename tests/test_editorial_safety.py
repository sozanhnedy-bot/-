import unittest

from scripts.editorial_safety import evaluate_report, is_eligible_for_auto_publish


def reviewed(**overrides):
    review = {
        "decision": "green",
        "verification": "verified",
        "originality": "original",
        "egypt_politics": "non_political",
        "sensitive_content": "none",
        "privacy": "protected",
        "media_rights": "original",
        "major_publisher_watermark": False,
    }
    review.update(overrides)
    return {"title": "خبر محلي", "editorial_review": review}


class EditorialSafetyTests(unittest.TestCase):
    def test_verified_local_story_can_pass(self):
        self.assertTrue(is_eligible_for_auto_publish(reviewed()))

    def test_positive_egyptian_achievement_can_pass(self):
        self.assertTrue(is_eligible_for_auto_publish(
            reviewed(egypt_politics="positive_or_public_interest")
        ))

    def test_missing_review_goes_to_review(self):
        decision = evaluate_report({"title": "خبر عن حادث محلي"})
        self.assertEqual(decision.status, "review")
        self.assertIn("editorial_review_missing", decision.reasons)

    def test_egyptian_political_criticism_is_not_green(self):
        item = reviewed(egypt_politics="criticism_or_controversy")
        self.assertFalse(is_eligible_for_auto_publish(item))

    def test_unclear_egyptian_politics_goes_to_review(self):
        self.assertEqual(
            evaluate_report(reviewed(egypt_politics="unclear")).status,
            "review",
        )

    def test_local_governorate_word_is_not_a_blocking_keyword(self):
        item = reviewed()
        item["title"] = "خدمات جديدة في محافظة محلية"
        self.assertTrue(is_eligible_for_auto_publish(item))

    def test_sensitive_story_requires_explicit_editorial_clearance(self):
        self.assertFalse(is_eligible_for_auto_publish(
            reviewed(sensitive_content="requires_review")
        ))

    def test_victim_privacy_must_be_protected(self):
        self.assertFalse(is_eligible_for_auto_publish(
            reviewed(privacy="identity_exposed")
        ))

    def test_personal_clip_requires_documented_permission_or_license(self):
        self.assertFalse(is_eligible_for_auto_publish(
            reviewed(media_rights="unknown")
        ))
        self.assertTrue(is_eligible_for_auto_publish(
            reviewed(media_rights="permission_documented")
        ))

    def test_major_broadcaster_watermark_is_not_reused_without_rights(self):
        self.assertFalse(is_eligible_for_auto_publish(
            reviewed(media_rights="unknown", major_publisher_watermark=True)
        ))

    def test_text_only_item_can_pass_if_other_checks_are_green(self):
        self.assertTrue(is_eligible_for_auto_publish(
            reviewed(media_rights="text_only")
        ))


if __name__ == "__main__":
    unittest.main()
