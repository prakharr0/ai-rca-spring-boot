package io.github.prakharr0.ai.rca.spring.boot.core.rag;

import io.github.prakharr0.ai.rca.spring.boot.core.util.CosineSimilarity;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads, chunks, and embeds runbook content declared via {@link RcaRunbook} annotations.
 *
 * <p>Initialized after all singleton beans are ready ({@link SmartInitializingSingleton}),
 * so it safely scans the application context for {@link RcaRunbook}-annotated beans.
 *
 * <p>Chunking strategy: split on markdown level-1 and level-2 headings. Chunks longer than
 * {@value #MAX_CHUNK_CHARS} characters are further split at paragraph boundaries.
 * Each chunk is embedded using the configured {@link EmbeddingModel}.
 */
public class  RunbookStore implements SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(RunbookStore.class);
    private static final int MAX_CHUNK_CHARS = 1500;
    private static final Pattern HEADING_SPLIT = Pattern.compile("(?m)^(?=#{1,2} )");

    private final EmbeddingModel embeddingModel;
    private final List<RunbookChunk> chunks = new ArrayList<>();
    private ApplicationContext applicationContext;

    public RunbookStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> runbookBeans = applicationContext.getBeansWithAnnotation(RcaRunbook.class);
        if (runbookBeans.isEmpty()) {
            log.debug("[AI-RCA] No @RcaRunbook annotations found — runbook store is empty");
            return;
        }

        List<String> sources = runbookBeans.values().stream()
                .flatMap(bean -> {
                    Class<?> cls = bean.getClass();
                    // Unwrap CGLIB proxies
                    if (cls.getName().contains("$$")) cls = cls.getSuperclass();
                    return Arrays.stream(cls.getAnnotationsByType(RcaRunbook.class));
                })
                .map(RcaRunbook::source)
                .distinct()
                .toList();

        for (String source : sources) {
            ingestSource(source, applicationContext);
        }

        log.info("[AI-RCA] RunbookStore initialized: {} sources, {} chunks embedded",
                sources.size(), chunks.size());
    }

    private void ingestSource(String source, ResourceLoader loader) {
        try {
            Resource resource = loader.getResource(source);
            if (!resource.exists()) {
                log.warn("[AI-RCA] Runbook source not found, skipping: {}", source);
                return;
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            ingest(source, content);
        } catch (Exception e) {
            log.warn("[AI-RCA] Failed to ingest runbook source '{}': {}", source, e.getMessage());
        }
    }

    /** Package-private for testing — processes raw content without going through a ResourceLoader. */
    void ingest(String source, String content) {
        List<String> rawChunks = splitIntoChunks(content);
        for (String raw : rawChunks) {
            String heading = extractHeading(raw);
            String body = raw.trim();
            if (body.isBlank()) continue;
            float[] embedding = embeddingModel.embed(heading + "\n" + body);
            chunks.add(new RunbookChunk(source, heading, body, embedding));
        }
        log.debug("[AI-RCA] Ingested '{}': {} chunks", source, rawChunks.size());
    }

    /**
     * Retrieves the top-N runbook chunks most relevant to the given query embedding.
     */
    public List<RunbookChunk> findRelevant(float[] queryEmbedding, int topN, double minScore) {
        if (queryEmbedding == null || chunks.isEmpty()) return List.of();

        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, CosineSimilarity.compute(queryEmbedding, chunk.embedding())))
                .filter(sc -> sc.score() >= minScore)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, topN))
                .map(ScoredChunk::chunk)
                .toList();
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    private List<String> splitIntoChunks(String content) {
        String[] sections = HEADING_SPLIT.split(content);
        List<String> result = new ArrayList<>();
        for (String section : sections) {
            if (section.length() <= MAX_CHUNK_CHARS) {
                result.add(section);
            } else {
                // Further split long sections at double-newline (paragraph) boundaries
                String[] paragraphs = section.split("\n\n");
                StringBuilder current = new StringBuilder();
                String sectionHeading = extractHeading(section);
                for (String para : paragraphs) {
                    if (current.length() + para.length() > MAX_CHUNK_CHARS && !current.isEmpty()) {
                        result.add(current.toString());
                        current = new StringBuilder(sectionHeading).append("\n");
                    }
                    current.append(para).append("\n\n");
                }
                if (!current.toString().isBlank()) result.add(current.toString());
            }
        }
        return result;
    }

    private String extractHeading(String chunk) {
        String trimmed = chunk.trim();
        int newline = trimmed.indexOf('\n');
        String firstLine = newline >= 0 ? trimmed.substring(0, newline) : trimmed;
        return firstLine.startsWith("#") ? firstLine.replaceFirst("^#+\\s*", "") : "";
    }

    private record ScoredChunk(RunbookChunk chunk, double score) {}
}