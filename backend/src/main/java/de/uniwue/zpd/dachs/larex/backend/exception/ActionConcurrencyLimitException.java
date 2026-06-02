package de.uniwue.zpd.dachs.larex.backend.exception;

public class ActionConcurrencyLimitException extends IllegalStateException {

    public ActionConcurrencyLimitException(String message) {
        super(message);
    }
}
