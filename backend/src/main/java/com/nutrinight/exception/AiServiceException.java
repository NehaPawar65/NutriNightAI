package com.nutrinight.exception;

/** The AI service responded with an error (HTTP 502 to our client, or 400 when it rejected our input). */
public class AiServiceException extends RuntimeException {

    private final int upstreamStatus;

    public AiServiceException(String message, int upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
