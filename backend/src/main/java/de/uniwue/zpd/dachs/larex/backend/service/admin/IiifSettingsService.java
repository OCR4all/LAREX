package de.uniwue.zpd.dachs.larex.backend.service.admin;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifSettingsDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifRuntimeSettings;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.IiifRuntimeSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IiifSettingsService {

    private static final Logger log = LoggerFactory.getLogger(IiifSettingsService.class);

    private final IiifRuntimeSettingsRepository repository;
    private final IiifProperties properties;
    private final AtomicReference<RuntimeState> runtimeState;

    public IiifSettingsService(IiifRuntimeSettingsRepository repository, IiifProperties properties) {
        this.repository = repository;
        this.properties = properties;
        this.runtimeState = new AtomicReference<>(RuntimeState.deploymentDefault(properties.getDownloadMinIntervalMs()));
    }

    @PostConstruct
    public void loadInitialSettings() {
        refreshFromDatabase();
    }

    @Scheduled(fixedDelay = 5_000, scheduler = "coordinationTaskScheduler")
    @Transactional(readOnly = true)
    public void refreshFromDatabase() {
        try {
            RuntimeState next = repository.findById(IiifRuntimeSettings.SINGLETON_ID)
                    .map(this::toRuntimeState)
                    .orElseGet(() -> RuntimeState.deploymentDefault(properties.getDownloadMinIntervalMs()));
            runtimeState.set(next);
        } catch (RuntimeException e) {
            log.warn("Could not refresh IIIF runtime settings; retaining the last effective value", e);
        }
    }

    public int getEffectiveDownloadMinIntervalMs() {
        return runtimeState.get().effectiveDownloadMinIntervalMs();
    }

    public IiifSettingsDto.Response getSettings() {
        return runtimeState.get().toResponse(properties.getDownloadMinIntervalMs());
    }

    @Transactional
    public IiifSettingsDto.Response updateSettings(Integer downloadMinIntervalMs, String updatedByUserId) {
        if (downloadMinIntervalMs == null) {
            repository.deleteById(IiifRuntimeSettings.SINGLETON_ID);
            RuntimeState next = RuntimeState.deploymentDefault(properties.getDownloadMinIntervalMs());
            runtimeState.set(next);
            log.info("IIIF download pacing reset to deployment default by user {}", updatedByUserId);
            return next.toResponse(properties.getDownloadMinIntervalMs());
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        IiifRuntimeSettings settings = repository.findById(IiifRuntimeSettings.SINGLETON_ID)
                .orElseGet(IiifRuntimeSettings::new);
        settings.setDownloadMinIntervalMs(downloadMinIntervalMs);
        settings.setUpdatedAt(updatedAt);
        settings.setUpdatedByUserId(updatedByUserId);
        IiifRuntimeSettings saved = repository.save(settings);

        RuntimeState next = toRuntimeState(saved);
        runtimeState.set(next);
        log.info("IIIF download pacing updated to {} ms by user {}", downloadMinIntervalMs, updatedByUserId);
        return next.toResponse(properties.getDownloadMinIntervalMs());
    }

    private RuntimeState toRuntimeState(IiifRuntimeSettings settings) {
        Integer override = settings.getDownloadMinIntervalMs();
        int effective = override == null ? properties.getDownloadMinIntervalMs() : override;
        return new RuntimeState(override, effective, settings.getUpdatedAt(), settings.getUpdatedByUserId());
    }

    private record RuntimeState(
            Integer overrideDownloadMinIntervalMs,
            int effectiveDownloadMinIntervalMs,
            LocalDateTime updatedAt,
            String updatedByUserId
    ) {
        private static RuntimeState deploymentDefault(int deploymentDefault) {
            return new RuntimeState(null, deploymentDefault, null, null);
        }

        private IiifSettingsDto.Response toResponse(int deploymentDefault) {
            return new IiifSettingsDto.Response(
                    deploymentDefault,
                    overrideDownloadMinIntervalMs,
                    effectiveDownloadMinIntervalMs,
                    updatedAt,
                    updatedByUserId
            );
        }
    }
}
