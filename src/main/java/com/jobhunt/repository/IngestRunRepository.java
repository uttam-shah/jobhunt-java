package com.jobhunt.repository;

import com.jobhunt.model.IngestRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {

    Optional<IngestRun> findTopByOkTrueOrderByIdDesc();

    Optional<IngestRun> findTopByOrderByIdDesc();
}
