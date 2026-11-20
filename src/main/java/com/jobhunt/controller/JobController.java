package com.jobhunt.controller;

import com.jobhunt.dto.MetaResponse;
import com.jobhunt.dto.SearchRequest;
import com.jobhunt.dto.SearchResponse;
import com.jobhunt.service.JobQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Jobs and filter options for the dashboard. */
@RestController
@RequestMapping("/api")
public class JobController {

    private final JobQueryService queries;

    public JobController(JobQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/meta")
    public MetaResponse meta() {
        return queries.meta();
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return queries.search(request);
    }
}
