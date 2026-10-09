package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;

import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ActionTagsMigrationIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void upgradesV33DatabaseThroughOutputModeCleanupAndCategoryReplacement(boolean outputModesAlreadyCleaned) throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("33").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                String yaml = "version: 1\nid: migration-test\nname: Migration test\ncategory: LAYOUT\ndescription: |\n  First line\n  Second line\nendpoint:\n  url: https://processor.example/dispatch\n";
                var yamlMapper = new ObjectMapper(new YAMLFactory());
                var jsonMapper = new ObjectMapper();
                var document = (ObjectNode) yamlMapper.readTree(yaml);
                var outputs = document.putObject("outputs");
                outputs.putObject("xml").put("enabled", true).put("mode", "upsert");
                outputs.putObject("images").put("enabled", false).putNull("mode");
                String json = jsonMapper.writeValueAsString(document);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO action_processor_definitions
                        (id, processor_key, name, yaml_source, parsed_json, endpoint_url, endpoint_timeout_seconds,
                         execute_role, lock_mode, category, target_types_json, accepts_images, accepts_xml,
                         outputs_images, outputs_xml, enabled, created_by_user_id, updated_by_user_id, created, updated)
                        VALUES ('migration-test', 'migration-test', 'Migration test', ?, ?, 'https://processor.example/dispatch',
                                30, 'CURATOR', 'PAGES', 'LAYOUT', '["PAGE"]', true, false, false, true, true,
                                'author', 'author', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)) {
                    insert.setString(1, yaml);
                    insert.setString(2, json);
                    insert.executeUpdate();
                }
                if (outputModesAlreadyCleaned) {
                    try (var migration = getClass().getResourceAsStream("/db/migration/V34__remove_action_output_modes.sql");
                         var statement = connection.createStatement()) {
                        assertThat(migration).isNotNull();
                        statement.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .target("34").load().migrate();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT parsed_json FROM action_processor_definitions WHERE id = 'migration-test'")) {
                    assertThat(rows.next()).isTrue();
                    var migrated = jsonMapper.readTree(rows.getString("parsed_json"));
                    assertThat(migrated.path("outputs").path("xml").has("mode")).isFalse();
                    assertThat(migrated.path("outputs").path("images").has("mode")).isFalse();
                    assertThat(migrated.path("outputs").path("xml").path("enabled").asBoolean()).isTrue();
                    assertThat(migrated.path("category").asString()).isEqualTo("LAYOUT");
                }
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM action_processor_definitions WHERE id = 'migration-test'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(yamlMapper.readTree(rows.getString("yaml_source")).has("category")).isFalse();
                    assertThat(jsonMapper.readTree(rows.getString("parsed_json")).path("tags").isArray()).isTrue();
                    assertThat(jsonMapper.readTree(rows.getString("parsed_json")).path("tags").size()).isZero();
                    assertThat(yamlMapper.readTree(rows.getString("yaml_source")).path("description").asString()).isEqualTo("First line\nSecond line\n");
                    assertThat(rows.getString("endpoint_url")).isEqualTo("https://processor.example/dispatch");
                    assertThat(rows.getString("created_by_user_id")).isEqualTo("author");
                }
                try (var columns = connection.getMetaData().getColumns(null, "public", "action_processor_definitions", "category")) {
                    assertThat(columns.next()).isFalse();
                }
            }
        }
    }
}
