package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowActionsMigrationIntegrationTest {
    @Test
    void linksExistingStepsAndPreservesOnlyCompletePerUserWorkflowDismissals() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                // Isolate the V49 data transformation; the other test migrates the complete schema.
                statement.execute("CREATE TABLE action_runs (id VARCHAR(255) PRIMARY KEY)");
                statement.execute("CREATE TABLE action_workflow_runs (id VARCHAR(255) PRIMARY KEY, status TEXT, step_run_ids_json TEXT)");
                statement.execute("CREATE TABLE action_run_dismissals (run_id VARCHAR(255), user_id VARCHAR(255))");
                statement.execute("INSERT INTO action_runs VALUES ('first'), ('second'), ('active-child'), ('standalone')");
                statement.execute("""
                    INSERT INTO action_workflow_runs VALUES
                    ('completed', 'COMPLETED', '["first", "second"]'),
                    ('active', 'RUNNING', '["active-child"]')
                    """);
                statement.execute("""
                    INSERT INTO action_run_dismissals VALUES
                    ('first', 'owner'), ('second', 'owner'), ('first', 'other-user'), ('active-child', 'owner')
                    """);
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .baselineOnMigrate(true).baselineVersion("48").load().migrate();
                try (var rows = statement.executeQuery("SELECT id, workflow_run_id FROM action_runs ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(2)).isEqualTo("active");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(2)).isEqualTo("completed");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(2)).isEqualTo("completed");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(2)).isNull();
                }
                try (var rows = statement.executeQuery("SELECT run_id, user_id FROM action_workflow_run_dismissals")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("completed");
                    assertThat(rows.getString(2)).isEqualTo("owner");
                    assertThat(rows.next()).isFalse();
                }
                statement.execute("DELETE FROM action_runs WHERE workflow_run_id = 'completed'");
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM action_workflow_run_dismissals")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void publishesLegacyWorkflowsWithoutChangingTheirStepsVersionOrWorkspaceActivation() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("45").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("""
                    INSERT INTO action_workflows (id, version, workspace_id, name, description, steps_json, enabled, created, updated)
                    VALUES ('legacy-workflow', 7, 'workspace', 'Legacy workflow', 'Existing defaults',
                            '[{"processorDefinitionId":"processor","parameters":{"threshold":0.5}}]', true, NOW(), NOW()),
                           ('disabled-workflow', 3, 'another-workspace', 'Disabled workflow', '', '[]', false, NOW(), NOW())
                    """);
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
                try (var rows = statement.executeQuery("""
                    SELECT d.workflow_id, d.enabled, d.global_available, w.version, w.steps_json,
                           a.workspace_id, a.enabled, g.workspace_id, g.enabled
                    FROM action_processor_definitions d JOIN action_workflows w ON w.id = d.workflow_id
                    JOIN action_processor_assignments a ON a.processor_definition_id = d.id
                    JOIN action_processor_workspace_availability g ON g.processor_definition_id = d.id
                    WHERE d.workflow_id = 'legacy-workflow'
                    """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("legacy-workflow");
                    assertThat(rows.getBoolean(2)).isTrue();
                    assertThat(rows.getBoolean(3)).isFalse();
                    assertThat(rows.getLong(4)).isEqualTo(7);
                    assertThat(rows.getString(5)).contains("threshold", "0.5");
                    assertThat(rows.getString(6)).isEqualTo("workspace");
                    assertThat(rows.getBoolean(7)).isTrue();
                    assertThat(rows.getString(8)).isEqualTo("workspace");
                    assertThat(rows.getBoolean(9)).isTrue();
                }
                try (var rows = statement.executeQuery("SELECT enabled FROM action_processor_definitions WHERE workflow_id = 'disabled-workflow'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isFalse();
                }
            }
        }
    }
}
