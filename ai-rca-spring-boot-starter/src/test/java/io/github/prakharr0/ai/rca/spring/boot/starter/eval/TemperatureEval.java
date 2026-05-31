package io.github.prakharr0.ai.rca.spring.boot.starter.eval;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl.DefaultAiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.chat.RcaChatService;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
public class TemperatureEval {

    @Autowired
    private DefaultAiRcaAnalyzer analyzer;

    @MockitoBean
    RcaChatService chatService;

    Exception exception;

    @BeforeEach
    void init() throws IOException {
        final byte[] bytes = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("throwable.txt")).readAllBytes();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            exception = (ArithmeticException) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void givenTemperatures_whenAnalysisTriggered_thenOutputEvaluated() {
//        analyzer.analyze(exception);
//
//        await().atMost(30, TimeUnit.SECONDS)
//               .until(() -> !analyzer.getResults().isEmpty());
//
//        AiRcaResponse response = analyzer.getResults().values().iterator().next();
//        assertThat(response).isNotNull();
//        assertThat(response.rootCauses()).isNotEmpty();
//        assertThat(response.analysisConfidence()).isBetween(0.0, 1.0);
//        assertThat(response.knownPattern()).isNotBlank();
    }

}