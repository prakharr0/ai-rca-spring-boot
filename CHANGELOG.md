# Changelog

## [0.1.0] - 2026-06-19

### Features

#### Confidence Threshold Quality Gate
- New config property `ai.rca.min-confidence` (default: `0.0`, disabled). When set, analysis results whose `analysisConfidence` falls below the threshold trigger `ai.rca.low-confidence-action`.
- New config property `ai.rca.low-confidence-action`: `LOG_WARN` (default — store result and tag it) or `SKIP` (discard result, mark event as failed).
- `AiRcaResponse` now includes a `lowConfidence` boolean field set by the library after the threshold check. Use it to filter low-quality results programmatically without reading the raw score.
- `isLowConfidence()` helper on `AiRcaResponse` provides a null-safe accessor (null → false, for results not yet threshold-checked).

#### Improved Chat System Prompt
- `RcaChatService` system prompt rewritten to explicitly instruct the model on the `ExceptionOccurrence` JSON structure: how to read `rootCauses[]`, `analysisConfidence`, `knownPattern`, `missingInformation`, and `analysisStatus`.
- Chat responses now lead with rank-1 root cause and confidence percentage, surface `diagnosticStep` as the recommended next action, and correctly handle `PENDING`/`FAILED` analysis states.

#### Token Usage in Chat Responses
- `POST /ai/rca/chat` response now includes `inputTokens` and `outputTokens` fields.
- Chat UI displays a running session tresoken total in the topbar (appears after the first response, resets on "Clear chat").

#### LLM Eval Suite
- New module `ai-rca-spring-boot-tests` contains three `@Disabled` eval test classes (run manually with a live API key):
  - `RcaStructuralEvalTest` — schema validation across 13 exception fixture scenarios (JSON must parse, all fields valid, ranks sequential).
  - `RcaConsistencyEvalTest` — rank-1 stability across 5 runs per fixture at production temperature; category-flip assertion.
  - `RcaConfidenceThresholdTest` — unit test (no AI calls) verifying `LOG_WARN` tags results and `SKIP` discards them.
- 13 fixture JSON files in `src/test/resources/fixtures/` covering all 5 root cause categories: Code, Configuration, Infrastructure, Dependency, Environment.

#### Unit Test Coverage
- 99 unit tests across core and starter modules (zero AI calls, all run in CI without API keys).
- Classes covered: `ExceptionFingerprint`, `LogBuffer`, `ContextCollector`, `ExceptionOccurrence`, `ExceptionTimelineStore`, `AiRcaResponse`, `UserPromptBuilder`, `RcaTimeParser`, `AiRcaProperties`, `GlobalExceptionHandler`, `AiRcaEventsController`.

#### Proposed Fixes in RCA Output
- `RootCause` now includes a `proposedFix` field: a specific, stack-trace-grounded corrective action for each hypothesis, ordered by rank (rank 1 = highest confidence fix).
- Distinct from `diagnosticStep` (which tells you how to *investigate*) — `proposedFix` tells you what to *change*.
- System prompt updated with `proposedFix` examples and a 2-sentence constraint. Output schema updated accordingly.

### Bug Fixes
- `LogBuffer.append(null)` previously threw `NullPointerException` (ArrayDeque does not accept null). Now silently ignores null lines, matching the documented contract.

### Breaking Changes
- `AiRcaResponse` record gains a `lowConfidence` field (`Boolean`, nullable). Existing Jackson deserialization is unaffected — absent field defaults to `null` which `isLowConfidence()` treats as `false`.
- `DefaultAiRcaAnalyzer` constructor gains two new parameters (`double minConfidence`, `LowConfidenceAction lowConfidenceAction`). Users overriding the bean via `@ConditionalOnMissingBean` must update their constructor call.

---

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