package io.github.prakharr0.ai.rca.spring.boot.core.context;

import org.springframework.core.SpringVersion;

import java.util.Arrays;
import java.util.stream.Collectors;

import java.lang.management.ManagementFactory;

/**
 * Collects structured diagnostic context from a {@link Throwable}
 * to support AI-based Root Cause Analysis (RCA).
 *
 * <p>
 * This class extracts:
 * <ul>
 *     <li>Exception metadata (type, root cause, message)</li>
 *     <li>Partial stack trace (top 15 frames of root cause)</li>
 *     <li>Recent application logs via {@code LogBuffer}</li>
 *     <li>Runtime and environment metadata (Spring version, Java version, profiles)</li>
 *     <li>Deployment characteristics (packaging, deployment target)</li>
 *     <li>Technology stack detection (database, web stack, build tool)</li>
 * </ul>
 *
 * <h2>Purpose</h2>
 * The collected {@link ContextSnapshot} is intended to provide
 * sufficient contextual information for AI models to generate
 * meaningful diagnostic explanations.
 *
 * <h2>Design Considerations</h2>
 * <ul>
 *     <li>Lightweight and reflection-based environment detection</li>
 *     <li>No hard dependency on specific Spring modules</li>
 *     <li>Safe fallbacks if optional components are absent</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is stateless and therefore thread-safe.
 *
 * @see ContextSnapshot
 */
public class ContextCollector {

    /**
     * Collects contextual diagnostic information from the given exception.
     *
     * <p><b>Extraction Process:</b></p>
     * <ol>
     *     <li>Resolve root cause of the exception</li>
     *     <li>Extract top 15 stack trace elements from root cause</li>
     *     <li>Capture recent logs</li>
     *     <li>Gather runtime metadata (Spring, Java, profiles)</li>
     *     <li>Detect deployment and application characteristics</li>
     * </ol>
     *
     * @param ex the exception to analyze; must not be {@code null}
     * @return a fully populated {@link ContextSnapshot}
     */
    public ContextSnapshot collect(Throwable ex) {

        Throwable root = getRootCause(ex);

        String stack = Arrays.stream(root.getStackTrace())
                .limit(15)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));

        return new ContextSnapshot(
                ex.getClass().getName(),
                root.getClass().getName(),
                root.getMessage(),
                stack,
                LogBuffer.dump(),
                SpringVersion.getVersion(),
                System.getProperty("java.version"),
                System.getProperty("spring.profiles.active", "default"),
                detectPackaging(),
                detectDeployment(),
                detectDatabase(),
                detectWebStack(),
                detectBuildTool()
        );
    }

    /**
     * Traverses the causal chain of an exception to determine the root cause.
     *
     * <p>
     * The root cause is defined as the deepest {@link Throwable}
     * in the causal chain (i.e., the last non-null {@code getCause()}).
     *
     * @param ex the exception to inspect
     * @return the deepest cause in the exception chain
     */
    private Throwable getRootCause(Throwable ex) {
        Throwable result = ex;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    /**
     * Attempts to detect application packaging type.
     *
     * <p>
     * Uses the JVM runtime name as a heuristic to determine
     * whether the application is packaged as a JAR or WAR.
     *
     * @return "jar" if runtime name contains "jar"; otherwise "war"
     */
    private String detectPackaging() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.contains("jar") ? "jar" : "war";
    }

    /**
     * Attempts to detect deployment environment.
     *
     * <p>
     * Uses the {@code HOSTNAME} environment variable as a heuristic
     * to infer whether the application is running in Kubernetes.
     *
     * @return "kubernetes" if hostname suggests K8s/GKE,
     *         otherwise "local"
     */
    private String detectDeployment() {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        if (host.contains("k8s") || host.contains("gke")) return "kubernetes";
        return "local";
    }

    /**
     * Detects whether Spring JDBC is available on the classpath.
     *
     * <p>
     * Uses reflection to check for the presence of
     * {@code org.springframework.jdbc.core.JdbcTemplate}.
     *
     * @return "jdbc" if Spring JDBC is present,
     *         otherwise "none"
     */
    private String detectDatabase() {
        try {
            Class.forName("org.springframework.jdbc.core.JdbcTemplate");
            return "jdbc";
        } catch (ClassNotFoundException e) {
            return "none";
        }
    }

    /**
     * Detects the active web stack based on classpath inspection.
     *
     * <p>
     * Detection order:
     * <ol>
     *     <li>Spring MVC ({@code DispatcherServlet})</li>
     *     <li>Spring WebFlux ({@code DispatcherHandler})</li>
     * </ol>
     *
     * @return "spring-mvc", "webflux", or "none"
     */
    private String detectWebStack() {
        try {
            Class.forName("org.springframework.web.servlet.DispatcherServlet");
            return "spring-mvc";
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.springframework.web.reactive.DispatcherHandler");
                return "webflux";
            } catch (ClassNotFoundException ex) {
                return "none";
            }
        }
    }

    /**
     * Attempts to detect the build tool used to package the application.
     *
     * <p>
     * Checks for common metadata directories:
     * <ul>
     *     <li>{@code /META-INF/maven}</li>
     *     <li>{@code /META-INF/gradle}</li>
     * </ul>
     *
     * @return "maven", "gradle", or "unknown"
     */
    private String detectBuildTool() {
        if (getClass().getResource("/META-INF/maven") != null) {
            return "maven";
        }
        if (getClass().getResource("/META-INF/gradle") != null) {
            return "gradle";
        }
        return "unknown";
    }
}