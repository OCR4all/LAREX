package de.uniwue.zpd.dachs.larex.backend.repository.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface AbstractWorkspaceRepository<T extends AbstractWorkspace> extends JpaRepository<T, String> {
    
    List<T> findByOwnerUserId(String ownerUserId);
    
    Optional<T> findByIdAndOwnerUserId(String id, String ownerUserId);
    
    boolean existsByIdAndOwnerUserId(String id, String ownerUserId);
}