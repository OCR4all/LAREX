package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_processor_definitions", indexes = {
        @Index(name = "idx_action_processor_key", columnList = "processor_key", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class ActionProcessorDefinition {

    public enum ExecuteRole {
        EDITOR,
        CURATOR
    }

    public enum LockMode {
        PAGES,
        PROJECT
    }

    public enum ActionCategory {
        WORKFLOW,
        OCR_HTR,
        LAYOUT
    }

    public enum ActionTarget {
        PAGE,
        REGION,
        TEXT_LINE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, name = "processor_key", length = 128)
    private String processorKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT", name = "yaml_source")
    private String yamlSource;

    @Column(nullable = false, columnDefinition = "TEXT", name = "parsed_json")
    private String parsedJson;

    @Column(nullable = false, columnDefinition = "TEXT", name = "endpoint_url")
    private String endpointUrl;

    @Column(nullable = false, name = "endpoint_timeout_seconds")
    private int endpointTimeoutSeconds = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "execute_role", length = 32)
    private ExecuteRole executeRole = ExecuteRole.CURATOR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "lock_mode", length = 32)
    private LockMode lockMode = LockMode.PAGES;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "category", length = 32, columnDefinition = "varchar(32) default 'WORKFLOW'")
    private ActionCategory category = ActionCategory.WORKFLOW;

    @Column(nullable = false, name = "target_types_json", columnDefinition = "TEXT")
    private String targetTypesJson = "[\"PAGE\"]";

    @Column(nullable = false, name = "accepts_images")
    private boolean acceptsImages = false;

    @Column(nullable = false, name = "accepts_xml")
    private boolean acceptsXml = false;

    @Column(nullable = false, name = "outputs_images")
    private boolean outputsImages = false;

    @Column(nullable = false, name = "outputs_xml")
    private boolean outputsXml = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, name = "global_available", columnDefinition = "boolean default false")
    private boolean globalAvailable = false;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @Column(nullable = false, name = "updated_by_user_id")
    private String updatedByUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProcessorKey() {
        return processorKey;
    }

    public void setProcessorKey(String processorKey) {
        this.processorKey = processorKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getYamlSource() {
        return yamlSource;
    }

    public void setYamlSource(String yamlSource) {
        this.yamlSource = yamlSource;
    }

    public String getParsedJson() {
        return parsedJson;
    }

    public void setParsedJson(String parsedJson) {
        this.parsedJson = parsedJson;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public int getEndpointTimeoutSeconds() {
        return endpointTimeoutSeconds;
    }

    public void setEndpointTimeoutSeconds(int endpointTimeoutSeconds) {
        this.endpointTimeoutSeconds = endpointTimeoutSeconds;
    }

    public ExecuteRole getExecuteRole() {
        return executeRole;
    }

    public void setExecuteRole(ExecuteRole executeRole) {
        this.executeRole = executeRole;
    }

    public LockMode getLockMode() {
        return lockMode;
    }

    public void setLockMode(LockMode lockMode) {
        this.lockMode = lockMode;
    }

    public ActionCategory getCategory() {
        return category;
    }

    public void setCategory(ActionCategory category) {
        this.category = category;
    }

    public String getTargetTypesJson() {
        return targetTypesJson;
    }

    public void setTargetTypesJson(String targetTypesJson) {
        this.targetTypesJson = targetTypesJson;
    }

    public boolean isAcceptsImages() {
        return acceptsImages;
    }

    public void setAcceptsImages(boolean acceptsImages) {
        this.acceptsImages = acceptsImages;
    }

    public boolean isAcceptsXml() {
        return acceptsXml;
    }

    public void setAcceptsXml(boolean acceptsXml) {
        this.acceptsXml = acceptsXml;
    }

    public boolean isOutputsImages() {
        return outputsImages;
    }

    public void setOutputsImages(boolean outputsImages) {
        this.outputsImages = outputsImages;
    }

    public boolean isOutputsXml() {
        return outputsXml;
    }

    public void setOutputsXml(boolean outputsXml) {
        this.outputsXml = outputsXml;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGlobalAvailable() {
        return globalAvailable;
    }

    public void setGlobalAvailable(boolean globalAvailable) {
        this.globalAvailable = globalAvailable;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(String updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }
}
