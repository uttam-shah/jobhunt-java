package com.jobhunt.fetch;

import java.time.Duration;
import java.time.Instant;

/** What the current run needs from a fetcher. */
public record FetchContext(Instant now, Instant keepFrom) {

    /** Whole days between keepFrom and now, rounded up (at least 1). */
    public int windowDays() {
        long hours = Math.max(1, Duration.between(keepFrom, now).toHours());
        return (int) Math.max(1, Math.min(30, (hours + 23) / 24));
    }
}
