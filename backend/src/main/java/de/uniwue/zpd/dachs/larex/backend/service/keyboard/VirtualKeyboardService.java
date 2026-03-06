package de.uniwue.zpd.dachs.larex.backend.service.keyboard;

import de.uniwue.zpd.dachs.larex.backend.dto.KeyboardItemDto;
import de.uniwue.zpd.dachs.larex.backend.dto.VirtualKeyboardDto;
import de.uniwue.zpd.dachs.larex.backend.entity.KeyboardItem;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class VirtualKeyboardService {

    private final VirtualKeyboardRepository virtualKeyboardRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuthorizationPolicyService authorizationPolicyService;

    public VirtualKeyboardService(VirtualKeyboardRepository virtualKeyboardRepository,
                                  WorkspaceAccessService workspaceAccessService,
                                  AuthorizationPolicyService authorizationPolicyService) {
        this.virtualKeyboardRepository = virtualKeyboardRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    @Transactional(readOnly = true)
    public List<VirtualKeyboardDto> getAllKeyboards(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        return virtualKeyboardRepository.findByWorkspaceId(workspaceId).stream()
                .map(keyboard -> toDto(keyboard, userId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VirtualKeyboardDto getKeyboard(String userId, String workspaceId, String id) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Objects.requireNonNull(id, "id");
        VirtualKeyboard keyboard = virtualKeyboardRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Virtual keyboard not found with id: " + id));
        return toDto(keyboard, userId);
    }

    @Transactional
    public VirtualKeyboardDto createKeyboard(String userId, String workspaceId, VirtualKeyboardDto dto) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        if (virtualKeyboardRepository.existsByNameAndWorkspaceId(dto.getName(), workspaceId)) {
            throw new IllegalArgumentException("Virtual keyboard with name '" + dto.getName() + "' already exists in this workspace");
        }

        VirtualKeyboard keyboard = new VirtualKeyboard();
        keyboard.setUserId(userId);
        keyboard.setWorkspaceId(workspaceId);
        updateKeyboardFromDto(keyboard, dto);
        VirtualKeyboard saved = virtualKeyboardRepository.save(keyboard);
        return toDto(saved, userId);
    }

    @Transactional
    public VirtualKeyboardDto updateKeyboard(String userId, String workspaceId, String id, VirtualKeyboardDto dto) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        Objects.requireNonNull(id, "id");
        VirtualKeyboard keyboard = virtualKeyboardRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Virtual keyboard not found with id: " + id));

        if (!Objects.equals(keyboard.getName(), dto.getName()) &&
                virtualKeyboardRepository.existsByNameAndWorkspaceId(dto.getName(), workspaceId)) {
            throw new IllegalArgumentException("Virtual keyboard with name '" + dto.getName() + "' already exists in this workspace");
        }
        
        keyboard.getItems().clear();
        
        updateKeyboardFromDto(keyboard, dto);
        VirtualKeyboard saved = virtualKeyboardRepository.save(keyboard);
        return toDto(saved, userId);
    }

    @Transactional
    public void deleteKeyboard(String userId, String workspaceId, String id) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        Objects.requireNonNull(id, "id");
        VirtualKeyboard keyboard = virtualKeyboardRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Virtual keyboard not found with id: " + id));
        virtualKeyboardRepository.delete(keyboard);
    }

    private VirtualKeyboardDto toDto(VirtualKeyboard keyboard, String userId) {
        VirtualKeyboardDto dto = new VirtualKeyboardDto(keyboard);
        dto.setCapabilities(authorizationPolicyService.resolveWorkspaceResourceCapabilities(keyboard.getWorkspaceId(), userId));
        return dto;
    }

    private void updateKeyboardFromDto(VirtualKeyboard keyboard, VirtualKeyboardDto dto) {
        keyboard.setName(dto.getName());
        keyboard.setDescription(dto.getDescription());
        final List<String> sanitizedTags = dto.getTags() == null
                ? List.of()
                : dto.getTags().stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(ArrayList::new));

        if (keyboard.getTags() == null) {
            keyboard.setTags(new ArrayList<>());
        }
        keyboard.getTags().clear();
        keyboard.getTags().addAll(sanitizedTags);
        keyboard.setCols(dto.getCols());
        keyboard.setRows(dto.getRows());
        keyboard.setThemeId(dto.getThemeId());

        if (dto.getItems() != null) {
            for (KeyboardItemDto itemDto : dto.getItems()) {
                KeyboardItem item = new KeyboardItem();
                item.setX(itemDto.getX());
                item.setY(itemDto.getY());
                item.setW(itemDto.getW());
                item.setCharValue(itemDto.getChar());
                item.setShiftChar(itemDto.getShiftChar());
                item.setColorClass(itemDto.getColorClass());
                item.setTextClass(itemDto.getTextClass());
                item.setDescription(itemDto.getDescription());
                item.setShiftDescription(itemDto.getShiftDescription());
                
                keyboard.addItem(item);
            }
        }
    }
}
