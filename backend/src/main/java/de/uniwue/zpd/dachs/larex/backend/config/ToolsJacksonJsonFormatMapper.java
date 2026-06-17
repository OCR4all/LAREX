package de.uniwue.zpd.dachs.larex.backend.config;

import org.hibernate.type.format.AbstractJsonFormatMapper;
import org.hibernate.type.format.FormatMapperCreationContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;

/**
 * Hibernate 7 still uses a Jackson 2-based JSON FormatMapper by default, while
 * this application persists JSON columns as Jackson 3 tools.jackson types.
 * <p>
 * TODO: Remove this mapper once Hibernate provides native Jackson 3 JSON mapper support.
 */
public final class ToolsJacksonJsonFormatMapper extends AbstractJsonFormatMapper {

    private final ObjectMapper objectMapper;

    public ToolsJacksonJsonFormatMapper() {
        this(JsonMapper.builder().findAndAddModules().build());
    }

    public ToolsJacksonJsonFormatMapper(FormatMapperCreationContext context) {
        this();
    }

    ToolsJacksonJsonFormatMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected <T> T fromString(CharSequence charSequence, Type type) {
        try {
            return objectMapper.readValue(charSequence.toString(), objectMapper.constructType(type));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not deserialize string to java type: " + type, e);
        }
    }

    @Override
    protected <T> String toString(T value, Type type) {
        try {
            return objectMapper.writerFor(objectMapper.constructType(type)).writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not serialize object to java type: " + type, e);
        }
    }
}
