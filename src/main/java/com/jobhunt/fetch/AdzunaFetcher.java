package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.TextUtil;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Optional aggregator that covers employers without a public ATS board. Active only when
 * ADZUNA_APP_ID / ADZUNA_APP_KEY are set (free key: https://developer.adzuna.com).
 */
@Component
public class AdzunaFetcher {

    private static final Logger log = LoggerFactory.getLogger(AdzunaFetcher.class);
    private static final int PER_PAGE = 50;

    private final HttpGateway http;
    private final JobhuntProperties.Adzuna cfg;

    public AdzunaFetcher(HttpGateway http, JobhuntProperties props) {
        this.http = http;
        this.cfg = props.adzuna();
    }

    public boolean enabled() {
        return cfg.enabled() && !cfg.appId().isBlank() && !cfg.appKey().isBlank();
    }

    public List<RawJob> fetch(List<String> places, int maxDaysOld) {
        Map<String, RawJob> jobs = new LinkedHashMap<>();
        for (String place : places) {
            for (int page = 1; page <= cfg.pages(); page++) {
                List<RawJob> got;
                try {
                    got = parse(http.getJson(url(place, page, maxDaysOld)));
                } catch (IOException e) {
                    log.warn("adzuna/{} page {} failed: {}", place, page, e.getMessage());
                    break;
                }
                got.forEach(j -> jobs.putIfAbsent(j.jobId(), j));
                if (got.size() < PER_PAGE) {
                    break;
                }
            }
        }
        return new ArrayList<>(jobs.values());
    }

    private String url(String place, int page, int maxDaysOld) {
        return "https://api.adzuna.com/v1/api/jobs/in/search/" + page
                + "?app_id=" + enc(cfg.appId()) + "&app_key=" + enc(cfg.appKey())
                + "&results_per_page=" + PER_PAGE + "&where=" + enc(place) + "&distance=25"
                + "&max_days_old=" + maxDaysOld + "&sort_by=date&content-type=application/json";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static List<RawJob> parse(JsonNode body) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : body.path("results")) {
            JsonNode loc = j.path("location");
            String where = loc.path("display_name").asText("");
            if (where.isEmpty() && loc.path("area").isArray()) {
                List<String> area = new ArrayList<>();
                loc.path("area").forEach(a -> area.add(a.asText()));
                where = String.join(", ", area);
            }
            String salary = null;
            boolean predicted = "1".equals(j.path("salary_is_predicted").asText());
            if (j.path("salary_min").asDouble(0) > 0 && j.path("salary_max").asDouble(0) > 0 && !predicted) {
                salary = String.format("₹%,d - ₹%,d", (long) j.path("salary_min").asDouble(), (long) j.path("salary_max").asDouble());
            }
            String company = TextUtil.orEmpty(j.path("company").path("display_name").asText(null));
            out.add(new RawJob(
                    "adzuna:" + j.path("id").asText(), "adzuna",
                    company.isEmpty() ? "Company not listed" : company,
                    TextUtil.stripHtml(j.path("title").asText(null)).strip(), where,
                    j.path("redirect_url").asText(""),
                    TextUtil.stripHtml(j.path("description").asText(null)),
                    j.path("created").asText(null), salary));
        }
        return out;
    }
}
