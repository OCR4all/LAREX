package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class IntegrationActionsMigrationIntegrationTest {
    @Test void upgradesExistingDatabaseAndEnforcesResourceScopesAndSnapshotOwnership() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("36").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                        INSERT INTO action_processor_definitions
                        (id, processor_key, name, yaml_source, parsed_json, endpoint_url, endpoint_timeout_seconds,
                         execute_role, lock_mode, target_types_json, accepts_images, accepts_xml, outputs_images,
                         outputs_xml, enabled, created_by_user_id, updated_by_user_id, created, updated)
                        VALUES ('integration', 'integration', 'Integration', '', '{}', 'http://localhost/dispatch',
                        30, 'CURATOR', 'NONE', '["PAGE"]', false, true, false, false, true, 'user', 'user', NOW(), NOW())
                        """);
                }
                insertRun(connection, "old-processing", "PROCESSING", "project", null);
                insertRun(connection, "old-training", "TRAINING", null, "dataset");
                insertRun(connection, "old-evaluation", "EVALUATION", null, "dataset");
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
                connection.createStatement().executeUpdate("UPDATE action_processor_definitions SET action_kind = 'INTEGRATION' WHERE id = 'integration'");
                insertRun(connection, "project-integration", "INTEGRATION", "project", null);
                insertRun(connection, "dataset-integration", "INTEGRATION", null, "dataset");
                assertThatThrownBy(() -> insertRun(connection, "ambiguous", "INTEGRATION", "project", "dataset")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertRun(connection, "missing", "INTEGRATION", null, null)).isInstanceOf(SQLException.class);
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                        INSERT INTO action_input_snapshots
                        (id, run_id, input_id, page_name, source_page_id, file_type, source_file_id, file_name,
                         mime_type, file_size, checksum_sha256, snapshot_path, position)
                        VALUES ('snapshot', 'project-integration', 'page', 'Page', 'page', 'xml', 'xml', 'page.xml',
                        'application/xml', 12, 'checksum', 'actions/integration/run/page.xml', 0)
                        """);
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM action_runs")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(5);
                    }
                    statement.execute("DELETE FROM action_runs WHERE id = 'project-integration'");
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM action_input_snapshots")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
                    }
                }
            }
        }
    }

    private void insertRun(Connection connection, String id, String kind, String project, String dataset) throws SQLException {
        try (var insert = connection.prepareStatement("""
            INSERT INTO action_runs (id, processor_definition_id, workspace_id, project_id, dataset_id, run_kind,
            cancel_requested, progress_percent, created, updated, secret_expires_at, secret_prefix, secret_hash,
            lock_mode, created_by_user_id, page_ids_json, status)
            VALUES (?, 'integration', 'workspace', ?, ?, ?, false, 0, NOW(), NOW(), NOW(), 'secret', 'hash', 'NONE', 'user', '[]', 'QUEUED')
            """)) {
            insert.setString(1, id); insert.setString(2, project); insert.setString(3, dataset); insert.setString(4, kind);
            insert.executeUpdate();
        }
    }
}
