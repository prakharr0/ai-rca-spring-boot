# AI RCA Spring Boot Starter

AI-powered Root Cause Analysis (RCA) for Spring Boot applications. Automatically intercepts exceptions, calls an LLM, and returns structured ranked hypotheses — with no code changes required beyond adding the dependency.

---

## Features

- Automatic exception interception via `@ControllerAdvice`
- Structured AI-based root cause analysis (ranked hypotheses, likelihood, diagnostic steps)
- In-memory exception timeline with time-based querying
- Conversational chat interface for incident Q&A
- Built-in lightweight chat UI
- Actuator endpoint for cached analysis results
- Fingerprint-based deduplication (same exception pattern → one AI call)
- Confidence threshold quality gate (`ai.rca.min-confidence`)
- Spring Boot auto-configuration — zero Java config required

---

## Modules

```
ai-rca-spring-boot/
 ├── ai-rca-spring-boot-core      # Context collection, prompt building, AI analyzer, models
 ├── ai-rca-spring-boot-starter   # Auto-configuration, exception handler, web endpoints
 └── ai-rca-spring-boot-tests     # Eval test suite and integration test app
```

### `ai-rca-spring-boot-core`
Context collection, prompt engineering, AI analyzer, RCA response models.

### `ai-rca-spring-boot-starter`
Spring Boot auto-configuration, global exception handler, REST endpoints, actuator integration.

### `ai-rca-spring-boot-tests`
A standalone Spring Boot app used for testing the library end-to-end. Contains:

- **`TemperatureEvalTest`** — runs the same exception 10 times at temperatures 0.1, 0.3, 0.7, and 1.0. Measures confidence variance, rank-1 title uniqueness, rank-3 likelihood stability, and known pattern distribution. Use this before changing temperature settings.
- **`RcaStructuralEvalTest`** — runs all 13 fixture scenarios through the model once each and asserts the JSON schema is valid: required fields present, enums within allowed values, ranks sequential.
- **`RcaConsistencyEvalTest`** — runs each fixture 5 times at production temperature and asserts rank-1 category never flips and confidence std dev stays below 0.15.
- **`RcaConfidenceThresholdTest`** — unit test (no API calls) verifying the `LOG_WARN` and `SKIP` confidence gate behaviors.

All eval tests are `@Disabled` by default — they require a live API key and are run manually. Run them from your IDE or with:

```bash
# From ai-rca-spring-boot-tests/
OPENAI_KEY=sk-... mvn test -Dtest=RcaStructuralEvalTest
```

The 13 fixture JSON files in `src/test/resources/fixtures/` cover all 5 root cause categories and serve as the ground truth corpus for eval runs.

---

## Requirements

| Dependency  | Version |
|-------------|---------|
| Java        | 21+     |
| Spring Boot | 3+      |
| Spring AI   | 1.0+    |

---

## Installation

### Maven

```xml
<dependency>
  <groupId>io.github.prakharr0</groupId>
  <artifactId>ai-rca-spring-boot-starter</artifactId>
  <version>0.0.7</version>
</dependency>
```

Add **one** of the following Spring AI model starters:

```xml
<!-- Anthropic Claude (recommended) -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>

<!-- OR OpenAI -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

Include the Spring AI BOM to manage versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Gradle

```groovy
implementation 'io.github.prakharr0:ai-rca-spring-boot-starter:0.0.7'
implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
```

---

## Configuration

### Step 1 — Set your API key

Set the key as an environment variable. Never hardcode it.

**Anthropic:**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

```yaml
# application.yml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-6      # claude-sonnet-4-6 or newer recommended
          max-tokens: 8192               # minimum 4096; RCA responses avg 500-800 tokens
```

**OpenAI:**

```bash
export OPENAI_API_KEY=sk-...
```

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
```

> **Temperature note:** The library defaults to `temperature=0.1` for deterministic JSON output. At `temperature > 0.5`, structured output schema violations occur in ~10–15% of calls. You can override this with `ai.rca.temperature`.

---

### Step 2 — Enable RCA (optional — enabled by default)

```yaml
ai:
  rca:
    enabled: true                          # master switch (default: true)
    history-size: 500                      # max exception events in memory (default: 500)
    chat-enabled: true                     # enables POST /ai/rca/chat (default: true)
    chat-ui-enabled: true                  # enables GET /ai/rca/chat/ui (default: true)
    default-time-tolerance-seconds: 1800   # tolerance for /events/at queries (default: 1800)
    chat-context-events: 20                # recent events fed to chat LLM (default: 20)

    # Model tuning (optional — library applies safe defaults when not set)
    temperature: 0.1                       # overrides provider temp for RCA calls only
    max-tokens: 8192                       # overrides provider max-tokens for RCA calls only

    # Quality gate (optional)
    min-confidence: 0.0                    # below this score, low-confidence action fires (0.0 = disabled)
    low-confidence-action: LOG_WARN        # LOG_WARN (tag result) | SKIP (discard result)
```

