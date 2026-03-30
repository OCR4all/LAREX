package de.uniwue.zpd.dachs.larex.backend.repository.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetItemRepository extends JpaRepository<DatasetItem, String> {

    @EntityGraph(attributePaths = {"copyFiles"})
    List<DatasetItem> findByDatasetIdOrderByCreatedAsc(String datasetId);

    boolean existsByDatasetIdAndSourcePageId(String datasetId, String sourcePageId);

    long countByDatasetId(String datasetId);

    @EntityGraph(attributePaths = {"copyFiles"})
    Optional<DatasetItem> findByIdAndDatasetId(String id, String datasetId);
}
