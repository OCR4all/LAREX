package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ActionTagsMigrationIntegrationTest {
    @Test
    void upgradesV34DatabaseAndDropsCategoryWithoutChangingOtherColumns() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("34").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                String yaml = "version: 1\nid: migration-test\nname: Migration test\ncategory: LAYOUT\ndescription: |\n  First line\n  Second line\nendpoint:\n  url: https://processor.example/dispatch\n";
                var yamlMapper = new ObjectMapper(new YAMLFactory());
                var jsonMapper = new ObjectMapper();
                String json = jsonMapper.writeValueAsString(yamlMapper.readTree(yaml));
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
