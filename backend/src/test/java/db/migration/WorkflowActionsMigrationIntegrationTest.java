package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowActionsMigrationIntegrationTest {
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
