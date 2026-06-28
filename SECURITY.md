# Security Policy

## Reporting a vulnerability

Please report security issues **privately** — do not open a public issue for a
suspected vulnerability.

- Use GitHub's private vulnerability reporting:
  <https://github.com/alexmond/notify4j/security/advisories/new>

We aim to acknowledge a report within a few days and will keep you updated as we
investigate. Once a fix is available we will publish an advisory and credit the
reporter (unless you prefer to remain anonymous).

## Supported versions

notify4j is pre-1.0. Security fixes land on the latest released line; until 1.0
that means the most recent `0.x` (and, after release, the `1.0.x` line).

## Security model

notify4j is an embeddable library that sends notifications to remote endpoints
over HTTP. A few properties are important to understand when deploying it.

### Channel URLs are trusted operator configuration

Channel URLs (`slack://…`, `pagerduty://…`, etc.) carry secrets — bot tokens,
routing keys, auth tokens, webhook paths — and they tell the library which hosts
to call. They are treated as **trusted configuration supplied by the operator**,
the same trust level as a database URL or an API key in `application.yml`.

- **Source them securely.** Prefer environment variables or a secret manager
  (Spring Cloud Config / Vault) over committing URLs to source control, and never
  log the raw `notify4j.urls` at application level.
- **Do not feed untrusted input directly into channel URLs.** If a multi-tenant
  application builds channel URLs from tenant-supplied data, that host then
  becomes attacker-influenced and the library can be steered to make outbound
  requests to internal addresses (SSRF). In that scenario you must validate the
  host yourself (e.g. an allowlist / internal-IP deny) before handing the URL to
  notify4j. A built-in host-allowlist option is planned for a later release; until
  then, the trust boundary is the caller's.

### What the library does to protect secrets

- **Secrets are redacted from logs.** Channel URLs are never logged verbatim.
  Delivery-failure messages use a redacted `scheme://host/…` form, and URL-parse
  errors (which happen at startup) keep only the scheme (`scheme://…`).
- **Redirects are not followed.** The shared HTTP client is pinned to
  `Redirect.NEVER`, so a credentialed request cannot be bounced by a `3xx`
  response to an attacker-chosen host.
- **TLS by default.** Channels default to `https`. The `+http` transport suffix
  exists mainly for tests and self-hosted endpoints; using it for a
  credential-bearing channel puts that secret on the wire in cleartext, and the
  library logs a warning when you do. Do not use `+http` for credential-bearing
  channels in production.
