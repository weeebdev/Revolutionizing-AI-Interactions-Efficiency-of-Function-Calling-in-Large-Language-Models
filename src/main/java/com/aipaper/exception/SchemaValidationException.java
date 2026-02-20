package com.aipaper.exception;

public class SchemaValidationException extends LlmResponseValidationException {

    public SchemaValidationException(String message) {
        super(message);
    }

    public SchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
