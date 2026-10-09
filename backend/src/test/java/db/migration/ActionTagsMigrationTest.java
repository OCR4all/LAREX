package db.migration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionTagsMigrationTest {
    @Test
    void preservesBlockAndFlowYamlIncludingMultilineDescriptionsAndNestedFields() {
        var yaml = new ObjectMapper(new YAMLFactory());
        for (String source : new String[]{
                "version: 1\nid: legacy\ncategory: LAYOUT\ndescription: |\n  First line\n  category: keep this text\nendpoint:\n  url: https://processor.example/dispatch\nparameters:\n  category:\n    type: string\n",
                "{version: 1, id: legacy, category: LAYOUT, description: 'category: keep this text', endpoint: {url: 'https://processor.example/dispatch'}}",
                "version: 1\nid: legacy\ndescription: No category\n"}) {
            var expected = (ObjectNode) yaml.readTree(source);
            expected.remove("category");
            expected.putArray("tags");
            assertThat(yaml.readTree(V35__replace_action_categories_with_tags.migrateYaml(source))).isEqualTo(expected);
        }
    }

    @Test
    void removesCategoryFromJsonIncludingNullAndInitializesTags() {
        var json = new ObjectMapper();
        for (String source : new String[]{"{\"category\":\"LAYOUT\",\"outputs\":{\"xml\":{\"enabled\":true}}}", "{\"category\":null}", "{}"}) {
            var expected = (ObjectNode) json.readTree(source);
            expected.remove("category");
            expected.putArray("tags");
            assertThat(json.readTree(V35__replace_action_categories_with_tags.migrateJson(source))).isEqualTo(expected);
        }
    }

    @Test
    void refusesInvalidDocumentsRatherThanDiscardingTheirContent() {
        assertThatThrownBy(() -> V35__replace_action_categories_with_tags.migrateYaml("- not a definition"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
