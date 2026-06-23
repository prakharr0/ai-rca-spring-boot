package io.github.prakharr0.ai.rca.spring.boot.core.store;

import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.github.prakharr0.ai.rca.spring.boot.core.util.CosineSimilarity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExceptionTimelineStore {

    private final int maxEvents;
    private final Deque<ExceptionOccurrence> events = new ArrayDeque<>();
    private final Map<String, List<ExceptionOccurrence>> eventsByFingerprint = new HashMap<>();

    public ExceptionTimelineStore(int maxEvents) {
        this.maxEvents = Math.max(50, maxEvents);
    }

    public synchronized void add(ExceptionOccurrence occurrence) {
        if (events.size() >= maxEvents) {
            ExceptionOccurrence evicted = events.removeFirst();
            List<ExceptionOccurrence> indexed = eventsByFingerprint.get(evicted.getFingerprint());
            if (indexed != null) {
                indexed.removeIf(item -> item.getEventId().equals(evicted.getEventId()));
                if (indexed.isEmpty()) {
                    eventsByFingerprint.remove(evicted.getFingerprint());
                }
            }
        }

        events.addLast(occurrence);
        eventsByFingerprint.computeIfAbsent(occurrence.getFingerprint(), key -> new ArrayList<>()).add(occurrence);
    }

    public synchronized List<ExceptionOccurrence> latest(int limit) {
        int safeLimit = Math.max(1, limit);
        List<ExceptionOccurrence> all = new ArrayList<>(events);
        int start = Math.max(0, all.size() - safeLimit);
        List<ExceptionOccurrence> slice = all.subList(start, all.size());
        List<ExceptionOccurrence> reversed = new ArrayList<>(slice);
        reversed.sort(Comparator.comparing(ExceptionOccurrence::getOccurredAt).reversed());
        return reversed;
    }

    public synchronized List<ExceptionOccurrence> findBetween(Instant from, Instant to, int limit) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now().plus(Duration.ofDays(3650)) : to;
        int safeLimit = Math.max(1, limit);

        List<ExceptionOccurrence> matched = new ArrayList<>();
        for (ExceptionOccurrence event : events) {
            Instant occurredAt = event.getOccurredAt();
            if ((occurredAt.equals(start) || occurredAt.isAfter(start))
                    && (occurredAt.equals(end) || occurredAt.isBefore(end))) {
                matched.add(event);
            }
        }

        matched.sort(Comparator.comparing(ExceptionOccurrence::getOccurredAt).reversed());
        if (matched.size() <= safeLimit) {
            return matched;
        }
        return new ArrayList<>(matched.subList(0, safeLimit));
    }

    public synchronized ExceptionOccurrence findAt(Instant target, Duration tolerance) {
        if (target == null || events.isEmpty()) {
            return null;
        }

        Duration maxDrift = tolerance == null ? Duration.ofMinutes(30) : tolerance;
        ExceptionOccurrence nearest = null;
        long nearestDiffMs = Long.MAX_VALUE;

        for (ExceptionOccurrence event : events) {
            long diffMs = Math.abs(Duration.between(event.getOccurredAt(), target).toMillis());
            if (diffMs <= maxDrift.toMillis() && diffMs < nearestDiffMs) {
                nearestDiffMs = diffMs;
                nearest = event;
            }
        }

        return nearest;
    }

    public synchronized void attachAnalysisByFingerprint(String fingerprint, AiRcaResponse response) {
        List<ExceptionOccurrence> matched = eventsByFingerprint.getOrDefault(fingerprint, List.of());
        for (ExceptionOccurrence occurrence : matched) {
            occurrence.attachAnalysis(response);
        }
    }

    public synchronized void markFailureByFingerprint(String fingerprint, String error) {
        List<ExceptionOccurrence> matched = eventsByFingerprint.getOrDefault(fingerprint, List.of());
        for (ExceptionOccurrence occurrence : matched) {
            occurrence.markFailed(error);
        }
    }

    public synchronized void attachEmbeddingByFingerprint(String fingerprint, float[] embedding) {
        List<ExceptionOccurrence> matched = eventsByFingerprint.getOrDefault(fingerprint, List.of());
        for (ExceptionOccurrence occurrence : matched) {
            occurrence.setEmbedding(embedding);
        }
    }

    /**
     * Finds the top-N past occurrences most semantically similar to the given query embedding.
     *
     * <p>Deduplicates by fingerprint — only the occurrence with the highest similarity score
     * per fingerprint group is considered. The fingerprint {@code excludeFingerprint} is skipped
     * entirely (used to exclude the current exception from its own search results).
     *
     * <p>Only occurrences that have been embedded (non-null embedding) are eligible.
     *
     * @param queryEmbedding     the embedding of the new exception to find similar incidents for
     * @param excludeFingerprint fingerprint of the current exception to exclude from results
     * @param topN               maximum number of results to return
     * @param minScore           minimum cosine similarity score to include a result
     * @return list of similar past occurrences, sorted by similarity descending
     */
    public synchronized List<SimilarOccurrence> findSimilar(
            float[] queryEmbedding, String excludeFingerprint, int topN, double minScore) {

        if (queryEmbedding == null || events.isEmpty()) return List.of();

        // Best score per fingerprint (dedup same exception type)
        Map<String, SimilarOccurrence> bestPerFingerprint = new HashMap<>();

        for (ExceptionOccurrence occurrence : events) {
            String fp = occurrence.getFingerprint();
            if (fp.equals(excludeFingerprint)) continue;
            float[] embedding = occurrence.getEmbedding();
            if (embedding == null) continue;

            double score = CosineSimilarity.compute(queryEmbedding, embedding);
            if (score < minScore) continue;

            SimilarOccurrence candidate = new SimilarOccurrence(occurrence, score);
            bestPerFingerprint.merge(fp, candidate,
                    (existing, next) -> next.score() > existing.score() ? next : existing);
        }

        return bestPerFingerprint.values().stream()
                .sorted(Comparator.comparingDouble(SimilarOccurrence::score).reversed())
                .limit(Math.max(1, topN))
                .toList();
    }

    public record SimilarOccurrence(ExceptionOccurrence occurrence, double score) {}
}
