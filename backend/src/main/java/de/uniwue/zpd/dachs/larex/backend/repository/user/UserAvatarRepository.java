package de.uniwue.zpd.dachs.larex.backend.repository.user;

import de.uniwue.zpd.dachs.larex.backend.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, String> {
    Optional<UserAvatar> findByStorageKey(String storageKey);
}