---

### Step 3 — Expose the Actuator endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, rca
```

---

### Step 4 — Configure log capture (optional but recommended)

Add `RingBufferLogAppender` to Logback so recent log lines are included in AI prompts:

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
  <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

  <appender name="RING_BUFFER"
            class="io.github.prakharr0.ai.rca.spring.boot.core.context.RingBufferLogAppender"/>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="RING_BUFFER"/>
  </root>
</configuration>
```

Without this, the AI prompt will have no log context. The buffer retains the last 50 log lines.

---

## Endpoints

All endpoints are registered automatically by the auto-configuration.

### `GET /actuator/rca`

Returns all cached AI analysis results, keyed by exception fingerprint (SHA-256 of exception type + root cause + stack trace). Requires actuator exposure (Step 3 above).

```
GET http://localhost:8080/actuator/rca
```

---

### `GET /ai/rca/events`

Returns the exception event timeline, most recent first.

| Parameter | Type   | Default | Description                            |
|-----------|--------|---------|----------------------------------------|
| `from`    | String | —       | Start time (ISO-8601, epoch ms, or natural language) |
| `to`      | String | —       | End time (same formats)               |
| `limit`   | int    | `50`    | Maximum events to return              |

```
GET /ai/rca/events
GET /ai/rca/events?limit=20
GET /ai/rca/events?from=2026-05-14T10:00:00Z&to=2026-05-14T18:00:00Z
GET /ai/rca/events?from=14 May 2026&limit=10
```

**Supported time formats:**
- ISO-8601: `2026-05-14T14:30:00Z`
- ISO local: `2026-05-14T14:30:00`
- ISO date: `2026-05-14`
- Natural language: `3 PM on 14 May 2026`, `3:30 pm on 14 may 2026`
- Epoch seconds (10 digits) or epoch millis (13 digits)

---

### `GET /ai/rca/events/at`

Finds the exception event nearest to a given timestamp.

| Parameter          | Type   | Default | Description                               |
|--------------------|--------|---------|-------------------------------------------|
| `time`             | String | —       | Target time (required, any supported format) |
| `toleranceSeconds` | int    | `1800`  | Search window around the target time. Minimum 60. |

```
GET /ai/rca/events/at?time=2026-05-14T15:00:00Z
GET /ai/rca/events/at?time=3 PM on 14 May 2026&toleranceSeconds=300
```

Returns `{ requestedTime, toleranceSeconds, event }`. `event` is `null` if nothing falls within the tolerance window.

---

### `POST /ai/rca/chat`

Ask a natural language question about past exceptions and their AI analysis. The service resolves time references in the question, retrieves matching events, and sends them as grounded context to the LLM.

```http
POST /ai/rca/chat
Content-Type: application/json

{
  "question": "Why did the exception at 3pm today happen?",
  "toleranceSeconds": 1800
}
```

**Response:**
```json
{
  "answer": "...",
  "referencedEventIds": ["uuid-1", "uuid-2"],
  "resolvedTime": "2026-05-14T15:00:00Z",
  "inputTokens": 1420,
  "outputTokens": 312
}
```

`toleranceSeconds` is optional (defaults to `ai.rca.default-time-tolerance-seconds`).

---

### `GET /ai/rca/chat/ui`

Opens a lightweight browser chat UI. Dark theme, sidebar with example queries, adjustable time tolerance.

```
GET http://localhost:8080/ai/rca/chat/ui
```

Disable with `ai.rca.chat-ui-enabled: false`.

---

## AI Response Schema

Every analysis result has this structure:

