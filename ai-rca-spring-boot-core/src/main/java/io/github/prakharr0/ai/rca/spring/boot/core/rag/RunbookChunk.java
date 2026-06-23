package io.github.prakharr0.ai.rca.spring.boot.core.rag;

/**
 * A single chunk of runbook content with its precomputed embedding vector.
 *
 * @param source    the resource path this chunk was loaded from
 * @param heading   the markdown heading under which this chunk appeared (or empty string)
 * @param content   the raw text content of this chunk
 * @param embedding the embedding vector generated from {@code heading + content}
 */
public record RunbookChunk(String source, String heading, String content, float[] embedding) {}