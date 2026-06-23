package io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.LowConfidenceAction;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AnalysisMetadata;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.github.prakharr0.ai.rca.spring.boot.core.rag.RunbookChunk;
import io.github.prakharr0.ai.rca.spring.boot.core.rag.RunbookStore;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore.SimilarOccurrence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ExceptionFingerprint;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.SystemPrompts;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.UserPromptBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultAiRcaAnalyzer implements AiRcaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiRcaAnalyzer.class);

    private final ChatClient chatClient;
    private final ContextCollector collector;
    private final ObjectMapper objectMapper;
    private final ExceptionTimelineStore timelineStore;
    private final double minConfidence;
    private final LowConfidenceAction lowConfidenceAction;
    private final boolean ragEnabled;
    private final int ragTopK;
    private final double ragMinSimilarityScore;

    /** Nullable — only present when an EmbeddingModel bean is configured. */
    private final EmbeddingModel embeddingModel;

    /** Nullable — only present when @RcaRunbook annotation is detected + EmbeddingModel exists. */
    private final RunbookStore runbookStore;

    private final Map<String, AiRcaResponse> cache = new ConcurrentHashMap<>();

    public DefaultAiRcaAnalyzer(
            ChatClient chatClient,
            ContextCollector collector,
            ObjectMapper objectMapper,
            ExceptionTimelineStore timelineStore,
            double minConfidence,
            LowConfidenceAction lowConfidenceAction,
            boolean ragEnabled,
            int ragTopK,
            double ragMinSimilarityScore,
            EmbeddingModel embeddingModel,
            RunbookStore runbookStore
    ) {
        this.chatClient = chatClient;
        this.collector = collector;
        this.objectMapper = objectMapper;
        this.timelineStore = timelineStore;
        this.minConfidence = minConfidence;
        this.lowConfidenceAction = lowConfidenceAction;
        this.ragEnabled = ragEnabled;
        this.ragTopK = ragTopK;
        this.ragMinSimilarityScore = ragMinSimilarityScore;
        this.embeddingModel = embeddingModel;
        this.runbookStore = runbookStore;
    }

    @Async
    @Override
    public void analyze(Throwable throwable) {
        ContextSnapshot snapshot = collector.collect(throwable);
        String fingerprint = ExceptionFingerprint.generate(snapshot);

        AiRcaResponse response;
        if (cache.containsKey(fingerprint)) {
            response = cache.get(fingerprint);
        } else {
            float[] embedding = generateEmbedding(snapshot);
            if (embedding != null) {
                timelineStore.attachEmbeddingByFingerprint(fingerprint, embedding);
            }
            String similarIncidentsBlock = buildSimilarIncidentsBlock(embedding, fingerprint);
            String runbookBlock = buildRunbookBlock(embedding);
            String userPrompt = UserPromptBuilder.build(snapshot, similarIncidentsBlock, runbookBlock);

            ChatResponse chatResponse = chatClient.prompt()
                    .system(SystemPrompts.SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            if (chatResponse == null || chatResponse.getResult() == null) {
                log.warn("AI Analysis Failed for {}", throwable.getMessage(), throwable.getCause());
                timelineStore.markFailureByFingerprint(fingerprint, "AI response was empty");
                return;
            }

            String content = stripMarkdown(chatResponse.getResult().getOutput().getText());
            AnalysisMetadata metadata = extractMetadata(chatResponse);

            try {
                response = objectMapper.readValue(content, AiRcaResponse.class)
                        .withMetadata(metadata);
            } catch (Exception e) {
                log.warn("[AI-RCA-SPRING-BOOT-STARTER] Analysis results for: {}\n{}", throwable.getLocalizedMessage(), content);
                timelineStore.markFailureByFingerprint(fingerprint, "AI response could not be parsed");
                return;
            }

            boolean isLowConfidence = minConfidence > 0.0 && response.analysisConfidence() < minConfidence;
            if (isLowConfidence) {
                log.warn("[AI-RCA] Low confidence result: score={} threshold={} fingerprint={}",
                        response.analysisConfidence(), minConfidence, fingerprint);
                if (lowConfidenceAction == LowConfidenceAction.SKIP) {
                    timelineStore.markFailureByFingerprint(fingerprint,
                            "Confidence %.2f below threshold %.2f — skipped".formatted(
                                    response.analysisConfidence(), minConfidence));
                    return;
                }
            }
            response = response.withLowConfidenceFlag(isLowConfidence);
        }

        AiRcaResponse finalResponse = response;
        cache.computeIfAbsent(fingerprint, k -> finalResponse);
        timelineStore.attachAnalysisByFingerprint(fingerprint, response);
        log.warn("[AI-RCA-SPRING-BOOT-STARTER] Analysis results for: {}\n{}", throwable.getLocalizedMessage(),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

    public Map<String, AiRcaResponse> getResults() {
        return cache;
    }

    // ── RAG helpers ──────────────────────────────────────────────────────────

    private float[] generateEmbedding(ContextSnapshot snapshot) {
        if (!ragEnabled && (runbookStore == null || runbookStore.isEmpty())) return null;
        if (embeddingModel == null) return null;
        try {
            String text = snapshot.rootCauseType() + ": " + snapshot.rootCauseMessage()
                    + "\n" + snapshot.stackTrace();
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.debug("[AI-RCA] Embedding generation failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildSimilarIncidentsBlock(float[] embedding, String currentFingerprint) {
        if (!ragEnabled || embedding == null) return null;
        List<SimilarOccurrence> similar = timelineStore.findSimilar(
                embedding, currentFingerprint, ragTopK, ragMinSimilarityScore);
        if (similar.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (SimilarOccurrence s : similar) {
            var occ = s.occurrence();
            sb.append("---\n")
              .append("Similarity score: ").append("%.2f".formatted(s.score())).append("\n")
              .append("Exception: ").append(occ.getExceptionType()).append("\n")
              .append("Root cause: ").append(occ.getRootCauseType()).append("\n")
              .append("Message: ").append(occ.getExceptionMessage()).append("\n")
              .append("Occurred at: ").append(Instant.ofEpochMilli(occ.getOccurredAt().toEpochMilli())).append("\n");
            if (occ.getAnalysis() != null && !occ.getAnalysis().rootCauses().isEmpty()) {
                var top = occ.getAnalysis().rootCauses().getFirst();
                sb.append("Previously diagnosed as: ").append(top.title()).append(" (").append(top.likelihood()).append(")\n");
                if (top.proposedFix() != null) {
                    sb.append("Fix applied: ").append(top.proposedFix()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String buildRunbookBlock(float[] embedding) {
        if (runbookStore == null || runbookStore.isEmpty() || embedding == null) return null;
        List<RunbookChunk> chunks = runbookStore.findRelevant(embedding, 2, 0.3);
        if (chunks.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (RunbookChunk chunk : chunks) {
            sb.append("--- Source: ").append(chunk.source());
            if (!chunk.heading().isBlank()) sb.append(" / ").append(chunk.heading());
            sb.append(" ---\n").append(chunk.content().strip()).append("\n\n");
        }
        return sb.toString();
    }

    // ── Metadata / formatting helpers ────────────────────────────────────────

    private AnalysisMetadata extractMetadata(ChatResponse chatResponse) {
        try {
            Usage usage = chatResponse.getMetadata().getUsage();
            Integer prompt = usage.getPromptTokens();
            Integer completion = usage.getCompletionTokens();
            Integer total = usage.getTotalTokens();
            return new AnalysisMetadata(
                    prompt.longValue(),
                    completion.longValue(),
                    total.longValue()
            );
        } catch (Exception e) {
            log.debug("[AI-RCA] Could not extract token usage from ChatResponse metadata: {}", e.getMessage());
            return new AnalysisMetadata(null, null, null);
        }
    }

    private String stripMarkdown(String content) {
        if (content == null) return null;
        String stripped = content.trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceAll("^```(?:json)?\\s*", "");
            stripped = stripped.replaceAll("\\s*```$", "");
        }
        return stripped.trim();
    }
}