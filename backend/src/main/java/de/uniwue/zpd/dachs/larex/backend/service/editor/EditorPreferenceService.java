package de.uniwue.zpd.dachs.larex.backend.service.editor;

import de.uniwue.zpd.dachs.larex.backend.controller.editor.EditorPreferenceController.EditorPreferenceDto;
import de.uniwue.zpd.dachs.larex.backend.entity.EditorPreference;
import de.uniwue.zpd.dachs.larex.backend.repository.editor.EditorPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EditorPreferenceService {

    private final EditorPreferenceRepository repository;

    public EditorPreferenceService(EditorPreferenceRepository repository) {
        this.repository = repository;
    }

    public EditorPreference getOrCreate(String userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> repository.save(new EditorPreference(userId)));
    }

    public EditorPreference update(String userId, EditorPreferenceDto dto) {
        EditorPreference pref = getOrCreate(userId);
        
        if (dto.backgroundColor() != null) pref.setBackgroundColor(dto.backgroundColor());
        if (dto.backgroundOpacity() != null) pref.setBackgroundOpacity(dto.backgroundOpacity());
        if (dto.toolbarLayout() != null) pref.setToolbarLayout(dto.toolbarLayout());
        if (dto.toolbarCompact() != null) pref.setToolbarCompact(dto.toolbarCompact());
        if (dto.toolbarFloatingOrientation() != null) pref.setToolbarFloatingOrientation(dto.toolbarFloatingOrientation());
        if (dto.toolbarFloatingX() != null) pref.setToolbarFloatingX(dto.toolbarFloatingX());
        if (dto.toolbarFloatingY() != null) pref.setToolbarFloatingY(dto.toolbarFloatingY());
        if (dto.leftCollapsed() != null) pref.setLeftCollapsed(dto.leftCollapsed());
        if (dto.rightCollapsed() != null) pref.setRightCollapsed(dto.rightCollapsed());
        if (dto.leftWidthPx() != null) pref.setLeftWidthPx(dto.leftWidthPx());
        if (dto.rightWidthPx() != null) pref.setRightWidthPx(dto.rightWidthPx());
        if (dto.constrainToImage() != null) pref.setConstrainToImage(dto.constrainToImage());
        if (dto.constrainToParent() != null) pref.setConstrainToParent(dto.constrainToParent());
        if (dto.autoSelect() != null) pref.setAutoSelect(dto.autoSelect());
        if (dto.preventOverlapOnCreate() != null) pref.setPreventOverlapOnCreate(dto.preventOverlapOnCreate());
        if (dto.moveWithChildren() != null) pref.setMoveWithChildren(dto.moveWithChildren());
        if (dto.cutMinAreaThreshold() != null) pref.setCutMinAreaThreshold(dto.cutMinAreaThreshold());
        if (dto.defaultLineWidth() != null) pref.setDefaultLineWidth(dto.defaultLineWidth());
        if (dto.textViewFontSize() != null) pref.setTextViewFontSize(dto.textViewFontSize());
        if (dto.textViewPadding() != null) pref.setTextViewPadding(dto.textViewPadding());
        if (dto.textViewCutoutHeight() != null) pref.setTextViewCutoutHeight(dto.textViewCutoutHeight());
        if (dto.textItemLayout() != null) pref.setTextItemLayout(dto.textItemLayout());
        if (dto.canvasTextCorrectionOverlaySnapToLine() != null) pref.setCanvasTextCorrectionOverlaySnapToLine(dto.canvasTextCorrectionOverlaySnapToLine());
        if (dto.canvasTextCorrectionOverlayXRatio() != null) pref.setCanvasTextCorrectionOverlayXRatio(dto.canvasTextCorrectionOverlayXRatio());
        if (dto.canvasTextCorrectionOverlayYRatio() != null) pref.setCanvasTextCorrectionOverlayYRatio(dto.canvasTextCorrectionOverlayYRatio());
        if (dto.canvasTextCorrectionZoom() != null) pref.setCanvasTextCorrectionZoom(dto.canvasTextCorrectionZoom());
        if (dto.textModeSubmode() != null) pref.setTextModeSubmode(dto.textModeSubmode());
        if (dto.highlightUnknownCodecChars() != null) pref.setHighlightUnknownCodecChars(dto.highlightUnknownCodecChars());
        if (dto.shortcutBindings() != null) pref.setShortcutBindings(dto.shortcutBindings());
        if (dto.onboardingDashboardTourVersion() != null) pref.setOnboardingDashboardTourVersion(dto.onboardingDashboardTourVersion());
        if (dto.onboardingEditorTourVersion() != null) pref.setOnboardingEditorTourVersion(dto.onboardingEditorTourVersion());
        if (dto.onboardingTourCompletion() != null) pref.setOnboardingTourCompletion(dto.onboardingTourCompletion());
        if (dto.onboardingToursOptedOut() != null) pref.setOnboardingToursOptedOut(dto.onboardingToursOptedOut());

        return repository.save(pref);
    }
}
