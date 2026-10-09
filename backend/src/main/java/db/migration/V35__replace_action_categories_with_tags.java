package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;

/** Migrate both stored representations before strict definition deserialization. */
public class V35__replace_action_categories_with_tags extends BaseJavaMigration {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    public static String migrateYaml(String source) {
        return migrateDocument(YAML, source);
    }

    public static String migrateJson(String source) {
        return migrateDocument(JSON, source);
    }

    private static String migrateDocument(ObjectMapper mapper, String source) {
        var tree = mapper.readTree(source);
        if (!(tree instanceof ObjectNode document)) {
            throw new IllegalArgumentException("Stored Action definition must be an object");
        }
        document.remove("category");
        document.putArray("tags");
        return mapper.writeValueAsString(document);
    }

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var query = connection.createStatement();
             var rows = query.executeQuery("SELECT id, yaml_source, parsed_json FROM action_processor_definitions");
             var update = connection.prepareStatement(
                     "UPDATE action_processor_definitions SET yaml_source = ?, parsed_json = ? WHERE id = ?")) {
            while (rows.next()) {
                update.setString(1, migrateYaml(rows.getString("yaml_source")));
                update.setString(2, migrateJson(rows.getString("parsed_json")));
                update.setString(3, rows.getString("id"));
                update.addBatch();
            }
            update.executeBatch();
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE action_processor_definitions DROP COLUMN category");
        }
    }
}
