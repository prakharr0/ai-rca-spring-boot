package io.github.prakharr0.ai.rca.spring.boot.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiRcaResponseTest {

    // ── isLowConfidence ──────────────────────────────────────────────────────

    @Test
    void isLowConfidence_falseForNull() {
        AiRcaResponse r = response(null);
        assertThat(r.isLowConfidence()).isFalse();
    }

    @Test
    void isLowConfidence_falseForFalse() {
        AiRcaResponse r = response(false);
        assertThat(r.isLowConfidence()).isFalse();
    }

    @Test
    void isLowConfidence_trueForTrue() {
        AiRcaResponse r = response(true);
        assertThat(r.isLowConfidence()).isTrue();
    }

    // ── withLowConfidenceFlag ────────────────────────────────────────────────

    @Test
    void withLowConfidenceFlag_setsFlag() {
        AiRcaResponse original = response(null);
        AiRcaResponse flagged = original.withLowConfidenceFlag(true);

        assertThat(flagged.isLowConfidence()).isTrue();
        assertThat(original.isLowConfidence()).isFalse(); // original unchanged
    }

    @Test
    void withLowConfidenceFlag_preservesOtherFields() {
        AiRcaResponse original = new AiRcaResponse(0.75, "some pattern", "msg",
                List.of("info"), List.of(), null, null);
        AiRcaResponse flagged = original.withLowConfidenceFlag(true);

        assertThat(flagged.analysisConfidence()).isEqualTo(0.75);
        assertThat(flagged.knownPattern()).isEqualTo("some pattern");
        assertThat(flagged.exceptionMessage()).isEqualTo("msg");
        assertThat(flagged.missingInformation()).containsExactly("info");
    }

    // ── withMetadata ─────────────────────────────────────────────────────────

    @Test
    void withMetadata_attachesMetadata() {
        AiRcaResponse original = response(null);
        AnalysisMetadata meta = new AnalysisMetadata(100L, 50L, 150L);
        AiRcaResponse enriched = original.withMetadata(meta);

        assertThat(enriched.metadata()).isSameAs(meta);
        assertThat(original.metadata()).isNull(); // original unchanged
    }

    @Test
    void withMetadata_preservesLowConfidenceFlag() {
        AiRcaResponse original = response(true);
        AiRcaResponse enriched = original.withMetadata(new AnalysisMetadata(1L, 1L, 2L));

        assertThat(enriched.isLowConfidence()).isTrue();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private AiRcaResponse response(Boolean lowConfidence) {
        return new AiRcaResponse(0.8, "pattern", "msg", List.of(), List.of(), null, lowConfidence);
    }
}