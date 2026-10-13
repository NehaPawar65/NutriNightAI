package com.nutrinight.exception;

/** The AI service could not be reached or timed out (HTTP 503). */
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
