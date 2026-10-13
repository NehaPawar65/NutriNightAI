package com.nutrinight.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LateNightWindowTest {

    private final LateNightWindow window = new LateNightWindow(LocalTime.of(22, 0), LocalTime.of(4, 0));

    @ParameterizedTest(name = "{0} -> late night = {1}")
    @CsvSource({
            "21:59:59, false",
            "21:59, false",
            "22:00, true",
            "23:00, true",
            "23:59:59, true",
            "00:00, true",
            "01:30, true",
            "03:59, true",
            "03:59:59, true",
            "04:00, false",
            "04:00:01, false",
            "12:00, false"
    })
    void boundaryCases(String time, boolean expected) {
        assertThat(window.contains(LocalTime.parse(time))).isEqualTo(expected);
    }

    @Test
    void afterMidnightOrdersBelongToThePreviousEvening() {
        LocalDateTime friday2330 = LocalDateTime.of(2026, 7, 3, 23, 30);
        LocalDateTime saturday0130 = LocalDateTime.of(2026, 7, 4, 1, 30);
        LocalDateTime saturday0400 = LocalDateTime.of(2026, 7, 4, 4, 0);

        assertThat(window.analysisDate(friday2330)).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(window.analysisDate(saturday0130)).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(window.analysisDate(saturday0400)).isEqualTo(LocalDate.of(2026, 7, 4));
    }

    @Test
    void crossesMonthAndYearBoundaries() {
        assertThat(window.analysisDate(LocalDateTime.of(2027, 1, 1, 0, 15))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(window.analysisDate(LocalDateTime.of(2026, 8, 1, 3, 59))).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void nonWrappingWindowUsesCalendarDate() {
        LateNightWindow evening = new LateNightWindow(LocalTime.of(18, 0), LocalTime.of(21, 0));
        assertThat(evening.contains(LocalTime.of(18, 0))).isTrue();
        assertThat(evening.contains(LocalTime.of(21, 0))).isFalse();
        assertThat(evening.analysisDate(LocalDateTime.of(2026, 7, 4, 1, 0))).isEqualTo(LocalDate.of(2026, 7, 4));
    }

    @Test
    void rejectsEmptyWindow() {
        assertThatThrownBy(() -> new LateNightWindow(LocalTime.NOON, LocalTime.NOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolverConvertsUtcTimesIntoTheConfiguredZone() {
        OrderTimeResolver resolver = new OrderTimeResolver(ZoneId.of("Asia/Kolkata"), window);

        // 16:30Z = 22:00 IST -> inside (inclusive start)
        var start = resolver.resolve("2026-07-01T16:30:00Z");
        assertThat(start.localHour()).isEqualTo(22);
        assertThat(start.lateNight()).isTrue();

        // 22:29:59Z on 1 July = 03:59:59 IST on 2 July -> inside, attributed to 1 July
        var end = resolver.resolve("2026-07-01T22:29:59Z");
        assertThat(end.lateNight()).isTrue();
        assertThat(end.localDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 2, 3, 59, 59));
        assertThat(end.analysisDate()).isEqualTo(LocalDate.of(2026, 7, 1));

        // 22:30Z = 04:00 IST -> outside (exclusive end)
        assertThat(resolver.resolve("2026-07-01T22:30:00Z").lateNight()).isFalse();
        // 16:29Z = 21:59 IST -> outside
        assertThat(resolver.resolve("2026-07-01T16:29:00Z").lateNight()).isFalse();
    }

    @Test
    void resolverTreatsTimesWithoutOffsetAsLocal() {
        OrderTimeResolver resolver = new OrderTimeResolver(ZoneId.of("Asia/Kolkata"), window);
        assertThat(resolver.resolve("2026-07-02T00:00:00").lateNight()).isTrue();
        assertThat(resolver.resolve("2026-07-02 03:59").lateNight()).isTrue();
        assertThat(resolver.resolve("2026-07-02 04:00:00").lateNight()).isFalse();
        assertThat(resolver.resolve("2026-07-02T00:00:00").analysisDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void resolverRejectsInvalidText() {
        OrderTimeResolver resolver = new OrderTimeResolver(ZoneId.of("Asia/Kolkata"), window);
        assertThatThrownBy(() -> resolver.resolve("yesterday night")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve("2026-13-01T10:00:00")).isInstanceOf(IllegalArgumentException.class);
    }
}
