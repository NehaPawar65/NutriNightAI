package com.nutrinight.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/** Uniform error body returned by every endpoint. */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<FieldIssue> details) {

    public record FieldIssue(String field, String message) {
    }

    public static ApiError of(int status, String message, String path) {
        return of(status, message, path, List.of());
    }

    public static ApiError of(int status, String message, String path, List<FieldIssue> details) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return new ApiError(Instant.now(), status, httpStatus == null ? "Error" : httpStatus.getReasonPhrase(),
                message, path, details);
    }
}
