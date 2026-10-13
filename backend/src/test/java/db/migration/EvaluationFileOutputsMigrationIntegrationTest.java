package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class EvaluationFileOutputsMigrationIntegrationTest {
    @Test
    void upgradesExistingOutputOwnershipAndDefaultsReportRuns() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE datasets (id VARCHAR(255) PRIMARY KEY)");
                statement.execute("CREATE TABLE action_runs (id VARCHAR(255) PRIMARY KEY)");
                statement.execute("CREATE TABLE action_outputs (id VARCHAR(255) PRIMARY KEY, project_id VARCHAR(255) NOT NULL, status VARCHAR(32))");
                statement.execute("CREATE TABLE stored_files (uuid VARCHAR(32) PRIMARY KEY, project_id VARCHAR(255) NOT NULL, file_type VARCHAR(16), status VARCHAR(32))");
                statement.execute("INSERT INTO datasets VALUES ('dataset')");
                statement.execute("INSERT INTO action_runs VALUES ('existing-run')");
                statement.execute("INSERT INTO action_outputs VALUES ('existing-output', 'project', 'READY')");
                statement.execute("INSERT INTO stored_files VALUES ('existing-file', 'project', 'OUTPUT', 'READY')");
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .baselineOnMigrate(true).baselineVersion("40").target("41").load().migrate();
                try (var rows = statement.executeQuery("SELECT evaluation_output_mode FROM action_runs WHERE id = 'existing-run'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("REPORT");
                }
                statement.execute("INSERT INTO action_outputs (id, dataset_id, status) VALUES ('dataset-output', 'dataset', 'READY')");
                statement.execute("INSERT INTO stored_files (uuid, dataset_id, file_type, status) VALUES ('dataset-file', 'dataset', 'OUTPUT', 'READY')");
                assertThatThrownBy(() -> statement.execute("INSERT INTO action_outputs (id) VALUES ('no-owner')")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("INSERT INTO action_outputs (id, project_id, dataset_id) VALUES ('both', 'project', 'dataset')")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("INSERT INTO stored_files (uuid, dataset_id, file_type) VALUES ('xml', 'dataset', 'XML')")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("UPDATE datasets SET output_retention_days = 0")).isInstanceOf(SQLException.class);
                statement.execute("UPDATE datasets SET output_retention_days = 7");
                try (var rows = statement.executeQuery("SELECT project_id FROM action_outputs WHERE id = 'existing-output'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("project");
                }
            }
        }
    }
}
