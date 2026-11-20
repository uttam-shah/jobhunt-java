package com.jobhunt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One execution of the update, successful or not. Also records how far back the data is complete. */
@Entity
@Table(name = "ingest_runs")
public class IngestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    private boolean ok;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IngestMode mode;
    /** Jobs posted after this instant were considered by this run. */
    @Column(name = "keep_from")
    private Instant keepFrom;
    /** Postings from this instant onwards are complete in the database (survives MAINTAIN runs). */
    @Column(name = "covered_from")
    private Instant coveredFrom;
    private int boards;
    @Column(name = "boards_with_jobs")
    private int boardsWithJobs;
    private int postings;
    private int kept;
    @Column(name = "new_jobs")
    private int newJobs;
    @Column(name = "purged")
    private int purged;
    @Column(length = 400)
    private String error;

    protected IngestRun() {}

    public IngestRun(Instant startedAt, IngestMode mode, Instant keepFrom, Instant coveredFrom) {
        this.startedAt = startedAt;
        this.mode = mode;
        this.keepFrom = keepFrom;
        this.coveredFrom = coveredFrom;
    }

    public void succeed(Instant at, int boards, int boardsWithJobs, int postings, int kept, int newJobs, int purged) {
        this.finishedAt = at; this.ok = true;
        this.boards = boards; this.boardsWithJobs = boardsWithJobs; this.postings = postings;
        this.kept = kept; this.newJobs = newJobs; this.purged = purged;
    }

    public void fail(Instant at, String message) {
        this.finishedAt = at; this.ok = false;
        this.error = message == null ? "unknown error" : message.substring(0, Math.min(400, message.length()));
    }

    public Long getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public boolean isOk() { return ok; }
    public IngestMode getMode() { return mode; }
    public Instant getKeepFrom() { return keepFrom; }
    public Instant getCoveredFrom() { return coveredFrom; }
    public int getBoards() { return boards; }
    public int getBoardsWithJobs() { return boardsWithJobs; }
    public int getPostings() { return postings; }
    public int getKept() { return kept; }
    public int getNewJobs() { return newJobs; }
    public int getPurged() { return purged; }
    public String getError() { return error; }
}
