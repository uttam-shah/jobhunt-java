package com.jobhunt.service;

import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.repository.IngestRunRepository;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/** Daily update at {@code ingest.run-at}, plus a catch-up at startup if today's run was missed. */
@Component
public class UpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(UpdateScheduler.class);

    private final TaskScheduler scheduler;
    private final RefreshCoordinator refresh;
    private final IngestRunRepository runs;
    private final JobhuntProperties props;
    private final Clock clock;

    public UpdateScheduler(TaskScheduler scheduler, RefreshCoordinator refresh, IngestRunRepository runs,
                           JobhuntProperties props, Clock clock) {
        this.scheduler = scheduler;
        this.refresh = refresh;
        this.runs = runs;
        this.props = props;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ZoneId zone = ZoneId.of(props.ingest().timezone());
        LocalTime at = LocalTime.parse(props.ingest().runAt());
        scheduler.schedule(() -> refresh.runInBackground("scheduled"),
                new CronTrigger("0 " + at.getMinute() + " " + at.getHour() + " * * *", zone));
        log.info("daily update scheduled at {} {}", props.ingest().runAt(), zone);
        catchUp(zone, at);
    }

    /** Server started after today's run time (or never ran): update now. */
    private void catchUp(ZoneId zone, LocalTime at) {
        ZonedDateTime now = clock.instant().atZone(zone);
        var last = runs.findTopByOkTrueOrderByIdDesc();
        if (last.isEmpty()) {
            refresh.runInBackground("first run");
        } else if (!now.toLocalTime().isBefore(at)
                && last.get().getFinishedAt().atZone(zone).toLocalDate().isBefore(now.toLocalDate())) {
            refresh.runInBackground("catch-up");
        }
    }
}
