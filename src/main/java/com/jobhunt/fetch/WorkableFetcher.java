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
public class WorkableFetcher extends SingleRequestFetcher {

    public WorkableFetcher(HttpGateway http) {
        super(http);
    }

    @Override public String ats() { return "workable"; }

    @Override public String careersUrl(Company c) { return "https://apply.workable.com/" + c.slug(); }

    @Override
    public String apiUrl(Company c) {
        return "https://apply.workable.com/api/v1/widget/accounts/" + c.slug() + "?details=true";
    }

    @Override
    public List<RawJob> parse(Company c, JsonNode body) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : body.path("jobs")) {
            String loc = join(", ", text(j, "city"), text(j, "state"), text(j, "country"));
            for (JsonNode l : j.path("locations")) {          // multi-location postings
                String extra = join(", ", text(l, "city"), text(l, "region"), text(l, "country"));
                if (!extra.isEmpty() && !loc.contains(extra)) {
                    loc = loc.isEmpty() ? extra : loc + "; " + extra;
                }
            }
            String code = TextUtil.firstNonBlank(text(j, "shortcode"), text(j, "code"));
            out.add(new RawJob(
                    "workable:" + c.slug() + ":" + code,
                    "workable", c.displayName(),
                    TextUtil.orEmpty(text(j, "title")), loc,
                    TextUtil.firstNonBlank(text(j, "url"), text(j, "shortlink"), text(j, "application_url")),
                    TextUtil.stripHtml(text(j, "description")),
                    TextUtil.firstNonBlank(text(j, "published_on"), text(j, "created_at")),
                    null));
        }
        return out;
    }

    private static String join(String sep, String... parts) {
        return String.join(sep, Stream.of(parts).filter(p -> p != null && !p.isBlank()).toList());
    }
}
