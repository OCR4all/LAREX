package de.uniwue.zpd.dachs.larex.backend.service.project;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProjectExportCleanupScheduler {
    private final ProjectExportJobService service;

    public ProjectExportCleanupScheduler(ProjectExportJobService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${larex.project-export.cleanup-interval-ms:3600000}")
    public void cleanup() {
        service.expireArtifacts();
    }
}
