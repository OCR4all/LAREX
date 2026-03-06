package de.uniwue.zpd.dachs.larex.backend.service.board;

import de.uniwue.zpd.dachs.larex.backend.dto.BoardThemeDto;
import de.uniwue.zpd.dachs.larex.backend.entity.BoardTheme;
import de.uniwue.zpd.dachs.larex.backend.repository.board.BoardThemeRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BoardThemeService {

    private final BoardThemeRepository boardThemeRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public BoardThemeService(BoardThemeRepository boardThemeRepository,
                             WorkspaceAccessService workspaceAccessService) {
        this.boardThemeRepository = boardThemeRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional(readOnly = true)
    public List<BoardThemeDto> getAllThemes(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        return boardThemeRepository.findByWorkspaceId(workspaceId).stream()
                .map(BoardThemeDto::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BoardThemeDto getTheme(String userId, String workspaceId, String id) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        BoardTheme theme = boardThemeRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Board theme not found with id: " + id));
        return new BoardThemeDto(theme);
    }

    @Transactional
    public BoardThemeDto createTheme(String userId, String workspaceId, BoardThemeDto dto) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        if (boardThemeRepository.existsByNameAndWorkspaceId(dto.getName(), workspaceId)) {
            throw new IllegalArgumentException("Board theme with name '" + dto.getName() + "' already exists in this workspace");
        }

        BoardTheme theme = new BoardTheme();
        theme.setWorkspaceId(workspaceId);
        updateThemeFromDto(theme, dto);
        BoardTheme saved = boardThemeRepository.save(theme);
        return new BoardThemeDto(saved);
    }

    @Transactional
    public BoardThemeDto updateTheme(String userId, String workspaceId, String id, BoardThemeDto dto) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        BoardTheme theme = boardThemeRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Board theme not found with id: " + id));

        if (!theme.getName().equals(dto.getName()) &&
                boardThemeRepository.existsByNameAndWorkspaceId(dto.getName(), workspaceId)) {
            throw new IllegalArgumentException("Board theme with name '" + dto.getName() + "' already exists in this workspace");
        }
        
        updateThemeFromDto(theme, dto);
        BoardTheme saved = boardThemeRepository.save(theme);
        return new BoardThemeDto(saved);
    }

    @Transactional
    public void deleteTheme(String userId, String workspaceId, String id) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        BoardTheme theme = boardThemeRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Board theme not found with id: " + id));
        boardThemeRepository.delete(theme);
    }

    private void updateThemeFromDto(BoardTheme theme, BoardThemeDto dto) {
        theme.setName(dto.getName());
        theme.setBgClass(dto.getBgClass());
        theme.setBorderClass(dto.getBorderClass());
        theme.setGridLineClass(dto.getGridLineClass());
        theme.setKeyBgClass(dto.getKeyBgClass());
        theme.setKeyTextClass(dto.getKeyTextClass());
        theme.setPreviewClass(dto.getPreviewClass());
        theme.setBgStyle(dto.getBgStyle());
        theme.setKeyBgStyle(dto.getKeyBgStyle());
        theme.setKeyTextStyle(dto.getKeyTextStyle());
    }
}
