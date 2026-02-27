package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "board_themes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_board_theme_workspace_name", columnNames = {"workspace_id", "name"})
    }
)
@EntityListeners(AuditingEntityListener.class)
public class BoardTheme {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "bg_class")
    private String bgClass;

    @Column(name = "border_class")
    private String borderClass;

    @Column(name = "grid_line_class")
    private String gridLineClass;

    @Column(name = "key_bg_class")
    private String keyBgClass;

    @Column(name = "key_text_class")
    private String keyTextClass;

    @Column(name = "preview_class")
    private String previewClass;

    @Column(name = "bg_style")
    private String bgStyle;

    @Column(name = "key_bg_style")
    private String keyBgStyle;

    @Column(name = "key_text_style")
    private String keyTextStyle;

    public BoardTheme() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getBgClass() {
        return bgClass;
    }

    public void setBgClass(String bgClass) {
        this.bgClass = bgClass;
    }

    public String getBorderClass() {
        return borderClass;
    }

    public void setBorderClass(String borderClass) {
        this.borderClass = borderClass;
    }

    public String getGridLineClass() {
        return gridLineClass;
    }

    public void setGridLineClass(String gridLineClass) {
        this.gridLineClass = gridLineClass;
    }

    public String getKeyBgClass() {
        return keyBgClass;
    }

    public void setKeyBgClass(String keyBgClass) {
        this.keyBgClass = keyBgClass;
    }

    public String getKeyTextClass() {
        return keyTextClass;
    }

    public void setKeyTextClass(String keyTextClass) {
        this.keyTextClass = keyTextClass;
    }

    public String getPreviewClass() {
        return previewClass;
    }

    public void setPreviewClass(String previewClass) {
        this.previewClass = previewClass;
    }

    public String getBgStyle() {
        return bgStyle;
    }

    public void setBgStyle(String bgStyle) {
        this.bgStyle = bgStyle;
    }

    public String getKeyBgStyle() {
        return keyBgStyle;
    }

    public void setKeyBgStyle(String keyBgStyle) {
        this.keyBgStyle = keyBgStyle;
    }

    public String getKeyTextStyle() {
        return keyTextStyle;
    }

    public void setKeyTextStyle(String keyTextStyle) {
        this.keyTextStyle = keyTextStyle;
    }
}
