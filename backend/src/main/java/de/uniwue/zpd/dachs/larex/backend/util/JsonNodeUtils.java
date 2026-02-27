package de.uniwue.zpd.dachs.larex.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

public final class JsonNodeUtils {

    private JsonNodeUtils() {}

    public record SanitizationResult(JsonNode node, boolean changed) {}

    public static SanitizationResult removeFieldRecursively(JsonNode input, String fieldName) {
        if (input == null) {
            return new SanitizationResult(null, false);
        }

        JsonNode copy = input.isContainerNode() ? input.deepCopy() : input;
        boolean changed = copy.isContainerNode() && removeFieldRecursivelyInPlace(copy, fieldName);
        return new SanitizationResult(copy, changed);
    }

    private static boolean removeFieldRecursivelyInPlace(JsonNode node, String fieldName) {
        boolean changed = false;

        if (node instanceof ObjectNode objectNode) {
            if (objectNode.has(fieldName)) {
                objectNode.remove(fieldName);
                changed = true;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
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

