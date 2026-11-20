package com.jobhunt.repository;

import com.jobhunt.model.Job;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, String> {

    /** Jobs posted in [start, end), newest first. */
    List<Job> findByPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtDesc(Instant start, Instant end);

    boolean existsByPostedAtGreaterThanEqual(Instant from);

    /** Which of the lookback days actually hold jobs. */
    @Query("select distinct j.postedDate from Job j where j.postedAt >= :from")
    List<LocalDate> postedDatesSince(@Param("from") Instant from);

    @Query("select j.postedDate as day, count(j) as n from Job j group by j.postedDate order by j.postedDate desc")
    List<DayCount> countByDay();

    @Query("select j from Job j where j.id in :ids")
    List<Job> findAllByIdIn(@Param("ids") Collection<String> ids);

    @Modifying
    @Query("delete from Job j where j.postedAt < :cutoff")
    int deleteByPostedAtBefore(@Param("cutoff") Instant cutoff);

    interface DayCount {
        LocalDate getDay();
        long getN();
    }
}
