package de.uniwue.zpd.dachs.larex.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "editor_preferences", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
@EntityListeners(AuditingEntityListener.class)
public class EditorPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "user_id", unique = true)
    private String userId;

    // Background settings
    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "background_opacity")
    private Double backgroundOpacity;

    // Toolbar layout
    @Column(name = "toolbar_layout")
    private String toolbarLayout;

    @Column(name = "toolbar_compact")
    private Boolean toolbarCompact;

    // Sidebar states
    @Column(name = "left_collapsed")
    private Boolean leftCollapsed;

    @Column(name = "right_collapsed")
    private Boolean rightCollapsed;

    @Column(name = "left_width_px")
    private Integer leftWidthPx;

    @Column(name = "right_width_px")
    private Integer rightWidthPx;

    // Global settings
    @Column(name = "constrain_to_image")
    private Boolean constrainToImage;

    @Column(name = "constrain_to_parent")
    private Boolean constrainToParent;

    @Column(name = "auto_select")
    private Boolean autoSelect;

    @Column(name = "prevent_overlap_on_create")
    private Boolean preventOverlapOnCreate;

    @Column(name = "move_with_children")
    private Boolean moveWithChildren;

    @Column(name = "cut_min_area_threshold")
    private Double cutMinAreaThreshold;

    @Column(name = "default_line_width")
    private String defaultLineWidth;

    @Column(name = "text_view_font_size")
    private Integer textViewFontSize;

    @Column(name = "text_view_padding")
    private Integer textViewPadding;

    @Column(name = "text_item_layout")
    private String textItemLayout;

    @Column(name = "canvas_text_correction_overlay_snap_to_line")
    private Boolean canvasTextCorrectionOverlaySnapToLine;

    @Column(name = "canvas_text_correction_overlay_x_ratio")
    private Double canvasTextCorrectionOverlayXRatio;

    @Column(name = "canvas_text_correction_overlay_y_ratio")
    private Double canvasTextCorrectionOverlayYRatio;

    @Column(name = "canvas_text_correction_zoom")
    private Double canvasTextCorrectionZoom;

    @Column(name = "text_mode_submode")
    private String textModeSubmode;

    @Column(name = "highlight_unknown_codec_chars")
    private Boolean highlightUnknownCodecChars;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shortcut_bindings", columnDefinition = "jsonb")
    private JsonNode shortcutBindings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_column_visibility", columnDefinition = "jsonb")
    private JsonNode tableColumnVisibility;

    @Column(name = "onboarding_dashboard_tour_version")
    private Integer onboardingDashboardTourVersion;

    @Column(name = "onboarding_editor_tour_version")
    private Integer onboardingEditorTourVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "onboarding_tour_completion", columnDefinition = "jsonb")
    private JsonNode onboardingTourCompletion;

    @Column(name = "onboarding_tours_opted_out")
    private Boolean onboardingToursOptedOut;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public EditorPreference() {}

    public EditorPreference(String userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public Double getBackgroundOpacity() { return backgroundOpacity; }
    public void setBackgroundOpacity(Double backgroundOpacity) { this.backgroundOpacity = backgroundOpacity; }

    public String getToolbarLayout() { return toolbarLayout; }
    public void setToolbarLayout(String toolbarLayout) { this.toolbarLayout = toolbarLayout; }

    public Boolean getToolbarCompact() { return toolbarCompact; }
    public void setToolbarCompact(Boolean toolbarCompact) { this.toolbarCompact = toolbarCompact; }

    public Boolean getLeftCollapsed() { return leftCollapsed; }
    public void setLeftCollapsed(Boolean leftCollapsed) { this.leftCollapsed = leftCollapsed; }

    public Boolean getRightCollapsed() { return rightCollapsed; }
    public void setRightCollapsed(Boolean rightCollapsed) { this.rightCollapsed = rightCollapsed; }

    public Integer getLeftWidthPx() { return leftWidthPx; }
    public void setLeftWidthPx(Integer leftWidthPx) { this.leftWidthPx = leftWidthPx; }

    public Integer getRightWidthPx() { return rightWidthPx; }
    public void setRightWidthPx(Integer rightWidthPx) { this.rightWidthPx = rightWidthPx; }

    public Boolean getConstrainToImage() { return constrainToImage; }
    public void setConstrainToImage(Boolean constrainToImage) { this.constrainToImage = constrainToImage; }

    public Boolean getConstrainToParent() { return constrainToParent; }
    public void setConstrainToParent(Boolean constrainToParent) { this.constrainToParent = constrainToParent; }

    public Boolean getAutoSelect() { return autoSelect; }
    public void setAutoSelect(Boolean autoSelect) { this.autoSelect = autoSelect; }

    public Boolean getPreventOverlapOnCreate() { return preventOverlapOnCreate; }
    public void setPreventOverlapOnCreate(Boolean preventOverlapOnCreate) { this.preventOverlapOnCreate = preventOverlapOnCreate; }

    public Boolean getMoveWithChildren() { return moveWithChildren; }
    public void setMoveWithChildren(Boolean moveWithChildren) { this.moveWithChildren = moveWithChildren; }

    public Double getCutMinAreaThreshold() { return cutMinAreaThreshold; }
    public void setCutMinAreaThreshold(Double cutMinAreaThreshold) { this.cutMinAreaThreshold = cutMinAreaThreshold; }

    public String getDefaultLineWidth() { return defaultLineWidth; }
    public void setDefaultLineWidth(String defaultLineWidth) { this.defaultLineWidth = defaultLineWidth; }

    public Integer getTextViewFontSize() { return textViewFontSize; }
    public void setTextViewFontSize(Integer textViewFontSize) { this.textViewFontSize = textViewFontSize; }

    public Integer getTextViewPadding() { return textViewPadding; }
    public void setTextViewPadding(Integer textViewPadding) { this.textViewPadding = textViewPadding; }

    public String getTextItemLayout() { return textItemLayout; }
    public void setTextItemLayout(String textItemLayout) { this.textItemLayout = textItemLayout; }

    public Boolean getCanvasTextCorrectionOverlaySnapToLine() { return canvasTextCorrectionOverlaySnapToLine; }
    public void setCanvasTextCorrectionOverlaySnapToLine(Boolean canvasTextCorrectionOverlaySnapToLine) { this.canvasTextCorrectionOverlaySnapToLine = canvasTextCorrectionOverlaySnapToLine; }

    public Double getCanvasTextCorrectionOverlayXRatio() { return canvasTextCorrectionOverlayXRatio; }
    public void setCanvasTextCorrectionOverlayXRatio(Double canvasTextCorrectionOverlayXRatio) { this.canvasTextCorrectionOverlayXRatio = canvasTextCorrectionOverlayXRatio; }

    public Double getCanvasTextCorrectionOverlayYRatio() { return canvasTextCorrectionOverlayYRatio; }
    public void setCanvasTextCorrectionOverlayYRatio(Double canvasTextCorrectionOverlayYRatio) { this.canvasTextCorrectionOverlayYRatio = canvasTextCorrectionOverlayYRatio; }

    public Double getCanvasTextCorrectionZoom() { return canvasTextCorrectionZoom; }
    public void setCanvasTextCorrectionZoom(Double canvasTextCorrectionZoom) { this.canvasTextCorrectionZoom = canvasTextCorrectionZoom; }

    public String getTextModeSubmode() { return textModeSubmode; }
    public void setTextModeSubmode(String textModeSubmode) { this.textModeSubmode = textModeSubmode; }

    public Boolean getHighlightUnknownCodecChars() { return highlightUnknownCodecChars; }
    public void setHighlightUnknownCodecChars(Boolean highlightUnknownCodecChars) { this.highlightUnknownCodecChars = highlightUnknownCodecChars; }

    public JsonNode getShortcutBindings() { return shortcutBindings; }
    public void setShortcutBindings(JsonNode shortcutBindings) { this.shortcutBindings = shortcutBindings; }

    public JsonNode getTableColumnVisibility() { return tableColumnVisibility; }
    public void setTableColumnVisibility(JsonNode tableColumnVisibility) { this.tableColumnVisibility = tableColumnVisibility; }

    public Integer getOnboardingDashboardTourVersion() { return onboardingDashboardTourVersion; }
    public void setOnboardingDashboardTourVersion(Integer onboardingDashboardTourVersion) { this.onboardingDashboardTourVersion = onboardingDashboardTourVersion; }

    public Integer getOnboardingEditorTourVersion() { return onboardingEditorTourVersion; }
    public void setOnboardingEditorTourVersion(Integer onboardingEditorTourVersion) { this.onboardingEditorTourVersion = onboardingEditorTourVersion; }

    public JsonNode getOnboardingTourCompletion() { return onboardingTourCompletion; }
    public void setOnboardingTourCompletion(JsonNode onboardingTourCompletion) { this.onboardingTourCompletion = onboardingTourCompletion; }

    public Boolean getOnboardingToursOptedOut() { return onboardingToursOptedOut; }
    public void setOnboardingToursOptedOut(Boolean onboardingToursOptedOut) { this.onboardingToursOptedOut = onboardingToursOptedOut; }

    public LocalDateTime getCreated() { return created; }
    public LocalDateTime getUpdated() { return updated; }
}
