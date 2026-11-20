package com.jobhunt.service;

import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.fetch.AdzunaFetcher;
import com.jobhunt.fetch.FetchContext;
import com.jobhunt.model.Company;
import com.jobhunt.model.IngestRun;
import com.jobhunt.model.Job;
import com.jobhunt.model.RawJob;
import com.jobhunt.repository.IngestRunRepository;
import com.jobhunt.repository.JobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The update: check whether the last 7 days are stored (IngestPlanner), fetch every board, keep
 * matching jobs inside the planned window, store them in PostgreSQL, delete anything older than
 * the retention period. One run at a time.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final CompanyCatalog catalog;
    private final BoardFetchService fetcher;
    private final AdzunaFetcher adzuna;
    private final JobClassifier classifier;
    private final JobStore store;
    private final IngestRunRepository runs;
    private final JobRepository jobs;
    private final JobhuntProperties props;
    private final IngestPlanner planner;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public IngestService(CompanyCatalog catalog, BoardFetchService fetcher, AdzunaFetcher adzuna,
                         JobClassifier classifier, JobStore store, IngestRunRepository runs,
                         JobRepository jobs, JobhuntProperties props, Clock clock) {
        this.catalog = catalog;
        this.fetcher = fetcher;
        this.adzuna = adzuna;
        this.classifier = classifier;
        this.store = store;
        this.runs = runs;
        this.jobs = jobs;
        this.props = props;
        this.clock = clock;
        this.planner = new IngestPlanner(props.ingest().lookbackDays(), props.ingest().maxGapHours());
    }

    public boolean isRunning() {
        return lock.isLocked();
    }

    /** Runs one update. Throws IllegalStateException if another is active or nothing could be fetched. */
    public IngestRun run(String why) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("an update is already running");
        }
        try {
            return execute(why);
        } finally {
            lock.unlock();
        }
    }

    private IngestRun execute(String why) {
        Instant now = clock.instant();
        Instant lookbackStart = now.minus(Duration.ofDays(props.ingest().lookbackDays()));
        IngestPlanner.Plan plan = planner.plan(now, runs.findTopByOkTrueOrderByIdDesc(),
                jobs.existsByPostedAtGreaterThanEqual(lookbackStart));
        log.info("update starting ({}): {} - {}, keeping jobs posted since {}", why, plan.mode(), plan.reason(), plan.keepFrom());

        IngestRun run = runs.save(new IngestRun(now, plan.mode(), plan.keepFrom(), plan.coveredFrom()));
        try {
            List<Company> companies = catalog.load();
            FetchContext ctx = new FetchContext(now, plan.keepFrom());
            BoardFetchService.Result fetched = fetcher.fetchAll(companies, ctx);
            List<RawJob> raw = new ArrayList<>(fetched.jobs());
            int postings = raw.size();
            if (adzuna.enabled()) {
                List<RawJob> extra = adzuna.fetch(adzunaPlaces(), ctx.windowDays() + 1);
                log.info("adzuna: {} postings", extra.size());
                raw.addAll(extra);
                postings += extra.size();
            }
            if (raw.isEmpty()) {
                throw new IllegalStateException("no postings fetched from any board - network down?");
            }

            List<Job> keep = screen(raw, plan.keepFrom());
            StoreResult saved = store.saveAndPurge(keep, now.minus(Duration.ofDays(props.ingest().retentionDays())));
            run.succeed(clock.instant(), fetched.boards(), fetched.boardsWithJobs(), postings,
                    keep.size(), saved.newJobs(), saved.purged());
            log.info("update done: {} postings from {}/{} boards -> {} kept ({} new), {} old deleted",
                    postings, fetched.boardsWithJobs(), fetched.boards(), keep.size(), saved.newJobs(), saved.purged());
        } catch (RuntimeException e) {
            run.fail(clock.instant(), e.getMessage());
            log.error("update failed: {}", e.getMessage());
            runs.save(run);
            throw e;
        }
        return runs.save(run);
    }

    /** Filter + de-duplicate by id (the same posting can appear twice in one fetch). */
    private List<Job> screen(List<RawJob> raw, Instant keepFrom) {
        Map<String, Job> keep = new LinkedHashMap<>();
        for (RawJob r : raw) {
            Optional<Job> j = classifier.evaluate(r);
            if (j.isPresent() && !j.get().getPostedAt().isBefore(keepFrom)) {
                keep.putIfAbsent(j.get().getId(), j.get());
            }
        }
        return new ArrayList<>(keep.values());
    }

    private List<String> adzunaPlaces() {
        List<String> configured = props.adzuna().places();
        return configured.isEmpty() ? props.cities().stream().map(JobhuntProperties.City::name).toList() : configured;
    }

    public record StoreResult(int newJobs, int purged) {}
}
