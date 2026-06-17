package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsJacksonJsonFormatMapperTest {

    @Test
    void roundTripsJsonNodes() {
        ToolsJacksonJsonFormatMapper mapper = new ToolsJacksonJsonFormatMapper();

        JsonNode node = mapper.fromString("{\"meta\":{\"name\":\"PAGE XML Standard\"}}", JsonNode.class);

        assertTrue(node.isObject());
        assertEquals("PAGE XML Standard", node.path("meta").path("name").asText());

        String json = mapper.toString(node, JsonNode.class);
        JsonNode reparsed = mapper.fromString(json, JsonNode.class);

        assertEquals(node, reparsed);
    }
}