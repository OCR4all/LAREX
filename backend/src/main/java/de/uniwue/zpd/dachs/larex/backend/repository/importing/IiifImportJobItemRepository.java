package de.uniwue.zpd.dachs.larex.backend.repository.importing;

import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJobItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IiifImportJobItemRepository extends JpaRepository<IiifImportJobItem, String> {

    boolean existsByJobIdAndCanvasIndex(String jobId, int canvasIndex);

    boolean existsByJobId(String jobId);

    List<IiifImportJobItem> findByJobIdOrderByCanvasIndexAsc(String jobId);

    List<IiifImportJobItem> findByJobIdInOrderByJobIdAscCanvasIndexAsc(Collection<String> jobIds);

    void deleteByJobId(String jobId);

    @Query("""
            SELECT COALESCE(SUM(i.actualBytes), 0)
            FROM IiifImportJobItem i
            WHERE i.jobId = :jobId
              AND i.status = 'IMPORTED'
            """)
    long sumImportedBytes(@Param("jobId") String jobId);
}
