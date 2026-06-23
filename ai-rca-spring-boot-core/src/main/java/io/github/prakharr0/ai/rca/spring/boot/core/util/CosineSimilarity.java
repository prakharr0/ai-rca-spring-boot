package io.github.prakharr0.ai.rca.spring.boot.core.util;

public final class CosineSimilarity {

    private CosineSimilarity() {}

    /**
     * Computes cosine similarity between two equal-length float vectors.
     * Returns a value in [-1.0, 1.0]; higher means more similar.
     * Returns 0.0 if either vector is null, empty, or has zero magnitude.
     */
    public static double compute(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0.0;

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }
}