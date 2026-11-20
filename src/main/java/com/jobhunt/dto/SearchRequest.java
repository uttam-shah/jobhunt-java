package com.jobhunt.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Body of POST /api/search. Missing lists mean "no filter". */
public record SearchRequest(
        String view,                    // 24h | 3d | 7d | 15d | yesterday | date
        String date,                    // YYYY-MM-DD when view == "date"
        List<String> cities,
        List<String> experience,
        List<String> categories,
        List<String> companies,
        @Size(max = 100) String query) {

    public SearchRequest {
        view = view == null || view.isBlank() ? "24h" : view;
        cities = cities == null ? List.of() : cities;
        experience = experience == null ? List.of() : experience;
        categories = categories == null ? List.of() : categories;
        companies = companies == null ? List.of() : companies;
        query = query == null ? "" : query;
    }
}
