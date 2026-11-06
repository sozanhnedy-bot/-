# Loka AI Newsroom — Editorial and Publishing Policy

Status: requirements baseline only. This document does not enable publishing or change the active workflow.

## 1. Coverage and discovery
- Discover Egyptian local trends and public-interest stories, Arabic and global trends, entertainment, sports, human-interest stories, and verified incidents.
- Treat trend lists and social posts as leads, not proof. Record the original post/page URL, account or publisher, capture time, and corroborating sources.
- Do not claim official X API access or stable free trend access unless credentials and the provider's current terms have been verified.
- Keep discovery separate from editorial verification and publishing.

## 2. Egyptian political boundary
- Allow verified stories that are clearly beneficial to Egypt, including public achievements, science, culture, sport, community initiatives, and useful public information.
- Do not auto-publish criticism, attacks, accusations, controversy, or partisan disputes about the Egyptian president, presidency, government, ministries, ministers, or government officials.
- If a story's relationship to Egyptian government/politics is unclear, hold it for human review.
- Do not block a story merely because it contains a broad location word such as "محافظة".
- Global and non-Egyptian political trends are not automatically covered by this Egypt-specific restriction; apply ordinary verification and platform rules.

## 3. Crime, violence, and sensitive reporting
- A crime or incident may be reported when supported by reliable sources, using restrained, factual, non-graphic language.
- Never publish graphic injury/death imagery, explicit sexual-violence details, or content that glorifies violence.
- Protect victims' and witnesses' identities and private information; apply heightened protection to children.
- Distinguish allegations, arrests, charges, and court findings. Never present an allegation as a conviction.
- Unverified claims, rumors, doxxing, and calls for harassment are not eligible for automatic publication; hold or reject them.

## 4. Media rights and reuse
- A visible logo or watermark from a major broadcaster/publisher is a strong exclusion signal for automatic reuse, unless a documented license or permission authorizes it.
- Personal/local clips may be considered for editing, excerpts, commentary, captions, or voice-over only when the uploader/rights-holder has granted permission or a suitable reuse license is documented.
- Editing, cropping, adding commentary, or removing a watermark does not itself grant copyright permission. Never remove watermarks to disguise ownership.
- Prefer original artwork, licensed stock, public-domain material, or media explicitly cleared for the intended platforms and territory.
- Keep a media-rights record: source URL, creator/rights-holder if known, permission/license evidence, allowed uses, platform scope, and review status.
- If rights cannot be established, do not auto-publish that media; use an independently created visual or publish text-only if otherwise eligible.

## 5. Editorial originality and sourcing
- Write original headlines, summaries, scripts, captions, and hashtags. Do not copy articles, scripts, or substantial protected excerpts.
- Attribute factual information and link to the original reporting/source. Attribution does not replace permission to reuse protected media or text.
- Do not fabricate quotes, numbers, locations, motives, or eyewitness details.
- Prefer two independent reliable sources for serious allegations or high-impact breaking claims; otherwise hold for review.

## 6. Automated decision states
- GREEN: verified, non-sensitive, original copy, rights-cleared media; eligible for auto-publishing within configured limits.
- AMBER: unclear political classification, weak/one-source verification for serious claims, uncertain media rights, privacy concern, or sensitive context; hold for human review.
- RED: Egyptian government/presidency criticism or controversy, graphic/explicit material, doxxing, unverified defamatory claims, or media with no reuse basis; do not auto-publish.

## 7. Operational safeguards before activation
- Default publishing remains OFF until policy tests, dry-runs, platform permissions, and a manual review of sample decisions are complete.
- Provide an immediate kill switch, per-platform daily limits, duplicate prevention, and an auditable log of source, decision, reason, media rights, and resulting post URL/ID.
- Never store access tokens in source files or logs. Use repository secrets and least-privilege platform permissions.
- Keep workflow changes on the feature branch until reviewed. Do not merge or enable publishing as part of this requirements-only change.
