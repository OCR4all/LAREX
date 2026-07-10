package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "iiif_runtime_settings")
public class IiifRuntimeSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "download_min_interval_ms")
    private Integer downloadMinIntervalMs;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id")
    private String updatedByUserId;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public Integer getDownloadMinIntervalMs() {
        return downloadMinIntervalMs;
    }

    public void setDownloadMinIntervalMs(Integer downloadMinIntervalMs) {
        this.downloadMinIntervalMs = downloadMinIntervalMs;
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
