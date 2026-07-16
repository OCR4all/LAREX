package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunPageResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActionRunPageResultRepository extends JpaRepository<ActionRunPageResult, String> {

    Optional<ActionRunPageResult> findByRunIdAndPageId(String runId, String pageId);

    List<ActionRunPageResult> findByRunIdOrderByCreatedAsc(String runId);

    List<ActionRunPageResult> findByRunIdInOrderByCreatedAsc(Collection<String> runIds);

    long countByRunId(String runId);

    boolean existsByRunId(String runId);
}
