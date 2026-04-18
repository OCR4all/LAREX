package de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AnnotationLeaseService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationLeaseService.class);

    private final PageService pageService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final Map<String, LeaseRecord> leases = new ConcurrentHashMap<>();

    @Value("${larex.collaboration.lease-ttl-ms:45000}")
    private long leaseTtlMs;

    public AnnotationLeaseService(PageService pageService,
                                  AuthorizationPolicyService authorizationPolicyService,
                                  UserService userService,
                                  NotificationService notificationService) {
        this.pageService = pageService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public RoomAccessContext resolveRoomAccess(String projectId, String pageId, String xmlId, String userId) {
        Optional<Page> pageOpt = pageService.getPageById(pageId, userId);
        PageXml xml = pageService.getXmlById(xmlId, userId);
        if (pageOpt.isEmpty() || xml == null) {
            throw new IllegalArgumentException("Annotation page not found");
        }

        Page page = pageOpt.get();
        if (!projectId.equals(page.getProject().getId())) {
            throw new IllegalArgumentException("Annotation project mismatch");
        }
        if (xml.getPage() == null || !pageId.equals(xml.getPage().getId())) {
            throw new IllegalArgumentException("Annotation XML mismatch");
        }

        String workspaceId = page.getProject().getLibrary().getWorkspaceId();
        boolean canEdit = authorizationPolicyService.canAccessWorkspace(workspaceId, userId)
                && !page.getProject().isLocked();
        boolean canForceTakeover = authorizationPolicyService.canManageProjects(workspaceId, userId);

        AnnotationCollaborationDto.UserSummary user = resolveUserSummary(userId);

        return new RoomAccessContext(
                workspaceId,
                projectId,
                pageId,
                xmlId,
                buildRoomKey(projectId, pageId, xmlId),
                page.getProject().getName(),
                page.getName(),
                canEdit,
                canForceTakeover,
                user,
                xml
        );
    }

    public AnnotationCollaborationDto.LeaseState getLeaseState(RoomAccessContext context) {
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationCollaborationDto.LeaseState leaseState;
        synchronized (leases) {
            LeaseRecord record = getActiveRecord(context.roomKey(), notifications);
            refreshRecordMetadata(record, context);
            leaseState = toLeaseState(record, context.user().id());
        }
        dispatchNotifications(notifications);
        return leaseState;
    }

    public AnnotationCollaborationDto.LeaseState joinLease(RoomAccessContext context, String instanceId) {
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationCollaborationDto.LeaseState leaseState;
        synchronized (leases) {
            LeaseRecord record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);
            expireIfNeeded(context.roomKey(), record, notifications);
            record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);
            touchParticipant(record, context.user(), instanceId);

            if (isOwner(record, context.user().id())) {
                touchInstance(record, instanceId);
            } else if (record.owner == null && context.canEdit() && isPreferredParticipant(record, context.user().id(), instanceId)) {
                assignOwner(record, context.user(), instanceId);
            }

            leaseState = toLeaseState(record, context.user().id());
        }
        dispatchNotifications(notifications);
        return leaseState;
    }

    public AnnotationCollaborationDto.LeaseState heartbeat(RoomAccessContext context, String instanceId) {
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationCollaborationDto.LeaseState leaseState;
        synchronized (leases) {
            LeaseRecord record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);
            expireIfNeeded(context.roomKey(), record, notifications);
            record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);
            touchParticipant(record, context.user(), instanceId);

            if (isOwner(record, context.user().id())) {
                touchInstance(record, instanceId);
            } else if (record.owner == null && context.canEdit() && isPreferredParticipant(record, context.user().id(), instanceId)) {
                assignOwner(record, context.user(), instanceId);
            }

            leaseState = toLeaseState(record, context.user().id());
        }
        dispatchNotifications(notifications);
        return leaseState;
    }

    public AnnotationCollaborationDto.LeaseState requestTakeover(RoomAccessContext context, boolean force) {
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationCollaborationDto.LeaseState leaseState;
        synchronized (leases) {
            LeaseRecord record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);
            expireIfNeeded(context.roomKey(), record, notifications);
            record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);

            if (!context.canEdit()) {
                leaseState = toLeaseState(record, context.user().id());
            } else if (record.owner == null) {
                assignOwner(record, context.user(), null);
                leaseState = toLeaseState(record, context.user().id());
            } else if (isOwner(record, context.user().id())) {
                leaseState = toLeaseState(record, context.user().id());
            } else if (force) {
                if (!context.canForceTakeover()) {
                    leaseState = toLeaseState(record, context.user().id());
                } else {
                    AnnotationCollaborationDto.UserSummary previousEditor = record.owner == null ? null : record.owner.user;
                    assignOwner(record, context.user(), null);
                    if (previousEditor != null && !previousEditor.id().equals(context.user().id())) {
                        queueNotification(notifications, "takeover-forced", () -> notificationService.createCollaborationTakeoverForcedNotification(
                                previousEditor.id(),
                                context.projectId(),
                                context.projectLabel(),
                                context.pageId(),
                                context.pageLabel(),
                                displayName(context.user())
                        ));
                    }
                    leaseState = toLeaseState(record, context.user().id());
                }
            } else {
                record.pendingTakeover = new PendingTakeoverRecord(context.user(), Instant.now().toString(), false);
                record.epoch++;
                if (record.owner != null
                        && record.owner.user != null
                        && !record.owner.user.id().equals(context.user().id())) {
                    String ownerUserId = record.owner.user.id();
                    queueNotification(notifications, "takeover-requested", () -> notificationService.createCollaborationTakeoverRequestedNotification(
                            ownerUserId,
                            context.projectId(),
                            context.projectLabel(),
                            context.pageId(),
                            context.pageLabel(),
                            displayName(context.user())
                    ));
                }
                leaseState = toLeaseState(record, context.user().id());
            }
        }
        dispatchNotifications(notifications);
        return leaseState;
    }

    public AnnotationCollaborationDto.LeaseState respondToTakeover(RoomAccessContext context,
                                                                   String decision,
                                                                   String handoffMode) {
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationCollaborationDto.LeaseState leaseState;
        synchronized (leases) {
            LeaseRecord record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);
            expireIfNeeded(context.roomKey(), record, notifications);
            record = getOrCreateRecord(context.roomKey());
            refreshRecordMetadata(record, context);

            if (!isOwner(record, context.user().id()) || record.pendingTakeover == null) {
                leaseState = toLeaseState(record, context.user().id());
            } else {
                PendingTakeoverRecord pending = record.pendingTakeover;
                AnnotationCollaborationDto.UserSummary previousEditor = record.owner == null ? null : record.owner.user;
                record.pendingTakeover = null;
                record.epoch++;

                boolean accepted = "accept".equalsIgnoreCase(decision);

                if (!accepted) {
                    if (pending.requester != null && !pending.requester.id().equals(context.user().id())) {
                        queueNotification(notifications, "takeover-declined", () -> notificationService.createCollaborationTakeoverDeclinedNotification(
                                pending.requester.id(),
                                context.projectId(),
                                context.projectLabel(),
                                context.pageId(),
                                context.pageLabel(),
                                displayName(context.user())
                        ));
                    }
                    leaseState = toLeaseState(record, context.user().id());
                } else if (pending.requester == null) {
                    leaseState = toLeaseState(record, context.user().id());
                } else {
                    assignOwner(record, pending.requester, null);
                    if (!pending.requester.id().equals(context.user().id())) {
                        queueNotification(notifications, "takeover-granted", () -> notificationService.createCollaborationTakeoverGrantedNotification(
                                pending.requester.id(),
                                context.projectId(),
                                context.projectLabel(),
                                context.pageId(),
                                context.pageLabel(),
                                displayName(context.user())
                        ));
                    }
                    if (previousEditor != null
                            && !previousEditor.id().equals(pending.requester.id())
                            && !previousEditor.id().equals(context.user().id())) {
                        queueNotification(notifications, "takeover-transferred", () -> notificationService.createCollaborationTakeoverForcedNotification(
                                previousEditor.id(),
                                context.projectId(),
                                context.projectLabel(),
                                context.pageId(),
                                context.pageLabel(),
                                displayName(pending.requester)
                        ));
                    }
                    leaseState = toLeaseState(record, context.user().id());
                }
            }
        }
        dispatchNotifications(notifications);
        return leaseState;
    }

    public AnnotationCollaborationDto.LeaseState releaseLease(RoomAccessContext context, String instanceId) {
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationCollaborationDto.LeaseState leaseState;
        synchronized (leases) {
            LeaseRecord record = getActiveRecord(context.roomKey(), notifications);
            refreshRecordMetadata(record, context);
            if (record == null) {
                leaseState = emptyLeaseState(context.user().id());
            } else {
                if (instanceId != null && !instanceId.isBlank()) {
                    record.participantInstances.remove(instanceId);
                }
                if (isOwner(record, context.user().id())) {
                    if (instanceId != null && !instanceId.isBlank()) {
                        record.activeInstances.remove(instanceId);
                    }
                    if (record.activeInstances.isEmpty()) {
                        transferOwnershipAfterOwnerDeparture(record, context.user().id());
                    } else {
                        updateExpiry(record);
                    }
                }
                leaseState = toLeaseState(record, context.user().id());
            }
        }
        dispatchNotifications(notifications);
        return leaseState;
    }

    public void assertWriteAccess(String projectId, String pageId, String xmlId, String userId) {
        RoomAccessContext context = resolveRoomAccess(projectId, pageId, xmlId, userId);
        assertWriteAccess(context, userId);
    }

    public void assertWriteAccess(RoomAccessContext context, String userId) {
        if (!context.canEdit()) {
            throw new AnnotationLeaseLockedException(
                    "This page is currently read-only.",
                    null,
                    "editing-disabled"
            );
        }
        List<NotificationIntent> notifications = new ArrayList<>();
        AnnotationLeaseLockedException lockedException = null;
        boolean writable = false;
        synchronized (leases) {
            LeaseRecord record = getActiveRecord(context.roomKey(), notifications);
            if (record == null || record.owner == null || isOwner(record, userId)) {
                writable = true;
            } else {
                lockedException = new AnnotationLeaseLockedException(
                        (record.owner.user.displayName() != null ? record.owner.user.displayName() : "Another editor")
                                + " currently holds the edit lock for this page.",
                        record.owner.user,
                        "lease-held-by-other-user"
                );
            }
        }
        dispatchNotifications(notifications);
        if (writable) {
            return;
        }
        throw lockedException;
    }

    public AnnotationCollaborationDto.UserSummary resolveUserSummary(String userId) {
        return userService.getUserProfile(userId)
                .map(profile -> new AnnotationCollaborationDto.UserSummary(
                        profile.id(),
                        profile.username(),
                        buildDisplayName(profile.id(), profile.username(), profile.firstName(), profile.lastName()),
                        profile.avatar()
                ))
                .orElseGet(() -> new AnnotationCollaborationDto.UserSummary(userId, userId, userId, null));
    }

    @Scheduled(fixedDelayString = "${larex.collaboration.lease-cleanup-ms:10000}")
    public void pruneExpiredLeases() {
        List<NotificationIntent> notifications = new ArrayList<>();
        synchronized (leases) {
            for (String roomKey : leases.keySet()) {
                LeaseRecord record = leases.get(roomKey);
                if (record == null) {
                    continue;
                }
                expireIfNeeded(roomKey, record, notifications);
                if (record.owner == null && record.pendingTakeover == null) {
                    leases.remove(roomKey, record);
                }
            }
        }
        dispatchNotifications(notifications);
    }

    private LeaseRecord getOrCreateRecord(String roomKey) {
        return leases.computeIfAbsent(roomKey, ignored -> new LeaseRecord());
    }

    private LeaseRecord getActiveRecord(String roomKey, List<NotificationIntent> notifications) {
        LeaseRecord record = leases.get(roomKey);
        if (record == null) {
            return null;
        }
        expireIfNeeded(roomKey, record, notifications);
        return leases.get(roomKey);
    }

    private void expireIfNeeded(String roomKey, LeaseRecord record, List<NotificationIntent> notifications) {
        if (record.owner == null) {
            return;
        }
        pruneExpiredInstances(record);
        pruneExpiredParticipants(record);
        if (!record.activeInstances.isEmpty()) {
            updateExpiry(record);
            return;
        }

        PendingTakeoverRecord pending = record.pendingTakeover;
        AnnotationCollaborationDto.UserSummary expiredEditor = record.owner.user;
        if (pending != null && pending.requester != null) {
            assignOwner(record, pending.requester, null);
            if (pending.requester.id() != null && !pending.requester.id().equals(expiredEditor.id())) {
                queueNotification(notifications, "lease-expired-granted", () -> notificationService.createCollaborationTakeoverGrantedNotification(
                        pending.requester.id(),
                        record.projectId,
                        record.projectLabel,
                        record.pageId,
                        record.pageLabel,
                        displayName(expiredEditor)
                ));
            }
            if (expiredEditor != null && expiredEditor.id() != null) {
                queueNotification(notifications, "lease-expired-previous-editor", () -> notificationService.createCollaborationLeaseExpiredNotification(
                        expiredEditor.id(),
                        record.projectId,
                        record.projectLabel,
                        record.pageId,
                        record.pageLabel
                ));
            }
            return;
        }

        if (transferOwnershipAfterOwnerDeparture(record, expiredEditor == null ? null : expiredEditor.id())) {
            if (expiredEditor != null && expiredEditor.id() != null) {
                queueNotification(notifications, "lease-expired-transfer", () -> notificationService.createCollaborationLeaseExpiredNotification(
                        expiredEditor.id(),
                        record.projectId,
                        record.projectLabel,
                        record.pageId,
                        record.pageLabel
                ));
            }
            return;
        }
        if (record.owner == null && record.pendingTakeover == null) {
          leases.remove(roomKey, record);
        }
    }

    private void refreshRecordMetadata(LeaseRecord record, RoomAccessContext context) {
        if (record == null || context == null) {
            return;
        }
        record.projectId = context.projectId();
        record.pageId = context.pageId();
        record.xmlId = context.xmlId();
        record.projectLabel = context.projectLabel();
        record.pageLabel = context.pageLabel();
    }

    private void assignOwner(LeaseRecord record, AnnotationCollaborationDto.UserSummary user, String instanceId) {
        record.owner = new LeaseOwnerRecord(user, Instant.now().toString());
        record.pendingTakeover = null;
        record.activeInstances.clear();
        touchInstance(record, instanceId);
        record.epoch++;
    }

    private boolean transferOwnershipAfterOwnerDeparture(LeaseRecord record, String departingUserId) {
        ParticipantInstanceRecord successor = findPreferredParticipant(record, departingUserId);
        if (successor != null) {
            assignOwner(record, successor.user, successor.instanceId);
            return true;
        }

        record.owner = null;
        record.pendingTakeover = null;
        record.epoch++;
        return false;
    }

    private void touchInstance(LeaseRecord record, String instanceId) {
        if (instanceId != null && !instanceId.isBlank()) {
            record.activeInstances.put(instanceId, Instant.now());
        }
        updateExpiry(record);
    }

    private void touchParticipant(LeaseRecord record, AnnotationCollaborationDto.UserSummary user, String instanceId) {
        if (user == null || instanceId == null || instanceId.isBlank()) {
            return;
        }

        ParticipantInstanceRecord existing = record.participantInstances.get(instanceId);
        String joinedAt = existing == null ? Instant.now().toString() : existing.joinedAt;
        record.participantInstances.put(instanceId, new ParticipantInstanceRecord(
                instanceId,
                user,
                joinedAt,
                Instant.now()
        ));
    }

    private void updateExpiry(LeaseRecord record) {
        Instant latestHeartbeat = null;
        for (Instant heartbeatAt : record.activeInstances.values()) {
            if (latestHeartbeat == null || heartbeatAt.isAfter(latestHeartbeat)) {
                latestHeartbeat = heartbeatAt;
            }
        }
        record.expiresAt = latestHeartbeat == null
                ? null
                : latestHeartbeat.plus(Duration.ofMillis(leaseTtlMs));
    }

    private void pruneExpiredInstances(LeaseRecord record) {
        Instant cutoff = Instant.now().minus(Duration.ofMillis(leaseTtlMs));
        Iterator<Map.Entry<String, Instant>> iterator = record.activeInstances.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue() == null || entry.getValue().isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    private void pruneExpiredParticipants(LeaseRecord record) {
        Instant cutoff = Instant.now().minus(Duration.ofMillis(leaseTtlMs));
        Iterator<Map.Entry<String, ParticipantInstanceRecord>> iterator = record.participantInstances.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ParticipantInstanceRecord> entry = iterator.next();
            ParticipantInstanceRecord participant = entry.getValue();
            if (participant == null || participant.lastHeartbeatAt == null || participant.lastHeartbeatAt.isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    private ParticipantInstanceRecord findPreferredParticipant(LeaseRecord record, String excludedUserId) {
        ParticipantInstanceRecord preferred = null;
        for (ParticipantInstanceRecord participant : record.participantInstances.values()) {
            if (participant == null || participant.user == null || participant.user.id() == null) {
                continue;
            }
            if (excludedUserId != null && excludedUserId.equals(participant.user.id())) {
                continue;
            }
            if (preferred == null
                    || participant.joinedAt.compareTo(preferred.joinedAt) < 0
                    || (participant.joinedAt.equals(preferred.joinedAt) && participant.instanceId.compareTo(preferred.instanceId) < 0)) {
                preferred = participant;
            }
        }
        return preferred;
    }

    private boolean isPreferredParticipant(LeaseRecord record, String userId, String instanceId) {
        ParticipantInstanceRecord preferred = findPreferredParticipant(record, null);
        return preferred != null
                && preferred.user != null
                && preferred.user.id() != null
                && preferred.user.id().equals(userId)
                && preferred.instanceId.equals(instanceId);
    }

    private boolean isOwner(LeaseRecord record, String userId) {
        return record.owner != null
                && record.owner.user != null
                && record.owner.user.id() != null
                && record.owner.user.id().equals(userId);
    }

    private void queueNotification(List<NotificationIntent> notifications, String action, Runnable dispatch) {
        if (notifications == null) {
            return;
        }
        notifications.add(new NotificationIntent(action, dispatch));
    }

    private void dispatchNotifications(List<NotificationIntent> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        for (NotificationIntent notification : notifications) {
            try {
                notification.dispatch().run();
            } catch (RuntimeException exception) {
                logger.warn("Collaboration notification dispatch failed for action {}", notification.action(), exception);
            }
        }
    }

    private AnnotationCollaborationDto.LeaseState emptyLeaseState(String currentUserId) {
        return new AnnotationCollaborationDto.LeaseState(null, null, false, 0, null);
    }

    private AnnotationCollaborationDto.LeaseState toLeaseState(LeaseRecord record, String currentUserId) {
        if (record == null) {
            return emptyLeaseState(currentUserId);
        }

        AnnotationCollaborationDto.LeaseOwner owner = record.owner == null
                ? null
                : new AnnotationCollaborationDto.LeaseOwner(record.owner.user, record.owner.acquiredAt);

        AnnotationCollaborationDto.TakeoverRequest pendingTakeover = record.pendingTakeover == null
                ? null
                : new AnnotationCollaborationDto.TakeoverRequest(
                        record.pendingTakeover.requester,
                        record.pendingTakeover.requestedAt,
                        record.pendingTakeover.force
                );

        return new AnnotationCollaborationDto.LeaseState(
                owner,
                pendingTakeover,
                owner != null && owner.user() != null && owner.user().id().equals(currentUserId),
                record.epoch,
                record.expiresAt == null ? null : record.expiresAt.toString()
        );
    }

    private String buildDisplayName(String userId, String username, String firstName, String lastName) {
        String safeFirstName = firstName == null ? "" : firstName.trim();
        String safeLastName = lastName == null ? "" : lastName.trim();
        String combined = (safeFirstName + " " + safeLastName).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        if (username != null && !username.trim().isEmpty()) {
            return username.trim();
        }
        return userId;
    }

    private String buildRoomKey(String projectId, String pageId, String xmlId) {
        return projectId + ":" + pageId + ":" + xmlId;
    }

    private String displayName(AnnotationCollaborationDto.UserSummary user) {
        if (user == null) {
            return "Another editor";
        }
        if (user.displayName() != null && !user.displayName().isBlank()) {
            return user.displayName();
        }
        if (user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return user.id();
    }

    public record RoomAccessContext(
            String workspaceId,
            String projectId,
            String pageId,
            String xmlId,
            String roomKey,
            String projectLabel,
            String pageLabel,
            boolean canEdit,
            boolean canForceTakeover,
            AnnotationCollaborationDto.UserSummary user,
            PageXml pageXml
    ) {}

    private static final class LeaseRecord {
        private LeaseOwnerRecord owner;
        private PendingTakeoverRecord pendingTakeover;
        private Instant expiresAt;
        private long epoch = 0;
        private final Map<String, Instant> activeInstances = new ConcurrentHashMap<>();
        private final Map<String, ParticipantInstanceRecord> participantInstances = new ConcurrentHashMap<>();
        private String projectId;
        private String pageId;
        private String xmlId;
        private String projectLabel;
        private String pageLabel;
    }

    private record LeaseOwnerRecord(
            AnnotationCollaborationDto.UserSummary user,
            String acquiredAt
    ) {}

    private record PendingTakeoverRecord(
            AnnotationCollaborationDto.UserSummary requester,
            String requestedAt,
            boolean force
    ) {}

    private static final class ParticipantInstanceRecord {
        private final String instanceId;
        private final AnnotationCollaborationDto.UserSummary user;
        private final String joinedAt;
        private final Instant lastHeartbeatAt;

        private ParticipantInstanceRecord(String instanceId,
                                          AnnotationCollaborationDto.UserSummary user,
                                          String joinedAt,
                                          Instant lastHeartbeatAt) {
            this.instanceId = instanceId;
            this.user = user;
            this.joinedAt = joinedAt;
            this.lastHeartbeatAt = lastHeartbeatAt;
        }
    }

    private record NotificationIntent(String action, Runnable dispatch) {}
}
