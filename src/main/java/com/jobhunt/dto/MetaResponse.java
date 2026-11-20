package com.jobhunt.dto;

import com.jobhunt.service.JobClassifier.ExperienceLevel;
import java.util.List;

public record MetaResponse(
        List<String> cities, List<String> categories, List<ExperienceLevel> experience,
        int maxExperienceYears, int companyCount, List<String> companies, String runAt, String timezone,
        boolean emailEnabled, LastUpdate lastUpdate, List<DayCount> days, String today, boolean refreshing,
        int lookbackDays, int retentionDays) {

    public record DayCount(String date, long n) {}
}
