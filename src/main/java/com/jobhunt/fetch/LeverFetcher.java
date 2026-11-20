package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.TextUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LeverFetcher extends SingleRequestFetcher {

    public LeverFetcher(HttpGateway http) {
        super(http);
    }

    @Override public String ats() { return "lever"; }

    @Override public String careersUrl(Company c) { return "https://jobs.lever.co/" + c.slug(); }

    @Override public String apiUrl(Company c) { return "https://api.lever.co/v0/postings/" + c.slug() + "?mode=json"; }

    @Override
    public List<RawJob> parse(Company c, JsonNode body) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : body) {
            JsonNode cats = j.path("categories");
            // Lever splits the JD across descriptionPlain + a `lists` array + additionalPlain.
            List<String> chunks = new ArrayList<>();
            chunks.add(TextUtil.firstNonBlank(text(j, "descriptionPlain"), TextUtil.stripHtml(text(j, "description"))));
            for (JsonNode l : j.path("lists")) {
                chunks.add(TextUtil.orEmpty(text(l, "text")));
                chunks.add(TextUtil.stripHtml(text(l, "content")));
            }
            chunks.add(TextUtil.firstNonBlank(text(j, "additionalPlain"), TextUtil.stripHtml(text(j, "additional"))));
            String description = String.join("\n\n", chunks.stream().filter(s -> !s.isBlank()).toList()).strip();

            // createdAt is epoch MILLISECONDS
            String posted = j.path("createdAt").isNumber()
                    ? Instant.ofEpochMilli(j.path("createdAt").asLong()).toString() : null;
            out.add(new RawJob(
                    "lever:" + c.slug() + ":" + j.path("id").asText(),
                    "lever", c.displayName(),
                    TextUtil.orEmpty(text(j, "text")),
                    TextUtil.orEmpty(text(cats, "location")),
                    TextUtil.firstNonBlank(text(j, "hostedUrl"), text(j, "applyUrl")),
                    description, posted, text(cats, "commitment")));
        }
        return out;
    }
}
