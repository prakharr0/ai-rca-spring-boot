package io.github.prakharr0.ai.rca.spring.boot.core.rag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container annotation for repeatable {@link RcaRunbook}. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RcaRunbooks {
    RcaRunbook[] value();
}