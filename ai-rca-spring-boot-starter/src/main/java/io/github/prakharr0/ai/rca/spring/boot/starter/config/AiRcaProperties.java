package io.github.prakharr0.ai.rca.spring.boot.starter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AI Root Cause Analysis (RCA) starter.
 *
 * <p>
 * Properties are bound from the {@code ai.rca} namespace in application
 * configuration files.
 *
 * <h2>Configuration Example</h2>
 * <pre>
 * ai:
 *   rca:
 *     enabled: true
 * </pre>
 *
 * <h2>Properties</h2>
 * <ul>
 *     <li>{@code enabled} – toggles AI RCA functionality (default: true)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * This class is registered via {@code @EnableConfigurationProperties}
 * and automatically bound by Spring Boot.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "ai.rca")
public class AiRcaProperties {

    /**
     * Enables or disables AI root cause analysis.
     *
     * <p>
     * When disabled, diagnostic analysis and related handlers should
     * not be invoked.
     */
    private boolean enabled = true;

    /**
     * Maximum in-memory exception events retained in the timeline.
     */
    private int historySize = 500;

    /**
     * Enables chat endpoint API.
     */
    private boolean chatEnabled = true;

    /**
     * Enables lightweight chat UI page.
     */
    private boolean chatUiEnabled = true;

    /**
     * Default tolerance in seconds when resolving natural language event times.
     */
    private int defaultTimeToleranceSeconds = 1800;

    /**
     * Number of most recent events to provide as context in generic chat questions.
     */
    private int chatContextEvents = 20;
}