```json
{
  "analysisConfidence": 0.92,
  "knownPattern": "Arithmetic error in business logic",
  "exceptionMessage": "/ by zero",
  "missingInformation": [],
  "lowConfidence": false,
  "rootCauses": [
    {
      "rank": 1,
      "title": "Unguarded division by zero in pricing calculation",
      "likelihood": "High",
      "category": "Code",
      "reasoning": "Stack trace points to PricingService.java:58 inside applyBulkDiscount(). The divisor variable reaches zero when totalQuantity is 0, with no guard preceding the operation.",
      "diagnosticStep": "Add a log statement at PricingService.java:55 to print totalQuantity before the division, then reproduce the issue with an empty cart.",
      "estimatedTimeToVerify": "< 5 minutes",
      "proposedFix": "Add a guard before the division: if (totalQuantity == 0) return 0; or throw an IllegalArgumentException. Validate totalQuantity is positive at the start of applyBulkDiscount."
    },
    {
      "rank": 2,
      "title": "Zero-quantity cart not validated before reaching service layer",
      "likelihood": "Medium",
      "category": "Code",
      "reasoning": "The controller does not validate that cart quantity is non-zero before calling the pricing service, allowing invalid state to propagate downstream.",
      "diagnosticStep": "Check CartController for pre-service input validation on quantity fields.",
      "estimatedTimeToVerify": "< 10 minutes",
      "proposedFix": "Add a @Min(1) constraint on the quantity field in the cart request DTO and enable @Valid on the controller method parameter to reject zero-quantity carts at the HTTP boundary."
    }
  ],
  "metadata": {
    "inputTokens": 1240,
    "outputTokens": 487,
    "totalTokens": 1727
  }
}
```

**`category` values:** `Code` · `Configuration` · `Infrastructure` · `Dependency` · `Environment`

**`likelihood` values:** `High` · `Medium` · `Low`

**`lowConfidence: true`** is set by the library (not the AI) when `analysisConfidence` falls below `ai.rca.min-confidence`. Use it to filter results programmatically.

---

## How Output Quality Is Measured

The library ships with an eval suite in the `ai-rca-spring-boot-tests` module. All eval tests are `@Disabled` — they require a live API key and are run manually, not in CI.

### Three levels of evaluation

**Level 1 — Structural (`RcaStructuralEvalTest`):**
Runs each of the 13 fixture scenarios once and asserts the response is schema-valid: JSON parses, all required fields present, `likelihood` and `category` values within the allowed set, `rank` values sequential from 1, `analysisConfidence` between 0.0 and 1.0. Run this before and after any prompt change to confirm the JSON contract is intact.

**Level 2 — Consistency (`RcaConsistencyEvalTest`):**
Runs each fixture 5 times at production temperature (0.1) and asserts:
- Rank-1 category is unanimous across all runs (no category flip)
- Rank-1 title varies by at most 2 distinct values (rephrasing allowed, concept flip is not)
- Confidence standard deviation stays below 0.15

Run this before and after prompt changes to confirm hypothesis stability.

**Level 3 — Temperature (`TemperatureEvalTest`):**
Runs a single serialised exception 10 times each at temperatures 0.1, 0.3, 0.7, and 1.0. Measures confidence variance, rank-1 title uniqueness, rank-3 likelihood violations, and known pattern distribution. Use this to calibrate the `ai.rca.temperature` setting.

### Running evals

```bash
# From ai-rca-spring-boot-tests/ — requires OPENAI_KEY or ANTHROPIC_KEY set
mvn test -Dtest=RcaStructuralEvalTest
mvn test -Dtest=RcaConsistencyEvalTest
mvn test -Dtest=TemperatureEvalTest
```

Or run any test class from your IDE (right-click → Run).

### Confidence threshold quality gate

Set `ai.rca.min-confidence` to reject or tag results below a quality bar:

```yaml
ai:
  rca:
    min-confidence: 0.7             # flag results below 70% confidence
    low-confidence-action: LOG_WARN # LOG_WARN (tag with lowConfidence=true) | SKIP (discard)
```

When confidence falls below the threshold, `AiRcaResponse.isLowConfidence()` returns `true`. Use this to filter results downstream without parsing the raw score.

### Baseline eval results

The first baseline run (2026-06-19, GPT-4o-mini, temperature 0.1) is documented in [`docs/eval-results/2026-06-19-v1.0.0.md`](docs/eval-results/2026-06-19-v1.0.0.md). Key findings:

- Structural: 13/13 fixtures passed
- Consistency: 11/13 passed; 2 known issues documented (minor wording variance and one category-ambiguous fixture)
- Temperature: output is fully stable at 0.1; degrades sharply above 0.7

---

## Design Principles

- **Ranked hypotheses first, fixes second** — each root cause includes a `proposedFix` ordered by confidence (rank 1 = most likely to fix the issue). Fixes are specific to the stack trace, never generic advice.
- **Analysis never blocks requests** — runs asynchronously after the exception is rethrown
- **Fingerprint deduplication** — identical exception patterns reuse the cached AI result
- **Graceful degradation** — AI failures are logged; the original exception behavior is always preserved
- **Every bean is `@ConditionalOnMissingBean`** — any component can be replaced with your own

---

## Build From Source

```bash
mvn clean install -DskipTests
```

---

## Contributing

Pull requests are welcome. Open an issue first to discuss major changes.

---

## License

MIT License — Copyright (c) 2026 Prakhar Rathi