package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionProcessorDefinitionRepository extends JpaRepository<ActionProcessorDefinition, String> {
    Optional<ActionProcessorDefinition> findByProcessorKey(String processorKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ActionProcessorDefinition d WHERE d.id = :id")
    Optional<ActionProcessorDefinition> findByIdForUpdate(@Param("id") String id);

    boolean existsByProcessorKey(String processorKey);

    List<ActionProcessorDefinition> findByEnabledTrueOrderByNameAsc();

    List<ActionProcessorDefinition> findByEnabledTrueAndGlobalAvailableTrueOrderByNameAsc();

    List<ActionProcessorDefinition> findAllByOrderByNameAsc();
}
