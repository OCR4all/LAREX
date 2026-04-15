package de.uniwue.zpd.dachs.larex.backend.controller.keyboard;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.VirtualKeyboardDto;
import de.uniwue.zpd.dachs.larex.backend.service.keyboard.VirtualKeyboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/virtual-keyboards")
public class VirtualKeyboardController {

    private final VirtualKeyboardService virtualKeyboardService;

    public VirtualKeyboardController(VirtualKeyboardService virtualKeyboardService) {
        this.virtualKeyboardService = virtualKeyboardService;
    }

    @GetMapping
    public ResponseEntity<List<VirtualKeyboardDto>> getAllKeyboards(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(virtualKeyboardService.getAllKeyboards(userId, workspaceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VirtualKeyboardDto> getKeyboard(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(virtualKeyboardService.getKeyboard(userId, workspaceId, id));
    }

    @PostMapping
    public ResponseEntity<VirtualKeyboardDto> createKeyboard(
            @PathVariable String workspaceId,
            @Valid @RequestBody VirtualKeyboardDto dto,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(virtualKeyboardService.createKeyboard(userId, workspaceId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VirtualKeyboardDto> updateKeyboard(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @Valid @RequestBody VirtualKeyboardDto dto,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(virtualKeyboardService.updateKeyboard(userId, workspaceId, id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyboard(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        virtualKeyboardService.deleteKeyboard(userId, workspaceId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteDto.BulkDeleteResponse> bulkDeleteKeyboards(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkDeleteDto.BulkDeleteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(virtualKeyboardService.bulkDeleteKeyboards(userId, workspaceId, request.ids()));
    }
}
