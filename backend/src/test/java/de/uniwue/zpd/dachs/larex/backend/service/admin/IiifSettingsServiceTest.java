package de.uniwue.zpd.dachs.larex.backend.service.admin;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifSettingsDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifRuntimeSettings;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.IiifRuntimeSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IiifSettingsServiceTest {

    private IiifRuntimeSettingsRepository repository;
    private IiifSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(IiifRuntimeSettingsRepository.class);
        IiifProperties properties = new IiifProperties();
        properties.setDownloadMinIntervalMs(100);
        service = new IiifSettingsService(repository, properties);
    }

    @Test
    void usesDeploymentDefaultWithoutOverride() {
        IiifSettingsDto.Response response = service.getSettings();

        assertThat(response.deploymentDefaultDownloadMinIntervalMs()).isEqualTo(100);
        assertThat(response.overrideDownloadMinIntervalMs()).isNull();
        assertThat(response.effectiveDownloadMinIntervalMs()).isEqualTo(100);
    }

    @Test
    void persistsAndImmediatelyAppliesOverride() {
        when(repository.findById(IiifRuntimeSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(IiifRuntimeSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IiifSettingsDto.Response response = service.updateSettings(25, "admin-1");

        assertThat(response.overrideDownloadMinIntervalMs()).isEqualTo(25);
        assertThat(response.effectiveDownloadMinIntervalMs()).isEqualTo(25);
        assertThat(response.updatedByUserId()).isEqualTo("admin-1");
        assertThat(service.getEffectiveDownloadMinIntervalMs()).isEqualTo(25);

        ArgumentCaptor<IiifRuntimeSettings> captor = ArgumentCaptor.forClass(IiifRuntimeSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(IiifRuntimeSettings.SINGLETON_ID);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void nullOverrideResetsToDeploymentDefault() {
        service.updateSettings(null, "admin-1");

        verify(repository).deleteById(IiifRuntimeSettings.SINGLETON_ID);
        assertThat(service.getEffectiveDownloadMinIntervalMs()).isEqualTo(100);
        assertThat(service.getSettings().overrideDownloadMinIntervalMs()).isNull();
    }

    @Test
    void scheduledRefreshAppliesDatabaseChangesFromAnotherReplica() {
        IiifRuntimeSettings settings = new IiifRuntimeSettings();
        settings.setDownloadMinIntervalMs(250);
        settings.setUpdatedAt(LocalDateTime.of(2026, 7, 6, 12, 0));
        settings.setUpdatedByUserId("admin-2");
        when(repository.findById(IiifRuntimeSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        service.refreshFromDatabase();

        assertThat(service.getEffectiveDownloadMinIntervalMs()).isEqualTo(250);
        assertThat(service.getSettings().updatedByUserId()).isEqualTo("admin-2");
    }
}
