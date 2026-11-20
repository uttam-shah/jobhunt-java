package com.jobhunt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** A job that passed every filter and is stored in PostgreSQL. */
@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "jobs_posted_at", columnList = "posted_at"),
        @Index(name = "jobs_posted_date", columnList = "posted_date")})
public class Job implements Persistable<String> {

    @Id
    @Column(length = 400)
    private String id;
    @Column(length = 500)
    private String title;
    @Column(length = 300)
    private String company;
    @Column(length = 500)
    private String location;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> cities;
    @Column(length = 2048)
    private String url;
    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;
    @Column(name = "posted_date", nullable = false)
    private LocalDate postedDate;          // in the board's timezone (IST)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experience_levels")
    private List<String> experienceLevels;
    @Column(name = "experience_label", length = 60)
    private String experienceLabel;
    @Column(length = 100)
    private String category;
    @Column(length = 300)
    private String salary;
    @Column(length = 30)
    private String ats;
    @Column(length = 300)
    private String snippet;
    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    /** Lets Spring Data INSERT without a SELECT-first round trip (ids are assigned, not generated). */
    @Transient
    private boolean isNew = true;

    protected Job() {}

    public Job(String id) {
        this.id = id;
    }

    /** Refresh everything except id and first_seen from a newer copy of the same posting. */
    public void copyFrom(Job o) {
        title = o.title; company = o.company; location = o.location; cities = o.cities; url = o.url;
        postedAt = o.postedAt; postedDate = o.postedDate; experienceLevels = o.experienceLevels;
        experienceLabel = o.experienceLabel; category = o.category; salary = o.salary; ats = o.ats;
        snippet = o.snippet;
    }

    @Override public String getId() { return id; }
    @Override public boolean isNew() { return isNew; }
    @PostLoad @PostPersist void markNotNew() { isNew = false; }

    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getLocation() { return location; }
    public List<String> getCities() { return cities; }
    public String getUrl() { return url; }
    public Instant getPostedAt() { return postedAt; }
    public LocalDate getPostedDate() { return postedDate; }
    public List<String> getExperienceLevels() { return experienceLevels; }
    public String getExperienceLabel() { return experienceLabel; }
    public String getCategory() { return category; }
    public String getSalary() { return salary; }
    public String getAts() { return ats; }
    public String getSnippet() { return snippet; }
    public Instant getFirstSeen() { return firstSeen; }

    public Job title(String v) { title = v; return this; }
    public Job company(String v) { company = v; return this; }
    public Job location(String v) { location = v; return this; }
    public Job cities(List<String> v) { cities = v; return this; }
    public Job url(String v) { url = v; return this; }
    public Job postedAt(Instant v) { postedAt = v; return this; }
    public Job postedDate(LocalDate v) { postedDate = v; return this; }
    public Job experienceLevels(List<String> v) { experienceLevels = v; return this; }
    public Job experienceLabel(String v) { experienceLabel = v; return this; }
    public Job category(String v) { category = v; return this; }
    public Job salary(String v) { salary = v; return this; }
    public Job ats(String v) { ats = v; return this; }
    public Job snippet(String v) { snippet = v; return this; }
    public Job firstSeen(Instant v) { firstSeen = v; return this; }
}
