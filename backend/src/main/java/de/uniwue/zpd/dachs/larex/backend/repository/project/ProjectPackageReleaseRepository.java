package de.uniwue.zpd.dachs.larex.backend.repository.project;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectPackageRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectPackageReleaseRepository extends JpaRepository<ProjectPackageRelease, String> {

    List<ProjectPackageRelease> findByProjectIdOrderByVersionNumberDesc(String projectId);

    Optional<ProjectPackageRelease> findByIdAndProjectId(String id, String projectId);

    Optional<ProjectPackageRelease> findBySharePublicId(String sharePublicId);

    @Query("""
            SELECT COALESCE(MAX(r.versionNumber), 0)
            FROM ProjectPackageRelease r
            WHERE r.project.id = :projectId
            """)
    Integer findMaxVersionNumberByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT COALESCE(SUM(r.packageFileSize), 0)
            FROM ProjectPackageRelease r
            WHERE r.project.library.workspaceId = :workspaceId AND r.packageFileSize IS NOT NULL
            """)
    Long sumPackageFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);
}
