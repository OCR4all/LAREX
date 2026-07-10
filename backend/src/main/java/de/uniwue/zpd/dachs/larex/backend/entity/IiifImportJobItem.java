package de.uniwue.zpd.dachs.larex.backend.entity;

import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "iiif_import_job_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_iiif_import_job_item_canvas",
                columnNames = {"job_id", "canvas_index"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class IiifImportJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "canvas_id", columnDefinition = "TEXT")
    private String canvasId;

    @Column(name = "canvas_label", columnDefinition = "TEXT")
    private String canvasLabel;

    @Column(name = "canvas_index", nullable = false)
    private int canvasIndex;

    @Column(name = "requested_page_name")
    private String requestedPageName;

    @Column(name = "final_page_name")
    private String finalPageName;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String status;

    @Column(name = "page_id")
    private String pageId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "actual_bytes")
    private Long actualBytes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public static IiifImportJobItem fromResult(
            String jobId,
            IiifImportDto.ItemResult result,
            Long actualBytes
    ) {
        IiifImportJobItem item = new IiifImportJobItem();
        item.jobId = jobId;
        item.canvasId = result.canvasId();
        item.canvasLabel = result.canvasLabel();
        item.canvasIndex = result.index();
        item.requestedPageName = result.requestedPageName();
        item.finalPageName = result.finalPageName();
        item.action = result.action();
        item.status = result.status();
        item.pageId = result.pageId();
        item.message = result.message();
        item.actualBytes = actualBytes;
        return item;
    }

    public IiifImportDto.ItemResult toResult() {
        return new IiifImportDto.ItemResult(
                canvasId,
                canvasLabel,
                canvasIndex,
                requestedPageName,
                finalPageName,
                action,
                status,
                pageId,
                message
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getCanvasId() {
        return canvasId;
    }

    public void setCanvasId(String canvasId) {
        this.canvasId = canvasId;
    }

    public String getCanvasLabel() {
        return canvasLabel;
    }

    public void setCanvasLabel(String canvasLabel) {
        this.canvasLabel = canvasLabel;
    }

    public int getCanvasIndex() {
        return canvasIndex;
    }

    public void setCanvasIndex(int canvasIndex) {
        this.canvasIndex = canvasIndex;
    }

    public String getRequestedPageName() {
        return requestedPageName;
    }

    public void setRequestedPageName(String requestedPageName) {
        this.requestedPageName = requestedPageName;
    }

    public String getFinalPageName() {
        return finalPageName;
    }

    public void setFinalPageName(String finalPageName) {
        this.finalPageName = finalPageName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getActualBytes() {
        return actualBytes;
    }

    public void setActualBytes(Long actualBytes) {
        this.actualBytes = actualBytes;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}
