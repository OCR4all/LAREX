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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseUpgradeIntegrationTest {

    private static final MigrationVersion BASELINE = MigrationVersion.fromVersion("1");
    private static final String SEEDS = "classpath:db/upgrade/V*.sql";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.7-alpine"));

    static {
        POSTGRES.start();
        installSeededHistory();
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
    void upgradeAppliesEveryNewerMigration() {
        var info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).allSatisfy(migration -> assertThat(migration.getState().isFailed()).isFalse());
        assertThat(info.applied()).anySatisfy(migration -> assertThat(migration.getVersion()).isEqualTo(BASELINE));
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

    private static void installSeededHistory() {
        Resource[] seeds = seeds();
        if (seeds.length == 0 || !seedVersion(seeds[0]).equals(BASELINE)) {
            throw new IllegalStateException("Upgrade seeds must start with V" + BASELINE + ".sql, the LAREX 1.0.0 schema");
        }
        for (Resource seed : seeds) {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .target(seedVersion(seed))
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                ScriptUtils.executeSqlScript(connection, seed);
            } catch (SQLException e) {
                throw new IllegalStateException("Could not apply upgrade seed " + seed.getFilename(), e);
            }
        }
    }

    private static Resource[] seeds() {
        try {
            Resource[] seeds = new PathMatchingResourcePatternResolver().getResources(SEEDS);
            Arrays.sort(seeds, Comparator.comparing(DatabaseUpgradeIntegrationTest::seedVersion));
            return seeds;
        } catch (IOException e) {
            throw new IllegalStateException("Could not list upgrade seeds", e);
        }
    }

    private static MigrationVersion seedVersion(Resource seed) {
        String name = seed.getFilename();
        return MigrationVersion.fromVersion(name.substring(1, name.length() - ".sql".length()));
    }
}
