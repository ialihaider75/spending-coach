package com.intellermatrix.spending_coach.agent.provider;

public class ProviderUnavailableException extends RuntimeException {

    private final Integer statusCode;

    public ProviderUnavailableException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
