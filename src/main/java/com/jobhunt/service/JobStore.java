package com.jobhunt.service;

import com.jobhunt.model.Job;
import com.jobhunt.repository.JobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes jobs to PostgreSQL: new ones are inserted, known ones refreshed (first_seen is kept). */
@Service
public class JobStore {

    private static final int CHUNK = 500;

    private final JobRepository jobs;
    private final Clock clock;

    public JobStore(JobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public IngestService.StoreResult saveAndPurge(List<Job> incoming, Instant deleteBefore) {
        Instant now = clock.instant();
        int fresh = 0;
        for (int i = 0; i < incoming.size(); i += CHUNK) {
            List<Job> chunk = incoming.subList(i, Math.min(incoming.size(), i + CHUNK));
            Map<String, Job> existing = new HashMap<>();
            jobs.findAllByIdIn(chunk.stream().map(Job::getId).collect(Collectors.toList()))
                    .forEach(j -> existing.put(j.getId(), j));
            List<Job> inserts = new ArrayList<>();
            for (Job j : chunk) {
                Job known = existing.get(j.getId());
                if (known != null) {
                    known.copyFrom(j);          // managed entity: flushed on commit
                } else {
                    inserts.add(j.firstSeen(now));
                }
            }
            jobs.saveAll(inserts);
            fresh += inserts.size();
        }
        int purged = jobs.deleteByPostedAtBefore(deleteBefore);
        return new IngestService.StoreResult(fresh, purged);
    }
}
