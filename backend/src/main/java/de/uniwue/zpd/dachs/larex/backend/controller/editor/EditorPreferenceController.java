package de.uniwue.zpd.dachs.larex.backend.controller.editor;

import com.fasterxml.jackson.databind.JsonNode;
import de.uniwue.zpd.dachs.larex.backend.entity.EditorPreference;
import de.uniwue.zpd.dachs.larex.backend.service.editor.EditorPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/editor/preferences")
public class EditorPreferenceController {

    private final EditorPreferenceService service;

    public EditorPreferenceController(EditorPreferenceService service) {
        this.service = service;
    }

    public record EditorPreferenceDto(
            String backgroundColor,
            Double backgroundOpacity,
            String toolbarLayout,
            Boolean toolbarCompact,
            Boolean leftCollapsed,
            Boolean rightCollapsed,
            Integer leftWidthPx,
            Integer rightWidthPx,
            Boolean constrainToImage,
            Boolean constrainToParent,
            Boolean autoSelect,
            Boolean preventOverlapOnCreate,
            Boolean moveWithChildren,
            Double cutMinAreaThreshold,
            String defaultLineWidth,
            Integer textViewFontSize,
            Integer textViewPadding,
            String textItemLayout,
            Boolean canvasTextCorrectionOverlaySnapToLine,
            Double canvasTextCorrectionOverlayXRatio,
            Double canvasTextCorrectionOverlayYRatio,
            Double canvasTextCorrectionZoom,
            String textModeSubmode,
            Boolean highlightUnknownCodecChars,
            JsonNode shortcutBindings,
            JsonNode tableColumnVisibility,
            Integer onboardingDashboardTourVersion,
            Integer onboardingEditorTourVersion,
            JsonNode onboardingTourCompletion,
            Boolean onboardingToursOptedOut
    ) {
        public static EditorPreferenceDto from(EditorPreference pref) {
            return new EditorPreferenceDto(
                    pref.getBackgroundColor(),
                    pref.getBackgroundOpacity(),
                    pref.getToolbarLayout(),
                    pref.getToolbarCompact(),
                    pref.getLeftCollapsed(),
                    pref.getRightCollapsed(),
                    pref.getLeftWidthPx(),
                    pref.getRightWidthPx(),
                    pref.getConstrainToImage(),
                    pref.getConstrainToParent(),
                    pref.getAutoSelect(),
                    pref.getPreventOverlapOnCreate(),
                    pref.getMoveWithChildren(),
                    pref.getCutMinAreaThreshold(),
                    pref.getDefaultLineWidth(),
                    pref.getTextViewFontSize(),
                    pref.getTextViewPadding(),
                    pref.getTextItemLayout(),
                    pref.getCanvasTextCorrectionOverlaySnapToLine(),
                    pref.getCanvasTextCorrectionOverlayXRatio(),
                    pref.getCanvasTextCorrectionOverlayYRatio(),
                    pref.getCanvasTextCorrectionZoom(),
                    pref.getTextModeSubmode(),
                    pref.getHighlightUnknownCodecChars(),
                    pref.getShortcutBindings(),
                    pref.getTableColumnVisibility(),
                    pref.getOnboardingDashboardTourVersion(),
                    pref.getOnboardingEditorTourVersion(),
                    pref.getOnboardingTourCompletion(),
                    pref.getOnboardingToursOptedOut()
            );
        }
    }

    @GetMapping
    public ResponseEntity<EditorPreferenceDto> get(@AuthenticationPrincipal(expression = "subject") String userId) {
        EditorPreference pref = service.getOrCreate(userId);
        return ResponseEntity.ok(EditorPreferenceDto.from(pref));
    }

    @PutMapping
    public ResponseEntity<EditorPreferenceDto> update(
            @Valid @RequestBody EditorPreferenceDto dto,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        EditorPreference pref = service.update(userId, dto);
        return ResponseEntity.ok(EditorPreferenceDto.from(pref));
    }
}
