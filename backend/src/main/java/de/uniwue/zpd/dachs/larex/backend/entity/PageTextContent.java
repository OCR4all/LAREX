package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Index table for storing extracted text content from PAGE XML files.
 * Enables efficient full-text search across pages without parsing XML on every query.
 * 
 * Populated/updated when annotations are saved via AnnotationProcessingService.
 */
@Entity
@Table(name = "page_text_content", indexes = {
    @Index(name = "idx_page_text_content_page_id", columnList = "page_id"),
    @Index(name = "idx_page_text_content_text", columnList = "text_content")
})
@EntityListeners(AuditingEntityListener.class)
public class PageTextContent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;

    @Column(name = "text_line_id")
    private String textLineId;

    @Column(name = "region_id")
    private String regionId;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "variant_index")
    private Integer variantIndex;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public PageTextContent() {}

    public PageTextContent(Page page, String textLineId, String regionId, String textContent, Integer variantIndex) {
        this.page = page;
        this.textLineId = textLineId;
        this.regionId = regionId;
        this.textContent = textContent;
        this.variantIndex = variantIndex;
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

    public String getTextLineId() {
        return textLineId;
    }

    public void setTextLineId(String textLineId) {
        this.textLineId = textLineId;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public Integer getVariantIndex() {
        return variantIndex;
    }

    public void setVariantIndex(Integer variantIndex) {
        this.variantIndex = variantIndex;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}
