package com.jobhunt.fetch;

import static com.jobhunt.TestSupport.company;
import static com.jobhunt.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobhunt.model.RawJob;
import com.jobhunt.util.DateParser;
import com.jobhunt.util.TextUtil;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ParserTest {

    @Test
    void leverUsesMillisecondsAndJoinsTheSplitDescription() {
        var f = new LeverFetcher(null);
        List<RawJob> jobs = f.parse(company("lever", "acme"), json("""
                [{"id":"1","text":"Intern","hostedUrl":"https://jobs.lever.co/acme/1","createdAt":1763600000000,
                  "categories":{"location":"Noida","commitment":"Full-time"},
                  "descriptionPlain":"About us","lists":[{"text":"Req","content":"<li>0-1 years of experience</li>"}],
                  "additionalPlain":"Perks"}]"""));
        RawJob j = jobs.get(0);
        assertThat(j.jobId()).isEqualTo("lever:acme:1");
        assertThat(j.postedAt()).isEqualTo("2025-11-20T00:53:20Z");
        assertThat(j.description()).contains("About us", "Req", "0-1 years of experience", "Perks");
        assertThat(j.location()).isEqualTo("Noida");
    }

    @Test
    void greenhouseContentIsEscapedHtml() {
        var f = new GreenhouseFetcher(null);
        RawJob j = f.parse(company("greenhouse", "acme"), json("""
                {"jobs":[{"id":7,"title":" Analyst ","absolute_url":"https://x/7","location":{"name":"Pune"},
                 "content":"&lt;p&gt;Needs &amp;amp; wants&lt;/p&gt;&lt;ul&gt;&lt;li&gt;2 years of experience&lt;/li&gt;&lt;/ul&gt;",
                 "first_published":"2026-11-19T10:00:00Z"}]}""")).get(0);
        assertThat(j.title()).isEqualTo("Analyst");
        assertThat(j.description()).contains("Needs & wants").doesNotContain("<");
        assertThat(j.postedAt()).isEqualTo("2026-11-19T10:00:00Z");
    }

    @Test
    void ashbySkipsUnlistedPostings() {
        var f = new AshbyFetcher(null);
        var jobs = f.parse(company("ashby", "acme"), json("""
                {"jobs":[{"id":"a","title":"A","isListed":false,"jobUrl":"u"},
                         {"id":"b","title":"B","jobUrl":"u","location":"Delhi","publishedAt":"2026-11-19"}]}"""));
        assertThat(jobs).extracting(RawJob::jobId).containsExactly("ashby:acme:b");
    }

    @Test
    void smartRecruitersCarriesTheExperienceHint() {
        var f = new SmartRecruitersFetcher(null);
        RawJob j = f.parse(company("smartrecruiters", "acme"), json("""
                {"content":[{"id":"9","name":"Analyst","releasedDate":"2026-11-19T01:00:00.000Z",
                 "location":{"city":"Pune","country":"in","remote":true},"experienceLevel":{"id":"entry_level"}}]}""")).get(0);
        assertThat(j.expHint()).isEqualTo("entry_level");
        assertThat(j.location()).isEqualTo("Pune, IN (Remote)");
    }

    @Test
    void workdayHelpers() {
        assertThat(WorkdayFetcher.daysAgo("Posted Today")).isEqualTo(0);
        assertThat(WorkdayFetcher.daysAgo("Posted Yesterday")).isEqualTo(1);
        assertThat(WorkdayFetcher.daysAgo("Posted 5 Days Ago")).isEqualTo(5);
        assertThat(WorkdayFetcher.daysAgo("Posted 30+ Days Ago")).isEqualTo(30);
        assertThat(WorkdayFetcher.daysAgo("whenever")).isNull();
        assertThat(WorkdayFetcher.tenant("adobe.wd5.myworkdayjobs.com")).isEqualTo("adobe");
        assertThat(WorkdayFetcher.pathLocation("/job/Gurgaon-Haryana/Title_R1")).isEqualTo("Gurgaon Haryana");
        var ids = WorkdayFetcher.locationFacetIds(json("""
                [{"facetParameter":"locationMainGroup","values":[{"facetParameter":"locations",
                  "values":[{"id":"abc","descriptor":"India - Pune"},{"id":"def","descriptor":"USA - Austin"}]}]}]"""),
                Pattern.compile("India", Pattern.CASE_INSENSITIVE));
        assertThat(ids).containsEntry("locations", List.of("abc")).hasSize(1);
    }

    @Test
    void datesInEveryShapeTheBoardsUse() {
        assertThat(DateParser.parse("2026-11-19T10:00:00Z")).isPresent();
        assertThat(DateParser.parse("2026-11-19T10:00:00.000+0530")).isPresent();
        assertThat(DateParser.parse("2026-11-19T10:00:00")).isPresent();
        assertThat(DateParser.parse("2026-11-19")).isPresent();
        assertThat(DateParser.parse("2026-11-19 10:00:00 UTC")).isPresent();
        assertThat(DateParser.parse("garbage")).isEmpty();
        assertThat(DateParser.parse(null)).isEmpty();
    }

    @Test
    void stripHtmlKeepsLineBreaks() {
        assertThat(TextUtil.stripHtml("<p>One</p><p>Two &amp; three</p>")).isEqualTo("One\n Two & three");
    }
}
