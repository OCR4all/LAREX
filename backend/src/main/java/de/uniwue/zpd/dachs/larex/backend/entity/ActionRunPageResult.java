package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_run_page_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_action_run_page_results_run_page",
                columnNames = {"run_id", "page_id"}),
        indexes = @Index(name = "idx_action_run_page_results_run", columnList = "run_id"))
@EntityListeners(AuditingEntityListener.class)
public class ActionRunPageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ActionRun run;

    @Column(name = "page_id", nullable = false)
    private String pageId;

    @Column(name = "result_summary_json", columnDefinition = "TEXT")
    private String resultSummaryJson;

    // Null on historical acknowledgements: their outcome cannot be reconstructed.
    @Column(name = "outcome_json", columnDefinition = "TEXT")
    private String outcomeJson;

    @Column(name = "validation_rejected", nullable = false)
    private boolean validationRejected;

    public boolean isValidationRejected() { return validationRejected; }
    public void setValidationRejected(boolean value) { validationRejected = value; }

    public String getOutcomeJson() { return outcomeJson; }
    public void setOutcomeJson(String value) { outcomeJson = value; }

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ActionRun getRun() {
        return run;
    }

    public void setRun(ActionRun run) {
        this.run = run;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getResultSummaryJson() {
        return resultSummaryJson;
    }

    public void setResultSummaryJson(String resultSummaryJson) {
        this.resultSummaryJson = resultSummaryJson;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}
