package io.github.prakharr0.ai.rca.spring.boot.core.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RcaTimeParser {

    private static final Locale LOCALE = Locale.ENGLISH;

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a 'on' d MMM uuuu").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h a 'on' d MMM uuuu").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a 'on' d MMMM uuuu").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h a 'on' d MMMM uuuu").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu h:mm a").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu h a").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu h:mm a").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu h a").toFormatter(LOCALE)
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(LOCALE),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(LOCALE),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private RcaTimeParser() {
    }

    public static Optional<Instant> parseInstant(String value, ZoneId zoneId) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = sanitize(value);

        try {
            return Optional.of(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Optional.of(OffsetDateTime.parse(normalized).toInstant());
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Optional.of(LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zoneId).toInstant());
        } catch (DateTimeParseException ignored) {
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(normalized, formatter);
                return Optional.of(dateTime.atZone(zoneId).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(normalized, formatter);
                return Optional.of(date.atStartOfDay(zoneId).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        if (normalized.matches("^\\d{10,13}$")) {
            long epoch = Long.parseLong(normalized);
            if (normalized.length() == 10) {
                epoch = epoch * 1000L;
            }
            return Optional.of(Instant.ofEpochMilli(epoch));
        }

        return Optional.empty();
    }

    private static String sanitize(String input) {
        return input
                .trim()
                .replace(",", "")
                .replaceAll("(?i)^at\\s+", "")
                .replaceAll("\\s+", " ");
    }
}
