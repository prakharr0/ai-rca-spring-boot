package io.github.prakharr0.ai.rca.spring.boot.starter.exception;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private AiRcaAnalyzer analyzer;
    @Mock
    private ContextCollector collector;
    @Mock
    private ExceptionTimelineStore timelineStore;

    private GlobalExceptionHandler handler;

    private static final ContextSnapshot STUB_SNAPSHOT = new ContextSnapshot(
            "java.lang.RuntimeException", "java.lang.RuntimeException",
            "test error", "at Foo.java:1", "",
            "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven"
    );

    @BeforeEach
    void setUp() {
        when(collector.collect(any(Throwable.class))).thenReturn(STUB_SNAPSHOT);
        handler = new GlobalExceptionHandler(analyzer, collector, timelineStore);
    }

    @Test
    void rethrowsOriginalException() {
        RuntimeException ex = new RuntimeException("original");
        assertThatThrownBy(() -> handler.handle(ex, null))
                .isSameAs(ex);
    }

    @Test
    void callsAnalyze_beforeRethrowing() {
        RuntimeException ex = new RuntimeException("test");
        try { handler.handle(ex, null); } catch (Exception ignored) {}
        verify(analyzer).analyze(ex);
    }

    @Test
    void addsOccurrenceToTimeline() {
        RuntimeException ex = new RuntimeException("test");
        try { handler.handle(ex, null); } catch (Exception ignored) {}
        verify(timelineStore).add(any());
    }

    @Test
    void occurrenceHasCorrectExceptionType() {
        RuntimeException ex = new RuntimeException("test");
        try { handler.handle(ex, null); } catch (Exception ignored) {}

        ArgumentCaptor<io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence> captor =
                ArgumentCaptor.forClass(io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence.class);
        verify(timelineStore).add(captor.capture());

        assertThat(captor.getValue().getExceptionType())
                .isEqualTo("java.lang.RuntimeException");
    }

    @Test
    void occurrenceHasNonNullEventId() {
        RuntimeException ex = new RuntimeException("test");
        try { handler.handle(ex, null); } catch (Exception ignored) {}

        ArgumentCaptor<io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence> captor =
                ArgumentCaptor.forClass(io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence.class);
        verify(timelineStore).add(captor.capture());

        assertThat(captor.getValue().getEventId()).isNotBlank();
    }

    @Test
    void nullRequest_doesNotThrowBeforeRethrow() {
        RuntimeException ex = new RuntimeException("test");
        assertThatThrownBy(() -> handler.handle(ex, null))
                .isSameAs(ex);
    }
}