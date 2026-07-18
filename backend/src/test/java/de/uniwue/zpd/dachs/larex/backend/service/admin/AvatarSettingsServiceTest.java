package de.uniwue.zpd.dachs.larex.backend.service.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.AvatarStyle;
import de.uniwue.zpd.dachs.larex.backend.entity.AvatarRuntimeSettings;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.AvatarRuntimeSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarSettingsServiceTest {

    private AvatarRuntimeSettingsRepository repository;
    private AvatarSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AvatarRuntimeSettingsRepository.class);
        service = new AvatarSettingsService(repository);
    }

    @Test
    void usesGradientWhenTheSettingsRowIsMissing() {
        when(repository.findById(AvatarRuntimeSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThat(service.getPublicSettings().defaultStyle()).isEqualTo(AvatarStyle.GRADIENT);
    }

    @Test
    void persistsTheSelectedStyleAndAuditMetadata() {
        when(repository.findById(AvatarRuntimeSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AvatarRuntimeSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateSettings(AvatarStyle.FLOW_FIELD, "admin-1");

        assertThat(response.defaultStyle()).isEqualTo(AvatarStyle.FLOW_FIELD);
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.updatedByUserId()).isEqualTo("admin-1");

        ArgumentCaptor<AvatarRuntimeSettings> captor = ArgumentCaptor.forClass(AvatarRuntimeSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDefaultStyle()).isEqualTo(AvatarStyle.FLOW_FIELD);
        assertThat(captor.getValue().getUpdatedByUserId()).isEqualTo("admin-1");
    }
}
