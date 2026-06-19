package io.github.prakharr0.ai.rca.spring.boot.starter.web;

import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRcaEventsControllerTest {

    private ExceptionTimelineStore store;
    private AiRcaEventsController controller;

    @BeforeEach
    void setUp() {
        store = new ExceptionTimelineStore(100);
        controller = new AiRcaEventsController(store);
    }

    // ── GET /ai/rca/events ────────────────────────────────────────────────────

    @Test
    void events_returnsEmpty_whenNoEvents() {
        List<ExceptionOccurrence> result = controller.events(null, null, 50);
        assertThat(result).isEmpty();
    }

    @Test
    void events_returnsStoredEvents() {
        store.add(occurrence("e1", Instant.now()));
        store.add(occurrence("e2", Instant.now().plusSeconds(1)));

        List<ExceptionOccurrence> result = controller.events(null, null, 50);
        assertThat(result).hasSize(2);
    }

    @Test
    void events_respectsLimit() {
        for (int i = 0; i < 10; i++) {
            store.add(occurrence("e" + i, Instant.now().plusSeconds(i)));
        }
        assertThat(controller.events(null, null, 3)).hasSize(3);
    }

    @Test
    void events_withFromParam_filtersEarlierEvents() {
        Instant boundary = Instant.parse("2026-01-01T12:00:00Z");
        store.add(occurrence("before", boundary.minusSeconds(3600)));
        store.add(occurrence("after",  boundary.plusSeconds(3600)));

        List<ExceptionOccurrence> result = controller.events(
                "2026-01-01T12:00:00Z", null, 50);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo("after");
    }

    @Test
    void events_withInvalidFromParam_throws400() {
        assertThatThrownBy(() -> controller.events("not-a-date", null, 50))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unable to parse");
    }

    // ── GET /ai/rca/events/at ─────────────────────────────────────────────────

    @Test
    void eventAt_findsNearestEvent() {
        Instant target = Instant.parse("2026-01-01T15:00:00Z");
        store.add(occurrence("near", target.plusSeconds(30)));

        Map<String, Object> response = controller.eventAt("2026-01-01T15:00:00Z", 300);

        assertThat(response).containsKey("event");
        ExceptionOccurrence event = (ExceptionOccurrence) response.get("event");
        assertThat(event).isNotNull();
        assertThat(event.getEventId()).isEqualTo("near");
    }

    @Test
    void eventAt_returnsNullEvent_whenNothingWithinTolerance() {
        Instant target = Instant.parse("2026-01-01T15:00:00Z");
        store.add(occurrence("far", target.plusSeconds(7200)));

        Map<String, Object> response = controller.eventAt("2026-01-01T15:00:00Z", 60);
        assertThat(response.get("event")).isNull();
    }

    @Test
    void eventAt_includesRequestedTimeAndTolerance() {
        Map<String, Object> response = controller.eventAt("2026-01-01T15:00:00Z", 120);
        assertThat(response).containsKey("requestedTime");
        assertThat(response).containsKey("toleranceSeconds");
    }

    @Test
    void eventAt_withUnparsableTime_throws400() {
        assertThatThrownBy(() -> controller.eventAt("not-a-time", 60))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unable to parse time");
    }

    @Test
    void eventAt_enforcesMinimumTolerance60Seconds() {
        Instant target = Instant.parse("2026-01-01T15:00:00Z");
        store.add(occurrence("e1", target.plusSeconds(50)));

        // toleranceSeconds=10 → clamped to 60 → event within 60s is found
        Map<String, Object> response = controller.eventAt("2026-01-01T15:00:00Z", 10);
        assertThat(response.get("event")).isNotNull();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ExceptionOccurrence occurrence(String id, Instant at) {
        return new ExceptionOccurrence(id, at, "java.lang.RuntimeException",
                "java.lang.RuntimeException", "test", "fp-" + id,
                "GET", "/test", "thread-1");
    }
}