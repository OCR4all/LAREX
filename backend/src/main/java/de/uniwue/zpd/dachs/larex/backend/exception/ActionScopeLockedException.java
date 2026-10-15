package de.uniwue.zpd.dachs.larex.backend.exception;

public class ActionScopeLockedException extends IllegalStateException {

    public ActionScopeLockedException(String message) {
        super(message);
    }
}
