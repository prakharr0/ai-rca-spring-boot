package io.github.prakharr0.ai.rca.spring.boot.core.store;

import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;

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
}
