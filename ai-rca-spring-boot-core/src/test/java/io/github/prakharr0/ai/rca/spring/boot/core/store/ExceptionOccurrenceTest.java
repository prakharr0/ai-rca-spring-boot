package io.github.prakharr0.ai.rca.spring.boot.core.store;

import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionOccurrenceTest {

    private ExceptionOccurrence occurrence() {
        return new ExceptionOccurrence(
                "evt-001", Instant.now(),
                "java.lang.NullPointerException", "java.lang.NullPointerException",
                "msg", "fp-abc", "GET", "/api/users", "http-exec-1"
        );
    }

    @Test
    void initialStatusIsPending() {
        assertThat(occurrence().getAnalysisStatus()).isEqualTo(AnalysisStatus.PENDING);
    }

    @Test
    void initialAnalysisIsNull() {
        assertThat(occurrence().getAnalysis()).isNull();
    }

    @Test
    void attachAnalysis_setsStatusToCompleted() {
        ExceptionOccurrence o = occurrence();
        o.attachAnalysis(stubResponse());
        assertThat(o.getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void attachAnalysis_storesResponse() {
        ExceptionOccurrence o = occurrence();
        AiRcaResponse response = stubResponse();
        o.attachAnalysis(response);
        assertThat(o.getAnalysis()).isSameAs(response);
    }

    @Test
    void attachAnalysis_clearsError() {
        ExceptionOccurrence o = occurrence();
        o.markFailed("prior error");
        o.attachAnalysis(stubResponse());
        assertThat(o.getAnalysisError()).isNull();
    }

    @Test
    void markFailed_setsStatusToFailed() {
        ExceptionOccurrence o = occurrence();
        o.markFailed("timeout");
        assertThat(o.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void markFailed_storesErrorMessage() {
        ExceptionOccurrence o = occurrence();
        o.markFailed("parse error");
        assertThat(o.getAnalysisError()).isEqualTo("parse error");
    }

    @Test
    void eventId_isRetained() {
        assertThat(occurrence().getEventId()).isEqualTo("evt-001");
    }

    @Test
    void fingerprint_isRetained() {
        assertThat(occurrence().getFingerprint()).isEqualTo("fp-abc");
    }

    private AiRcaResponse stubResponse() {
        return new AiRcaResponse(0.9, "pattern", "msg", List.of(), List.of(), null, null);
    }
}