package com.jobhunt.dto;

public record StatusResponse(boolean running, LastUpdate lastUpdate, String lastError, int boards, long totalJobs) {}
