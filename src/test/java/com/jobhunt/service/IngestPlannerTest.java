package com.jobhunt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobhunt.model.IngestMode;
import com.jobhunt.model.IngestRun;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngestPlannerTest {

    private final IngestPlanner planner = new IngestPlanner(7, 48);
    private final Instant now = Instant.parse("2026-11-20T05:00:00Z");

    private IngestRun run(Instant finished, Instant coveredFrom) {
        IngestRun r = new IngestRun(finished, IngestMode.BACKFILL, coveredFrom, coveredFrom);
        r.succeed(finished, 1, 1, 1, 1, 1, 0);
        return r;
    }

    @Test
    void firstRunBackfillsTheWholeWeek() {
        var p = planner.plan(now, Optional.empty(), false);
        assertThat(p.mode()).isEqualTo(IngestMode.BACKFILL);
        assertThat(p.keepFrom()).isEqualTo(now.minus(Duration.ofDays(7)));
    }

    @Test
    void completeHistoryOnlyAddsRecentJobs() {
        Instant last = now.minus(Duration.ofHours(24));
        var p = planner.plan(now, Optional.of(run(last, now.minus(Duration.ofDays(8)))), true);
        assertThat(p.mode()).isEqualTo(IngestMode.MAINTAIN);
        assertThat(p.keepFrom()).isEqualTo(last.minus(Duration.ofHours(2)));     // since the last run
        assertThat(p.coveredFrom()).isEqualTo(now.minus(Duration.ofDays(8)));    // coverage is carried over
    }

    @Test
    void maintainKeepsAtLeastTheLastDay() {
        var p = planner.plan(now, Optional.of(run(now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofDays(8)))), true);
        assertThat(p.keepFrom()).isEqualTo(now.minus(Duration.ofHours(24)));
    }

    @Test
    void emptyLookbackWindowBackfillsEvenAfterASuccessfulRun() {
        var p = planner.plan(now, Optional.of(run(now.minus(Duration.ofHours(3)), now.minus(Duration.ofDays(8)))), false);
        assertThat(p.mode()).isEqualTo(IngestMode.BACKFILL);
    }

    @Test
    void historyThatDoesNotReachBackSevenDaysYetKeepsBackfilling() {
        var p = planner.plan(now, Optional.of(run(now.minus(Duration.ofHours(3)), now.minus(Duration.ofDays(3)))), true);
        assertThat(p.mode()).isEqualTo(IngestMode.BACKFILL);
    }

    @Test
    void aGapLongerThanTwoDaysBackfillsAgain() {
        var p = planner.plan(now, Optional.of(run(now.minus(Duration.ofDays(4)), now.minus(Duration.ofDays(11)))), true);
        assertThat(p.mode()).isEqualTo(IngestMode.BACKFILL);
        assertThat(p.reason()).contains("gap");
    }
}
