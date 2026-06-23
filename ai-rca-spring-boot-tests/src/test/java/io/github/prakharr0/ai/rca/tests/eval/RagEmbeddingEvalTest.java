package io.github.prakharr0.ai.rca.tests.eval;

import io.github.prakharr0.ai.rca.spring.boot.core.rag.RcaRunbook;
import io.github.prakharr0.ai.rca.spring.boot.core.rag.RunbookChunk;
import io.github.prakharr0.ai.rca.spring.boot.core.rag.RunbookStore;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import io.github.prakharr0.ai.rca.spring.boot.core.util.CosineSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG embedding integration eval — validates that real OpenAI embeddings produce
 * semantically meaningful similarity scores across exception fixture categories.
 *
 * <p><b>What it tests:</b>
 * <ul>
 *   <li>Semantic clustering: same-category exceptions are more similar to each other than cross-category</li>
 *   <li>{@link ExceptionTimelineStore#findSimilar} returns the closest past incident for a given exception</li>
 *   <li>{@link RunbookStore#findRelevant} returns the correct runbook section for a given exception type</li>
 *   <li>Full pairwise similarity matrix logged for human review</li>
 * </ul>
 *
 * <p><b>Why @Disabled:</b> Makes live embedding API calls (OpenAI text-embedding-3-small).
 * Run manually before any RAG pipeline change to establish a baseline.
 *
 * <p><b>Runbook setup:</b> The nested {@link TestRunbookConfig} registers a marker bean annotated
 * with {@code @RcaRunbook}, which causes the Spring-managed {@link RunbookStore} to ingest
 * {@code classpath:runbooks/test-runbook.md} via its {@code afterSingletonsInstantiated()} hook.
 * This exercises the full production path without bypassing the package-private ingest method.
 */
@Disabled("Requires OPENAI_KEY — run manually: right-click test class → Run")
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagEmbeddingEvalTest {

    /**
     * Registers a @RcaRunbook-annotated marker bean so the Spring-managed RunbookStore
     * ingests the test runbook at context startup via afterSingletonsInstantiated().
     */
    @TestConfiguration
    static class TestRunbookConfig {

        @RcaRunbook(source = "classpath:runbooks/test-runbook.md")
        static class TestRunbookMarker {}

        @Bean
        TestRunbookMarker testRunbookMarker() {
            return new TestRunbookMarker();
        }
    }

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RunbookStore runbookStore;

    private List<EvalFixture> fixtures;
    private Map<String, float[]> embeddingCache;

    /**
     * Embeds all fixture exception texts before any test runs — one API call per fixture.
     * Text format mirrors DefaultAiRcaAnalyzer.generateEmbedding() exactly so vectors are comparable.
     */
    @BeforeAll
    void embedAllFixtures() {
        fixtures = FixtureLoader.loadAll();
        embeddingCache = new LinkedHashMap<>();
        for (EvalFixture f : fixtures) {
            String text = f.rootCauseType() + ": " + f.rootCauseMessage()
                    + "\n" + f.stackTrace();
            embeddingCache.put(f.id(), embeddingModel.embed(text));
        }
        int dims = embeddingCache.values().iterator().next().length;
        log.info("[RAG Eval] Embedded {} fixtures, {} dims each", embeddingCache.size(), dims);
    }

    // ── Semantic clustering ───────────────────────────────────────────────────

    @Test
    void npeFixtures_areMutuallyMoreSimilar_thanCrossCategory() {
        float[] npe1 = embeddingCache.get("npe_service_layer");
        float[] npe2 = embeddingCache.get("npe_deep_stack");
        float[] db1  = embeddingCache.get("db_connection_timeout");

        double npeVsNpe = CosineSimilarity.compute(npe1, npe2);
        double npeVsDb  = CosineSimilarity.compute(npe1, db1);

        log.info("[RAG Eval] npe_service_layer vs npe_deep_stack      : {}",
                String.format("%.4f", npeVsNpe));
        log.info("[RAG Eval] npe_service_layer vs db_connection_timeout: {}",
                String.format("%.4f", npeVsDb));

        assertThat(npeVsNpe)
                .as("NPE exceptions should be more similar to each other than to DB exceptions")
                .isGreaterThan(npeVsDb);
    }

    @Test
    void dbFixtures_areMutuallyMoreSimilar_thanCrossCategory() {
        float[] db1  = embeddingCache.get("db_connection_timeout");
        float[] db2  = embeddingCache.get("db_query_syntax_error");
        float[] npe1 = embeddingCache.get("npe_service_layer");

        double dbVsDb  = CosineSimilarity.compute(db1, db2);
        double dbVsNpe = CosineSimilarity.compute(db1, npe1);

        log.info("[RAG Eval] db_connection_timeout vs db_query_syntax_error: {}",
                String.format("%.4f", dbVsDb));
        log.info("[RAG Eval] db_connection_timeout vs npe_service_layer     : {}",
                String.format("%.4f", dbVsNpe));

        assertThat(dbVsDb)
                .as("DB exceptions should be more similar to each other than to NPE exceptions")
                .isGreaterThan(dbVsNpe);
    }

    @Test
    void allFixtures_logSimilarityMatrix() {
        List<String> keys = List.of(
                "npe_service_layer", "npe_deep_stack",
                "db_connection_timeout", "db_query_syntax_error", "missing_datasource",
                "oom_heap", "http_timeout_downstream"
        );
        log.info("[RAG Eval] Pairwise cosine similarity matrix (text-embedding-3-small):");
        for (String a : keys) {
            StringBuilder row = new StringBuilder(String.format("  %-35s |", a));
            for (String b : keys) {
                double s = CosineSimilarity.compute(embeddingCache.get(a), embeddingCache.get(b));
                row.append(String.format(" %.3f", s));
            }
            log.info(row.toString());
        }
    }

    // ── ExceptionTimelineStore.findSimilar ───────────────────────────────────

    @Test
    void timelineStore_findSimilar_returnsNpeDeepStack_forNpeQuery() {
        ExceptionTimelineStore store = buildStoreWithAllFixtures();

        float[] npeQuery = embeddingCache.get("npe_service_layer");
        var results = store.findSimilar(npeQuery, "npe_service_layer", 5, 0.0);

        log.info("[RAG Eval] findSimilar(npe_service_layer) top-5:");
        results.forEach(r -> log.info("  {} → {}",
                r.occurrence().getFingerprint(), String.format("%.4f", r.score())));

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().occurrence().getFingerprint())
                .as("The most similar past exception to npe_service_layer should be npe_deep_stack")
                .isEqualTo("npe_deep_stack");
    }

    @Test
    void timelineStore_findSimilar_returnsDbException_forDbQuery() {
        ExceptionTimelineStore store = buildStoreWithAllFixtures();

        float[] dbQuery = embeddingCache.get("db_connection_timeout");
        var results = store.findSimilar(dbQuery, "db_connection_timeout", 5, 0.0);

        log.info("[RAG Eval] findSimilar(db_connection_timeout) top-5:");
        results.forEach(r -> log.info("  {} → {}",
                r.occurrence().getFingerprint(), String.format("%.4f", r.score())));

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().occurrence().getFingerprint())
                .as("Top result for a DB connection query should be another DB-related exception")
                .isIn("db_query_syntax_error", "missing_datasource");
    }

    @Test
    void timelineStore_findSimilar_excludesCurrentFingerprint() {
        ExceptionTimelineStore store = buildStoreWithAllFixtures();

        float[] npeQuery = embeddingCache.get("npe_service_layer");
        var results = store.findSimilar(npeQuery, "npe_service_layer", 10, 0.0);

        assertThat(results)
                .noneMatch(r -> r.occurrence().getFingerprint().equals("npe_service_layer"));
    }

    @Test
    void timelineStore_findSimilar_fewerResults_atHigherMinScore() {
        ExceptionTimelineStore store = buildStoreWithAllFixtures();

        float[] npeQuery = embeddingCache.get("npe_service_layer");
        var strictResults = store.findSimilar(npeQuery, "npe_service_layer", 10, 0.99);
        var looseResults  = store.findSimilar(npeQuery, "npe_service_layer", 10, 0.0);

        log.info("[RAG Eval] findSimilar results at minScore=0.99: {}, minScore=0.0: {}",
                strictResults.size(), looseResults.size());

        assertThat(strictResults.size()).isLessThan(looseResults.size());
    }

    // ── RunbookStore.findRelevant ─────────────────────────────────────────────

    @Test
    void runbookStore_isPopulatedFromTestRunbook() {
        assertThat(runbookStore.isEmpty())
                .as("RunbookStore should have ingested classpath:runbooks/test-runbook.md at startup")
                .isFalse();
    }

    @Test
    void runbookStore_findRelevant_returnsNpeSection_forNpeException() {
        float[] npeEmbedding = embeddingCache.get("npe_service_layer");
        List<RunbookChunk> results = runbookStore.findRelevant(npeEmbedding, 1, 0.0);

        log.info("[RAG Eval] findRelevant(npe_service_layer) top section: '{}'",
                results.isEmpty() ? "NONE" : results.getFirst().heading());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().heading()).isEqualTo("NullPointerException Errors");
    }

    @Test
    void runbookStore_findRelevant_returnsDbSection_forDbException() {
        float[] dbEmbedding = embeddingCache.get("db_connection_timeout");
        List<RunbookChunk> results = runbookStore.findRelevant(dbEmbedding, 1, 0.0);

        log.info("[RAG Eval] findRelevant(db_connection_timeout) top section: '{}'",
                results.isEmpty() ? "NONE" : results.getFirst().heading());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().heading()).isEqualTo("Database Connection Failures");
    }

    @Test
    void runbookStore_findRelevant_returnsOomSection_forOomException() {
        float[] oomEmbedding = embeddingCache.get("oom_heap");
        List<RunbookChunk> results = runbookStore.findRelevant(oomEmbedding, 1, 0.0);

        log.info("[RAG Eval] findRelevant(oom_heap) top section: '{}'",
                results.isEmpty() ? "NONE" : results.getFirst().heading());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().heading()).isEqualTo("OutOfMemoryError Heap Space");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ExceptionTimelineStore buildStoreWithAllFixtures() {
        ExceptionTimelineStore store = new ExceptionTimelineStore(100);
        for (EvalFixture f : fixtures) {
            ExceptionOccurrence occ = new ExceptionOccurrence(
                    f.id(), Instant.now(),
                    f.exceptionType(), f.rootCauseType(),
                    f.rootCauseMessage() != null ? f.rootCauseMessage() : "",
                    f.id(), "GET", "/test", "thread-1");
            store.add(occ);
            store.attachEmbeddingByFingerprint(f.id(), embeddingCache.get(f.id()));
        }
        return store;
    }
}