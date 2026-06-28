# Brainstorm-panel log — notify4j

## 2026-06-28 — 1.0.0 release scope (stress-test a plan + gap-finding)
- Proposed: API Steward, Reliability/SRE, Security, Consumer DX, Competitive/Product, Release Manager (skeptic). User added: none. User removed: none.
- Style: swarm → director-led (Release Manager converges; API Steward veto on freeze). Fit well for a scope/cut-line decision.
- Recurring (worth seating again on this repo): **API Steward** and **Release Manager** are essential for any release/API-freeze target. **Security** + **Reliability** each surfaced a host-impacting issue the prior 4-lens review missed (SSRF; unbounded async queue → OOM) — keep both for anything touching delivery or URLs.
- Key cross-lens findings the earlier review missed: (1) async pool has an **unbounded queue → can OOM the host**; (2) **SSRF** via user-supplied channel hosts (multi-tenant); (3) **no title/severity** in the SPI (`NotificationAdapter` is id/status/message) — channels flatten everything, PagerDuty hardcodes `severity:"error"`; (4) **secret leak in URL-*parse* exceptions** (regression of 0.7.0 log redaction, on the startup path); (5) **no IDE config metadata** (`additional-spring-configuration-metadata.json`); (6) **no SECURITY.md**; (7) `NotificationMetrics`/SPI need `default` methods to stay evolvable past the freeze.
- Adjudication rule that worked: "blocks 1.0" = *irreversible after the freeze* (API Steward) **or** *credibility/safety failure in a published 1.0* (Release Manager). Anything addable compatibly later (new property, `default` SPI method, new channel) is 1.0.x/1.1.
- Note: a 1.0 here is overwhelmingly an **API-surface promise**, not a feature ceiling — the only strictly-irreversible work is the public/internal boundary (#37) and the URL grammar (#38).
