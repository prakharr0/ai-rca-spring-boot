package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl.DefaultAiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.starter.exception.GlobalExceptionHandler;
import tools.jackson.databind.ObjectMapper;

@EnableAsync
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(AiRcaProperties.class)
public class AiRcaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContextCollector contextCollector() {
        return new ContextCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultAiRcaAnalyzer aiRcaAnalyzer(ChatClient.Builder builder, ContextCollector collector, ObjectMapper objectMapper) {
        return new DefaultAiRcaAnalyzer(
                builder.build(),
                collector,
                objectMapper
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ai.rca",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public GlobalExceptionHandler globalExceptionHandler(
            AiRcaAnalyzer analyzer) {
        return new GlobalExceptionHandler(analyzer);
    }

    @Bean
    public AiRcaEndpoint aiRcaEndpoint(
            DefaultAiRcaAnalyzer analyzer) {
        return new AiRcaEndpoint(analyzer);
    }
}
