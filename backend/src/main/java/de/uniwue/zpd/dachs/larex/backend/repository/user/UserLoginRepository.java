package de.uniwue.zpd.dachs.larex.backend.repository.user;

import de.uniwue.zpd.dachs.larex.backend.entity.UserLogin;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserLoginRepository extends JpaRepository<UserLogin, String> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO user_logins (user_id, last_login_at)
            VALUES (:userId, :loginAt)
            ON CONFLICT (user_id) DO UPDATE
            SET last_login_at = GREATEST(user_logins.last_login_at, EXCLUDED.last_login_at)
            """, nativeQuery = true)
    void recordLogin(@Param("userId") String userId, @Param("loginAt") Instant loginAt);
}
