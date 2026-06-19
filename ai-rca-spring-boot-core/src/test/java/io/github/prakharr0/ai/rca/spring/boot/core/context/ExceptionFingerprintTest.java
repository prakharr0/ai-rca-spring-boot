package io.github.prakharr0.ai.rca.spring.boot.core.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionFingerprintTest {

    @Test
    void sameInput_producesSameFingerprint() {
        ContextSnapshot s = snapshot("NPE", "NPE", "at Foo.java:1");
        assertThat(ExceptionFingerprint.generate(s))
                .isEqualTo(ExceptionFingerprint.generate(s));
    }

    @Test
    void differentStackTrace_producesDifferentFingerprint() {
        ContextSnapshot a = snapshot("NPE", "NPE", "at Foo.java:1");
        ContextSnapshot b = snapshot("NPE", "NPE", "at Bar.java:99");
        assertThat(ExceptionFingerprint.generate(a))
                .isNotEqualTo(ExceptionFingerprint.generate(b));
    }

    @Test
    void differentExceptionType_producesDifferentFingerprint() {
        ContextSnapshot a = snapshot("NPE", "NPE", "at Foo.java:1");
        ContextSnapshot b = snapshot("IAE", "IAE", "at Foo.java:1");
        assertThat(ExceptionFingerprint.generate(a))
                .isNotEqualTo(ExceptionFingerprint.generate(b));
    }

    @Test
    void fingerprintIsLowercaseHex() {
        String fp = ExceptionFingerprint.generate(snapshot("NPE", "NPE", "at Foo.java:1"));
        assertThat(fp).matches("[0-9a-f]+");
    }

    @Test
    void fingerprintIsSha256Length_64chars() {
        String fp = ExceptionFingerprint.generate(snapshot("NPE", "NPE", "at Foo.java:1"));
        assertThat(fp).hasSize(64);
    }

    private ContextSnapshot snapshot(String exType, String rootType, String stack) {
        return new ContextSnapshot(exType, rootType, "msg", stack, "", "3.2", "21",
                "default", "jar", "local", "none", "spring-mvc", "maven");
    }
}