package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.TextUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Paged (100 per page) and filtered to India. The list endpoint carries no job description. */
@Component
public class SmartRecruitersFetcher extends SingleRequestFetcher {

    private static final int PAGE = 100;
    private static final int SAFETY_CAP = 1000;       // postings per company
    private final HttpGateway http;

    public SmartRecruitersFetcher(HttpGateway http) {
        super(http);
        this.http = http;
    }

    @Override public String ats() { return "smartrecruiters"; }

    @Override public String careersUrl(Company c) { return "https://jobs.smartrecruiters.com/" + c.slug(); }

    @Override public String apiUrl(Company c) { return page(c, 0); }

    private static String page(Company c, int offset) {
        return "https://api.smartrecruiters.com/v1/companies/" + c.slug()
                + "/postings?limit=" + PAGE + "&country=in&offset=" + offset;
    }

    @Override
    public List<RawJob> fetch(Company c, FetchContext ctx) throws IOException {
        List<RawJob> jobs = new ArrayList<>();
        for (int offset = 0; offset < SAFETY_CAP; offset += PAGE) {
            JsonNode body = http.getJson(page(c, offset));
            List<RawJob> got = parse(c, body);
            jobs.addAll(got);
            if (got.isEmpty() || offset + PAGE >= body.path("totalFound").asInt(0)) {
                break;
            }
        }
        return jobs;
    }

    @Override
    public List<RawJob> parse(Company c, JsonNode body) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : body.path("content")) {
            JsonNode loc = j.path("location");
            String where = TextUtil.firstNonBlank(text(loc, "fullLocation"), String.join(", ",
                    Stream.of(text(loc, "city"), text(loc, "region"),
                                    text(loc, "country") == null ? null : text(loc, "country").toUpperCase())
                            .filter(s -> s != null && !s.isBlank()).toList()));
            if (loc.path("remote").asBoolean(false)) {
                where += " (Remote)";
            }
            String description = String.join(" ", Stream.of(
                            text(j.path("function"), "label"), text(j.path("industry"), "label"))
                    .filter(s -> s != null && !s.isBlank()).toList());
            out.add(new RawJob(
                    "smartrecruiters:" + c.slug() + ":" + j.path("id").asText(),
                    "smartrecruiters", c.displayName(),
                    TextUtil.orEmpty(text(j, "name")), where,
                    "https://jobs.smartrecruiters.com/" + c.slug() + "/" + j.path("id").asText(),
                    description, text(j, "releasedDate"), null,
                    text(j.path("experienceLevel"), "id")));
        }
        return out;
    }
}
