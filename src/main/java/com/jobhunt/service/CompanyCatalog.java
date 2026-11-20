package com.jobhunt.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.fetch.AtsFetcher;
import com.jobhunt.model.Company;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads companies.yaml. The file is re-read on every call, so adding a company needs no restart.
 * One malformed line is skipped with a warning and never takes the update down.
 */
@Service
public class CompanyCatalog {

    private static final Logger log = LoggerFactory.getLogger(CompanyCatalog.class);

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Path file;
    private final Set<String> supportedAts;

    public CompanyCatalog(JobhuntProperties props, List<AtsFetcher> fetchers) {
        this.file = Path.of(props.companiesFile());
        this.supportedAts = fetchers.stream().map(AtsFetcher::ats).collect(Collectors.toUnmodifiableSet());
    }

    public List<Company> load() {
        try {
            JsonNode root = yaml.readTree(Files.readString(file));
            return validate(yamlList(root.path("companies")));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    private List<JsonNode> yamlList(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(out::add);
        }
        return out;
    }

    /** Needs ats + slug, a known ATS, host + site for Workday; drops duplicates (case-insensitive). */
    List<Company> validate(List<JsonNode> entries) {
        List<Company> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int n = 0;
        for (JsonNode e : entries) {
            n++;
            Company c;
            try {
                c = yaml.treeToValue(e, Company.class);
            } catch (IOException | IllegalArgumentException ex) {
                log.warn("companies.yaml entry #{} ignored (not a mapping): {}", n, e);
                continue;
            }
            if (c == null || blank(c.ats())) {
                log.warn("companies.yaml entry #{} ignored (needs ats + slug): {}", n, e);
                continue;
            }
            String ats = c.ats().strip().toLowerCase(Locale.ROOT);
            if (!supportedAts.contains(ats)) {      // e.g. successfactors: listed for reference, no fetcher yet
                log.debug("companies.yaml {}: ats '{}' has no fetcher yet - skipped", c.displayName(), ats);
                continue;
            }
            if (blank(c.slug())) {
                c = withSlug(c, ats, slugFromCareersUrl(ats, c.careersUrl()));
            }
            if (blank(c.slug())) {
                log.warn("companies.yaml entry #{} ignored (needs ats + slug): {}", n, e);
                continue;
            }
            Company clean = new Company(ats, c.slug().strip(), c.name(), c.host(), c.site(), c.queries(), c.maxPerQuery(), c.careersUrl());
            if ("workday".equals(ats) && (blank(clean.host()) || blank(clean.site()))) {
                log.warn("companies.yaml {}: workday needs host + site - skipped", clean.slug());
                continue;
            }
            // Workday slugs like "External" repeat across companies, so host+site is its identity.
            String key = "workday".equals(ats)
                    ? "workday|" + clean.host().toLowerCase(Locale.ROOT) + "|" + clean.site().toLowerCase(Locale.ROOT)
                    : ats + "|" + clean.slug().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(clean);
            }
        }
        return out;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Company withSlug(Company c, String ats, String slug) {
        return new Company(ats, slug, c.name(), c.host(), c.site(), c.queries(), c.maxPerQuery(), c.careersUrl());
    }

    /**
     * Entries added by hand often carry only the careers page. The slug is in it:
     * jobs.lever.co/<slug>, job-boards.greenhouse.io/<slug>, <slug>.recruitee.com ...
     * Workday cannot be derived this way (it needs host + site).
     */
    static String slugFromCareersUrl(String ats, String careersUrl) {
        if (blank(careersUrl) || "workday".equals(ats)) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(careersUrl.strip());
            if ("recruitee".equals(ats) && uri.getHost() != null) {
                return uri.getHost().split("\\.")[0];
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            for (String part : path.split("/")) {
                if (!part.isBlank() && !"en-US".equalsIgnoreCase(part)) {
                    return part;
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug("bad careers_url {}", careersUrl);
        }
        return null;
    }
}
