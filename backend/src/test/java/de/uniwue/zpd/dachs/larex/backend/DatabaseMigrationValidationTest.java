package de.uniwue.zpd.dachs.larex.backend;

import org.junit.jupiter.api.Test;
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
