package com.jobhunt.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Everything tunable, bound from the {@code jobhunt:} block of application.yml. */
@ConfigurationProperties(prefix = "jobhunt")
public record JobhuntProperties(
        @DefaultValue("companies.yaml") String companiesFile,
        @DefaultValue Search search,
        @DefaultValue Ingest ingest,
        @DefaultValue Adzuna adzuna,
        @DefaultValue List<City> cities,
        @DefaultValue List<String> excludeTitles,
        @DefaultValue List<Category> categories) {

    public record Search(
            @DefaultValue("2") int maxExperienceYears,
            @DefaultValue("3000") int maxResults,
            @DefaultValue("true") boolean indiaWide) {}

    public record Ingest(
            @DefaultValue("10:00") String runAt,
            @DefaultValue("Asia/Kolkata") String timezone,
            /** A fresh database is filled with this many days of postings. */
            @DefaultValue("7") int lookbackDays,
            /** Postings older than this are deleted after every run. */
            @DefaultValue("15") int retentionDays,
            /** If the last good run is older than this, the history has a hole: backfill again. */
            @DefaultValue("48") int maxGapHours,
            @DefaultValue("16") int fetchWorkers,
            @DefaultValue("120") int refreshCooldownSeconds) {}

    public record Adzuna(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("") String appId,
            @DefaultValue("") String appKey,
            @DefaultValue("4") int pages,
            @DefaultValue List<String> places) {}

    public record City(String name, List<String> aliases) {}

    public record Category(String name, String pattern) {}
}
