# jobhunt (Java / Spring Boot)

India job board backend. Reads the public JSON job APIs of the companies in `companies.yaml`
(Greenhouse, Lever, Ashby, Workable, Recruitee, SmartRecruiters, Workday, optional Adzuna),
filters with plain regex (**no LLM**), stores the result in PostgreSQL (Neon) and serves the
Next.js dashboard in `../web`.

## Run

```bash
cp .env.example .env        # fill DB_URL / DB_USERNAME / DB_PASSWORD (already done locally)
mvn test                    # offline unit tests
mvn spring-boot:run         # API on http://localhost:8000  (PORT=8010 to change)
```

Stop the old Python server first if it is running - both use port 8000, which is what lets
the dashboard work without any change.

Tables `jobs` and `ingest_runs` are created automatically on first start.

## Architecture (MVC)

```
controller/   JobController (/api/meta, /api/search)   UpdateController (/api/status, /api/refresh, /api/health)
service/      IngestService  - the update; IngestPlanner - backfill vs maintain; JobStore - writes + purge
              JobClassifier  - location / experience / category rules   JobQueryService - read side
              BoardFetchService - parallel fetch   CompanyCatalog - companies.yaml   UpdateScheduler - 10:00 IST
fetch/        one AtsFetcher per ATS (parse() is pure -> unit-tested with fixtures), HttpGateway = only HTTP code
model/        Job + IngestRun (JPA entities), RawJob, Company        dto/  JSON shapes for the dashboard
repository/   Spring Data JPA                                         exception/  every error -> {"detail": "..."}
```

## What happens on every update

1. **Check history.** `IngestPlanner` looks at the last successful run and at the `jobs` table:
   * no run yet, no jobs for the last 7 days, history shorter than 7 days, or the last run is
     more than 48 h old -> **BACKFILL**: keep every matching job posted in the last 7 days;
   * otherwise -> **MAINTAIN**: keep only jobs posted since the last run (at least the last 24 h).
2. Fetch every board in parallel (a dead board is logged and skipped), filter, upsert
   (`first_seen` is kept for known jobs).
3. **Delete** jobs posted more than 15 days ago.

Runs happen daily at `jobhunt.ingest.run-at` (10:00 IST), at startup if today's run was missed,
and from the dashboard's "Fetch now" button (one at a time, 120 s cooldown).
The dashboard views: last 24 h / 3 days / 7 days / **15 days**, yesterday, any date.

## Configuration

`src/main/resources/application.yml` -> `jobhunt:` holds cities, categories, exclude-title
regexes, lookback (7) and retention (15) days. `companies.yaml` is re-read on every update.
Entries with only a `careers_url` (no `slug`) work too: the slug is taken from the URL.
Entries for ATSs without a fetcher (successfactors, oracle, darwinbox, eightfold, own_platform)
are skipped.

Secrets (`.env`, git-ignored): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, optional
`ADZUNA_APP_ID` / `ADZUNA_APP_KEY`.
