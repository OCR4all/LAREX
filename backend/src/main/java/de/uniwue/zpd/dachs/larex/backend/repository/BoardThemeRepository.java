package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.BoardTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardThemeRepository extends JpaRepository<BoardTheme, String> {
	List<BoardTheme> findByWorkspaceId(String workspaceId);

	Optional<BoardTheme> findByIdAndWorkspaceId(String id, String workspaceId);

	Optional<BoardTheme> findByNameAndWorkspaceId(String name, String workspaceId);

	boolean existsByNameAndWorkspaceId(String name, String workspaceId);
}
