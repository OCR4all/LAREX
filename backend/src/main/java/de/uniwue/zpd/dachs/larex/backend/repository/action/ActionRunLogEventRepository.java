package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunLogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionRunLogEventRepository extends JpaRepository<ActionRunLogEvent, String> {

    List<ActionRunLogEvent> findByRunIdOrderByCreatedAsc(String runId);
}
