package de.uniwue.zpd.dachs.larex.backend.repository.user;

import de.uniwue.zpd.dachs.larex.backend.entity.UserPrivateAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPrivateAccessTokenRepository extends JpaRepository<UserPrivateAccessToken, String> {

    List<UserPrivateAccessToken> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    Optional<UserPrivateAccessToken> findByIdAndOwnerUserId(String id, String ownerUserId);

    Optional<UserPrivateAccessToken> findBySecretHash(String secretHash);

    @Query("""
            SELECT COUNT(t)
            FROM UserPrivateAccessToken t
            WHERE t.ownerUserId = :ownerUserId
              AND t.workspaceId = :workspaceId
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
            """)
    long countActiveByOwnerAndWorkspace(
            @Param("ownerUserId") String ownerUserId,
            @Param("workspaceId") String workspaceId,
            @Param("now") LocalDateTime now
    );
}
