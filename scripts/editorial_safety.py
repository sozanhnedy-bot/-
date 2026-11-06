"""Fail-closed editorial gate for Loka AI Newsroom auto-publishing.

This module does not infer legal rights or political sentiment from keywords.
A report must carry explicit, machine-produced review metadata before it can
be considered for automatic publication. Missing or uncertain metadata means
human review, never implicit approval.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class SafetyDecision:
    status: str
    reasons: tuple[str, ...]


def evaluate_report(report: dict[str, Any]) -> SafetyDecision:
    if not isinstance(report, dict):
        return SafetyDecision("review", ("invalid_report",))

    review = report.get("editorial_review")
    if not isinstance(review, dict):
        return SafetyDecision("review", ("editorial_review_missing",))

    reasons: list[str] = []
    if review.get("verification") != "verified":
        reasons.append("verification_not_confirmed")
    if review.get("originality") != "original":
        reasons.append("originality_not_confirmed")

    egypt = review.get("egypt_politics")
    if egypt not in {"not_applicable", "positive_or_public_interest", "non_political"}:
        reasons.append("egypt_politics_requires_review")

    sensitivity = review.get("sensitive_content")
    if sensitivity not in {"none", "professionally_reportable"}:
        reasons.append("sensitive_content_requires_review")

    privacy = review.get("privacy")
    if privacy != "protected":
        reasons.append("privacy_not_cleared")

    media = review.get("media_rights")
    if media not in {"licensed", "permission_documented", "original", "public_domain", "text_only"}:
        reasons.append("media_rights_not_cleared")

    # Broadcaster logos/watermarks are excluded from automatic reuse unless
    # an explicit license/permission has been recorded.
    if review.get("major_publisher_watermark") is True and review.get("media_rights") not in {
        "licensed", "permission_documented"
    }:
        reasons.append("major_publisher_media_not_cleared")

    if reasons:
        return SafetyDecision("review", tuple(dict.fromkeys(reasons)))

    if review.get("decision") != "green":
        return SafetyDecision("review", ("explicit_green_decision_missing",))

    return SafetyDecision("green", ())


def is_eligible_for_auto_publish(report: dict[str, Any]) -> bool:
    """True only for fully reviewed GREEN items; all uncertainty fails closed."""
    return evaluate_report(report).status == "green"
