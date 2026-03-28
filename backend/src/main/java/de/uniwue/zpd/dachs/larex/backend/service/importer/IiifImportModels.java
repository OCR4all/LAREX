package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;

import java.util.List;

record IiifPreviewSession(
        String workspaceId,
        String projectId,
        String userId,
        IiifImportJob.SourceType sourceType,
        String sourceReference,
        IiifImportDto.ManifestSummary manifest,
        long estimatedStorageBytes,
        int unknownSizeCanvasCount,
        List<String> warnings,
        List<IiifPreviewCanvas> canvases
) {}

record IiifPreviewCanvas(
        String canvasId,
        String canvasLabel,
        int index,
        String derivedPageName,
        boolean importable,
        String imageUrl,
        Long estimatedBytes,
        List<String> warnings,
        String existingPageId,
        String existingPageName,
        boolean existingIiifImage
) {}

record IiifJobCanvasPayload(
        String canvasId,
        String canvasLabel,
        int index,
        String requestedPageName,
        String finalPageName,
        String pageDescription,
        String action,
        String imageUrl,
        Long estimatedBytes,
        String targetPageId,
        String externalSourceUrl,
        String externalSourceMetadataJson
) {}
