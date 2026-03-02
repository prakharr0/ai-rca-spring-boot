package io.github.prakharr0.ai.rca.spring.boot.core.context;

import org.springframework.core.SpringVersion;

import java.util.Arrays;
import java.util.stream.Collectors;

import java.lang.management.ManagementFactory;

public class ContextCollector {

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

    private Throwable getRootCause(Throwable ex) {
        Throwable result = ex;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private String detectPackaging() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.contains("jar") ? "jar" : "war";
    }

    private String detectDeployment() {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        if (host.contains("k8s") || host.contains("gke")) return "kubernetes";
        return "local";
    }

    private String detectDatabase() {
        try {
            Class.forName("org.springframework.jdbc.core.JdbcTemplate");
            return "jdbc";
        } catch (ClassNotFoundException e) {
            return "none";
        }
    }

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