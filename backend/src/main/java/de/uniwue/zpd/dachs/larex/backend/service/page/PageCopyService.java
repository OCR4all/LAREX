package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageMoveDto.*;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.exception.PageCopyConflictException;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PageCopyService {
    private final PageMoveService transfers;
    private final PageRepository pages;
    private final PageOrderService order;
    private final AnnotationLeaseService leases;
    private final PageMoveFileService files;
    private final PageFilterIndexService index;
    private final WorkspaceQuotaGuardService quota;
    private final WorkspaceQuotaRefreshService quotaRefresh;

    public PageCopyService(PageMoveService transfers, PageRepository pages, PageOrderService order,
                           AnnotationLeaseService leases, PageMoveFileService files, PageFilterIndexService index,
                           WorkspaceQuotaGuardService quota, WorkspaceQuotaRefreshService quotaRefresh) {
        this.transfers = transfers;
        this.pages = pages;
        this.order = order;
        this.leases = leases;
        this.files = files;
        this.index = index;
        this.quota = quota;
        this.quotaRefresh = quotaRefresh;
    }

    @Transactional(rollbackFor = IOException.class)
    public Preview copy(String workspaceId, String sourceProjectId, Request request, String userId) throws IOException {
        var plan = transfers.prepareCopy(workspaceId, sourceProjectId, request, userId);
        Preview preview = plan.preview();
        if (request.fingerprint() == null || !request.fingerprint().equals(preview.fingerprint())) {
            throw new PageCopyConflictException("The copy preview has changed. Review a fresh preview before copying pages.");
        }
        if (!preview.canTransfer()) {
            throw new PageCopyConflictException("The copy is blocked. Review the preview for details.");
        }
        List<Item> selected = preview.items().stream().filter(item -> item.outcome() != Outcome.SKIP).toList();
        List<String> sourceIds = selected.stream().map(Item::pageId).toList();
        List<String> overwrittenIds = selected.stream().map(Item::overwrittenPageId).filter(Objects::nonNull).toList();
        List<String> participatingIds = new ArrayList<>(sourceIds);
        participatingIds.addAll(overwrittenIds);
        leases.reservePagesForMove(participatingIds);
        List<Integer> sortOrders = order.reserveAppendSortOrders(request.destinationProjectId(), selected.size());
        Map<String, Page> copies = new LinkedHashMap<>();
        for (int i = 0; i < selected.size(); i++) {
            Item item = selected.get(i);
            Page source = plan.sourcePages().get(item.pageId());
            Page copy = new Page(item.resultingName(), source.getDescription(), plan.destination());
            copy.setTags(new ArrayList<>(source.getTags()));
            copy.setSortOrder(sortOrders.get(i));
            copy.setExternalSourceType(source.getExternalSourceType());
            copy.setExternalSourceId(source.getExternalSourceId());
            copy.setExternalSourceUrl(source.getExternalSourceUrl());
            copy.setExternalSourceMetadataJson(source.getExternalSourceMetadataJson());
            // Do not persist until overwrite targets have been deleted (page names are unique per project).
            copies.put(source.getId(), copy);
        }
        // Quota reservation clears the persistence context. Snapshot source metadata before reserving.
        long reservation = quota.reserveBytesOrThrow(workspaceId, files.copiedAssetBytes(sourceIds), "page-copy");
        var changes = files.enlist(sourceProjectId, request.destinationProjectId());
        // Physical overwrite cleanup runs only after commit; failures restore destination records and files.
        if (!overwrittenIds.isEmpty()) transfers.deleteOverwritten(overwrittenIds, changes, userId);
        copies.replaceAll((id, page) -> pages.save(page));
        files.duplicateAssets(copies, workspaceId, request.destinationProjectId(), userId, changes);
        pages.flush();
        for (Page copy : copies.values()) index.indexPageFromXml(copy);
        pages.flush();
        quota.syncUsageAndReleaseReservation(workspaceId, reservation);
        quotaRefresh.scheduleUsageRefresh(workspaceId);
        return preview;
    }
}
