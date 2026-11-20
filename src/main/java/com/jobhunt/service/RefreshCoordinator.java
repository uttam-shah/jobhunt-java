package com.jobhunt.service;

import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.dto.RefreshResponse;
import com.jobhunt.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Starts updates in the background for the "Fetch now" button, the scheduler and startup catch-up. */
@Service
public class RefreshCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RefreshCoordinator.class);

    private final IngestService ingest;
    private final Clock clock;
    private final Duration cooldown;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ingest");
        t.setDaemon(true);
        return t;
    });
    private volatile Instant lastManual = Instant.EPOCH;

    public RefreshCoordinator(IngestService ingest, JobhuntProperties props, Clock clock) {
        this.ingest = ingest;
        this.clock = clock;
        this.cooldown = Duration.ofSeconds(props.ingest().refreshCooldownSeconds());
    }

    /** The dashboard's "Fetch now": one run at a time, with a cooldown against hammering the boards. */
    public synchronized RefreshResponse manual() {
        if (ingest.isRunning()) {
            return new RefreshResponse(false, true, "already running");
        }
        Duration since = Duration.between(lastManual, clock.instant());
        if (since.compareTo(cooldown) < 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Just updated - try again in " + (cooldown.minus(since).toSeconds() + 1) + "s.");
        }
        lastManual = clock.instant();
        runInBackground("manual button");
        return new RefreshResponse(true, true, null);
    }

    public void runInBackground(String why) {
        executor.submit(() -> {
            try {
                ingest.run(why);
            } catch (RuntimeException e) {
                log.warn("update ({}) did not complete: {}", why, e.getMessage());
            }
        });
    }
}
