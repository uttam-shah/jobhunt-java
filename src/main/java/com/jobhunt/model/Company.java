package com.jobhunt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One entry of companies.yaml. Workday entries also need {@code host} and {@code site}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Company(
        String ats,
        String slug,
        String name,
        String host,
        String site,
        List<String> queries,
        Integer maxPerQuery,
        @JsonProperty("careers_url") String careersUrl) {

    public String displayName() {
        return name == null || name.isBlank() ? slug : name;
    }
}
