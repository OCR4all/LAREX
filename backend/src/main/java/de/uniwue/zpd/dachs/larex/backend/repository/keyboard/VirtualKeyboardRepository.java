package de.uniwue.zpd.dachs.larex.backend.repository.keyboard;

import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualKeyboardRepository extends JpaRepository<VirtualKeyboard, String> {
    List<VirtualKeyboard> findByWorkspaceId(String workspaceId);

    Optional<VirtualKeyboard> findByIdAndWorkspaceId(String id, String workspaceId);

    Optional<VirtualKeyboard> findByNameAndWorkspaceId(String name, String workspaceId);

    boolean existsByNameAndWorkspaceId(String name, String workspaceId);
}
