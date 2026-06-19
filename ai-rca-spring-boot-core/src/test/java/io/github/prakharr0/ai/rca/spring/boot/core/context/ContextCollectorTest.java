package io.github.prakharr0.ai.rca.spring.boot.core.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCollectorTest {

    private final ContextCollector collector = new ContextCollector();

    @Test
    void capturesExceptionType() {
        ContextSnapshot snapshot = collector.collect(new IllegalArgumentException("bad arg"));
        assertThat(snapshot.exceptionType()).isEqualTo("java.lang.IllegalArgumentException");
    }

    @Test
    void capturesRootCauseType_forDirectException() {
        ContextSnapshot snapshot = collector.collect(new NullPointerException("npe"));
        assertThat(snapshot.rootCauseType()).isEqualTo("java.lang.NullPointerException");
    }

    @Test
    void traversesToRootCause_forWrappedException() {
        NullPointerException root = new NullPointerException("root npe");
        RuntimeException wrapper = new RuntimeException("wrapper", root);
        RuntimeException outer = new RuntimeException("outer", wrapper);

        ContextSnapshot snapshot = collector.collect(outer);

        assertThat(snapshot.exceptionType()).isEqualTo("java.lang.RuntimeException");
        assertThat(snapshot.rootCauseType()).isEqualTo("java.lang.NullPointerException");
        assertThat(snapshot.rootCauseMessage()).isEqualTo("root npe");
    }

    @Test
    void stackTraceIsNonEmpty() {
        ContextSnapshot snapshot = collector.collect(new RuntimeException("test"));
        assertThat(snapshot.stackTrace()).isNotBlank();
    }

    @Test
    void stackTraceExcludesFrameworkPackages() {
        ContextSnapshot snapshot = collector.collect(new RuntimeException("test"));
        // Framework packages should be filtered out; only application frames kept
        assertThat(snapshot.stackTrace()).doesNotContain("org.springframework.web.servlet");
        assertThat(snapshot.stackTrace()).doesNotContain("org.apache.catalina");
    }

    @Test
    void capturesJavaVersion() {
        ContextSnapshot snapshot = collector.collect(new RuntimeException("test"));
        assertThat(snapshot.javaVersion()).isNotBlank();
    }

    @Test
    void capturesSpringVersion() {
        ContextSnapshot snapshot = collector.collect(new RuntimeException("test"));
        // Spring is on the classpath so version must be present
        assertThat(snapshot.springVersion()).isNotBlank();
    }

    @Test
    void rootCauseMessage_isNullWhenExceptionHasNoMessage() {
        ContextSnapshot snapshot = collector.collect(new NullPointerException());
        assertThat(snapshot.rootCauseMessage()).isNull();
    }
}