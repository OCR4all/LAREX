package de.uniwue.zpd.dachs.larex.backend.repository.normalization;

import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NormalizationProfileRepository extends JpaRepository<NormalizationProfile, String> {

    List<NormalizationProfile> findByWorkspaceId(String workspaceId);

    Optional<NormalizationProfile> findByIdAndWorkspaceId(String id, String workspaceId);

    Optional<NormalizationProfile> findByNameAndWorkspaceId(String name, String workspaceId);

    boolean existsByNameAndWorkspaceId(String name, String workspaceId);
}
