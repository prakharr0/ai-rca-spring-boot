package io.github.prakharr0.ai.rca.spring.boot.core.store;

import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTimelineStoreTest {

    private ExceptionTimelineStore store;

    @BeforeEach
    void setUp() {
        store = new ExceptionTimelineStore(100);
    }

    // ── add / latest ────────────────────────────────────────────────────────

    @Test
    void latest_returnsEmpty_whenStoreIsEmpty() {
        assertThat(store.latest(10)).isEmpty();
    }

    @Test
    void latest_returnsMostRecentFirst() {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T11:00:00Z");
        store.add(occurrence("e1", t1, "fp-1"));
        store.add(occurrence("e2", t2, "fp-2"));

        List<ExceptionOccurrence> result = store.latest(10);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventId()).isEqualTo("e2"); // most recent first
        assertThat(result.get(1).getEventId()).isEqualTo("e1");
    }

    @Test
    void latest_respectsLimit() {
        for (int i = 0; i < 10; i++) {
            store.add(occurrence("e" + i, Instant.now(), "fp-" + i));
        }
        assertThat(store.latest(3)).hasSize(3);
    }

    @Test
    void capacityEviction_removesOldestEvent() {
        // Constructor enforces Math.max(50, size), so effective minimum capacity is 50.
        // Fill exactly 50 events, then add one more to trigger eviction of the first.
        ExceptionTimelineStore cappedStore = new ExceptionTimelineStore(50);
        String evictedId = "evicted-first";
        cappedStore.add(occurrence(evictedId, Instant.parse("2026-01-01T00:00:00Z"), "fp-evicted"));

        for (int i = 1; i < 50; i++) {
            cappedStore.add(occurrence("e" + i, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i), "fp-" + i));
        }
        // This 51st add exceeds capacity → evicts the first
        cappedStore.add(occurrence("trigger", Instant.parse("2026-01-01T01:00:00Z"), "fp-trigger"));

        List<ExceptionOccurrence> events = cappedStore.latest(100);
        assertThat(events).hasSize(50);
        assertThat(events).noneMatch(e -> e.getEventId().equals(evictedId));
    }

    // ── findBetween ──────────────────────────────────────────────────────────

    @Test
    void findBetween_returnsEventsInRange() {
        Instant base = Instant.parse("2026-01-01T12:00:00Z");
        store.add(occurrence("before", base.minusSeconds(3600), "fp-b"));
        store.add(occurrence("inside", base, "fp-i"));
        store.add(occurrence("after", base.plusSeconds(3600), "fp-a"));

        List<ExceptionOccurrence> result = store.findBetween(
                base.minusSeconds(60), base.plusSeconds(60), 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo("inside");
    }

    @Test
    void findBetween_nullBounds_returnsAll() {
        store.add(occurrence("e1", Instant.now().minusSeconds(100), "fp-1"));
        store.add(occurrence("e2", Instant.now(), "fp-2"));
        assertThat(store.findBetween(null, null, 100)).hasSize(2);
    }

    // ── findAt ───────────────────────────────────────────────────────────────

    @Test
    void findAt_returnsNearestEvent() {
        Instant target = Instant.parse("2026-01-01T15:00:00Z");
        store.add(occurrence("near", target.plusSeconds(30), "fp-near"));
        store.add(occurrence("far", target.plusSeconds(3600), "fp-far"));

        ExceptionOccurrence result = store.findAt(target, Duration.ofSeconds(60));
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo("near");
    }

    @Test
    void findAt_returnsNull_whenNothingWithinTolerance() {
        Instant target = Instant.parse("2026-01-01T15:00:00Z");
        store.add(occurrence("far", target.plusSeconds(7200), "fp-far"));

        assertThat(store.findAt(target, Duration.ofSeconds(60))).isNull();
    }

    @Test
    void findAt_returnsNull_whenStoreIsEmpty() {
        assertThat(store.findAt(Instant.now(), Duration.ofSeconds(60))).isNull();
    }

    @Test
    void findAt_returnsNull_forNullTarget() {
        store.add(occurrence("e1", Instant.now(), "fp-1"));
        assertThat(store.findAt(null, Duration.ofSeconds(60))).isNull();
    }

    // ── attachAnalysisByFingerprint ───────────────────────────────────────────

    @Test
    void attachAnalysis_updatesAllMatchingEvents() {
        store.add(occurrence("e1", Instant.now(), "fp-shared"));
        store.add(occurrence("e2", Instant.now().plusSeconds(1), "fp-shared"));
        store.add(occurrence("e3", Instant.now().plusSeconds(2), "fp-other"));

        AiRcaResponse response = new AiRcaResponse(0.9, "pattern", "msg", List.of(), List.of(), null, null);
        store.attachAnalysisByFingerprint("fp-shared", response);

        List<ExceptionOccurrence> all = store.latest(10);
        long completed = all.stream()
                .filter(e -> e.getAnalysisStatus() == AnalysisStatus.COMPLETED)
                .count();
        assertThat(completed).isEqualTo(2);

        ExceptionOccurrence other = all.stream()
                .filter(e -> e.getEventId().equals("e3"))
                .findFirst().orElseThrow();
        assertThat(other.getAnalysisStatus()).isEqualTo(AnalysisStatus.PENDING);
    }

    @Test
    void attachAnalysis_unknownFingerprint_doesNotThrow() {
        AiRcaResponse response = new AiRcaResponse(0.9, "pattern", "msg", List.of(), List.of(), null, null);
        store.attachAnalysisByFingerprint("fp-unknown", response);
    }

    // ── markFailureByFingerprint ──────────────────────────────────────────────

    @Test
    void markFailure_setsStatusToFailed_onMatchingEvents() {
        store.add(occurrence("e1", Instant.now(), "fp-fail"));
        store.markFailureByFingerprint("fp-fail", "AI timed out");

        ExceptionOccurrence e = store.latest(1).get(0);
        assertThat(e.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(e.getAnalysisError()).isEqualTo("AI timed out");
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private ExceptionOccurrence occurrence(String id, Instant at, String fingerprint) {
        return new ExceptionOccurrence(id, at, "java.lang.RuntimeException",
                "java.lang.RuntimeException", "test", fingerprint,
                "GET", "/test", "thread-1");
    }
}