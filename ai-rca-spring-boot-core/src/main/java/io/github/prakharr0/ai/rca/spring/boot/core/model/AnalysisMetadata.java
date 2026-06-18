package io.github.prakharr0.ai.rca.spring.boot.core.model;

/**
 * Observability metadata attached to every AI RCA analysis call.
 *
 * <p>This record is populated by the library — it is NOT part of the AI model's
 * JSON output. It is constructed from the {@code ChatResponse} metadata after
 * the model responds and attached to the parsed {@link AiRcaResponse}.
 *
 * <p>All fields are nullable. A {@code null} value means the provider did not
 * report that metric for this call (some providers omit usage on errors or
 * streaming responses).
 *
 * @param inputTokens  Number of tokens consumed by the prompt (system + user messages).
 *                     Directly affects API cost. {@code null} if the provider did not report it.
 * @param outputTokens Number of tokens generated in the model's response.
 *                     A value consistently near {@code max_tokens} indicates the response
 *                     was truncated — lower temperature or inspect prompt size.
 *                     {@code null} if the provider did not report it.
 *
 * @see AiRcaResponse
 */
public record AnalysisMetadata(
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
) {}