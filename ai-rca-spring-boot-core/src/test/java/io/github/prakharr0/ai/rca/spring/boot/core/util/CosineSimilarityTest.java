package io.github.prakharr0.ai.rca.spring.boot.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class CosineSimilarityTest {

    @Test
    void identicalVectors_returns1() {
        float[] a = {1f, 0f, 0f};
        assertThat(CosineSimilarity.compute(a, a)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void orthogonalVectors_returns0() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void partialSimilarity_returnsExpectedScore() {
        // dot=1, normA=√2, normB=1 → 1/√2 ≈ 0.707
        float[] a = {1f, 1f};
        float[] b = {1f, 0f};
        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(0.7071, offset(0.0001));
    }

    @Test
    void symmetrical_sameResultBothDirections() {
        float[] a = {0.6f, 0.8f};
        float[] b = {0.8f, 0.6f};
        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(CosineSimilarity.compute(b, a), offset(1e-9));
    }

    @Test
    void nullFirstArg_returns0() {
        assertThat(CosineSimilarity.compute(null, new float[]{1f, 0f})).isEqualTo(0.0);
    }

    @Test
    void nullSecondArg_returns0() {
        assertThat(CosineSimilarity.compute(new float[]{1f, 0f}, null)).isEqualTo(0.0);
    }

    @Test
    void emptyArrays_returns0() {
        assertThat(CosineSimilarity.compute(new float[]{}, new float[]{})).isEqualTo(0.0);
    }

    @Test
    void mismatchedLengths_returns0() {
        assertThat(CosineSimilarity.compute(new float[]{1f, 0f}, new float[]{1f})).isEqualTo(0.0);
    }

    @Test
    void zeroVector_returns0() {
        float[] zero = {0f, 0f};
        float[] a    = {1f, 0f};
        assertThat(CosineSimilarity.compute(zero, a)).isEqualTo(0.0);
    }

    @Test
    void negativeComponents_handledCorrectly() {
        float[] a = { 1f,  0f};
        float[] b = {-1f,  0f};
        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(-1.0, offset(1e-9));
    }
}