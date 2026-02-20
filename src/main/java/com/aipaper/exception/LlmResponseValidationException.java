package com.aipaper.exception;

public class LlmResponseValidationException extends RuntimeException {

    public LlmResponseValidationException(String message) {
        super(message);
    }

    public LlmResponseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
