package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.LowConfidenceAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRcaPropertiesTest {

    @Test
    void defaultEnabled_isTrue() {
        assertThat(new AiRcaProperties().isEnabled()).isTrue();
    }

    @Test
    void defaultHistorySize_is500() {
        assertThat(new AiRcaProperties().getHistorySize()).isEqualTo(500);
    }

    @Test
    void defaultChatEnabled_isTrue() {
        assertThat(new AiRcaProperties().isChatEnabled()).isTrue();
    }

    @Test
    void defaultChatUiEnabled_isTrue() {
        assertThat(new AiRcaProperties().isChatUiEnabled()).isTrue();
    }

    @Test
    void defaultTimeToleranceSeconds_is1800() {
        assertThat(new AiRcaProperties().getDefaultTimeToleranceSeconds()).isEqualTo(1800);
    }

    @Test
    void defaultChatContextEvents_is20() {
        assertThat(new AiRcaProperties().getChatContextEvents()).isEqualTo(20);
    }

    @Test
    void defaultTemperature_isNull() {
        assertThat(new AiRcaProperties().getTemperature()).isNull();
    }

    @Test
    void defaultMaxTokens_isNull() {
        assertThat(new AiRcaProperties().getMaxTokens()).isNull();
    }

    @Test
    void defaultMinConfidence_isZero() {
        assertThat(new AiRcaProperties().getMinConfidence()).isEqualTo(0.0);
    }

    @Test
    void defaultLowConfidenceAction_isLogWarn() {
        assertThat(new AiRcaProperties().getLowConfidenceAction())
                .isEqualTo(LowConfidenceAction.LOG_WARN);
    }

    @Test
    void settersWork() {
        AiRcaProperties p = new AiRcaProperties();
        p.setEnabled(false);
        p.setHistorySize(200);
        p.setMinConfidence(0.7);
        p.setLowConfidenceAction(LowConfidenceAction.SKIP);
        p.setTemperature(0.1);
        p.setMaxTokens(4096);

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getHistorySize()).isEqualTo(200);
        assertThat(p.getMinConfidence()).isEqualTo(0.7);
        assertThat(p.getLowConfidenceAction()).isEqualTo(LowConfidenceAction.SKIP);
        assertThat(p.getTemperature()).isEqualTo(0.1);
        assertThat(p.getMaxTokens()).isEqualTo(4096);
    }
}