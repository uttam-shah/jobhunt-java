package com.jobhunt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobhunt.TestSupport;
import com.jobhunt.model.Job;
import com.jobhunt.model.RawJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JobClassifierTest {

    private final JobClassifier c = new JobClassifier(TestSupport.props());

    private RawJob raw(String title, String location, String desc, Instant posted) {
        return new RawJob("t:" + title, "t", "Acme", title, location, "https://x.test/1", desc,
                posted == null ? null : posted.toString(), null);
    }

    private RawJob raw(String title, String location) {
        return raw(title, location, "", Instant.now().minus(2, ChronoUnit.HOURS));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Marketing Intern||intern",
            "Graduate Engineer||fresher",
            "Sales Executive|0-1 years of experience|fresher",
            "Accountant|2 years of experience in Tally|junior",
            "Software Engineer II||junior"})
    void experienceLevels(String title, String desc, String level) {
        assertThat(c.classifyExperience(title, desc == null ? "" : desc, null).orElseThrow().levels()).contains(level);
    }

    @Test
    void threeYearsIsOverTheCap() {
        assertThat(c.classifyExperience("Accountant", "3+ years of experience", null)).isEmpty();
    }

    @Test
    void smartRecruitersHint() {
        assertThat(c.classifyExperience("Analyst", "", "entry_level").orElseThrow().levels()).containsExactly("fresher");
        assertThat(c.classifyExperience("Analyst", "", "mid_senior_level")).isEmpty();
    }

    @Test
    void locatesEveryIndianCityAndBareIndia() {
        assertThat(c.locate("Gurgaon, India")).containsExactly("Gurugram");
        assertThat(c.locate("Noida; Gurugram")).containsExactly("Noida", "Gurugram");
        assertThat(c.locate("New Delhi")).containsExactly("Delhi");
        assertThat(c.locate("Pune, Maharashtra")).containsExactly("Pune");
        assertThat(c.locate("Remote - India")).containsExactly(JobClassifier.INDIA_OTHER);
        assertThat(c.locate("London, UK")).isEmpty();
    }

    @Test
    void evaluateDropsForeignSeniorAndUndated() {
        assertThat(c.evaluate(raw("Sales Executive", "Pune"))).isPresent();
        assertThat(c.evaluate(raw("Sales Executive", "London, UK"))).isEmpty();
        assertThat(c.evaluate(raw("Senior Sales Executive", "Pune"))).isEmpty();
        assertThat(c.evaluate(raw("Account Manager", "Pune"))).isEmpty();
        assertThat(c.evaluate(raw("Sales Executive", "Pune", "", null))).isEmpty();
    }

    @Test
    void categoriesFirstMatchWins() {
        assertThat(c.categoryOf("Flutter Developer")).isEqualTo("Flutter & Mobile");
        assertThat(c.categoryOf("Account Executive")).isEqualTo("Sales & Business Development");
        assertThat(c.categoryOf("Accounts Executive")).isEqualTo("Accounts & Finance");
        assertThat(c.categoryOf("Zookeeper")).isEqualTo("Other");
    }

    @Test
    void evaluateFillsEveryField() {
        Job j = c.evaluate(raw("Backend Developer", "Noida", "We need 0-1 years of experience.", Instant.now())).orElseThrow();
        assertThat(j.getCities()).containsExactly("Noida");
        assertThat(j.getCategory()).isEqualTo("Backend");
        assertThat(j.getExperienceLevels()).containsExactly("fresher");
        assertThat(j.getSnippet()).startsWith("We need");
        assertThat(j.getPostedDate()).isNotNull();
        assertThat(c.cityNames()).contains("Pune", JobClassifier.INDIA_OTHER);
        assertThat(Optional.of(j.getPostedAt())).isPresent();
        assertThat(List.of(j.getId())).containsExactly("t:Backend Developer");
    }
}
