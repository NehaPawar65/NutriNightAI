package com.nutrinight.exception;

/** A client error (HTTP 400) with a user-readable message. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
