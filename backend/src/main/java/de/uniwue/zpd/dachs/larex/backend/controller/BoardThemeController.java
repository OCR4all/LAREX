package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.BoardThemeDto;
import de.uniwue.zpd.dachs.larex.backend.service.BoardThemeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/board-themes")
public class BoardThemeController {

    private final BoardThemeService boardThemeService;

    public BoardThemeController(BoardThemeService boardThemeService) {
        this.boardThemeService = boardThemeService;
    }

    @GetMapping
    public ResponseEntity<List<BoardThemeDto>> getAllThemes(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(boardThemeService.getAllThemes(userId, workspaceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardThemeDto> getTheme(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(boardThemeService.getTheme(userId, workspaceId, id));
    }

    @PostMapping
    public ResponseEntity<BoardThemeDto> createTheme(
            @PathVariable String workspaceId,
            @Valid @RequestBody BoardThemeDto dto,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardThemeService.createTheme(userId, workspaceId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BoardThemeDto> updateTheme(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @Valid @RequestBody BoardThemeDto dto,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(boardThemeService.updateTheme(userId, workspaceId, id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTheme(
            @PathVariable String workspaceId,
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        boardThemeService.deleteTheme(userId, workspaceId, id);
        return ResponseEntity.noContent().build();
    }
}
