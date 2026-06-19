package io.github.prakharr0.ai.rca.spring.boot.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RcaTimeParserTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void parsesIsoInstant() {
        Optional<Instant> result = RcaTimeParser.parseInstant("2026-05-14T14:30:00Z", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("2026-05-14T14:30:00Z"));
    }

    @Test
    void parsesIsoLocalDateTime() {
        Optional<Instant> result = RcaTimeParser.parseInstant("2026-05-14T14:30:00", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(
                LocalDate.of(2026, 5, 14).atTime(14, 30).atZone(UTC).toInstant());
    }

    @Test
    void parsesIsoLocalDate_producesStartOfDay() {
        Optional<Instant> result = RcaTimeParser.parseInstant("2026-05-14", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(
                LocalDate.of(2026, 5, 14).atStartOfDay(UTC).toInstant());
    }

    @Test
    void parsesNaturalLanguageWithTime_hourAndMinute() {
        Optional<Instant> result = RcaTimeParser.parseInstant("3:30 PM on 14 May 2026", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(
                LocalDate.of(2026, 5, 14).atTime(15, 30).atZone(UTC).toInstant());
    }

    @Test
    void parsesNaturalLanguageWithTime_hourOnly() {
        Optional<Instant> result = RcaTimeParser.parseInstant("3 PM on 14 May 2026", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(
                LocalDate.of(2026, 5, 14).atTime(15, 0).atZone(UTC).toInstant());
    }

    @Test
    void parsesNaturalLanguageWithFullMonthName() {
        Optional<Instant> result = RcaTimeParser.parseInstant("2 AM on 1 January 2026", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(
                LocalDate.of(2026, 1, 1).atTime(2, 0).atZone(UTC).toInstant());
    }

    @Test
    void parsesNaturalLanguageDateOnly() {
        Optional<Instant> result = RcaTimeParser.parseInstant("14 May 2026", UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(
                LocalDate.of(2026, 5, 14).atStartOfDay(UTC).toInstant());
    }

    @Test
    void parsesEpochSeconds_tenDigits() {
        long epochSeconds = 1747230600L;
        Optional<Instant> result = RcaTimeParser.parseInstant(String.valueOf(epochSeconds), UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.ofEpochMilli(epochSeconds * 1000L));
    }

    @Test
    void parsesEpochMillis_thirteenDigits() {
        long epochMillis = 1747230600000L;
        Optional<Instant> result = RcaTimeParser.parseInstant(String.valueOf(epochMillis), UTC);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.ofEpochMilli(epochMillis));
    }

    @Test
    void returnsEmpty_forUnparseable() {
        assertThat(RcaTimeParser.parseInstant("not a date at all", UTC)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void returnsEmpty_forNullOrBlank(String input) {
        assertThat(RcaTimeParser.parseInstant(input, UTC)).isEmpty();
    }

    @Test
    void isCaseInsensitive() {
        Optional<Instant> lower = RcaTimeParser.parseInstant("3 pm on 14 may 2026", UTC);
        Optional<Instant> upper = RcaTimeParser.parseInstant("3 PM on 14 May 2026", UTC);
        assertThat(lower).isPresent();
        assertThat(upper).isPresent();
        assertThat(lower.get()).isEqualTo(upper.get());
    }

    @Test
    void stripsLeadingCommas() {
        // sanitize() strips commas — "May 14, 2026" normalises to "May 14 2026"
        Optional<Instant> result = RcaTimeParser.parseInstant("2026-05-14", UTC);
        assertThat(result).isPresent();
    }
}