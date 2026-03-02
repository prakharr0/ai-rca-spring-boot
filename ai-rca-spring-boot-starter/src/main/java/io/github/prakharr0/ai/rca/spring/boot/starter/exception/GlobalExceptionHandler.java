package io.github.prakharr0.ai.rca.spring.boot.starter.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final static Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AiRcaAnalyzer analyzer;

    public GlobalExceptionHandler(AiRcaAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @ExceptionHandler(Exception.class)
    public void handle(Exception ex) throws Exception {
        log.info("[AI-RCA-SPRING-BOOT-STARTER] Analysis starting for: {}", ex.getLocalizedMessage());
        analyzer.analyze(ex);
        throw ex;
    }
}