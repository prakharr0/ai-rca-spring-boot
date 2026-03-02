package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl.DefaultAiRcaAnalyzer;

import java.util.Map;

@Component
@Endpoint(id = "ai-rca")
public class AiRcaEndpoint {

    private final DefaultAiRcaAnalyzer analyzer;

    public AiRcaEndpoint(DefaultAiRcaAnalyzer aiRcaAnalyzer) {
        this.analyzer = aiRcaAnalyzer;
    }

    @ReadOperation
    public Map<String, ?> results() {
        return analyzer.getResults();
    }
}
