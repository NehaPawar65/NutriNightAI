package com.nutrinight.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Converts raw order-time text into an absolute instant plus the local-time fields used for analysis.
 * Times with an explicit UTC offset (e.g. {@code 2026-07-01T18:30:00Z}) are converted into the analysis zone;
 * times without an offset are interpreted as local time in the analysis zone.
 */
public final class OrderTimeResolver {

    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

    private final ZoneId zone;
    private final LateNightWindow window;

    public OrderTimeResolver(ZoneId zone, LateNightWindow window) {
        this.zone = zone;
        this.window = window;
    }

    public ZoneId zone() {
        return zone;
    }

    public LateNightWindow window() {
        return window;
    }

    /** @throws IllegalArgumentException when the text is not a supported date-time format */
    public ResolvedOrderTime resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("order_time is required");
        }
        String text = raw.trim();
        Instant instant = parseInstant(text);
        return resolve(instant);
    }

    public ResolvedOrderTime resolve(Instant instant) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, zone);
        return new ResolvedOrderTime(instant, local, local.getHour(), window.analysisDate(local), window.contains(local));
    }

    private Instant parseInstant(String text) {
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through to local formats
        }
        for (DateTimeFormatter format : LOCAL_FORMATS) {
            try {
                // atZone resolves DST gaps/overlaps deterministically (earlier offset)
                return ZonedDateTime.of(LocalDateTime.parse(text, format), zone).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException(
                "order_time '" + text + "' is not a valid ISO-8601 date-time (e.g. 2026-07-01T23:15:00 or 2026-07-01T17:45:00Z)");
    }

    public record ResolvedOrderTime(Instant instant, LocalDateTime localDateTime, int localHour,
                                    LocalDate analysisDate, boolean lateNight) {
    }
}
