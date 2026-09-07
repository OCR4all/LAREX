package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionTrainingInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionTrainingInputRepository extends JpaRepository<ActionTrainingInput, String> {
    List<ActionTrainingInput> findByRunIdOrderByCreatedAsc(String runId);
}
