package de.uniwue.zpd.dachs.larex.backend.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

public final class JsonNodeUtils {

    private JsonNodeUtils() {}

    public record SanitizationResult(JsonNode node, boolean changed) {}

    public static SanitizationResult removeFieldRecursively(JsonNode input, String fieldName) {
        if (input == null) {
            return new SanitizationResult(null, false);
        }

        JsonNode copy = input.isContainer() ? input.deepCopy() : input;
        boolean changed = copy.isContainer() && removeFieldRecursivelyInPlace(copy, fieldName);
        return new SanitizationResult(copy, changed);
    }

    private static boolean removeFieldRecursivelyInPlace(JsonNode node, String fieldName) {
        boolean changed = false;

        if (node instanceof ObjectNode objectNode) {
            if (objectNode.has(fieldName)) {
                objectNode.remove(fieldName);
                changed = true;
            }

            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                changed |= removeFieldRecursivelyInPlace(entry.getValue(), fieldName);
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                changed |= removeFieldRecursivelyInPlace(child, fieldName);
            }
        }

        return changed;
    }
}
