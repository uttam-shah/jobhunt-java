package com.jobhunt.service;

import com.jobhunt.model.IngestMode;
import com.jobhunt.model.IngestRun;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides how much history one run must keep. Pure logic (no database, no clock) so it is
 * easy to test:
 *
 * <ul>
 *   <li><b>BACKFILL</b> - the database does not hold a complete {@code lookbackDays} history
 *       (first run, data deleted, or the app was down longer than {@code maxGapHours}):
 *       keep every matching job posted in the last {@code lookbackDays} days.</li>
 *   <li><b>MAINTAIN</b> - the history is complete: keep only what was posted since the last
 *       run (at least the last 24 hours), i.e. "today's" jobs.</li>
 * </ul>
 */
public final class IngestPlanner {

    public record Plan(IngestMode mode, Instant keepFrom, Instant coveredFrom, String reason) {}

    private final int lookbackDays;
    private final Duration maxGap;

    public IngestPlanner(int lookbackDays, int maxGapHours) {
        this.lookbackDays = lookbackDays;
        this.maxGap = Duration.ofHours(maxGapHours);
    }

    /**
     * @param lastGoodRun       most recent successful run, if any
     * @param hasJobsInLookback whether the jobs table holds anything posted inside the lookback window
     */
    public Plan plan(Instant now, Optional<IngestRun> lastGoodRun, boolean hasJobsInLookback) {
        Instant windowStart = now.minus(Duration.ofDays(lookbackDays));
        if (lastGoodRun.isEmpty()) {
            return backfill(windowStart, "no previous successful run");
        }
        IngestRun last = lastGoodRun.get();
        if (!hasJobsInLookback) {
            return backfill(windowStart, "no jobs stored for the last " + lookbackDays + " days");
        }
        if (last.getCoveredFrom() == null || last.getCoveredFrom().isAfter(windowStart)) {
            return backfill(windowStart, "history does not reach back " + lookbackDays + " days yet");
        }
        if (last.getFinishedAt() == null || last.getFinishedAt().isBefore(now.minus(maxGap))) {
            return backfill(windowStart, "last update was more than " + maxGap.toHours() + "h ago (gap in history)");
        }
        Instant sinceLastRun = last.getFinishedAt().minus(Duration.ofHours(2));   // small overlap
        Instant today = now.minus(Duration.ofHours(24));
        Instant keepFrom = sinceLastRun.isBefore(today) ? sinceLastRun : today;
        if (keepFrom.isBefore(windowStart)) {
            keepFrom = windowStart;
        }
        return new Plan(IngestMode.MAINTAIN, keepFrom, last.getCoveredFrom(),
                "last " + lookbackDays + " days already stored - adding recent jobs only");
    }

    private Plan backfill(Instant windowStart, String reason) {
        return new Plan(IngestMode.BACKFILL, windowStart, windowStart, reason);
    }
}
