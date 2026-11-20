package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class RecruiteeFetcher extends SingleRequestFetcher {

    public RecruiteeFetcher(HttpGateway http) {
        super(http);
    }

    @Override public String ats() { return "recruitee"; }

    @Override public String careersUrl(Company c) { return "https://" + c.slug() + ".recruitee.com"; }

    @Override public String apiUrl(Company c) { return "https://" + c.slug() + ".recruitee.com/api/offers/"; }

    @Override
    public List<RawJob> parse(Company c, JsonNode body) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : body.path("offers")) {
            String status = text(j, "status");
            if (status != null && !"published".equals(status)) {
                continue;
            }
            String loc = TextUtil.firstNonBlank(text(j, "location"), String.join(", ",
                    Stream.of(text(j, "city"), text(j, "country")).filter(s -> s != null && !s.isBlank()).toList()));
            out.add(new RawJob(
                    "recruitee:" + c.slug() + ":" + j.path("id").asText(),
                    "recruitee", c.displayName(),
                    TextUtil.orEmpty(text(j, "title")), loc,
                    TextUtil.firstNonBlank(text(j, "careers_url"), text(j, "url")),
                    TextUtil.stripHtml(TextUtil.orEmpty(text(j, "description")) + " " + TextUtil.orEmpty(text(j, "requirements"))),
                    TextUtil.firstNonBlank(text(j, "published_at"), text(j, "created_at")),
                    null));
        }
        return out;
    }
}
