package de.uniwue.zpd.dachs.larex.backend.dto;

public enum AdminUserAuditAction {
    CREATE,
    ENABLE,
    DISABLE,
    RESEND_SETUP_EMAIL,
    GLOBAL_CURATOR_GRANT,
    GLOBAL_CURATOR_REVOKE,
    PRIVATE_ACCESS_TOKENS_ENABLE,
    PRIVATE_ACCESS_TOKENS_DISABLE
}
