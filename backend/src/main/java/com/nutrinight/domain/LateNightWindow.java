package com.nutrinight.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * The late-night window, evaluated on local wall-clock time: {@code start} inclusive, {@code end} exclusive.
 * With the defaults 22:00-04:00 the window wraps past midnight, so 22:00, 23:00, 00:00 and 03:59 are
 * inside while 21:59 and 04:00 are outside.
 */
public final class LateNightWindow {

    private final LocalTime start;
    private final LocalTime end;

    public LateNightWindow(LocalTime start, LocalTime end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (start.equals(end)) {
            throw new IllegalArgumentException("Late-night window start and end must differ");
        }
    }

    public LocalTime start() {
        return start;
    }

    public LocalTime end() {
        return end;
    }

    public boolean wrapsMidnight() {
        return end.isBefore(start);
    }

    public boolean contains(LocalTime time) {
        if (wrapsMidnight()) {
            return !time.isBefore(start) || time.isBefore(end);
        }
        return !time.isBefore(start) && time.isBefore(end);
    }

    public boolean contains(LocalDateTime dateTime) {
        return contains(dateTime.toLocalTime());
    }

    /**
     * The "analysis day" an order belongs to. Days roll over at the window's end (04:00 by default),
     * so an order at 01:30 on Saturday is attributed to Friday night rather than being split from
     * the orders placed before midnight. For a window that does not wrap midnight the calendar date is used.
     */
    public LocalDate analysisDate(LocalDateTime dateTime) {
        if (wrapsMidnight() && dateTime.toLocalTime().isBefore(end)) {
            return dateTime.toLocalDate().minusDays(1);
        }
        return dateTime.toLocalDate();
    }

    @Override
    public String toString() {
        return start + "-" + end + " (start inclusive, end exclusive)";
    }
}
