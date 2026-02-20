package com.aipaper.exception;

public class ParameterMismatchException extends LlmResponseValidationException {

    public ParameterMismatchException(String message) {
        super(message);
    }

    public ParameterMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
