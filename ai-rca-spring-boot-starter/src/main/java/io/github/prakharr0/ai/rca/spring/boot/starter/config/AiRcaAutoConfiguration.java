package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import io.github.prakharr0.ai.rca.spring.boot.core.chat.RcaChatService;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl.DefaultAiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import io.github.prakharr0.ai.rca.spring.boot.starter.exception.GlobalExceptionHandler;
import io.github.prakharr0.ai.rca.spring.boot.starter.web.AiRcaChatController;
import io.github.prakharr0.ai.rca.spring.boot.starter.web.AiRcaEventsController;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configuration module for the AI Root Cause Analysis (RCA) starter.
 *
 * <p>
 * This configuration conditionally registers beans required for AI-based
 * exception analysis and diagnostic handling. It integrates with Spring Boot’s
 * auto-configuration mechanism and activates components based on classpath
 * and property conditions.
 *
 * <h2>Auto-Configuration Behavior</h2>
 * <ul>
 *     <li>Enabled when Spring AI's {@link ChatClient} is available</li>
 *     <li>Configurable via {@code ai.rca.enabled} property</li>
 *     <li>Supports conditional bean registration</li>
 *     <li>Enables asynchronous analysis execution</li>
 * </ul>
 *
 * <h2>Registered Components</h2>
 * <ul>
 *     <li>{@link ContextCollector} – exception context extraction</li>
 *     <li>{@link DefaultAiRcaAnalyzer} – AI-based root cause analysis</li>
 *     <li>{@link GlobalExceptionHandler} – centralized exception handling</li>
 *     <li>{@link AiRcaEndpoint} – diagnostic API endpoint</li>
 * </ul>
 *
 * <h2>Conditional Activation</h2>
 * The configuration activates only when:
 * <ul>
 *     <li>{@link ChatClient} is present on the classpath</li>
 *     <li>AI RCA is enabled (default: true)</li>
 * </ul>
 *
 * @see AutoConfiguration
 * @see AiRcaAnalyzer
 */
@EnableAsync
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(AiRcaProperties.class)
public class AiRcaAutoConfiguration {

    /**
     * Provides a {@link ContextCollector} bean when none is defined by the user.
     *
     * <p>
     * This bean extracts contextual diagnostic information from exceptions.
     *
     * @return a new {@link ContextCollector} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ContextCollector contextCollector() {
        return new ContextCollector();
    }

    /**
     * Provides the default AI RCA analyzer implementation.
     *
     * <p>
     * The analyzer is constructed using:
     * <ul>
     *     <li>Spring AI {@link ChatClient}</li>
     *     <li>{@link ContextCollector}</li>
     *     <li>{@link ObjectMapper} for structured data processing</li>
     * </ul>
     *
     * @param builder ChatClient builder provided by Spring AI
     * @param collector context collector for diagnostic extraction
     * @param objectMapper Jackson object mapper for JSON processing
     * @return configured {@link DefaultAiRcaAnalyzer}
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultAiRcaAnalyzer aiRcaAnalyzer(
            ChatClient.Builder builder,
            ContextCollector collector,
            ObjectMapper objectMapper,
            ExceptionTimelineStore timelineStore
    ) {
        return new DefaultAiRcaAnalyzer(
                builder.build(),
                collector,
                objectMapper,
                timelineStore
        );
    }

    /**
     * Registers a global exception handler when AI RCA is enabled.
     *
     * <p>
     * The handler intercepts uncaught exceptions and triggers analysis
     * through the {@link AiRcaAnalyzer}.
     *
     * @param analyzer root cause analyzer
     * @return configured {@link GlobalExceptionHandler}
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "ai.rca",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public GlobalExceptionHandler globalExceptionHandler(
            AiRcaAnalyzer analyzer,
            ContextCollector collector,
            ExceptionTimelineStore timelineStore) {
        return new GlobalExceptionHandler(analyzer, collector, timelineStore);
    }

    /**
     * Diagnostic endpoint for AI RCA results.
     *
     * <p>
     * This endpoint allows retrieval of cached analysis results for
     * debugging and observability purposes.
     *
     * @param analyzer AI RCA analyzer
     * @return configured {@link AiRcaEndpoint}
     */
    @Bean
    public AiRcaEndpoint aiRcaEndpoint(
            DefaultAiRcaAnalyzer analyzer) {
        return new AiRcaEndpoint(analyzer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExceptionTimelineStore timelineStore(AiRcaProperties properties) {
        return new ExceptionTimelineStore(properties.getHistorySize());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public AiRcaEventsController aiRcaEventsController(ExceptionTimelineStore timelineStore) {
        return new AiRcaEventsController(timelineStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.rca", name = "chat-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public RcaChatService rcaChatService(
            ChatClient.Builder builder,
            ExceptionTimelineStore timelineStore,
            ObjectMapper objectMapper,
            AiRcaProperties properties
    ) {
        return new RcaChatService(
                builder.build(),
                timelineStore,
                objectMapper,
                properties.getDefaultTimeToleranceSeconds(),
                properties.getChatContextEvents()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.rca", name = "chat-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public AiRcaChatController aiRcaChatController(RcaChatService service, AiRcaProperties properties) {
        return new AiRcaChatController(service, properties.isChatUiEnabled());
    }
}
