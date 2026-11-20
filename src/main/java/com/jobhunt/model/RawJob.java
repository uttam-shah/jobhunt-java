package com.jobhunt.model;

/** A posting exactly as an ATS reported it, before any filtering. */
public record RawJob(
        String jobId,        // "<ats>:<slug>:<id>" - stable, used for dedupe
        String ats,
        String company,
        String title,
        String location,
        String url,
        String description,
        String postedAt,     // raw date text from the ATS (null when unknown)
        String salary,
        String expHint) {    // board-provided level, e.g. SmartRecruiters "entry_level"

    public RawJob(String jobId, String ats, String company, String title, String location,
                  String url, String description, String postedAt, String salary) {
        this(jobId, ats, company, title, location, url, description, postedAt, salary, null);
    }
}
