package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Deduplicated PAGE XML attribute occurrence used for page filtering.
 */
@Entity
@Table(name = "page_xml_attribute_index", indexes = {
    @Index(name = "idx_page_xml_attribute_index_page_id", columnList = "page_id"),
    @Index(name = "idx_page_xml_attribute_index_name", columnList = "attribute_name"),
    @Index(name = "idx_page_xml_attribute_index_element_name", columnList = "element_name, attribute_name")
})
@EntityListeners(AuditingEntityListener.class)
public class PageXmlAttributeIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;

    @Column(name = "element_name", nullable = false)
    private String elementName;

    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    @Column(name = "attribute_value", nullable = false, columnDefinition = "TEXT")
    private String attributeValue;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public PageXmlAttributeIndex() {}

    public PageXmlAttributeIndex(Page page, String elementName, String attributeName, String attributeValue) {
        this.page = page;
        this.elementName = elementName;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Page getPage() { return page; }
    public void setPage(Page page) { this.page = page; }
    public String getElementName() { return elementName; }
    public void setElementName(String elementName) { this.elementName = elementName; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String attributeName) { this.attributeName = attributeName; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String attributeValue) { this.attributeValue = attributeValue; }
    public LocalDateTime getCreated() { return created; }
    public void setCreated(LocalDateTime created) { this.created = created; }
}
