# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

notify4j is an **embeddable, domain-agnostic multi-channel notification library** for Java/Spring Boot. The jar is the product — there is no server. Apps declare channels as Apprise/shoutrrr-style URLs (`slack://…`, `pagerduty://…`) and get status-transition filtering, runtime mute, and reminders for free.

Status: the implementation is being extracted from `alexmond/builder` (`builder-notifications`); see that repo's `docs/design/notify4j-extraction-plan.md`.

## Build & test

Multi-module Maven build (parent inherits from `spring-boot-starter-parent`). Use the wrapper.

```shell
./mvnw verify                       # build + test + ALL quality gates (core + starter)
./mvnw verify -Pdefault             # also builds/tests notify4j-sample (this is what CI runs)
./mvnw -pl notify4j-core test       # test one module
./mvnw -pl notify4j-core test -Dtest=NotifierUrlParserTest          # single test class
./mvnw -pl notify4j-core test -Dtest=NotifierUrlParserTest#parsesSlack  # single test method
./mvnw spring-javaformat:apply      # auto-fix formatting (do this before committing)
```

### Quality gates run on every `verify` (all fail the build)
- **spring-javaformat** (`validate` phase) — strict format check. Run `spring-javaformat:apply` to fix; do not hand-format.
- **Checkstyle** — uses `checkstyle.xml` + `checkstyle-suppressions.xml` at repo root; includes test sources.
- **PMD** — uses `pmd-ruleset.xml` at repo root; `failOnViolation=true`; main sources only.
- **JaCoCo** — BUNDLE line coverage must be ≥ **80%** (`jacoco:check`). New code generally needs tests to keep the bundle above the floor.

The `release` profile (CI release pipeline only) adds GPG signing + source/javadoc jars + Maven Central publish; never run it locally.

## Architecture

The whole design keeps a **Spring-free core** generic over the application's event type `E`, with a thin Spring Boot starter on top. The core never references any app domain type — a single `NotificationAdapter<E>` bridges it.

### Modules
- **notify4j-core** — the engine. No Spring dependency; uses the JDK `HttpClient`.
- **notify4j-spring-boot-starter** — auto-configuration that binds `notify4j.*` properties; adds the email channel (the one channel that is *not* a URL, since it's Spring-mail-coupled).
- **notify4j-bom** — dependency BOM for version alignment.
- **notify4j-sample** — runnable example; only built under `-Pdefault`, never released.

### The key seams (read these together to understand the flow)
- **`Notifier<E>`** — the SPI: `void notify(E)`. Implementations must **never throw**; a failing channel must not break the caller.
- **`NotificationAdapter<E>`** — the app supplies one. Maps `E` → `id` (stable identity for transition tracking), `status`, and `message`. This is the *only* place the app's domain type is referenced, which is what lets channels stay event-agnostic.
- **`Notifications<E>`** — the application-facing facade. Holds a fan-out list of channels. `send(event)` / `send(event, routeTags)` delivers to every channel whose tags overlap the route tags (untagged channels always fire). Two gates sit in front: a `FilteringNotifier` (runtime mute) and per-channel tags; each channel additionally applies its own `TransitionFilter`. Swallows per-channel `RuntimeException`s.
- **`NotificationsFactory<E>`** — builds `Notifications` facades from a URL list with shared defaults. Single-tenant apps get one global facade; **multi-tenant apps inject the factory and build one facade per tenant** (and set `notify4j.global=false` to suppress the global one).
- **`NotifierUrlParser<E>`** — turns a URL into a `Channel<E>` (notifier + routing tags). Scheme selects the channel/payload shape; an optional `+http`/`+https` suffix selects transport (default `https`; `http` is mainly for tests); `?tags=a,b` sets routing tags and is stripped before building the endpoint. Adding a channel = adding a `case` in the `switch` here plus the notifier class.

### Notifier class hierarchy
- **`AbstractEventNotifier<E>`** — base: `enabled` flag, `shouldNotify` guard, and error isolation (`notify` is `final`, catches and logs). Subclasses implement `doNotify`.
- **`AbstractHttpNotifier<E>`** — base for webhook-style channels. Configured with *functions* (no subclass-per-event); shares one JDK `HttpClient` + timeouts + retry via `HttpClientConfig`, applies a `TransitionFilter`, and POSTs a body whose `contentType()` is JSON by default (override + `formEncode()` for form-encoded channels). Subclasses implement `payload(event)` and optionally `headers()` (auth) / `contentType()`. Transient failures (5xx/429/`IOException`) are retried with backoff. Concrete: `Slack`, `Teams`, `Discord`, `Mattermost`, `RocketChat`, `GoogleChat`, `Webhook`, `Telegram`, `Ntfy`, `Gotify`, `Pushover`, `Twilio`, `Signal`, `WhatsApp`, `Zulip`, `Pushbullet`, `PagerDuty`, `OpsGenie`.
- **`AbstractTransitionNotifier<E>`** — alternative base for non-HTTP notifiers that subclass instead of taking functions; delegates gating to a `TransitionFilter` via `entityId`/`status`.
- **Wrappers (decorators):** `CompositeNotifier` (fan-out), `FilteringNotifier` (mute), `RemindingNotifier` (re-notify for entities stuck in a state — `checkReminders(Instant)` is the testable core, `start()`/`stop()` schedule it on a daemon thread), `AsyncNotifier` (deliver off the caller thread on an `Executor`; the facade wraps every channel when one is configured), `LoggingNotifier` (always-on default sink).
- **Metrics:** delivery outcomes (sent/failed/suppressed, per channel) record against the dependency-free `NotificationMetrics` SPI inside `AbstractEventNotifier`; the starter wires a Micrometer impl when a `MeterRegistry` is present.

### Transition semantics (`TransitionFilter`)
Stateful gate keyed by entity `id`: tracks last status, suppresses non-transitions and any change matching an `ignoreChanges` pattern (`OLD:NEW` with `*` wildcards, e.g. `*:RUNNING`). The starter's default `ignore-changes` is `*:PENDING`, `*:RUNNING`, `*:ASSIGNED` so channels fire on terminal SUCCESS/FAILED, not intermediate states.

### Spring starter wiring (`NotificationsAutoConfiguration`)
Everything is conditional on the app providing a `NotificationAdapter` bean. The factory + global facade are created from `notify4j.*` props; **any other `Notifier` beans in the context are auto-folded in as extra fan-out targets**. Email registers as an extra `Notifier` only when a `JavaMailSender` exists and `notify4j.email.to` is set. Raw generic types in this class are deliberate (the event type is known only to the adapter bean). Properties prefix is `notify4j` (bound by `NotificationProperties`).
