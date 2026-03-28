package de.uniwue.zpd.dachs.larex.backend.repository.admin;

import de.uniwue.zpd.dachs.larex.backend.entity.ErrorEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, String>, JpaSpecificationExecutor<ErrorEvent> {

    @Query("""
            select count(e)
            from ErrorEvent e
            where e.created >= :since
            """)
    long countSince(@Param("since") LocalDateTime since);

    @Query("""
            select count(e)
            from ErrorEvent e
            where e.created >= :since and e.severity = de.uniwue.zpd.dachs.larex.backend.entity.ErrorEvent$Severity.ERROR
            """)
    long countServerErrorsSince(@Param("since") LocalDateTime since);

    @Query("""
            select count(e)
            from ErrorEvent e
            where e.created >= :since and e.severity = de.uniwue.zpd.dachs.larex.backend.entity.ErrorEvent$Severity.WARN
            """)
    long countActionableClientErrorsSince(@Param("since") LocalDateTime since);

    @Query("""
            select count(distinct e.userId)
            from ErrorEvent e
            where e.created >= :since and e.userId is not null
            """)
    long countDistinctUsersSince(@Param("since") LocalDateTime since);

    @Query("""
            select count(distinct e.workspaceId)
            from ErrorEvent e
            where e.created >= :since and e.workspaceId is not null
            """)
    long countDistinctWorkspacesSince(@Param("since") LocalDateTime since);

    Optional<ErrorEvent> findById(String id);

    long deleteByCreatedBefore(LocalDateTime cutoff);
}
