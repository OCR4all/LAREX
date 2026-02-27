package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.ResourceTransferDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.*;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class ResourceTransferService {

    private final ResourceTransferRequestRepository transferRequestRepository;
    private final CodecRepository codecRepository;
    private final VirtualKeyboardRepository virtualKeyboardRepository;
    private final LabelSetRepository labelSetRepository;
    private final LibraryRepository libraryRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceQueryService workspaceQueryService;

    public ResourceTransferService(
            ResourceTransferRequestRepository transferRequestRepository,
            CodecRepository codecRepository,
            VirtualKeyboardRepository virtualKeyboardRepository,
            LabelSetRepository labelSetRepository,
            LibraryRepository libraryRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceQueryService workspaceQueryService) {
        this.transferRequestRepository = transferRequestRepository;
        this.codecRepository = codecRepository;
        this.virtualKeyboardRepository = virtualKeyboardRepository;
        this.labelSetRepository = labelSetRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceQueryService = workspaceQueryService;
    }

    public Optional<ResourceTransferRequest> requestTransfer(
            String resourceId, ResourceTransferRequest.ResourceType resourceType,
            String targetWorkspaceId, String requestedByUserId, String message,
            ResourceTransferRequest.TransferType transferType) {

        String sourceWorkspaceId = getSourceWorkspaceId(resourceId, resourceType);
        if (sourceWorkspaceId == null || sourceWorkspaceId.equals(targetWorkspaceId)) {
            return Optional.empty();
        }

        if (!isUserAdministratorInWorkspace(sourceWorkspaceId, requestedByUserId)) {
            return Optional.empty();
        }

        if (workspaceQueryService.findWorkspaceById(targetWorkspaceId).isEmpty()) {
            return Optional.empty();
        }

        if (transferRequestRepository.existsByResourceIdAndResourceTypeAndStatus(
                resourceId, resourceType, ResourceTransferRequest.Status.PENDING)) {
            return Optional.empty();
        }

        boolean canAutoApprove = isUserAdministratorInWorkspace(targetWorkspaceId, requestedByUserId);

        ResourceTransferRequest request = new ResourceTransferRequest(
                resourceId, resourceType, sourceWorkspaceId, targetWorkspaceId,
                requestedByUserId, message, transferType
        );

        if (canAutoApprove) {
            request.setStatus(ResourceTransferRequest.Status.APPROVED);
            request.setApprovedByUserId(requestedByUserId);
            request = transferRequestRepository.save(request);
            executeTransfer(request);
            return Optional.of(request);
        } else {
            request.setStatus(ResourceTransferRequest.Status.PENDING);
            return Optional.of(transferRequestRepository.save(request));
        }
    }

    public boolean approveTransferRequest(String requestId, String approvingUserId) {
        return transferRequestRepository.findById(requestId)
                .filter(r -> r.getStatus() == ResourceTransferRequest.Status.PENDING)
                .filter(r -> isUserAdministratorInWorkspace(r.getTargetWorkspaceId(), approvingUserId))
                .map(request -> {
                    request.setStatus(ResourceTransferRequest.Status.APPROVED);
                    request.setApprovedByUserId(approvingUserId);
                    transferRequestRepository.save(request);
                    executeTransfer(request);
                    return true;
                }).orElse(false);
    }

    public boolean rejectTransferRequest(String requestId, String rejectingUserId, String rejectionReason) {
        return transferRequestRepository.findById(requestId)
                .filter(r -> r.getStatus() == ResourceTransferRequest.Status.PENDING)
                .filter(r -> isUserAdministratorInWorkspace(r.getTargetWorkspaceId(), rejectingUserId))
                .map(request -> {
                    request.setStatus(ResourceTransferRequest.Status.REJECTED);
                    request.setApprovedByUserId(rejectingUserId);
                    request.setRejectionReason(rejectionReason);
                    transferRequestRepository.save(request);
                    return true;
                }).orElse(false);
    }

    public boolean cancelTransferRequest(String requestId, String cancellingUserId) {
        return transferRequestRepository.findById(requestId)
                .filter(r -> r.getStatus() == ResourceTransferRequest.Status.PENDING)
                .filter(r -> r.getRequestedByUserId().equals(cancellingUserId))
                .map(request -> {
                    request.setStatus(ResourceTransferRequest.Status.CANCELLED);
                    transferRequestRepository.save(request);
                    return true;
                }).orElse(false);
    }

    public List<ResourceTransferRequest> getUserTransferRequests(String userId) {
        return transferRequestRepository.findByRequestedByUserId(userId);
    }

    public List<ResourceTransferRequest> getPendingIncomingRequestsForWorkspace(String workspaceId) {
        return transferRequestRepository.findPendingRequestsForTargetWorkspace(workspaceId);
    }

    public List<ResourceTransferRequest> getPendingOutgoingRequestsForWorkspace(String workspaceId) {
        return transferRequestRepository.findPendingRequestsFromSourceWorkspace(workspaceId);
    }

    public List<ResourceTransferDto.Response> toResponses(List<ResourceTransferRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<String> workspaceIds = new HashSet<>();
        Set<String> codecIds = new HashSet<>();
        Set<String> keyboardIds = new HashSet<>();
        Set<String> labelSetIds = new HashSet<>();

        for (ResourceTransferRequest request : requests) {
            workspaceIds.add(request.getSourceWorkspaceId());
            workspaceIds.add(request.getTargetWorkspaceId());
            switch (request.getResourceType()) {
                case CODEC -> codecIds.add(request.getResourceId());
                case VIRTUAL_KEYBOARD -> keyboardIds.add(request.getResourceId());
                case LABEL_SET -> labelSetIds.add(request.getResourceId());
            }
        }

        Map<String, String> workspaceNames = workspaceQueryService.findWorkspaceNamesByIds(workspaceIds);
        Map<String, String> resourceNames = new HashMap<>();
        for (Codec codec : codecRepository.findAllById(codecIds)) {
            resourceNames.put(codec.getId(), codec.getName());
        }
        for (VirtualKeyboard keyboard : virtualKeyboardRepository.findAllById(keyboardIds)) {
            resourceNames.put(keyboard.getId(), keyboard.getName());
        }
        for (LabelSet labelSet : labelSetRepository.findAllById(labelSetIds)) {
            resourceNames.put(labelSet.getId(), labelSet.getName());
        }

        List<ResourceTransferDto.Response> responses = new ArrayList<>(requests.size());
        for (ResourceTransferRequest request : requests) {
            responses.add(new ResourceTransferDto.Response(
                    request.getId(),
                    request.getResourceId(),
                    resourceNames.getOrDefault(request.getResourceId(), "Unknown"),
                    request.getResourceType(),
                    request.getSourceWorkspaceId(),
                    workspaceNames.getOrDefault(request.getSourceWorkspaceId(), "Unknown"),
                    request.getTargetWorkspaceId(),
                    workspaceNames.getOrDefault(request.getTargetWorkspaceId(), "Unknown"),
                    request.getRequestedByUserId(),
                    request.getApprovedByUserId(),
                    request.getStatus(),
                    request.getTransferType(),
                    request.getMessage(),
                    request.getRejectionReason(),
                    request.getCreated(),
                    request.getUpdated()
            ));
        }

        return responses;
    }

    public ResourceTransferDto.Response toResponse(ResourceTransferRequest request) {
        if (request == null) {
            return null;
        }
        List<ResourceTransferDto.Response> responses = toResponses(List.of(request));
        return responses.isEmpty() ? null : responses.getFirst();
    }

    private void executeTransfer(ResourceTransferRequest request) {
        switch (request.getResourceType()) {
            case CODEC -> executeCodecTransfer(request);
            case VIRTUAL_KEYBOARD -> executeVirtualKeyboardTransfer(request);
            case LABEL_SET -> executeLabelSetTransfer(request);
        }
        request.setStatus(ResourceTransferRequest.Status.COMPLETED);
        transferRequestRepository.save(request);
    }

    private void executeCodecTransfer(ResourceTransferRequest request) {
        codecRepository.findById(request.getResourceId()).ifPresent(codec -> {
            Library targetLibrary = getOrCreateTargetLibrary(request.getTargetWorkspaceId());
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                Codec newCodec = new Codec(codec.getName() + " (Copy)", targetLibrary);
                newCodec.setDescription(codec.getDescription());
                newCodec.setCharacters(new HashSet<>(codec.getCharacters()));
                newCodec.setTags(new HashSet<>(codec.getTags()));
                codecRepository.save(newCodec);
            } else {
                codec.setLibrary(targetLibrary);
                codecRepository.save(codec);
            }
        });
    }

    private void executeVirtualKeyboardTransfer(ResourceTransferRequest request) {
        virtualKeyboardRepository.findById(request.getResourceId()).ifPresent(keyboard -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                VirtualKeyboard newKeyboard = new VirtualKeyboard();
                newKeyboard.setName(keyboard.getName() + " (Copy)");
                newKeyboard.setWorkspaceId(request.getTargetWorkspaceId());
                newKeyboard.setDescription(keyboard.getDescription());
                newKeyboard.setCols(keyboard.getCols());
                newKeyboard.setRows(keyboard.getRows());
                newKeyboard.setTags(new ArrayList<>(keyboard.getTags()));
                newKeyboard = virtualKeyboardRepository.save(newKeyboard);
                // Copy items
                for (KeyboardItem item : keyboard.getItems()) {
                    KeyboardItem newItem = new KeyboardItem();
                    newItem.setCharValue(item.getCharValue());
                    newItem.setShiftChar(item.getShiftChar());
                    newItem.setX(item.getX());
                    newItem.setY(item.getY());
                    newItem.setW(item.getW());
                    newItem.setColorClass(item.getColorClass());
                    newItem.setTextClass(item.getTextClass());
                    newItem.setDescription(item.getDescription());
                    newItem.setShiftDescription(item.getShiftDescription());
                    newKeyboard.addItem(newItem);
                }
                virtualKeyboardRepository.save(newKeyboard);
            } else {
                keyboard.setWorkspaceId(request.getTargetWorkspaceId());
                virtualKeyboardRepository.save(keyboard);
            }
        });
    }

    private void executeLabelSetTransfer(ResourceTransferRequest request) {
        labelSetRepository.findById(request.getResourceId()).ifPresent(labelSet -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                LabelSet newLabelSet = new LabelSet(
                        request.getTargetWorkspaceId(),
                        labelSet.getName() + " (Copy)",
                        labelSet.getDescription(),
                        labelSet.getDefinition()
                );
                newLabelSet.setTags(new ArrayList<>(labelSet.getTags()));
                labelSetRepository.save(newLabelSet);
            } else {
                labelSet.setWorkspaceId(request.getTargetWorkspaceId());
                labelSetRepository.save(labelSet);
            }
        });
    }

    private String getSourceWorkspaceId(String resourceId, ResourceTransferRequest.ResourceType resourceType) {
        return switch (resourceType) {
            case CODEC -> codecRepository.findById(resourceId)
                    .map(c -> c.getLibrary().getWorkspaceId()).orElse(null);
            case VIRTUAL_KEYBOARD -> virtualKeyboardRepository.findById(resourceId)
                    .map(VirtualKeyboard::getWorkspaceId).orElse(null);
            case LABEL_SET -> labelSetRepository.findById(resourceId)
                    .map(LabelSet::getWorkspaceId).orElse(null);
        };
    }

    private Library getOrCreateTargetLibrary(String targetWorkspaceId) {
        return libraryRepository.findByWorkspaceId(targetWorkspaceId)
                .orElseGet(() -> {
                    String name = workspaceQueryService.findWorkspaceById(targetWorkspaceId)
                            .map(w -> w.getName()).orElse("Unknown Workspace");
                    return libraryRepository.save(new Library(targetWorkspaceId, name));
                });
    }

    private boolean isUserAdministratorInWorkspace(String workspaceId, String userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.getRole() == WorkspaceMember.Role.ADMINISTRATOR)
                .filter(m -> m.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED)
                .isPresent();
    }
}
