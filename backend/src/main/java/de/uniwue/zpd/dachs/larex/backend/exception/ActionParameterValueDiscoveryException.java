package de.uniwue.zpd.dachs.larex.backend.exception;

public class ActionParameterValueDiscoveryException extends RuntimeException {

    public ActionParameterValueDiscoveryException(String message) {
        super(message);
    }

    public ActionParameterValueDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
