package com.jobhunt.service;

import com.jobhunt.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** The time window behind each dashboard view. Windows are always by the date the job was posted. */
public record ViewRange(String label, Instant start, Instant end) {

    private static final Map<String, Duration> ROLLING = Map.of(
            "24h", Duration.ofHours(24), "3d", Duration.ofDays(3), "7d", Duration.ofDays(7), "15d", Duration.ofDays(15));
    private static final Map<String, String> LABELS = Map.of(
            "24h", "Last 24 hours", "3d", "Last 3 days", "7d", "Last 7 days", "15d", "Last 15 days");

    public static ViewRange of(String view, String day, ZoneId zone, Instant now) {
        if (ROLLING.containsKey(view)) {
            return new ViewRange(LABELS.get(view), now.minus(ROLLING.get(view)), now);
        }
        LocalDate d;
        if ("yesterday".equals(view)) {
            d = now.atZone(zone).toLocalDate().minusDays(1);
        } else if ("date".equals(view) && day != null && !day.isBlank()) {
            try {
                d = LocalDate.parse(day);
            } catch (DateTimeParseException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "date must look like 2026-11-18");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "view must be 24h, 3d, 7d, 15d, yesterday or date");
        }
        Instant start = d.atStartOfDay(zone).toInstant();
        return new ViewRange(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).format(d),
                start, d.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
