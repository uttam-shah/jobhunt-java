package com.jobhunt.service;

import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.dto.JobView;
import com.jobhunt.dto.LastUpdate;
import com.jobhunt.dto.MetaResponse;
import com.jobhunt.dto.SearchRequest;
import com.jobhunt.dto.SearchResponse;
import com.jobhunt.dto.StatusResponse;
import com.jobhunt.model.IngestRun;
import com.jobhunt.model.Job;
import com.jobhunt.repository.IngestRunRepository;
import com.jobhunt.repository.JobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side: everything the dashboard asks for. */
@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.ENGLISH);

    private final JobRepository jobs;
    private final IngestRunRepository runs;
    private final CompanyCatalog catalog;
    private final JobClassifier classifier;
    private final IngestService ingest;
    private final JobhuntProperties props;
    private final Clock clock;
    private final ZoneId zone;

    public JobQueryService(JobRepository jobs, IngestRunRepository runs, CompanyCatalog catalog,
                           JobClassifier classifier, IngestService ingest, JobhuntProperties props, Clock clock) {
        this.jobs = jobs;
        this.runs = runs;
        this.catalog = catalog;
        this.classifier = classifier;
        this.ingest = ingest;
        this.props = props;
        this.clock = clock;
        this.zone = ZoneId.of(props.ingest().timezone());
    }

    public SearchResponse search(SearchRequest req) {
        Instant now = clock.instant();
        ViewRange range = ViewRange.of(req.view(), req.date(), zone, now);
        List<Job> inRange = jobs.findByPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtDesc(range.start(), range.end());
        List<Job> matching = inRange.stream().filter(j -> matches(j, req)).toList();
        List<JobView> shown = matching.stream().limit(props.search().maxResults()).map(j -> JobView.of(j, now)).toList();
        return new SearchResponse(shown, shown.size(), counts(inRange), range.label(),
                UUID.randomUUID().toString().substring(0, 12), lastUpdate().orElse(null));
    }

    public MetaResponse meta() {
        List<String> companies = catalog.load().stream().map(c -> c.displayName()).sorted().toList();
        List<MetaResponse.DayCount> days = jobs.countByDay().stream()
                .map(d -> new MetaResponse.DayCount(d.getDay().toString(), d.getN())).limit(60).toList();
        return new MetaResponse(classifier.cityNames(), classifier.categoryNames(), JobClassifier.EXPERIENCE_LEVELS,
                classifier.maxYears(), companies.size(), companies, props.ingest().runAt(), props.ingest().timezone(),
                false, lastUpdate().orElse(null), days, clock.instant().atZone(zone).toLocalDate().toString(),
                ingest.isRunning(), props.ingest().lookbackDays(), props.ingest().retentionDays());
    }

    public StatusResponse status() {
        Optional<IngestRun> last = runs.findTopByOrderByIdDesc();
        String error = last.filter(r -> !r.isOk() && r.getFinishedAt() != null).map(IngestRun::getError).orElse(null);
        return new StatusResponse(ingest.isRunning(), lastUpdate().orElse(null), error,
                catalog.load().size(), jobs.count());
    }

    private Optional<LastUpdate> lastUpdate() {
        return runs.findTopByOkTrueOrderByIdDesc().map(r -> new LastUpdate(
                r.getFinishedAt().atZone(zone).toOffsetDateTime().toString(),
                LABEL.format(r.getFinishedAt().atZone(zone)), r.getKept(), r.getNewJobs(),
                r.getBoardsWithJobs(), r.getMode() == null ? null : r.getMode().name()));
    }

    // ------------------------------------------------------------ filters ---
    static boolean matches(Job j, SearchRequest f) {
        if (!f.cities().isEmpty() && j.getCities().stream().noneMatch(f.cities()::contains)) {
            return false;
        }
        if (!f.experience().isEmpty() && j.getExperienceLevels().stream().noneMatch(f.experience()::contains)) {
            return false;
        }
        if (!f.categories().isEmpty() && !f.categories().contains(j.getCategory())) {
            return false;
        }
        if (!f.companies().isEmpty() && !f.companies().contains(j.getCompany())) {
            return false;
        }
        String q = f.query().strip().toLowerCase();
        return q.isEmpty() || (j.getTitle() + " " + j.getCompany() + " " + j.getLocation() + " " + j.getCategory())
                .toLowerCase().contains(q);
    }

    static SearchResponse.Counts counts(List<Job> rows) {
        return new SearchResponse.Counts(rows.size(),
                tally(rows, j -> List.of(j.getCategory())),
                tally(rows, Job::getCities),
                tally(rows, Job::getExperienceLevels));
    }

    private static Map<String, Integer> tally(List<Job> rows, Function<Job, List<String>> key) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (Job j : rows) {
            for (String k : key.apply(j)) {
                raw.merge(k, 1, Integer::sum);
            }
        }
        Map<String, Integer> sorted = new LinkedHashMap<>();   // most common first
        raw.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
