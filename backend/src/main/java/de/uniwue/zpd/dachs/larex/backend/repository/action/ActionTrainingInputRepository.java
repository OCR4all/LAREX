package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionTrainingInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface ActionTrainingInputRepository extends JpaRepository<ActionTrainingInput, String> {
    List<ActionTrainingInput> findByRunIdOrderByCreatedAsc(String runId);

    @Query("""
            select distinct input.sourcePageId
            from ActionTrainingInput input
            where input.run.workspaceId = :workspaceId
              and input.run.kind = de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Kind.TRAINING
              and input.run.status in :statuses
              and input.split in (
                  de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem.Split.TRAIN,
                  de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem.Split.VAL)
              and input.sourcePageId in :pageIds
            """)
    Set<String> findTrainedPageIds(@Param("workspaceId") String workspaceId,
                                   @Param("statuses") Collection<ActionRun.Status> statuses,
                                   @Param("pageIds") Collection<String> pageIds);
}
