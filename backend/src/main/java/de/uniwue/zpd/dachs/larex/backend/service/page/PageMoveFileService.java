package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchLexiconService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PageMoveFileService {
    private static final Logger log = LoggerFactory.getLogger(PageMoveFileService.class);
    private final PageImageRepository images;
    private final PageXmlRepository xmls;
    private final HierarchicalFileStorageService storage;
    private final PageXmlVersionService versions;
    private final AnnotationReadCache cache;
    private final SearchLexiconService lexicon;
    private final TransactionTemplate cleanupTransaction;

    public PageMoveFileService(PageImageRepository images, PageXmlRepository xmls,
                               HierarchicalFileStorageService storage, PageXmlVersionService versions,
                               AnnotationReadCache cache, SearchLexiconService lexicon,
                               PlatformTransactionManager transactionManager) {
        this.images = images;
        this.xmls = xmls;
        this.storage = storage;
        this.versions = versions;
        this.cache = cache;
        this.lexicon = lexicon;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Enlist before copying, so even a later flush/commit failure removes new physical files. */
    public Changes enlist(String sourceProjectId, String destinationProjectId) {
        Changes changes = new Changes();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                changes.xmlIds.forEach(cache::evict);
                runCleanup(() -> cleanupTransaction.executeWithoutResult(status -> {
                    storage.deleteUnreferencedStoredFiles(changes.replacedPaths);
                    versions.deleteVersionDirectories(changes.overwrittenXmlIds);
                }));
                runCleanup(() -> lexicon.rebuildProjectLexicon(sourceProjectId));
                runCleanup(() -> lexicon.rebuildProjectLexicon(destinationProjectId));
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    for (String path : changes.createdPaths) {
                        try {
                            Files.deleteIfExists(storage.resolveUploadPath(path));
                        } catch (IOException exception) {
                            log.error("Could not remove rolled-back page move file {}", path, exception);
                        }
                    }
                }
            }
        });
        return changes;
    }

    private void runCleanup(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException exception) {
            // The move is already committed. Do not report a failed move and invite a retry.
            log.error("Post-commit page move cleanup failed", exception);
        }
    }

    public void copyAssets(List<String> pageIds, String workspaceId, String destinationProjectId,
                           String userId, Changes changes) throws IOException {
        for (PageImage image : images.findByPageIdIn(pageIds)) {
            var stored = copy(image.getFilePath(), image.getFileName(), image.getMimeType(),
                    workspaceId, destinationProjectId, StoredFileType.IMG, userId, changes);
            image.setFilePath(stored.storagePath());
            image.setFileSize(stored.sizeBytes());
            if (image.getThumbnailPath() != null && !image.getThumbnailPath().isBlank()) {
                var thumbnail = copy(image.getThumbnailPath(), image.getFileName(), image.getMimeType(),
                        workspaceId, destinationProjectId, StoredFileType.THUMB, userId, changes);
                image.setThumbnailPath(thumbnail.storagePath());
            }
        }
        for (PageXml xml : xmls.findByPage_IdIn(pageIds)) {
            var stored = copy(xml.getFilePath(), xml.getFileName(), xml.getMimeType(),
                    workspaceId, destinationProjectId, StoredFileType.XML, userId, changes);
            xml.setFilePath(stored.storagePath());
            xml.setFileSize(stored.sizeBytes());
            changes.xmlIds.add(xml.getId());
        }
    }

    public void collectOverwrittenAssets(List<String> pageIds, Changes changes) {
        for (PageImage image : images.findByPageIdIn(pageIds)) {
            changes.replacedPaths.add(image.getFilePath());
            changes.replacedPaths.add(image.getThumbnailPath());
        }
        for (PageXml xml : xmls.findByPage_IdIn(pageIds)) {
            changes.replacedPaths.add(xml.getFilePath());
            changes.overwrittenXmlIds.add(xml.getId());
            changes.xmlIds.add(xml.getId());
        }
    }

    private HierarchicalFileStorageService.StoredFileDescriptor copy(
            String path, String filename, String mimeType, String workspaceId, String projectId,
            StoredFileType type, String userId, Changes changes) throws IOException {
        var stored = storage.storeFromPath(storage.resolveUploadPath(path), filename, mimeType,
                workspaceId, projectId, type, userId, false);
        changes.createdPaths.add(stored.storagePath());
        changes.replacedPaths.add(path);
        return stored;
    }

    public static final class Changes {
        private final List<String> createdPaths = new ArrayList<>();
        private final List<String> replacedPaths = new ArrayList<>();
        private final List<String> overwrittenXmlIds = new ArrayList<>();
        private final List<String> xmlIds = new ArrayList<>();
    }
}
