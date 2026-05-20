package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunLogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ActionRunLogEventRepository extends JpaRepository<ActionRunLogEvent, String> {

    List<ActionRunLogEvent> findByRunIdOrderByCreatedAsc(String runId);

    @Modifying
    @Query("DELETE FROM ActionRunLogEvent e WHERE e.run.id IN :runIds")
    int deleteByRunIds(@Param("runIds") Collection<String> runIds);
}
