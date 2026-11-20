package com.jobhunt.controller;

import com.jobhunt.dto.RefreshResponse;
import com.jobhunt.dto.StatusResponse;
import com.jobhunt.service.JobQueryService;
import com.jobhunt.service.RefreshCoordinator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Last fetched" status and the "Fetch now" button. */
@RestController
@RequestMapping("/api")
public class UpdateController {

    private final JobQueryService queries;
    private final RefreshCoordinator refresh;

    public UpdateController(JobQueryService queries, RefreshCoordinator refresh) {
        this.queries = queries;
        this.refresh = refresh;
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return queries.status();
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh() {
        return refresh.manual();
    }
}
