package com.jobhunt.exception;

import org.springframework.http.HttpStatus;

/** An error the dashboard should see as {"detail": "..."} with a specific status. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
