package io.github.prakharr0.ai.rca.spring.boot.core.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookStoreTest {

    @Mock
    EmbeddingModel embeddingModel;

    RunbookStore store;

    // Two clearly distinct embeddings so cosine similarity separates them cleanly
    static final float[] DB_EMBEDDING  = {1f, 0f, 0f};
    static final float[] NPE_EMBEDDING = {0f, 1f, 0f};

    @BeforeEach
    void setUp() {
        store = new RunbookStore(embeddingModel);
    }

    // ── isEmpty ───────────────────────────────────────────────────────────────

    @Test
    void isEmpty_trueWhenNothingIngested() {
        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_falseAfterIngest() {
        when(embeddingModel.embed(anyString())).thenReturn(DB_EMBEDDING);
        store.ingest("classpath:test.md", "## Database Errors\nCheck your connection pool.");
        assertThat(store.isEmpty()).isFalse();
    }

    // ── findRelevant ──────────────────────────────────────────────────────────

    @Test
    void findRelevant_returnsEmpty_whenStoreIsEmpty() {
        assertThat(store.findRelevant(DB_EMBEDDING, 3, 0.0)).isEmpty();
    }

    @Test
    void findRelevant_returnsEmpty_whenQueryEmbeddingIsNull() {
        when(embeddingModel.embed(anyString())).thenReturn(DB_EMBEDDING);
        store.ingest("classpath:test.md", "## DB Errors\nSome content.");
        assertThat(store.findRelevant(null, 3, 0.0)).isEmpty();
    }

    @Test
    void findRelevant_returnsChunkWithHighestSimilarity() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(DB_EMBEDDING)   // first chunk gets db embedding
                .thenReturn(NPE_EMBEDDING); // second chunk gets npe embedding

        store.ingest("classpath:test.md",
                "## Database Errors\nCheck connection pool.\n\n## NullPointerException\nCheck for nulls.");

        // Query similar to DB_EMBEDDING → DB chunk should rank first
        List<RunbookChunk> results = store.findRelevant(DB_EMBEDDING, 1, 0.0);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().heading()).isEqualTo("Database Errors");
    }

    @Test
    void findRelevant_respectsTopK() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(DB_EMBEDDING)
                .thenReturn(NPE_EMBEDDING)
                .thenReturn(new float[]{0f, 0f, 1f});

        store.ingest("classpath:test.md",
                "## Section A\nContent A.\n\n## Section B\nContent B.\n\n## Section C\nContent C.");

        assertThat(store.findRelevant(DB_EMBEDDING, 2, 0.0)).hasSize(2);
    }

    @Test
    void findRelevant_excludesChunksBelowMinScore() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(DB_EMBEDDING)
                .thenReturn(NPE_EMBEDDING);

        store.ingest("classpath:test.md",
                "## DB Errors\nContent.\n\n## NPE Errors\nContent.");

        // DB_EMBEDDING vs NPE_EMBEDDING → cosine = 0.0 → below threshold of 0.5
        List<RunbookChunk> results = store.findRelevant(DB_EMBEDDING, 5, 0.5);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().heading()).isEqualTo("DB Errors");
    }

    @Test
    void findRelevant_returnsAllChunks_whenMinScoreIsZero() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(DB_EMBEDDING)
                .thenReturn(NPE_EMBEDDING);

        store.ingest("classpath:test.md",
                "## DB Errors\nContent.\n\n## NPE Errors\nContent.");

        assertThat(store.findRelevant(DB_EMBEDDING, 10, 0.0)).hasSize(2);
    }

    // ── ingest / chunking ─────────────────────────────────────────────────────

    @Test
    void ingest_createsOneChunkPerHeading() {
        when(embeddingModel.embed(anyString())).thenReturn(DB_EMBEDDING);
        String markdown = "## Section One\nContent one.\n\n## Section Two\nContent two.";
        store.ingest("classpath:test.md", markdown);

        // isEmpty is false — we can't directly count chunks, but findRelevant with minScore=0 tells us
        assertThat(store.findRelevant(DB_EMBEDDING, 10, 0.0)).hasSize(2);
    }

    @Test
    void ingest_handlesContentWithNoHeadings_asSingleChunk() {
        when(embeddingModel.embed(anyString())).thenReturn(DB_EMBEDDING);
        store.ingest("classpath:test.md", "Plain prose with no headings.");

        assertThat(store.findRelevant(DB_EMBEDDING, 10, 0.0)).hasSize(1);
    }

    @Test
    void ingest_skipsBlankChunks() {
        when(embeddingModel.embed(anyString())).thenReturn(DB_EMBEDDING);
        // Leading blank before first heading — should not create an empty chunk
        store.ingest("classpath:test.md", "\n\n## Section\nContent here.");

        assertThat(store.findRelevant(DB_EMBEDDING, 10, 0.0)).hasSize(1);
    }

    @Test
    void ingest_extractsHeadingCorrectly() {
        when(embeddingModel.embed(anyString())).thenReturn(DB_EMBEDDING);
        store.ingest("classpath:test.md", "## Database Connection Errors\nCheck pool size.");

        RunbookChunk chunk = store.findRelevant(DB_EMBEDDING, 1, 0.0).getFirst();
        assertThat(chunk.heading()).isEqualTo("Database Connection Errors");
        assertThat(chunk.source()).isEqualTo("classpath:test.md");
    }
}