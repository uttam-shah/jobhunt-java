package com.jobhunt.dto;

import java.util.List;
import java.util.Map;

public record SearchResponse(
        List<JobView> jobs, int matching, Counts counts, String viewLabel, String searchId, LastUpdate lastUpdate) {

    public record Counts(int total, Map<String, Integer> byCategory, Map<String, Integer> byCity,
                         Map<String, Integer> byExperience) {}
}
