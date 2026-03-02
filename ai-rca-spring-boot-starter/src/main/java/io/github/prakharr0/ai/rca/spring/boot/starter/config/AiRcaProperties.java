package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "ai.rca")
public class AiRcaProperties {
    private boolean enabled = true;
}