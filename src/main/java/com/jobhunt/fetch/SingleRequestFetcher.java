package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import java.io.IOException;
import java.util.List;

/** Template for boards that publish all their jobs from one GET: build the URL, parse the JSON. */
public abstract class SingleRequestFetcher implements AtsFetcher {

    private final HttpGateway http;

    protected SingleRequestFetcher(HttpGateway http) {
        this.http = http;
    }

    /** JSON API url for the company. */
    public abstract String apiUrl(Company c);

    /** Pure function from the JSON body to postings (no I/O, so it is unit-tested with fixtures). */
    public abstract List<RawJob> parse(Company c, JsonNode body);

    @Override
    public List<RawJob> fetch(Company c, FetchContext ctx) throws IOException {
        return parse(c, http.getJson(apiUrl(c)));
    }

    protected static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
