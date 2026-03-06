package io.github.prakharr0.ai.rca.spring.boot.core.store;

import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;

import java.time.Instant;
import java.util.Objects;

public class ExceptionOccurrence {

    private final String eventId;
    private final Instant occurredAt;
    private final String exceptionType;
    private final String rootCauseType;
    private final String exceptionMessage;
    private final String fingerprint;
    private final String httpMethod;
    private final String requestPath;
    private final String threadName;

    private volatile AnalysisStatus analysisStatus;
    private volatile AiRcaResponse analysis;
    private volatile String analysisError;

    public ExceptionOccurrence(
            String eventId,
            Instant occurredAt,
            String exceptionType,
            String rootCauseType,
            String exceptionMessage,
            String fingerprint,
            String httpMethod,
            String requestPath,
            String threadName
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.exceptionType = exceptionType;
        this.rootCauseType = rootCauseType;
        this.exceptionMessage = exceptionMessage;
        this.fingerprint = fingerprint;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.threadName = threadName;
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getRootCauseType() {
        return rootCauseType;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getThreadName() {
        return threadName;
    }

    public AnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public AiRcaResponse getAnalysis() {
        return analysis;
    }

    public String getAnalysisError() {
        return analysisError;
    }

    public synchronized void attachAnalysis(AiRcaResponse response) {
        this.analysis = response;
        this.analysisError = null;
        this.analysisStatus = AnalysisStatus.COMPLETED;
    }

    public synchronized void markFailed(String error) {
        this.analysisError = error;
        this.analysisStatus = AnalysisStatus.FAILED;
    }
}
