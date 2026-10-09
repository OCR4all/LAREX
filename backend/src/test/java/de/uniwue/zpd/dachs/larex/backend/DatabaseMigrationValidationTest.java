package de.uniwue.zpd.dachs.larex.backend;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import de.uniwue.zpd.dachs.larex.backend.repository.user.UserLoginRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseMigrationValidationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserLoginRepository userLoginRepository;

    @Test
    void lastLoginRetainsOnlyTheLatestTimestamp() {
        Instant first = Instant.parse("2026-10-01T10:00:00Z");
        Instant latest = first.plusSeconds(3600);
        userLoginRepository.recordLogin("login-test-user", first);
        userLoginRepository.recordLogin("login-test-user", latest);
        userLoginRepository.recordLogin("login-test-user", first);
        assertEquals(latest, userLoginRepository.findById("login-test-user").orElseThrow().getLastLoginAt());
        assertEquals(1, userLoginRepository.findAllById(java.util.List.of("login-test-user")).size());
    }

	@Test
	void flywayMigrationsMatchJpaMappings() {
	}

	@Test
	void pageXmlsEnforceOneHeadPerPage() {
		Integer matchingConstraints = jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM pg_constraint
				WHERE conrelid = 'public.page_xmls'::regclass
				  AND contype = 'u'
				  AND pg_get_constraintdef(oid) = 'UNIQUE (page_id)'
				""", Integer.class);

		assertEquals(1, matchingConstraints);
	}

}
