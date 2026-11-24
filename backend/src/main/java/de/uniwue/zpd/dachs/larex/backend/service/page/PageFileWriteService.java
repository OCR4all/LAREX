package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PageFileWriteService {

    private static final Logger log = LoggerFactory.getLogger(PageFileWriteService.class);

    private final PageWriteGuard pageWriteGuard;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final TransactionTemplate transactionTemplate;

    public PageFileWriteService(PageWriteGuard pageWriteGuard,
                                HierarchicalFileStorageService hierarchicalFileStorageService,
                                PlatformTransactionManager transactionManager) {
        this.pageWriteGuard = pageWriteGuard;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Optional<String> writeUnlessLocked(String pageId, boolean canReviewPages, PageFileWrite write) throws IOException {
        return inTransaction(changes -> {
            Optional<String> lockReason = Optional.ofNullable(
                    pageWriteGuard.lockPages(List.of(pageId), canReviewPages).get(pageId));
            if (lockReason.isEmpty()) {
                write.apply(changes);
            }
            return lockReason;
        });
    }

    public <T> T inTransaction(FileTransaction<T> transaction) throws IOException {
        FileChanges changes = new FileChanges();
        T result;
        try {
            result = transactionTemplate.execute(status -> {
                try {
                    return transaction.apply(changes);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            deleteFiles(changes.stored);
            throw e.getCause();
        } catch (RuntimeException e) {
            deleteFiles(changes.stored);
            throw e;
        }
        deleteFiles(changes.replaced);
        return result;
    }

    private void deleteFiles(List<String> storagePaths) {
        if (storagePaths.isEmpty()) {
            return;
        }
        try {
            hierarchicalFileStorageService.deleteUnreferencedStoredFiles(storagePaths);
        } catch (RuntimeException e) {
            log.warn("Failed to delete {} unused page file(s)", storagePaths.size(), e);
        }
    }

    @FunctionalInterface
    public interface PageFileWrite {
        void apply(FileChanges changes) throws IOException;
    }

    @FunctionalInterface
    public interface FileTransaction<T> {
        T apply(FileChanges changes) throws IOException;
    }

    public static final class FileChanges {
        private final List<String> stored = new ArrayList<>();
        private final List<String> replaced = new ArrayList<>();

        public void stored(String storagePath) {
            if (storagePath != null) {
                stored.add(storagePath);
            }
        }

        public void replaced(String storagePath) {
            if (storagePath != null) {
                replaced.add(storagePath);
            }
        }
    }
}
