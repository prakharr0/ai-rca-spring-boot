package io.github.prakharr0.ai.rca.spring.boot.core.rag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a runbook or error-playbook source for RAG-based injection into RCA prompts.
 *
 * <p>Place on the {@code @SpringBootApplication} class. The library loads and chunks the
 * content at startup, embeds each chunk, and retrieves the most relevant sections whenever
 * an exception is analyzed.
 *
 * <p>Supports both classpath and external URL sources:
 * <pre>
 * {@code
 * @RcaRunbook(source = "classpath:runbooks/payments-errors.md")
 * @RcaRunbook(source = "https://wiki.internal/runbooks/db-connectivity")
 * @SpringBootApplication
 * public class PaymentsApplication { }
 * }
 * </pre>
 *
 * <p>Requires an {@code EmbeddingModel} bean. Has no effect if no embedding model is configured.
 * Annotation is repeatable — add one per runbook.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RcaRunbooks.class)
public @interface RcaRunbook {

    /**
     * Resource path or URL of the runbook.
     * Supports Spring resource prefixes: {@code classpath:}, {@code file:}, {@code https://}, etc.
     */
    String source();
}