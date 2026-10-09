package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageMoveDto.*;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException;
import de.uniwue.zpd.dachs.larex.backend.exception.PageMoveConflictException;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PageMoveService {
    private final ProjectRepository projects;
    private final PageRepository pages;
    private final WorkspaceAccessService access;
    private final PageOrderService order;
    private final AnnotationLeaseService leases;
    private final PageMoveFileService files;
    private final TaskPageLinkRepository taskLinks;
    private final SubtaskRepository subtasks;
    private final WorkspaceQuotaRefreshService quotaRefresh;
    private final DatasetItemRepository datasetItems;

    public PageMoveService(ProjectRepository projects, PageRepository pages, WorkspaceAccessService access,
                           PageOrderService order, AnnotationLeaseService leases, PageMoveFileService files,
                           TaskPageLinkRepository taskLinks, SubtaskRepository subtasks,
                           WorkspaceQuotaRefreshService quotaRefresh, DatasetItemRepository datasetItems) {
        this.projects = projects;
        this.pages = pages;
        this.access = access;
        this.order = order;
        this.leases = leases;
        this.files = files;
        this.taskLinks = taskLinks;
        this.subtasks = subtasks;
        this.quotaRefresh = quotaRefresh;
        this.datasetItems = datasetItems;
    }

    @Transactional(readOnly = true)
    public Preview preview(String workspaceId, String sourceProjectId, Request request, String userId) {
        return prepare(workspaceId, sourceProjectId, request, userId, false).preview();
    }

    @Transactional(rollbackFor = IOException.class)
    public Preview move(String workspaceId, String sourceProjectId, Request request, String userId) throws IOException {
        Plan plan = prepare(workspaceId, sourceProjectId, request, userId, true);
        Preview preview = plan.preview();
        if (request.fingerprint() == null || !request.fingerprint().equals(preview.fingerprint())) {
            throw new PageMoveConflictException("The move preview has changed. Review a fresh preview before moving pages.");
        }
        if (!preview.canMove()) {
            throw new PageMoveConflictException("The move is blocked. Review the preview for details.");
        }

        List<Item> moving = preview.items().stream().filter(item -> item.outcome() != Outcome.SKIP).toList();
        List<String> movingIds = moving.stream().map(Item::pageId).toList();
        List<String> overwrittenIds = moving.stream().map(Item::overwrittenPageId).filter(Objects::nonNull).toList();
        List<String> participatingIds = new ArrayList<>(request.pageIds());
        participatingIds.addAll(overwrittenIds);
        leases.reservePagesForMove(participatingIds);
        var changes = files.enlist(sourceProjectId, request.destinationProjectId());
        files.copyAssets(movingIds, workspaceId, request.destinationProjectId(), userId, changes);
        if (!overwrittenIds.isEmpty()) {
            files.collectOverwrittenAssets(overwrittenIds, changes);
            taskLinks.deleteByPageIdIn(overwrittenIds);
            subtasks.deleteByPageIdIn(overwrittenIds);
            // Asset/index/history foreign keys cascade. Tags need explicit removal for bulk deletion.
            pages.deleteTagsByPageIds(overwrittenIds);
            pages.deleteByIdIn(overwrittenIds);
            pages.flush();
        }
        List<Integer> sortOrders = order.reserveAppendSortOrders(request.destinationProjectId(), moving.size());
        for (int index = 0; index < moving.size(); index++) {
            Item item = moving.get(index);
            Page page = plan.sourcePages().get(item.pageId());
            page.setName(item.resultingName());
            page.setProject(plan.destination());
            page.setSortOrder(sortOrders.get(index));
        }
        for (DatasetItem item : datasetItems.findBySourcePageIdInAndMode(movingIds, DatasetItem.Mode.LINK)) {
            Page page = plan.sourcePages().get(item.getSourcePageId());
            item.setSourceProjectId(plan.destination().getId());
            item.setSourceProjectName(plan.destination().getName());
            item.setSourcePageName(page.getName());
        }
        pages.flush();
        quotaRefresh.scheduleUsageRefresh(workspaceId);
        return preview;
    }

    private Plan prepare(String workspaceId, String sourceId, Request request, String userId, boolean lock) {
        if (!access.canManageProjects(workspaceId, userId)) {
            throw new SecurityException("You do not have permission to move pages in this workspace.");
        }
        if (sourceId.equals(request.destinationProjectId())) {
            throw new IllegalArgumentException("Choose a different destination project.");
        }
        if (request.pageIds() == null || request.pageIds().isEmpty()
                || request.pageIds().stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(request.pageIds()).size() != request.pageIds().size()
                || request.conflictPolicy() == null) {
            throw new IllegalArgumentException("Select distinct pages and a conflict policy.");
        }
        Map<String, Project> projectMap = new HashMap<>();
        Map<String, List<Page>> projectPages = new HashMap<>();
        // Use the same order for opposite-direction moves to avoid deadlocks.
        List<String> projectIds = List.of(sourceId, request.destinationProjectId()).stream().sorted().toList();
        for (String id : projectIds) {
            Project project = (lock ? projects.findByIdAndLibraryWorkspaceIdForUpdate(id, workspaceId)
                    : projects.findByIdAndLibraryWorkspaceId(id, workspaceId))
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found in this workspace."));
            projectMap.put(id, project);
        }
        for (String id : projectIds) {
            projectPages.put(id, lock ? pages.findByProjectIdForUpdate(id) : pages.findByProjectId(id));
        }
        var sourcePages = projectPages.get(sourceId).stream()
                .collect(Collectors.toMap(Page::getId, Function.identity()));
        if (!sourcePages.keySet().containsAll(request.pageIds())) {
            throw new IllegalArgumentException("Every selected page must belong to the source project.");
        }
        List<Page> selected = order.sortPages(request.pageIds().stream().map(sourcePages::get).toList());
        List<Page> destinationPages = projectPages.get(request.destinationProjectId());
        var destinationByName = destinationPages.stream().collect(Collectors.toMap(Page::getName, Function.identity()));
        List<String> blockers = new ArrayList<>();
        for (String id : projectIds) {
            if (projectMap.get(id).isLocked()) {
                blockers.add("Project '" + projectMap.get(id).getName() + "' is locked.");
            }
        }
        String prefix = Objects.requireNonNullElse(request.prefix(), "");
        String suffix = Objects.requireNonNullElse(request.suffix(), "");
        List<Item> items = new ArrayList<>();
        for (Page page : selected) {
            Page clash = destinationByName.get(page.getName());
            Outcome outcome = clash == null ? Outcome.MOVE : switch (request.conflictPolicy()) {
                case SKIP -> Outcome.SKIP;
                case OVERWRITE -> Outcome.OVERWRITE;
                case RENAME -> Outcome.RENAME;
            };
            String name = outcome == Outcome.RENAME ? prefix + page.getName() + suffix : page.getName();
            List<String> itemBlockers = new ArrayList<>();
            checkWritable(page, itemBlockers);
            if (outcome != Outcome.SKIP) {
                if (outcome == Outcome.OVERWRITE) checkWritable(clash, itemBlockers);
                if (name.isBlank() || name.length() > 255 || name.codePoints().anyMatch(Character::isISOControl)) {
                    itemBlockers.add("The resulting name must contain 1–255 characters and no control characters.");
                }
                if (outcome == Outcome.RENAME && prefix.isEmpty() && suffix.isEmpty()) {
                    itemBlockers.add("Enter a prefix and/or suffix for clashing pages.");
                }
                if (outcome == Outcome.RENAME && destinationByName.containsKey(name)) {
                    itemBlockers.add("The resulting name already exists in the destination project.");
                }
            }
            items.add(new Item(page.getId(), page.getName(), name, outcome,
                    outcome == Outcome.OVERWRITE ? clash.getId() : null, itemBlockers));
        }
        Map<String, Long> resultingCounts = items.stream().filter(item -> item.outcome() != Outcome.SKIP)
                .collect(Collectors.groupingBy(Item::resultingName, Collectors.counting()));
        for (Item item : items) {
            if (item.outcome() != Outcome.SKIP && resultingCounts.get(item.resultingName()) > 1) {
                item.blockers().add("Multiple selected pages would have this resulting name.");
            }
        }
        int skipped = (int) items.stream().filter(item -> item.outcome() == Outcome.SKIP).count();
        int overwritten = (int) items.stream().filter(item -> item.outcome() == Outcome.OVERWRITE).count();
        int renamed = (int) items.stream().filter(item -> item.outcome() == Outcome.RENAME).count();
        Preview preview = new Preview(items, blockers, items.size() - skipped, skipped, overwritten, renamed,
                fingerprint(workspaceId, sourceId, request, projectMap, sourcePages, destinationByName, items));
        return new Plan(projectMap.get(request.destinationProjectId()), sourcePages, preview);
    }

    private void checkWritable(Page page, List<String> blockers) {
        if (page.isEffectivelyLocked()) {
            blockers.add("Page '" + page.getName() + "' is locked.");
        }
        try {
            leases.assertNoOtherActiveEditor(page.getId(), null);
        } catch (AnnotationLeaseLockedException exception) {
            blockers.add("Page '" + page.getName() + "': " + exception.getMessage());
        }
    }

    private String fingerprint(String workspaceId, String sourceId, Request request,
                               Map<String, Project> projectMap, Map<String, Page> sourcePages,
                               Map<String, Page> destinationByName, List<Item> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hash(digest, workspaceId, sourceId, request.destinationProjectId(), request.conflictPolicy(),
                    Objects.requireNonNullElse(request.prefix(), ""), Objects.requireNonNullElse(request.suffix(), ""));
            for (String id : projectMap.keySet().stream().sorted().toList()) {
                Project project = projectMap.get(id);
                hash(digest, id, project.isLocked(), project.getLockedReason());
            }
            for (Item item : items) {
                Page source = sourcePages.get(item.pageId());
                hash(digest, source.getId(), source.getName(), source.isEffectivelyLocked(), source.getEffectiveLockedReason(),
                        item.resultingName(), item.outcome(), item.overwrittenPageId());
                // Only relevant destination names/targets affect the reviewed plan. Items are in source order.
                for (String name : new LinkedHashSet<>(List.of(source.getName(), item.resultingName()))) {
                    Page target = destinationByName.get(name);
                    hash(digest, name, target == null ? null : target.getId());
                    if (item.outcome() == Outcome.OVERWRITE && target != null) {
                        hash(digest, target.isEffectivelyLocked(), target.getEffectiveLockedReason());
                    }
                }
                for (String blocker : item.blockers()) hash(digest, blocker);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void hash(MessageDigest digest, Object... values) {
        for (Object value : values) {
            byte[] bytes = Objects.toString(value, "").getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }

    private record Plan(Project destination, Map<String, Page> sourcePages, Preview preview) {}
}
