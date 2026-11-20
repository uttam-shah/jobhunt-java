package com.jobhunt.service;

import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.fetch.AtsFetcher;
import com.jobhunt.fetch.FetchContext;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Fetches every company board in parallel. A dead board is logged and skipped, never fatal. */
@Service
public class BoardFetchService {

    private static final Logger log = LoggerFactory.getLogger(BoardFetchService.class);

    public record Result(List<RawJob> jobs, int boards, int boardsWithJobs) {}

    private final Map<String, AtsFetcher> fetchers;
    private final int workers;

    public BoardFetchService(List<AtsFetcher> fetchers, JobhuntProperties props) {
        this.fetchers = fetchers.stream().collect(Collectors.toMap(AtsFetcher::ats, Function.identity()));
        this.workers = Math.max(1, props.ingest().fetchWorkers());
    }

    public Result fetchAll(List<Company> companies, FetchContext ctx) {
        List<RawJob> all = new ArrayList<>();
        int withJobs = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            List<Future<List<RawJob>>> futures = companies.stream()
                    .map(c -> pool.submit(() -> fetchOne(c, ctx))).toList();
            for (Future<List<RawJob>> f : futures) {
                List<RawJob> got = join(f);
                if (!got.isEmpty()) {
                    withJobs++;
                    all.addAll(got);
                }
            }
        }
        return new Result(all, companies.size(), withJobs);
    }

    private List<RawJob> fetchOne(Company c, FetchContext ctx) {
        try {
            return fetchers.get(c.ats()).fetch(c, ctx);
        } catch (Exception e) {          // dead slug, rate limit, network blip: never abort the update
            log.warn("{}/{} -> {}: {}", c.ats(), c.slug(), e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private static List<RawJob> join(Future<List<RawJob>> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
