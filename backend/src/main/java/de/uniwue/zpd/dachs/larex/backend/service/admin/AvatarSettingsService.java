package de.uniwue.zpd.dachs.larex.backend.service.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.AvatarSettingsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AvatarStyle;
import de.uniwue.zpd.dachs.larex.backend.entity.AvatarRuntimeSettings;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.AvatarRuntimeSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AvatarSettingsService {

    private final AvatarRuntimeSettingsRepository repository;

    public AvatarSettingsService(AvatarRuntimeSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AvatarSettingsDto.PublicResponse getPublicSettings() {
        return new AvatarSettingsDto.PublicResponse(loadSettings().getDefaultStyle());
    }

    @Transactional(readOnly = true)
    public AvatarSettingsDto.AdminResponse getAdminSettings() {
        return toAdminResponse(loadSettings());
    }

    @Transactional
    public AvatarSettingsDto.AdminResponse updateSettings(AvatarStyle defaultStyle, String updatedByUserId) {
        AvatarRuntimeSettings settings = loadSettings();
        settings.setDefaultStyle(defaultStyle);
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedByUserId(updatedByUserId);
        return toAdminResponse(repository.save(settings));
    }

    private AvatarRuntimeSettings loadSettings() {
        return repository.findById(AvatarRuntimeSettings.SINGLETON_ID)
                .orElseGet(AvatarRuntimeSettings::new);
    }

    private AvatarSettingsDto.AdminResponse toAdminResponse(AvatarRuntimeSettings settings) {
        return new AvatarSettingsDto.AdminResponse(
                settings.getDefaultStyle(),
                settings.getUpdatedAt(),
                settings.getUpdatedByUserId()
        );
    }
}
