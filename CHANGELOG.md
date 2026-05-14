# Changelog

## [0.0.6] - 2026-05-14

### Features

#### Automatic Exception Interception
- Intercepts all uncaught exceptions from Spring MVC handlers via `@ControllerAdvice` without altering HTTP response behavior — the original error is always rethrown unchanged.

#### AI-Powered Root Cause Analysis
- On every new exception, collects a structured `ContextSnapshot` (exception type, root cause, stack trace, recent logs, Spring/Java version, active profiles, deployment environment, web stack, database stack, build tool).
- Builds a structured prompt and calls a configured LLM via Spring AI `ChatClient` to produce ranked root cause hypotheses.
- AI response is parsed into a typed `AiRcaResponse` containing: overall confidence score, matched known pattern, ranked `RootCause` list (each with title, likelihood, category, reasoning, diagnostic step, estimated verification time).
- Analysis runs asynchronously — LLM latency does not block the HTTP request.

#### Fingerprint-Based Deduplication
- Generates a SHA-256 fingerprint per exception (based on exception type, root cause type, and stack trace).
- Identical exception patterns reuse the cached AI result instead of calling the LLM again, reducing cost and latency.

#### In-Memory Exception Timeline
- Maintains a bounded ring of `ExceptionOccurrence` events (default: 500, configurable via `ai.rca.history-size`).
- Each event records: UUID, timestamp, exception/root cause types, message, fingerprint, HTTP method, request path, thread name, and analysis status (`PENDING` → `COMPLETED` / `FAILED`).
- Secondary index by fingerprint allows bulk-attaching AI results to all occurrences of the same exception pattern.

#### Log Capture
- `RingBufferLogAppender` (Logback) captures the last 50 formatted log lines into an in-memory circular buffer.
- Buffer contents are included in every AI prompt as temporal context around the failure.

#### REST API
- `GET /ai/rca/events?from=&to=&limit=` — query exception timeline by time range.
- `GET /ai/rca/events/at?time=&toleranceSeconds=` — find the nearest exception event to a given timestamp (supports ISO-8601, epoch millis, and natural language formats such as "3 PM on 14 May 2026").

#### Actuator Endpoint
- `GET /actuator/rca` — exposes all cached AI analysis results keyed by exception fingerprint.

#### Conversational Chat Interface
- `POST /ai/rca/chat` — ask natural language questions about past exceptions and incidents. The service resolves time references in the question, retrieves relevant timeline events, and sends them as grounded context to the LLM.
- `GET /ai/rca/chat/ui` — lightweight single-page chat UI (dark theme, sidebar with example queries and API links, adjustable time tolerance setting).
- Chat responses strip markdown tables and normalize formatting for readability.

#### Auto-Configuration
- Zero-config Spring Boot starter: all beans are registered automatically when Spring AI `ChatClient` is on the classpath.
- Every bean is `@ConditionalOnMissingBean` — any component can be replaced by a user-defined bean.
- Feature flags via `application.yml`:
  - `ai.rca.enabled` (default: `true`) — master switch.
  - `ai.rca.chat-enabled` (default: `true`) — enables chat API and UI.
  - `ai.rca.chat-ui-enabled` (default: `true`) — enables the browser UI.
  - `ai.rca.history-size` (default: `500`) — max events retained in memory.
  - `ai.rca.default-time-tolerance-seconds` (default: `1800`) — tolerance window for time-based event lookup.
  - `ai.rca.chat-context-events` (default: `20`) — number of recent events provided as context for generic chat questions.