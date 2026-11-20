package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GreenhouseFetcher extends SingleRequestFetcher {

    public GreenhouseFetcher(HttpGateway http) {
        super(http);
    }

    @Override public String ats() { return "greenhouse"; }

    @Override public String careersUrl(Company c) { return "https://boards.greenhouse.io/" + c.slug(); }

    @Override
    public String apiUrl(Company c) {
        return "https://boards-api.greenhouse.io/v1/boards/" + c.slug() + "/jobs?content=true";
    }

    @Override
    public List<RawJob> parse(Company c, JsonNode body) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : body.path("jobs")) {
            out.add(new RawJob(
                    "greenhouse:" + c.slug() + ":" + j.path("id").asText(),
                    "greenhouse", c.displayName(),
                    TextUtil.orEmpty(text(j, "title")),
                    TextUtil.orEmpty(text(j.path("location"), "name")),
                    TextUtil.orEmpty(text(j, "absolute_url")),
                    // Greenhouse sends HTML that is itself entity-escaped
                    TextUtil.stripHtml(text(j, "content")),
                    TextUtil.firstNonBlank(text(j, "first_published"), text(j, "updated_at")),
                    null));
        }
        return out;
    }
}
