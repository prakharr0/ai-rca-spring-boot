package io.github.prakharr0.ai.rca.tests.eval;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.LowConfidenceAction;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl.DefaultAiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test for the confidence threshold quality gate in {@link DefaultAiRcaAnalyzer}.
 *
 * <p><b>No AI calls are made.</b> The ChatClient is mocked to return a fixed low-confidence
 * JSON response. This tests the library's branching logic, not the model's output.
 *
 * <p><b>Two behaviors under test:</b>
 * <ul>
 *   <li>{@code LOG_WARN} — result is stored but {@code lowConfidence=true} is set on the response</li>
 *   <li>{@code SKIP} — result is discarded; {@code markFailureByFingerprint} is called instead</li>
 * </ul>
 *
 * <p><b>How the Spring AI ChatClient chain is mocked:</b>
 * Spring AI's ChatClient uses a fluent builder API. The chain is:
 * {@code chatClient.prompt()  →  ChatClientRequestSpec}
 * {@code .system(String)      →  ChatClientRequestSpec}
 * {@code .user(String)        →  ChatClientRequestSpec}
 * {@code .call()              →  CallResponseSpec}
 * {@code .chatResponse()      →  ChatResponse}
 * Each step returns an interface we mock independently.
 *
 * <p><b>Note on @Async:</b> {@code DefaultAiRcaAnalyzer.analyze()} is annotated {@code @Async},
 * but without a Spring context the annotation is not proxied — the method runs synchronously
 * in these unit tests, which is exactly what we want.
 */
@Disabled("Unit test — no API key needed, but @Disabled per eval convention. Run manually.")
@ExtendWith(MockitoExtension.class)
class RcaConfidenceThresholdTest {

    // Valid low-confidence JSON the mock AI will "return"
    private static final String LOW_CONFIDENCE_JSON = """
            {
              "analysisConfidence": 0.2,
              "exceptionMessage": "/ by zero",
              "knownPattern": "Arithmetic error in business logic",
              "missingInformation": ["Full heap dump", "Thread dump at time of failure"],
              "rootCauses": [
                {
                  "rank": 1,
                  "title": "Division by zero in calculation path",
                  "likelihood": "High",
                  "category": "Code",
                  "reasoning": "ArithmeticException at PricingService line 58 indicates a divisor reached zero.",
                  "diagnosticStep": "Log the divisor value at PricingService.java:55 before the division.",
                  "estimatedTimeToVerify": "< 5 minutes"
                }
              ]
            }
            """;

    @Mock
    private ChatClient chatClient;

    // Correct inner interface name: ChatClient.ChatClientRequestSpec
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    @Mock
    private ContextCollector contextCollector;

    @Mock
    private ExceptionTimelineStore timelineStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setupMocks() {
        // Wire the fluent chain: chatClient.prompt().system(...).user(...).call().chatResponse()
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        // Build a real ChatResponse wrapping our low-confidence JSON
        AssistantMessage message = new AssistantMessage(LOW_CONFIDENCE_JSON);
        Generation generation = new Generation(message);
        ChatResponse fakeResponse = new ChatResponse(List.of(generation));
        when(callSpec.chatResponse()).thenReturn(fakeResponse);

        // ContextCollector returns a minimal snapshot (content doesn't matter for threshold logic)
        when(contextCollector.collect(any(Throwable.class))).thenReturn(
                new ContextSnapshot(
                        "java.lang.ArithmeticException", "java.lang.ArithmeticException",
                        "/ by zero", "at PricingService.java:58", "log line",
                        "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven"
                )
        );
    }

    /**
     * LOG_WARN: result is stored with {@code lowConfidence=true}.
     * The event must NOT be marked as failed.
     */
    @Test
    void givenLowConfidenceResult_whenActionIsLogWarn_thenResultIsStoredWithFlag() {
        DefaultAiRcaAnalyzer analyzer = analyzerWith(0.7, LowConfidenceAction.LOG_WARN);

        analyzer.analyze(new ArithmeticException("/ by zero"));

        // attachAnalysisByFingerprint must be called — result is stored
        ArgumentCaptor<AiRcaResponse> captor = ArgumentCaptor.forClass(AiRcaResponse.class);
        verify(timelineStore).attachAnalysisByFingerprint(anyString(), captor.capture());

        AiRcaResponse stored = captor.getValue();
        assertThat(stored.analysisConfidence()).isEqualTo(0.2);
        assertThat(stored.lowConfidence())
                .as("lowConfidence flag must be true when score 0.2 < threshold 0.7")
                .isTrue();

        // markFailureByFingerprint must NOT be called — result was stored, not discarded
        verify(timelineStore, never()).markFailureByFingerprint(anyString(), anyString());
    }

    /**
     * SKIP: result is discarded; event is marked failed.
     * {@code attachAnalysisByFingerprint} must NOT be called.
     */
    @Test
    void givenLowConfidenceResult_whenActionIsSkip_thenResultIsDiscarded() {
        DefaultAiRcaAnalyzer analyzer = analyzerWith(0.7, LowConfidenceAction.SKIP);

        analyzer.analyze(new ArithmeticException("/ by zero"));

        verify(timelineStore).markFailureByFingerprint(anyString(), anyString());
        verify(timelineStore, never()).attachAnalysisByFingerprint(anyString(), any());
    }

    /**
     * Threshold disabled (0.0): even a very low confidence result is stored without the flag.
     */
    @Test
    void givenThresholdDisabled_whenConfidenceIsLow_thenResultIsStoredWithoutFlag() {
        DefaultAiRcaAnalyzer analyzer = analyzerWith(0.0, LowConfidenceAction.LOG_WARN);

        analyzer.analyze(new ArithmeticException("/ by zero"));

        ArgumentCaptor<AiRcaResponse> captor = ArgumentCaptor.forClass(AiRcaResponse.class);
        verify(timelineStore).attachAnalysisByFingerprint(anyString(), captor.capture());
        assertThat(captor.getValue().lowConfidence())
                .as("lowConfidence must be false when threshold is disabled (0.0)")
                .isFalse();
    }

    /**
     * Score meets threshold (confidence 0.2 >= threshold 0.1): result stored without flag.
     */
    @Test
    void givenConfidenceMeetsThreshold_thenResultIsStoredWithoutFlag() {
        DefaultAiRcaAnalyzer analyzer = analyzerWith(0.1, LowConfidenceAction.LOG_WARN);

        analyzer.analyze(new ArithmeticException("/ by zero"));

        ArgumentCaptor<AiRcaResponse> captor = ArgumentCaptor.forClass(AiRcaResponse.class);
        verify(timelineStore).attachAnalysisByFingerprint(anyString(), captor.capture());
        assertThat(captor.getValue().lowConfidence())
                .as("lowConfidence must be false when confidence 0.2 >= threshold 0.1")
                .isFalse();
    }

    private DefaultAiRcaAnalyzer analyzerWith(double threshold, LowConfidenceAction action) {
        return new DefaultAiRcaAnalyzer(
                chatClient, contextCollector, objectMapper, timelineStore, threshold, action
        );
    }
}