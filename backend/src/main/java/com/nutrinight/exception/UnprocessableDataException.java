package com.nutrinight.exception;

/** The request was valid but the data cannot be processed, e.g. no orders in the selected period (HTTP 422). */
public class UnprocessableDataException extends RuntimeException {

    public UnprocessableDataException(String message) {
        super(message);
    }
}
