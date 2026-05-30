package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
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

    private static final Logger log = LoggerFactory.getLogger(AiRcaAutoConfiguration.class);

    /** Library default temperature — low for deterministic JSON schema compliance. */
    private static final double DEFAULT_TEMPERATURE = 0.1;
    /**
     * Library default max output tokens.
     *
     * <p>A well-formed 3-hypothesis RCA JSON at temperature 0.1 uses ~500–800 output tokens.
     * 8192 gives 5× headroom and protects against mid-JSON truncation if the model is
     * more verbose than expected. The real guard against verbosity is the low temperature
     * default — this is purely a safety ceiling.
     */
    private static final int DEFAULT_MAX_TOKENS = 8192;

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
     * <p>Temperature and max-token resolution follows this priority order:
     * <ol>
     *   <li>{@code ai.rca.temperature} / {@code ai.rca.max-tokens} — explicit library override</li>
     *   <li>{@code spring.ai.<provider>.chat.options.temperature} — user's Spring AI provider config</li>
     *   <li>Library defaults ({@value #DEFAULT_TEMPERATURE} / {@value #DEFAULT_MAX_TOKENS}) —
     *       applied only when neither of the above is configured</li>
     * </ol>
     *
     * <p>{@link ChatClient.Builder} is prototype-scoped in Spring AI, so mutating this builder
     * via {@code defaultOptions()} only affects the RCA ChatClient — other ChatClient beans
     * (e.g. the chat service) receive their own independent builder instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultAiRcaAnalyzer aiRcaAnalyzer(
            ChatClient.Builder builder,
            ObjectProvider<ChatModel> chatModelProvider,
            ContextCollector collector,
            ObjectMapper objectMapper,
            ExceptionTimelineStore timelineStore,
            AiRcaProperties properties
    ) {
        applyRcaDefaults(builder, chatModelProvider, properties);
        return new DefaultAiRcaAnalyzer(builder.build(), collector, objectMapper, timelineStore);
    }

    /**
     * Applies temperature and max-token defaults to the RCA ChatClient builder.
     *
     * <p>Each value is resolved independently:
     * <ul>
     *   <li>If {@code ai.rca.*} property is set → use it (explicit library override)</li>
     *   <li>Else if the provider's ChatModel has the option configured → leave it alone</li>
     *   <li>Else → apply the library default for deterministic JSON output</li>
     * </ul>
     *
     * <p>{@link ObjectProvider} is used instead of direct {@code ChatModel} injection to
     * gracefully handle projects with multiple AI providers on the classpath. If the primary
     * ChatModel cannot be unambiguously resolved, provider inspection is skipped and library
     * defaults are applied unconditionally (still safe — they are only ChatClient-level defaults
     * that the user can override via per-call options or by setting {@code ai.rca.*} properties).
     */
    private void applyRcaDefaults(ChatClient.Builder builder, ObjectProvider<ChatModel> chatModelProvider, AiRcaProperties properties) {
        ChatModel chatModel = chatModelProvider.getIfUnique();
        ChatOptions providerOptions = chatModel != null ? chatModel.getDefaultOptions() : null;

        // Temperature: safe to inspect providerOptions — Spring AI providers use nullable Double
        // for temperature (no hardcoded Java default), so null reliably means "user didn't set it".
        Double temperature = resolveTemperature(properties.getTemperature(), providerOptions);

        Integer maxTokens = resolveMaxTokens(properties.getMaxTokens(), providerOptions);

        ChatOptions.Builder optionsBuilder = ChatOptions.builder();
        if (temperature != null) optionsBuilder.temperature(temperature);
        if (maxTokens != null) optionsBuilder.maxTokens(maxTokens);
        builder.defaultOptions(optionsBuilder.build());
    }

    /**
     * Resolves temperature using three-tier priority.
     *
     * <p>Temperature is safe to null-check on providerOptions because Spring AI providers
     * declare it as a nullable {@code Double} with no Java-level default. Null means
     * the user did not configure it via {@code spring.ai.<provider>.chat.options.temperature}.
     *
     * @return the temperature to apply, or {@code null} if the provider already has one set
     */
    private Double resolveTemperature(Double libraryOverride, ChatOptions providerOptions) {
        if (libraryOverride != null) {
            log.debug("[AI-RCA] Using ai.rca.temperature override: {}", libraryOverride);
            return libraryOverride;
        }
        Double providerTemp = providerOptions != null ? providerOptions.getTemperature() : null;
        if (providerTemp != null) {
            log.debug("[AI-RCA] Provider has temperature configured ({}), leaving it alone", providerTemp);
            return null;
        }
        log.debug("[AI-RCA] No temperature configured — applying library default: {}", DEFAULT_TEMPERATURE);
        return DEFAULT_TEMPERATURE;
    }

    /**
     * Resolves maxTokens using a comparison strategy instead of null-checking.
     *
     * <p>Unlike temperature, maxTokens cannot be safely null-checked on providerOptions because
     * some providers (e.g. Anthropic) hardcode a Java-level default (1024) since their API
     * requires the field. A non-null value here does not reliably mean the user configured it.
     *
     * <p>Resolution logic:
     * <ul>
     *   <li>{@code ai.rca.max-tokens} set → use it (explicit override, always wins)</li>
     *   <li>Provider value >= library default → user configured something intentionally large; leave it alone</li>
     *   <li>Provider value < library default (or null) → apply library default as a safe ceiling</li>
     * </ul>
     *
     * <p>The edge case: if the user intentionally set a value lower than our default
     * (e.g. {@code spring.ai.anthropic.chat.options.max-tokens=500} for cost control),
     * our default overrides it. In that case, the user should set {@code ai.rca.max-tokens=500}
     * to express explicit intent that we respect unconditionally.
     *
     * @return the maxTokens value to apply, or {@code null} if the provider already has equal/higher
     */
    private Integer resolveMaxTokens(Integer libraryOverride, ChatOptions providerOptions) {
        if (libraryOverride != null) {
            log.debug("[AI-RCA] Using ai.rca.max-tokens override: {}", libraryOverride);
            return libraryOverride;
        }
        Integer providerMax = providerOptions != null ? providerOptions.getMaxTokens() : null;
        if (providerMax != null && providerMax >= DEFAULT_MAX_TOKENS) {
            log.debug("[AI-RCA] Provider max-tokens ({}) >= library default ({}), leaving it alone",
                    providerMax, DEFAULT_MAX_TOKENS);
            return null;
        }
        log.debug("[AI-RCA] Provider max-tokens ({}) < library default ({}) — applying library default",
                providerMax, DEFAULT_MAX_TOKENS);
        return DEFAULT_MAX_TOKENS;
    }

    // Kept for potential future use with other safely-nullable options (topP, frequencyPenalty, etc.)
    private <T> T resolveOption(T libraryOverride, T providerValue, T libraryDefault, String optionName) {
        if (libraryOverride != null) {
            log.debug("[AI-RCA] Using ai.rca.{} override: {}", optionName, libraryOverride);
            return libraryOverride;
        }
        if (providerValue != null) {
            log.debug("[AI-RCA] Provider has {} configured ({}), leaving it alone", optionName, providerValue);
            return null;
        }
        log.debug("[AI-RCA] No {} configured — applying library default: {}", optionName, libraryDefault);
        return libraryDefault;
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
