package com.jobhunt.service;

import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.model.Job;
import com.jobhunt.model.RawJob;
import com.jobhunt.util.DateParser;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The single gate every posting passes (no LLM, all regex): location -> seniority words in the
 * title -> parseable date -> years of experience -> category. {@link #evaluate} returns empty
 * when the posting is not for this board.
 */
@Component
public class JobClassifier {

    public static final String INDIA_OTHER = "India (other)";   // in India, but no listed city named

    public record Experience(List<String> levels, String label) {}

    public record ExperienceLevel(String id, String label, String hint) {}

    public static final List<ExperienceLevel> EXPERIENCE_LEVELS = List.of(
            new ExperienceLevel("intern", "Internship", "Interns & trainees"),
            new ExperienceLevel("fresher", "Fresher", "0-1 years"),
            new ExperienceLevel("junior", "1-2 years", "Early career"));

    // SmartRecruiters' own experience ids -> our levels (empty list = too senior)
    private static final Map<String, List<String>> HINTS = Map.of(
            "internship", List.of("intern"),
            "entry_level", List.of("fresher"),
            "associate", List.of("fresher", "junior"),
            "mid_senior_level", List.of(),
            "director", List.of(),
            "executive", List.of());

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern INDIA = Pattern.compile("\\bindia\\b");
    private static final Pattern INTERN = Pattern.compile("\\b(intern|interns|internship|trainee|apprentice)\\b", CI);
    private static final Pattern FRESHER_TITLE = Pattern.compile(
            "\\b(graduate|new grad|entry[- ]level|junior|jr|fresher|freshers|campus|early career|"
                    + "college hire|university|associate)\\b|"
                    + "\\b(sde|swe|engineer|developer|analyst|executive)\\s*[- ]?\\s*(i|1)\\b", CI);
    private static final Pattern JUNIOR_TITLE = Pattern.compile(
            "\\b(sde|swe|engineer|developer|analyst|executive)\\s*[- ]?\\s*(ii|2)\\b", CI);
    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*(?:\\+|\\s*(?:-|–|—|to)\\s*(\\d{1,2}))?\\s*\\+?\\s*(?:years?|yrs?)\\b", CI);

    private record CityPattern(String name, Pattern pattern) {}

    private final int maxYears;
    private final boolean indiaWide;
    private final ZoneId zone;
    private final List<CityPattern> cities = new ArrayList<>();
    private final List<Pattern> exclude = new ArrayList<>();
    private final List<Map.Entry<String, Pattern>> categories = new ArrayList<>();

    public JobClassifier(JobhuntProperties props) {
        this.maxYears = props.search().maxExperienceYears();
        this.indiaWide = props.search().indiaWide();
        this.zone = ZoneId.of(props.ingest().timezone());
        for (JobhuntProperties.City c : props.cities()) {
            List<String> aliases = c.aliases() == null || c.aliases().isEmpty() ? List.of(c.name().toLowerCase()) : c.aliases();
            String alts = String.join("|", aliases.stream().map(a -> Pattern.quote(a.toLowerCase())).toList());
            cities.add(new CityPattern(c.name(), Pattern.compile("\\b(?:" + alts + ")\\b")));
        }
        props.excludeTitles().forEach(p -> exclude.add(Pattern.compile(p, CI)));
        props.categories().forEach(c -> categories.add(Map.entry(c.name(), Pattern.compile(c.pattern(), CI))));
    }

    /** City names offered by the dashboard filter. */
    public List<String> cityNames() {
        List<String> names = new ArrayList<>(cities.stream().map(CityPattern::name).toList());
        if (indiaWide) {
            names.add(INDIA_OTHER);
        }
        return names;
    }

    public List<String> categoryNames() {
        List<String> names = new ArrayList<>(categories.stream().map(Map.Entry::getKey).toList());
        names.add("Other");
        return names;
    }

    public int maxYears() {
        return maxYears;
    }

    // ----------------------------------------------------------- location ---
    public List<String> locate(String location) {
        String loc = location == null ? "" : location.toLowerCase();
        List<String> hits = new ArrayList<>();
        for (CityPattern c : cities) {
            if (c.pattern().matcher(loc).find()) {
                hits.add(c.name());
            }
        }
        if (hits.isEmpty() && indiaWide && INDIA.matcher(loc).find()) {
            return List.of(INDIA_OTHER);
        }
        return hits;
    }

    // --------------------------------------------------------- experience ---
    /** First "N years of experience" style requirement: {low, high or -1}. */
    static int[] requiredYears(String text) {
        Matcher m = YEARS.matcher(text);
        while (m.find()) {
            String ctx = text.substring(Math.max(0, m.start() - 90), Math.min(text.length(), m.end() + 90)).toLowerCase();
            if (ctx.contains("experience") || ctx.contains("exp")) {
                int lo = Integer.parseInt(m.group(1));
                int hi = m.group(2) != null ? Integer.parseInt(m.group(2)) : -1;
                if (lo <= 30) {
                    return new int[] {lo, hi};
                }
            }
        }
        return null;
    }

    /** Empty when the role needs more than the maximum years. */
    public Optional<Experience> classifyExperience(String title, String description, String hint) {
        if (INTERN.matcher(title).find()) {
            return Optional.of(new Experience(List.of("intern"), "Internship"));
        }
        int[] yrs = requiredYears(description);
        if (yrs != null) {
            int lo = yrs[0];
            int hi = yrs[1];
            if (lo > maxYears) {
                return Optional.empty();
            }
            String label = hi >= 0 ? lo + "-" + hi + " yrs" : (lo > 0 ? lo + "+ yrs" : "0 yrs");
            if (lo == 0) {
                return Optional.of(new Experience(List.of("fresher"), "Fresher"));
            }
            if (lo == 1) {
                return Optional.of(new Experience(List.of("fresher", "junior"), label));
            }
            return Optional.of(new Experience(List.of("junior"), label));
        }
        if (hint != null && HINTS.containsKey(hint)) {
            List<String> levels = HINTS.get(hint);
            if (levels.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Experience(levels, levels.equals(List.of("fresher")) ? "Fresher" : "0-2 yrs"));
        }
        if (FRESHER_TITLE.matcher(title).find()) {
            return Optional.of(new Experience(List.of("fresher"), "Fresher"));
        }
        if (JUNIOR_TITLE.matcher(title).find()) {
            return Optional.of(new Experience(List.of("junior"), "1-2 yrs"));
        }
        return Optional.of(new Experience(List.of("fresher", "junior"), "Not specified"));
    }

    public String categoryOf(String title) {
        for (Map.Entry<String, Pattern> c : categories) {
            if (c.getValue().matcher(title).find()) {
                return c.getKey();
            }
        }
        return "Other";
    }

    // ----------------------------------------------------------- evaluate ---
    public Optional<Job> evaluate(RawJob raw) {
        if (isBlank(raw.title()) || isBlank(raw.url())) {
            return Optional.empty();
        }
        List<String> where = locate(raw.location());
        if (where.isEmpty()) {
            return Optional.empty();
        }
        if (exclude.stream().anyMatch(p -> p.matcher(raw.title()).find())) {
            return Optional.empty();
        }
        Optional<Instant> posted = DateParser.parse(raw.postedAt());
        if (posted.isEmpty()) {            // cannot prove how fresh it is -> do not list it
            return Optional.empty();
        }
        String description = raw.description() == null ? "" : raw.description();
        Optional<Experience> exp = classifyExperience(raw.title(), description, raw.expHint());
        if (exp.isEmpty()) {
            return Optional.empty();
        }
        String flat = String.join(" ", description.strip().split("\\s+"));
        String snippet = flat.length() > 260 ? flat.substring(0, 260) + "..." : flat;
        return Optional.of(new Job(raw.jobId())
                .title(truncate(raw.title(), 500))
                .company(truncate(raw.company(), 300))
                .location(truncate(raw.location(), 500))
                .cities(where)
                .url(truncate(raw.url(), 2048))
                .postedAt(posted.get())
                .postedDate(posted.get().atZone(zone).toLocalDate())
                .experienceLevels(exp.get().levels())
                .experienceLabel(exp.get().label())
                .category(categoryOf(raw.title()))
                .salary(raw.salary() == null ? null : truncate(raw.salary(), 300))
                .ats(raw.ats())
                .snippet(snippet));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
