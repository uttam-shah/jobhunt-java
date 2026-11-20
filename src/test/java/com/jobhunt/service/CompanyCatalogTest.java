package com.jobhunt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobhunt.TestSupport;
import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.fetch.AshbyFetcher;
import com.jobhunt.fetch.GreenhouseFetcher;
import com.jobhunt.fetch.LeverFetcher;
import com.jobhunt.fetch.WorkdayFetcher;
import com.jobhunt.model.Company;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompanyCatalogTest {

    private static CompanyCatalog catalogFor(Path file) {
        JobhuntProperties p = TestSupport.props();
        JobhuntProperties withFile = new JobhuntProperties(file.toString(), p.search(), p.ingest(), p.adzuna(),
                p.cities(), p.excludeTitles(), p.categories());
        return new CompanyCatalog(withFile, List.of(new LeverFetcher(null), new GreenhouseFetcher(null),
                new AshbyFetcher(null), new WorkdayFetcher(null, null)));
    }

    @Test
    void dedupesAndSkipsBadEntries(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("companies.yaml");
        Files.writeString(f, """
                companies:
                - {ats: lever, slug: Sprinto, name: Sprinto}
                - {ats: LEVER, slug: sprinto, name: dup}
                - {ats: madeup, slug: x}
                - {ats: greenhouse}
                - {ats: eightfold, slug: null, careers_url: https://x}
                - {ats: workday, slug: adobe}
                - {ats: workday, slug: External, host: a.wd1.myworkdayjobs.com, site: External}
                - {ats: workday, slug: External, host: b.wd1.myworkdayjobs.com, site: External}
                - {ats: workday, slug: External, host: A.wd1.myworkdayjobs.com, site: external}
                - not a mapping
                """);
        List<Company> got = catalogFor(f).load();
        assertThat(got).extracting(Company::slug).containsExactly("Sprinto", "External", "External");
        assertThat(got.get(1).host()).isEqualTo("a.wd1.myworkdayjobs.com");
    }

    @Test
    void theShippedCompaniesFileLoads() {
        List<Company> got = catalogFor(Path.of("companies.yaml")).load();
        assertThat(got.size()).isGreaterThan(200);
        assertThat(got).allSatisfy(c -> assertThat(c.slug()).isNotBlank());
        assertThat(got).filteredOn(c -> "workday".equals(c.ats()))
                .allSatisfy(c -> assertThat(c.host()).isNotBlank());
    }

    @Test
    void slugIsDerivedFromTheCareersUrlWhenMissing(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("companies.yaml");
        Files.writeString(f, """
                companies:
                - {ats: greenhouse, slug: null, name: Zenoti, careers_url: "https://job-boards.greenhouse.io/zenoti"}
                - {ats: lever, slug: null, name: Coupa, careers_url: "https://jobs.lever.co/coupa"}
                - {ats: lever, slug: null, name: NoUrl}
                """);
        assertThat(catalogFor(f).load()).extracting(Company::slug).containsExactly("zenoti", "coupa");
    }
}
