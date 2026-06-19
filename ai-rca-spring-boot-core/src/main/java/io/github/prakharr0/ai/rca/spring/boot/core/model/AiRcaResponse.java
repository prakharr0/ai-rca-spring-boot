package io.github.prakharr0.ai.rca.spring.boot.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Represents the structured result of an AI-powered Root Cause Analysis.
 *
 * <p>This record is produced in two stages:
 * <ol>
 *   <li>Jackson deserializes the AI model's JSON response into an instance where
 *       {@code metadata} is {@code null} (the model does not output this field).</li>
 *   <li>The analyzer calls {@link #withMetadata(AnalysisMetadata)} to attach
 *       token usage and other observability data from the {@code ChatResponse}.</li>
 * </ol>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures that any fields
 * the model outputs beyond the defined schema do not cause deserialization failures.
 *
 * @param analysisConfidence  A score between {@code 0.0} and {@code 1.0} indicating
 *                            how confident the AI is in the analysis.
 * @param knownPattern        A named pattern the AI matched this exception against.
 *                            Never {@code null} — the model is instructed to always provide one.
 * @param exceptionMessage    The exception message being analyzed.
 * @param missingInformation  Context items absent but that would improve accuracy.
 *                            Empty list when confidence is sufficient.
 * @param rootCauses          Ordered list of {@link RootCause} hypotheses, ranked 1 = most likely.
 * @param metadata            Token usage and call observability data. {@code null} only when
 *                            this instance was produced directly from AI JSON without enrichment.
 *
 * @see RootCause
 * @see AnalysisMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRcaResponse(
        double analysisConfidence,
        String knownPattern,
        String exceptionMessage,
        List<String> missingInformation,
        List<RootCause> rootCauses,
        AnalysisMetadata metadata,
        Boolean lowConfidence
) {

    /**
     * Returns {@code true} if this result was flagged as low-confidence by the library.
     * Null-safe: {@code null} (present when deserialized directly from AI JSON, before any
     * threshold check) is treated as {@code false}.
     */
    public boolean isLowConfidence() {
        return Boolean.TRUE.equals(lowConfidence);
    }

    public AiRcaResponse withMetadata(AnalysisMetadata metadata) {
        return new AiRcaResponse(analysisConfidence, knownPattern, exceptionMessage,
                missingInformation, rootCauses, metadata, lowConfidence);
    }

    public AiRcaResponse withLowConfidenceFlag(boolean lowConfidence) {
        return new AiRcaResponse(analysisConfidence, knownPattern, exceptionMessage,
                missingInformation, rootCauses, metadata, lowConfidence);
    }
}