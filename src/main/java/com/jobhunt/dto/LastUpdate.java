package com.jobhunt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** "Last fetched" panel on the dashboard. */
public record LastUpdate(String at, String label, int kept, @JsonProperty("new") int newJobs, int boards, String mode) {}
