package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.TextUtil;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Workday: POST {host}/wday/cxs/{tenant}/{site}/jobs. The list gives only relative dates
 * ("Posted 3 Days Ago"), so for postings inside the run's window the detail page is read too:
 * it carries the real start date and the description.
 */
@Component
public class WorkdayFetcher implements AtsFetcher {

    private static final int PAGE = 20;
    private static final int DEFAULT_CAP = 1500;     // Workday refuses to page past ~2000 results
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Pattern DAYS_AGO = Pattern.compile("posted\\s+(\\d+)\\+?\\s+days?\\s+ago", Pattern.CASE_INSENSITIVE);

    private final HttpGateway http;
    private final ObjectMapper mapper;

    public WorkdayFetcher(HttpGateway http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    @Override public String ats() { return "workday"; }

    @Override
    public String careersUrl(Company c) {
        return "https://" + c.host() + "/en-US/" + c.site();
    }

    @Override
    public List<RawJob> fetch(Company c, FetchContext ctx) throws IOException {
        String tenant = tenant(c.host());
        String base = "https://" + c.host() + "/wday/cxs/" + tenant + "/" + c.site();
        int cap = c.maxPerQuery() != null ? c.maxPerQuery() : DEFAULT_CAP;
        List<String> words = c.queries() != null && !c.queries().isEmpty() ? c.queries() : List.of("India");

        Map<String, JsonNode> postings = new LinkedHashMap<>();
        Pattern wanted = Pattern.compile(String.join("|", words.stream().map(Pattern::quote).toList()), Pattern.CASE_INSENSITIVE);
        Map<String, List<String>> facetIds = locationFacetIds(page(base, mapper.createObjectNode(), "", 0).path("facets"), wanted);
        if (!facetIds.isEmpty()) {      // preferred: the board's own location facet (exact, small result set)
            ObjectNode facets = mapper.createObjectNode();
            facetIds.forEach((param, ids) -> {
                var arr = facets.putArray(param);
                ids.forEach(arr::add);
            });
            readAll(base, facets, "", cap, postings);
        } else {                         // no location facet: fall back to text search
            for (String w : words) {
                readAll(base, mapper.createObjectNode(), w, cap, postings);
            }
        }
        return toJobs(c, tenant, base, ctx, postings);
    }

    // ----------------------------------------------------------------- list ---
    private JsonNode page(String base, ObjectNode facets, String text, int offset) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.set("appliedFacets", facets);
        body.put("limit", PAGE).put("offset", offset).put("searchText", text);
        return http.postJson(base + "/jobs", body);
    }

    private void readAll(String base, ObjectNode facets, String text, int cap, Map<String, JsonNode> into) throws IOException {
        JsonNode first = page(base, facets, text, 0);
        int total = Math.min(first.path("total").asInt(0), cap);
        List<JsonNode> all = new ArrayList<>();
        first.path("jobPostings").forEach(all::add);
        // Workday ranks by relevance, not date, so a search can only be read in full.
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<JsonNode>> pages = new ArrayList<>();
            for (int offset = PAGE; offset < total; offset += PAGE) {
                int o = offset;
                pages.add(pool.submit(() -> page(base, facets, text, o)));
            }
            for (Future<JsonNode> f : pages) {
                f.get().path("jobPostings").forEach(all::add);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IOException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e);
        }
        for (JsonNode p : all) {
            into.putIfAbsent(p.path("externalPath").asText(""), p);
        }
    }

    // ---------------------------------------------------------------- jobs ---
    private List<RawJob> toJobs(Company c, String tenant, String base, FetchContext ctx, Map<String, JsonNode> postings) {
        List<RawJob> out = new ArrayList<>();
        ZonedDateTime nowIst = ctx.now().atZone(IST);
        int detailDays = ctx.windowDays();
        for (Map.Entry<String, JsonNode> e : postings.entrySet()) {
            String path = e.getKey();
            if (path.isEmpty()) {
                continue;
            }
            JsonNode p = e.getValue();
            Integer ago = daysAgo(p.path("postedOn").asText(null));
            String description = "";
            String posted = null;
            if (ago != null && ago <= detailDays) {
                String start = null;
                try {
                    JsonNode info = http.getJson(base + path).path("jobPostingInfo");
                    description = TextUtil.stripHtml(info.path("jobDescription").asText(null));
                    start = info.path("startDate").asText(null);        // YYYY-MM-DD
                } catch (IOException ignored) {
                    // keep the relative date below
                }
                LocalDate day = start != null && !start.isBlank()
                        ? LocalDate.parse(start.substring(0, 10)) : nowIst.toLocalDate().minusDays(ago);
                // date-only: pin to noon IST that day, never in the future
                Instant at = day.atTime(12, 0).atZone(IST).toInstant();
                posted = (at.isAfter(ctx.now()) ? ctx.now() : at).toString();
            }
            String location = TextUtil.firstNonBlank(p.path("locationsText").asText(null), pathLocation(path));
            out.add(new RawJob(
                    "workday:" + tenant + ":" + path.substring(path.lastIndexOf('/') + 1),
                    "workday", c.displayName(),
                    TextUtil.orEmpty(p.path("title").asText(null)), location.strip(),
                    "https://" + c.host() + "/en-US/" + c.site() + path,
                    description, posted, null));
        }
        return out;
    }

    // ------------------------------------------------------------- helpers ---
    static String tenant(String host) {
        return host.substring(0, host.indexOf('.') < 0 ? host.length() : host.indexOf('.'));
    }

    /** "Posted Today" -> 0, "Posted Yesterday" -> 1, "Posted 5 Days Ago" -> 5, anything else -> null. */
    static Integer daysAgo(String text) {
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains("today")) {
            return 0;
        }
        if (t.contains("yesterday")) {
            return 1;
        }
        Matcher m = DAYS_AGO.matcher(t);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** Walk Workday's nested facet tree: {facetParameter: [ids]} for location values whose label matches. */
    static Map<String, List<String>> locationFacetIds(JsonNode facets, Pattern wanted) {
        Map<String, List<String>> found = new LinkedHashMap<>();
        walk(facets, null, wanted, found);
        return found;
    }

    private static void walk(JsonNode nodes, String param, Pattern wanted, Map<String, List<String>> found) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode n : nodes) {
            String p = n.hasNonNull("facetParameter") ? n.get("facetParameter").asText() : param;
            if (n.path("values").isArray() && !n.path("values").isEmpty()) {
                walk(n.get("values"), p, wanted, found);
            } else if (n.hasNonNull("id") && p != null && p.toLowerCase().contains("location")
                    && wanted.matcher(n.path("descriptor").asText("")).find()) {
                found.computeIfAbsent(p, k -> new ArrayList<>()).add(n.get("id").asText());
            }
        }
    }

    /** /job/Gurgaon-Haryana/Title_R1 -> "Gurgaon Haryana" (some boards omit locationsText). */
    static String pathLocation(String path) {
        String[] parts = path.replaceAll("^/+|/+$", "").split("/");
        return parts.length > 2 && "job".equals(parts[0]) ? parts[1].replace('-', ' ') : "";
    }
}
