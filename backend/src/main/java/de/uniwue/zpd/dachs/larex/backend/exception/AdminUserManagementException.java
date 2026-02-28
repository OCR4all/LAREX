package de.uniwue.zpd.dachs.larex.backend.exception;

public class AdminUserManagementException extends RuntimeException {

    private final AdminUserErrorCode code;

    public AdminUserManagementException(AdminUserErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AdminUserManagementException(AdminUserErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AdminUserErrorCode getCode() {
        return code;
    }
}
