package io.github.prakharr0.ai.rca.spring.boot.core.prompt;

import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;

/**
 * Builds structured AI prompts from {@link ContextSnapshot} data.
 *
 * <p>
 * This class transforms runtime exception context into a prompt suitable
 * for AI-based root cause analysis. The generated prompt includes:
 * <ul>
 *     <li>Structured exception metadata</li>
 *     <li>Stack trace excerpts</li>
 *     <li>Recent log context</li>
 *     <li>Application environment details</li>
 *     <li>Output schema instructions</li>
 * </ul>
 *
 * <h2>Design</h2>
 * <ul>
 *     <li>Static utility class (non-instantiable)</li>
 *     <li>String-based prompt generation</li>
 *     <li>Output schema enforcement instructions</li>
 * </ul>
 *
 * <h2>Output Contract</h2>
 * The AI model is instructed to return responses matching a strict JSON schema
 * defined in {@link #OUTPUT_SCHEMA}. This enables deterministic parsing and
 * structured diagnostic analysis.
 *
 * @see ContextSnapshot
 */
public class UserPromptBuilder {

    /**
     * JSON output schema definition for AI responses.
     *
     * <p>
     * The schema enforces:
     * <ul>
     *     <li>Structured root cause analysis output</li>
     *     <li>Confidence scoring</li>
     *     <li>Ranked root causes</li>
     *     <li>Diagnostic steps</li>
     * </ul>
     *
     * <p>
     * This string is included in prompts to instruct the AI model
     * on the expected response format.
     */
    private static final String OUTPUT_SCHEMA = """
            Respond with this exact JSON structure:
            {
              "analysisConfidence": <0.0-1.0>,
              "exceptionMessage": <String of the exception message being analyzed>"
              "missingInformation": ["<Only include if analysisConfidence < 0.6.
                                 List specific runtime artifacts that were absent and would
                                 materially improve diagnosis accuracy, e.g.
                                 'Full stack trace beyond trimmed frames',
                                 'spring.datasource.url property value',
                                 'Heap dump at time of failure',
                                 'Active Spring beans list'.
                                 Do NOT list generic items like 'more logs' or 'more context'.
                                 Empty array if confidence >= 0.6 or all necessary context was available.>"],
              "knownPattern": "<A well-known Spring Boot or Spring ecosystem misconfiguration pattern name,
                                   e.g. 'Circular bean dependency', 'Missing DataSource configuration', 'Missing bean definition'.
                                   If the failure is generic application logic unrelated to Spring, return a short label describing
                                   the general failure class instead, e.g. 'Arithmetic error in business logic',
                                   'Null pointer in service layer', 'Array index out of bounds'.
                                   Never return null. Always return a string.>"
              "rootCauses": [
                {
                  "rank": <int>,
                  "title": "<string>",
                  "likelihood": "<High|Medium|Low>",
                  "category": "<Configuration|Code|Infrastructure|Dependency|Environment>",
                  "reasoning": "<max 2 sentences>",
                  "diagnosticStep": "<max 1 sentence>",
                  "estimatedTimeToVerify": "<e.g. < 5 minutes>"
                }
              ]
            }
            """;

    /**
     * Builds a structured AI prompt from the given {@link ContextSnapshot}.
     *
     * <p>
     * The generated prompt contains:
     * <ul>
     *     <li>Exception summary</li>
     *     <li>Stack trace (truncated)</li>
     *     <li>Recent log context (truncated)</li>
     *     <li>Application metadata</li>
     *     <li>Analysis instructions</li>
     *     <li>Output schema definition</li>
     * </ul>
     *
     * <p>
     * The resulting string is intended for use with AI clients such as
     * Spring AI's {@code ChatClient}.
     *
     * @param c the context snapshot containing diagnostic data; must not be {@code null}
     * @return a fully constructed AI prompt string
     */
    public static String build(ContextSnapshot c) {

        return """
You are given structured production failure data.

===============================
EXCEPTION SUMMARY
===============================

Exception Type: 
%s

Root Cause Type: 
%s

Root Cause Message: 
%s

===============================
STACK TRACE (trimmed to relevant frames)
===============================
%s

===============================
RECENT LOG CONTEXT (last 50 lines before failure)
===============================
%s
      
===============================
APPLICATION CONTEXT
===============================  
Spring Boot Version: %s
Java Version: %s
Active Profiles: %s
Packaging: %s
Deployment Environment: %s
Database: %s
Web Stack: %s
Build Tool: %s


===============================
TASK
===============================

Perform structured root cause analysis.

PHASE 1:
List the TOP 3 most probable root causes ranked by likelihood.

For each root cause:
    - Short title
- Likelihood: High | Medium | Low
- Category: Configuration | Code | Infrastructure | Dependency | Environment
- Technical reasoning (specific to the stack trace)
- Fastest diagnostic step to confirm or eliminate
- Estimated verification time (rough estimate)

PHASE 2:
Before finalizing:
    - Remove any hypothesis that contradicts the stack trace.
    - Ensure ranking reflects realistic production probability.

PHASE 3:
Provide:
    - Overall confidence score between 0.0 and 1.0
    - If confidence < 0.6, list missing information that would increase certainty.
    
Identify if the failure matches a known Spring Boot misconfiguration pattern.\s
If yes, label it explicitly (e.g. "Missing DataSource configuration").
If no, classify it as a general failure pattern (e.g. "Arithmetic error in business logic").
Never leave this field null.

IMPORTANT:
Do NOT suggest fixes.
Do NOT give code changes.
Focus only on ranked root causes and diagnostics.

Output strictly valid JSON using the schema below.
%s
""".formatted(
                c.exceptionType(),
                c.rootCauseType(),
                c.rootCauseMessage(),
                truncate(c.stackTrace(), 1000),
                truncate(c.recentLogs(), 500),
                c.springVersion(),
                c.javaVersion(),
                c.activeProfiles(),
                c.packaging(),
                c.deploymentEnvironment(),
                c.database(),
                c.webStack(),
                c.buildTool(),
                OUTPUT_SCHEMA
        );
    }

    /**
     * Truncates a string to a maximum number of characters.
     *
     * <p>
     * If the input is blank or {@code null}, the string {@code "N/A"} is returned.
     * If truncation occurs, an indicator is appended.
     *
     * @param value the input string
     * @param maxChars maximum allowed characters
     * @return truncated string or original value if within limit
     */
    private static String truncate(String value, int maxChars) {
        if (value == null || value.isBlank()) return "N/A";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "\n... [truncated]";
    }
}










