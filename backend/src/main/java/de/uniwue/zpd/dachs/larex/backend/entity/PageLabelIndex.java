package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Index table for storing label assignments from PAGE XML files.
 * Enables efficient filtering by labels without parsing XML on every query.
 * 
 * Populated/updated when annotations are saved via AnnotationProcessingService.
 */
@Entity
@Table(name = "page_label_index", indexes = {
    @Index(name = "idx_page_label_index_page_id", columnList = "page_id"),
    @Index(name = "idx_page_label_index_label_id", columnList = "label_id")
})
@EntityListeners(AuditingEntityListener.class)
public class PageLabelIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;

    @Column(name = "label_id", nullable = false)
    private String labelId;

    @Column(name = "element_id", nullable = false)
    private String elementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false)
    private ElementType elementType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public PageLabelIndex() {}

    public PageLabelIndex(Page page, String labelId, String elementId, ElementType elementType) {
        this.page = page;
        this.labelId = labelId;
        this.elementId = elementId;
        this.elementType = elementType;
    }

    // Element type enum
    public enum ElementType {
        REGION,
        LINE
    }

    // Getters and Setters

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

    public String getLabelId() {
        return labelId;
    }

    public void setLabelId(String labelId) {
        this.labelId = labelId;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public void setElementType(ElementType elementType) {
        this.elementType = elementType;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}
