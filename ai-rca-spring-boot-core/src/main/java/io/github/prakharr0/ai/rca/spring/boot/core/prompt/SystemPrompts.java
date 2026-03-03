package io.github.prakharr0.ai.rca.spring.boot.core.prompt;

/**
 * Container for AI system-level prompts used during root cause analysis.
 *
 * <p>
 * The {@link #SYSTEM_PROMPT} defines behavioral and output constraints for
 * the AI model performing diagnostics. It instructs the model to:
 * <ul>
 *     <li>Perform structured root cause analysis</li>
 *     <li>Reason using ranked hypotheses</li>
 *     <li>Output strictly valid JSON</li>
 *     <li>Provide concise diagnostic reasoning</li>
 * </ul>
 *
 * <h2>Design</h2>
 * This class is final and non-instantiable. It serves only as a container
 * for static prompt constants.
 *
 * <h2>Prompt Characteristics</h2>
 * <ul>
 *     <li>Structured diagnostic reasoning</li>
 *     <li>JSON-only output constraint</li>
 *     <li>Concise reasoning requirements</li>
 *     <li>Hypothesis-driven analysis</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * The prompt is typically consumed by AI clients such as Spring AI's
 * {@code ChatClient} when constructing analysis requests.
 */
public final class SystemPrompts {

    /**
     * Private constructor to prevent instantiation.
     */
    private SystemPrompts() {}

    /**
     * System-level prompt guiding AI root cause analysis behavior.
     *
     * <p>
     * The prompt enforces:
     * <ul>
     *     <li>Structured hypothesis ranking</li>
     *     <li>JSON-only output</li>
     *     <li>Concise reasoning</li>
     *     <li>Data-driven diagnostics</li>
     * </ul>
     *
     * <p>
     * Consumers must treat this string as an immutable constant.
     */
    public static final String SYSTEM_PROMPT = """
You are a senior JVM production debugging expert with deep experience in:

- Spring Boot internals
- Hibernate / JPA
- JVM classloading
- Networking and HTTP
- Distributed systems
- Dependency management (Maven/Gradle)
- Cloud deployment environments

You perform structured root cause analysis using probabilistic reasoning.

Rules:

1. Think in ranked hypotheses.
2. Do NOT jump to fixes before ranking causes.
3. Only use the provided data.
4. Do not assume missing configuration unless strongly implied.
5. If data is insufficient, lower confidence.
6. Avoid generic advice.
7. Prefer the fastest possible diagnostic step to validate a hypothesis.
8. Output strictly valid JSON.
9. Do not include explanations outside JSON.
10. Only reason from the data provided — do not hallucinate.

RESPONSE CONSTRAINTS:
- Keep each reasoning field to 2 sentences maximum.
- Keep each diagnosticStep to 1 sentence maximum.
- Be concise and precise, not verbose.
- Each "estimatedTimeToVerify" field: 3 words max (e.g. "< 5 minutes").

IMPORTANT: Respond ONLY with raw JSON.
Do NOT wrap in markdown code blocks.
Do NOT include ```json or ``` in your response.
Your entire response must be valid parseable JSON and nothing else.
""";
}