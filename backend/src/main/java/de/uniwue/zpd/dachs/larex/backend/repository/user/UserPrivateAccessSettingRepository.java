package de.uniwue.zpd.dachs.larex.backend.repository.user;

import de.uniwue.zpd.dachs.larex.backend.entity.UserPrivateAccessSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPrivateAccessSettingRepository extends JpaRepository<UserPrivateAccessSetting, String> {
}
