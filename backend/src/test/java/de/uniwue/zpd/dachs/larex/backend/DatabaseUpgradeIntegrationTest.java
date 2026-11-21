package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseUpgradeIntegrationTest {

    private static final String RELEASE = "1.0.0";
    private static final String RELEASE_SCHEMA_VERSION = "1";
    private static final Map<String, Integer> RELEASED_MIGRATION_CHECKSUMS = Map.of("1", 1423058824);

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.7-alpine"));

    static {
        POSTGRES.start();
        installRelease();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamWorkspaceRepository teamWorkspaces;
    @Autowired PersonalWorkspaceRepository personalWorkspaces;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;

    @Test
    void releasedMigrationsAreUnchanged() {
        Map<String, Integer> applied = jdbc.queryForList(
                        "SELECT version, checksum FROM flyway_schema_history WHERE version IS NOT NULL")
                .stream()
                .filter(row -> RELEASED_MIGRATION_CHECKSUMS.containsKey((String) row.get("version")))
                .collect(Collectors.toMap(row -> (String) row.get("version"), row -> (Integer) row.get("checksum")));

        assertThat(applied).isEqualTo(RELEASED_MIGRATION_CHECKSUMS);
    }

    @Test
    void upgradeAppliesEveryNewerMigration() {
        var info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).allSatisfy(migration -> assertThat(migration.getState().isFailed()).isFalse());
        assertThat(info.current().getVersion())
                .isGreaterThanOrEqualTo(MigrationVersion.fromVersion(RELEASE_SCHEMA_VERSION));
    }

    @Test
    void seededWorkspacesKeepTheirSettingsAndGainAttributionDefaults() {
        var team = teamWorkspaces.findById("upgrade-team-workspace").orElseThrow();
        assertThat(team.getName()).isEqualTo("Upgrade Team");
        assertThat(team.isRecordPageXmlAuthors()).isTrue();
        assertThat(team.isPageXmlCreatorFromUser()).isTrue();

        var personal = personalWorkspaces.findById("upgrade-personal-workspace").orElseThrow();
        assertThat(personal.isRecordPageXmlAuthors()).isTrue();
        assertThat(personal.isPageXmlCreatorFromUser()).isTrue();
    }

    @Test
    void seededProjectsAndPagesRemainReadable() {
        assertThat(projects.findById("upgrade-project").orElseThrow().getName()).isEqualTo("Upgrade Project");
        assertThat(pages.findByProjectId("upgrade-project"))
                .extracting(Page::getName, Page::getWorkflowState)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("0001", Page.WorkflowState.DONE),
                        org.assertj.core.groups.Tuple.tuple("0002", Page.WorkflowState.IN_REVIEW));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM page_xmls WHERE page_id = 'upgrade-page-1'", Integer.class))
                .isEqualTo(1);
    }

    private static void installRelease() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(RELEASE_SCHEMA_VERSION))
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/upgrade/" + RELEASE + ".sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not seed the LAREX " + RELEASE + " database", e);
        }
    }
}
