package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Index table for storing PAGE XML confidence values (@conf) by element type.
 * Enables efficient page filtering by confidence range and element categories.
 */
@Entity
@Table(name = "page_confidence_index", indexes = {
    @Index(name = "idx_page_confidence_index_page_id", columnList = "page_id"),
    @Index(name = "idx_page_confidence_index_element_type", columnList = "element_type"),
    @Index(name = "idx_page_confidence_index_confidence", columnList = "confidence")
})
@EntityListeners(AuditingEntityListener.class)
public class PageConfidenceIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false)
    private ElementType elementType;

    @Column(name = "element_id")
    private String elementId;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public PageConfidenceIndex() {}

    public PageConfidenceIndex(Page page, ElementType elementType, String elementId, Double confidence) {
        this.page = page;
        this.elementType = elementType;
        this.elementId = elementId;
        this.confidence = confidence;
    }

    public enum ElementType {
        PAGE,
        COORDS,
        TEXTEQUIV,
        READING_ORDER,
        BASELINE,
        ALTERNATIVE_IMAGE
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public void setElementType(ElementType elementType) {
        this.elementType = elementType;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}
