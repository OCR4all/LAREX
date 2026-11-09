package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class EditorPreferencesJsonMigrationIntegrationTest {

    @Test
    void movesSetColumnsIntoPreferencesObjectAndOmitsUnsetOnes() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("54").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO editor_preferences
                            (id, user_id, created, updated, background_color, background_opacity, toolbar_compact,
                             auto_open_jobs_panel, text_view_font_size, textline_sort_order, shortcut_bindings, table_sorting)
                            VALUES ('pref-1', 'user-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '#fff', 0.5, false,
                                    false, 18, 'alphabetical',
                                    '{"version":1,"bindings":{"redo":["meta_y"]}}'::jsonb,
                                    '{"project-pages-v2":{"column":"name","direction":"asc"}}'::jsonb)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO editor_preferences (id, user_id, created, updated)
                            VALUES ('pref-2', 'user-2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """);
                }

                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .target("55").load().migrate();

                var mapper = new ObjectMapper();
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT user_id, preferences FROM editor_preferences ORDER BY user_id")) {
                    assertThat(rows.next()).isTrue();
                    var migrated = mapper.readTree(rows.getString("preferences"));
                    assertThat(migrated.size()).isEqualTo(8);
                    assertThat(migrated.path("backgroundColor").asString()).isEqualTo("#fff");
                    assertThat(migrated.path("backgroundOpacity").asDouble()).isEqualTo(0.5);
                    assertThat(migrated.path("toolbarCompact").isBoolean()).isTrue();
                    assertThat(migrated.path("toolbarCompact").asBoolean()).isFalse();
                    assertThat(migrated.path("autoOpenJobsPanel").asBoolean()).isFalse();
                    assertThat(migrated.path("textViewFontSize").asInt()).isEqualTo(18);
                    assertThat(migrated.path("textlineSortOrder").asString()).isEqualTo("alphabetical");
                    assertThat(migrated.path("shortcutBindings").path("bindings").path("redo").get(0).asString()).isEqualTo("meta_y");
                    assertThat(migrated.path("tableSorting").path("project-pages-v2").path("direction").asString()).isEqualTo("asc");

                    assertThat(rows.next()).isTrue();
                    assertThat(mapper.readTree(rows.getString("preferences")).isEmpty()).isTrue();
                }

                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load().migrate();

                var remainingColumns = new java.util.TreeSet<String>();
                try (var columns = connection.getMetaData().getColumns(null, "public", "editor_preferences", null)) {
                    while (columns.next()) {
                        remainingColumns.add(columns.getString("COLUMN_NAME"));
                    }
                }
                assertThat(remainingColumns).containsExactly("created", "id", "preferences", "updated", "user_id");
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT preferences FROM editor_preferences WHERE user_id = 'user-1'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(mapper.readTree(rows.getString("preferences")).size()).isEqualTo(8);
                }
            }
        }
    }
}
