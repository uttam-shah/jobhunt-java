package com.jobhunt.dto;

import com.jobhunt.model.Job;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** A job as the dashboard receives it. */
public record JobView(
        String id, String title, String company, String location, List<String> cities, String url,
        String postedAt, String postedDate, long hoursAgo, List<String> experienceLevels,
        String experienceLabel, String category, String salary, String ats, String snippet) {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC);

    public static JobView of(Job j, Instant now) {
        long hours = Math.max(0, Duration.between(j.getPostedAt(), now).toHours());
        return new JobView(j.getId(), j.getTitle(), j.getCompany(), j.getLocation(), j.getCities(), j.getUrl(),
                ISO.format(j.getPostedAt()), j.getPostedDate().toString(), hours, j.getExperienceLevels(),
                j.getExperienceLabel(), j.getCategory(), j.getSalary(), j.getAts(), j.getSnippet());
    }
}
