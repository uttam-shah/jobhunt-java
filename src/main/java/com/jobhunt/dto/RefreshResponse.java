package com.jobhunt.dto;

public record RefreshResponse(boolean started, boolean running, String reason) {}
