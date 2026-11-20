package com.jobhunt.fetch;

import com.jobhunt.model.Company;
import com.jobhunt.model.RawJob;
import java.io.IOException;
import java.util.List;

/** One implementation per applicant-tracking system. */
public interface AtsFetcher {

    /** Lower-case ATS id as written in companies.yaml, e.g. "lever". */
    String ats();

    /** Human careers page for the slug, to check it by hand. */
    String careersUrl(Company c);

    /** Fetch every open posting of one company. Throws on network/HTTP failure. */
    List<RawJob> fetch(Company c, FetchContext ctx) throws IOException;
}
