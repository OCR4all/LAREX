package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionAuditEventRepository extends JpaRepository<ActionAuditEvent, String> {
    List<ActionAuditEvent> findTop100ByProcessorDefinitionIdOrderByCreatedDesc(String processorDefinitionId);
}
