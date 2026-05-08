package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionProcessorDefinitionRepository extends JpaRepository<ActionProcessorDefinition, String> {
    Optional<ActionProcessorDefinition> findByProcessorKey(String processorKey);

    boolean existsByProcessorKey(String processorKey);

    List<ActionProcessorDefinition> findByEnabledTrueOrderByNameAsc();

    List<ActionProcessorDefinition> findAllByOrderByNameAsc();
}
