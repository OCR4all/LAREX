package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "search_lexicon_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uk_search_lexicon_workspace_project_token",
            columnNames = {"workspace_id", "project_id", "normalized_token"})
})
public class SearchLexiconEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "normalized_token", nullable = false)
    private String normalizedToken;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    public SearchLexiconEntry() {
    }

    public SearchLexiconEntry(String workspaceId, String projectId, String normalizedToken, int occurrenceCount) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.normalizedToken = normalizedToken;
        this.occurrenceCount = occurrenceCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getNormalizedToken() {
        return normalizedToken;
    }

    public void setNormalizedToken(String normalizedToken) {
        this.normalizedToken = normalizedToken;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }
}
