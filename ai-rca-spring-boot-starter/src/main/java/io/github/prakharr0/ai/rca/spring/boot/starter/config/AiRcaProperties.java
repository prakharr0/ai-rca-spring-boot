package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.LowConfidenceAction;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AI Root Cause Analysis (RCA) starter.
 *
 * <p>
 * Properties are bound from the {@code ai.rca} namespace in application
 * configuration files.
 *
 * <h2>Configuration Example</h2>
 * <pre>
 * ai:
 *   rca:
 *     enabled: true
 * </pre>
 *
 * <h2>Properties</h2>
 * <ul>
 *     <li>{@code enabled} – toggles AI RCA functionality (default: true)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * This class is registered via {@code @EnableConfigurationProperties}
 * and automatically bound by Spring Boot.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "ai.rca")
public class AiRcaProperties {

    /**
     * Enables or disables AI root cause analysis.
     *
     * <p>
     * When disabled, diagnostic analysis and related handlers should
     * not be invoked.
     */
    private boolean enabled = true;

    /**
     * Maximum in-memory exception events retained in the timeline.
     */
    private int historySize = 500;

    /**
     * Enables chat endpoint API.
     */
    private boolean chatEnabled = true;

    /**
     * Enables lightweight chat UI page.
     */
    private boolean chatUiEnabled = true;

    /**
     * Default tolerance in seconds when resolving natural language event times.
     */
    private int defaultTimeToleranceSeconds = 1800;

    /**
     * Number of most recent events to provide as context in generic chat questions.
     */
    private int chatContextEvents = 20;

    /**
     * Optional temperature override for RCA analysis calls (0.0–1.0).
     *
     * <p><b>When NOT set (default):</b> temperature is controlled entirely by your Spring AI provider
     * configuration (e.g. {@code spring.ai.openai.chat.options.temperature}). This is the recommended
     * approach — configure temperature once via Spring AI and all ChatClient calls, including RCA,
     * will use it.
     *
     * <p><b>When set:</b> this value is passed per-call via {@code .options()} and takes precedence
     * over the provider-level setting for RCA analysis calls only. Use this if you want RCA calls
     * to use a different temperature than the rest of your application.
     *
     * <p>For deterministic JSON output, set this (or your provider's equivalent) to 0.1.
     * At default provider temperatures (0.7–1.0), structured output schema violations occur
     * in approximately 10–15% of calls.
     */
    private Double temperature = null;

    /**
     * Minimum acceptable analysis confidence score (0.0–1.0).
     *
     * <p>When set above 0.0, any AI result whose {@code analysisConfidence} falls below
     * this threshold triggers {@link #lowConfidenceAction}. Set to {@code 0.0} (default)
     * to disable the threshold entirely and accept all results regardless of confidence.
     *
     * <p>Example: setting {@code 0.7} means results below 70% confidence are flagged or
     * skipped, depending on {@link #lowConfidenceAction}.
     */
    private double minConfidence = 0.0;

    /**
     * Action to take when analysis confidence falls below {@link #minConfidence}.
     *
     * <ul>
     *   <li>{@code LOG_WARN} (default) — store the result but tag it with
     *       {@code lowConfidence: true} so consumers can filter it.</li>
     *   <li>{@code SKIP} — discard the result and mark the event as failed.
     *       Use when downstream consumers must not receive low-quality analyses.</li>
     * </ul>
     */
    private LowConfidenceAction lowConfidenceAction = LowConfidenceAction.LOG_WARN;

    /**
     * Optional max-tokens override for RCA analysis calls.
     *
     * <p>When not set, the provider default applies. A full RCA response with 3 root causes
     * is typically 400–600 tokens. Setting this too low truncates the JSON mid-response,
     * causing parse failures.
     *
     * <p>When set, this value is passed per-call and takes precedence over the provider setting.
     * When not set, the library applies a default of 4096 — 5× the typical RCA response size,
     * chosen as a safety ceiling against mid-JSON truncation on verbose outputs.
     */
    private Integer maxTokens = null;

    /** RAG (Retrieval-Augmented Generation) settings for context injection into RCA prompts. */
    private RagProperties rag = new RagProperties();

    @Getter
    @Setter
    public static class RagProperties {

        /**
         * Enables RAG context injection via embeddings.
         *
         * <p>When enabled, each analyzed exception is embedded and stored. New exceptions
         * are compared against stored embeddings using cosine similarity, and the top-K most
         * similar past incidents are injected into the RCA prompt as additional context.
         *
         * <p>Also activates runbook chunk retrieval when {@code @RcaRunbook} is present.
         *
         * <p>Requires an {@code EmbeddingModel} bean (e.g. via
         * {@code spring-ai-starter-model-openai}). Has no effect if no embedding model is available.
         */
        private boolean enabled = false;

        /**
         * Maximum number of similar past incidents to retrieve and inject into the RCA prompt.
         * Standard RAG "top-K" parameter.
         */
        private int topK = 3;

        /**
         * Minimum cosine similarity score (0.0–1.0) a past incident must reach to be included.
         * Incidents below this threshold are excluded even if they are the closest matches.
         * Set to 0.0 to include any past incident regardless of similarity.
         */
        private double minSimilarityScore = 0.3;
    }
}

