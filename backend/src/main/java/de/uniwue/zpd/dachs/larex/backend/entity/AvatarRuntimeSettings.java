package de.uniwue.zpd.dachs.larex.backend.entity;

import de.uniwue.zpd.dachs.larex.backend.dto.AvatarStyle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "avatar_runtime_settings")
public class AvatarRuntimeSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_style", nullable = false, length = 32)
    private AvatarStyle defaultStyle = AvatarStyle.GRADIENT;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id")
    private String updatedByUserId;

    public Short getId() {
        return id;
    }

    public AvatarStyle getDefaultStyle() {
        return defaultStyle;
    }

    public void setDefaultStyle(AvatarStyle defaultStyle) {
        this.defaultStyle = defaultStyle;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(String updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }
}
