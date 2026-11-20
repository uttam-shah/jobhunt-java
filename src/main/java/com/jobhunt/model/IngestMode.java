package com.jobhunt.model;

public enum IngestMode {
    /** The database has no complete history for the lookback window: keep the whole window. */
    BACKFILL,
    /** History is complete: only add what was posted since the last run. */
    MAINTAIN
}
