package de.uniwue.zpd.dachs.larex.backend.repository.validation;

import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationRulesetRepository extends JpaRepository<ValidationRuleset, String> {

    List<ValidationRuleset> findByWorkspaceId(String workspaceId);

    Optional<ValidationRuleset> findByIdAndWorkspaceId(String id, String workspaceId);

    Optional<ValidationRuleset> findByNameAndWorkspaceId(String name, String workspaceId);

    boolean existsByNameAndWorkspaceId(String name, String workspaceId);
}
