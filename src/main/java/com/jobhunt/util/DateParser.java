package com.jobhunt.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** ATS date fields come in many shapes; anything unparseable means "undated". */
public final class DateParser {

    private static final List<DateTimeFormatter> OFFSET_FORMATS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][.SS][.S]xx"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]xxx"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]xx"));

    private DateParser() {}

    public static Optional<Instant> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.strip().replace(" UTC", "Z").replace("Z", "+00:00");
        for (DateTimeFormatter f : OFFSET_FORMATS) {
            try {
                return Optional.of(OffsetDateTime.parse(v, f).toInstant());
            } catch (DateTimeParseException ignored) {
                // try the next shape
            }
        }
        try {
            return Optional.of(ZonedDateTime.parse(v).toInstant());
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {   // no zone given: the ATS means UTC
            return Optional.of(LocalDateTime.parse(v).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Optional.of(LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
