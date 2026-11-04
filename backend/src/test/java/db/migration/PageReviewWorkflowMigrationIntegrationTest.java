package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class PageReviewWorkflowMigrationIntegrationTest {
    @Test
    void convertsInProgressPagesAndBackfillsPageCompletion() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE pages (
                            id VARCHAR(255) PRIMARY KEY,
                            workflow_state VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                            CONSTRAINT pages_workflow_state_check CHECK (workflow_state IN ('OPEN', 'IN_PROGRESS', 'DONE'))
                        )""");
                statement.execute("CREATE TABLE tasks (id VARCHAR(255) PRIMARY KEY, sync_linked_page_states BOOLEAN NOT NULL DEFAULT FALSE)");
                statement.execute("CREATE TABLE projects (id VARCHAR(255) PRIMARY KEY)");
                statement.execute("CREATE TABLE team_workspaces (id VARCHAR(255) PRIMARY KEY)");
                statement.execute("CREATE TABLE personal_workspaces (id VARCHAR(255) PRIMARY KEY)");
                statement.execute("""
                        CREATE TABLE notifications (
                            id VARCHAR(255) PRIMARY KEY,
                            type VARCHAR(255) NOT NULL,
                            CONSTRAINT notifications_type_check CHECK (type IN ('TASK_ASSIGNED'))
                        )""");
                statement.execute("""
                        CREATE TABLE notification_preferences (
                            id VARCHAR(255) PRIMARY KEY,
                            notification_type VARCHAR(255) NOT NULL,
                            CONSTRAINT notification_preferences_notification_type_check CHECK (notification_type IN ('TASK_ASSIGNED'))
                        )""");
                statement.execute("INSERT INTO pages (id, workflow_state) VALUES ('open', 'OPEN'), ('working', 'IN_PROGRESS'), ('done', 'DONE')");
                statement.execute("INSERT INTO tasks VALUES ('synced', TRUE), ('plain', FALSE)");
                statement.execute("INSERT INTO projects VALUES ('project')");

                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .baselineOnMigrate(true).baselineVersion("51").target("52").load().migrate();

                try (var rows = statement.executeQuery("SELECT workflow_state FROM pages WHERE id = 'working'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("OPEN");
                }
                try (var rows = statement.executeQuery("SELECT workflow_state FROM pages WHERE id = 'done'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("DONE");
                }
                try (var rows = statement.executeQuery("SELECT id, page_completion FROM tasks ORDER BY id")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("NONE");
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("DONE");
                }
                try (var rows = statement.executeQuery("SELECT review_required FROM projects")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getObject(1)).isNull();
                }
                statement.execute("UPDATE pages SET workflow_state = 'IN_REVIEW' WHERE id = 'open'");
                assertThatThrownBy(() -> statement.execute("UPDATE pages SET workflow_state = 'IN_PROGRESS' WHERE id = 'open'")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("UPDATE tasks SET page_completion = 'OTHER'")).isInstanceOf(SQLException.class);
                statement.execute("INSERT INTO notifications VALUES ('review', 'PAGE_REVIEW_REQUESTED')");
                statement.execute("INSERT INTO notification_preferences VALUES ('changes', 'PAGE_CHANGES_REQUESTED')");
                statement.execute("""
                        INSERT INTO page_workflow_events (id, page_id, action, from_state, to_state, created)
                        VALUES ('event', 'open', 'ASSIGNMENT_SYNC', 'OPEN', 'IN_REVIEW', NOW())""");
                statement.execute("DELETE FROM pages WHERE id = 'open'");
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM page_workflow_events")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}
