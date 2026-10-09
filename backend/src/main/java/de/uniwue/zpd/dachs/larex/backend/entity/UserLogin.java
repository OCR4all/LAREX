package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_logins")
public class UserLogin {

    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected UserLogin() {
    }

    public String getUserId() {
        return userId;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
